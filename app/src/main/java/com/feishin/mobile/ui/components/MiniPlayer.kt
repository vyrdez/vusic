package com.feishin.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.theme.LocalPlayingColors
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.playingColor
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

/**
 * Diameter of the circular cover art. The progress ring lives in the 8dp card
 * padding around it: ring outer edge 56dp (= card height minus 2 x 8dp), so
 * cover + 2dp gap + 3dp stroke fit exactly with equal padding on top, bottom
 * and left.
 */
val MiniPlayerCoverDiameter = 46.dp

/** Outer diameter of the progress ring (stroke centered inside this). */
val MiniPlayerRingDiameter = 56.dp

/** Ring stroke width. */
val MiniPlayerRingStroke = 3.dp

/** Gap between the cover edge and the ring's inner edge. */
val MiniPlayerRingGap = 2.dp

/** Padding inside the card, top and bottom (fits the 56dp ring in 72dp). */
val MiniPlayerInnerPadding = 8.dp

/** Symmetric side insets: ring box and last button box sit equally far in. */
val MiniPlayerSidePadding = 12.dp

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
    modifier: Modifier = Modifier,
    onQueueCleared: (PlayerManager.ClearedQueue) -> Unit = {},
) {
    val queue by player.queue.collectAsStateWithLifecycle()
    val current by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    // NOTE: position/duration are deliberately NOT collected as state. The
    // ring below samples them at ~4Hz in a coroutine and draws in the draw
    // phase, so position ticks never recompose the card.
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

    // Artwork tinting (or the theme pair when dynamic color is off / no art).
    val scheme = LocalPlayingColors.current
    // The card is the DARK, barely-tinted surface (much darker than the row
    // highlight), so the pill reads as part of the background rather than a
    // bright block. See DarkToneSpec.
    val cardBg = if (scheme != null) {
        playingColor(scheme.miniContainer)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    // 1dp hairline: keeps the pill a distinct shape against the page background
    // and the nav bar without brightening the fill.
    val outline = if (scheme != null) {
        playingColor(scheme.miniOutline)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ink = if (scheme != null) {
        playingColor(scheme.primary)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val subInk = if (scheme != null) {
        playingColor(scheme.primary)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val ringBar = if (scheme != null) {
        playingColor(scheme.primary)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ringTrack = if (scheme != null) {
        playingColor(scheme.track)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    }

    // Ring progress: sampled from the player at ~4Hz and interpolated with a
    // matching 250ms tween, so the ring glides instead of stepping. The Canvas
    // below reads this Animatable in the DRAW phase, which schedules a redraw
    // without recomposing the card. Snapped (not animated) on track change and
    // on first composition, so a restored queue shows the right progress
    // immediately instead of sweeping from zero.
    fun sampledProgress(): Float {
        val d = player.duration.value
        return if (d > 0) (player.position.value.toFloat() / d).coerceIn(0f, 1f) else 0f
    }
    val ring = remember { Animatable(sampledProgress()) }
    androidx.compose.runtime.LaunchedEffect(song.id) {
        ring.snapTo(sampledProgress())
        while (true) {
            val target = sampledProgress()
            if (kotlin.math.abs(target - ring.value) > 0.0005f) {
                ring.animateTo(target, tween(250, easing = LinearEasing))
            } else {
                kotlinx.coroutines.delay(250)
            }
        }
    }
    // Drag state lives in an Animatable: snapTo() per pointer event tracks the
    // finger with no recomposition (graphicsLayer reads don't recompose), and
    // animateTo() springs back on a short release.
    val dragY = remember { Animatable(0f) }
    // Full pill: corner radius is half the card height.
    val shape = RoundedCornerShape(MiniPlayerHeight / 2)

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
            .background(cardBg)
            // 1dp hairline (same clip, drawn inside) so the near-black pill is
            // still clearly delineated from the page background and nav bar.
            .border(1.dp, outline, shape)
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
            // Symmetric horizontal insets: the ring box and the last button
            // box sit the same distance from the card edge on both sides, so
            // the ring no longer hugs the left curve and the right icon no
            // longer floats at a different gap. (Vertical stays 8dp: the 56dp
            // ring fills the 72dp card exactly.)
            Modifier.fillMaxWidth().padding(
                start = MiniPlayerSidePadding,
                end = MiniPlayerSidePadding,
                top = MiniPlayerInnerPadding,
                bottom = MiniPlayerInnerPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // Cover + progress ring, concentric with the card's rounded end.
            // The ring's outer edge is the card height minus twice the inner
            // padding (equal padding top/bottom/left); the cover sits inside
            // it with an even gap and stays clean — no icon over the art.
            Box(
                modifier = Modifier.size(MiniPlayerRingDiameter),
                contentAlignment = Alignment.Center,
            ) {
                MiniPlayerRing(progress = { ring.value }, bar = ringBar, track = ringTrack)
                CoverImage(
                    player.coverArtUrl(song.coverArt, 128), song.title,
                    modifier = Modifier.size(MiniPlayerCoverDiameter),
                    roundedDp = MiniPlayerCoverDiameter / 2,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = subInk,
                )
            }
            IconButton(onClick = player::skipToPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.SkipPrevious, "Previous track",
                    tint = ink,
                )
            }
            IconButton(onClick = player::togglePlayPause, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = ink,
                )
            }
            IconButton(onClick = player::skipToNext, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.SkipNext, "Next track",
                    tint = ink,
                )
            }
        }
    }
}

/**
 * Progress ring drawn around the mini-player cover. Reads [progress] in the
 * draw phase, so position ticks redraw the ring without recomposing the card.
 * Starts at 12 o'clock; concentric with the cover by construction (same
 * center, fixed gap).
 */
@Composable
private fun MiniPlayerRing(progress: () -> Float, bar: Color, track: Color) {
    Canvas(Modifier.size(MiniPlayerRingDiameter)) {
        val strokePx = MiniPlayerRingStroke.toPx()
        // Inset by half the stroke so the outer edge lands exactly on the box
        // (a full-size arc would clip half the stroke).
        val inset = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
        )
        val arcTopLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx),
        )
        val sweep = (progress().coerceIn(0f, 1f)) * 360f
        if (sweep > 0.5f) {
            drawArc(
                color = bar,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}