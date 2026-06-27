package com.metromusic.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metromusic.app.ui.screens.player.PlayerViewModel

@Composable
fun SongDownloadIndicator(
    songId: String,
    playerViewModel: PlayerViewModel,
    isDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadState by playerViewModel.downloadState.collectAsState()
    val isDownloading = downloadState.songId == songId && downloadState.isDownloading

    if (isDownloading) {
        CircularProgressIndicator(
            progress = { downloadState.progress },
            modifier = modifier.size(24.dp),
            strokeWidth = 2.5.dp
        )
    } else {
        IconButton(
            onClick = onDownloadClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                contentDescription = "Download",
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
