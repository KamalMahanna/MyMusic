package com.metromusic.app.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.metromusic.app.data.model.DownloadedSong
import com.metromusic.app.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: Flow<List<DownloadedSong>> = _downloadedSongs.asStateFlow()

    val downloadDir: File
        get() {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MyMusic"
            )
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "downloadDir: Created directory MyMusic: $created")
            }
            return dir
        }

    fun getFileForSong(song: Song): File {
        val sanitizedArtist = song.primaryArtistNames.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val sanitizedName = song.name.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val fileName = "$sanitizedName - $sanitizedArtist.m4a"
        val targetFile = File(downloadDir, fileName)
        Log.d(TAG, "getFileForSong: Target file path '${targetFile.absolutePath}'")
        return targetFile
    }

    fun isSongDownloaded(song: Song): Boolean {
        val primaryFile = getFileForSong(song)
        if (primaryFile.exists() && primaryFile.canRead()) {
            Log.d(TAG, "isSongDownloaded: Song '${song.name}' downloaded (.m4a)")
            return true
        }
        // Backward compatibility: check old .mp3 extension
        val sanitizedArtist = song.primaryArtistNames.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val sanitizedName = song.name.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val mp3File = File(downloadDir, "$sanitizedName - $sanitizedArtist.mp3")
        val mp3Exists = mp3File.exists() && mp3File.canRead()
        Log.d(TAG, "isSongDownloaded: Checking .mp3 at '${mp3File.absolutePath}', exists=$mp3Exists")
        return mp3Exists
    }

    suspend fun refreshDownloadedSongs() = withContext(Dispatchers.IO) {
        val dir = downloadDir
        Log.d(TAG, "refreshDownloadedSongs: Scanning directory '${dir.absolutePath}'")
        if (!dir.exists()) {
            Log.w(TAG, "refreshDownloadedSongs: directory does not exist")
            _downloadedSongs.value = emptyList()
            return@withContext
        }

        val files = dir.listFiles()?.filter {
            val ext = it.extension.lowercase()
            (ext == "m4a" || ext == "mp3" || ext == "mp4") && it.canRead()
        } ?: emptyList()

        Log.d(TAG, "refreshDownloadedSongs: Found ${files.size} readable matching audio files")

        val songs = files.map { file ->
            val retriever = android.media.MediaMetadataRetriever()
            var name = file.nameWithoutExtension
            var artist = "Unknown Artist"
            var album: String? = null
            var duration: Int? = null
            var imageUrl: String? = null

            try {
                retriever.setDataSource(file.absolutePath)
                val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durStr?.toIntOrNull()?.let { it / 1000 }

                val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                val fileTitle = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                val fileArtist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"

                name = if (!metaTitle.isNullOrBlank()) metaTitle else fileTitle
                artist = if (!metaArtist.isNullOrBlank() && metaArtist != "Unknown Artist") metaArtist else fileArtist

                if ((artist == "Unknown Artist" || artist.isBlank()) && parts.size > 1) {
                    name = fileTitle
                    artist = fileArtist
                }

                val artworkBytes = retriever.embeddedPicture
                if (artworkBytes != null) {
                    val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
                    if (!cacheFile.exists() || cacheFile.length() != artworkBytes.size.toLong()) {
                        cacheFile.writeBytes(artworkBytes)
                    }
                    imageUrl = cacheFile.absolutePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshDownloadedSongs: Error reading metadata for '${file.name}': ${e.message}", e)
                val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                name = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                artist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Log.e(TAG, "refreshDownloadedSongs: Error releasing MediaMetadataRetriever", e)
                }
            }

            DownloadedSong(
                id = file.absolutePath.hashCode().toString(),
                name = name,
                artist = artist,
                album = album,
                duration = duration,
                filePath = file.absolutePath,
                imageUrl = imageUrl,
                fileSize = file.length()
            )
        }.sortedByDescending { it.filePath }

        Log.d(TAG, "refreshDownloadedSongs: Refreshed ${songs.size} songs in StateFlow")
        _downloadedSongs.value = songs
    }

    fun getCachedArtworkForSong(song: Song): File? {
        val file = getFileForSong(song)
        if (!file.exists() || !file.canRead()) {
            Log.d(TAG, "getCachedArtworkForSong: Song file does not exist or is unreadable")
            return null
        }
        val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
        val cacheExists = cacheFile.exists() && cacheFile.canRead()
        Log.d(TAG, "getCachedArtworkForSong: Checking cached artwork at '${cacheFile.absolutePath}', exists=$cacheExists")
        return if (cacheExists) cacheFile else null
    }

    suspend fun deleteSong(song: DownloadedSong): Boolean = withContext(Dispatchers.IO) {
        val file = File(song.filePath)
        Log.d(TAG, "deleteSong: Deleting file '${file.absolutePath}'")
        val deleted = file.delete()
        Log.d(TAG, "deleteSong: Deletion status: $deleted")
        if (deleted) {
            // Also clean up cached artwork
            val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
            if (cacheFile.exists()) {
                val artworkDeleted = cacheFile.delete()
                Log.d(TAG, "deleteSong: Deleted cached artwork: $artworkDeleted")
            }
            refreshDownloadedSongs()
        }
        deleted
    }

    companion object {
        private const val TAG = "DownloadRepository"
    }
}
