package com.feishin.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.feishin.mobile.core.common.SheetPhysics
import com.feishin.mobile.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the queue sheet's position and, with it, the single source of truth for
 * whether the sheet is open.
 *
 * Why this exists: the freeze in Now Playing was an invisible, still-clickable
 * full-screen scrim. "Is the sheet up?" used to be derived from a float
 * (`progress > 0.001f`) that drags wrote directly, so a gesture interrupted
 * before its end callback left the sheet at a tiny non-zero value: composed, so
 * it ate every tap, but visually invisible.
 *
 * The rules enforced here:
 *  1. [open] only ever becomes false *after* [progress] has reached exactly 0,
 *     so a closed sheet has nothing over the screen behind it at all.
 *  2. Every release, cancel, back press and configuration change goes through
 *     [settle], which lands on one of the two anchors.
 *  3. A new drag cancels the running settle instead of racing it.
 *  4. [sheetHeightPx] is fed by the sheet's own measurement, so a drag moves the
 *     surface 1:1 with the finger on any screen size.
 */
class SheetState internal constructor(private val scope: CoroutineScope) {

    private val anim = Animatable(SheetPhysics.CLOSED)

    /** 0 = closed, 1 = open. Read only inside graphicsLayer/alpha lambdas. */
    val progress: Float get() = anim.value

    /**
     * True while the sheet and its touch-blocking scrim are part of the
     * composition. Never true at rest-closed.
     */
    var open by mutableStateOf(false)
        private set

    /** Measured sheet height; 0 until the first measurement lands. */
    var sheetHeightPx by mutableFloatStateOf(560f)

    private var settleJob: Job? = null
 
    /**
     * Authoritative position, mirrored synchronously on every drag so [settle]
     * always decides on the finger's latest position — never on a stale
     * [Animatable] value whose queued snapTo hasn't run yet. That race (a
     * release reading the pre-drag progress, then a late snap cancelling the
     * settle animation mid-flight) is what stranded the sheet halfway after
     * fast drags.
     */
    private var dragValue: Float = SheetPhysics.CLOSED

    /** Anchor the current gesture started from, so the settle is directional. */
    private var dragStart: Float = SheetPhysics.CLOSED
    private var gestureActive: Boolean = false

    /** Positive [deltaProgress] moves the sheet toward OPEN. */
    fun dragBy(deltaProgress: Float) {
        if (deltaProgress == 0f) return
        if (settleJob != null) {
            // Interrupting a settle animation: re-anchor to the live visual
            // position first, so the sheet follows the finger instead of
            // jumping to the settle's never-reached target.
            dragValue = anim.value
            settleJob?.cancel()
            settleJob = null
        }
        if (!gestureActive) {
            gestureActive = true
            dragStart = dragValue
        }
        // Keep the overlay composed for the whole gesture so the first frame of
        // a close drag is not a pop-in.
        open = true
        dragValue = (dragValue + deltaProgress).coerceIn(0f, 1f)
        val target = dragValue
        scope.launch { anim.snapTo(target) }
    }

    /** Vertical-drag lambdas report pixels; a downward drag closes the sheet. */
    fun dragByPixels(deltaPx: Float) {
        val h = sheetHeightPx
        if (h <= 0f) return
        dragBy(-deltaPx / h)
    }

    /**
     * Resolve to an anchor. Safe from a cancel path, from back press and from a
     * configuration change; [velocity] of 0 means "position only". Decides on
     * the synchronously-mirrored [dragValue], never on a possibly stale
     * animation value, and measures the displacement from [dragStart] so a
     * short drag opens from closed and equally short one closes from open.
     */
    fun settle(velocityProgressPerSec: Float = 0f) {
        val start = if (gestureActive) dragStart else dragValue
        settleTo(
            SheetPhysics.settleTargetFrom(start, dragValue, velocityProgressPerSec),
            velocityProgressPerSec,
        )
    }

    fun openSheet() = settleTo(SheetPhysics.OPEN, 0f)

    fun close() = settleTo(SheetPhysics.CLOSED, 0f)

    fun toggle() = if (open) close() else openSheet()

    private fun settleTo(target: Float, velocity: Float) {
        settleJob?.cancel()
        gestureActive = false
        // Compose the overlay BEFORE animating when opening: the sheet must
        // exist (at progress 0, fully translated away) for the rise to be
        // visible. A tap used to animate an uncomposed sheet and then pop in.
        if (target > 0.5f) open = true
        dragValue = target
        dragStart = target
        settleJob = scope.launch {
            // animateTo is cancelled by the next drag's snapTo; in that case the
            // lines below must not run, which is exactly what we want.
            anim.animateTo(target, Motion.sheetSpring(), initialVelocity = velocity)
            // Reached the anchor exactly: only now may the overlay be removed.
            open = target > 0.5f
            if (!open) anim.snapTo(SheetPhysics.CLOSED)
        }
    }

    /**
     * Gesture end of any kind. Both the normal end and the cancel path funnel
     * here so an interrupted drag can never be left mid-air.
     *
     * [velocityPxPerSec] is the raw drag velocity (positive = finger moving
     * down) and is converted to progress units, where positive means "toward
     * open".
     */
    fun onDragEnd(velocityPxPerSec: Float = 0f) {
        val h = sheetHeightPx
        settle(if (h <= 0f) 0f else -velocityPxPerSec / h)
    }
}

@Composable
fun rememberSheetState(): SheetState {
    val scope = rememberCoroutineScope()
    return remember { SheetState(scope) }
}

/** Scrim alpha for a given progress; exactly 0 when closed. */
fun scrimAlpha(progress: Float): Float = progress.coerceIn(0f, 1f) * 0.62f

/** Converts a drag velocity (px/s, downward positive) to progress units/s. */
fun velocityToProgress(velocityPxPerSec: Float, sheetHeightPx: Float): Float =
    if (sheetHeightPx <= 0f) 0f else -velocityPxPerSec / sheetHeightPx