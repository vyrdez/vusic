package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part A1/A2: the queue sheet must only ever rest fully open or fully closed,
 * and nothing invisible may be left over the screen behind it.
 */
class SheetPhysicsTest {

    // ---- settleTarget: always one of exactly two anchors -------------------

    @Test
    fun settleTarget_neverReturnsAValueBetweenAnchors() {
        var p = 0f
        while (p <= 1.0001f) {
            for (v in listOf(-5f, -1f, -0.9f, -0.06f, 0f, 0.06f, 0.9f, 1f, 5f)) {
                val t = SheetPhysics.settleTarget(p, v)
                assertTrue("p=$p v=$v -> $t", t == SheetPhysics.CLOSED || t == SheetPhysics.OPEN)
            }
            p += 0.01f
        }
    }

    @Test
    fun settleTarget_belowThresholdAndNoFling_closes() {
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.34f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.2f, 0.04f))
    }

    @Test
    fun settleTarget_aboveThresholdAndNoFling_opens() {
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.35f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.99f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(1f, 0f))
    }

    @Test
    fun settleTarget_flingWinsOverPosition() {
        // A short but decisive flick up must open from well below the threshold.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.05f, 2.5f))
        // ...and a decisive flick down must close from well above it.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.95f, -2.5f))
    }

    @Test
    fun settleTarget_tinyVelocityIsTreatedAsNoFling() {
        // Finger jitter must not flip a settled sheet.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(1f, 0.04f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0f, -0.04f))
    }

    @Test
    fun settleTarget_outOfRangeProgressIsClampedNotThrown() {
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(4f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(-3f, 0f))
    }

    @Test
    fun settleTarget_customThresholds() {
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.5f, 0f, positionThreshold = 0.8f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.5f, 0f, positionThreshold = 0.2f))
    }

    // ---- isAtRest: what decides whether the overlay stays composed ----------

    @Test
    fun isAtRest_onlyNearAnchors() {
        assertTrue(SheetPhysics.isAtRest(0f))
        assertTrue(SheetPhysics.isAtRest(1f))
        // The residual value the old code stranded mid-gesture: close enough to
        // CLOSED to be treated as closed, so the overlay is removed.
        assertTrue(SheetPhysics.isAtRest(0.0005f))
        assertTrue(SheetPhysics.isAtRest(0.999f))
        // Genuinely in between: still an interaction, overlay stays.
        assertFalse(SheetPhysics.isAtRest(0.4f))
        assertFalse(SheetPhysics.isAtRest(0.5f))
        assertFalse(SheetPhysics.isAtRest(0.995f))
    }

    @Test
    fun epsilonBoundsExactlyWhereAtRestEnds() {
        // The old bug lived just above the old `> 0.001f` visibility threshold:
        // such a value composed an invisible, click-eating scrim. The fix is not
        // a wider epsilon but a boolean `open` flag that only clears once the
        // animation has landed on 0 exactly, so these values cannot persist.
        // This test pins the epsilon semantics that flag relies on.
        assertTrue(SheetPhysics.isAtRest(0.0009f))
        assertFalse(SheetPhysics.isAtRest(0.0011f))
        assertTrue(SheetPhysics.isAtRest(0.001f, 0.001f))
        assertTrue(SheetPhysics.isAtRest(1f - 0.0009f))
        assertFalse(SheetPhysics.isAtRest(1f - 0.0011f))
    }

    // ---- drag maths ---------------------------------------------------------

    @Test
    fun dragDown_closesAndDragUp_opens() {
        assertEquals(0.9f, SheetPhysics.progressAfterDragDown(1f, 56f, 560f), 1e-4f)
        assertEquals(0.1f, SheetPhysics.progressAfterDragDown(0f, -56f, 560f), 1e-4f)
    }

    @Test
    fun dragIsClampedToTheAnchorRange() {
        assertEquals(0f, SheetPhysics.progressAfterDragDown(0.1f, 500f, 560f), 1e-4f)
        assertEquals(1f, SheetPhysics.progressAfterDragDown(0.9f, -500f, 560f), 1e-4f)
    }

    @Test
    fun dragWithUnmeasuredHeightLeavesProgressUntouched() {
        assertEquals(0.3f, SheetPhysics.progressAfterDragDown(0.3f, 100f, 0f), 1e-4f)
        assertEquals(0.3f, SheetPhysics.progressAfterDragDown(0.3f, 100f, -1f), 1e-4f)
    }

    // ---- the freeze scenario, end to end on the pure logic ------------------

    @Test
    fun interruptedDragAlwaysResolvesToAnAnchor() {
        // Any progress a drag can leave behind, released with any velocity,
        // must resolve. There is no third outcome to get stuck in.
        val progresses = listOf(0.001f, 0.01f, 0.12f, 0.35f, 0.5f, 0.88f, 0.999f)
        val velocities = listOf(-3000f, -800f, -10f, 0f, 10f, 800f, 3000f)
        for (p in progresses) for (v in velocities) {
            val t = SheetPhysics.settleTarget(p, v)
            assertTrue("p=$p v=$v", t == 0f || t == 1f)
            assertTrue(SheetPhysics.isAtRest(t))
        }
    }

    @Test
    fun doubleSettleIsIdempotent() {
        val first = SheetPhysics.settleTarget(0.7f, 0f)
        val second = SheetPhysics.settleTarget(first, 0f)
        assertEquals(first, second, 0f)
    }

    @Test
    fun repeatedTapOutsideNeverReOpens() {
        // close() must be terminal: settling an already-closed sheet is a no-op
        // that does not leave a residual value to be mistaken for "open".
        var p = SheetPhysics.settleTarget(0.3f, 0f)
        repeat(20) { p = SheetPhysics.settleTarget(p, 0f) }
        assertEquals(SheetPhysics.CLOSED, p, 0f)
        assertTrue(SheetPhysics.isAtRest(p))
    }
}