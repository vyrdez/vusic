package com.feishin.mobile.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.PlaylistOrdering
import com.feishin.mobile.core.common.PlaylistSort
import com.feishin.mobile.core.common.PlaylistSortKey
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.LocalTopBarController
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
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
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Album not found")
        is UiState.Data -> {
            val a = s.value
            val starred = a.starred
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            val listState = rememberSavableListState()
            // Publish the name to the shared top bar once the header scrolls away.
            val topBar = LocalTopBarController.current
            val barScrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
                }
            }
            LaunchedEffect(a.name, barScrolled) {
                topBar.title = a.name
                topBar.scrolled = barScrolled
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
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
                    items(a.tracks, key = { it.id }, contentType = { "item" }) { song ->
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
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
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

/**
 * Sort chips plus a search toggle, shared by every track list that supports
 * view-only ordering (playlists, Favorite songs, Cached, Downloads).
 *
 * Tapping the active chip flips ascending/descending and shows an arrow; a
 * different chip always starts ascending. Same chip language as the Library
 * tab row so the two do not feel like different apps.
 */
@Composable
fun PlaylistSortRow(
    sort: PlaylistSort,
    onSelect: (PlaylistSortKey) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Downloads rows carry no duration, so that chip is not offered there.
    availableKeys: List<PlaylistSortKey> = PlaylistSortKey.entries,
) {
    // A remembered sort naming a key this list cannot express falls back, so a
    // stale preference can never blank the list.
    val effective = remember(sort, availableKeys) {
        if (sort.key in availableKeys) sort else PlaylistOrdering.DEFAULT
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(availableKeys, key = { it.name }, contentType = { "item" }) { key ->
                val selected = key == effective.key
                AssistChip(
                    onClick = { onSelect(key) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(key.label, maxLines = 1)
                            if (selected) {
                                Icon(
                                    if (effective.descending) Icons.Default.ArrowDownward
                                    else Icons.Default.ArrowUpward,
                                    contentDescription = if (effective.descending) "Descending" else "Ascending",
                                    modifier = Modifier.size(14.dp).padding(start = 2.dp),
                                )
                            }
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) VusicColors.SurfaceHigh
                        else androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
        }
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Default.Search, contentDescription = "Search tracks")
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
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val isVirtual = vm.isVirtualFavorites || vm.isVirtualCached
    val sort by vm.sort.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox(
            if (vm.isVirtualFavorites) "Nothing starred yet"
            else if (vm.isVirtualCached) "Nothing cached yet"
            else "Playlist not found",
        )
        is UiState.Data -> {
            val p = s.value
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            // The rows the user can actually see, in the order they can see
            // them. `visibleIndices` are positions in p.tracks, which stays
            // untouched: nothing here is ever sent back to the server.
            val visibleIndices = remember(p.tracks, sort, query) {
                PlaylistOrdering.visibleIndices(
                    size = p.tracks.size,
                    sort = sort,
                    query = query,
                    titleAt = { p.tracks[it].title },
                    artistAt = { p.tracks[it].artist },
                    albumAt = { p.tracks[it].album },
                    durationAt = { p.tracks[it].durationSec },
                )
            }
            val visibleTracks = remember(visibleIndices, p.tracks) {
                visibleIndices.map { p.tracks[it] }
            }
            val canMutate = PlaylistOrdering.canMutateOrder(sort, query)
            val listState = rememberSavableListState()
            val topBar = LocalTopBarController.current
            val barScrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
                }
            }
            LaunchedEffect(p.name, barScrolled) {
                topBar.title = p.name
                topBar.scrolled = barScrolled
            }
            val cover = if (vm.isVirtualCached) {
                PlaylistCover.select(
                    serverCoverArtId = null,
                    trackCoverArtIds = p.tracks.map { it.coverArt },
                )
            } else if (isVirtual) {
                PlaylistCover.Favorites(
                    p.tracks.mapNotNull { it.coverArt }.distinct().take(4),
                )
            } else {
                PlaylistCover.select(
                    serverCoverArtId = null,
                    trackCoverArtIds = p.tracks.map { it.coverArt },
                )
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                ) {
                    item {
                        AlbumHeader(
                            name = p.name,
                            artist = "",
                            artistId = null,
                            coverArtUrl = null,
                            // Show the VISIBLE count while a sort or filter narrows
                            // the list, so the header agrees with what is on
                            // screen and with what Play will queue.
                            subtitle = if (query.isBlank()) "${p.songCount} songs"
                            else "${visibleTracks.size} of ${p.songCount} songs",
                            starred = false,
                            onPlay = { player.playAll(visibleTracks, source = p.name) },
                            onShuffle = { player.playAll(visibleTracks, shuffle = true, source = p.name) },
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
                            // The virtual collection can't be deleted or renamed.
                            if (!isVirtual) {
                                OutlinedButton(onClick = { vm.delete(onBack) }) { Text("Delete playlist") }
                            }
                        }
                        AnimatedVisibility(
                            visible = searchOpen,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = com.feishin.mobile.ui.theme.Motion.layoutTween(),
                            ) + fadeIn(com.feishin.mobile.ui.theme.Motion.fade()),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = com.feishin.mobile.ui.theme.Motion.layoutTween(),
                            ) + fadeOut(com.feishin.mobile.ui.theme.Motion.fade()),
                        ) {
                            com.feishin.mobile.ui.library.LibrarySearchField(
                                query = query,
                                onQuery = vm::setQuery,
                                onClose = { searchOpen = false; vm.setQuery("") },
                                autoFocus = true,
                            )
                        }
                        PlaylistSortRow(
                            sort = sort,
                            onSelect = vm::toggleSort,
                            onSearchClick = { searchOpen = !searchOpen },
                        )
                        if (query.isNotBlank() && visibleTracks.isEmpty()) {
                            Text(
                                "No tracks match \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VusicColors.SecondaryText,
                                modifier = Modifier.padding(horizontal = VusicSpacing.ScreenHorizontal, vertical = 12.dp),
                            )
                        }
                    }
                    // Keyed by source position, not id alone: a playlist may
                    // legitimately hold the same song twice, and the source
                    // index is what stays stable across a re-sort/re-filter.
                    itemsIndexed(visibleIndices, key = { _, src -> "${p.tracks[src].id}#$src" }) { visibleIndex, src ->
                        val song = p.tracks[src]
                        TrackRow(
                            track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                            // Play exactly what the user can see.
                            onClick = { player.playAll(visibleTracks, visibleIndex, source = p.name) },
                            onActions = { onActions(ActionTarget.Track(song)) },
                            // Removal acts on the server's entry position, so it
                            // is only offered while the visible list IS the
                            // stored order - and then it uses the source index
                            // rather than the id, which is what keeps a
                            // duplicated song from deleting the wrong copy.
                            onRemove = if (isVirtual || !canMutate) null else ({ vm.removeAt(src) }),
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
    onBack: () -> Unit = {},
    onActions: (ActionTarget) -> Unit,
) {
    ArtistScreen(vm = vm, player = player, repo = repo, messenger = messenger, onAlbum = onAlbum, onArtist = onArtist, onActions = onActions, onBack = onBack)
}
