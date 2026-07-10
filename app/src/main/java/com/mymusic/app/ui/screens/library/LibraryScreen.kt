package com.mymusic.app.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mymusic.app.ui.screens.player.PlayerViewModel
import com.mymusic.app.ui.components.SongListItem

@Composable
fun LibraryScreen(
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    isOffline: Boolean = false,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val songs by viewModel.filteredDownloadedSongs.collectAsState()
    val allSongs by viewModel.downloadedSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Clear search when going back online
    LaunchedEffect(isOffline) {
        if (!isOffline) {
            isSearchActive = false
            viewModel.clearSearch()
        }
    }

    // Auto-focus search field when it becomes visible
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (allSongs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloaded songs yet.")
        }
        return
    }

    val songList = remember(songs) { viewModel.getAsSongList(songs) }
    // Build the full (unfiltered) song list for the play queue so all downloaded
    // songs are queued, not just the currently visible search results.
    val fullSongList = remember(allSongs) { viewModel.getAsSongList(allSongs) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val playingIndex = remember(songList, currentPlayingSongId) {
        songList.indexOfFirst { it.id == currentPlayingSongId }.takeIf { it != -1 }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTablet) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // Header with "Downloads" title and optional search icon/bar
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search bar (expands from right, replaces title)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search downloads...") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // Title (hidden when search is active)
                AnimatedVisibility(
                    visible = !isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                // Search icon — only visible when offline
                AnimatedVisibility(
                    visible = isOffline,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                viewModel.clearSearch()
                            } else {
                                isSearchActive = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearchActive) "Close search" else "Search downloads",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Empty search results message
        if (songs.isEmpty() && searchQuery.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(
            items = songList,
            key = { _, song -> song.id }
        ) { index, song ->
            val isPlaying = currentPlayingSongId == song.id
            SongListItem(
                song = song,
                onClick = {
                    // Find this song's index in the full (unfiltered) list so the
                    // entire library is queued, not just the search results.
                    val fullIndex = fullSongList.indexOfFirst { it.id == song.id }
                        .takeIf { it != -1 } ?: index
                    playerViewModel.playSongFromList(fullSongList, fullIndex)
                    onPlaySong()
                },
                onDownloadClick = {},
                isDownloaded = true,
                isDownloading = false,
                isPlaying = isPlaying,
                index = index,
                totalCount = songList.size,
                playingIndex = playingIndex,
                trailingContent = {
                    IconButton(onClick = { viewModel.deleteSong(songs[index]) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.animateItem()
            )
        }
    }
}
