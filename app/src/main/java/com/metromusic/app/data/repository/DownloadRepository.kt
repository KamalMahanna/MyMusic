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
            val parts = file.nameWithoutExtension.split(" - ", limit = 2)
            val name = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
            val artist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"
            DownloadedSong(
                id = file.absolutePath.hashCode().toString(),
                name = name,
                artist = artist,
                album = null,
                duration = null,
                filePath = file.absolutePath,
                imageUrl = null,
                fileSize = file.length()
            )
        }?.sortedByDescending { it.filePath } ?: emptyList()

        _downloadedSongs.value = songs
    }

    suspend fun deleteSong(song: DownloadedSong): Boolean = withContext(Dispatchers.IO) {
        val file = File(song.filePath)
        val deleted = file.delete()
        if (deleted) refreshDownloadedSongs()
        deleted
    }
}
