package com.feishin.mobile.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.theme.VusicColors
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    vm: SearchViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    val q by vm.query.collectAsState()
    val ui by vm.ui.collectAsState()
    val settingsState by settings.flow.collectAsState(initial = null)
    val nowPlaying by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(ui) {
        if (ui is UiState.Data && q.isNotBlank()) settings.addRecentSearch(q)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, vm::onQuery,
            label = { Text("Search artists, albums, songs") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            singleLine = true,
        )
        when (val s = ui) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorState(message = s.message, onRetry = { vm.onQuery(q) })
            is UiState.Empty -> {
                val recent = settingsState?.recentSearches.orEmpty()
                if (q.isBlank() && recent.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { settings.clearRecentSearches() } }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, "Clear recent searches")
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(recent, key = { "rs$it" }) { r ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        vm.onQuery(r)
                                        scope.launch { settings.addRecentSearch(r) }
                                    }
                                    .padding(16.dp, 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.History, null, tint = VusicColors.SecondaryText)
                                Text(r, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                } else {
                    EmptyState(if (q.isBlank()) "Type to search" else "No results")
                }
            }
            is UiState.Data -> {
                val res = s.value
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = rememberSavableListState(),
                    contentPadding = PaddingValues(bottom = 160.dp),
                ) {
                    if (res.tracks.isNotEmpty()) {
                        item { SectionHeader(title = "Songs") }
                        items(res.tracks, key = { "s${it.id}" }) { song ->
                            TrackRow(
                                track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                                onClick = { player.playAll(res.tracks, res.tracks.indexOf(song), source = "Search") },
                                onActions = { onActions(ActionTarget.Track(song)) },
                                onArtist = { id -> onArtist(id) },
                                isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                            )
                        }
                    }
                    if (res.albums.isNotEmpty()) {
                        item { SectionHeader(title = "Albums") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                items(res.albums, key = { "b${it.id}" }) { a ->
                                    AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) }, modifier = Modifier.width(140.dp))
                                }
                            }
                        }
                    }
                    if (res.artists.isNotEmpty()) {
                        item { SectionHeader(title = "Artists") }
                        items(res.artists, key = { "a${it.id}" }) { a ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onArtist(a.id) }
                                    .padding(16.dp, 12.dp),
                            ) {
                                Text(a.name, modifier = Modifier.weight(1f))
                                Text(
                                    "${a.albumCount}", style = MaterialTheme.typography.bodySmall,
                                    color = VusicColors.SecondaryText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
