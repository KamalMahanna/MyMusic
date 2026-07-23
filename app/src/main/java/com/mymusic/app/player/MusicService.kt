package com.mymusic.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.mymusic.app.MainActivity

@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    @Inject
    lateinit var queueManager: QueueManager

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        Log.d(TAG, "onCreate() service started")
        super.onCreate()
        sharedPreferences = getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        promoteToForegroundImmediate()

        val player = musicPlayerManager.getPlayer()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                Log.d(TAG, "onPlaybackResumption: System UI requested playback resumption")
                val songs = queueManager.queue.value
                val currentIndex = queueManager.currentIndex.value
                val savedPos = sharedPreferences.getLong("KEY_SEEK_POSITION", 0L)

                if (songs.isEmpty()) {
                    Log.w(TAG, "onPlaybackResumption: no songs in queue to resume")
                    return Futures.immediateFailedFuture(
                        UnsupportedOperationException("No songs to resume")
                    )
                }

                val mediaItems = songs.map { song ->
                    musicPlayerManager.buildMediaItemForResumption(song)
                }
                val startIndex = if (currentIndex in songs.indices) currentIndex else 0

                Log.d(TAG, "onPlaybackResumption: restoring ${mediaItems.size} items, startIndex=$startIndex, startPos=$savedPos")
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, savedPos)
                )
            }
        })
            .setSessionActivity(pendingIntent)
            .build()
        // Register with Media3's notification system so MediaStyle controls appear
        // in the notification panel and Quick Settings media player.
        addSession(mediaSession!!)
        Log.d(TAG, "MediaLibrarySession successfully built and registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundImmediate()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun promoteToForegroundImmediate() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyMusic")
            .setContentText("Preparing playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    fallbackNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1001, fallbackNotification)
            }
            Log.d(TAG, "Promoted service to foreground successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground safely: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession: controller package=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved() called. App swiped away from recent tasks.")
        val player = mediaSession?.player
        if (player != null && player.playWhenReady && player.mediaItemCount > 0) {
            // Pause playback but keep the service alive so System UI can still
            // show the media resumption card in the notification shade.
            Log.d(TAG, "Pausing playback but keeping service alive for media resumption")
            player.pause()
        } else {
            // Nothing playing — safe to tear down the service entirely.
            Log.d(TAG, "No active playback, stopping service")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() service stopping")
        mediaSession?.run {
            Log.d(TAG, "Releasing MediaLibrarySession")
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel: $CHANNEL_ID")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "mymusic_playback"
    }
}
