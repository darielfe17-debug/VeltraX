package com.veltrax.music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import com.veltrax.music.data.LibraryStore
import com.veltrax.music.data.MusicRepository
import com.veltrax.music.data.Song
import com.veltrax.music.download.DownloadWorker
import com.veltrax.music.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MusicRepository(app)
    private val library = LibraryStore(app)
    private val workManager = WorkManager.getInstance(app)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()
    private val _favorites = MutableStateFlow(library.favorites())
    val favorites = _favorites.asStateFlow()
    private val _playlists = MutableStateFlow(library.playlists())
    val playlists = _playlists.asStateFlow()
    private val _downloads = MutableStateFlow(library.downloads())
    val downloads = _downloads.asStateFlow()
    private val _workStates = MutableStateFlow<Map<String, WorkInfo>>(emptyMap())
    val workStates = _workStates.asStateFlow()
    private val _nowPlaying = MutableStateFlow<Song?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    init {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({
                try {
                    controller = f.get().also { c ->
                        c.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncPlayer() }
                            override fun onPlaybackStateChanged(playbackState: Int) { syncPlayer() }
                        })
                        syncPlayer()
                    }
                } catch (_: Exception) { /* No session available yet. */ }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }
    private fun syncPlayer() {
        val c = controller ?: return
        val uri = c.currentMediaItem?.localConfiguration?.uri?.toString()
        _nowPlaying.value = _songs.value.firstOrNull { it.uri == uri }
            ?: c.currentMediaItem?.let { item ->
                Song(uri ?: "", item.mediaMetadata.title?.toString() ?: "Canción",
                    item.mediaMetadata.artist?.toString() ?: "", "", c.duration.coerceAtLeast(0))
            }
        _isPlaying.value = c.isPlaying
        _position.value = c.currentPosition.coerceAtLeast(0)
        _duration.value = c.duration.coerceAtLeast(0)
    }
    suspend fun refreshSongs() {
        try { _songs.value = repository.readAll(); syncPlayer() } catch (_: SecurityException) { _songs.value = emptyList() }
    }
    fun refreshProgress() {
        val c = controller ?: return
        _position.value = c.currentPosition.coerceAtLeast(0)
        _duration.value = c.duration.coerceAtLeast(0)
    }
    suspend fun refreshDownloads() {
        _downloads.value = library.downloads()
        val results = withContext(Dispatchers.IO) {
            try { workManager.getWorkInfosByTag("veltrax").get() } catch (_: Exception) { emptyList() }
        }
        val currentRequests = _downloads.value.filter { it.workId.isNotBlank() }.associate { it.workId to it.id }
        _workStates.value = results.mapNotNull { wi ->
            currentRequests[wi.id.toString()]?.let { downloadId -> downloadId to wi }
        }.toMap()
    }
    fun play(song: Song, collection: List<Song>) {
        val c = controller ?: return
        val queue = collection.ifEmpty { listOf(song) }
        val items = queue.map { s ->
            MediaItem.Builder().setUri(Uri.parse(s.uri)).setMediaId(s.uri).setMediaMetadata(
                MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).build()
            ).build()
        }
        c.setMediaItems(items, queue.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0), 0)
        c.prepare()
        c.play()
        syncPlayer()
    }
    fun togglePlayback() { controller?.let { if (it.isPlaying) it.pause() else it.play() }; syncPlayer() }
    fun next() { controller?.seekToNextMediaItem(); syncPlayer() }
    fun previous() { controller?.seekToPreviousMediaItem(); syncPlayer() }
    fun seek(milliseconds: Long) { controller?.seekTo(milliseconds); syncPlayer() }
    fun toggleFavorite(uri: String) { library.toggleFavorite(uri); _favorites.value = library.favorites() }
    fun createPlaylist(name: String) { library.createPlaylist(name); _playlists.value = library.playlists() }
    fun addToPlaylist(name: String, uri: String) { library.addToPlaylist(name, uri); _playlists.value = library.playlists() }
    fun removeFromPlaylist(name: String, uri: String) { library.removeFromPlaylist(name, uri); _playlists.value = library.playlists() }
    fun enqueue(url: String, filename: String) {
        val validUrl = try { java.net.URI(url.trim()).let { it.scheme in listOf("https", "http") && it.host != null } } catch (_: Exception) { false }
        if (!validUrl || filename.isBlank()) return
        val id = UUID.randomUUID().toString()
        val item = LibraryStore.DownloadItem(id, url.trim(), filename.trim())
        library.addDownload(item)
        _downloads.value = library.downloads()
        submit(item)
    }
    private fun submit(item: LibraryStore.DownloadItem) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.URL, item.url)
                .putString(DownloadWorker.NAME, item.fileName).putString(DownloadWorker.ID, item.id).build())
            .addTag("veltrax").addTag("file:${item.id}").build()
        library.addDownload(item.copy(workId = request.id.toString()))
        _downloads.value = library.downloads()
        workManager.enqueueUniqueWork("veltrax:${item.id}", ExistingWorkPolicy.REPLACE, request)
        viewModelScope.launch { refreshDownloads() }
    }
    fun pause(item: LibraryStore.DownloadItem) {
        workManager.cancelUniqueWork("veltrax:${item.id}")
        viewModelScope.launch { refreshDownloads() }
    }
    fun resume(item: LibraryStore.DownloadItem) { submit(item) }
    override fun onCleared() {
        future?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
