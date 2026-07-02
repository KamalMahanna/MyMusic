@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mymusic.app.data.model.Song

fun groupedSongItemShape(
    index: Int,
    totalCount: Int,
    playingIndex: Int? = null
): RoundedCornerShape {
    if (playingIndex != null && playingIndex in 0 until totalCount) {
        return when {
            index == playingIndex -> RoundedCornerShape(20.dp)
            index == playingIndex - 1 -> RoundedCornerShape(
                topStart = if (index == 0) 20.dp else 4.dp,
                topEnd = if (index == 0) 20.dp else 4.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            )
            index == playingIndex + 1 -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (index == totalCount - 1) 20.dp else 4.dp,
                bottomEnd = if (index == totalCount - 1) 20.dp else 4.dp
            )
            index == 0 -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 4.dp
            )
            index == totalCount - 1 -> RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 4.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            )
            else -> RoundedCornerShape(4.dp)
        }
    }

    return when {
        totalCount <= 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 4.dp,
            bottomEnd = 4.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
        else -> RoundedCornerShape(4.dp)
    }
}

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    index: Int = 0,
    totalCount: Int = 1,
    playingIndex: Int? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemScale"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isPlaying)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 300),
        label = "SongItemBg"
    )

    val itemShape = remember(index, totalCount, playingIndex) {
        groupedSongItemShape(index, totalCount, playingIndex)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(itemShape)
            .background(animatedBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.mediumQualityImageUrl,
            contentDescription = "Song Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.primaryArtistNames,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (trailingContent != null) {
            trailingContent()
        } else if (isPlaying) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        } else if (isDownloading) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(
                    progress = { downloadProgress ?: 0f },
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDownloadClick()
            }) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    contentDescription = "Download",
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

