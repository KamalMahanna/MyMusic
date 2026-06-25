package com.metromusic.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metromusic.app.data.model.DownloadedSong
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = downloadRepository.downloadedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            downloadRepository.refreshDownloadedSongs()
        }
    }

    fun deleteSong(song: DownloadedSong) {
        viewModelScope.launch {
            downloadRepository.deleteSong(song)
        }
    }
    
    fun getAsSongList(): List<Song> {
        return downloadedSongs.value.map { ds ->
            Song(
                id = ds.id,
                name = ds.name,
                url = ds.filePath, // Use file path as URL for local playback
                duration = ds.duration,
                artists = com.metromusic.app.data.model.SongArtists(
                    primary = listOf(com.metromusic.app.data.model.ArtistMap(name = ds.artist))
                ),
                image = emptyList(),
                downloadUrl = emptyList() // It's already local
            )
        }
    }
}
