package com.feishin.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.LocalTopBarController
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.ClickableName
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    container: Color = VusicColors.SurfaceHigh,
    tint: Color = Color.White,
    size: Int = 52,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(container),
    ) {
        Icon(icon, description, tint = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    vm: AlbumDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val nowPlaying by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Album not found")
        is UiState.Data -> {
            val a = s.value
            val starred = a.starred
            val refreshing by vm.refreshing.collectAsState()
            val listState = rememberSavableListState()
            // Publish the name to the shared top bar once the header scrolls away.
            val topBar = LocalTopBarController.current
            LaunchedEffect(a.name, listState.firstVisibleItemIndex) {
                topBar.title = a.name
                topBar.scrolled = listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 120
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom),
                ) {
                    item {
                        AlbumHeader(
                            name = a.name,
                            artist = a.artist,
                            artistId = a.artistId,
                            coverArtUrl = repo.coverArtUrl(a.coverArt, 600),
                            subtitle = "${a.songCount} songs",
                            starred = starred,
                            onPlay = { player.playAll(a.tracks, source = a.name) },
                            onShuffle = { player.playAll(a.tracks, shuffle = true, source = a.name) },
                            onFavorite = { vm.toggleStar(starred) },
                            onMore = {
                                onActions(
                                    ActionTarget.Album(
                                        com.feishin.mobile.domain.model.Album(
                                            a.id, a.name, a.artist, a.artistId, a.coverArt,
                                            a.songCount, a.durationSec, 0, a.year, a.genre, a.starred,
                                        ),
                                        a.tracks,
                                    ),
                                )
                            },
                            onArtistClick = onArtist,
                        )
                    }
                    items(a.tracks, key = { it.id }) { song ->
                        TrackRow(
                            track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                            onClick = { player.playAll(a.tracks, a.tracks.indexOf(song), source = a.name) },
                            onActions = { onActions(ActionTarget.Track(song)) },
                            onArtist = { id -> onArtist(id) },
                            isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared entity header (album / playlist framing): large rounded cover, title,
 * tappable artist line, count, then shuffle / play / favourite / more.
 */
@Composable
fun AlbumHeader(
    name: String,
    artist: String,
    artistId: String?,
    coverArtUrl: String?,
    subtitle: String,
    starred: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    onArtistClick: (String) -> Unit,
    cover: @Composable (Modifier) -> Unit = { m ->
        CoverImage(coverArtUrl, name, modifier = m, roundedDp = 12.dp, contentScale = ContentScale.Crop)
    },
) {
    Column(
        Modifier.fillMaxWidth().padding(VusicSpacing.ScreenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        cover(Modifier.size(220.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            name, style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        ClickableName(
            name = artist,
            enabled = artistId?.isNotBlank() == true,
            onClick = { artistId?.let(onArtistClick) },
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = onShuffle, icon = Icons.Default.Shuffle, description = "Shuffle")
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White),
            ) {
                Icon(Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(40.dp))
            }
            CircleIconButton(
                onClick = onFavorite,
                icon = if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                description = "Favorite",
            )
            CircleIconButton(onClick = onMore, icon = Icons.Default.MoreVert, description = "More")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    vm: PlaylistDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onActions: (ActionTarget) -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val nowPlaying by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Playlist not found")
        is UiState.Data -> {
            val p = s.value
            val refreshing by vm.refreshing.collectAsState()
            val listState = rememberSavableListState()
            val topBar = LocalTopBarController.current
            LaunchedEffect(p.name, listState.firstVisibleItemIndex) {
                topBar.title = p.name
                topBar.scrolled = listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 120
            }
            val cover = PlaylistCover.select(
                serverCoverArtId = null,
                trackCoverArtIds = p.tracks.map { it.coverArt },
            )
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom),
                ) {
                    item {
                        AlbumHeader(
                            name = p.name,
                            artist = "",
                            artistId = null,
                            coverArtUrl = null,
                            subtitle = "${p.songCount} songs",
                            starred = false,
                            onPlay = { player.playAll(p.tracks, source = p.name) },
                            onShuffle = { player.playAll(p.tracks, shuffle = true, source = p.name) },
                            onFavorite = {},
                            onMore = {
                                onActions(
                                    ActionTarget.Playlist(
                                        com.feishin.mobile.domain.model.Playlist(p.id, p.name, p.songCount, p.durationSec, null),
                                        p.tracks,
                                    ),
                                )
                            },
                            onArtistClick = {},
                            cover = { m ->
                                PlaylistCoverArt(
                                    cover = cover,
                                    resolver = { id -> repo.coverArtUrl(id, 600) },
                                    contentDescription = p.name,
                                    modifier = m,
                                    roundedDp = 12.dp,
                                )
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = VusicSpacing.ScreenHorizontal),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            OutlinedButton(onClick = { vm.delete(onBack) }) { Text("Delete playlist") }
                        }
                    }
                    items(p.tracks.size, key = { i -> p.tracks[i].id + "_$i" }) { i ->
                        val song = p.tracks[i]
                        TrackRow(
                            track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                            onClick = { player.playAll(p.tracks, i, source = p.name) },
                            onActions = { onActions(ActionTarget.Track(song)) },
                            onRemove = { vm.removeAt(i) },
                            isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }
}

// Kept for source compatibility; the artist page lives in ArtistScreen.kt.
@Composable
fun ArtistDetailScreen(
    vm: ArtistDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    ArtistScreen(vm = vm, player = player, repo = repo, messenger = messenger, onAlbum = onAlbum, onArtist = onArtist, onActions = onActions)
}
