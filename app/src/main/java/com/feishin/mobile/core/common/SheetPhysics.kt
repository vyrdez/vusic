package com.feishin.mobile.core.common

import kotlin.math.abs

/**
 * Two-anchor sheet physics, kept pure so the decision "where does the sheet
 * rest" is unit-testable instead of being buried in a gesture callback.
 *
 * The queue sheet has exactly two resting positions — CLOSED (0) and OPEN (1).
 * A drag may leave it anywhere in between, but a release always resolves to one
 * of the two, and cancelling the gesture resolves it too. That is what stops the
 * sheet from parking half-open with an invisible but touch-eating layer over
 * the screen underneath.
 */
object SheetPhysics {

    const val CLOSED = 0f
    const val OPEN = 1f

    /**
     * Position threshold: below this the sheet snaps back unless the fling
     * clearly wants it open. Above it, the sheet completes unless the fling
     * clearly wants it closed. 0.18 means "a short upward drag that passes
     * roughly a fifth of the travel is a decision to open" — the old 0.35
     * made short drags visibly open the sheet and then snap it shut again.
     */
    const val POSITION_THRESHOLD = 0.18f

    /**
     * Fling threshold in progress-units per second. A decisive flick wins over
     * the position, so a quick short flick still opens the sheet and a quick
     * flick downward still closes it.
     *
     * 0.5 is deliberately low. Touch slop swallows the first ~47px of a drag,
     * so the velocity Compose reports is meaningfully below the geometric
     * average; at 0.9 a real finger flick of ~2000px/s still fell under the
     * bar and was ignored. 0.5 is ~940px/s on a 1872px sheet — well above a
     * deliberate drag (which the position rule handles) and comfortably below a
     * flick.
     */
    const val VELOCITY_THRESHOLD = 0.5f

    /**
     * Velocity below which we ignore the fling entirely (finger jitter / a
     * tap that drifted a couple of pixels).
     */
    const val VELOCITY_DEADZONE = 0.05f

    /**
     * Resolves the anchor a release should settle on.
     *
     * @param progress current position, 0 = closed, 1 = open
     * @param velocity px/s converted to progress units per second; positive
     *        means "moving toward open". Direction is flipped by callers that
     *        report drag deltas rather than sheet offsets.
     * @return [OPEN] or [CLOSED], never anything in between.
     */
    fun settleTarget(
        progress: Float,
        velocity: Float,
        positionThreshold: Float = POSITION_THRESHOLD,
        velocityThreshold: Float = VELOCITY_THRESHOLD,
        velocityDeadzone: Float = VELOCITY_DEADZONE,
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        if (abs(velocity) >= velocityDeadzone) {
            return if (velocity > 0f) OPEN else CLOSED
        }
        return if (p >= positionThreshold) OPEN else CLOSED
    }

    /**
     * Directional settle: decides from the anchor the gesture STARTED at.
     *
     * [settleTarget] is absolute, so with a small open threshold (0.18) closing
     * would need ~82% of the travel — a long downward drag could never close a
     * sheet that a short upward drag had opened. Measuring the displacement from
     * [startProgress] makes the two directions symmetric, which is what the
     * gesture actually means:
     *  - short upward drag from closed, or any upward fling  -> [OPEN]
     *  - short downward drag from open, or any downward fling -> [CLOSED]
     *  - movement below the threshold returns to the starting anchor, so a
     *    stray touch never moves the sheet.
     *
     * @param startProgress progress when the gesture began (an anchor, 0 or 1)
     * @param currentProgress progress at release
     * @param velocity progress units per second; positive means "toward open"
     */
    fun settleTargetFrom(
        startProgress: Float,
        currentProgress: Float,
        velocity: Float,
        positionThreshold: Float = POSITION_THRESHOLD,
        velocityThreshold: Float = VELOCITY_THRESHOLD,
    ): Float {
        val start = startProgress.coerceIn(0f, 1f)
        val current = currentProgress.coerceIn(0f, 1f)
        // A decisive flick wins outright.
        if (abs(velocity) >= velocityThreshold) {
            return if (velocity > 0f) OPEN else CLOSED
        }
        val delta = current - start
        if (delta >= positionThreshold) return OPEN
        if (delta <= -positionThreshold) return CLOSED
        // Too small to be a decision: snap back to where we came from.
        return if (start >= 0.5f) OPEN else CLOSED
    }

    /**
     * True when [value] is close enough to an anchor that the sheet should be
     * treated as fully at rest. Used to decide whether the touch-blocking
     * overlay may be removed from the composition.
     */
    fun isAtRest(value: Float, epsilon: Float = 0.001f): Boolean =
        value <= CLOSED + epsilon || value >= OPEN - epsilon

    /**
     * Progress implied by a drag of [delta] px over a sheet [sheetHeightPx]
     * tall. Dragging DOWN (positive delta) closes the sheet, so the sign is
     * inverted; the result is clamped to the two anchors' range.
     */
    fun progressAfterDragDown(progress: Float, delta: Float, sheetHeightPx: Float): Float {
        if (sheetHeightPx <= 0f) return progress.coerceIn(0f, 1f)
        return (progress - delta / sheetHeightPx).coerceIn(0f, 1f)
    }
}