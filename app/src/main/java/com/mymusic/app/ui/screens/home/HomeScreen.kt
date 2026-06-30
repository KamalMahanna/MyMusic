@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.screens.home

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import kotlin.math.abs
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                sheetState = playlistSheetState
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
                sheetState = albumSheetState
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.mediumQualityImageUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
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

        HorizontalDivider()

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
                    val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = album.mediumQualityImageUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
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

        HorizontalDivider()

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
                    val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
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
