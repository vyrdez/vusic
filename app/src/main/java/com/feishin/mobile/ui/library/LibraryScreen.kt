package com.feishin.mobile.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.ArtistCard
import com.feishin.mobile.ui.components.SongRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableGridState
import com.feishin.mobile.ui.components.rememberSavableListState

@Composable
fun LibraryScreen(
    vm: LibraryViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onGenre: (String) -> Unit,
) {
    val tab by vm.tab.collectAsState()
    val grid by vm.grid.collectAsState()
    val sort by vm.sort.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.weight(1f), edgePadding = 8.dp) {
                LibraryTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { vm.selectTab(t) }, text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            IconButton(onClick = { vm.toggleGrid() }) {
                Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Toggle view")
            }
            IconButton(onClick = { sortMenu = true }) { Text("⇅", style = MaterialTheme.typography.titleMedium) }
            DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = { vm.setSort(s); sortMenu = false },
                    )
                }
            }
        }
        LibraryPullToRefresh(vm, tab) {
            when (tab) {
            LibraryTab.ALBUMS -> {
                val ui by vm.albums.collectAsState()
                when (val s = ui) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(s.message) { vm.loadAlbums(true) }
                    is UiState.Empty -> EmptyBox("No albums")
                    is UiState.Data -> if (grid) {
                        val gridState = rememberSavableGridState()
                        val atEnd = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?.let { it >= s.value.size - 6 } == true
                        LaunchedEffect(atEnd, s.value.size) { if (atEnd) vm.loadAlbums() }
                        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), state = gridState, contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 160.dp)) {
                            items(s.value, key = { it.id }) { a ->
                                AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) })
                            }
                        }
                    } else {
                        val listState = rememberSavableListState()
                        val atEnd = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?.let { it >= s.value.size - 6 } == true
                        LaunchedEffect(atEnd, s.value.size) { if (atEnd) vm.loadAlbums() }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 160.dp),
                        ) {
                            items(s.value, key = { it.id }) { a ->
                                Row(Modifier.fillMaxWidth().clickable { onAlbum(a.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(a.artist, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(a.year?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            LibraryTab.ARTISTS -> {
                val ui by vm.artists.collectAsState()
                when (val s = ui) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(s.message, vm::refresh)
                    is UiState.Empty -> EmptyBox("No artists")
                    is UiState.Data -> if (grid) {
                        LazyVerticalGrid(
                            GridCells.Fixed(2),
                            Modifier.fillMaxSize(),
                            state = rememberSavableGridState(),
                            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 160.dp),
                        ) {
                            items(s.value, key = { it.id }) { a ->
                                ArtistCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onArtist(a.id) })
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = rememberSavableListState(),
                            contentPadding = PaddingValues(bottom = 160.dp),
                        ) {
                            items(s.value, key = { it.id }) { a ->
                                Row(Modifier.fillMaxWidth().clickable { onArtist(a.id) }.padding(16.dp, 12.dp)) {
                                    Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text("${a.albumCount}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            LibraryTab.GENRES -> {
                val ui by vm.genres.collectAsState()
                when (val s = ui) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(s.message, vm::refresh)
                    is UiState.Empty -> EmptyBox("No genres")
                    is UiState.Data -> LazyColumn(
                        Modifier.fillMaxSize(),
                        state = rememberSavableListState(),
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        items(s.value, key = { it.name }) { g ->
                            Row(Modifier.fillMaxWidth().clickable { onGenre(g.name) }.padding(16.dp, 12.dp)) {
                                Text(g.name, modifier = Modifier.weight(1f))
                                Text("${g.albumCount} albums", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            LibraryTab.PLAYLISTS -> {
                val ui by vm.playlists.collectAsState()
                when (val s = ui) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(s.message, vm::refresh)
                    is UiState.Empty -> EmptyBox("No playlists")
                    is UiState.Data -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(s.value, key = { it.id }) { p ->
                            Column(Modifier.fillMaxWidth().clickable { onPlaylist(p.id) }.padding(16.dp, 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text("${p.songCount} songs", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            LibraryTab.FAVORITES -> {
                val ui by vm.favs.collectAsState()
                when (val s = ui) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(s.message, vm::refresh)
                    is UiState.Empty -> EmptyBox("Nothing starred yet")
                    is UiState.Data -> {
                        val favs = s.value
                        val artists = favs.artists
                        val albums = favs.albums
                        val songs = favs.tracks
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                            if (albums.isNotEmpty()) {
                                item { Text("Albums", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                                items(albums, key = { "al${it.id}" }) { a ->
                                    Row(Modifier.fillMaxWidth().clickable { onAlbum(a.id) }.padding(16.dp, 8.dp)) {
                                        Text(a.name, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            if (songs.isNotEmpty()) {
                                item { Text("Songs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                                items(songs, key = { "so${it.id}" }) { song ->
                                    SongRow(song, repo.coverArtUrl(song.coverArt, 128), onClick = {
                                        player.playAll(songs, songs.indexOf(song))
                                    }, player = player, messenger = messenger)
                                }
                            }
                            if (artists.isNotEmpty()) {
                                item { Text("Artists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
                                items(artists, key = { "ar${it.id}" }) { a ->
                                    Row(Modifier.fillMaxWidth().clickable { onArtist(a.id) }.padding(16.dp, 8.dp)) {
                                        Text(a.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPullToRefresh(
    vm: LibraryViewModel,
    tab: LibraryTab,
    content: @Composable () -> Unit,
) {
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val genres by vm.genres.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favs by vm.favs.collectAsState()
    val refreshing = when (tab) {
        LibraryTab.ALBUMS -> albums is UiState.Loading
        LibraryTab.ARTISTS -> artists is UiState.Loading
        LibraryTab.GENRES -> genres is UiState.Loading
        LibraryTab.PLAYLISTS -> playlists is UiState.Loading
        LibraryTab.FAVORITES -> favs is UiState.Loading
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}
