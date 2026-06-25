package com.metromusic.app.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.metromusic.app.data.model.Song
import com.metromusic.app.player.QueueManager
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val song = playbackState.currentSong ?: return
    val currentIndex by viewModel.queueIndex.collectAsState()

    var showQueue by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val playerGestureModifier = if (!showQueue) {
        Modifier.pointerInput(currentIndex) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY += dragAmount.y
                },
                onDragEnd = {
                    val swipeThresholdPx = with(density) { 80.dp.toPx() }
                    if (kotlin.math.abs(dragOffsetX) > kotlin.math.abs(dragOffsetY)) {
                        // Horizontal Swipe
                        if (dragOffsetX < -swipeThresholdPx) {
                            // Swipe Left -> Play Next
                            viewModel.playNext()
                        } else if (dragOffsetX > swipeThresholdPx) {
                            // Swipe Right -> Play Previous or Replay from start
                            if (currentIndex == 0) {
                                viewModel.seekTo(0)
                            } else {
                                viewModel.playPrevious()
                            }
                        }
                    } else {
                        // Vertical Swipe
                        if (dragOffsetY < -swipeThresholdPx) {
                            // Swipe Up -> Open Play Queue
                            showQueue = true
                        } else if (dragOffsetY > swipeThresholdPx) {
                            // Swipe Down -> Collapse Player
                            onCollapse()
                        }
                    }
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                },
                onDragCancel = {
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                }
            )
        }
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .then(playerGestureModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar (Collapse & Queue toggle buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse Player",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(
                    imageVector = if (showQueue) Icons.Default.MusicNote else Icons.Default.QueueMusic,
                    contentDescription = "Toggle Queue",
                    modifier = Modifier.size(28.dp),
                    tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showQueue) {
            // Queue View
            QueueView(
                viewModel = viewModel,
                onSwipeDown = { showQueue = false }
            )
        } else {
            // Main Player View
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Large Artwork
                AsyncImage(
                    model = song.highQualityImageUrl,
                    contentDescription = "Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                )

                Spacer(modifier = Modifier.weight(1f))

                // Song Title & Artist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.primaryArtistNames,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Download Button
                    val isDownloading = downloadState.songId == song.id && downloadState.isDownloading
                    val isDownloaded = viewModel.isSongDownloaded(song)
                    
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.downloadSong(song) }) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = "Download High Quality 320kbps",
                                modifier = Modifier.size(32.dp),
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Slider
                val progress = if (playbackState.duration > 0) {
                    playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
                } else 0f

                var sliderPosition by remember { mutableStateOf<Float?>(null) }

                Slider(
                    value = sliderPosition ?: progress,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        sliderPosition?.let { pos ->
                            viewModel.seekTo((pos * playbackState.duration).toLong())
                        }
                        sliderPosition = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Progress Time Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillis(playbackState.currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMillis(playbackState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { viewModel.togglePlayPause() },
                        shape = RoundedCornerShape(100.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QueueView(
    viewModel: PlayerViewModel,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.queueIndex.collectAsState()
    val listState = rememberLazyListState()

    // Detect if we are scrolled to the top
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    // Intercept scroll-down drags when list is scrolled to the top
    val nestedScrollConnection = remember(isAtTop) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && isAtTop) {
                    dragOffsetY += available.y
                    if (dragOffsetY > swipeThresholdPx) {
                        onSwipeDown()
                        dragOffsetY = 0f
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0) {
                    dragOffsetY = 0f
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        Text(
            text = "Play Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(queue) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { viewModel.playQueueIndex(index) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.highQualityImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.primaryArtistNames,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index == currentIndex) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { viewModel.removeFromQueue(index) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
