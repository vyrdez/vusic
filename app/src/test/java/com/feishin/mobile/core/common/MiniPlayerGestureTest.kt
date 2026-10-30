package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P8-B: the mini player must follow the finger on one axis and commit exactly one action. */
class MiniPlayerGestureTest {

    private val w = 1000f   // card width  (1080 - 2*12dp margins)
    private val h = 300f    // card height (72dp)
    private val off = 0     // REPEAT_MODE_OFF
    private val one = 1     // REPEAT_MODE_ONE
    private val all = 2     // REPEAT_MODE_ALL

    private fun action(
        dx: Float = 0f, dy: Float = 0f, vx: Float = 0f, vy: Float = 0f,
        hasPrev: Boolean = true, hasNext: Boolean = true,
    ) = MiniPlayerGesture.classify(dx, dy, vx, vy, hasPrev, hasNext, w, h)

    // ---- directions -------------------------------------------------------

    @Test
    fun dragDownClears() {
        assertEquals(MiniPlayerGesture.Action.CLEAR_QUEUE, action(dy = 200f))
        assertEquals(MiniPlayerGesture.Action.CLEAR_QUEUE, action(dy = 110f, vy = 900f))
    }

    @Test
    fun dragLeftGoesNext() {
        // P10.1 convention: left = next, the card moves with the finger.
        assertEquals(MiniPlayerGesture.Action.NEXT, action(dx = -400f))
        assertEquals(MiniPlayerGesture.Action.NEXT, action(dx = -60f, vx = -900f))
    }

    @Test
    fun dragRightGoesPrevious() {
        assertEquals(MiniPlayerGesture.Action.PREVIOUS, action(dx = 400f))
        assertEquals(MiniPlayerGesture.Action.PREVIOUS, action(dx = 60f, vx = 900f))
    }

    @Test
    fun dragUpOpensNowPlaying() {
        assertEquals(MiniPlayerGesture.Action.OPEN, action(dy = -150f))
        assertEquals(MiniPlayerGesture.Action.OPEN, action(dy = -40f, vy = -900f))
    }

    @Test
    fun shortReleaseDoesNothing() {
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 40f))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = -40f))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dy = 50f))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dy = -50f))
        assertEquals(MiniPlayerGesture.Action.NONE, action())
    }

    @Test
    fun subThresholdTravelOnEveryAxisDoesNothing() {
        // Real thresholds for this card: clear = max(160, 300*0.35) = 160,
        // open = max(120, 300*0.25) = 120, skip = max(120, 1000*0.35) = 350.
        // Movement short of the threshold must not commit on any axis - this
        // is the case that covers all three directions at once, so it is not
        // the same assertion as a zero-length release.
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 100f, dy = 100f))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dy = 100f))    // short down
        assertEquals(MiniPlayerGesture.Action.NONE, action(dy = -100f))   // short up
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = -100f))   // short left
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 100f))    // short right
    }

    @Test
    fun aFlingCommitsEvenWithAlmostNoTravel() {
        // The complement of the above: velocity alone is enough, so a quick
        // flick that never travelled past the distance threshold still acts.
        // This pins both halves of each rule (distance OR velocity).
        assertEquals(MiniPlayerGesture.Action.PREVIOUS, action(dx = 5f, vx = 900f))
        assertEquals(MiniPlayerGesture.Action.CLEAR_QUEUE, action(dy = 5f, vy = 900f))
    }

    // ---- edge cases --------------------------------------------------------

    @Test
    fun noPreviousAtFirstTrackSpringsBack() {
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 400f, hasPrev = false))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 60f, vx = 900f, hasPrev = false))
    }

    @Test
    fun noNextAtLastTrackSpringsBack() {
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = -400f, hasNext = false))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = -60f, vx = -900f, hasNext = false))
    }

    @Test
    fun oneItemQueueCannotSkip() {
        // size == 1: neither direction has a neighbour, so both springs back
        // and nothing restarts.
        val hasPrev = MiniPlayerGesture.hasPrevious(0, 1, off, all, one)
        val hasNext = MiniPlayerGesture.hasNext(0, 1, off, all, one)
        assertFalse(hasPrev)
        assertFalse(hasNext)
        // right = previous, left = next (P10.1).
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 400f, hasPrev = false))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = -400f, hasNext = false))
    }

    // ---- availability with repeat -----------------------------------------

    @Test
    fun availabilityWithRepeatOff() {
        assertFalse(MiniPlayerGesture.hasPrevious(0, 5, off, all, one))
        assertTrue(MiniPlayerGesture.hasPrevious(3, 5, off, all, one))
        assertTrue(MiniPlayerGesture.hasNext(0, 5, off, all, one))
        assertFalse(MiniPlayerGesture.hasNext(4, 5, off, all, one))
    }

    @Test
    fun availabilityWithRepeatAll() {
        assertTrue(MiniPlayerGesture.hasPrevious(0, 5, all, all, one))
        assertTrue(MiniPlayerGesture.hasNext(4, 5, all, all, one))
    }

    @Test
    fun availabilityWithRepeatOneWrapsBothWays() {
        assertTrue(MiniPlayerGesture.hasPrevious(0, 5, one, all, one))
        assertTrue(MiniPlayerGesture.hasNext(4, 5, one, all, one))
    }

    @Test
    fun repeatOneAtEdgeWouldRestartSameTrack_isReported() {
        assertTrue(MiniPlayerGesture.wouldRestartSameTrack(4, 5, toNext = true, repeatMode = one, repeatOne = one))
        assertTrue(MiniPlayerGesture.wouldRestartSameTrack(0, 5, toNext = false, repeatMode = one, repeatOne = one))
        assertFalse(MiniPlayerGesture.wouldRestartSameTrack(2, 5, toNext = true, repeatMode = one, repeatOne = one))
        assertFalse(MiniPlayerGesture.wouldRestartSameTrack(4, 5, toNext = true, repeatMode = off, repeatOne = one))
    }

    // ---- axis lock ---------------------------------------------------------

    @Test
    fun dominantAxisPicksTheLargerComponent() {
        assertEquals(1, MiniPlayerGesture.dominantAxis(120f, 30f, 10f))
        assertEquals(2, MiniPlayerGesture.dominantAxis(30f, 120f, 10f))
        assertEquals(0, MiniPlayerGesture.dominantAxis(4f, 5f, 10f))
        assertEquals(0, MiniPlayerGesture.dominantAxis(0f, 0f, 10f))
    }

    @Test
    fun diagonalBelowSlopCommitsNothing() {
        assertEquals(0, MiniPlayerGesture.dominantAxis(8f, 9f, 12f))
    }

    @Test
    fun axisLockPreventsTwoActionsFromOneGesture() {
        // Once locked horizontally, the caller feeds dy = 0: a strong downward
        // component can no longer clear the queue.
        assertEquals(MiniPlayerGesture.Action.PREVIOUS, action(dx = 400f, dy = 0f))
        assertEquals(MiniPlayerGesture.Action.NONE, action(dx = 0f, dy = 0f))
    }

    // ---- scale ------------------------------------------------------------

    @Test
    fun thresholdsScaleWithCardSize() {
        // A wider card needs proportionally more travel to commit a skip:
        // 600px wide -> threshold 210, 2000px wide -> threshold 700.
        val narrow = MiniPlayerGesture.classify(250f, 0f, 0f, 0f, true, true, 600f, h)
        val wide = MiniPlayerGesture.classify(250f, 0f, 0f, 0f, true, true, 2000f, h)
        assertEquals(MiniPlayerGesture.Action.PREVIOUS, narrow)
        assertEquals(MiniPlayerGesture.Action.NONE, wide)
        // ...and far enough on the wide card it commits.
        assertEquals(
            MiniPlayerGesture.Action.PREVIOUS,
            MiniPlayerGesture.classify(800f, 0f, 0f, 0f, true, true, 2000f, h),
        )
    }

    @Test
    fun unmeasuredCardUsesTheFixedDistances() {
        // size 0 must not produce a zero threshold (every drag would commit).
        assertEquals(
            MiniPlayerGesture.Action.NONE,
            MiniPlayerGesture.classify(50f, 0f, 0f, 0f, true, true, 0f, 0f),
        )
        assertEquals(
            MiniPlayerGesture.Action.PREVIOUS,
            MiniPlayerGesture.classify(500f, 0f, 0f, 0f, true, true, 0f, 0f),
        )
    }

    @Test
    fun everyAxisAndDirectionResolvesToTheExpectedAction() {
        // Each case pins the exact action rather than merely "some action":
        // the old version asserted membership in Action.entries, which can
        // never fail for a non-null Action and so proved nothing.
        assertEquals(
            MiniPlayerGesture.Action.NONE,
            MiniPlayerGesture.classify(0f, 0f, 0f, 0f, true, true, w, h),
        )
        assertEquals(
            MiniPlayerGesture.Action.PREVIOUS,
            MiniPlayerGesture.classify(999f, 0f, 0f, 0f, true, true, w, h),
        )
        assertEquals(
            MiniPlayerGesture.Action.NEXT,
            MiniPlayerGesture.classify(-999f, 0f, 0f, 0f, true, true, w, h),
        )
        assertEquals(
            MiniPlayerGesture.Action.CLEAR_QUEUE,
            MiniPlayerGesture.classify(0f, 999f, 0f, 0f, true, true, w, h),
        )
        assertEquals(
            MiniPlayerGesture.Action.OPEN,
            MiniPlayerGesture.classify(0f, -999f, 0f, 0f, true, true, w, h),
        )
    }

    // ---- contradictory input ----------------------------------------------

    @Test
    fun upwardDragEndingInADownwardFlickDoesNotClearTheQueue() {
        // A long upward drag legitimately opens Now Playing (dy -300 is well
        // past the open threshold), so OPEN is the correct answer here - what
        // matters is that the contradictory downward release velocity does NOT
        // turn it into a queue clear.
        assertEquals(
            MiniPlayerGesture.Action.OPEN,
            MiniPlayerGesture.classify(
                dx = 0f, dy = -300f, velocityX = 0f, velocityY = 800f,
                hasPrevious = true, hasNext = true, widthPx = w, heightPx = h,
            ),
        )

        // The regression itself: an upward drag that ends below the open
        // threshold, flicking downward on release. Before the direction gate
        // this returned CLEAR_QUEUE, because the velocity arm was ungated.
        assertEquals(
            MiniPlayerGesture.Action.NONE,
            MiniPlayerGesture.classify(
                dx = 0f, dy = -50f, velocityX = 0f, velocityY = 800f,
                hasPrevious = true, hasNext = true, widthPx = w, heightPx = h,
            ),
        )
    }

    @Test
    fun aShortDownwardFlickStillClearsTheQueue() {
        // The direction gate must not disable the genuine short downward
        // flick: a small positive dy with a high downward velocity is a real
        // clear, and it is the case the fix has to keep working.
        assertEquals(
            MiniPlayerGesture.Action.CLEAR_QUEUE,
            MiniPlayerGesture.classify(0f, 12f, 0f, 900f, true, true, w, h),
        )
    }

    @Test
    fun rubberBandHorizontalOffsetBoundsShiftAndRespectsDirection() {
        val maxShift = 150f
        assertEquals(0f, MiniPlayerGesture.dampedOffsetX(0f, maxShift), 0.001f)
        val right = MiniPlayerGesture.dampedOffsetX(400f, maxShift)
        val left = MiniPlayerGesture.dampedOffsetX(-400f, maxShift)
        assertTrue(right > 0f && right <= maxShift)
        assertTrue(left < 0f && left >= -maxShift)
        assertEquals(right, -left, 0.001f)
        assertTrue(MiniPlayerGesture.dampedOffsetX(5000f, maxShift) <= maxShift)
    }

    @Test
    fun upwardFadeAlphaTracksDragSmoothly() {
        assertEquals(1f, MiniPlayerGesture.upwardFadeAlpha(0f, 300f), 0.001f)
        assertEquals(1f, MiniPlayerGesture.upwardFadeAlpha(50f, 300f), 0.001f)
        assertEquals(0.5f, MiniPlayerGesture.upwardFadeAlpha(-150f, 300f), 0.001f)
        assertEquals(0f, MiniPlayerGesture.upwardFadeAlpha(-300f, 300f), 0.001f)
        assertEquals(0f, MiniPlayerGesture.upwardFadeAlpha(-500f, 300f), 0.001f)
    }
}
