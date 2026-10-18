package com.feishin.mobile.ui.detail

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.PlaylistOrdering
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import com.feishin.mobile.core.common.PlaylistSortKey
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.LocalTopBarController
import com.feishin.mobile.ui.TopBarAction
import com.feishin.mobile.ui.TopBarSearch
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.components.ClickableName
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.library.SortBottomSheet
import com.feishin.mobile.ui.library.SortOption
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

/**
 * How long the page takes to scroll the cover and the buttons away when search
 * opens. Long enough to read as one movement, short enough that the field feels
 * immediate.
 */
private const val SearchScrollMs = 350

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
 * tappable artist line, count, then shuffle / play / more - with an optional
 * favourite between play and more for the screens that have a real one.
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
    /**
     * False on screens with no meaningful favourite action (playlists), so the
     * row is shuffle / play / more centred instead of carrying a dead button.
     */
    showFavorite: Boolean = true,
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
            if (showFavorite) {
                CircleIconButton(
                    onClick = onFavorite,
                    icon = if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    description = "Favorite",
                )
            }
            CircleIconButton(onClick = onMore, icon = Icons.Default.MoreVert, description = "More")
        }
    }
}

/**
 * The controls that sit directly above a track list: one sort button in the
 * Library's language, and an optional edit toggle for per-row removal.
 *
 * Right-aligned and one row tall. The old chip strip grew with the number of
 * keys and put a search icon in the list; the icon now lives in the top bar and
 * the sort sheet carries the options and the direction.
 */
@Composable
fun TrackListControlsRow(
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
    editMode: Boolean? = null,
    onToggleEdit: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = VusicSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSortClick) {
            Icon(Icons.Default.Sort, contentDescription = "Sort")
        }
        if (editMode != null && onToggleEdit != null) {
            IconButton(onClick = onToggleEdit) {
                Icon(
                    if (editMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editMode) "Done editing" else "Edit tracks",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaylistDetailScreen(
    vm: PlaylistDetailViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onActions: (ActionTarget) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val isVirtual = vm.isVirtualFavorites || vm.isVirtualCached
    val sort by vm.sort.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // Hoisted above the state machine on purpose: a transient Loading (a
    // pull-to-refresh, an event-triggered reload) must not throw away where
    // the reader was.
    val listState = rememberSavableListState()
    // Opening or closing the keyboard resizes the list, and the lazy layout
    // re-anchors on its first visible item when it does - which dragged the
    // playlist back under the cover while the reader was filtering. Keep the
    // anchor while the IME is up and put it back when it goes.
    val imeVisible = WindowInsets.isImeVisible
    val imeAnchor = remember { object { var value: Pair<Int, Int>? = null } }
    LaunchedEffect(imeVisible, listState) {
        if (imeVisible) {
            // Keep the anchor current for as long as the keyboard is up, so
            // closing it restores where the reader actually is. Capturing it
            // once, on open, threw away any scrolling they did while
            // filtering and yanked them back to the top.
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { imeAnchor.value = it }
        } else {
            imeAnchor.value?.let { (index, offset) -> listState.scrollToItem(index, offset) }
            imeAnchor.value = null
        }
    }
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
            val topBar = LocalTopBarController.current
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current
            val barScrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
                }
            }
            LaunchedEffect(p.name, barScrolled) {
                topBar.title = p.name
                topBar.scrolled = barScrolled
            }

            // Sort and edit state. Both are view-only: neither touches the
            // stored order, and removing a row still addresses the stored entry.
            val sortOptions = remember {
                PlaylistSortKey.entries.map { SortOption(it.name, it.label) }
            }
            var showSortSheet by remember { mutableStateOf(false) }
            var editMode by remember { mutableStateOf(false) }
            // Removal is only possible on a real, user-created playlist.
            val canEditRows = !isVirtual

            val closeSearch = {
                searchOpen = false
                vm.setQuery("")
                focusManager.clearFocus()
            }
            val barSearch = remember {
                TopBarSearch(
                    hint = "Filter this playlist…",
                    onQuery = vm::setQuery,
                    onClose = closeSearch,
                )
            }
            val openSearch = {
                scope.launch {
                    // Scroll the cover and the button row out of the way FIRST,
                    // so the songs come to rest under the bar, and only then
                    // reveal the field. Opening straight onto the field dropped
                    // the list out from under the keyboard instead.
                    val headerPx = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == 0 }?.size ?: 0
                    if (headerPx > 0 && listState.canScrollForward) {
                        listState.animateScrollBy(
                            headerPx.toFloat(),
                            Motion.value(SearchScrollMs),
                        )
                    }
                    searchOpen = true
                }
            }
            LaunchedEffect(searchOpen, p.id) {
                topBar.search = if (searchOpen) barSearch else null
                // The bar has one action slot and it belongs to search, which
                // is why the icon stays visible while the list scrolls.
                topBar.action = if (searchOpen) {
                    null
                } else {
                    TopBarAction(Icons.Default.Search, "Search tracks") { openSearch() }
                }
            }
            LaunchedEffect(query) { barSearch.query = query }
            // Back leaves the search (keeping the scroll position) rather than
            // leaving the playlist.
            androidx.activity.compose.BackHandler(enabled = searchOpen) { closeSearch() }

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
                            // A playlist has no "favourite" of its own: the heart
                            // was wired to nothing here, so it is gone rather than
                            // left as a dead button. Delete now lives in the ⋮.
                            onFavorite = {},
                            showFavorite = false,
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
                    }
                    // Directly above the songs, and the target the search scroll
                    // stops on, so opening search lands on the controls with the
                    // list under the bar rather than on the cover.
                    item {
                        TrackListControlsRow(
                            onSortClick = { showSortSheet = true },
                            editMode = if (canEditRows) editMode else null,
                            onToggleEdit = { editMode = !editMode },
                        )
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
                            // Always the STORED index (`src`), never the visible
                            // one, so a removal under a sort or filter still hits
                            // the entry the user pointed at - and a duplicated
                            // song still cannot delete the wrong copy. The X only
                            // exists in edit mode.
                            onRemove = if (canEditRows && editMode) ({ vm.removeAt(src) }) else null,
                            isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                        )
                    }
                    if (query.isNotBlank() && visibleTracks.isEmpty()) {
                        item {
                            Text(
                                "No tracks match \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VusicColors.SecondaryText,
                                modifier = Modifier.padding(
                                    horizontal = VusicSpacing.ScreenHorizontal,
                                    vertical = 12.dp,
                                ),
                            )
                        }
                    }
                }
            }
            if (showSortSheet) {
                // The Library's own sheet, reused rather than copied, so the two
                // places can never drift apart.
                SortBottomSheet(
                    options = sortOptions,
                    selectedId = sort.key.name,
                    ascending = !sort.descending,
                    onSelect = { id, ascending ->
                        val key = runCatching { PlaylistSortKey.valueOf(id) }
                            .getOrDefault(PlaylistSortKey.PLAYLIST_ORDER)
                        vm.setSort(key, descending = !ascending)
                    },
                    onDismiss = { showSortSheet = false },
                )
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
