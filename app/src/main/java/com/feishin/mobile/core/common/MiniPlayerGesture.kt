package com.feishin.mobile.core.common

import kotlin.math.abs
import kotlin.math.max

/**
 * Mini player gesture rules, pure so the decision is unit-testable instead of
 * living in a pointer callback.
 *
 * The caller axis-locks first and passes only the locked axis (the other is 0),
 * so a diagonal drag can never fire two actions.
 *
 * Direction conventions (as specified): drag LEFT goes to the PREVIOUS song and
 * drag RIGHT to the NEXT one; drag DOWN clears the queue; drag UP opens Now
 * Playing. Note this inverts the pre-1.1 left/right mapping deliberately.
 */
object MiniPlayerGesture {

    /** Downward travel that commits a queue clear. */
    const val CLEAR_DISTANCE = 160f

    /** Horizontal travel that commits a skip. */
    const val SKIP_DISTANCE = 120f

    /** Upward travel that opens Now Playing. */
    const val OPEN_DISTANCE = 120f

    /** px/s in either direction that counts as a fling. */
    const val FLING_VELOCITY = 700f

    /**
     * A skip should not need an absolute number of pixels: a wider card is
     * easier to swipe, so the threshold scales with it. A clear is a short
     * downward flick on a small card and a longer one on a tall screen, so it
     * is a blend of the fixed distance and a fraction of the height.
     */
    const val SKIP_FRACTION_OF_WIDTH = 0.35f
    const val CLEAR_FRACTION_OF_HEIGHT = 0.35f
    const val OPEN_FRACTION_OF_HEIGHT = 0.25f

    enum class Action { NONE, CLEAR_QUEUE, PREVIOUS, NEXT, OPEN }

    fun classify(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
        widthPx: Float,
        heightPx: Float,
        clearDistance: Float = CLEAR_DISTANCE,
        skipDistance: Float = SKIP_DISTANCE,
        openDistance: Float = OPEN_DISTANCE,
        flingVelocity: Float = FLING_VELOCITY,
    ): Action {
        val clear = max(clearDistance, heightPx * CLEAR_FRACTION_OF_HEIGHT)
        val skip = max(skipDistance, widthPx * SKIP_FRACTION_OF_WIDTH)
        val open = max(openDistance, heightPx * OPEN_FRACTION_OF_HEIGHT)

        // Down is tested first: clearing the queue is the most destructive, so
        // it must win over an ambiguous upward release.
        if (dy >= clear || velocityY >= flingVelocity) return Action.CLEAR_QUEUE
        if (dy <= -open || velocityY <= -flingVelocity) return Action.OPEN
        if (dx <= -skip || velocityX <= -flingVelocity) {
            return if (hasPrevious) Action.PREVIOUS else Action.NONE
        }
        if (dx >= skip || velocityX >= flingVelocity) {
            return if (hasNext) Action.NEXT else Action.NONE
        }
        return Action.NONE
    }

    /**
     * Whether a previous/next exists, accounting for repeat. With repeat off at
     * the first or last track there is nothing to go to, so the swipe springs
     * back instead of restarting or wrapping.
     */
    fun hasPrevious(index: Int, size: Int, repeatMode: Int, repeatAll: Int, repeatOne: Int): Boolean =
        index > 0 || repeatMode == repeatAll || repeatMode == repeatOne || size <= 1 && index < 0

    fun hasNext(index: Int, size: Int, repeatMode: Int, repeatAll: Int, repeatOne: Int): Boolean =
        index in 0 until (size - 1) || repeatMode == repeatAll || repeatMode == repeatOne

    /** True when a skip would land on the same track (repeat-one wrap). */
    fun wouldRestartSameTrack(index: Int, size: Int, toNext: Boolean, repeatMode: Int, repeatOne: Int): Boolean =
        repeatMode == repeatOne && size > 0 &&
            ((toNext && index >= size - 1) || (!toNext && index <= 0))

    /** Absolute helper so the axis-lock assertion in tests reads clearly. */
    fun dominantAxis(dx: Float, dy: Float, slop: Float): Int = when {
        abs(dx) <= slop && abs(dy) <= slop -> 0
        abs(dx) >= abs(dy) -> 1
        else -> 2
    }
}
