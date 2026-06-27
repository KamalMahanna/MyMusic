package com.metromusic.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metromusic.app.data.model.Album
import com.metromusic.app.data.model.ModuleItem
import com.metromusic.app.data.model.ModuleSection
import com.metromusic.app.data.model.Playlist
import com.metromusic.app.data.model.Song
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
            _uiState.value = _uiState.value.copy(error = null)
            try {
                when (item.type.lowercase().trim()) {
                    "song" -> {
                        val placeholderSong = Song(
                            id = item.id,
                            name = item.name,
                            image = item.image
                        )
                        musicPlayerManager.setLoadingSong(placeholderSong)

                        val result = musicRepository.getSongById(item.id)
                        result.onSuccess { song ->
                            musicPlayerManager.playSong(song)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load song", e)
                            _uiState.value = _uiState.value.copy(error = "Failed to load song: ${e.message}")
                            musicPlayerManager.clearLoadingState(item.id)
                        }
                    }
                    "playlist", "channel" -> {
                        val placeholderPlaylist = Playlist(
                            id = item.id,
                            name = item.name,
                            image = item.image,
                            songs = null
                        )
                        _uiState.value = _uiState.value.copy(selectedPlaylist = placeholderPlaylist)

                        val result = musicRepository.getPlaylistById(item.id)
                        result.onSuccess { playlist ->
                            val current = _uiState.value.selectedPlaylist
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(selectedPlaylist = playlist)
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load playlist", e)
                            val current = _uiState.value.selectedPlaylist
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(
                                    selectedPlaylist = null,
                                    error = "Failed to load playlist: ${e.message}"
                                )
                            }
                        }
                    }
                    "album" -> {
                        val placeholderAlbum = Album(
                            id = item.id,
                            name = item.name,
                            image = item.image,
                            songs = null
                        )
                        _uiState.value = _uiState.value.copy(selectedAlbum = placeholderAlbum)

                        val result = musicRepository.getAlbumById(item.id)
                        result.onSuccess { album ->
                            val current = _uiState.value.selectedAlbum
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(selectedAlbum = album)
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load album", e)
                            val current = _uiState.value.selectedAlbum
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(
                                    selectedAlbum = null,
                                    error = "Failed to load album: ${e.message}"
                                )
                            }
                        }
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(error = "Unsupported type: ${item.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playModuleItem", e)
                _uiState.value = _uiState.value.copy(error = "An error occurred: ${e.message}")
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
