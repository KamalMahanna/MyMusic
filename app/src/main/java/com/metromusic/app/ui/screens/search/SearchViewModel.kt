package com.metromusic.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metromusic.app.data.model.SearchArtist
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.SONGS,
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val error: String? = null
)

enum class SearchFilter {
    SONGS, ARTISTS
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        performSearch(query, _uiState.value.filter)
    }

    fun onFilterChange(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        performSearch(_uiState.value.query, filter)
    }

    private fun performSearch(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(songs = emptyList(), artists = emptyList(), isLoading = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(1000) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            if (filter == SearchFilter.SONGS) {
                val result = musicRepository.searchSongs(query)
                result.onSuccess { res ->
                    _uiState.value = _uiState.value.copy(isLoading = false, songs = res.results, artists = emptyList())
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            } else {
                val result = musicRepository.searchArtists(query)
                result.onSuccess { res ->
                    _uiState.value = _uiState.value.copy(isLoading = false, artists = res.results, songs = emptyList())
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
}
