package com.feishin.mobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.SongRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    PullToRefreshBox(isRefreshing = ui is UiState.Loading, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = ui) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, vm::refresh)
            is UiState.Empty -> EmptyBox("No music found on this server")
            is UiState.Data -> HomeContent(s.value, player, repo, messenger, onAlbum)
        }
    }
}

@Composable
private fun HomeContent(data: HomeData, player: PlayerManager, repo: MusicDataRepository, messenger: UiMessenger, onAlbum: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberSavableListState(),
        contentPadding = PaddingValues(bottom = 160.dp),
    ) {
        if (data.recent.isNotEmpty()) item { Section("Recently played", data.recent, repo, onAlbum, player) }
        if (data.newest.isNotEmpty()) item { Section("Newest", data.newest, repo, onAlbum, player) }
        if (data.frequent.isNotEmpty()) item { Section("Most played", data.frequent, repo, onAlbum, player) }
        if (data.random.isNotEmpty()) item { Section("Random", data.random, repo, onAlbum, player) }
        if (data.starredSongs.isNotEmpty()) {
            item {
                Text("Starred songs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp))
            }
            items(data.starredSongs.take(20), key = { it.id }) { song ->
                SongRow(song, repo.coverArtUrl(song.coverArt, 128), onClick = {
                    player.playAll(data.starredSongs, data.starredSongs.indexOf(song))
                }, player = player, messenger = messenger)
            }
        }
    }
}

@Composable
private fun Section(title: String, albums: List<Album>, repo: MusicDataRepository, onAlbum: (String) -> Unit, player: PlayerManager) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(albums, key = { it.id }) { a ->
                AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) }, modifier = Modifier.width(140.dp))
            }
        }
    }
}

fun <T> itemsPlaceholder(): List<T> = emptyList()
