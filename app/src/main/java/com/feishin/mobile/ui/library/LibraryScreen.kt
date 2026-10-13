package com.feishin.mobile.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.components.ActionTarget
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
    val tab by vm.tab.collectAsState()
    val grid by vm.grid.collectAsState()
    val sort by vm.sort.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    val nowPlaying by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()

    // The nav arg is authoritative when it changes (Explore pills / restored entry).
    LaunchedEffect(initialTab) { initialTab?.let { vm.selectTab(it) } }

    Column(Modifier.fillMaxSize()) {
        // Title row: title on the left, view/sort controls on the right, so the
        // chip row below can scroll horizontally without colliding with them.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.toggleGrid() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (grid) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle view",
                )
            }
            IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
            }
            DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = { vm.setSort(s); sortMenu = false },
                    )
                }
            }
        }
        // Chips own their line and scroll on their own.
        FilterChipRow(
            options = LibraryTab.entries.map(::tabLabel),
            selected = tabLabel(tab),
            onSelect = { label -> vm.selectTab(LibraryTab.entries.first { tabLabel(it) == label }) },
        )
        LibraryPullToRefresh(vm, tab) {
            when (tab) {
                LibraryTab.ALBUMS -> {
                    val ui by vm.albums.collectAsState()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(message = s.message, onRetry = { vm.loadAlbums(true) })
                        is UiState.Empty -> EmptyState("No albums")
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
                LibraryTab.ARTISTS -> {
                    val ui by vm.artists.collectAsState()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState("No artists")
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
                                    androidx.compose.foundation.layout.Row(
                                        Modifier.fillMaxWidth()
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                            .clickable(onClick = { onArtist(a.id) })
                                            .padding(16.dp, 12.dp),
                                    ) {
                                        Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        Text("${a.albumCount}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryTab.SONGS -> {
                    val ui by vm.songs.collectAsState()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState("No songs")
                        is UiState.Data -> LazyColumn(
                            Modifier.fillMaxSize(),
                            state = rememberSavableListState(),
                            contentPadding = PaddingValues(bottom = 160.dp),
                        ) {
                            items(s.value, key = { it.id }) { song ->
                                TrackRow(
                                    track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                                    onClick = { player.playAll(s.value, s.value.indexOf(song), source = "Songs") },
                                    onActions = { onActions(ActionTarget.Track(song)) },
                                    onArtist = { id -> onArtist(id) },
                                    isCurrent = song.id == nowPlaying?.id, isPlaying = isPlaying,
                                )
                            }
                        }
                    }
                }
                LibraryTab.PLAYLISTS -> {
                    val ui by vm.playlists.collectAsState()
                    val favsUi by vm.favs.collectAsState()
                    val favTracks = (favsUi as? UiState.Data)?.value?.tracks.orEmpty()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> {
                            // Even with no server playlists, Favorite songs is pinned.
                            FavoriteSongsRow(
                                count = favTracks.size,
                                covers = favTracks.map { it.coverArt },
                                repo = repo,
                                onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                            )
                        }
                        is UiState.Data -> LazyColumn(
                            Modifier.fillMaxSize(),
                            state = rememberSavableListState(),
                            contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom),
                        ) {
                            item(key = "favorites") {
                                FavoriteSongsRow(
                                    count = favTracks.size,
                                    covers = favTracks.map { it.coverArt },
                                    repo = repo,
                                    onClick = { onPlaylist(FAVORITE_SONGS_PLAYLIST_ID) },
                                )
                            }
                            items(s.value, key = { it.id }) { p ->
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
                LibraryTab.FAVORITES -> {
                    val ui by vm.favs.collectAsState()
                    when (val s = ui) {
                        is UiState.Loading -> com.feishin.mobile.ui.LoadingBox()
                        is UiState.Error -> ErrorState(s.message, vm::refresh)
                        is UiState.Empty -> EmptyState("Nothing starred yet")
                        is UiState.Data -> {
                            val favs = s.value
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
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
                LibraryTab.DOWNLOADS -> {
                    DownloadsTab(
                        downloads = downloads,
                        repo = repo,
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

/** Downloaded music: totals, then the songs, with remove buttons. */
@Composable
private fun DownloadsTab(
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    repo: MusicDataRepository,
    onPlaySong: (com.feishin.mobile.data.db.DownloadEntity) -> Unit,
    onRemove: (String) -> Unit,
    onManage: () -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    val all by downloads.downloads.collectAsState(initial = emptyList())
    val done = all.filter { it.statusEnum == com.feishin.mobile.data.db.DownloadStatus.COMPLETED }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (all.isEmpty()) {
        EmptyState("Nothing downloaded yet. Use the download button on a song, album or playlist.")
        return
    }
    val total = done.fold(0L) { acc, e -> acc + e.sizeBytes }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom),
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
                OutlinedButton(onClick = onManage) { Text("Manage") }
            }
        }
        items(done, key = { it.songId }) { entity ->
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
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favs by vm.favs.collectAsState()
    val songs by vm.songs.collectAsState()
    val refreshing = when (tab) {
        LibraryTab.ALBUMS -> albums is UiState.Loading
        LibraryTab.ARTISTS -> artists is UiState.Loading
        LibraryTab.SONGS -> songs is UiState.Loading
        LibraryTab.PLAYLISTS -> playlists is UiState.Loading
        LibraryTab.FAVORITES -> favs is UiState.Loading
        LibraryTab.DOWNLOADS -> false
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}
