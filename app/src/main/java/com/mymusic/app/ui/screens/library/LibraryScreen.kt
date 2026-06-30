package com.mymusic.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloaded songs yet.")
        }
        return
    }

    val songList = remember(songs) { viewModel.getAsSongList() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTablet) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        itemsIndexed(
            items = songList,
            key = { _, song -> song.id }
        ) { index, song ->
            val isPlaying = currentPlayingSongId == song.id
            SongListItem(
                song = song,
                onClick = {
                    playerViewModel.playSongFromList(songList, index)
                    onPlaySong()
                },
                onDownloadClick = {},
                isDownloaded = true,
                isDownloading = false,
                isPlaying = isPlaying,
                index = index,
                totalCount = songList.size,
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

