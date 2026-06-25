package com.metromusic.app.data.repository

import android.content.Context
import android.os.Environment
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
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getFileForSong(song: Song): File {
        val sanitizedArtist = song.primaryArtistNames.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val sanitizedName = song.name.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val fileName = "$sanitizedName - $sanitizedArtist.mp3"
        return File(downloadDir, fileName)
    }

    fun isSongDownloaded(song: Song): Boolean {
        return getFileForSong(song).exists()
    }

    suspend fun refreshDownloadedSongs() = withContext(Dispatchers.IO) {
        val dir = downloadDir
        if (!dir.exists()) {
            _downloadedSongs.value = emptyList()
            return@withContext
        }

        val songs = dir.listFiles()?.filter { it.extension.equals("mp3", ignoreCase = true) }?.map { file ->
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
                e.printStackTrace()
                val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                name = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                artist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
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
        }?.sortedByDescending { it.filePath } ?: emptyList()

        _downloadedSongs.value = songs
    }

    fun getCachedArtworkForSong(song: Song): File? {
        val file = getFileForSong(song)
        if (!file.exists()) return null
        val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
        return if (cacheFile.exists()) cacheFile else null
    }

    suspend fun deleteSong(song: DownloadedSong): Boolean = withContext(Dispatchers.IO) {
        val file = File(song.filePath)
        val deleted = file.delete()
        if (deleted) {
            // Also clean up cached artwork
            val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
            if (cacheFile.exists()) cacheFile.delete()
            refreshDownloadedSongs()
        }
        deleted
    }
}
