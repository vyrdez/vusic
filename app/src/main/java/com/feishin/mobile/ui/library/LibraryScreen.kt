package com.feishin.mobile.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.feishin.mobile.ui.theme.VusicColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.ArtistCard
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.FilterChipRow
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableGridState
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.theme.VusicSpacing

private fun tabLabel(t: LibraryTab) = when (t) {
    LibraryTab.PLAYLISTS -> "Playlists"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.SONGS -> "Songs"
    LibraryTab.FAVORITES -> "Favorites"
    LibraryTab.DOWNLOADS -> "Downloads"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialTab: LibraryTab? = null,
    vm: LibraryViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onGenre: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val layouts by vm.layouts.collectAsStateWithLifecycle()
    val sorts by vm.sorts.collectAsStateWithLifecycle()
    val queries by vm.queries.collectAsStateWithLifecycle()
    // Read the mode from the collected map (subscribes to changes): reading
    // vm.layoutFor() here would sample StateFlow.value without subscribing,
    // so the icon and layout would never update on toggle.
    val grid = (layouts[tab] ?: LayoutMode.GRID) == LayoutMode.GRID
    var showSortSheet by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val query = queries[tab].orEmpty()

    val settings by vm.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val showDeviceFiles = settings?.showDeviceFiles == true
    val isDeviceMode = showDeviceFiles && settings?.libraryViewMode == "DEVICE"
    val currentTitle = if (isDeviceMode) "Device files" else "Library"
    var showViewMySheet by remember { mutableStateOf(false) }

    // The nav arg is authoritative only the first time each value is seen
    // (Explore pills); a back re-entry must not reset the user's tab.
    LaunchedEffect(initialTab) { vm.applyNavTab(initialTab) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(com.feishin.mobile.ui.components.TopLevelTitleBar.RowHeight)
                .padding(start = VusicSpacing.ScreenHorizontal, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (showDeviceFiles) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showViewMySheet = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                } else Modifier,
            ) {
                Text(
                    currentTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = VusicColors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (showDeviceFiles) {
                    Spacer(Modifier.width(8.dp))
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (showViewMySheet) 180f else 0f,
                        animationSpec = tween(250),
                        label = "chevron",
                    )
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(VusicColors.SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch library source",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!isDeviceMode) {
                IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "Search library")
                }
                IconButton(onClick = { vm.toggleLayout(tab) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (grid) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle view",
                    )
                }
                IconButton(onClick = { showSortSheet = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
            }
        }

        Crossfade(
            targetState = isDeviceMode,
            animationSpec = tween(250),
            label = "library_source_crossfade",
        ) { deviceMode ->
            if (deviceMode) {
                DeviceFilesScreen(player = player, onActions = onActions)
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Inline search
                    AnimatedVisibility(
                        visible = searchOpen,
                        enter = expandVertically(
                            expandFrom = androidx.compose.ui.Alignment.Top,
                            animationSpec = com.feishin.mobile.ui.theme.Motion.layoutTween(),
                        ) + fadeIn(com.feishin.mobile.ui.theme.Motion.fade()),
                        exit = shrinkVertically(
                            shrinkTowards = androidx.compose.ui.Alignment.Top,
                            animationSpec = com.feishin.mobile.ui.theme.Motion.layoutTween(),
                        ) + fadeOut(com.feishin.mobile.ui.theme.Motion.fade()),
                    ) {
                        LibrarySearchField(
                            query = query,
                            onQuery = { vm.setQuery(tab, it) },
                            onClose = { searchOpen = false; vm.clearQuery(tab) },
                            autoFocus = true,
                        )
                    }
                    // Chips own their line and scroll on their own.
                    FilterChipRow(
                        options = LibraryTab.entries.map(::tabLabel),
                        selected = tabLabel(tab),
                        onSelect = { label -> vm.selectTab(LibraryTab.entries.first { tabLabel(it) == label }) },
                    )
                    if (showSortSheet) {
                        SortBottomSheet(
                            tabName = tab.name,
                            current = sorts[tab] ?: com.feishin.mobile.core.common.LibrarySorts.default(tab.name),
                            onSelect = { vm.setSort(tab, it) },
                            onDismiss = { showSortSheet = false },
                        )
                    }
                    LibraryPullToRefresh(vm, tab) {
            when (tab) {
                LibraryTab.ALBUMS -> {
                    val ui by vm.albums.collectAsStateWithLifecycle()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(message = s.message, onRetry = { vm.loadAlbums(true) })
                        is UiState.Empty -> EmptyState(if (query.isBlank()) "No albums" else "No albums match \"$query\"")
                        is UiState.Data -> {
                            val shown = com.feishin.mobile.core.common.LibraryOrdering.filterAlbums(s.value, query)
                            if (shown.isEmpty()) {
                                EmptyState("No albums match \"$query\"")
                            } else if (grid) {
                                val gridState = rememberSavableGridState()
                                val atEnd by remember {
                                    derivedStateOf {
                                        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                            ?.let { it >= shown.size - 6 } == true
                                    }
                                }
                                LaunchedEffect(atEnd, shown.size) { if (atEnd) vm.loadAlbums() }
                                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), state = gridState, contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current)) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { a ->
                                        AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) })
                                    }
                                }
                            } else {
                                val listState = rememberSavableListState()
                                val atEnd by remember {
                                    derivedStateOf {
                                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                            ?.let { it >= shown.size - 6 } == true
                                    }
                                }
                                LaunchedEffect(atEnd, shown.size) { if (atEnd) vm.loadAlbums() }
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    state = listState,
                                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { a ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .clickable(onClick = { onAlbum(a.id) })
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CoverImage(repo.coverArtUrl(a.coverArt, 128), a.name, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
                                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            Text(
                                                "${a.artist} • ${a.year?.toString() ?: "—"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(onClick = { onActions(ActionTarget.Album(a)) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Default.MoreVert, "Album options")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                LibraryTab.ARTISTS -> {
                    val ui by vm.artists.collectAsStateWithLifecycle()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState(if (query.isBlank()) "No artists" else "No artists match \"$query\"")
                        is UiState.Data -> {
                            val shown = com.feishin.mobile.core.common.LibraryOrdering.filterArtists(s.value, query)
                            if (shown.isEmpty()) {
                                EmptyState("No artists match \"$query\"")
                            } else if (grid) {
                                LazyVerticalGrid(
                                    GridCells.Fixed(2),
                                    Modifier.fillMaxSize(),
                                    state = rememberSavableGridState(),
                                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { a ->
                                        ArtistPhotoCard(a, photoUrl = vm.artistPhotoUrl(a), repo = repo, onClick = { onArtist(a.id) })
                                        LaunchedEffect(a.id) { vm.prefetchArtistPhoto(a) }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    state = rememberSavableListState(),
                                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { a ->
                                        ArtistPhotoRow(a, photoUrl = vm.artistPhotoUrl(a), repo = repo, onClick = { onArtist(a.id) })
                                        LaunchedEffect(a.id) { vm.prefetchArtistPhoto(a) }
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryTab.SONGS -> {
                    val ui by vm.songs.collectAsStateWithLifecycle()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState(if (query.isBlank()) "No songs" else "No songs match \"$query\"")
                        is UiState.Data -> {
                            val shown = com.feishin.mobile.core.common.LibraryOrdering.filterSongs(s.value, query)
                            if (shown.isEmpty()) {
                                EmptyState("No songs match \"$query\"")
                            } else if (grid) {
                                LazyVerticalGrid(
                                    GridCells.Fixed(2), Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { song ->
                                        Box(Modifier.animateItem()) {
                                            SongGridCell(
                                                song = song, artUrl = repo.coverArtUrl(song.coverArt, 300),
                                                onClick = { player.playAll(shown, shown.indexOf(song), source = "Songs") },
                                                onActions = { onActions(ActionTarget.Track(song)) },
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    state = rememberSavableListState(),
                                    contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    items(shown, key = { it.id }, contentType = { "item" }) { song ->
                                        TrackRow(
                                            track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                                            onClick = { player.playAll(shown, shown.indexOf(song), source = "Songs") },
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
                LibraryTab.PLAYLISTS -> {
                    val ui by vm.playlists.collectAsStateWithLifecycle()
                    val favsUi by vm.favs.collectAsStateWithLifecycle()
                    val favTracks = (favsUi as? UiState.Data)?.value?.tracks.orEmpty()
                    val cached by vm.cached.collectAsStateWithLifecycle()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> {
                            // Even with no server playlists, the built-ins stay pinned.
                            FavoriteSongsRow(
                                count = favTracks.size,
                                covers = favTracks.map { it.coverArt },
                                repo = repo,
                                onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                            )
                            CachedSongsRow(
                                count = cached.size,
                                covers = cached.map { it.coverArt },
                                repo = repo,
                                onClick = { onPlaylist(CACHED_SONGS_PLAYLIST_ID) },
                            )
                        }
                        is UiState.Data -> {
                            val shown = com.feishin.mobile.core.common.LibraryOrdering.filterPlaylists(s.value, query)
                            if (grid) {
                                LazyVerticalGrid(
                                    GridCells.Fixed(2), Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    item(key = "favorites") {
                                        PlaylistGridCell(
                                            name = "Favorite songs", count = "${favTracks.size} songs",
                                            cover = PlaylistCover.Favorites(favTracks.map { it.coverArt }.filterNotNull().distinct().take(4)),
                                            repo = repo, onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                                        )
                                    }
                                    item(key = "cached") {
                                        PlaylistGridCell(
                                            name = "Cached", count = "${cached.size} songs",
                                            cover = PlaylistCover.select(null, cached.map { it.coverArt }),
                                            repo = repo, onClick = { onPlaylist(CACHED_SONGS_PLAYLIST_ID) },
                                        )
                                    }
                                    if (shown.isEmpty() && query.isNotBlank()) {
                                        item { EmptyState("No playlists match \"$query\"") }
                                    }
                                    items(shown, key = { it.id }, contentType = { "item" }) { p ->
                                        PlaylistGridCell(
                                            name = p.name, count = "${p.songCount} songs",
                                            cover = PlaylistCover.select(p.coverArt, emptyList()),
                                            repo = repo, onClick = { onPlaylist(p.id) },
                                        )
                                    }
                                }
                            } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = rememberSavableListState(),
                                contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                            ) {
                                // Built-in collections stay pinned above server playlists.
                                item(key = "favorites") {
                                    FavoriteSongsRow(
                                        count = favTracks.size,
                                        covers = favTracks.map { it.coverArt },
                                        repo = repo,
                                        onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                                    )
                                }
                                item(key = "cached") {
                                    CachedSongsRow(
                                        count = cached.size,
                                        covers = cached.map { it.coverArt },
                                        repo = repo,
                                        onClick = { onPlaylist(CACHED_SONGS_PLAYLIST_ID) },
                                    )
                                }
                                if (shown.isEmpty() && query.isNotBlank()) {
                                    item { EmptyState("No playlists match \"$query\"") }
                                }
                                items(shown, key = { it.id }, contentType = { "item" }) { p ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clickable(onClick = { onPlaylist(p.id) })
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlaylistCoverArt(
                                        cover = PlaylistCover.select(p.coverArt, emptyList()),
                                        resolver = { id -> repo.coverArtUrl(id, 128) },
                                        contentDescription = p.name,
                                        modifier = Modifier.size(56.dp),
                                        roundedDp = 4.dp,
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            "${p.songCount} songs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                }
                LibraryTab.FAVORITES -> {
                    val ui by vm.favs.collectAsStateWithLifecycle()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState("Nothing starred yet")
                        is UiState.Data -> {
                            val favs = s.value
                            if (grid) {
                                LazyVerticalGrid(
                                    GridCells.Fixed(2), Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                                ) {
                                    if (favs.tracks.isNotEmpty()) {
                                        item(span = { GridItemSpan(2) }) { SectionHeader(title = "Songs") }
                                        items(favs.tracks, key = { "so${it.id}" }) { song ->
                                            SongGridCell(
                                                song = song, artUrl = repo.coverArtUrl(song.coverArt, 300),
                                                onClick = { player.playAll(favs.tracks, favs.tracks.indexOf(song), source = "Favorites") },
                                                onActions = { onActions(ActionTarget.Track(song)) },
                                            )
                                        }
                                    }
                                    if (favs.albums.isNotEmpty()) {
                                        item(span = { GridItemSpan(2) }) { SectionHeader(title = "Albums") }
                                        items(favs.albums, key = { "al${it.id}" }) { a ->
                                            AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) }, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                    if (favs.artists.isNotEmpty()) {
                                        item(span = { GridItemSpan(2) }) { SectionHeader(title = "Artists") }
                                        items(favs.artists, key = { "ar${it.id}" }) { a ->
                                            ArtistPhotoCard(a, photoUrl = vm.artistPhotoUrl(a), repo = repo, onClick = { onArtist(a.id) })
                                            LaunchedEffect(a.id) { vm.prefetchArtistPhoto(a) }
                                        }
                                    }
                                }
                            } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current)) {
                                if (favs.tracks.isNotEmpty()) {
                                    item(key = "fav-songs") {
                                        FavoriteSongsRow(
                                            count = favs.tracks.size,
                                            covers = favs.tracks.map { it.coverArt },
                                            repo = repo,
                                            onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                                        )
                                    }
                                }
                                if (favs.albums.isNotEmpty()) {
                                    item { SectionHeader(title = "Albums") }
                                    items(favs.albums, key = { "al${it.id}" }) { a ->
                                        AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                if (favs.tracks.isNotEmpty()) {
                                    item { SectionHeader(title = "Songs") }
                                    items(favs.tracks, key = { "so${it.id}" }) { song ->
                                        TrackRow(
                                            track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                                            onClick = { player.playAll(favs.tracks, favs.tracks.indexOf(song), source = "Favorites") },
                                            onActions = { onActions(ActionTarget.Track(song)) },
                                            onArtist = { id -> onArtist(id) },
                                            isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                                        )
                                    }
                                }
                                if (favs.artists.isNotEmpty()) {
                                        item { SectionHeader(title = "Artists") }
                                        items(favs.artists, key = { "ar${it.id}" }) { a ->
                                            androidx.compose.foundation.layout.Row(
                                                Modifier.fillMaxWidth()
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                                    .clickable(onClick = { onArtist(a.id) })
                                                    .padding(16.dp, 8.dp),
                                            ) {
                                                Text(a.name)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryTab.DOWNLOADS -> {
                    DownloadsTab(
                        downloads = downloads,
                        repo = repo,
                        grid = grid,
                        onPlaySong = { entity ->
                            player.playAll(listOf(entity.toDomainTrack()), source = "Downloads")
                        },
                        onRemove = { id -> scope.launch { downloads.removeFromLibrary(id) } },
                        onManage = onOpenDownloads,
                        onActions = onActions,
                    )
                }
            }
        }
    }
}
        }
    }

    if (showViewMySheet) {
        val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showViewMySheet = false },
            sheetState = sheetState,
            containerColor = VusicColors.SurfaceHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "View my",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                val serverSubtitle = serverUrl.takeIf { it.isNotBlank() } ?: "Server music"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            vm.setLibraryViewMode("SERVER")
                            scope.launch { sheetState.hide() }.invokeOnCompletion { showViewMySheet = false }
                        }
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!isDeviceMode) com.feishin.mobile.ui.theme.VusicColors.Accent else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            serverSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (!isDeviceMode) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active",
                            tint = com.feishin.mobile.ui.theme.VusicColors.Accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            vm.setLibraryViewMode("DEVICE")
                            scope.launch { sheetState.hide() }.invokeOnCompletion { showViewMySheet = false }
                        }
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Device files",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isDeviceMode) com.feishin.mobile.ui.theme.VusicColors.Accent else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Music stored on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (isDeviceMode) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active",
                            tint = com.feishin.mobile.ui.theme.VusicColors.Accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Downloaded music: totals, then the songs, with remove buttons. */
@Composable
private fun DownloadsTab(
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    repo: MusicDataRepository,
    grid: Boolean,
    onPlaySong: (com.feishin.mobile.data.db.DownloadEntity) -> Unit,
    onRemove: (String) -> Unit,
    onManage: () -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    val all by downloads.downloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val done = all.filter { it.statusEnum == com.feishin.mobile.data.db.DownloadStatus.COMPLETED }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (all.isEmpty()) {
        EmptyState("Nothing downloaded yet. Use the download button on a song, album or playlist.")
        return
    }
    val total = done.fold(0L) { acc, e -> acc + e.sizeBytes }
    if (grid) {
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
        ) {
            item(span = { GridItemSpan(2) }) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${done.size} songs", style = MaterialTheme.typography.titleMedium)
                        Text(
                            com.feishin.mobile.core.common.CacheSizePolicy.human(total),
                            style = MaterialTheme.typography.bodySmall,
                            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                        )
                    }
                    com.feishin.mobile.ui.settings.SettingsActionButton(label = "Manage", onClick = onManage)
                }
            }
            items(done, key = { it.songId }, contentType = { "item" }) { entity ->
                SongGridCell(
                    song = entity.toDomainTrack(),
                    artUrl = repo.coverArtUrl(entity.coverArt, 300),
                    onClick = { onPlaySong(entity) },
                    onActions = { onActions(ActionTarget.Track(entity.toDomainTrack())) },
                )
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${done.size} songs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        com.feishin.mobile.core.common.CacheSizePolicy.human(total),
                        style = MaterialTheme.typography.bodySmall,
                        color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                    )
                }
                com.feishin.mobile.ui.settings.SettingsActionButton(label = "Manage", onClick = onManage)
            }
        }
        items(done, key = { it.songId }, contentType = { "item" }) { entity ->
            TrackRow(
                track = entity.toDomainTrack(),
                artUrl = repo.coverArtUrl(entity.coverArt, 128),
                onClick = { onPlaySong(entity) },
                onActions = { onActions(ActionTarget.Track(entity.toDomainTrack())) },
                downloadState = entity,
                onRemove = { scope.launch { onRemove(entity.songId) } },
            )
        }
    }
}

/** A stored download as a playable domain track (used by the Downloads tab). */
private fun com.feishin.mobile.data.db.DownloadEntity.toDomainTrack() =
    com.feishin.mobile.domain.model.Track(
        id = songId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        coverArt = coverArt,
        durationSec = durationSec,
        trackNo = trackNo,
        year = year,
        genre = genre,
        suffix = suffix,
        starred = false,
        path = path,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        replayGainPeak = replayGainPeak,
    )

/**
 * Pinned "Favorite songs" row: heart-on-gradient cover, or a collage of the
 * latest favorites. Opens the built-in collection page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCell(
    song: com.feishin.mobile.domain.model.Track,
    artUrl: String?,
    onClick: () -> Unit,
    onActions: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onActions)
            .padding(4.dp),
    ) {
        CoverImage(artUrl, song.title, modifier = Modifier.fillMaxWidth().aspectRatio(1f), roundedDp = 8.dp)
        Text(
            song.title, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Text(
            song.artist, style = MaterialTheme.typography.bodySmall,
            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 2-column grid cell for a playlist: collage/cover, name, song count. */
@Composable
private fun PlaylistGridCell(
    name: String,
    count: String,
    cover: PlaylistCover,
    repo: MusicDataRepository,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        PlaylistCoverArt(
            cover = cover,
            resolver = { id -> repo.coverArtUrl(id, 300) },
            contentDescription = name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            roundedDp = 8.dp,
        )
        Text(
            name, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Text(
            count, style = MaterialTheme.typography.bodySmall,
            color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
@Composable
private fun FavoriteSongsRow(
    count: Int,
    covers: List<String?>,
    repo: MusicDataRepository,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCoverArt(
            cover = PlaylistCover.Favorites(covers.filterNotNull().distinct().take(4)),
            resolver = { id -> repo.coverArtUrl(id, 128) },
            contentDescription = "Favorite songs",
            modifier = Modifier.size(56.dp),
            roundedDp = 4.dp,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Favorite songs", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                "$count songs",
                style = MaterialTheme.typography.bodySmall,
                color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
            )
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
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favs by vm.favs.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val refreshing = when (tab) {
        LibraryTab.ALBUMS -> albums is UiState.Loading
        LibraryTab.ARTISTS -> artists is UiState.Loading
        LibraryTab.SONGS -> songs is UiState.Loading
        LibraryTab.PLAYLISTS -> playlists is UiState.Loading
        LibraryTab.FAVORITES -> favs is UiState.Loading
        LibraryTab.DOWNLOADS -> false
    }
    // Cache hits never show Loading, so a background refresh needs its own
    // signal. It is the same small pull indicator, never a full-screen spinner.
    val networkRefreshing by vm.refreshing.collectAsStateWithLifecycle()
    val failed by vm.refreshFailed.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = refreshing || networkRefreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            // At most one small line, and it takes itself away: a failed
            // refresh keeps the old list on screen.
            AnimatedVisibility(
                visible = failed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            ) {
                Text(
                    "Couldn't refresh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
