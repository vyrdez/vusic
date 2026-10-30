package com.feishin.mobile.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.FilterChipRow
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.components.SkeletonLoader
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.library.LibraryTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onSettings: () -> Unit,
    onExplore: (LibraryTab) -> Unit,
    onGenres: () -> Unit,
    onActions: (ActionTarget) -> Unit,
    username: String,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && state.recent.isEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    HomeTopBar(username, onSettings)
                    SpeedDialSkeleton()
                    SkeletonLoader()
                    SkeletonLoader()
                }
            }
            state.globalError != null -> ErrorBox(state.globalError, vm::refresh)
            else -> HomeContent(state, vm, player, repo, messenger, onAlbum, onArtist, onPlaylist, onSettings, onExplore, onGenres, onActions, username)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUi,
    vm: HomeViewModel,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onSettings: () -> Unit,
    onExplore: (LibraryTab) -> Unit,
    onGenres: () -> Unit,
    onActions: (ActionTarget) -> Unit,
    username: String,
) {
    val scope = rememberCoroutineScope()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberSavableListState(),
        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
    ) {
        item { HomeTopBar(username, onSettings) }
        item {
            FilterChipRow(options = state.genres, selected = state.selectedGenre, onSelect = vm::selectGenre)
        }
        if (state.speedDial.isNotEmpty()) {
            item {
                SpeedDialPager(
                    tiles = state.speedDial,
                    repo = repo,
                    onAlbum = onAlbum,
                    onPlaylist = { id ->
                        scope.launch {
                            (repo.playlist(id) as? Result.Ok)?.value?.let {
                                player.playAll(it.tracks, source = it.name)
                            }
                        }
                    },
                )
            }
        }
        if (state.recent.isNotEmpty()) {
            item { SectionHeader(title = "Listen again") }
            item {
                AlbumCarousel(state.recent, repo, onAlbum) { a ->
                    scope.launch {
                        (repo.album(a.id) as? Result.Ok)?.value?.let { player.playAll(it.tracks, source = it.name) }
                    }
                }
            }
        }
        if (state.quickPicks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Quick picks",
                    onPlayAll = { player.playAll(state.quickPicks, source = "Quick picks") },
                )
            }
            item {
                com.feishin.mobile.ui.components.SongPagerSection(
                    tracks = state.quickPicks,
                    sourceName = "Quick picks",
                    player = player, repo = repo,
                    nowPlayingId = nowPlaying?.id, isPlaying = isPlaying,
                    onActions = { onActions(ActionTarget.Track(it)) },
                    onArtist = onArtist,
                )
            }
        }
        if (state.newest.isNotEmpty()) {
            item { SectionHeader(title = "New releases") }
            item { AlbumCarousel(state.newest, repo, onAlbum) {} }
        }
        if (state.forgottenAlbums.isNotEmpty() || state.forgottenTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Forgotten favorites",
                    onPlayAll = {
                        player.playAll(state.forgottenTracks, source = "Forgotten favorites")
                    },
                )
            }
            if (state.forgottenAlbums.isNotEmpty()) {
                item { AlbumCarousel(state.forgottenAlbums, repo, onAlbum) {} }
            }
            // Same pager as Quick picks - one shared component, no duplication.
            item {
                com.feishin.mobile.ui.components.SongPagerSection(
                    tracks = state.forgottenTracks,
                    sourceName = "Forgotten favorites",
                    player = player, repo = repo,
                    nowPlayingId = nowPlaying?.id, isPlaying = isPlaying,
                    onActions = { onActions(ActionTarget.Track(it)) },
                    onArtist = onArtist,
                )
            }
        }
        val artist = state.moreArtist
        if (artist != null && state.moreArtistAlbums.isNotEmpty()) {
            item {
                SectionHeader(
                    label = "More from",
                    title = artist.name,
                    avatarUrl = repo.coverArtUrl(artist.coverArt ?: state.moreArtistAlbums.firstOrNull()?.coverArt, 128),
                    onOpen = { artist.id.takeIf { it.isNotBlank() }?.let(onArtist) },
                )
            }
            item { AlbumCarousel(state.moreArtistAlbums, repo, onAlbum) {} }
        }
        if (state.mixes.isNotEmpty()) {
            item { SectionHeader(title = "Mixed for you") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.mixes, key = { it.name }, contentType = { "item" }) { mix ->
                        MixCard(mix = mix, repo = repo, onClick = { player.playAll(mix.tracks, source = mix.name) })
                    }
                }
            }
        }
        val discover = state.discover
        if (discover != null) {
            item { SectionHeader(title = "Your daily discover") }
            item {
                DiscoverCard(
                    album = discover, repo = repo,
                    onClick = { onAlbum(discover.id) },
                    onPlay = {
                        scope.launch {
                            (repo.album(discover.id) as? Result.Ok)?.value?.let { player.playAll(it.tracks, source = it.name) }
                        }
                    },
                )
            }
        }
        if (state.frequent.isNotEmpty()) {
            item { SectionHeader(title = "Most played") }
            item { AlbumCarousel(state.frequent, repo, onAlbum) {} }
        }
        item { SectionHeader(title = "Explore") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExplorePill("New releases", Modifier.weight(1f)) { onExplore(LibraryTab.ALBUMS) }
                ExplorePill("Genres", Modifier.weight(1f)) { onGenres() }
                ExplorePill("Playlists", Modifier.weight(1f)) { onExplore(LibraryTab.PLAYLISTS) }
            }
        }
        if (state.recent.isEmpty() && state.newest.isEmpty() && state.frequent.isEmpty() &&
            state.quickPicks.isEmpty() && state.mixes.isEmpty()
        ) {
            item { EmptyState("Nothing here yet — play something and come back.") }
        }
    }
}

/**
 * Shimmering placeholder for the speed-dial grid shown during the first load.
 */
@Composable
private fun SpeedDialSkeleton() {
    val t = rememberInfiniteTransition(label = "sd-skel")
    val alpha by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "sd-alpha",
    )
    val color = com.feishin.mobile.ui.theme.VusicColors.SurfaceHigh.copy(alpha = alpha)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 32.dp)) {
        Box(Modifier.width(140.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            Row(Modifier.fillMaxWidth()) {
                repeat(3) {
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(color))
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(username: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.MusicNote, "Vusic", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        Text("Vusic", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                username.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Speed dial as a 3x3 page pager (up to 3 pages / 27 tiles) with animated page
 * dots, mirroring YouTube Music. The dots track the real page count and hide
 * when there is only one page.
 */
@Composable
private fun SpeedDialPager(
    tiles: List<SpeedTile>,
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
    onPlaylist: (String) -> Unit,
) {
    val pages = tiles.chunked(9).take(3)
    if (pages.isEmpty()) return
    val pager = rememberPagerState(pageCount = { pages.size })
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = "Speed dial")
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { page ->
            Column(Modifier.fillMaxWidth()) {
                pages[page].chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { tile ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
                                SpeedTile(tile, repo, onAlbum, onPlaylist)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val selected = i == pager.currentPage
                    val size by animateDpAsState(if (selected) 8.dp else 6.dp, label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(size)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTile(tile: SpeedTile, repo: MusicDataRepository, onAlbum: (String) -> Unit, onPlaylist: (String) -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            .clickable {
                when (val t = tile.target) {
                    is SpeedTarget.AlbumTarget -> onAlbum(t.album.id)
                    is SpeedTarget.PlaylistTarget -> onPlaylist(t.playlist.id)
                }
            },
    ) {
        if (tile.playlistCover != null) {
            PlaylistCoverArt(
                cover = tile.playlistCover,
                resolver = { id -> repo.coverArtUrl(id, 300) },
                contentDescription = tile.title,
                modifier = Modifier.fillMaxSize(),
                roundedDp = 8.dp,
            )
        } else {
            CoverImage(repo.coverArtUrl(tile.coverArt, 300), tile.title, modifier = Modifier.fillMaxSize(), roundedDp = 8.dp, contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(tile.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumCarousel(
    albums: List<Album>,
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
    onPlay: (Album) -> Unit = {},
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        // Server lists can repeat an id; duplicate lazy keys crash the list.
        items(albums.distinctBy { it.id }, key = { it.id }, contentType = { "item" }) { a ->
            AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) }, modifier = Modifier.width(140.dp))
        }
    }
}

@Composable
private fun QuickPicksPager(
    tracks: List<Track>,
    player: PlayerManager,
    repo: MusicDataRepository,
    nowPlayingId: String?,
    isPlaying: Boolean,
    onActions: (ActionTarget) -> Unit,
    onArtist: (String) -> Unit = {},
) {
    com.feishin.mobile.ui.components.SongPagerSection(
        tracks = tracks,
        sourceName = "Quick picks",
        player = player,
        repo = repo,
        nowPlayingId = nowPlayingId,
        isPlaying = isPlaying,
        onActions = { onActions(ActionTarget.Track(it)) },
        onArtist = onArtist,
    )
}

@Composable
private fun MixCard(mix: Mix, repo: MusicDataRepository, onClick: () -> Unit) {
    Column(
        Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val covers = (mix.covers + List(4) { null }).take(4)
            @Composable
            fun androidx.compose.foundation.layout.RowScope.cell(coverId: String?) {
                CoverImage(repo.coverArtUrl(coverId, 300), mix.name, modifier = Modifier.weight(1f).fillMaxSize(), roundedDp = 0.dp)
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    cell(covers.getOrNull(0))
                    cell(covers.getOrNull(1))
                }
                Row(Modifier.weight(1f)) {
                    cell(covers.getOrNull(2))
                    cell(covers.getOrNull(3))
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                ),
            )
            Text(
                mix.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
        Text(
            "${mix.tracks.size} songs",
            style = MaterialTheme.typography.bodySmall, color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

@Composable
private fun DiscoverCard(album: Album, repo: MusicDataRepository, onClick: () -> Unit, onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(repo.coverArtUrl(album.coverArt, 600), album.name, modifier = Modifier.size(120.dp), roundedDp = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlay).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ExplorePill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
