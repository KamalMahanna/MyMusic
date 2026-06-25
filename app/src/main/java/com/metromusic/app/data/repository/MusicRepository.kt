package com.metromusic.app.data.repository

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
        try {
            val response = api.searchSongs(query, page, limit)
            if (response.success && response.data != null) {
                Result.success(response.data.clean())
            } else {
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0, limit: Int = 50): Result<SearchArtistResult> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchArtists(query, page, limit)
            if (response.success && response.data != null) {
                Result.success(response.data.clean())
            } else {
                Result.failure(Exception("Artist search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSongById(id: String): Result<Song> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSongById(id)
            if (response.success && response.data != null && response.data.isNotEmpty()) {
                Result.success(response.data.first().clean())
            } else {
                Result.failure(Exception("Song not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSongSuggestions(songId: String, limit: Int = 20): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSongSuggestions(songId, limit)
            if (response.success && response.data != null) {
                Result.success(response.data.map { it.clean() })
            } else {
                Result.failure(Exception("No suggestions found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistById(id: String): Result<ArtistDetail> = withContext(Dispatchers.IO) {
        try {
            val response = api.getArtistById(id)
            if (response.success && response.data != null) {
                Result.success(response.data.clean())
            } else {
                Result.failure(Exception("Artist not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumById(id: String): Result<Album> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlbumById(id)
            if (response.success && response.data != null) {
                Result.success(response.data.clean())
            } else {
                Result.failure(Exception("Album not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylistById(id: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPlaylistById(id)
            if (response.success && response.data != null) {
                Result.success(response.data.clean())
            } else {
                Result.failure(Exception("Playlist not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModules(): Result<List<ModuleSection>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getModules()
            if (response.success && response.data != null) {
                Result.success(response.data.map { it.clean() })
            } else {
                Result.failure(Exception("Failed to load modules"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrending(): Result<List<ModuleItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrending()
            if (response.success && response.data != null) {
                Result.success(response.data.map { it.clean() })
            } else {
                Result.failure(Exception("Failed to load trending"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
