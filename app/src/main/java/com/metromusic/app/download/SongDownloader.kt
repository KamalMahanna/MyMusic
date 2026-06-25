package com.metromusic.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.metromusic.app.data.model.Song
import com.metromusic.app.data.repository.DownloadRepository
import android.util.Log
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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.util.logging.Logger
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

        val tempFile = File(context.cacheDir, "temp_download_${song.id}.m4a")
        if (tempFile.exists()) tempFile.delete()

        try {
            Log.d("SongDownloader", "Downloading to temp file: ${tempFile.absolutePath}")
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

            FileOutputStream(tempFile).use { output ->
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

            Log.d("SongDownloader", "Finished downloading. Writing metadata tags.")
            // Write metadata and download/embed artwork
            writeMetadataTags(tempFile, song)

            Log.d("SongDownloader", "Copying tagged file to final destination: ${file.absolutePath}")
            file.parentFile?.mkdirs()
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()

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
            Log.e("SongDownloader", "Error during download/tagging", e)
            if (tempFile.exists()) tempFile.delete()
            if (file.exists()) file.delete()
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

    private fun writeMetadataTags(file: File, song: Song) {
        try {
            // Suppress verbose jaudiotagger logging
            Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF

            Log.d("SongDownloader", "Reading audio file: ${file.absolutePath}")
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            Log.d("SongDownloader", "Setting metadata: Title=${song.name}, Artist=${song.primaryArtistNames}, Album=${song.album.name}")
            tag.setField(FieldKey.TITLE, song.name)
            tag.setField(FieldKey.ARTIST, song.primaryArtistNames)
            tag.setField(FieldKey.ALBUM, song.album.name ?: "")

            // Download and embed cover art
            val imageBytes = downloadArtworkBytes(song.highQualityImageUrl)
            if (imageBytes != null) {
                try {
                    Log.d("SongDownloader", "Downloading artwork from ${song.highQualityImageUrl} (size=${imageBytes.size} bytes)")
                    // Write image to a temp file in cache for ArtworkFactory
                    val tempImageFile = File(context.cacheDir, "temp_artwork_${song.id}.jpg")
                    tempImageFile.writeBytes(imageBytes)
                    val artwork = ArtworkFactory.createArtworkFromFile(tempImageFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                    tempImageFile.delete()
                    Log.d("SongDownloader", "Artwork successfully embedded in tag")
                } catch (artEx: Exception) {
                    Log.e("SongDownloader", "Failed to write artwork tag", artEx)
                }
            } else {
                Log.w("SongDownloader", "No artwork bytes downloaded")
            }

            Log.d("SongDownloader", "Committing audio file...")
            audioFile.commit()
            Log.d("SongDownloader", "Successfully committed tags to file.")
        } catch (e: Exception) {
            Log.e("SongDownloader", "Failed to write metadata tags", e)
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
