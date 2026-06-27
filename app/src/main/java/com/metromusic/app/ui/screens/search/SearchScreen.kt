package com.metromusic.app.ui.screens.search

import com.metromusic.app.ui.components.rememberFrictionFlingBehavior

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

import com.metromusic.app.ui.components.SongListItem
import com.metromusic.app.ui.screens.player.PlayerViewModel
import com.metromusic.app.ui.screens.home.PlaylistSheetContent
import com.metromusic.app.ui.screens.home.AlbumSheetContent

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
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

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

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.error}")
            }
        } else {
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTablet) 4 else 2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                flingBehavior = rememberFrictionFlingBehavior()
            ) {
                // 1. Songs Section
                if (uiState.songs.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Songs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    itemsIndexed(
                        items = uiState.songs,
                        key = { _, song -> song.id },
                        span = { _, _ -> GridItemSpan(2) } // 2 columns on tablet, 1 column on mobile
                    ) { index, song ->
                        val isDownloading = activeDownloadSongId == song.id
                        val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(uiState.songs, index) {
                            {
                                playerViewModel.playSongFromList(uiState.songs, index)
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
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // 2. Albums Section
                if (uiState.albums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(
                        items = uiState.albums,
                        key = { album -> album.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { album ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectAlbum(album.id) }
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = album.mediumQualityImageUrl,
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 3. Artists Section
                if (uiState.artists.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Artists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(
                        items = uiState.artists,
                        key = { artist -> artist.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { artist ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectArtist(artist.id) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = artist.mediumQualityImageUrl,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 4. Playlists Section
                if (uiState.playlists.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(
                        items = uiState.playlists,
                        key = { playlist -> playlist.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { playlist ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectPlaylist(playlist.id) }
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = playlist.mediumQualityImageUrl,
                                contentDescription = playlist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isArtistDetailLoading || uiState.isPlaylistDetailLoading || uiState.isAlbumDetailLoading) {
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
                        model = artistDetail.mediumQualityImageUrl,
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isTablet) 2 else 1),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        flingBehavior = rememberFrictionFlingBehavior()
                    ) {
                        itemsIndexed(
                            items = topSongs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val isDownloading = activeDownloadSongId == song.id
                            val isDownloaded = remember(downloadedSongs, song.id) { playerViewModel.isSongDownloaded(song) }
                            val isPlaying = currentPlayingSongId == song.id
                            
                            val onClick = remember(topSongs, index) {
                                {
                                    playerViewModel.playSongFromList(topSongs, index)
                                    viewModel.clearSelectedArtist()
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
                                isPlaying = isPlaying
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.selectedPlaylist != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedPlaylist() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

private fun formatCount(count: Int): String {
    return if (count >= 1_000_000) {
        "%.1fM".format(count / 1_000_000f)
    } else if (count >= 1_000) {
        "%.1fK".format(count / 1_000f)
    } else {
        count.toString()
    }
}
