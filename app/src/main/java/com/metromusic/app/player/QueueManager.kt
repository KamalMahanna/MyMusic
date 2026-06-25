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
    private val playedKeys = mutableSetOf<Pair<String, String>>()
    private var isLoadingSuggestions = false

    val currentSong: Song?
        get() {
            val idx = _currentIndex.value
            val q = _queue.value
            return if (idx in q.indices) q[idx] else null
        }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        val originalTargetSong = if (startIndex in songs.indices) songs[startIndex] else null

        val uniqueSongs = mutableListOf<Song>()
        val seenKeys = mutableSetOf<Pair<String, String>>()
        songs.forEach { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            if (seenKeys.add(key)) {
                uniqueSongs.add(song)
            }
        }

        _queue.value = uniqueSongs
        playedSongIds.clear()
        playedKeys.clear()
        uniqueSongs.forEach {
            playedSongIds.add(it.id)
            playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
        }

        val newIndex = if (originalTargetSong != null) {
            uniqueSongs.indexOfFirst { it.id == originalTargetSong.id }
        } else {
            -1
        }
        _currentIndex.value = if (newIndex != -1) newIndex else startIndex.coerceIn(-1, uniqueSongs.size - 1)
    }

    fun addToQueue(song: Song) {
        val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
        if (!playedSongIds.contains(song.id) && !playedKeys.contains(key)) {
            _queue.value = _queue.value + song
            playedSongIds.add(song.id)
            playedKeys.add(key)
        }
    }

    fun addToQueue(songs: List<Song>) {
        val newSongs = songs.filter { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            !playedSongIds.contains(song.id) && !playedKeys.contains(key)
        }
        if (newSongs.isNotEmpty()) {
            val uniqueIncoming = mutableListOf<Song>()
            val incomingKeys = mutableSetOf<Pair<String, String>>()
            newSongs.forEach { song ->
                val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
                if (incomingKeys.add(key)) {
                    uniqueIncoming.add(song)
                }
            }
            _queue.value = _queue.value + uniqueIncoming
            uniqueIncoming.forEach {
                playedSongIds.add(it.id)
                playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
            }
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
        playedKeys.clear()
    }

    fun removeAt(index: Int) {
        val mutableQueue = _queue.value.toMutableList()
        if (index in mutableQueue.indices) {
            val removedSong = mutableQueue.removeAt(index)
            _queue.value = mutableQueue
            playedSongIds.remove(removedSong.id)
            playedKeys.remove(removedSong.name.lowercase().trim() to removedSong.primaryArtistNames.lowercase().trim())
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }
}
