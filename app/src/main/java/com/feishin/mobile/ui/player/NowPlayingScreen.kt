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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.feishin.mobile.ui.components.UiMessenger
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
    val song by player.current.collectAsState()
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

    // Dynamic background from the cover art's dominant colour, clamped dark enough
    // that white text always has strong contrast (fixes washed-out light covers).
    var targetBg by remember { mutableStateOf(VusicColors.Background) }
    val ctx = LocalContext.current
    val artUrl = player.coverArtUrl(current.coverArt, 512)
    LaunchedEffect(artUrl) {
        if (artUrl == null) {
            targetBg = VusicColors.Background
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
    // The backdrop is clamped dark, so the content is always white.
    val content = Color.White
    val dim = Color.White.copy(alpha = 0.72f)

    var lyricsMode by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A fresh queue (empty -> non-empty, e.g. playback after Clear) must not
    // inherit a stale open-sheet value from the previous session.
    val queue by player.queue.collectAsState()
    var hadEmptyQueue by remember { mutableStateOf(queue.isEmpty()) }

    // ---- Queue sheet: 0f = closed, 1f = open. Read only inside graphicsLayer /
    // draw lambdas so dragging never recomposes per frame, and the sheet is
    // always composed so the first drag frame is already there.
    val sheetProgress = remember { mutableFloatStateOf(0f) }
    var sheetSettleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Actual sheet height in px, measured once composed; drives the drag
    // distance so the sheet tracks the finger 1:1 on any screen size.
    val sheetHeightPx = remember { mutableFloatStateOf(560f) }
    fun settleSheet(target: Float, velocityPxPerSec: Float = 0f) {
        sheetSettleJob?.cancel()
        sheetSettleJob = scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = sheetProgress.floatValue,
                targetValue = target,
                initialVelocity = velocityPxPerSec,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.9f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> sheetProgress.floatValue = value }
        }
    }

    LaunchedEffect(queue.isEmpty()) {
        if (queue.isEmpty()) {
            hadEmptyQueue = true
            sheetProgress.floatValue = 0f
        } else if (hadEmptyQueue) {
            hadEmptyQueue = false
            sheetProgress.floatValue = 0f
            showPicker = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Every stop is OPAQUE: a translucent mid-stop let the screen
                // behind bleed through. The tint fades to near-black instead.
                Brush.verticalGradient(listOf(bg, bgMid, VusicColors.Background)),
            )
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
            var dragOffset by remember { mutableFloatStateOf(0f) }
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
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset > 260f) onClose()
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, amount ->
                                    if (amount > 0) dragOffset = (dragOffset + amount).coerceAtMost(700f)
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
                    val downloadState by downloads.observeSong(current.id).collectAsState(initial = null)
                    val favTracks by favs.tracks.collectAsState()
                    val favsLoaded by favs.loaded.collectAsState()
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
                        onClick = { settleSheet(1f) },
                        onDrag = { delta ->
                            sheetSettleJob?.cancel()
                            sheetProgress.floatValue =
                                (sheetProgress.floatValue - delta / sheetHeightPx.floatValue).coerceIn(0f, 1f)
                        },
                        onDragEnd = { velocity ->
                            val open = sheetProgress.floatValue > 0.35f ||
                                velocity < -600f
                            settleSheet(if (open) 1f else 0f, velocity)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            } else {
                LyricsPanel(lyricsVm, player, content, dim, modifier = Modifier.weight(1f))
            }
        }
    }

    if (sheetProgress.floatValue > 0.001f || sheetSettleJob?.isActive == true) {
        QueueSheet(
            progress = { sheetProgress.floatValue },
            onProgress = { sheetProgress.floatValue = it },
            onSettle = { target, velocity -> settleSheet(target, velocity) },
            onHeightPx = { sheetHeightPx.floatValue = it.coerceAtLeast(1f) },
            player = player, repo = repo, messenger = messenger,
            content = content, playlistEvents = playlistEvents,
            onActions = { onActions(ActionTarget.Track(it)) },
            onDismiss = { settleSheet(0f) },
        )
    }
    // The bottom area's tap/drag handler is wired after the sheet exists.
    androidx.activity.compose.BackHandler(enabled = sheetProgress.floatValue > 0.001f) {
        settleSheet(0f)
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
                color = if (selected) Color.Black else dim,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (selected) Color.White else Color.Transparent)
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
    val source by player.sourceName.collectAsState()
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
    val pos by player.position.collectAsState()
    val dur by player.duration.collectAsState()
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
    val playing by player.isPlaying.collectAsState()
    val shuffle by player.shuffleEnabled.collectAsState()
    val repeat by player.repeatMode.collectAsState()
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
                Modifier.size(72.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play",
                    tint = Color.Black, modifier = Modifier.size(40.dp),
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
    val ui by lyricsVm.ui.collectAsState()
    val pos by player.position.collectAsState()
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
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        items(displayOrder, key = { it.queueId }) { item ->
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
                    Row(
                        placement
                            .fillMaxWidth()
                            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
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
                            .padding(12.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val artUrl = player.coverArtUrl(item.song.coverArt, 128)
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.BottomStart) {
                            CoverImage(artUrl, item.song.title, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
                            if (isCurrent && isPlaying) {
                                EqualizerOverlay(Modifier.padding(6.dp))
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
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${item.song.artist} • ${formatDuration(item.song.durationSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VusicColors.SecondaryText,
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Immediate drag from the handle: no long-press delay,
                        // and dragging anywhere else never starts a reorder.
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { onCommit() },
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = if (shuffleOn) "Shuffle on" else "Shuffle off",
                tint = if (shuffleOn) content else dim,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close queue", tint = content)
        }
        IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
            when (repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ONE -> Icon(
                    Icons.Default.RepeatOne, contentDescription = "Repeat one",
                    tint = content,
                )
                androidx.media3.common.Player.REPEAT_MODE_ALL -> Icon(
                    Icons.Default.Repeat, contentDescription = "Repeat all",
                    tint = content,
                )
                else -> Icon(
                    Icons.Default.Repeat, contentDescription = "Repeat off",
                    tint = dim,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueueSheet(
    progress: () -> Float,
    onProgress: (Float) -> Unit,
    onSettle: (Float, Float) -> Unit,
    onHeightPx: (Float) -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    content: Color = MaterialTheme.colorScheme.onSurface,
    playlistEvents: PlaylistEvents? = null,
    onActions: (com.feishin.mobile.domain.model.Track) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val queue by player.queue.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    val sourceName by player.sourceName.collectAsState()
    val radioActive by player.radioActive.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val shuffleOn by player.shuffleEnabled.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    if (queue.isEmpty()) {
        // The last item is gone: close the sheet.
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

    // Direct-manipulation sheet: always composed, translated by the finger
    // through graphicsLayer (no recomposition per frame). A nested-scroll
    // handoff lets the list scroll normally and only takes over when the list
    // is already at the top and the finger drags down.
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
                    onProgress((progress() - available.y / 560f).coerceIn(0f, 1f))
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim fades in proportionally with the drag.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = progress() * 0.62f }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSettle(0f, 0f) },
                ),
        )
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.78f)
                .align(Alignment.BottomCenter)
                .onSizeChanged { onHeightPx(it.height.toFloat()) }
                .graphicsLayer {
                    translationY = (1f - progress()) * size.height
                    // Keep the header grab area solid while sliding.
                    clip = progress() < 1f
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(VusicColors.Surface)
                .nestedScroll(nestedScroll)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        onProgress((progress() - delta / 560f).coerceIn(0f, 1f))
                    },
                    onDragStopped = { velocity ->
                        val open = progress() > 0.35f || velocity < -600f
                        onSettle(if (open) 1f else 0f, velocity)
                    },
                )
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(VusicColors.SecondaryText.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(10.dp))
            // Top row: source title (marquee when long) on the left, a text
            // Save button on the right. Nothing else lives here.
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarqueeTitle(
                    text = sourceName ?: "Queue (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
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
                Text(
                    "Save",
                    style = MaterialTheme.typography.labelLarge, color = Color.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .clickable { showSaveDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
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
