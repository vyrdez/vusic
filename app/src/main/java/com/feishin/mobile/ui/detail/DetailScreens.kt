package com.feishin.mobile.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.ui.components.SongRow
import com.feishin.mobile.ui.components.formatDuration
import com.feishin.mobile.ui.components.rememberSavableListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    vm: AlbumDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    onArtist: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Album not found")
        is UiState.Data -> {
            val a = s.value
            val starred = a.starred
            val refreshing by vm.refreshing.collectAsState()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = rememberSavableListState(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            repo.coverArtUrl(a.coverArt, 600), a.name,
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(a.name, style = MaterialTheme.typography.headlineSmall)
                        Text(a.artist, style = MaterialTheme.typography.bodyMedium)
                        Text("${a.songCount} songs · ${formatDuration(a.durationSec)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = { player.playAll(a.tracks) }) {
                                Icon(Icons.Default.PlayArrow, null); Text("Play")
                            }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { player.playAll(a.tracks, shuffle = true) }) {
                                Icon(Icons.Default.Shuffle, null); Text("Shuffle")
                            }
                            IconButton(onClick = { vm.toggleStar(starred) }) {
                                Icon(if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                            }
                        }
                        OutlinedButton(onClick = { player.addAllToEnd(a.tracks) }) { Text("Add to queue") }
                    }
                }
                items(a.tracks, key = { it.id }) { song ->
                    SongRow(song, repo.coverArtUrl(song.coverArt, 128), onClick = {
                        player.playAll(a.tracks, a.tracks.indexOf(song))
                    }, onMore = { player.playNext(song) })
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    vm: ArtistDetailViewModel = hiltViewModel(),
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Artist not found")
        is UiState.Data -> {
            val a = s.value
            val starred = a.starred
            val refreshing by vm.refreshing.collectAsState()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = rememberSavableListState(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.headlineSmall)
                            Text("${a.albumCount} albums", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.toggleStar(starred) }) {
                            Icon(
                                if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                            )
                        }
                    }
                }
                items(a.albums, key = { it.id }) { al ->
                    Row(Modifier.fillMaxWidth().clickable { onAlbum(al.id) }.padding(16.dp, 8.dp)) {
                        Text("${al.name} (${al.year ?: "—"})")
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    vm: PlaylistDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Playlist not found")
        is UiState.Data -> {
            val p = s.value
            val refreshing by vm.refreshing.collectAsState()
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = rememberSavableListState(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${p.songCount} songs", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(onClick = { player.playAll(p.tracks) }) { Text("Play") }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { player.playAll(p.tracks, shuffle = true) }) { Text("Shuffle") }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { vm.delete(onBack) }) { Text("Delete") }
                        }
                    }
                }
                items(p.tracks.size, key = { i -> p.tracks[i].id + "_$i" }) { i ->
                    val song = p.tracks[i]
                    SongRow(song, repo.coverArtUrl(song.coverArt, 128), onClick = {
                        player.playAll(p.tracks, i)
                    }, onMore = { vm.removeAt(i) })
                }
            }
            }
        }
    }
}
