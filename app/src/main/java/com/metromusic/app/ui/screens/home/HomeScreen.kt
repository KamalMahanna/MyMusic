package com.metromusic.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.metromusic.app.ui.components.SongListItem
import com.metromusic.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaySong: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Hoist sheet states so they survive recompositions and avoid animation jank on open
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val albumSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                contentPadding = PaddingValues(bottom = 80.dp) // padding for MiniPlayer
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
                                        .width(120.dp)
                                        .clickable { viewModel.playModuleItem(item) }
                                ) {
                                    SubcomposeAsyncImage(
                                        model = item.highQualityImageUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        },
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
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

        if (uiState.isItemLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                    .clickable(enabled = false) {}, // Block clicks underneath
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (uiState.selectedPlaylist != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedPlaylist() },
                sheetState = playlistSheetState,
                modifier = Modifier.fillMaxHeight(0.8f)
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
                modifier = Modifier.fillMaxHeight(0.8f)
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
private fun PlaylistSheetContent(
    playlist: com.metromusic.app.data.model.Playlist,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val activeDownloadSongId by playerViewModel.activeDownloadSongId.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = playlist.highQualityImageUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
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

        val songs = playlist.songs ?: emptyList()
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs found.")
            }
        } else {
            LazyColumn(
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
                    val isDownloading = activeDownloadSongId == song.id
                    val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                    val isPlaying = currentPlayingSongId == song.id
                    SongListItem(
                        song = song,
                        onClick = {
                            playerViewModel.playSongFromList(songs, index)
                            onPlaySong()
                        },
                        onDownloadClick = { playerViewModel.downloadSong(song) },
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPlaying = isPlaying,
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
private fun AlbumSheetContent(
    album: com.metromusic.app.data.model.Album,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val activeDownloadSongId by playerViewModel.activeDownloadSongId.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = album.highQualityImageUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
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

        val songs = album.songs ?: emptyList()
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs found.")
            }
        } else {
            LazyColumn(
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
                    val isDownloading = activeDownloadSongId == song.id
                    val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                    val isPlaying = currentPlayingSongId == song.id
                    SongListItem(
                        song = song,
                        onClick = {
                            playerViewModel.playSongFromList(songs, index)
                            onPlaySong()
                        },
                        onDownloadClick = { playerViewModel.downloadSong(song) },
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPlaying = isPlaying,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
