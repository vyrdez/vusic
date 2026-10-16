package com.feishin.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.SearchGrouping
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.FilterChipRow
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.ui.components.SkeletonLoader
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.components.SubSectionHeader
import com.feishin.mobile.ui.components.ArtistAvatar
import com.feishin.mobile.ui.theme.VusicColors
import kotlinx.coroutines.launch

private val FILTER_LABELS = listOf("All", "Songs", "Albums", "Artists", "Playlists")

private fun filterOf(label: String): SearchFilter = when (label) {
    "Songs" -> SearchFilter.SONGS
    "Albums" -> SearchFilter.ALBUMS
    "Artists" -> SearchFilter.ARTISTS
    "Playlists" -> SearchFilter.PLAYLISTS
    else -> SearchFilter.ALL
}

private fun labelOf(filter: SearchFilter): String = when (filter) {
    SearchFilter.ALL -> "All"
    SearchFilter.SONGS -> "Songs"
    SearchFilter.ALBUMS -> "Albums"
    SearchFilter.ARTISTS -> "Artists"
    SearchFilter.PLAYLISTS -> "Playlists"
}

private fun ArtistInfoPlaceholder() = Unit

@Composable
fun SearchScreen(
    vm: SearchViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val q by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val visible by vm.visibleCount.collectAsStateWithLifecycle()
    val songsExpanded by vm.songsExpanded.collectAsStateWithLifecycle()
    val settingsState by settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val offlineNotice by vm.offlineNotice.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(ui) {
        if (ui is UiState.Data && q.isNotBlank()) settings.addRecentSearch(q)
    }
    if (offlineNotice && (ui is UiState.Data || ui is UiState.Empty)) {
        Text(
            "Offline — searching downloaded music",
            style = MaterialTheme.typography.labelMedium,
            color = VusicColors.SecondaryText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }

    // No navigationBarsPadding and no imePadding() anywhere here: the scaffold's
    // content box already pads max(nav bar, IME) exactly once, so adding either
    // here double-counts the inset and pushes the content up over the keyboard
    // or leaves a dead band above the nav bar.
    Column(Modifier.fillMaxSize()) {
        // Rounded pill: back arrow, field, clear X. No voice button.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = { keyboard?.hide(); onBack() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(VusicColors.SurfaceHigh)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                if (q.isEmpty()) {
                    Text("Artists, albums, songs", style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText)
                }
                BasicTextField(
                    value = q,
                    onValueChange = vm::onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (q.isNotEmpty()) {
                IconButton(onClick = { vm.onQuery("") }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }
        FilterChipRow(
            options = FILTER_LABELS,
            selected = labelOf(filter),
            onSelect = { vm.onFilter(filterOf(it)) },
        )
        when (val s = ui) {
            is UiState.Loading -> {
                SkeletonLoader()
                SkeletonLoader()
            }
            is UiState.Error -> ErrorState(message = s.message, onRetry = { vm.onQuery(q) })
            is UiState.Empty -> {
                if (q.isBlank()) {
                    RecentSearches(
                        recent = settingsState?.recentSearches.orEmpty(),
                        onRun = { vm.onQuery(it) },
                        onRemove = { r -> scope.launch { settings.removeRecentSearch(r) } },
                        onClearAll = { scope.launch { settings.clearRecentSearches() } },
                    )
                } else {
                    EmptyState("No results for \"$q\"")
                }
            }
            is UiState.Data -> {
                val res = s.value
                if (filter == SearchFilter.ALL) {
                    AllResults(
                        res = res, songsExpanded = songsExpanded,
                        player = player, repo = repo, vm = vm,
                        nowPlayingId = nowPlaying?.id, isPlaying = isPlaying,
                        onAlbum = onAlbum, onArtist = onArtist, onPlaylist = onPlaylist,
                        onActions = onActions,
                        onExpandSongs = vm::expandSongs,
                        onSongsFilter = { vm.onFilter(SearchFilter.SONGS) },
                    )
                } else {
                    SingleTypeResults(
                        res = res, filter = filter, visible = visible,
                        player = player, repo = repo, vm = vm,
                        nowPlayingId = nowPlaying?.id, isPlaying = isPlaying,
                        onAlbum = onAlbum, onArtist = onArtist, onPlaylist = onPlaylist,
                        onActions = onActions,
                        onShowMore = vm::showMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onRun: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recent.isEmpty()) {
        EmptyState("Type to search")
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onClearAll) { Text("Clear all") }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current)) {
        items(recent, key = { "rs$it" }, contentType = { "item" }) { r ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRun(r) }
                    .padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, null, tint = VusicColors.SecondaryText)
                Text(r, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = { onRemove(r) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = VusicColors.SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun SectionTitlePlaceholder() = Unit

@Composable
private fun AllResults(
    res: SearchResults,
    songsExpanded: Boolean,
    player: PlayerManager,
    repo: MusicDataRepository,
    vm: SearchViewModel,
    nowPlayingId: String?,
    isPlaying: Boolean,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
    onExpandSongs: () -> Unit,
    onSongsFilter: () -> Unit,
) {
    // Server hits can repeat an id; duplicate lazy keys crash the list, so each
    // section plays and renders from its own deduped list.
    val tracks = remember(res.tracks) { res.tracks.distinctBy { it.id } }
    val albums = remember(res.albums) { res.albums.distinctBy { it.id } }
    val artists = remember(res.artists) { res.artists.distinctBy { it.id } }
    val playlists = remember(res.playlists) { res.playlists.distinctBy { it.id } }
    val top = SearchGrouping.topResult(res)
    val songs = if (songsExpanded) tracks else SearchGrouping.previewSongs(res).distinctBy { it.id }
    val moreSongs = !songsExpanded && SearchGrouping.remainingSongs(res).isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberSavableListState(),
        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
    ) {
        if (top != null) {
            item(key = "top") {
                SubSectionHeader("Top result")
                when (top) {
                    is SearchGrouping.TopHit.Artist -> TopArtistRow(
                        id = top.id, name = top.name, repo = repo, vm = vm,
                        // Last visual fallback: the art of a matching song hit,
                        // which Navidrome does provide even when the artist and
                        // album records carry none.
                        fallbackCoverArt = res.tracks.firstOrNull {
                            it.artistId == top.id || it.artist.equals(top.name, ignoreCase = true)
                        }?.coverArt,
                        onArtist = onArtist, onActions = onActions,
                    )
                    is SearchGrouping.TopHit.Album -> {
                        val album = res.albums.firstOrNull { it.id == top.id }
                        if (album != null) {
                            AlbumRow(album, repo, onAlbum, onActions)
                        }
                    }
                }
            }
        }
        if (res.tracks.isNotEmpty()) {
            item(key = "songs-h") { SubSectionHeader("Songs") }
            items(songs, key = { "s${it.id}" }) { song ->
                TrackRow(
                    track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                    onClick = { player.playAll(tracks, tracks.indexOf(song), source = "Search") },
                    onActions = { onActions(ActionTarget.Track(song)) },
                    onArtist = { id -> onArtist(id) },
                    isCurrent = song.id == nowPlayingId, isPlaying = isPlaying,
                )
            }
            if (moreSongs) {
                item(key = "songs-more") {
                    TextButton(onClick = onExpandSongs, modifier = Modifier.padding(start = 8.dp)) {
                        Text("View more")
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item(key = "albums-h") { SubSectionHeader("Albums") }
            items(albums, key = { "b${it.id}" }) { a ->
                AlbumRow(a, repo, onAlbum, onActions)
            }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists-h") { SubSectionHeader("Artists") }
            items(artists, key = { "a${it.id}" }) { a ->
                ArtistSearchRow(a, repo, vm, onArtist, onActions)
            }
        }
        if (playlists.isNotEmpty()) {
            item(key = "playlists-h") { SubSectionHeader("Playlists") }
            items(playlists, key = { "p${it.id}" }) { p ->
                PlaylistSearchRow(p, repo, onPlaylist, onActions)
            }
        }
    }
}

@Composable
private fun SingleTypeResults(
    res: SearchResults,
    filter: SearchFilter,
    visible: Int,
    player: PlayerManager,
    repo: MusicDataRepository,
    vm: SearchViewModel,
    nowPlayingId: String?,
    isPlaying: Boolean,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
    onShowMore: () -> Unit,
) {
    val listState = rememberSavableListState()
    // Infinite scroll: reveal the next page as the end approaches.
    val atEnd by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { info ->
                val total = listState.layoutInfo.totalItemsCount
                total > 0 && info.index >= total - 4
            } == true
        }
    }
    LaunchedEffect(atEnd, visible) { if (atEnd) onShowMore() }
    // Same duplicate-id guard as AllResults above: render and play from the
    // deduped lists so keys stay unique and indices match.
    val tracks = remember(res.tracks) { res.tracks.distinctBy { it.id } }
    val albums = remember(res.albums) { res.albums.distinctBy { it.id } }
    val artists = remember(res.artists) { res.artists.distinctBy { it.id } }
    val playlists = remember(res.playlists) { res.playlists.distinctBy { it.id } }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
    ) {
        when (filter) {
            SearchFilter.SONGS -> {
                val shown = tracks.take(visible)
                items(shown, key = { "s${it.id}" }) { song ->
                    TrackRow(
                        track = song, artUrl = repo.coverArtUrl(song.coverArt, 128),
                        onClick = { player.playAll(tracks, tracks.indexOf(song), source = "Search") },
                        onActions = { onActions(ActionTarget.Track(song)) },
                        onArtist = { id -> onArtist(id) },
                        isCurrent = song.id == nowPlayingId, isPlaying = isPlaying,
                    )
                }
            }
            SearchFilter.ALBUMS -> {
                items(albums.take(visible), key = { "b${it.id}" }) { a ->
                    AlbumRow(a, repo, onAlbum, onActions)
                }
            }
            SearchFilter.ARTISTS -> {
                items(artists.take(visible), key = { "a${it.id}" }) { a ->
                    ArtistSearchRow(a, repo, vm, onArtist, onActions)
                }
            }
            SearchFilter.PLAYLISTS -> {
                items(playlists.take(visible), key = { "p${it.id}" }) { p ->
                    PlaylistSearchRow(p, repo, onPlaylist, onActions)
                }
            }
            SearchFilter.ALL -> Unit
        }
    }
}

@Composable
private fun AllResultsPlaceholder() = Unit

@Composable
private fun TopArtistRow(
    id: String,
    name: String,
    repo: MusicDataRepository,
    vm: SearchViewModel,
    fallbackCoverArt: String? = null,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    // Resolve the provider photo for the top hit lazily (same chain as Library).
    val artist = remember(id, name) { Artist(id, name, fallbackCoverArt, 0, false) }
    LaunchedEffect(id) { vm.prefetchArtistPhoto(artist) }
    val resolved = vm.artistInfoImage(id)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VusicColors.SurfaceHigh)
            .clickable { onArtist(id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(
            name = name,
            infoImageUrl = resolved,
            coverArtUrl = artist.coverArt?.let { repo.coverArtUrl(it, 256) },
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Artist", style = MaterialTheme.typography.labelMedium, color = VusicColors.SecondaryText)
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = { onAlbum(album.id) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(repo.coverArtUrl(album.coverArt, 128), album.name, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(album.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Album • ${album.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onActions(ActionTarget.Album(album)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreVert, "Album options")
        }
    }
}

@Composable
private fun ArtistSearchRow(
    artist: Artist,
    repo: MusicDataRepository,
    vm: SearchViewModel,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    LaunchedEffect(artist.id) { vm.prefetchArtistPhoto(artist) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onArtist(artist.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(
            name = artist.name,
            infoImageUrl = vm.artistInfoImage(artist.id),
            coverArtUrl = artist.coverArt?.let { repo.coverArtUrl(it, 256) },
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
            )
        }
        IconButton(onClick = { onActions(ActionTarget.Artist(artist)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreVert, "Artist options")
        }
    }
}

@Composable
private fun PlaylistSearchRow(
    playlist: Playlist,
    repo: MusicDataRepository,
    onPlaylist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = { onPlaylist(playlist.id) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCoverArt(
            cover = PlaylistCover.select(playlist.coverArt, emptyList()),
            resolver = { id -> repo.coverArtUrl(id, 128) },
            contentDescription = playlist.name,
            modifier = Modifier.size(56.dp),
            roundedDp = 4.dp,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
            )
        }
        IconButton(
            onClick = {
                onActions(
                    ActionTarget.Playlist(
                        playlist,
                        null,
                    ),
                )
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.MoreVert, "Playlist options")
        }
    }
}
