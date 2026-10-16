package com.feishin.mobile.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EqualizerOverlay
import com.feishin.mobile.ui.components.MarqueeTitle
import com.feishin.mobile.ui.components.PlaylistPickerSheet
import com.feishin.mobile.ui.components.SheetState
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.components.rememberSheetState
import com.feishin.mobile.ui.components.scrimAlpha
import com.feishin.mobile.ui.theme.LocalPlayingColors
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.playingColor
import com.feishin.mobile.ui.components.formatDuration
import com.feishin.mobile.ui.components.shareText
import com.feishin.mobile.core.common.PlaybackProgress
import com.feishin.mobile.ui.theme.VusicColorMath
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState
import kotlin.math.roundToInt

/**
 * Full-screen Now Playing. Playback lives in the service, so rotation and the
 * system back gesture never disturb it; back (and swipe-down) only closes this UI.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerManager,
    lyricsVm: LyricsViewModel = hiltViewModel(),
    repo: MusicDataRepository,
    favs: FavoritesStore,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents,
    messenger: com.feishin.mobile.ui.components.UiMessenger,
    onClose: () -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onActions: (ActionTarget) -> Unit,
) {
    BackHandler(onBack = onClose)
    val song by player.current.collectAsStateWithLifecycle()
    val current = song
    LaunchedEffect(current?.id) { lyricsVm.load(current) }
    if (current == null) {
        // Unreachable while the scaffold only shows this overlay when something
        // is playing. Never render a placeholder here: a transparent box with
        // text would overlay whatever screen is underneath. Just close.
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize())
        return
    }

    // Artwork color scheme (shared with rows, mini player and queue). When it
    // is present the background is its tinted gradient and the content its
    // contrast-enforced surface text — in both themes. When it is null (the
    // setting is off, or no art), the legacy path below runs instead.
    val scheme = LocalPlayingColors.current
    val schemeTop = scheme?.let { playingColor(it.surfaceTop) }
    val schemeBottom = scheme?.let { playingColor(it.surfaceBottom) }

    // Legacy background: cover art's dominant colour, clamped dark enough
    // that white text always has strong contrast (fixes washed-out light covers).
    // Captured outside the remember/LaunchedEffect lambdas, which are not
    // composable and therefore cannot read VusicColors.
    val themeBackground = VusicColors.Background
    var targetBg by remember { mutableStateOf(themeBackground) }
    val ctx = LocalContext.current
    val artUrl = player.coverArtUrl(current.coverArt, 512)
    LaunchedEffect(artUrl, scheme) {
        // The shared scheme already sampled this art: decoding twice would
        // double the IO for the same pixels.
        if (scheme != null) return@LaunchedEffect
        if (artUrl == null) {
            targetBg = themeBackground
            return@LaunchedEffect
        }
        val sampled = withContext(Dispatchers.IO) {
            runCatching {
                // Small decode: we only need color statistics, not resolution.
                val req = ImageRequest.Builder(ctx).data(artUrl).size(128, 128).allowHardware(false).build()
                val bmp = (ctx.imageLoader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp?.let { Palette.from(it).generate().getDominantColor(0) }
            }.getOrNull()
        }
        if (sampled != null && sampled != 0) {
            val raw = Color(sampled)
            val (r, g, b) = VusicColorMath.clampToDark(raw.red, raw.green, raw.blue)
            targetBg = Color(r, g, b)
        }
    }
    val bg by animateColorAsState(targetBg, label = "np-bg")
    // Mid gradient stop: the same tint, darkened (opaque, so nothing bleeds through).
    val bgMid = VusicColorMath.scale(bg.red, bg.green, bg.blue, 0.55f)
        .let { (r, g, b) -> Color(r, g, b) }
    // Backdrop: scheme gradient when tinting, legacy dark gradient otherwise.
    // Every stop is OPAQUE so nothing bleeds through.
    val backdrop = if (scheme != null && schemeTop != null && schemeBottom != null) {
        Brush.verticalGradient(listOf(schemeTop, schemeBottom))
    } else {
        Brush.verticalGradient(listOf(bg, bgMid, VusicColors.Background))
    }
    // Content follows the backdrop: enforced surface text on a scheme
    // gradient, white on the legacy dark gradient.
    val content = scheme?.let { playingColor(it.surfaceText) } ?: Color.White
    val dim = scheme?.let { playingColor(it.surfaceSecondary) } ?: Color.White.copy(alpha = 0.72f)

    var lyricsMode by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A fresh queue (empty -> non-empty, e.g. playback after Clear) must not
    // inherit a stale open-sheet value from the previous session.
    val queue by player.queue.collectAsStateWithLifecycle()
    var hadEmptyQueue by remember { mutableStateOf(queue.isEmpty()) }

    // ---- Queue sheet --------------------------------------------------------
    // A single SheetState owns the position AND whether the sheet is open, so
    // the touch-blocking scrim is composed only while the sheet is genuinely up.
    // See SheetState for the freeze this replaced.
    val sheet = rememberSheetState()
    // Rotation / inset changes must never leave the sheet parked half-open.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { sheet.settle(0f) }
    // Queue emptied under the sheet: close through the one close path.
    LaunchedEffect(queue.isEmpty()) {
        if (queue.isEmpty()) {
            hadEmptyQueue = true
            sheet.close()
        } else if (hadEmptyQueue) {
            hadEmptyQueue = false
            sheet.close()
            showPicker = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top row: collapse far left, Song|Lyrics pill centred, ⋮ far right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = VusicSpacing.ScreenHorizontal, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Close", tint = content)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SongLyricsToggle(lyricsMode = lyricsMode, onSelect = { lyricsMode = it }, dim = dim)
                }
                IconButton(onClick = { onActions(ActionTarget.Track(current)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, "Options", tint = content)
                }
            }
            // Swipe-down-to-close area: art, titles, chips, seek, controls.
            // An Animatable, so a release/cancel always springs back to a
            // settled offset instead of stranding the layout mid-drag.
            val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
            if (!lyricsMode) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Nearly full-width art (16dp gutters) that still leaves room
                // for everything below it on short screens: capped by height.
                // Landscape/short screens get a compact budget instead.
                val compact = maxHeight < 600.dp
                val artSide = if (compact) {
                    minOf(maxWidth * 0.5f, maxHeight * 0.30f)
                } else {
                    minOf(maxWidth - 32.dp, maxHeight * 0.42f)
                }
                val gapTop = if (compact) 12.dp else 24.dp
                val gapArtTitle = if (compact) 8.dp else 12.dp
                val gapArtistChips = if (compact) 8.dp else 18.dp
                Column(
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                        .graphicsLayer { alpha = (1f - dragOffset.value / 700f).coerceIn(0.35f, 1f) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset.value > 260f) onClose()
                                    scope.launch { dragOffset.animateTo(0f, Motion.snapBackSpring()) }
                                },
                                onDragCancel = {
                                    // Cancelled drags must also settle.
                                    scope.launch { dragOffset.animateTo(0f, Motion.snapBackSpring()) }
                                },
                                onVerticalDrag = { change, amount ->
                                    if (amount > 0) {
                                        change.consume()
                                        val next = (dragOffset.value + amount).coerceAtMost(700f)
                                        scope.launch { dragOffset.snapTo(next) }
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(gapTop))
                    CoverImage(
                        artUrl, current.title,
                        modifier = Modifier.size(artSide),
                        roundedDp = 16.dp,
                    )
                    Spacer(Modifier.height(gapArtTitle))
                    // Title row: balanced 48dp slots on both sides keep the text
                    // visually centred; short titles are centred, long ones marquee.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(48.dp))
                        MarqueeTitle(
                            text = current.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = content,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            if (current.albumId?.isNotBlank() == true) {
                                IconButton(onClick = { current.albumId?.let(onAlbum) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.ChevronRight, "Go to album", tint = dim)
                                }
                            }
                        }
                    }
                    // Artist: centred when short, marquee with a clean ellipsis
                    // when long (multi-artist "A / B / C" included).
                    MarqueeTitle(
                        text = current.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = current.artistId?.isNotBlank() == true) {
                                current.artistId?.let { onArtist(it) }
                            },
                    )
                    Spacer(Modifier.height(gapArtistChips))
                    // Optimistic favorite toggle with rollback; guarded so fast
                    // taps can't interleave requests. Converges to the server
                    // collection once loaded (the queue's snapshot can be stale).
                    var starred by remember(current.id) { mutableStateOf(current.starred) }
                    var starring by remember(current.id) { mutableStateOf(false) }
                    val downloadState by downloads.observeSong(current.id).collectAsStateWithLifecycle(initialValue = null)
                    val favTracks by favs.tracks.collectAsStateWithLifecycle()
                    val favsLoaded by favs.loaded.collectAsStateWithLifecycle()
                    LaunchedEffect(current.id, favTracks, favsLoaded) {
                        if (favsLoaded && !starring) {
                            starred = favTracks.any { it.id == current.id }
                        }
                    }
                    LaunchedEffect(current.id) {
                        scope.launch { favs.refresh() }
                    }
                    NpChipsRow(
                        content = content,
                        starred = starred,
                        downloadState = downloadState,
                        onFavorite = {
                            if (starring) return@NpChipsRow
                            val next = !starred
                            starred = next
                            starring = true
                            scope.launch {
                                when (favs.setTrackStarred(current.id, next)) {
                                    is Result.Ok -> Unit
                                    is Result.Err -> {
                                        starred = !next
                                        messenger.show("Could not update favorite")
                                    }
                                }
                                starring = false
                            }
                        },
                        onSave = { showPicker = true },
                        onShare = { shareText(context, "${current.title} - ${current.artist}") },
                        onDownload = {
                            scope.launch {
                                when (downloadState?.statusEnum) {
                                    com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> {
                                        downloads.removeFromLibrary(current.id)
                                        messenger.show("Download removed")
                                    }
                                    com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
                                    com.feishin.mobile.data.db.DownloadStatus.QUEUED,
                                    -> {
                                        downloads.pause(current.id)
                                        messenger.show("Download cancelled")
                                    }
                                    com.feishin.mobile.data.db.DownloadStatus.PAUSED,
                                    com.feishin.mobile.data.db.DownloadStatus.FAILED,
                                    -> {
                                        downloads.resume(current.id)
                                        messenger.show("Download resumed")
                                    }
                                    null -> {
                                        downloads.enqueueTracks(listOf(current))
                                        messenger.show("Downloading: ${current.title}")
                                    }
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SeekBar(player, content)
                    Spacer(Modifier.height(4.dp))
                    ControlsRow(player, content)
                    // The queue entry sits near the bottom edge, above the
                    // gesture bar: tappable and swipe-up draggable.
                    Spacer(Modifier.weight(1f))
                    PlayingFromBottom(
                        player = player, dim = dim,
                        onClick = { sheet.openSheet() },
                        onDrag = { delta -> sheet.dragByPixels(delta) },
                        onDragEnd = { velocity -> sheet.onDragEnd(velocity) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            } else {
                LyricsPanel(lyricsVm, player, content, dim, modifier = Modifier.weight(1f))
            }
        }
    }

    // Composed ONLY while the sheet is open. When it is closed there is nothing at
    // all over Now Playing, so no invisible layer can swallow a tap.
    if (sheet.open) {
        QueueSheet(
            state = sheet,
            player = player, repo = repo, messenger = messenger,
            content = content, playlistEvents = playlistEvents,
            onActions = { onActions(ActionTarget.Track(it)) },
            onDismiss = { sheet.close() },
        )
    }
    // One close path for back press, tap-outside and the chevron.
    androidx.activity.compose.BackHandler(enabled = sheet.open) {
        sheet.close()
    }
    if (showPicker) {
        PlaylistPickerSheet(
            tracks = listOf(current), title = current.title,
            onDismiss = { showPicker = false }, repo = repo, messenger = messenger,
            playlistEvents = playlistEvents,
        )
    }
}

@Composable
private fun SongLyricsToggle(lyricsMode: Boolean, onSelect: (Boolean) -> Unit, dim: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(dim.copy(alpha = 0.18f)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Song" to false, "Lyrics" to true).forEach { (label, value) ->
            val selected = lyricsMode == value
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else dim,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun NpChipsRow(
    content: Color,
    starred: Boolean,
    downloadState: com.feishin.mobile.data.db.DownloadEntity?,
    onFavorite: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    val dlStatus = downloadState?.statusEnum
    val downloadLabel = when (dlStatus) {
        com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> "Downloaded"
        com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
        com.feishin.mobile.data.db.DownloadStatus.QUEUED,
        -> "${((downloadState?.progress ?: 0f) * 100).toInt()}%"
        com.feishin.mobile.data.db.DownloadStatus.PAUSED -> "Paused"
        com.feishin.mobile.data.db.DownloadStatus.FAILED -> "Retry"
        null -> "Download"
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        NpChip(
            label = if (starred) "Favorited" else "Favorite",
            icon = if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            content = content,
            accent = if (starred) FavoriteRed else null,
            onClick = onFavorite,
        )
        NpChip("Save", Icons.Default.Save, content, onSave)
        NpChip("Share", Icons.Default.Share, content, onShare)
        NpChip(
            label = downloadLabel,
            icon = if (dlStatus == com.feishin.mobile.data.db.DownloadStatus.COMPLETED) {
                Icons.Default.DownloadDone
            } else {
                Icons.Default.Download
            },
            content = content,
            onClick = onDownload,
        )
    }
}

private val FavoriteRed = Color(0xFFFF6B6B)

@Composable
private fun NpChip(
    label: String,
    icon: ImageVector,
    content: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Color? = null,
) {
    val tint = accent ?: content
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(content.copy(alpha = if (enabled) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (enabled) label else "$label • soon",
            style = MaterialTheme.typography.labelLarge,
            color = tint.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** How far the queue sheet travels when fully open, in pixels. */
private fun ArtistInfoPlaceholder() = Unit

@Composable
private fun PlayingFromBottom(
    player: PlayerManager,
    dim: Color,
    onClick: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
) {
    val source by player.sourceName.collectAsStateWithLifecycle()
    // Whole area is tappable and draggable, following the finger 1:1 from the
    // first pointer event (no threshold). The parent swipe-down-to-close only
    // tracks downward drags, so up-here and down-there never fight. The area
    // is at least 72dp tall so the entry is an easy tap target.
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragEnd(0f) },
                    onDragCancel = { onDragEnd(0f) },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(dim.copy(alpha = 0.5f)))
        Spacer(Modifier.height(6.dp))
        MarqueeTitle(
            text = "Playing from ${source ?: "Queue"}",
            style = MaterialTheme.typography.bodySmall,
            color = dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SeekBar(player: PlayerManager, content: Color) {
    val pos by player.position.collectAsStateWithLifecycle()
    val dur by player.duration.collectAsStateWithLifecycle()
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val known = !PlaybackProgress.isUnknown(dur)
    val progress = PlaybackProgress.progressWithScrub(pos, dur, scrubMs)

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(known, dur) {
                    if (!known) return@pointerInput
                    fun msAt(x: Float): Long {
                        val w = size.width
                        if (w <= 0) return 0L
                        return ((x / w).coerceIn(0f, 1f) * dur).toLong().coerceIn(0L, dur)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { off -> dragging = true; scrubMs = msAt(off.x) },
                        onHorizontalDrag = { change, _ -> scrubMs = msAt(change.position.x) },
                        onDragEnd = {
                            scrubMs?.let { player.seekTo(it) }
                            scrubMs = null
                            dragging = false
                        },
                        onDragCancel = { scrubMs = null; dragging = false },
                    )
                }
                .pointerInput(known, dur) {
                    if (!known) return@pointerInput
                    detectTapGestures { off ->
                        val w = size.width
                        if (w > 0) {
                            player.seekTo(((off.x / w).coerceIn(0f, 1f) * dur).toLong().coerceIn(0L, dur))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 4dp track with the filled portion exactly matching progress.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(content.copy(alpha = 0.28f)))
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(content),
            )
            // Circular thumb pinned to the end of the filled part; grows on drag.
            val thumb = if (dragging) 18.dp else 12.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * progress.coerceIn(0f, 1f))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(content),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                PlaybackProgress.formatElapsed(scrubMs ?: pos),
                color = dim75(content), style = MaterialTheme.typography.bodySmall,
            )
            Text(
                PlaybackProgress.formatRemaining(pos, dur, scrubMs),
                color = dim75(content), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun dim75(color: Color) = color.copy(alpha = 0.85f)

@Composable
private fun ControlsRow(player: PlayerManager, content: Color) {
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val shuffle by player.shuffleEnabled.collectAsStateWithLifecycle()
    val repeat by player.repeatMode.collectAsStateWithLifecycle()
    val active = content
    val idle = content.copy(alpha = 0.55f)

    // The favorite toggle lives in the chip row now and the queue opens from
    // the bottom area, so this row is transport only.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) active else idle)
        }
        IconButton(onClick = { player.skipToPrevious() }) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = content, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(76.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(
                    // Accent pair, not a white circle: on the Light theme's pale
                    // backdrop a white button nearly vanishes.
                    MaterialTheme.colorScheme.primary,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { player.skipToNext() }) {
            Icon(Icons.Default.SkipNext, "Next", tint = content, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { player.cycleRepeat() }) {
            val mode = repeat
            Icon(
                if (mode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Repeat",
                tint = if (mode == Player.REPEAT_MODE_OFF) idle else active,
            )
        }
    }
}

@Composable
private fun LyricsPanel(
    lyricsVm: LyricsViewModel,
    player: PlayerManager,
    content: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val ui by lyricsVm.ui.collectAsStateWithLifecycle()
    val pos by player.position.collectAsStateWithLifecycle()
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        when (val s = ui) {
            is LyricsUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = content)
            }
            is LyricsUi.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No lyrics available", color = dim, style = MaterialTheme.typography.bodyMedium)
            }
            is LyricsUi.Plain -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(s.text, color = content, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(64.dp))
            }
            is LyricsUi.Synced -> SyncedLyricsList(s.lyrics, pos, content, dim) { player.seekTo(it) }
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lyrics: SyncedLyrics,
    positionMs: Long,
    content: Color,
    dim: Color,
    onSeek: (Long) -> Unit,
) {
    val index = lyrics.lineIndexAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(index) {
        if (index >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines, key = { i, line -> "$i-${line.startMs}" }) { i, line ->
            val active = i == index
            Text(
                line.text,
                color = if (active) content else dim.copy(alpha = 0.6f),
                style = if (active) {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.startMs) }
                    .padding(vertical = 6.dp),
            )
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

/**
 * The reorderable queue list. onMove mutates ONLY the local [displayOrder]
 * (cheap, per frame); [onCommit] fires once on drop and writes through to the
 * player. Stable keys are the unique per-entry queue ids, so two copies of
 * the same song stay distinct.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueList(
    displayOrder: List<com.feishin.mobile.playback.QueueItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentIndex: Int,
    isPlaying: Boolean,
    player: PlayerManager,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onMoveLocal: (Int, Int) -> Unit,
    onCommit: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onActions: (com.feishin.mobile.domain.model.Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reorderState = rememberReorderableLazyColumnState(listState) { from, to ->
        onMoveLocal(from.index, to.index)
    }
    // Auto-scroll to the current item on track changes — but never while a
    // drag is in flight (the old per-frame animateScrollToItem fought the
    // gesture and was half the freeze).
    val anyDragging = reorderState.isAnyItemDragging
    LaunchedEffect(currentIndex, displayOrder.size, anyDragging) {
        if (!anyDragging && displayOrder.isNotEmpty() && !listState.isScrollInProgress) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, displayOrder.size - 1))
        }
    }
    // Bottom padding clears the bar so the last row is never hidden under it.
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 56.dp),
    ) {
        items(displayOrder, key = { it.queueId }, contentType = { "item" }) { item ->
            val idx = displayOrder.indexOfFirst { it.queueId == item.queueId }
            // animateItem is a LazyItemScope extension: create it here (where
            // the scope is the receiver) and pass it into the row.
            val placement = Modifier.animateItem()
            ReorderableItem(reorderState, key = item.queueId) { isDragging ->
                // Drop = commit exactly once. The guard inside onCommit makes
                // the initial (non-drag) composition a no-op.
                LaunchedEffect(isDragging) { if (!isDragging) onCommit() }
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart && idx >= 0) {
                            onRemoveAt(idx)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {},
                ) {
                    val isCurrent = idx == currentIndex
                    // Played rows (before the current index) dim but stay
                    // legible: secondary text clears 4.5:1, above the 3:1 bar.
                    val played = idx in 0 until currentIndex
                    val scheme = LocalPlayingColors.current
                    val rowBg = if (isCurrent) {
                        scheme?.let { playingColor(it.container) }
                            ?: MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                    // Every row gets an EXPLICIT colour: Color.Unspecified
                    // falls back to LocalContentColor, which is dark here
                    // (the sheet is an overlay with no Surface), so upcoming
                    // rows rendered dim instead of full strength.
                    val rowTitle = when {
                        isCurrent && scheme != null -> playingColor(scheme.onContainer)
                        played && scheme != null -> playingColor(scheme.surfaceSecondary)
                        scheme != null -> playingColor(scheme.surfaceText)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    // (colors above are explicit: Unspecified would fall back
                    // to the dark LocalContentColor and dim these rows)
                    val rowSub = when {
                        isCurrent && scheme != null -> playingColor(scheme.secondary)
                        played && scheme != null -> playingColor(scheme.surfaceSecondary)
                        scheme != null -> playingColor(scheme.surfaceSecondary)
                        else -> VusicColors.SecondaryText
                    }
                    Row(
                        placement
                            .fillMaxWidth()
                            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(16.dp))
                            // Current row: 16dp rounded container from the artwork scheme.
                            .background(rowBg, RoundedCornerShape(16.dp))
                            .combinedClickable(
                                enabled = idx >= 0,
                                onClick = { if (idx >= 0) onPlayAt(idx) },
                                onLongClick = {
                                    // Long-press opens the same sheet as the ⋮
                                    // button, with a haptic tick for feedback.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActions(item.song)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val artUrl = player.coverArtUrl(item.song.coverArt, 128)
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.BottomStart) {
                            // 56dp art with 8dp corners (one place, so the
                            // current-row highlight and the art share a radius).
                            CoverImage(artUrl, item.song.title, modifier = Modifier.size(56.dp), roundedDp = 8.dp)
                            if (isCurrent && isPlaying) {
                                // Dark scrim behind the bars: readable on any
                                // art (see TrackRow).
                                Box(
                                    Modifier.padding(6.dp).size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EqualizerOverlay(Modifier.fillMaxSize())
                                }
                            } else if (isCurrent) {
                                Icon(
                                    Icons.Default.PlayArrow, "Current track",
                                    tint = Color.White,
                                    modifier = Modifier.padding(6.dp).size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                        .padding(2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // Both text lines start at the same x (one column).
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                                color = rowTitle,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${item.song.artist} • ${formatDuration(item.song.durationSec)}",
                                fontSize = 14.sp,
                                color = rowSub,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Explicit ⋮ entry point next to the drag handle, so
                        // the actions sheet is reachable without long-press.
                        IconButton(
                            onClick = { onActions(item.song) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Track options",
                                tint = rowSub,
                            )
                        }
                        // Immediate drag from the handle: no long-press delay,
                        // and dragging anywhere else never starts a reorder.
                        // A 48dp box gives the handle a full-size touch target
                        // without making the glyph itself huge.
                        Box(
                            Modifier.size(48.dp).draggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { onCommit() },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Reorder",
                                tint = rowSub,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queue bottom bar: shuffle left, close chevron centered, repeat right. Both
 * toggles show the player's live state and stay in sync with Now Playing.
 */
@Composable
private fun QueueBottomBar(
    shuffleOn: Boolean,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
    content: Color,
) {
    val dim = content.copy(alpha = 0.55f)
    val scheme = LocalPlayingColors.current
    // Active toggles get the scheme's primary (contrast-enforced >= 3:1) on a
    // tonal pill, so "on" is unmistakable next to "off" in both themes.
    val activeTint = scheme?.let { playingColor(it.primary) } ?: MaterialTheme.colorScheme.primary
    val activePill = scheme?.let { playingColor(it.container) } ?: MaterialTheme.colorScheme.primaryContainer
    // OPAQUE scrim: the list must never ghost through the controls.
    val scrim = scheme?.let { playingColor(it.surfaceBottom) } ?: VusicColors.Surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(scrim)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .then(if (shuffleOn) Modifier.background(activePill) else Modifier),
        ) {
            IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = if (shuffleOn) "Shuffle on" else "Shuffle off",
                    tint = if (shuffleOn) activeTint else dim,
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close queue", tint = content)
        }
        val repeatActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .then(if (repeatActive) Modifier.background(activePill) else Modifier),
        ) {
            IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
                when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icon(
                        Icons.Default.RepeatOne, contentDescription = "Repeat one",
                        tint = activeTint,
                    )
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> Icon(
                        Icons.Default.Repeat, contentDescription = "Repeat all",
                        tint = activeTint,
                    )
                    else -> Icon(
                        Icons.Default.Repeat, contentDescription = "Repeat off",
                        tint = dim,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueSheet(
    state: SheetState,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    content: Color = MaterialTheme.colorScheme.onSurface,
    playlistEvents: PlaylistEvents? = null,
    onActions: (com.feishin.mobile.domain.model.Track) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val queue by player.queue.collectAsStateWithLifecycle()
    val currentIndex by player.currentIndex.collectAsStateWithLifecycle()
    val sourceName by player.sourceName.collectAsStateWithLifecycle()
    val radioActive by player.radioActive.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val shuffleOn by player.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by player.repeatMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Hoisted: the header (Save pill, secondary line) and the sheet gradient
    // below both read it, as does each row through the local.
    val scheme = LocalPlayingColors.current

    if (queue.isEmpty()) {
        // The last item is gone: close through the one close path.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // ---- Commit-on-drop reorder -------------------------------------------
    // The library's onMove fires on EVERY drag frame. player.move() per frame
    // used to (1) emit a new queue (full-list recompose per frame), (2) IPC a
    // moveMediaItem to the service per frame, (3) write DataStore per frame,
    // and (4) re-trigger the auto-scroll below per frame — the loop that froze
    // the app after a few drags. Now onMove touches ONLY this local copy; the
    // player sees exactly ONE commitOrder() on drop.
    var displayOrder by remember(queue) { mutableStateOf(queue) }
    val liveIds = queue.map { it.queueId }
    val shownIds = displayOrder.map { it.queueId }
    fun commitIfDirty() {
        if (shownIds != liveIds) player.commitOrder(shownIds)
        // displayOrder re-syncs from remember(queue) once the player emits.
    }

    // Direct-manipulation sheet. The nested-scroll handoff lets the list scroll
    // normally and only takes over when the list is already at the top and the
    // finger drags down. Progress is read only inside graphicsLayer lambdas, so
    // dragging never recomposes a frame.
    val nestedScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0 && atTop) {
                    val h = state.sheetHeightPx
                    if (h > 0f) state.dragBy(-available.y / h)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // Tinted gradient when the artwork scheme is present, flat surface when
    // the setting is off. Brush either way so the background() overload is
    // unambiguous.
    val sheetBrush: Brush = scheme?.let { s ->
        Brush.verticalGradient(
            listOf(playingColor(s.surfaceTop), playingColor(s.surfaceBottom)),
        )
    } ?: SolidColor(VusicColors.Surface)

    Box(Modifier.fillMaxSize()) {
        // Scrim: alpha follows the drag, tap-outside closes through the one path.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha(state.progress) }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { state.close() },
                ),
        )
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.78f)
                .align(Alignment.BottomCenter)
                .onSizeChanged { state.sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    val p = state.progress
                    translationY = (1f - p) * size.height
                    // Keep the header grab area solid while sliding.
                    clip = p < 1f
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(sheetBrush)
                .nestedScroll(nestedScroll)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> state.dragByPixels(delta) },
                    // Fling velocity is normalized to progress units so the
                    // decision lives in SheetPhysics, not here.
                    onDragStopped = { velocity ->
                        state.onDragEnd(velocity)
                    },
                )
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // One standard drag handle at the top.
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(content.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(12.dp))
            // Header: big bold source title on the left; the Radio chip (when
            // active) and the Save button right-aligned on the same row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Large + bold; marquees only when it actually overflows.
                MarqueeTitle(
                    text = sourceName ?: "Queue (${queue.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                if (radioActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Radio",
                        style = MaterialTheme.typography.labelSmall,
                        color = FavoriteRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(FavoriteRed.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Save pill uses the scheme's primary/onPrimary (contrast
                // enforced), not a hardcoded white — in Light theme the sheet
                // is pale, so a white pill would vanish.
                Text(
                    "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme?.let { playingColor(it.onPrimary) } ?: MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme?.let { playingColor(it.primary) } ?: MaterialTheme.colorScheme.primary)
                        .clickable { showSaveDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Secondary line: count + total runtime of the queue.
            Text(
                "${queue.size} songs · ${formatDuration(queue.sumOf { it.song.durationSec.toLong() }.toInt())}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme?.let { playingColor(it.surfaceSecondary) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            QueueList(
                displayOrder = displayOrder,
                listState = listState,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                player = player,
                haptics = haptics,
                onMoveLocal = { from, to ->
                    displayOrder = com.feishin.mobile.core.common.ShufflePlan
                        .applyOrder(
                            shownIds.toMutableList().apply { add(to, removeAt(from)) },
                            displayOrder,
                        ) { it.queueId }
                },
                onCommit = { commitIfDirty() },
                onPlayAt = { idx -> player.playAt(idx) },
                onRemoveAt = { idx -> player.removeAt(idx) },
                onActions = onActions,
                modifier = Modifier.weight(1f),
            )
            // Bottom bar: shuffle | close chevron | repeat, all live state.
            QueueBottomBar(
                shuffleOn = shuffleOn,
                repeatMode = repeatMode,
                onShuffle = { player.toggleShuffle() },
                onRepeat = { player.cycleRepeat() },
                onClose = onDismiss,
                content = content,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save queue as playlist") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy && queue.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            when (repo.createPlaylist(name.trim(), queue.map { it.song.id })) {
                                is Result.Ok -> {
                                    messenger.show("Saved playlist $name")
                                    showSaveDialog = false
                                }
                                is Result.Err -> {
                                    messenger.show("Could not save playlist")
                                    busy = false
                                }
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    // A new playlist can't have duplicates, but adding the queue
                    // (radio included) to an existing one goes through the
                    // duplicate check in the picker.
                    TextButton(onClick = { showSaveDialog = false; showPicker = true }) { Text("Existing…") }
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (showPicker && queue.isNotEmpty()) {
        PlaylistPickerSheet(
            tracks = queue.map { it.song }, title = "Queue (${queue.size})",
            onDismiss = { showPicker = false }, repo = repo, messenger = messenger,
            playlistEvents = playlistEvents,
        )
    }
}
