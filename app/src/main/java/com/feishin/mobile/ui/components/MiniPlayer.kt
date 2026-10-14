package com.feishin.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.feishin.mobile.playback.PlayerManager
import kotlinx.coroutines.launch

/** Pixels of downward travel that commit a queue clear on release. */
private const val CLEAR_THRESHOLD_PX = 160f

/**
 * Docked directly above the nav bar. Tap or swipe up opens Now Playing,
 * swipe left/right skips tracks, drag down clears the queue (with Undo).
 * Hidden when the queue is empty.
 *
 * The downward drag follows the finger 1:1 (offset + fade read in
 * graphicsLayer, so no recomposition per frame). Releasing past the threshold
 * stops playback and clears everything; releasing early springs back.
 */
@Composable
fun MiniPlayer(
    player: PlayerManager,
    onOpen: () -> Unit,
    onQueueCleared: (PlayerManager.ClearedQueue) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val queue by player.queue.collectAsState()
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    // Hidden when the queue is empty: zero height, so the content area gets its
    // bottom padding back. (NavRules.shouldShowMiniPlayer documents the same
    // rule for tests; the inline check here avoids a ui.components ->
    // ui.navigation edge, since navigation already depends on components.)
    if (queue.isEmpty()) return
    val song = current ?: return

    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val scope = rememberCoroutineScope()
    // Drag state lives in an Animatable: snapTo() per pointer event tracks the
    // finger with no recomposition (graphicsLayer reads don't recompose), and
    // animateTo() springs back on a short release.
    val dragY = remember { Animatable(0f) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                translationY = dragY.value
                alpha = (1f - dragY.value / 600f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .pointerInput(queue.size) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDrag = { change, amount ->
                        totalX += amount.x
                        totalY += amount.y
                        // Only downward travel moves the card; horizontal
                        // skips and upward opens never displace it.
                        val down = totalY.coerceAtLeast(0f)
                        if (down > 0f && down >= kotlin.math.abs(totalX)) {
                            change.consume()
                            scope.launch { dragY.snapTo(down) }
                        }
                    },
                    onDragEnd = {
                        when {
                            totalY > CLEAR_THRESHOLD_PX && totalY >= kotlin.math.abs(totalX) ->
                                onQueueCleared(player.clearWithUndo())
                            totalY < -120f && -totalY >= kotlin.math.abs(totalX) -> onOpen()
                            totalX < -120f -> player.skipToNext()
                            totalX > 120f -> player.skipToPrevious()
                        }
                        if (totalY <= CLEAR_THRESHOLD_PX) {
                            scope.launch { dragY.animateTo(0f, spring()) }
                        } else {
                            // Card is gone with the queue; reset for next time.
                            scope.launch { dragY.snapTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragY.animateTo(0f, spring()) }
                    },
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                player.coverArtUrl(song.coverArt, 128), song.title,
                modifier = Modifier.size(48.dp), roundedDp = 8.dp,
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = player::togglePlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = player::skipToNext) {
                Icon(Icons.Default.SkipNext, "Next track")
            }
        }
        Row(Modifier.fillMaxWidth().height(2.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight(progress.coerceAtLeast(0.001f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight((1f - progress).coerceAtLeast(0.001f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
