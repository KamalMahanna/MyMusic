@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.screens.home

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mymusic.app.ui.components.SongListItem
import com.mymusic.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val cardSize = if (isTablet) 160.dp else 120.dp

    // Hoist sheet states so they survive recompositions and avoid animation jank on open
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val albumSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else if (uiState.error != null && uiState.sections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.error}")
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(
                    items = uiState.sections,
                    key = { section -> section.title },
                    contentType = { "section" }
                ) { section ->
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = section.data,
                                key = { item -> item.id },
                                contentType = { "module_item" }
                            ) { item ->
                                Column(
                                    modifier = Modifier
                                        .animateItem()
                                        .width(cardSize)
                                        .clickable {
                                            if (item.type.lowercase().trim() == "song") {
                                                onPlaySong()
                                            }
                                            viewModel.playModuleItem(item)
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.mediumQualityImageUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(cardSize)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                            .border(
                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                                RoundedCornerShape(12.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.subtitle != null) {
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        if (uiState.selectedPlaylist != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedPlaylist() },
                sheetState = playlistSheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                PlaylistSheetContent(
                    playlist = uiState.selectedPlaylist!!,
                    playerViewModel = playerViewModel,
                    onPlaySong = {
                        viewModel.clearSelectedPlaylist()
                        onPlaySong()
                    }
                )
            }
        }

        if (uiState.selectedAlbum != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedAlbum() },
                sheetState = albumSheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                AlbumSheetContent(
                    album = uiState.selectedAlbum!!,
                    playerViewModel = playerViewModel,
                    onPlaySong = {
                        viewModel.clearSelectedAlbum()
                        onPlaySong()
                    }
                )
            }
        }
    }
}

/**
 * Extracted playlist sheet content into a separate composable so that
 * playbackState/downloadState collection is scoped here — not the main HomeScreen.
 * This prevents the entire home LazyColumn from recomposing every 500ms.
 */
@Composable
internal fun PlaylistSheetContent(
    playlist: com.mymusic.app.data.model.Playlist,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        // Extended Background Container: Blurred Image + Glassmorphism Scrim Gradient
        // Measures 1000dp taller than the sheet layout so that the blurred artwork and gradient overlay
        // extend seamlessly past the bottom edge when the sheet is stretched or bounced during fast scrolling.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val extraHeight = 1000.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.maxHeight + extraHeight,
                            maxHeight = constraints.maxHeight + extraHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Blurred Playlist Image Background
            playlist.highQualityImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(radius = 70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }

            // Glassmorphism Dark Gradient Scrim Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag Handle Pill
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = playlist.mediumQualityImageUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = playlist.description ?: "${playlist.songCount ?: 0} Songs"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val songs = playlist.songs
            if (songs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs found.")
                }
            } else {
                val playingIndex = remember(songs, currentPlayingSongId) {
                    songs.indexOfFirst { it.id == currentPlayingSongId }.takeIf { it != -1 }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 2 else 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(songs, index) {
                            {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            }
                        }
                        val onDownloadClick = remember(song) {
                            { playerViewModel.downloadSong(song) }
                        }

                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onDownloadClick = onDownloadClick,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            downloadProgress = downloadStates[song.id]?.progress,
                            index = index,
                            totalCount = songs.size,
                            playingIndex = playingIndex,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracted album sheet content — same rationale as PlaylistSheetContent.
 */
@Composable
internal fun AlbumSheetContent(
    album: com.mymusic.app.data.model.Album,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        // Extended Background Container: Blurred Image + Glassmorphism Scrim Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val extraHeight = 1000.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.maxHeight + extraHeight,
                            maxHeight = constraints.maxHeight + extraHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Blurred Album Image Background
            album.highQualityImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(radius = 70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }

            // Glassmorphism Dark Gradient Scrim Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag Handle Pill
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = album.mediumQualityImageUrl,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = album.description ?: "${album.songCount ?: 0} Songs"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val songs = album.songs
            if (songs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs found.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 2 else 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(songs, index) {
                            {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            }
                        }
                        val onDownloadClick = remember(song) {
                            { playerViewModel.downloadSong(song) }
                        }

                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onDownloadClick = onDownloadClick,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            downloadProgress = downloadStates[song.id]?.progress,
                            index = index,
                            totalCount = songs.size,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}
