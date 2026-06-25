package com.metromusic.app.player

import android.content.Context
import android.content.Intent
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
                exoPlayer = player
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
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
                                scope.launch { playNext() }
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                    }
                })

                // Auto-play when audio device (headphones/bluetooth) connects
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val callback = object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        val hasHeadphones = addedDevices?.any { device ->
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        } == true
                        
                        if (hasHeadphones) {
                            if (player.mediaItemCount > 0 && !player.isPlaying) {
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
        val player = getOrCreatePlayer()
        
        try {
            val intent = Intent(context, MusicService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val file = downloadRepository.getFileForSong(song)
        
        val uri = when {
            song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:")) -> {
                if (song.url.startsWith("/")) android.net.Uri.fromFile(java.io.File(song.url)).toString()
                else song.url
            }
            file.exists() -> {
                android.net.Uri.fromFile(file).toString()
            }
            else -> {
                song.highQualityDownloadUrl ?: return
            }
        }

        val localArtworkFile = downloadRepository.getCachedArtworkForSong(song)
        val artworkUri = if (localArtworkFile != null) {
            android.net.Uri.fromFile(localArtworkFile)
        } else {
            song.highQualityImageUrl?.let { android.net.Uri.parse(it) }
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

        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = true,
            isBuffering = true
        )

        // Check if we need more suggestions
        scope.launch {
            if (queueManager.isNearEnd()) {
                queueManager.loadMoreSuggestions()
            }
        }
    }

    fun playSongFromQueue(songs: List<Song>, index: Int) {
        queueManager.setQueue(songs, index)
        val song = queueManager.currentSong ?: return
        playSong(song)
    }

    suspend fun playNext() {
        if (queueManager.isNearEnd()) {
            queueManager.loadMoreSuggestions()
        }
        val nextSong = queueManager.moveToNext()
        if (nextSong != null) {
            playSong(nextSong)
        }
    }

    fun playPrevious() {
        val prevSong = queueManager.moveToPrevious()
        if (prevSong != null) {
            playSong(prevSong)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            try {
                val intent = Intent(context, MusicService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            player.play()
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playbackState.value = _playbackState.value.copy(currentPosition = position)
    }

    fun jumpToQueueIndex(index: Int) {
        val song = queueManager.jumpTo(index) ?: return
        playSong(song)
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
        stopProgressUpdate()
        scope.cancel()
        audioDeviceCallback?.let { callback ->
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.unregisterAudioDeviceCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioDeviceCallback = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
