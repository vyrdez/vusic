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
     * clearly wants it closed. 0.35 means "less than a third of the way is not
     * a decision".
     */
    const val POSITION_THRESHOLD = 0.35f

    /**
     * Fling threshold in progress-units per second. A decisive flick wins over
     * the position, so a quick short flick still opens the sheet and a quick
     * flick downward still closes it.
     */
    const val VELOCITY_THRESHOLD = 0.9f

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