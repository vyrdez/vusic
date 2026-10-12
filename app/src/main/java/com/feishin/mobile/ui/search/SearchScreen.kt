package com.feishin.mobile.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.ui.components.SongRow
import com.feishin.mobile.ui.components.rememberSavableListState

@Composable
fun SearchScreen(
    vm: SearchViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val q by vm.query.collectAsState()
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, vm::onQuery,
            label = { Text("Search artists, albums, songs") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            singleLine = true,
        )
        when (val s = ui) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message) { vm.onQuery(q) }
            is UiState.Empty -> EmptyBox(if (q.isBlank()) "Type to search" else "No results")
            is UiState.Data -> {
                val res = s.value
                val artists = res.artists
                val albums = res.albums
                val songs = res.tracks
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = rememberSavableListState(),
                    contentPadding = PaddingValues(bottom = 160.dp),
                ) {
                    if (artists.isNotEmpty()) {
                        item { Text("Artists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                        items(artists, key = { "a${it.id}" }) { a ->
                            Row(Modifier.fillMaxWidth().clickable { onArtist(a.id) }.padding(16.dp, 8.dp)) { Text(a.name) }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item { Text("Albums", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                        items(albums, key = { "b${it.id}" }) { a ->
                            Row(Modifier.fillMaxWidth().clickable { onAlbum(a.id) }.padding(16.dp, 8.dp)) {
                                Text("${a.name} — ${a.artist}")
                            }
                        }
                    }
                    if (songs.isNotEmpty()) {
                        item { Text("Songs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                        items(songs, key = { "s${it.id}" }) { song ->
                            SongRow(song, repo.coverArtUrl(song.coverArt, 128), onClick = {
                                player.playAll(songs, songs.indexOf(song))
                            }, onMore = { player.playNext(song) })
                        }
                    }
                }
            }
        }
    }
}
