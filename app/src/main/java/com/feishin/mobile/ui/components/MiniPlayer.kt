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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.feishin.mobile.core.common.MiniPlayerGesture
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.theme.LocalPlayingColors
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.playingColor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPEAT_ALL = Player.REPEAT_MODE_ALL
private const val REPEAT_ONE = Player.REPEAT_MODE_ONE

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
    // Drag state lives in Animatables: snapTo() per pointer event tracks the
    // finger with no recomposition (graphicsLayer reads don't recompose), and
    // animateTo() springs back on a short release. One offset per axis, and the
    // gesture is axis-locked, so only the locked one is ever non-zero and a
    // diagonal drag cannot fire two actions.
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    // Dismiss/hand-off alpha, animated independently so a clear or a skip can
    // fade the card out while it is still moving (never fade-only).
    val fade = remember { Animatable(1f) }
    // Committed skip, split into a direction and a trigger. `skipSeq` increments
    // on every swipe so the LaunchedEffect restarts even when two swipes in a
    // row share a direction; `skipDir` (-1 previous, +1 next) is only read by
    // the effect, so a same-direction repeat is not swallowed.
    var skipSeq by remember { mutableIntStateOf(0) }
    var skipDir by remember { mutableFloatStateOf(0f) }
    fun commitSkip(dir: Float) {
        skipDir = dir
        skipSeq++
    }
    // Card size in px: the skip slide-off distance and the clear threshold.
    var cardW by remember { mutableFloatStateOf(0f) }
    var cardH by remember { mutableFloatStateOf(0f) }
    val currentIndex by player.currentIndex.collectAsStateWithLifecycle()
    val repeatMode by player.repeatMode.collectAsStateWithLifecycle()

    // A committed clear leaves the card translated off the bottom and faded
    // out. `visible` going true again means the queue came back (Undo, or new
    // playback), so the offsets must be put back or the card reappears
    // invisible.
    LaunchedEffect(visible) {
        if (visible) {
            dragX.snapTo(0f)
            dragY.snapTo(0f)
            fade.snapTo(1f)
            skipSeq = 0
            skipDir = 0f
        }
    }

    // Two-phase skip: the old content slides out in the drag direction, the
    // player advances, then the new content slides in from the other side.
    //
    // The trigger is a monotonically increasing sequence number, NOT the
    // direction. Using the direction as the LaunchedEffect key made two fast
    // swipes in the same direction collapse into one: the second gesture wrote
    // the same value, the effect did not restart, so that swipe was swallowed
    // and the card stayed translated where the finger released it.
    androidx.compose.runtime.LaunchedEffect(skipSeq) {
        if (skipSeq == 0) return@LaunchedEffect
        val dir = skipDir
        val travel = maxOf(cardW * 1.15f, 240f)
        var swapped = false
        try {
            dragX.animateTo(dir * travel, Motion.fade(Motion.Fast))
            fade.animateTo(0f, Motion.fade(Motion.Fast))
            if (dir < 0f) player.skipToPrevious() else player.skipToNext()
            swapped = true
            dragX.snapTo(-dir * travel)
            fade.animateTo(1f, Motion.fade(Motion.Fast))
            dragX.animateTo(0f, Motion.snapBackSpring())
        } finally {
            // The skip belongs to the gesture, not to this coroutine: if a
            // newer swipe cancelled us mid-flight the original must still fire,
            // otherwise a fast double-swipe skips only once.
            if (!swapped) {
                if (dir < 0f) player.skipToPrevious() else player.skipToNext()
            }
            // `snapTo` suspends, so on a cancelled coroutine these calls would
            // themselves throw and leave the card stranded off-screen - exactly
            // the bug this block exists to prevent. NonCancellable makes the
            // reset unconditional.
            withContext(NonCancellable) {
                dragX.snapTo(0f)
                dragY.snapTo(0f)
                fade.snapTo(1f)
            }
        }
    }

    fun springBack() {
        scope.launch {
            launch { dragX.animateTo(0f, Motion.snapBackSpring()) }
            launch { dragY.animateTo(0f, Motion.snapBackSpring()) }
        }
    }

    // Full pill: corner radius is half the card height.
    val shape = RoundedCornerShape(MiniPlayerHeight / 2)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MiniPlayerMargin)
            .onSizeChanged { cardW = it.width.toFloat(); cardH = it.height.toFloat() }
            .graphicsLayer {
                // Enter/exit: rise + fade, driven by the same value the padding uses.
                translationY = dragY.value + (1f - enter.value) * 48f
                translationX = dragX.value
                alpha = enter.value * fade.value *
                    // Only the downward clear dims the card while it travels;
                    // a skip keeps full opacity so the slide reads as motion.
                    (1f - (dragY.value / 900f).coerceIn(0f, 1f))
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
            .pointerInput(queue.size, currentIndex, repeatMode) {
                // Axis lock plus a real velocity: detectDragGestures gives the
                // drag but no release velocity, so a VelocityTracker is fed from
                // the pointer events to make flings work.
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                val slop = viewConfiguration.touchSlop
                var axis = 0   // 0 undecided, 1 horizontal, 2 vertical
                var tx = 0f
                var ty = 0f
                detectDragGestures(
                    onDragStart = {
                        axis = 0; tx = 0f; ty = 0f
                        tracker.resetTracking()
                    },
                    onDrag = { change, _ ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        tx += change.position.x - change.previousPosition.x
                        ty += change.position.y - change.previousPosition.y
                        if (axis == 0 && (kotlin.math.abs(tx) > slop || kotlin.math.abs(ty) > slop)) {
                            axis = MiniPlayerGesture.dominantAxis(tx, ty, slop)
                        }
                        if (axis == 0) return@detectDragGestures
                        change.consume()
                        // Only the locked axis moves the card.
                        if (axis == 1) {
                            scope.launch {
                                dragX.snapTo(tx.coerceIn(-(cardW * 1.2f), cardW * 1.2f))
                            }
                        } else {
                            scope.launch { dragY.snapTo(ty.coerceIn(0f, cardH * 1.4f)) }
                        }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        val locked = axis
                        val hasPrev = MiniPlayerGesture.hasPrevious(
                            currentIndex, queue.size, repeatMode, REPEAT_ALL, REPEAT_ONE,
                        )
                        val hasNext = MiniPlayerGesture.hasNext(
                            currentIndex, queue.size, repeatMode, REPEAT_ALL, REPEAT_ONE,
                        )
                        // Feed only the locked axis so a diagonal cannot commit twice.
                        val dx = if (locked == 1) tx else 0f
                        val dy = if (locked == 2) ty else 0f
                        val vx = if (locked == 1) v.x else 0f
                        val vy = if (locked == 2) v.y else 0f
                        when (MiniPlayerGesture.classify(dx, dy, vx, vy, hasPrev, hasNext, cardW, cardH)) {
                            MiniPlayerGesture.Action.CLEAR_QUEUE -> {
                                // Slide off the bottom, then clear. Movement first,
                                // fade second: never a fade-only dismissal.
                                scope.launch {
                                    dragY.animateTo(
                                        cardH + 320f,
                                        Motion.fade(Motion.Fast),
                                    )
                                    fade.animateTo(0f, Motion.fade(Motion.Fast))
                                    onQueueCleared(player.clearWithUndo())
                                    // The card is on its way out, so reset the
                                    // offsets now: otherwise a later restore (the
                                    // Undo) would bring it back translated and
                                    // faded. `visible` does not change again in
                                    // this pass, so this must happen here.
                                    dragY.snapTo(0f)
                                    fade.snapTo(1f)
                                    dragX.snapTo(0f)
                                }
                            }
                            MiniPlayerGesture.Action.PREVIOUS -> commitSkip(-1f)
                            MiniPlayerGesture.Action.NEXT -> commitSkip(1f)
                            MiniPlayerGesture.Action.OPEN -> {
                                springBack()
                                onOpen()
                            }
                            MiniPlayerGesture.Action.NONE -> springBack()
                        }
                    },
                    onDragCancel = { springBack() },
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