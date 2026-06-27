package com.metromusic.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.metromusic.app.ui.components.SongListItem
import com.metromusic.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    isPlayerExpanded: Boolean,
    onPlaySong: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val activeDownloadSongId by playerViewModel.activeDownloadSongId.collectAsState(initial = null)

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search...") },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filter == SearchFilter.SONGS,
                onClick = { viewModel.onFilterChange(SearchFilter.SONGS) },
                label = { Text("Songs") }
            )
            FilterChip(
                selected = uiState.filter == SearchFilter.ARTISTS,
                onClick = { viewModel.onFilterChange(SearchFilter.ARTISTS) },
                label = { Text("Artists") }
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.error}")
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (uiState.filter == SearchFilter.SONGS) {
                    itemsIndexed(
                        items = uiState.songs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        val isDownloading = activeDownloadSongId == song.id
                        val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        SongListItem(
                            song = song,
                            onClick = {
                                playerViewModel.playSongFromList(uiState.songs, index)
                                onPlaySong()
                            },
                            onDownloadClick = { playerViewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            modifier = Modifier.animateItem()
                        )
                    }
                } else {
                    items(
                        items = uiState.artists,
                        key = { artist -> artist.id }
                    ) { artist ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectArtist(artist.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artist.highQualityImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (uiState.isArtistDetailLoading) {
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

    if (uiState.selectedArtistDetail != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedArtist() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val artistDetail = uiState.selectedArtistDetail!!
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
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artistDetail.highQualityImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artistDetail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = artistDetail.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val followerCount = artistDetail.followerCount ?: artistDetail.fanCount
                        if (followerCount != null) {
                            Text(
                                text = "${formatCount(followerCount)} Followers",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Top Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                val topSongs = artistDetail.topSongs ?: emptyList()
                if (topSongs.isEmpty()) {
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
                        itemsIndexed(
                            items = topSongs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val isDownloading = activeDownloadSongId == song.id
                            val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                            val isPlaying = currentPlayingSongId == song.id
                            SongListItem(
                                song = song,
                                onClick = {
                                    playerViewModel.playSongFromList(topSongs, index)
                                    viewModel.clearSelectedArtist()
                                    onPlaySong()
                                },
                                onDownloadClick = { playerViewModel.downloadSong(song) },
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isPlaying = isPlaying
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return if (count >= 1_000_000) {
        "%.1fM".format(count / 1_000_000f)
    } else if (count >= 1_000) {
        "%.1fK".format(count / 1_000f)
    } else {
        count.toString()
    }
}
