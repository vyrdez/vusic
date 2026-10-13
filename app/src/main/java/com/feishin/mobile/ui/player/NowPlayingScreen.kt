package com.feishin.mobile.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.playback.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState
import kotlin.math.roundToInt

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Full-screen Now Playing. Playback lives in the service, so rotation and the
 * system back gesture never disturb it; back (and swipe-down) only closes this UI.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerManager,
    lyricsVm: LyricsViewModel = hiltViewModel(),
    onClose: () -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
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

    // Dynamic background from the cover art's dominant color.
    // NOTE: explicit RGB constructor — hex literals above Int range resolve to the
    // ULong overload with different channel packing, which renders translucent.
    var targetBg by remember { mutableStateOf(Color(20, 26, 34)) }
    val ctx = LocalContext.current
    val artUrl = player.coverArtUrl(current.coverArt, 512)
    LaunchedEffect(artUrl) {
        if (artUrl == null) {
            targetBg = Color(20, 26, 34)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(ctx).data(artUrl).allowHardware(false).build()
                val bmp = (ctx.imageLoader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bmp != null) {
                    val dominant = Palette.from(bmp).generate().getDominantColor(0xFF141A22.toInt())
                    targetBg = Color(dominant)
                }
            }
        }
    }
    val bg by animateColorAsState(targetBg, label = "np-bg")
    val content = if (bg.luminance() > 0.45f) Color(0xFF101418.toInt()) else Color.White
    val dim = content.copy(alpha = 0.72f)

    var showQueue by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bg.copy(alpha = 1f), MaterialTheme.colorScheme.background),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close", tint = content) }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    color = dim,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, "Queue", tint = content) }
            }
            // Swipe-down-to-close area: art, titles, seek, controls.
            var dragOffset by remember { mutableFloatStateOf(0f) }
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
                AsyncImage(
                    artUrl, current.title,
                    modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, color = content, maxLines = 2, textAlign = TextAlign.Center)
                Text(
                    current.artist, style = MaterialTheme.typography.bodyLarge, color = dim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = current.artistId?.isNotBlank() == true) {
                        current.artistId?.let { onArtist(it) }
                    },
                )
                Text(
                    current.album, style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = current.albumId?.isNotBlank() == true) {
                        current.albumId?.let { onAlbum(it) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                SeekBar(player, content)
                Spacer(Modifier.height(4.dp))
                ControlsRow(player, lyricsVm, current, content, dim, onOpenQueue = { showQueue = true })
            }
            // Lyrics fill the rest.
            LyricsPanel(lyricsVm, player, content, dim, modifier = Modifier.weight(1f))
        }
    }

    if (showQueue) {
        QueueSheet(player = player, content = content, onDismiss = { showQueue = false })
    }
}

@Composable
private fun SeekBar(player: PlayerManager, content: Color) {
    val pos by player.position.collectAsState()
    val dur by player.duration.collectAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val range = 0f..(dur.coerceAtLeast(1).toFloat())
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = scrub ?: pos.toFloat().coerceIn(range),
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { player.seekTo(it.toLong()) }
                scrub = null
            },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = content,
                activeTrackColor = content,
                inactiveTrackColor = content.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt((scrub ?: pos.toFloat()).toLong()), color = content.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            val remaining = (dur - (scrub ?: pos.toFloat()).toLong()).coerceAtLeast(0)
            Text("-" + fmt(remaining), color = content.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
        IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(72.dp)) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play",
                tint = content, modifier = Modifier.size(56.dp),
            )
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
    content: Color = MaterialTheme.colorScheme.onSurface,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queue by player.queue.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Queue (${queue.size})", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (idx == currentIndex) FontWeight.Bold else null,
                                    )
                                    Text(
                                        item.song.artist, style = MaterialTheme.typography.bodySmall,
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
}
