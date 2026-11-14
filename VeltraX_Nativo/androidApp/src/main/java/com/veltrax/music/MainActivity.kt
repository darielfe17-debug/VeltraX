package com.veltrax.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veltrax.music.ui.VeltraXScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: MainViewModel = viewModel()
            var permission by remember { mutableStateOf(hasAudioPermission()) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                permission = it
            }
            LaunchedEffect(permission) { if (permission) model.refreshSongs() }
            VeltraXScreen(model, permission, onPermission = {
                launcher.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            })
        }
    }
    private fun hasAudioPermission(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(this,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
