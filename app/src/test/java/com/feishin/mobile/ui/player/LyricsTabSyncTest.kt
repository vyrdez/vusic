package com.feishin.mobile.ui.player

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** specs/section_h_spec.md & H2: scroll retention, 35% anchor target, padding math, frame-0 sync. */
class LyricsTabSyncTest {

    @Test
    fun testScrollRetentionWhenActiveLineUnchanged() {
        val lastScrolledIndex = 7
        val activeIndex = 7
        val lineChangedWhileAway = activeIndex != lastScrolledIndex

        assertFalse(lineChangedWhileAway)

        val initialVisibleIndex = 5
        val initialScrollOffset = 18
        val (retainedIndex, retainedOffset) = if (!lineChangedWhileAway) {
            initialVisibleIndex to initialScrollOffset
        } else {
            activeIndex to 0
        }
        assertEquals(initialVisibleIndex, retainedIndex)
        assertEquals(initialScrollOffset, retainedOffset)
    }

    @Test
    fun testUnifiedAnchorAndScrollOffsetMath() {
        val viewportHeight = 1000
        val contentPaddingTopPx = 50 // 32.dp

        val anchorPx = computeLyricsAnchorPx(viewportHeight, 0.35f)
        assertEquals(350, anchorPx)

        val targetScrollOffset = computeLyricsScrollOffset(viewportHeight, contentPaddingTopPx, 0.35f)
        // scrollOffset = -(anchorPx - contentPaddingTopPx) = -(350 - 50) = -300
        assertEquals(-300, targetScrollOffset)

        // In Compose: item top lands at contentPaddingTopPx - scrollOffset = 50 - (-300) = 350 == anchorPx
        val itemTopOnScreen = contentPaddingTopPx - targetScrollOffset
        assertEquals(anchorPx, itemTopOnScreen)

        // Delta when item is already at anchorPx must be exactly 0
        val deltaAtAnchor = computeLyricsScrollDelta(itemTopOffset = anchorPx, viewportHeight = viewportHeight, anchorFraction = 0.35f)
        assertEquals(0f, deltaAtAnchor, 0.001f)

        // Delta when item is below anchor (at 500px, needs to scroll up by 150px)
        val deltaBelow = computeLyricsScrollDelta(itemTopOffset = 500, viewportHeight = viewportHeight, anchorFraction = 0.35f)
        assertEquals(150f, deltaBelow, 0.001f)
    }

    @Test
    fun testImmediateFrame0ActiveIndexResolutionOnReEntry() {
        val lyrics = Lyrics(lines = (0..15).map { LyricLine(startMs = it * 2000L, text = "Line $it") })
        val lastScrolledIndex = 2
        val reEntryPositionMs = 21000L // Line 10

        val frame0ActiveIndex = lyrics.lineIndexAt(reEntryPositionMs)
        assertEquals(10, frame0ActiveIndex)
        assertTrue(frame0ActiveIndex != lastScrolledIndex)

        val viewportHeight = 1000
        val contentPaddingTopPx = 48
        val targetScrollOffset = computeLyricsScrollOffset(viewportHeight, contentPaddingTopPx)
        assertEquals(-(350 - 48), targetScrollOffset)
    }
}
