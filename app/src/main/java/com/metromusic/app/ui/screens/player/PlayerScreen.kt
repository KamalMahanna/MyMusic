package com.metromusic.app.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
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
                    val swipeThresholdPx = with(density) { 30.dp.toPx() }
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
            .then(playerGestureModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar (Collapse & Queue toggle buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (showQueue) showQueue = false else onCollapse()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = if (showQueue) "PLAY QUEUE" else "NOW PLAYING",
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

        AnimatedContent(
            targetState = showQueue,
            transitionSpec = {
                if (targetState) {
                    // Slide up & fade in queue, slide up & fade out player
                    (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + 
                     fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } + 
                                      fadeOut(animationSpec = tween(300)))
                } else {
                    // Slide down & fade in player, slide down & fade out queue
                    (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } + 
                     fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + 
                                      fadeOut(animationSpec = tween(300)))
                }
            },
            label = "QueueTransition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) { isQueueVisible ->
            if (isQueueVisible) {
                // Queue View
                QueueView(
                    viewModel = viewModel,
                    onSwipeDown = { showQueue = false }
                )
            } else {
                // Main Player View
                var prevIndex by remember { mutableStateOf(currentIndex) }
                val isNext = currentIndex >= prevIndex

                LaunchedEffect(currentIndex) {
                    prevIndex = currentIndex
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Only Artwork & Info transitions horizontally
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            if (isNext) {
                                (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width } + 
                                 fadeIn(animationSpec = tween(250)))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width } + 
                                        fadeOut(animationSpec = tween(250))
                                    )
                            } else {
                                (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width } + 
                                 fadeIn(animationSpec = tween(250)))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width } + 
                                        fadeOut(animationSpec = tween(250))
                                    )
                            }
                        },
                        label = "SongChangeTransition",
                        modifier = Modifier.weight(1.0f)
                    ) { currentSong ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            // Large Artwork
                            AsyncImage(
                                model = currentSong.highQualityImageUrl,
                                contentDescription = "Artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Song Title & Artist Info
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentSong.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                spacing = MarqueeSpacing(48.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentSong.primaryArtistNames,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Download Button
                                val isDownloading = downloadState.songId == currentSong.id && downloadState.isDownloading
                                val isDownloaded = viewModel.isSongDownloaded(currentSong)
                                
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.downloadSong(currentSong) }) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                            contentDescription = "Download High Quality 320kbps",
                                            modifier = Modifier.size(28.dp),
                                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Progress Slider (Transitions with title)
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.1f))

                    // Controls Row (Static)
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

                    Spacer(modifier = Modifier.weight(0.1f))
                }
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
    val swipeThresholdPx = with(density) { 30.dp.toPx() }

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
        // Title is now managed by the top bar.

        
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
