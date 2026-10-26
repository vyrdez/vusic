package com.feishin.mobile.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section H contract (specs/section_h_spec.md): edge fade, bottom padding, freeze, return. */
class LyricsTabCorrectionsTest {

    private val fade = 56f
    private val height = 800f
    private fun alpha(y: Float) = computeEdgeFadeAlpha(y = y, height = height, fadeLength = fade)

    // H4: fade height constant is 56dp, alpha 0 at each edge, 1 after the ramp.
    @Test
    fun fadeIsZeroAtBothEdges() {
        assertEquals(0f, alpha(0f), 1e-4f)
        assertEquals(0f, alpha(height), 1e-4f)
    }

    @Test
    fun fadeReachesFullAlphaAt56dpFromEachEdge() {
        assertEquals(1f, alpha(fade), 1e-4f)
        assertEquals(1f, alpha(height - fade), 1e-4f)
        assertEquals(1f, alpha(fade + 1f), 1e-4f)
        assertEquals(1f, alpha(height - fade - 1f), 1e-4f)
    }

    @Test
    fun fadeRampsLinearlyThroughHalfFade() {
        assertEquals(0.5f, alpha(fade / 2f), 1e-4f)
        assertEquals(0.5f, alpha(height - fade / 2f), 1e-4f)
    }

    @Test
    fun fadeIsSymmetricTopAndBottom() {
        listOf(0f, 1f, 14f, 28f, 42f, 55f, 56f).forEach { y ->
            assertEquals("alpha must mirror at y=$y", alpha(y), alpha(height - y), 1e-4f)
        }
    }

    @Test
    fun fadeStaysInsideUnitRange() {
        assertTrue((0..800).all { alpha(it.toFloat()) in 0f..1f })
    }

    // H1: padding clears strip + navigation inset, and never falls below the strip.
    @Test
    fun bottomPaddingSumsStripInsetAndClearance() {
        assertEquals(96.dp, computeLyricsBottomPadding(stripHeight = 72.dp, bottomInset = 8.dp, clearance = 16.dp))
        assertTrue(computeLyricsBottomPadding(72.dp, 8.dp, 16.dp) >= 72.dp)
    }

    @Test
    fun bottomPaddingExtendsUnderNavigationInset() {
        assertEquals(120.dp, computeLyricsBottomPadding(stripHeight = 72.dp, bottomInset = 32.dp, clearance = 16.dp))
        assertEquals(72.dp, computeLyricsBottomPadding(stripHeight = 72.dp, bottomInset = 0.dp, clearance = 0.dp))
    }

    // H3.2: leaving the tab freezes the scroll, so no retargeting runs.
    @Test
    fun scrollRunsWhenTabFullyActive() {
        assertTrue(shouldRunLyricsScroll(isLyricsTabActive = true, isScreenLeaving = false, userScrolledAway = false))
    }

    @Test
    fun scrollStopsWhileTabIsLeaving() {
        assertFalse(shouldRunLyricsScroll(isLyricsTabActive = true, isScreenLeaving = true, userScrolledAway = false))
    }

    @Test
    fun scrollStopsWhenTabInactiveOrUserScrolledAway() {
        assertFalse(shouldRunLyricsScroll(isLyricsTabActive = false, isScreenLeaving = false, userScrolledAway = false))
        assertFalse(shouldRunLyricsScroll(isLyricsTabActive = true, isScreenLeaving = false, userScrolledAway = true))
    }

    // H3.3: returning snaps once the viewport is measured, never animates from top.
    @Test
    fun returnOrFirstLayoutSnaps() {
        assertEquals(ScrollAction.SNAP, resolveReturnScrollAction(isFirstLayout = false, isReturningFromTab = true))
        assertEquals(ScrollAction.SNAP, resolveReturnScrollAction(isFirstLayout = true, isReturningFromTab = false))
    }

    @Test
    fun steadyStateAnimatesLineToLine() {
        assertEquals(ScrollAction.ANIMATE, resolveReturnScrollAction(isFirstLayout = false, isReturningFromTab = false))
    }
}
