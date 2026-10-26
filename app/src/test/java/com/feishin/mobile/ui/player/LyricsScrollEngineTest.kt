package com.feishin.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section D: pure scroll math, no Android runtime. */
class LyricsScrollEngineTest {

    @Test
    fun offsetAnchorsItemAtThirtyFivePercent() {
        // index 10 * 60px = item top 600; anchor = 1000 * 0.35 = 350.
        assertEquals(250, computeScrollOffset(targetIndex = 10, viewportHeight = 1000, itemHeight = 60))
    }

    @Test
    fun offsetClampsToZeroWhenItemSitsAboveAnchor() {
        assertEquals(0, computeScrollOffset(targetIndex = 0, viewportHeight = 1000, itemHeight = 60))
        assertEquals(0, computeScrollOffset(targetIndex = 1, viewportHeight = 1000, itemHeight = 60))
    }

    @Test
    fun offsetHonoursCustomAnchorRatio() {
        assertEquals(600, computeScrollOffset(20, viewportHeight = 800, itemHeight = 50, anchorRatio = 0.5f))
        assertEquals(1000, computeScrollOffset(20, viewportHeight = 800, itemHeight = 50, anchorRatio = 0f))
    }

    @Test
    fun offsetRejectsNegativeTargetIndex() {
        assertEquals(0, computeScrollOffset(targetIndex = -1, viewportHeight = 1000, itemHeight = 60))
        assertEquals(0, computeScrollOffset(targetIndex = -42, viewportHeight = 1000, itemHeight = 60))
    }

    @Test
    fun deltaUsesItemTopInsteadOfIndex() {
        assertEquals(250, computeScrollDelta(itemTop = 600, itemHeight = 60, viewportHeight = 1000))
        assertEquals(0, computeScrollDelta(itemTop = 100, itemHeight = 60, viewportHeight = 1000))
    }

    @Test
    fun durationIsBoundedForSingleLineStep() {
        assertEquals(450, computeScrollDurationMs(0))
        assertEquals(450, computeScrollDurationMs(1))
    }

    @Test
    fun durationIsSlowerForShortBursts() {
        // 2-5 lines: 500ms, gives quick successive lines room to coalesce.
        assertEquals(500, computeScrollDurationMs(2))
        assertEquals(500, computeScrollDurationMs(5))
    }

    @Test
    fun durationAcceleratesThroughMidRange() {
        assertEquals(350, computeScrollDurationMs(6))
        assertEquals(350, computeScrollDurationMs(10))
    }

    @Test
    fun farSeekUsesFastJump() {
        assertEquals(250, computeScrollDurationMs(11))
        assertEquals(250, computeScrollDurationMs(120))
    }

    @Test
    fun durationIgnoresDirection() {
        assertEquals(500, computeScrollDurationMs(-3))
        assertEquals(250, computeScrollDurationMs(-40))
    }

    @Test
    fun durationNeverGrowsPastShortBurstBand() {
        val durations = (0..40).map { computeScrollDurationMs(it) }
        assertTrue(durations.all { it in 250..500 })
    }

    @Test
    fun autoScrollOnlyWhenEverythingAllowsIt() {
        assertTrue(shouldAutoScroll(activeIndex = 0, userScrolledAway = false, autoScroll = true))
        assertTrue(shouldAutoScroll(activeIndex = 7, userScrolledAway = false, autoScroll = true, isSeeking = false))
    }

    @Test
    fun autoScrollRejectedWithoutActiveLine() {
        assertFalse(shouldAutoScroll(activeIndex = -1, userScrolledAway = false, autoScroll = true))
    }

    @Test
    fun autoScrollRejectedWhileUserDragged() {
        // Cooldown window owned by caller (3500ms); engine only reports the drag flag.
        assertFalse(shouldAutoScroll(activeIndex = 3, userScrolledAway = true, autoScroll = true))
    }

    @Test
    fun autoScrollRejectedWhenDisabledOrSeeking() {
        assertFalse(shouldAutoScroll(activeIndex = 3, userScrolledAway = false, autoScroll = false))
        assertFalse(shouldAutoScroll(activeIndex = 3, userScrolledAway = false, autoScroll = true, isSeeking = true))
    }

    @Test
    fun retargetReturnsFullDeltaBeyondThreshold() {
        assertEquals(120f, evaluateRetargetDelta(currentAnimatedOffset = 380f, newTargetOffset = 500f), 1e-3f)
        assertEquals(-120f, evaluateRetargetDelta(currentAnimatedOffset = 500f, newTargetOffset = 380f), 1e-3f)
    }

    @Test
    fun retargetIgnoredInsideThreshold() {
        assertEquals(0f, evaluateRetargetDelta(500f, 500f), 1e-3f)
        assertEquals(0f, evaluateRetargetDelta(500f, 500.5f), 1e-3f)
        assertEquals(0f, evaluateRetargetDelta(500f, 500.4f), 1e-3f)
        assertEquals(0.6f, evaluateRetargetDelta(500f, 500.6f), 1e-3f)
    }

    @Test
    fun retargetHonoursCustomThreshold() {
        assertEquals(0f, evaluateRetargetDelta(0f, 6f, threshold = 10f), 1e-3f)
        assertEquals(20f, evaluateRetargetDelta(0f, 20f, threshold = 10f), 1e-3f)
    }

    @Test
    fun retargetAfterQuickLineChangeMovesOnlyRemainder() {
        // Line advances mid-flight: animated offset 300, new anchor target 450.
        val delta = evaluateRetargetDelta(currentAnimatedOffset = 300f, newTargetOffset = 450f)
        assertEquals(150f, delta, 1e-3f)
        assertEquals(450f, 300f + delta, 1e-3f)
    }
}
