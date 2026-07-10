package com.mymusic.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.mymusic.app.ui.screens.player.PlayerViewModel
import com.mymusic.app.ui.components.SongListItem

@Composable
fun LibraryScreen(
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val songs by viewModel.downloadedSongs.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloaded songs yet.")
        }
        return
    }

    val songList = remember(songs) { viewModel.getAsSongList() }
    val filteredSongList = remember(songList, searchQuery) {
        if (searchQuery.isBlank()) {
            songList
        } else {
            songList.filter { song ->
                song.name.contains(searchQuery, ignoreCase = true) ||
                song.artists.primary.any { artist -> artist.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }
    
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val playingIndex = remember(filteredSongList, currentPlayingSongId) {
        filteredSongList.indexOfFirst { it.id == currentPlayingSongId }.takeIf { it != -1 }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTablet) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val focusRequester = remember { FocusRequester() }
            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search downloads...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearching = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close search"
                            )
                        }
                    },
                    singleLine = true
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = { isSearching = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search downloads",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        if (filteredSongList.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching downloads found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = filteredSongList,
                key = { _, song -> song.id }
            ) { index, song ->
                val isPlaying = currentPlayingSongId == song.id
                SongListItem(
                    song = song,
                    onClick = {
                        val originalIndex = songList.indexOfFirst { it.id == song.id }
                        playerViewModel.playSongFromList(songList, if (originalIndex != -1) originalIndex else index)
                        onPlaySong()
                    },
                    onDownloadClick = {},
                    isDownloaded = true,
                    isDownloading = false,
                    isPlaying = isPlaying,
                    index = index,
                    totalCount = filteredSongList.size,
                    playingIndex = playingIndex,
                    trailingContent = {
                        IconButton(onClick = {
                            songs.find { it.id == song.id }?.let { viewModel.deleteSong(it) }
                        }) {
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
}

