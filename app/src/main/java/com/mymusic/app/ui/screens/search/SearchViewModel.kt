package com.mymusic.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.app.data.model.*
import com.mymusic.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
    val isArtistDetailLoading: Boolean = false,
    val selectedArtistDetail: ArtistDetail? = null,
    val isPlaylistDetailLoading: Boolean = false,
    val selectedPlaylist: Playlist? = null,
    val isAlbumDetailLoading: Boolean = false,
    val selectedAlbum: Album? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        performSearch(query)
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                isLoading = false
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(1000) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val songsDeferred = async { musicRepository.searchSongs(query) }
            val albumsDeferred = async { musicRepository.searchAlbums(query) }
            val artistsDeferred = async { musicRepository.searchArtists(query) }
            val playlistsDeferred = async { musicRepository.searchPlaylists(query) }

            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()
            val artistsResult = artistsDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            if (songsResult.isSuccess || albumsResult.isSuccess || artistsResult.isSuccess || playlistsResult.isSuccess) {
                val rawSongs = songsResult.getOrNull()?.results ?: emptyList()
                val rawAlbums = albumsResult.getOrNull()?.results ?: emptyList()
                val rawArtists = artistsResult.getOrNull()?.results ?: emptyList()
                val rawPlaylists = playlistsResult.getOrNull()?.results ?: emptyList()

                // Deduplicate and sort songs by playCount desc
                val cleanSongs = rawSongs
                    .distinctBy { song -> song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim() }
                    .sortedByDescending { it.playCount ?: 0 }

                // Deduplicate albums by ID
                val cleanAlbums = rawAlbums.distinctBy { it.id }

                // Deduplicate artists by ID
                val cleanArtists = rawArtists.distinctBy { it.id }

                // Deduplicate playlists by ID
                val cleanPlaylists = rawPlaylists.distinctBy { it.id }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    songs = cleanSongs,
                    albums = cleanAlbums,
                    artists = cleanArtists,
                    playlists = cleanPlaylists
                )
            } else {
                val errorMsg = songsResult.exceptionOrNull()?.message
                    ?: albumsResult.exceptionOrNull()?.message
                    ?: artistsResult.exceptionOrNull()?.message
                    ?: playlistsResult.exceptionOrNull()?.message
                    ?: "Search failed"
                _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
            }
        }
    }

    fun selectArtist(artistId: String) {
        _uiState.value = _uiState.value.copy(isArtistDetailLoading = true, selectedArtistDetail = null)
        viewModelScope.launch {
            musicRepository.getArtistById(artistId)
                .onSuccess { detail ->
                    val deduplicatedTopSongs = detail.topSongs
                        ?.distinctBy { song -> song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim() }
                    val cleanDetail = if (deduplicatedTopSongs != null) detail.copy(topSongs = deduplicatedTopSongs) else detail
                    _uiState.value = _uiState.value.copy(
                        isArtistDetailLoading = false,
                        selectedArtistDetail = cleanDetail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isArtistDetailLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun selectPlaylist(playlistId: String) {
        _uiState.value = _uiState.value.copy(isPlaylistDetailLoading = true, selectedPlaylist = null)
        viewModelScope.launch {
            musicRepository.getPlaylistById(playlistId)
                .onSuccess { detail ->
                    val deduplicatedSongs = detail.songs
                        ?.distinctBy { song -> song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim() }
                    val cleanDetail = if (deduplicatedSongs != null) detail.copy(songs = deduplicatedSongs) else detail
                    _uiState.value = _uiState.value.copy(
                        isPlaylistDetailLoading = false,
                        selectedPlaylist = cleanDetail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isPlaylistDetailLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun selectAlbum(albumId: String) {
        _uiState.value = _uiState.value.copy(isAlbumDetailLoading = true, selectedAlbum = null)
        viewModelScope.launch {
            musicRepository.getAlbumById(albumId)
                .onSuccess { detail ->
                    val deduplicatedSongs = detail.songs
                        ?.distinctBy { song -> song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim() }
                    val cleanDetail = if (deduplicatedSongs != null) detail.copy(songs = deduplicatedSongs) else detail
                    _uiState.value = _uiState.value.copy(
                        isAlbumDetailLoading = false,
                        selectedAlbum = cleanDetail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isAlbumDetailLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun clearSelectedArtist() {
        _uiState.value = _uiState.value.copy(selectedArtistDetail = null)
    }

    fun clearSelectedPlaylist() {
        _uiState.value = _uiState.value.copy(selectedPlaylist = null)
    }

    fun clearSelectedAlbum() {
        _uiState.value = _uiState.value.copy(selectedAlbum = null)
    }
}
