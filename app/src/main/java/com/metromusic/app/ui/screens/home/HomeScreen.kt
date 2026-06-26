package com.metromusic.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.metromusic.app.ui.components.SongListItem
import com.metromusic.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadState by playerViewModel.downloadState.collectAsState()

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // padding for MiniPlayer
            ) {
                items(uiState.sections) { section ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(section.data) { item ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable { viewModel.playModuleItem(item) }
                                ) {
                                    AsyncImage(
                                        model = item.highQualityImageUrl,
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
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
                sheetState = rememberModalBottomSheetState()
            ) {
                val playlist = uiState.selectedPlaylist!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playlist.highQualityImageUrl,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
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
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(songs) { index, song ->
                                val isDownloading = downloadState.songId == song.id && downloadState.isDownloading
                                val isDownloaded = playerViewModel.isSongDownloaded(song)
                                SongListItem(
                                    song = song,
                                    onClick = { playerViewModel.playSongFromList(songs, index) },
                                    onDownloadClick = { playerViewModel.downloadSong(song) },
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.selectedAlbum != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedAlbum() },
                sheetState = rememberModalBottomSheetState()
            ) {
                val album = uiState.selectedAlbum!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = album.highQualityImageUrl,
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
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
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(songs) { index, song ->
                                val isDownloading = downloadState.songId == song.id && downloadState.isDownloading
                                val isDownloaded = playerViewModel.isSongDownloaded(song)
                                SongListItem(
                                    song = song,
                                    onClick = { playerViewModel.playSongFromList(songs, index) },
                                    onDownloadClick = { playerViewModel.downloadSong(song) },
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
