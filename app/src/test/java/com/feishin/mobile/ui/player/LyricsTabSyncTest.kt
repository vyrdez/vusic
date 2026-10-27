package com.feishin.mobile.ui.player

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** specs/section_h_spec.md: scroll retention, 35% anchor target, frame-0 index. */
class LyricsTabSyncTest {

    @Test
    fun testScrollRetentionWhenActiveLineUnchanged() {
        val lastScrolledIndex = 7
        val activeIndex = 7
        val lineChangedWhileAway = activeIndex != lastScrolledIndex

        assertFalse(lineChangedWhileAway)
        val scrollDelta = if (lineChangedWhileAway) computeScrollOffset(activeIndex, 1000, 50) else 0
        assertEquals(0, scrollDelta)

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
    fun testThirtyFivePercentTopAnchorTargetingWhenLineChanged() {
        val viewportHeight = 1000
        val itemHeight = 60
        val activeIndex = 10 // top at 600px, anchor at 350px -> offset 250px

        val expectedOffset = (10 * itemHeight) - (viewportHeight * 0.35f).toInt()
        val computedOffset = computeScrollOffset(activeIndex, viewportHeight, itemHeight)
        assertEquals(expectedOffset, computedOffset)
        assertEquals(250, computedOffset)

        val itemTop = activeIndex * itemHeight
        assertEquals(250, computeScrollDelta(itemTop, itemHeight, viewportHeight))
    }

    @Test
    fun testImmediateFrame0ActiveIndexResolutionOnReEntry() {
        val lyrics = Lyrics(lines = (0..15).map { LyricLine(startMs = it * 2000L, text = "Line $it") })
        val lastScrolledIndex = 2
        val reEntryPositionMs = 21000L // Line 10

        val frame0ActiveIndex = lyrics.lineIndexAt(reEntryPositionMs)
        assertEquals(10, frame0ActiveIndex)
        assertTrue(frame0ActiveIndex != lastScrolledIndex)

        val targetScroll = computeScrollOffset(frame0ActiveIndex, viewportHeight = 1000, itemHeight = 50)
        assertEquals((10 * 50) - 350, targetScroll)
    }
}
