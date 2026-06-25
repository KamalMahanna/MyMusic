package com.metromusic.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.File
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v23Tag
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val songId: String = "",
    val songName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

@Singleton
class SongDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music download progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun downloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = song.highQualityDownloadUrl ?: return@withContext false
        val file = downloadRepository.getFileForSong(song)

        if (file.exists()) return@withContext true

        _downloadState.value = DownloadState(
            songId = song.id,
            songName = song.name,
            isDownloading = true
        )

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    error = "Download failed: ${response.code}"
                )
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            file.parentFile?.mkdirs()

            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else 0f

                        _downloadState.value = _downloadState.value.copy(progress = progress)
                        updateNotification(song.name, (progress * 100).toInt())
                    }
                }
            }

            // Write ID3 metadata and download/embed artwork
            writeId3Tags(file, song)

            _downloadState.value = DownloadState(
                songId = song.id,
                songName = song.name,
                progress = 1f,
                isDownloading = false,
                isComplete = true
            )

            showCompleteNotification(song.name)
            downloadRepository.refreshDownloadedSongs()
            true
        } catch (e: Exception) {
            file.delete()
            _downloadState.value = DownloadState(
                songId = song.id,
                songName = song.name,
                isDownloading = false,
                error = e.message
            )
            false
        }
    }

    private fun updateNotification(songName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(songName)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(songName: String) {
        notificationManager.cancel(NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(songName)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun writeId3Tags(file: File, song: Song) {
        try {
            val imageBytes = downloadArtworkBytes(song.highQualityImageUrl)
            
            val mp3File = Mp3File(file.absolutePath)
            val tag = if (mp3File.hasId3v2Tag()) {
                mp3File.id3v2Tag
            } else {
                val newTag = ID3v23Tag()
                mp3File.id3v2Tag = newTag
                newTag
            }

            tag.title = song.name
            tag.artist = song.primaryArtistNames
            tag.album = song.album.name ?: ""
            
            if (imageBytes != null) {
                val mimeType = if (song.highQualityImageUrl?.endsWith(".png", ignoreCase = true) == true) {
                    "image/png"
                } else {
                    "image/jpeg"
                }
                tag.setAlbumImage(imageBytes, mimeType)
            }

            val tempFile = File(file.absolutePath + ".tmp")
            mp3File.save(tempFile.absolutePath)

            if (tempFile.exists()) {
                tempFile.inputStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) return null
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CHANNEL_ID = "metro_music_downloads"
        private const val NOTIFICATION_ID = 1001
    }
}
