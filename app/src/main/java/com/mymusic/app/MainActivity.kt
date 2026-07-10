package com.mymusic.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mymusic.app.player.MusicService
import com.mymusic.app.player.QueueManager
import com.mymusic.app.data.NetworkConnectivityObserver
import com.mymusic.app.ui.navigation.MyMusicNavGraph
import com.mymusic.app.ui.theme.MyMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var networkConnectivityObserver: NetworkConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // If there is a previously saved song, start MusicService eagerly so that
        // restoreLastPlayedSong() runs immediately and the mini player appears on launch.
        if (queueManager.currentSong != null) {
            Log.d("MainActivity", "Restored session found — starting MusicService eagerly")
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, MusicService::class.java)
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start MusicService on restore: ${e.message}", e)
            }
        }

        setContent {
            MyMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyMusicNavGraph(
                        networkConnectivityObserver = networkConnectivityObserver
                    )
                }
            }
        }
    }
}

