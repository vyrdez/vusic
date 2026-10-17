package com.feishin.mobile.core.common

import com.feishin.mobile.ui.components.velocityToProgress
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
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.17f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.1f, 0.04f))
    }

    @Test
    fun settleTarget_aboveThresholdAndNoFling_opens() {
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.18f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.19f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.99f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(1f, 0f))
    }

    @Test
    fun settleTarget_shortUpwardDragPastThreshold_opens() {
        // The flicker this phase fixes: a short upward drag that passes roughly
        // a fifth of the travel must open fully, never snap back shut.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.19f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.25f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.17f, 0f))
    }

    @Test
    fun settleTarget_upwardFlingFromNearClosed_opens() {
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.05f, 300f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0f, 300f))
    }

    @Test
    fun settleTarget_downwardFlingFromOpen_closes() {
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.95f, -300f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(1f, -300f))
    }

    @Test
    fun settleTarget_slowReleaseJustBelowThreshold_closes() {
        // A gentle release below the threshold still closes: direction and
        // fling only win past the deadzone or the threshold.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.1f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.1f, 0.04f))
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
        var p = SheetPhysics.settleTarget(0.1f, 0f)
        repeat(20) { p = SheetPhysics.settleTarget(p, 0f) }
        assertEquals(SheetPhysics.CLOSED, p, 0f)
        assertTrue(SheetPhysics.isAtRest(p))
    }

    // ---- directional settle (the gesture actually means direction) ---------

    @Test
    fun settleTargetFrom_shortUpwardDragFromClosed_opens() {
        // Started closed, moved up 18%+ -> open.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(0f, 0.19f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(0f, 0.5f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(0f, 1f, 0f))
    }

    @Test
    fun settleTargetFrom_shortDownwardDragFromOpen_closes() {
        // The symmetric case the absolute rule could not express: from open,
        // a 18% downward drag closes instead of needing ~82% of the travel.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(1f, 0.81f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(1f, 0.5f, 0f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(1f, 0f, 0f))
    }

    @Test
    fun settleTargetFrom_tinyMovement_returnsToStartingAnchor() {
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(0f, 0.1f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(1f, 0.9f, 0f))
    }

    @Test
    fun settleTargetFrom_flingOverridesPosition() {
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(1f, 0.95f, 3f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(0f, 0.05f, -3f))
    }

    @Test
    fun settleTargetFrom_weakVelocityIsNotAFling() {
        // Below the fling threshold the POSITION decides, so a weak velocity
        // pointing the other way cannot override a decisive drag.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(1f, 0.5f, 0.3f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(0f, 0.5f, -0.3f))
        // ...and with only a tiny movement the sheet returns to where it began,
        // ignoring the weak nudge entirely.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(1f, 0.95f, 0.3f))
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(0f, 0.05f, -0.3f))
    }

    @Test
    fun settleTargetFrom_alwaysLandsOnAnAnchor() {
        val starts = listOf(0f, 1f)
        val currents = listOf(0f, 0.05f, 0.17f, 0.18f, 0.5f, 0.82f, 0.95f, 1f)
        val vels = listOf(-3f, -0.9f, -0.2f, 0f, 0.2f, 0.9f, 3f)
        for (s in starts) for (c in currents) for (v in vels) {
            val t = SheetPhysics.settleTargetFrom(s, c, v)
            assertTrue("s=$s c=$c v=$v -> $t", t == SheetPhysics.CLOSED || t == SheetPhysics.OPEN)
            assertTrue(SheetPhysics.isAtRest(t))
        }
    }

    @Test
    fun settleTargetFrom_noGestureStartEqualsCurrent_returnsToThatAnchor() {
        // settle() with no gesture in flight: start == current, so the sheet
        // stays where it already is.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTargetFrom(0f, 0f, 0f))
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTargetFrom(1f, 1f, 0f))
    }

    @Test
    fun velocityToProgress_downwardIsNegative_upwardIsPositive() {
        // 500 px/s downward on a 1000 px sheet = half the travel per second
        // toward closed.
        assertEquals(-0.5f, velocityToProgress(500f, 1000f), 1e-4f)
        assertEquals(0.5f, velocityToProgress(-500f, 1000f), 1e-4f)
        assertEquals(0f, velocityToProgress(0f, 1000f), 1e-4f)
        assertEquals(0f, velocityToProgress(500f, 0f), 1e-4f)
    }
}
