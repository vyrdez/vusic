package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part A: the next page must peek in by 28-36dp on every screen width. */
class PagerPeekTest {

    private val widths = listOf(320f, 360f, 411f, 600f)

    @Test
    fun peekIsInBandOnEveryWidth() {
        for (w in widths) {
            val peek = PagerPeek.peekVisibleDp(w, pageCount = 6)
            assertTrue("width=$w peek=$peek", peek >= 28f && peek <= 36f)
        }
    }

    @Test
    fun peekIsExactly32dp() {
        for (w in widths) {
            assertEquals(32f, PagerPeek.peekVisibleDp(w, pageCount = 6), 1e-3f)
        }
    }

    @Test
    fun pageWidthShrinksCorrectly() {
        // Page = viewport minus the unchanged left margin and the reservation
        // that produces the peek.
        assertEquals(320f - 4f - 44f, PagerPeek.pageWidthDp(320f, 6), 1e-3f)
        assertEquals(411f - 4f - 44f, PagerPeek.pageWidthDp(411f, 6), 1e-3f)
        assertEquals(600f - 4f - 44f, PagerPeek.pageWidthDp(600f, 6), 1e-3f)
    }

    @Test
    fun singlePageFillsFullWidth() {
        // No next column: symmetric margins, no stranded reservation, no peek.
        for (w in widths) {
            assertEquals(4f, PagerPeek.endPaddingDp(1), 1e-3f)
            assertEquals(w - 8f, PagerPeek.pageWidthDp(w, 1), 1e-3f)
            assertEquals(0f, PagerPeek.peekVisibleDp(w, 1), 1e-3f)
            assertTrue(PagerPeek.textColumnDp(w, 1) >= 100f)
        }
    }

    @Test
    fun textColumnStaysRoomy() {
        // Even on the smallest width the title column keeps 100dp+, so at
        // font scale 1.3 titles truncate with an ellipsis instead of reaching
        // the peek (dp layout is scale-independent; maxLines absorbs the rest).
        for (w in widths) {
            assertTrue("width=$w", PagerPeek.textColumnDp(w, 6) >= 100f)
        }
        assertEquals(132f, PagerPeek.textColumnDp(320f, 6), 1e-3f)
    }

    @Test
    fun endPaddingIsPeekPlusSpacing() {
        assertEquals(
            PagerPeek.PEEK_DP + PagerPeek.SPACING_DP,
            PagerPeek.endPaddingDp(6),
            1e-3f,
        )
    }
}
