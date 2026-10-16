package com.feishin.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.LocalTopBarController
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.ErrorState
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSavableListState
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing
import kotlinx.coroutines.launch

/** Strips HTML tags + common entities for biographies. */
fun stripHtmlBio(html: String): String =
    html.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    vm: ArtistDetailViewModel,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
    onBack: () -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val topSongsUi by vm.topSongs.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    when (val s = ui) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, vm::refresh)
        is UiState.Empty -> EmptyBox("Artist not found")
        is UiState.Data -> {
            val a = s.value
            LaunchedEffect(a.id, a.name) { vm.loadExtras(a.name) }
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            var topExpanded by remember(a.id) { mutableStateOf(false) }
            val topBar = LocalTopBarController.current
            // Edge-to-edge hero: this screen draws its own back button over the
            // image, so the shared bar stays hidden until the hero scrolls away.
            LaunchedEffect(a.id) { topBar.hideBar = true }
            androidx.compose.runtime.DisposableEffect(a.id) {
                onDispose { topBar.hideBar = false }
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                val listState = rememberSavableListState()
                // Publish the artist name so it fades into the shared top bar.
                // Derived once per scroll change instead of re-reading the
                // frequently-changing scroll state on every composition.
                val barScrolled by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
                    }
                }
                LaunchedEffect(a.name, barScrolled) {
                    topBar.title = a.name
                    // The hero is ~300dp tall: the title appears once it is gone.
                    topBar.scrolled = barScrolled
                }
                // Collapsing hero.
                //
                // The hero item keeps a FIXED height and collapses by parallax
                // (translation + fade), never by resizing. The old code shrank
                // the item height with the scroll offset (320dp / 520px), which
                // made every 1px of upward scroll grow the content above it by
                // 0.615px at the same time: scrolling up was ~62% eaten, which
                // is exactly the "hard to scroll up" report. With a fixed height
                // the list offset maps 1:1 to content movement in both
                // directions.
                val scrollPx by remember {
                    derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() }
                }
                val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding()
                val heroHeight = 340.dp + topInset
                // 1 while the hero is still showing its identity, 0 once it is
                // fully collapsed behind the top bar.
                val heroVisible = (1f - scrollPx / 420f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                    ) {
                        item {
                            ArtistHero(
                                name = a.name,
                                meta = "${a.albumCount} albums",
                                imageUrl = info?.imageUrl ?: repo.coverArtUrl(a.coverArt, 600),
                                heroHeight = heroHeight,
                                topInset = topInset,
                                artUrl = repo.coverArtUrl(a.coverArt, 600),
                                scrollPx = scrollPx,
                                onBack = onBack,
                            )
                        }
                        item {
                            ArtistControls(
                                artist = Artist(a.id, a.name, a.coverArt, a.albumCount, a.starred),
                                starred = a.starred,
                                onStar = { vm.toggleStar(a.starred) },
                                onActions = onActions,
                                player = player,
                                repo = repo,
                                messenger = messenger,
                            )
                        }
                    // Top songs.
                    // Server lists can repeat an id (Navidrome does for similar
                    // artists and occasionally tracks); duplicate lazy keys
                    // crash the whole screen, so every list here is deduped by
                    // id first. The deduped list is also what plays, so the
                    // tapped index always matches.
                    when (val ts = topSongsUi) {
                        is UiState.Data -> if (ts.value.isNotEmpty()) {
                            val topSongs = ts.value.distinctBy { it.id }
                            item { SectionHeader(title = "Top songs") }
                            items(topSongs.take(5), key = { "top${it.id}" }) { t ->
                                TrackRow(
                                    track = t, artUrl = repo.coverArtUrl(t.coverArt, 128),
                                    onClick = { player.playAll(topSongs, topSongs.indexOf(t), source = a.name) },
                                    onActions = { onActions(ActionTarget.Track(t)) },
                                    isCurrent = t.id == nowPlaying?.id, isPlaying = isPlaying,
                                )
                            }
                            if (topSongs.size > 5) {
                                if (!topExpanded) {
                                    item {
                                        Text(
                                            "Show more",
                                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(VusicColors.SurfaceHigh)
                                                .clickable { topExpanded = true }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                } else {
                                    items(topSongs.drop(5), key = { "topx${it.id}" }) { t ->
                                        TrackRow(
                                            track = t, artUrl = repo.coverArtUrl(t.coverArt, 128),
                                            onClick = { player.playAll(topSongs, topSongs.indexOf(t), source = a.name) },
                                            onActions = { onActions(ActionTarget.Track(t)) },
                                            isCurrent = t.id == nowPlaying?.id, isPlaying = isPlaying,
                                        )
                                    }
                                }
                            }
                        }
                        is UiState.Error, is UiState.Empty -> Unit
                        is UiState.Loading -> Unit
                    }
                    // Albums grid, newest year first.
                    if (a.albums.isNotEmpty()) {
                        item { SectionHeader(title = "Albums") }
                        val sorted = a.albums.distinctBy { it.id }.sortedByDescending { it.year ?: 0 }
                        val rows = sorted.chunked(2)
                        items(rows, key = { r -> r.joinToString("+") { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                row.forEach { al ->
                                    AlbumCard(al, repo.coverArtUrl(al.coverArt, 300), onClick = { onAlbum(al.id) }, modifier = Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    // Similar artists.
                    val similar = info?.similarArtists.orEmpty().distinctBy { it.id }
                    if (similar.isNotEmpty()) {
                        item { SectionHeader(title = "Fans might also like") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                items(similar, key = { it.id }, contentType = { "item" }) { sa ->
                                    Column(
                                        Modifier.width(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onArtist(sa.id) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        CoverImage(
                                            sa.coverArt?.let { repo.coverArtUrl(it, 300) },
                                            sa.name, modifier = Modifier.size(96.dp), roundedDp = 48.dp,
                                        )
                                        Text(sa.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    // Biography.
                    val bio = stripHtmlBio(info?.biography.orEmpty())
                    if (bio.isNotBlank()) {
                        item { SectionHeader(title = "About") }
                        item {
                            var expanded by remember(a.id) { mutableStateOf(false) }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                Text(
                                    bio, style = MaterialTheme.typography.bodyMedium,
                                    color = VusicColors.SecondaryText,
                                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (expanded) "Show less" else "Read more",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { expanded = !expanded }
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    }
                    // Collapsed top bar, drawn OVER the list once the hero has
                    // scrolled away. Owns its own back arrow, so the action row
                    // and every list item can scroll all the way to the top edge
                    // without anything being clipped and without losing the way
                    // back. Fades in on the same scroll offset as the hero —
                    // and stays fully on once the hero has scrolled off
                    // entirely (past that point firstVisibleItemScrollOffset
                    // resets to the next item, so the offset alone would read
                    // as "back at the top" and the bar would vanish).
                    val heroGone by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 0 }
                    }
                    val barAlpha = if (heroGone) {
                        1f
                    } else {
                        ((scrollPx / 420f) - 0.55f).coerceIn(0f, 1f) / 0.45f
                    }
                    ArtistCollapsedTopBar(
                        name = a.name,
                        alpha = barAlpha,
                        topInset = topInset,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(
    name: String,
    meta: String,
    imageUrl: String?,
    heroHeight: Dp,
    topInset: Dp,
    artUrl: String?,
    scrollPx: Float,
    onBack: () -> Unit = {},
) {
    // The image reaches under the status bar and fills the full width. When the
    // provider image is missing or low-res, a blurred, scaled copy sits behind
    // it so no empty area shows; with no art at all, a gradient from the cover
    // colour carries a large initial.
    // Parallax: the artwork drifts up slower than the list and fades out, so the
    // hero reads as collapsing without the item ever changing height.
    Box(
        Modifier.fillMaxWidth().height(heroHeight)
            .graphicsLayer {
                val t = (scrollPx / 420f).coerceIn(0f, 1f)
                translationY = -scrollPx * 0.5f
                alpha = 1f - t
            },
    ) {
        val resolved = imageUrl
        val backdrop = artUrl
        if (resolved != null) {
            // Blurred backdrop guarantees full-bleed coverage for tiny/odd art.
            if (backdrop != null && backdrop != resolved) {
                AsyncImage(
                    backdrop, null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f,
                )
            } else {
                AsyncImage(
                    resolved, null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            AsyncImage(
                resolved, name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(VusicColors.SurfaceHigh, VusicColors.Background),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.displayLarge,
                    color = VusicColors.SecondaryText,
                )
            }
        }
        // Bottom gradient fades the image into the page background with no edge.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to VusicColors.Background,
                ),
            ),
        )
        // Translucent circular back button inside the safe area.
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(start = 12.dp, top = topInset + 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            // Over the hero image: keep white, it is a scrim backdrop.
            Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * Collapsed header for the artist screen: a fixed-height bar with a back arrow
 * and the artist name, fading in as the hero scrolls away. It sits above the
 * list in the same Box, so it never scrolls with the content and never clips a
 * control underneath it.
 */
@Composable
private fun BoxScope.ArtistCollapsedTopBar(
    name: String,
    alpha: Float,
    topInset: Dp,
    onBack: () -> Unit,
) {
    if (alpha <= 0.001f) return
    Row(
        Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    listOf(
                        VusicColors.Background.copy(alpha = 0.97f),
                        VusicColors.Background.copy(alpha = 0.92f),
                    ),
                ),
            )
            .padding(top = topInset, bottom = 8.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
    }
}

@Composable
private fun ArtistControls(
    artist: Artist,
    starred: Boolean,
    onStar: () -> Unit,
    onActions: (ActionTarget) -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
) {
    val scope = rememberCoroutineScope()
    var gathering by remember { mutableStateOf(false) }

    suspend fun allTracks(): List<Track> {
        val out = ArrayList<Track>()
        val detail = (repo.artist(artist.id) as? Result.Ok)?.value ?: return emptyList()
        for (al in detail.albums) {
            when (val r = repo.album(al.id)) {
                is Result.Ok -> out += r.value.tracks
                is Result.Err -> Unit
            }
        }
        return out
    }

    fun withTracks(shuffle: Boolean) {
        if (gathering) return
        gathering = true
        scope.launch {
            try {
                val tracks = allTracks()
                if (tracks.isEmpty()) messenger.show("No tracks found for ${artist.name}")
                else player.playAll(tracks, shuffle = shuffle, source = artist.name)
            } finally {
                gathering = false
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(20.dp, 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { withTracks(false) },
            enabled = !gathering,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
        }
        CircleIconButtonShim(onClick = { withTracks(true) }, enabled = !gathering, icon = Icons.Default.Shuffle, description = "Shuffle")
        CircleIconButtonShim(
            onClick = onStar,
            icon = if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            description = "Favorite",
        )
        CircleIconButtonShim(
            onClick = { onActions(ActionTarget.Artist(artist)) },
            icon = Icons.Default.MoreVert, description = "More",
        )
    }
}

@Composable
private fun CircleIconButtonShim(onClick: () -> Unit, icon: ImageVector, description: String, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp).clip(CircleShape).background(VusicColors.SurfaceHigh),
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

