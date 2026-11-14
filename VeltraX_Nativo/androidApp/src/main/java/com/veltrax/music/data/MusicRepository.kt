package com.veltrax.music.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(val uri: String, val title: String, val artist: String, val album: String, val duration: Long)

class MusicRepository(private val context: Context) {
    suspend fun readAll(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE
        )
        context.contentResolver.query(collection, columns, "${MediaStore.Audio.Media.SIZE} > 0", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cur ->
            val id = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cur.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cur.getLong(id))
                songs += Song(uri.toString(), cur.getString(title) ?: "Sin título",
                    cur.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Artista desconocido",
                    cur.getString(album) ?: "", cur.getLong(duration))
            }
        }
        songs
    }
}
