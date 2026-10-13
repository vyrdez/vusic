package com.feishin.mobile.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.core.common.Result
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing playing", color = MaterialTheme.colorScheme.onSurface)
        }
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

    var showQueue by remember { mutableStateOf(false) }
    var lyricsMode by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Every stop is OPAQUE: a translucent mid-stop let the screen
                // behind bleed through. The tint fades to near-black instead.
                Brush.verticalGradient(listOf(bg, bgMid, VusicColors.Background)),
            )
            .statusBarsPadding(),
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
            Column(
                Modifier
                    .fillMaxWidth()
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                // Cap the art so the title, chips, seek bar and controls always fit
                // on screen (short devices / landscape otherwise push them off).
                CoverImage(
                    artUrl, current.title,
                    modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
                    roundedDp = 16.dp,
                )
                Spacer(Modifier.height(16.dp))
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
                Text(
                    current.artist, style = MaterialTheme.typography.bodyLarge, color = dim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = current.artistId?.isNotBlank() == true) {
                        current.artistId?.let { onArtist(it) }
                    },
                )
                Spacer(Modifier.height(12.dp))
                NpChipsRow(
                    content = content,
                    onLyrics = { lyricsMode = true },
                    onSave = { showPicker = true },
                    onShare = { shareText(context, "${current.title} - ${current.artist}") },
                )
                Spacer(Modifier.height(8.dp))
                SeekBar(player, content)
                Spacer(Modifier.height(4.dp))
                ControlsRow(player, lyricsVm, current, content, dim, onOpenQueue = { showQueue = true })
                Spacer(Modifier.height(4.dp))
                PlayingFromRow(player = player, dim = dim, onClick = { showQueue = true })
            }
            } else {
                LyricsPanel(lyricsVm, player, content, dim, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            player = player, repo = repo, messenger = messenger,
            content = content, onDismiss = { showQueue = false },
        )
    }
    if (showPicker) {
        PlaylistPickerSheet(
            tracks = listOf(current), title = current.title,
            onDismiss = { showPicker = false }, repo = repo, messenger = messenger,
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
private fun NpChipsRow(content: Color, onLyrics: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NpChip("Lyrics", Icons.Default.MusicNote, content, onLyrics)
        NpChip("Save", Icons.Default.Save, content, onSave)
        NpChip("Share", Icons.Default.Share, content, onShare)
        NpChip("Download", Icons.Default.Download, content, onClick = {}, enabled = false)
    }
}

@Composable
private fun NpChip(label: String, icon: ImageVector, content: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(content.copy(alpha = if (enabled) 0.14f else 0.07f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = content.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (enabled) label else "$label • soon",
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
private fun PlayingFromRow(player: PlayerManager, dim: Color, onClick: () -> Unit) {
    val source by player.sourceName.collectAsState()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(dim.copy(alpha = 0.5f)))
        Spacer(Modifier.height(6.dp))
        Text(
            "Playing from ${source ?: "Queue"}",
            style = MaterialTheme.typography.bodySmall, color = dim,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
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
private fun ControlsRow(
    player: PlayerManager,
    lyricsVm: LyricsViewModel,
    song: com.feishin.mobile.domain.model.Track,
    content: Color,
    dim: Color,
    onOpenQueue: () -> Unit,
) {
    val playing by player.isPlaying.collectAsState()
    val shuffle by player.shuffleEnabled.collectAsState()
    val repeat by player.repeatMode.collectAsState()
    val active = content
    val idle = content.copy(alpha = 0.55f)
    var starred by remember(song.id) { mutableStateOf(song.starred) }

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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            val next = !starred
            starred = next
            lyricsVm.setStarred(song, next) { starred = it }
        }) {
            Icon(if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Star", tint = content)
        }
        IconButton(onClick = onOpenQueue) { Icon(Icons.Default.QueueMusic, "Queue", tint = content) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    player: PlayerManager,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    content: Color = MaterialTheme.colorScheme.onSurface,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queue by player.queue.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    val sourceName by player.sourceName.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Material3's ModalBottomSheet already draws the standard drag handle;
            // adding our own produced a doubled bar.
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Playing from", style = MaterialTheme.typography.labelMedium, color = VusicColors.SecondaryText)
                    Text(
                        sourceName ?: "Queue (${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.labelLarge, color = Color.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable { showSaveDialog = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { player.clear() }) { Text("Clear") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
            }
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyColumnState(listState) { from, to ->
                player.move(from.index, to.index)
            }
            LaunchedEffect(currentIndex, queue.size) {
                if (queue.isNotEmpty() && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(currentIndex.coerceIn(0, queue.size - 1))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(queue, key = { it.queueId }) { item ->
                    val idx = queue.indexOfFirst { it.queueId == item.queueId }
                    ReorderableItem(reorderState, key = item.queueId) { _ ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart && idx >= 0) {
                                    player.removeAt(idx)
                                    true
                                } else false
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {},
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (idx == currentIndex) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable(enabled = idx >= 0) { if (idx >= 0) player.playAt(idx) }
                                    .padding(12.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val artUrl = player.coverArtUrl(item.song.coverArt, 128)
                                Box(Modifier.size(56.dp), contentAlignment = Alignment.BottomStart) {
                                    CoverImage(artUrl, item.song.title, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
                                    if (idx == currentIndex && isPlaying) {
                                        EqualizerOverlay(Modifier.padding(6.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (idx == currentIndex) FontWeight.Bold else null,
                                    )
                                    Text(
                                        "${item.song.artist} • ${formatDuration(item.song.durationSec)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VusicColors.SecondaryText,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    modifier = Modifier.longPressDraggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
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
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }
}
