package com.metromusic.app.player

import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val musicRepository: MusicRepository
) {
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val playedSongIds = mutableSetOf<String>()
    private var isLoadingSuggestions = false

    val currentSong: Song?
        get() {
            val idx = _currentIndex.value
            val q = _queue.value
            return if (idx in q.indices) q[idx] else null
        }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        _queue.value = songs.toList()
        _currentIndex.value = startIndex
        playedSongIds.clear()
        songs.forEach { playedSongIds.add(it.id) }
    }

    fun addToQueue(song: Song) {
        if (!playedSongIds.contains(song.id)) {
            _queue.value = _queue.value + song
            playedSongIds.add(song.id)
        }
    }

    fun addToQueue(songs: List<Song>) {
        val newSongs = songs.filter { !playedSongIds.contains(it.id) }
        if (newSongs.isNotEmpty()) {
            _queue.value = _queue.value + newSongs
            newSongs.forEach { playedSongIds.add(it.id) }
        }
    }

    fun moveToNext(): Song? {
        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < _queue.value.size) {
            _currentIndex.value = nextIndex
            _queue.value[nextIndex]
        } else null
    }

    fun moveToPrevious(): Song? {
        val prevIndex = _currentIndex.value - 1
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            _queue.value[prevIndex]
        } else null
    }

    fun jumpTo(index: Int): Song? {
        return if (index in _queue.value.indices) {
            _currentIndex.value = index
            _queue.value[index]
        } else null
    }

    fun isNearEnd(): Boolean {
        return _currentIndex.value >= _queue.value.size - 3
    }

    suspend fun loadMoreSuggestions() {
        if (isLoadingSuggestions) return
        isLoadingSuggestions = true

        try {
            val currentSong = currentSong ?: return
            val result = musicRepository.getSongSuggestions(currentSong.id, 20)
            result.onSuccess { suggestions ->
                addToQueue(suggestions)
            }
        } finally {
            isLoadingSuggestions = false
        }
    }

    fun clear() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        playedSongIds.clear()
    }

    fun removeAt(index: Int) {
        val mutableQueue = _queue.value.toMutableList()
        if (index in mutableQueue.indices) {
            mutableQueue.removeAt(index)
            _queue.value = mutableQueue
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }
}
