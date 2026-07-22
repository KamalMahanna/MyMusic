package com.mymusic.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mymusic.app.data.repository.DownloadRepository
import com.mymusic.app.player.MusicService
import com.mymusic.app.player.QueueManager
import com.mymusic.app.ui.navigation.MyMusicNavGraph
import com.mymusic.app.ui.theme.MyMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted — triggering download scan")
            CoroutineScope(Dispatchers.IO).launch {
                downloadRepository.refreshDownloadedSongs()
            }
        } else {
            Log.w(TAG, "Storage permission denied")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted — media controls will appear")
            // Re-start MusicService so the notification is posted now that permission is granted
            if (queueManager.currentSong != null) {
                try {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, MusicService::class.java)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart MusicService after notification permission: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "Notification permission denied — media controls will not appear")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission first (Android 13+) so the media notification
        // can be posted. Without this, media controls won't appear on the lock screen
        // or notification shade on Android 13+ devices.
        requestNotificationPermissionIfNeeded()

        // If there is a previously saved song, start MusicService eagerly so that
        // restoreLastPlayedSong() runs immediately and the mini player appears on launch.
        if (queueManager.currentSong != null) {
            Log.d(TAG, "Restored session found — starting MusicService eagerly")
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, MusicService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicService on restore: ${e.message}", e)
            }
        }

        // Request storage permission so the download scanner can read Music/MyMusic
        requestStoragePermissionIfNeeded()

        setContent {
            MyMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyMusicNavGraph()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission")
                    notificationPermissionLauncher.launch(permission)
                }
            }
        }
    }

    private fun requestStoragePermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Storage permission already granted — triggering download scan")
                CoroutineScope(Dispatchers.IO).launch {
                    downloadRepository.refreshDownloadedSongs()
                }
            }
            else -> {
                Log.d(TAG, "Requesting storage permission: $permission")
                storagePermissionLauncher.launch(permission)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
