package com.metromusic.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.compose.AsyncImage
import com.metromusic.app.data.model.Song

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.VolumeUp

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isPlaying)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else
            androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "SongItemBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBgColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.highQualityImageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Song Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.primaryArtistNames,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        } else if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                    contentDescription = "Download",
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
