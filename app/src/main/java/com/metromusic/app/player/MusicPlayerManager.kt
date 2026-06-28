package com.metromusic.app.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metromusic.app.data.model.Song
import androidx.media3.common.ForwardingPlayer
import com.metromusic.app.data.repository.DownloadRepository
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

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
    private val downloadRepository: DownloadRepository,
    private val streamingCacheManager: StreamingCacheManager
) {
    private var exoPlayer: ExoPlayer? = null
    private var forwardingPlayer: Player? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null
    private var cachingJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun getPlayer(): Player {
        return getOrCreatePlayer()
    }

    private fun getOrCreatePlayer(): Player {
        val player = exoPlayer
        if (player != null) return forwardingPlayer ?: player

        val newExoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
                restoreLastPlayedSong(player)
            }

        val newForwardingPlayer = object : ForwardingPlayer(newExoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                scope.launch { playNext() }
            }

            override fun seekToNextMediaItem() {
                scope.launch { playNext() }
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }
        forwardingPlayer = newForwardingPlayer
        return newForwardingPlayer
    }

    fun playSong(song: Song) {
        Log.d(TAG, "playSong: songId='${song.id}', name='${song.name}', artist='${song.primaryArtistNames}'")
        val player = getOrCreatePlayer()
        
        try {
            Log.d(TAG, "Starting MusicService")
            val intent = Intent(context, MusicService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
        }

        val readableFile = downloadRepository.getReadableFileForSong(song)
        val cachedFile = if (readableFile == null) streamingCacheManager.getCachedFileForSong(song) else null
        
        val uri = when {
            song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:")) -> {
                val filePath = if (song.url.startsWith("file://")) song.url.substring(7) else song.url
                val customFile = java.io.File(filePath)
                if (customFile.exists() && customFile.canRead()) {
                    Log.d(TAG, "Using file URI directly from song.url: ${song.url}")
                    if (song.url.startsWith("/")) android.net.Uri.fromFile(customFile).toString()
                    else song.url
                } else {
                    Log.w(TAG, "Local file from song.url does not exist or is unreadable: $filePath. Falling back to other sources.")
                    when {
                        readableFile != null -> {
                            Log.d(TAG, "Found downloaded song file at ${readableFile.absolutePath}")
                            android.net.Uri.fromFile(readableFile).toString()
                        }
                        cachedFile != null -> {
                            Log.d(TAG, "Found cached song file at ${cachedFile.absolutePath}")
                            android.net.Uri.fromFile(cachedFile).toString()
                        }
                        else -> {
                            val dlUrl = song.highQualityDownloadUrl
                            Log.d(TAG, "Using high quality download URL: $dlUrl")
                            dlUrl ?: return
                        }
                    }
                }
            }
            readableFile != null -> {
                Log.d(TAG, "Found downloaded song file at ${readableFile.absolutePath}")
                android.net.Uri.fromFile(readableFile).toString()
            }
            cachedFile != null -> {
                Log.d(TAG, "Found cached song file at ${cachedFile.absolutePath}")
                android.net.Uri.fromFile(cachedFile).toString()
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

        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = true,
            isBuffering = true
        )

        cachingJob?.cancel()
        streamingCacheManager.cleanTempFiles()
        if (readableFile == null && cachedFile == null) {
            cachingJob = scope.launch {
                Log.d(TAG, "Triggering background caching for song '${song.name}'")
                streamingCacheManager.cacheSong(song)
            }
        }

        scope.launch {
            // Fetch artwork with 150ms timeout to avoid delaying playback
            val artworkBitmap = withContext(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(150) {
                        when {
                            localArtworkFile != null -> BitmapFactory.decodeFile(localArtworkFile.absolutePath)
                            song.highQualityImageUrl != null -> {
                                val request = ImageRequest.Builder(context)
                                    .data(song.highQualityImageUrl)
                                    .allowHardware(false)
                                    .build()
                                (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
                            }
                            else -> null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }

            val artworkBytes = artworkBitmap?.let { bitmap ->
                try {
                    ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        stream.toByteArray()
                    }
                } catch (e: Exception) {
                    null
                }
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.primaryArtistNames)
                .setAlbumTitle(song.album.name)
                .setArtworkUri(artworkUri)
            if (artworkBytes != null) {
                metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            Log.d(TAG, "ExoPlayer: setMediaItem, prepared and play() invoked (hasArtworkData=${artworkBytes != null})")

            // Fallback: If artwork timed out, fetch it fully in background and patch metadata
            if (artworkBitmap == null && song.highQualityImageUrl != null) {
                launch(Dispatchers.IO) {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(song.highQualityImageUrl)
                            .allowHardware(false)
                            .build()
                        val result = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
                        if (result != null) {
                            withContext(Dispatchers.Main) {
                                if (_playbackState.value.currentSong?.id == song.id) {
                                    updateArtworkMetadata(player, result, song)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallback artwork fetch failed: ${e.message}")
                    }
                }
            }

            // Check if we need more suggestions
            if (queueManager.isNearEnd()) {
                Log.d(TAG, "Queue is near the end, loading suggestions")
                queueManager.loadMoreSuggestions()
            }
        }
    }

    /**
     * Updates the current MediaItem's metadata with the fetched [bitmap] as raw JPEG bytes.
     * This is required for the system media notification (lock screen / notification shade)
     * to display album art — it cannot load remote HTTP URLs itself.
     */
    private fun updateArtworkMetadata(player: Player, bitmap: Bitmap, song: Song) {
        try {
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
            val updatedMetadata = player.mediaMetadata
                .buildUpon()
                .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                player.currentMediaItem!!.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()
            )
            Log.d(TAG, "Artwork metadata updated for '${song.name}' (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update artwork metadata: ${e.message}")
        }
    }

    fun setLoadingSong(song: Song) {
        Log.d(TAG, "setLoadingSong: songId='${song.id}', name='${song.name}'")
        exoPlayer?.stop()
        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = false,
            isBuffering = true
        )
    }

    fun clearLoadingState(songId: String) {
        Log.d(TAG, "clearLoadingState: songId='$songId'")
        if (_playbackState.value.currentSong?.id == songId && _playbackState.value.isBuffering) {
            _playbackState.value = PlaybackState(
                currentSong = null,
                isPlaying = false,
                isBuffering = false
            )
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

    fun playSongWithRecommendations(song: Song) {
        Log.d(TAG, "playSongWithRecommendations: songId='${song.id}', name='${song.name}'")
        queueManager.setQueue(listOf(song), 0)
        playSong(song)
        scope.launch {
            try {
                queueManager.loadMoreSuggestions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load suggestions: ${e.message}", e)
            }
        }
    }


    suspend fun playNext() {
        Log.d(TAG, "playNext() invoked")
        if (queueManager.isNearEnd()) {
            Log.d(TAG, "Queue near end, pre-loading suggestions")
            scope.launch {
                try {
                    queueManager.loadMoreSuggestions()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load suggestions in playNext: ${e.message}", e)
                }
            }
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
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
            }
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: position=$position")
        exoPlayer?.seekTo(position)
        _playbackState.value = _playbackState.value.copy(currentPosition = position)
        try {
            val sharedPreferences = context.getSharedPreferences("metro_music_playback_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit()
                .putLong("KEY_SEEK_POSITION", position)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save seek position", e)
        }
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
                    val pos = player.currentPosition.coerceAtLeast(0)
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = pos,
                        duration = player.duration.coerceAtLeast(0)
                    )
                    try {
                        val sharedPreferences = context.getSharedPreferences("metro_music_playback_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putLong("KEY_SEEK_POSITION", pos)
                            .apply()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save progress seek position", e)
                    }
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
        cachingJob?.cancel()
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
        forwardingPlayer = null
    }

    private fun restoreLastPlayedSong(player: Player) {
        val lastSong = queueManager.currentSong
        if (lastSong != null) {
            Log.d(TAG, "restoreLastPlayedSong: found song '${lastSong.name}' in queue manager, restoring to ExoPlayer")
            val readableFile = downloadRepository.getReadableFileForSong(lastSong)
            val cachedFile = if (readableFile == null) streamingCacheManager.getCachedFileForSong(lastSong) else null
            
            val uri = when {
                lastSong.url.isNotEmpty() && (lastSong.url.startsWith("/") || lastSong.url.startsWith("file:")) -> {
                    val filePath = if (lastSong.url.startsWith("file://")) lastSong.url.substring(7) else lastSong.url
                    val customFile = java.io.File(filePath)
                    if (customFile.exists() && customFile.canRead()) {
                        if (lastSong.url.startsWith("/")) android.net.Uri.fromFile(customFile).toString()
                        else lastSong.url
                    } else {
                        when {
                            readableFile != null -> android.net.Uri.fromFile(readableFile).toString()
                            cachedFile != null -> android.net.Uri.fromFile(cachedFile).toString()
                            else -> lastSong.highQualityDownloadUrl
                        }
                    }
                }
                readableFile != null -> android.net.Uri.fromFile(readableFile).toString()
                cachedFile != null -> android.net.Uri.fromFile(cachedFile).toString()
                else -> lastSong.highQualityDownloadUrl
            }

            if (uri != null) {
                val localArtworkFile = downloadRepository.getCachedArtworkForSong(lastSong)
                val artworkUri = if (localArtworkFile != null) {
                    android.net.Uri.fromFile(localArtworkFile)
                } else {
                    lastSong.highQualityImageUrl?.let { android.net.Uri.parse(it) }
                }

                scope.launch {
                    val artworkBitmap = withContext(Dispatchers.IO) {
                        try {
                            when {
                                localArtworkFile != null -> BitmapFactory.decodeFile(localArtworkFile.absolutePath)
                                lastSong.highQualityImageUrl != null -> {
                                    val request = ImageRequest.Builder(context)
                                        .data(lastSong.highQualityImageUrl)
                                        .allowHardware(false)
                                        .build()
                                    (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load artwork for background restoration: ${e.message}", e)
                            null
                        }
                    }

                    val artworkBytes = artworkBitmap?.let { bitmap ->
                        try {
                            ByteArrayOutputStream().use { stream ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                stream.toByteArray()
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(lastSong.name)
                        .setArtist(lastSong.primaryArtistNames)
                        .setAlbumTitle(lastSong.album.name)
                        .setArtworkUri(artworkUri)
                    if (artworkBytes != null) {
                        metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadataBuilder.build())
                        .build()

                    player.setMediaItem(mediaItem)
                    
                    val sharedPreferences = context.getSharedPreferences("metro_music_playback_prefs", Context.MODE_PRIVATE)
                    val savedPos = sharedPreferences.getLong("KEY_SEEK_POSITION", 0L)
                    if (savedPos > 0) {
                        player.seekTo(savedPos)
                    }
                    player.prepare()

                    _playbackState.value = PlaybackState(
                        currentSong = lastSong,
                        isPlaying = false,
                        isBuffering = false,
                        currentPosition = savedPos,
                        duration = 0L
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "MusicPlayerManager"
    }
}
