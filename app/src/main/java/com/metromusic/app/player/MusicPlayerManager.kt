package com.metromusic.app.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isBuffering: Boolean = false
)

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val downloadRepository: DownloadRepository
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun getPlayer(): ExoPlayer {
        return getOrCreatePlayer()
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                Log.d(TAG, "ExoPlayer created and configured")
                exoPlayer = player
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateString = when (playbackState) {
                            Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            Player.STATE_READY -> "STATE_READY"
                            Player.STATE_ENDED -> "STATE_ENDED"
                            Player.STATE_IDLE -> "STATE_IDLE"
                            else -> "UNKNOWN"
                        }
                        Log.d(TAG, "onPlaybackStateChanged: $stateString")

                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playbackState.value = _playbackState.value.copy(isBuffering = true)
                            }
                            Player.STATE_READY -> {
                                _playbackState.value = _playbackState.value.copy(
                                    isBuffering = false,
                                    duration = player.duration.coerceAtLeast(0)
                                )
                            }
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Playback ended, playing next song")
                                scope.launch { playNext() }
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                    }
                })

                // Auto-play when audio device (headphones/bluetooth) connects
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val callback = object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        val deviceTypes = addedDevices?.map { it.type }?.joinToString(", ") ?: "None"
                        Log.d(TAG, "Audio devices added: $deviceTypes")
                        val hasHeadphones = addedDevices?.any { device ->
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        } == true
                        
                        if (hasHeadphones) {
                            Log.d(TAG, "Headphones/Bluetooth detected as added. player.mediaItemCount=${player.mediaItemCount}, player.isPlaying=${player.isPlaying}")
                            if (player.mediaItemCount > 0 && !player.isPlaying) {
                                Log.d(TAG, "Resuming playback due to audio device addition")
                                player.play()
                            }
                        }
                    }
                }
                audioManager.registerAudioDeviceCallback(callback, null)
                audioDeviceCallback = callback
            }
    }

    fun playSong(song: Song) {
        Log.d(TAG, "playSong: songId='${song.id}', name='${song.name}', artist='${song.primaryArtistNames}'")
        val player = getOrCreatePlayer()
        
        try {
            Log.d(TAG, "Starting MusicService")
            val intent = Intent(context, MusicService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
        }

        val file = downloadRepository.getFileForSong(song)
        
        val uri = when {
            song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:")) -> {
                Log.d(TAG, "Using file URI directly from song.url: ${song.url}")
                if (song.url.startsWith("/")) android.net.Uri.fromFile(java.io.File(song.url)).toString()
                else song.url
            }
            file.exists() -> {
                Log.d(TAG, "Found downloaded song file at ${file.absolutePath}")
                android.net.Uri.fromFile(file).toString()
            }
            else -> {
                val dlUrl = song.highQualityDownloadUrl
                Log.d(TAG, "Using high quality download URL: $dlUrl")
                dlUrl ?: return
            }
        }

        val localArtworkFile = downloadRepository.getCachedArtworkForSong(song)
        val artworkUri = if (localArtworkFile != null) {
            Log.d(TAG, "Using cached artwork: ${localArtworkFile.absolutePath}")
            android.net.Uri.fromFile(localArtworkFile)
        } else {
            song.highQualityImageUrl?.let {
                Log.d(TAG, "Using network artwork URL: $it")
                android.net.Uri.parse(it)
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.primaryArtistNames)
                    .setAlbumTitle(song.album.name)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        Log.d(TAG, "ExoPlayer: setMediaItem, prepared and play() invoked")

        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = true,
            isBuffering = true
        )

        // Check if we need more suggestions
        scope.launch {
            if (queueManager.isNearEnd()) {
                Log.d(TAG, "Queue is near the end, loading suggestions")
                queueManager.loadMoreSuggestions()
            }
        }
    }

    fun playSongFromQueue(songs: List<Song>, index: Int) {
        Log.d(TAG, "playSongFromQueue: index=$index, size=${songs.size}")
        queueManager.setQueue(songs, index)
        val song = queueManager.currentSong
        if (song != null) {
            playSong(song)
        } else {
            Log.w(TAG, "playSongFromQueue: no song found at index $index")
        }
    }

    suspend fun playNext() {
        Log.d(TAG, "playNext() invoked")
        if (queueManager.isNearEnd()) {
            Log.d(TAG, "Queue near end, pre-loading suggestions")
            queueManager.loadMoreSuggestions()
        }
        val nextSong = queueManager.moveToNext()
        Log.d(TAG, "playNext: nextSong='${nextSong?.name}'")
        if (nextSong != null) {
            playSong(nextSong)
        }
    }

    fun playPrevious() {
        Log.d(TAG, "playPrevious() invoked")
        val prevSong = queueManager.moveToPrevious()
        Log.d(TAG, "playPrevious: prevSong='${prevSong?.name}'")
        if (prevSong != null) {
            playSong(prevSong)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player == null) {
            Log.w(TAG, "togglePlayPause: player is null")
            return
        }
        Log.d(TAG, "togglePlayPause: currently playing=${player.isPlaying}")
        if (player.isPlaying) {
            player.pause()
        } else {
            try {
                Log.d(TAG, "Starting MusicService on resume")
                val intent = Intent(context, MusicService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
            }
            player.play()
        }
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: position=$position")
        exoPlayer?.seekTo(position)
        _playbackState.value = _playbackState.value.copy(currentPosition = position)
    }

    fun jumpToQueueIndex(index: Int) {
        Log.d(TAG, "jumpToQueueIndex: index=$index")
        val song = queueManager.jumpTo(index)
        if (song != null) {
            playSong(song)
        } else {
            Log.w(TAG, "jumpToQueueIndex: no song at index $index")
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = player.currentPosition.coerceAtLeast(0),
                        duration = player.duration.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun release() {
        Log.d(TAG, "release() invoked")
        stopProgressUpdate()
        scope.cancel()
        audioDeviceCallback?.let { callback ->
            try {
                Log.d(TAG, "Unregistering audio device callback")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.unregisterAudioDeviceCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister audio device callback: ${e.message}", e)
            }
        }
        audioDeviceCallback = null
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        private const val TAG = "MusicPlayerManager"
    }
}
