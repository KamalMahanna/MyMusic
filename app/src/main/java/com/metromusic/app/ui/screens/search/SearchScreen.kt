package com.metromusic.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.metromusic.app.ui.components.SongListItem
import com.metromusic.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadState by playerViewModel.downloadState.collectAsState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (uiState.filter == SearchFilter.SONGS) {
                    items(uiState.songs) { song ->
                        val isDownloading = downloadState.songId == song.id && downloadState.isDownloading
                        val isDownloaded = playerViewModel.isSongDownloaded(song)
                        SongListItem(
                            song = song,
                            onClick = { playerViewModel.playSongFromList(uiState.songs, uiState.songs.indexOf(song)) },
                            onDownloadClick = { playerViewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading
                        )
                    }
                } else {
                    items(uiState.artists) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* TODO: Artist detail */ }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = artist.highQualityImageUrl,
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
}
