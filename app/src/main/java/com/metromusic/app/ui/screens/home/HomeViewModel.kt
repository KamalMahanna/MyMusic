package com.metromusic.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metromusic.app.data.model.Album
import com.metromusic.app.data.model.ModuleItem
import com.metromusic.app.data.model.ModuleSection
import com.metromusic.app.data.model.Playlist
import com.metromusic.app.data.repository.MusicRepository
import com.metromusic.app.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val sections: List<ModuleSection> = emptyList(),
    val error: String? = null,
    val isItemLoading: Boolean = false,
    val selectedPlaylist: Playlist? = null,
    val selectedAlbum: Album? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadModules()
    }

    private fun loadModules() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = musicRepository.getModules()
            result.onSuccess { sections ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sections = sections.sortedBy { it.position }
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun playModuleItem(item: ModuleItem) {
        Log.d(TAG, "playModuleItem: id='${item.id}', type='${item.type}', name='${item.name}'")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isItemLoading = true, error = null)
            try {
                when (item.type.lowercase().trim()) {
                    "song" -> {
                        val result = musicRepository.getSongById(item.id)
                        result.onSuccess { song ->
                            musicPlayerManager.playSong(song)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load song", e)
                            _uiState.value = _uiState.value.copy(error = "Failed to load song: ${e.message}")
                        }
                    }
                    "playlist", "channel" -> {
                        val result = musicRepository.getPlaylistById(item.id)
                        result.onSuccess { playlist ->
                            _uiState.value = _uiState.value.copy(selectedPlaylist = playlist)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load playlist", e)
                            _uiState.value = _uiState.value.copy(error = "Failed to load playlist: ${e.message}")
                        }
                    }
                    "album" -> {
                        val result = musicRepository.getAlbumById(item.id)
                        result.onSuccess { album ->
                            _uiState.value = _uiState.value.copy(selectedAlbum = album)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load album", e)
                            _uiState.value = _uiState.value.copy(error = "Failed to load album: ${e.message}")
                        }
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(error = "Unsupported type: ${item.type}")
                    }
                }
            } finally {
                _uiState.value = _uiState.value.copy(isItemLoading = false)
            }
        }
    }

    fun clearSelectedPlaylist() {
        _uiState.value = _uiState.value.copy(selectedPlaylist = null)
    }

    fun clearSelectedAlbum() {
        _uiState.value = _uiState.value.copy(selectedAlbum = null)
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
