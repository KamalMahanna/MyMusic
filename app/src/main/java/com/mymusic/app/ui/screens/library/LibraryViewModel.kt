package com.mymusic.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.app.data.model.DownloadedSong
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = downloadRepository.downloadedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Downloaded songs filtered by the current search query (name or artist). */
    val filteredDownloadedSongs: StateFlow<List<DownloadedSong>> =
        combine(downloadedSongs, _searchQuery) { songs, query ->
            if (query.isBlank()) {
                songs
            } else {
                val lower = query.lowercase()
                songs.filter { song ->
                    song.name.lowercase().contains(lower) ||
                        song.artist.lowercase().contains(lower)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    fun getAsSongList(songs: List<DownloadedSong> = filteredDownloadedSongs.value): List<Song> {
        return songs.map { ds ->
            Song(
                id = ds.id,
                name = ds.name,
                url = ds.filePath, // Use file path as URL for local playback
                duration = ds.duration,
                artists = com.mymusic.app.data.model.SongArtists(
                    primary = listOf(com.mymusic.app.data.model.ArtistMap(name = ds.artist))
                ),
                image = if (!ds.imageUrl.isNullOrEmpty()) {
                    listOf(com.mymusic.app.data.model.DownloadLink(quality = "500x500", url = ds.imageUrl))
                } else {
                    emptyList()
                },
                downloadUrl = emptyList() // It's already local
            )
        }
    }
}
