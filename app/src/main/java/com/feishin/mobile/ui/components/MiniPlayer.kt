package com.feishin.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import kotlinx.coroutines.launch

/** Pixels of downward travel that commit a queue clear on release. */
private const val CLEAR_THRESHOLD_PX = 160f

/**
 * Exact height of the floating card: 8dp padding + 56dp circular cover + 8dp
 * padding. Fixed (not wrap-content) so the scaffold can reserve precisely this
 * much space for it and the card never pushes the layout around while its
 * content loads.
 */
val MiniPlayerHeight = 72.dp

/** Diameter of the circular cover: the card height minus 2 x 8dp padding. */
val MiniPlayerCoverDiameter = 56.dp

/** Padding inside the card, equal on the left, top and bottom. */
val MiniPlayerInnerPadding = 8.dp

/** Gap between the card and the nav bar. */
val MiniPlayerNavGap = 8.dp

/** Side margin, so the card reads as floating rather than full-bleed. */
val MiniPlayerMargin = 12.dp

/**
 * Bottom clearance the scrollable content reserves for the floating card,
 * provided by the scaffold and animated with the card's enter/exit. Lists read
 * this in their own `contentPadding` so items scroll *behind* the card instead
 * of stopping above a dead, painted spacer.
 */
val LocalMiniClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * Floating mini player: a rounded card that hovers above the nav bar with the
 * screen's content scrolling visibly behind and around it.
 *
 * Gestures (all unchanged): tap or swipe up opens Now Playing, swipe
 * left/right skips, drag down clears the queue with Undo.
 *
 * The downward drag follows the finger 1:1 (offset + fade read in graphicsLayer,
 * so no recomposition per frame). Releasing past the threshold clears; releasing
 * early springs back. Enter and exit are animated by [enterProgress] so the card
 * slides in and out instead of popping.
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
    val visible = queue.isNotEmpty() && current != null
    // 1 = fully present, 0 = gone. Animated so the card slides and the list's
    // bottom padding shrinks with it instead of popping.
    val enter = remember { Animatable(if (visible) 1f else 0f) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(visible) {
        enter.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) Motion.layoutSpring() else Motion.sheetSpring(),
        )
    }

    val song = current
    if (song == null || enter.value <= 0.001f) return

    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // Drag state lives in an Animatable: snapTo() per pointer event tracks the
    // finger with no recomposition (graphicsLayer reads don't recompose), and
    // animateTo() springs back on a short release.
    val dragY = remember { Animatable(0f) }
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MiniPlayerMargin)
            .graphicsLayer {
                // Enter/exit: rise + fade, driven by the same value the padding uses.
                translationY = (1f - enter.value) * 48f
                alpha = enter.value * (1f - dragY.value / 600f).coerceIn(0f, 1f)
            }
            // Soft elevation instead of the old solid black band. Nothing else
            // here has a background: this container is fully transparent, so
            // content shows on every side of the card itself.
            .shadow(12.dp, shape, clip = false, ambientColor = androidx.compose.ui.graphics.Color(0x60000000), spotColor = androidx.compose.ui.graphics.Color(0x80000000))
            .clip(shape)
            .background(VusicColors.SurfaceHigh)
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
                            scope.launch { dragY.animateTo(0f, Motion.snapBackSpring()) }
                        } else {
                            // Card is gone with the queue; reset for next time.
                            scope.launch { dragY.snapTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragY.animateTo(0f, Motion.snapBackSpring()) }
                    },
                )
            }
            .height(MiniPlayerHeight),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = MiniPlayerInnerPadding,
                end = 4.dp,
                top = MiniPlayerInnerPadding,
                bottom = MiniPlayerInnerPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // Circular cover, concentric with the card's rounded end: its
            // diameter is the card height minus twice the inner padding, with
            // equal padding on the left, top and bottom, so no edge ever clips.
            CoverImage(
                player.coverArtUrl(song.coverArt, 128), song.title,
                modifier = Modifier.size(MiniPlayerCoverDiameter),
                roundedDp = MiniPlayerCoverDiameter / 2,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = VusicColors.SecondaryText,
                )
            }
            IconButton(onClick = player::skipToPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.SkipPrevious, "Previous track",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = player::togglePlayPause, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = player::skipToNext, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.SkipNext, "Next track",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Thin progress line along the bottom edge of the card, inset past the
        // rounded corners so it never pokes out of the clip. It sits below the
        // circle (the circle ends 8dp above the card bottom), so it never cuts it.
        Box(
            Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(1.dp))
                .align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}