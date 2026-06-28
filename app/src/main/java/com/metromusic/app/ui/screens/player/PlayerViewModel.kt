package com.metromusic.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.DownloadRepository
import com.metromusic.app.download.SongDownloader
import com.metromusic.app.player.MusicPlayerManager
import com.metromusic.app.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicPlayerManager: MusicPlayerManager,
    private val songDownloader: SongDownloader,
    private val downloadRepository: DownloadRepository,
    private val queueManager: QueueManager
) : ViewModel() {

    val playbackState = musicPlayerManager.playbackState
    val downloadState = songDownloader.downloadState
    val downloadStates = songDownloader.downloadStates
    val queue = queueManager.queue
    val queueIndex = queueManager.currentIndex
    val downloadedSongs = downloadRepository.downloadedSongs
    val isShuffleEnabled = queueManager.isShuffleEnabled

    val currentSong: StateFlow<Song?> = musicPlayerManager.playbackState
        .map { it.currentSong }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentSongId: StateFlow<String?> = musicPlayerManager.playbackState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isPlaying: StateFlow<Boolean> = musicPlayerManager.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isBuffering: StateFlow<Boolean> = musicPlayerManager.playbackState
        .map { it.isBuffering }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val activeDownloadSongId: StateFlow<String?> = songDownloader.downloadState
        .map { if (it.isDownloading) it.songId else null }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun playSongFromList(songs: List<Song>, index: Int) {
        musicPlayerManager.playSongFromQueue(songs, index)
    }

    fun playSongWithRecommendations(song: Song) {
        musicPlayerManager.playSongWithRecommendations(song)
    }


    fun playNext() {
        viewModelScope.launch {
            musicPlayerManager.playNext()
        }
    }

    fun playPrevious() {
        musicPlayerManager.playPrevious()
    }

    fun togglePlayPause() {
        musicPlayerManager.togglePlayPause()
    }

    fun seekTo(position: Long) {
        musicPlayerManager.seekTo(position)
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            songDownloader.downloadSong(song)
        }
    }

    fun isSongDownloaded(song: Song): Boolean {
        return downloadRepository.isSongDownloaded(song)
    }

    fun playQueueIndex(index: Int) {
        musicPlayerManager.jumpToQueueIndex(index)
    }

    fun removeFromQueue(index: Int) {
        queueManager.removeAt(index)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }
}
