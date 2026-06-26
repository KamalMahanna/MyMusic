package com.metromusic.app.data.repository

import android.util.Log
import com.metromusic.app.data.api.SaavnApi
import com.metromusic.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SaavnApi
) {
    suspend fun searchSongs(query: String, page: Int = 0, limit: Int = 50): Result<SearchSongResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchSongs(query='$query', page=$page, limit=$limit)")
        try {
            val response = api.searchSongs(query, page, limit)
            if (response.success && response.data != null) {
                Log.d(TAG, "searchSongs success: found ${response.data.results.size} songs")
                Result.success(response.data.clean())
            } else {
                Log.e(TAG, "searchSongs failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchSongs error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0, limit: Int = 50): Result<SearchArtistResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchArtists(query='$query', page=$page, limit=$limit)")
        try {
            val response = api.searchArtists(query, page, limit)
            if (response.success && response.data != null) {
                Log.d(TAG, "searchArtists success: found ${response.data.results.size} artists")
                Result.success(response.data.clean())
            } else {
                Log.e(TAG, "searchArtists failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Artist search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchArtists error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSongById(id: String): Result<Song> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getSongById(id='$id')")
        try {
            val response = api.getSongById(id)
            if (response.success && response.data != null && response.data.isNotEmpty()) {
                Log.d(TAG, "getSongById success: loaded song name='${response.data.first().name}'")
                Result.success(response.data.first().clean())
            } else {
                Log.e(TAG, "getSongById failed: success=${response.success}, empty/null data: ${response.data.isNullOrEmpty()}")
                Result.failure(Exception("Song not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSongById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSongSuggestions(songId: String, limit: Int = 20): Result<List<Song>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getSongSuggestions(songId='$songId', limit=$limit)")
        try {
            val response = api.getSongSuggestions(songId, limit)
            if (response.success && response.data != null) {
                Log.d(TAG, "getSongSuggestions success: loaded ${response.data.size} suggestions")
                Result.success(response.data.map { it.clean() })
            } else {
                Log.e(TAG, "getSongSuggestions failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("No suggestions found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSongSuggestions error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getArtistById(id: String): Result<ArtistDetail> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getArtistById(id='$id')")
        try {
            val response = api.getArtistById(id)
            if (response.success && response.data != null) {
                Log.d(TAG, "getArtistById success: loaded artist='${response.data.name}'")
                Result.success(response.data.clean())
            } else {
                Log.e(TAG, "getArtistById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Artist not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getArtistById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getAlbumById(id: String): Result<Album> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAlbumById(id='$id')")
        try {
            val response = api.getAlbumById(id)
            if (response.success && response.data != null) {
                Log.d(TAG, "getAlbumById success: loaded album='${response.data.name}'")
                Result.success(response.data.clean())
            } else {
                Log.e(TAG, "getAlbumById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Album not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAlbumById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistById(id: String, limit: Int = 1000): Result<Playlist> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getPlaylistById(id='$id', limit=$limit)")
        try {
            val response = api.getPlaylistById(id, limit = limit)
            if (response.success && response.data != null) {
                Log.d(TAG, "getPlaylistById success: loaded playlist='${response.data.name}', songs=${response.data.songs?.size ?: 0}")
                Result.success(response.data.clean())
            } else {
                Log.e(TAG, "getPlaylistById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Playlist not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getModules(): Result<List<ModuleSection>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getModules()")
        try {
            val response = api.getModules()
            if (response.success && response.data != null) {
                Log.d(TAG, "getModules success: loaded ${response.data.size} sections")
                Result.success(response.data.map { it.clean() })
            } else {
                Log.e(TAG, "getModules failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Failed to load modules"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getModules error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTrending(): Result<List<ModuleItem>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getTrending()")
        try {
            val response = api.getTrending()
            if (response.success && response.data != null) {
                Log.d(TAG, "getTrending success: loaded ${response.data.size} items")
                Result.success(response.data.map { it.clean() })
            } else {
                Log.e(TAG, "getTrending failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Failed to load trending"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTrending error: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}
