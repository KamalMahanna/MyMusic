@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mymusic.app.ui.screens.player.PlayerViewModel

@Composable
fun SongDownloadIndicator(
    songId: String,
    playerViewModel: PlayerViewModel,
    isDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val state = downloadStates[songId]
    val isDownloading = state?.isDownloading == true

    if (isDownloading) {
        Box(
            modifier = modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator(
                progress = { state?.progress ?: 0f },
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        IconButton(
            onClick = onDownloadClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                contentDescription = "Download",
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
