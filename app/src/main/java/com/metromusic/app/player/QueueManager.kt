package com.metromusic.app.player

import android.content.Context
import android.util.Log
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.MusicRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val musicRepository: MusicRepository,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) {
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val playedSongIds = mutableSetOf<String>()
    private val playedKeys = mutableSetOf<Pair<String, String>>()
    private var isLoadingSuggestions = false

    private val sharedPreferences = context.getSharedPreferences("metro_music_playback_prefs", Context.MODE_PRIVATE)

    val currentSong: Song?
        get() {
            val idx = _currentIndex.value
            val q = _queue.value
            return if (idx in q.indices) q[idx] else null
        }

    init {
        restoreState()
    }

    private fun saveState() {
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Song::class.java)
            val json = moshi.adapter<List<Song>>(listType).toJson(_queue.value)
            sharedPreferences.edit()
                .putString("KEY_QUEUE", json)
                .putInt("KEY_CURRENT_INDEX", _currentIndex.value)
                .apply()
            Log.d(TAG, "saveState success: queue size=${_queue.value.size}, index=${_currentIndex.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save queue state", e)
        }
    }

    private fun restoreState() {
        try {
            val json = sharedPreferences.getString("KEY_QUEUE", null)
            val index = sharedPreferences.getInt("KEY_CURRENT_INDEX", -1)
            if (!json.isNullOrEmpty()) {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Song::class.java)
                val restored: List<Song>? = moshi.adapter<List<Song>>(listType).fromJson(json)
                if (!restored.isNullOrEmpty()) {
                    _queue.value = restored
                    _currentIndex.value = index
                    playedSongIds.clear()
                    playedKeys.clear()
                    restored.forEach {
                        playedSongIds.add(it.id)
                        playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
                    }
                    Log.d(TAG, "restored state success: restored queue of size ${restored.size} at index $index")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore queue state", e)
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        Log.d(TAG, "setQueue: input size=${songs.size}, startIndex=$startIndex")
        val originalTargetSong = if (startIndex in songs.indices) songs[startIndex] else null

        val uniqueSongs = mutableListOf<Song>()
        val seenKeys = mutableSetOf<Pair<String, String>>()
        songs.forEach { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            if (seenKeys.add(key)) {
                uniqueSongs.add(song)
            }
        }
        Log.d(TAG, "setQueue: after deduplication, queue size=${uniqueSongs.size}")

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
        Log.d(TAG, "setQueue: new index=${_currentIndex.value}, song='${currentSong?.name}'")
        saveState()
    }

    fun addToQueue(song: Song) {
        val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
        if (!playedSongIds.contains(song.id) && !playedKeys.contains(key)) {
            Log.d(TAG, "addToQueue: adding song='${song.name}'")
            _queue.value = _queue.value + song
            playedSongIds.add(song.id)
            playedKeys.add(key)
            saveState()
        } else {
            Log.d(TAG, "addToQueue: skipped duplicate song='${song.name}'")
        }
    }

    fun addToQueue(songs: List<Song>) {
        val newSongs = songs.filter { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            !playedSongIds.contains(song.id) && !playedKeys.contains(key)
        }
        Log.d(TAG, "addToQueue (bulk): filtered ${songs.size} down to ${newSongs.size} new unique songs")
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
            Log.d(TAG, "addToQueue (bulk): new total queue size=${_queue.value.size}")
            saveState()
        }
    }

    fun moveToNext(): Song? {
        val nextIndex = _currentIndex.value + 1
        Log.d(TAG, "moveToNext: target index=$nextIndex, queue size=${_queue.value.size}")
        return if (nextIndex < _queue.value.size) {
            _currentIndex.value = nextIndex
            Log.d(TAG, "moveToNext: moved to '${_queue.value[nextIndex].name}'")
            saveState()
            _queue.value[nextIndex]
        } else {
            Log.d(TAG, "moveToNext: already at end of queue")
            null
        }
    }

    fun moveToPrevious(): Song? {
        val prevIndex = _currentIndex.value - 1
        Log.d(TAG, "moveToPrevious: target index=$prevIndex")
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            Log.d(TAG, "moveToPrevious: moved to '${_queue.value[prevIndex].name}'")
            saveState()
            _queue.value[prevIndex]
        } else {
            Log.d(TAG, "moveToPrevious: already at beginning of queue")
            null
        }
    }

    fun jumpTo(index: Int): Song? {
        Log.d(TAG, "jumpTo: index=$index, queue size=${_queue.value.size}")
        return if (index in _queue.value.indices) {
            _currentIndex.value = index
            Log.d(TAG, "jumpTo: moved to '${_queue.value[index].name}'")
            saveState()
            _queue.value[index]
        } else {
            Log.w(TAG, "jumpTo: index out of bounds")
            null
        }
    }

    fun isNearEnd(): Boolean {
        val near = _currentIndex.value >= _queue.value.size - 3
        Log.d(TAG, "isNearEnd: index=${_currentIndex.value}, size=${_queue.value.size}, result=$near")
        return near
    }

    suspend fun loadMoreSuggestions() {
        if (isLoadingSuggestions) {
            Log.d(TAG, "loadMoreSuggestions: already loading, ignore")
            return
        }
        val current = currentSong
        if (current == null) {
            Log.w(TAG, "loadMoreSuggestions: current song is null, cannot load suggestions")
            return
        }
        Log.d(TAG, "loadMoreSuggestions: triggering suggestions request for songId='${current.id}'")
        isLoadingSuggestions = true

        try {
            val result = musicRepository.getSongSuggestions(current.id, 20)
            result.onSuccess { suggestions ->
                Log.d(TAG, "loadMoreSuggestions: successfully retrieved ${suggestions.size} suggestions")
                addToQueue(suggestions)
            }.onFailure { exception ->
                Log.e(TAG, "loadMoreSuggestions: failed to fetch suggestions", exception)
            }
        } finally {
            isLoadingSuggestions = false
        }
    }

    fun clear() {
        Log.d(TAG, "clear: clearing play queue")
        _queue.value = emptyList()
        _currentIndex.value = -1
        playedSongIds.clear()
        playedKeys.clear()
        saveState()
    }

    fun removeAt(index: Int) {
        Log.d(TAG, "removeAt: index=$index, queue size=${_queue.value.size}")
        val mutableQueue = _queue.value.toMutableList()
        if (index in mutableQueue.indices) {
            val removedSong = mutableQueue.removeAt(index)
            _queue.value = mutableQueue
            playedSongIds.remove(removedSong.id)
            playedKeys.remove(removedSong.name.lowercase().trim() to removedSong.primaryArtistNames.lowercase().trim())
            Log.d(TAG, "removeAt: removed song='${removedSong.name}'")
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
                Log.d(TAG, "removeAt: decremented current index to ${_currentIndex.value}")
            } else if (index == _currentIndex.value) {
                Log.d(TAG, "removeAt: removed current song, index remains ${_currentIndex.value}")
            }
            saveState()
        } else {
            Log.w(TAG, "removeAt: index out of bounds")
        }
    }

    companion object {
        private const val TAG = "QueueManager"
    }
}
