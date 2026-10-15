package com.feishin.mobile.core.common

/**
 * Page-width maths for the Home song pagers (Quick picks, Forgotten
 * favorites), kept pure so the peek is exact by construction instead of
 * eyeballed on one screen size.
 *
 * The pager reserves [END_PADDING_DP] after the last page slot: the page
 * itself fills `viewport - START - END`, so the next page always starts
 * [PEEK_DP] before the right edge and its first column peeks in. The
 * [SPACING_DP] between pages is part of that reservation, which is what keeps
 * the peek identical on every screen width.
 */
object PagerPeek {

    /** Visible slice of the next page: inside the asked 28-36dp band. */
    const val PEEK_DP = 32f

    /** Gap between two pages. */
    const val SPACING_DP = 12f

    /** Left margin of the first page (unchanged from the full-width pager). */
    const val START_DP = 4f

    /**
     * End content padding that produces the peek. Only when there IS a next
     * page to peek: a lone page has nothing to reveal, so it fills the full
     * width (symmetric with the left margin) instead of stranding an empty
     * reservation on the right.
     */
    fun endPaddingDp(pageCount: Int): Float =
        if (pageCount <= 1) START_DP else PEEK_DP + SPACING_DP

    /** Width one page fills inside a viewport [viewportWidthDp] wide. */
    fun pageWidthDp(viewportWidthDp: Float, pageCount: Int): Float =
        viewportWidthDp - START_DP - endPaddingDp(pageCount)

    /**
     * How much of the next page stays visible while a non-last page is
     * settled. Always [PEEK_DP] on multi-page sections (and 0 when there is
     * only one page, which fills the width); the test pins the identity so a
     * refactor cannot silently eat the peek.
     */
    fun peekVisibleDp(viewportWidthDp: Float, pageCount: Int): Float =
        if (pageCount <= 1) {
            0f
        } else {
            viewportWidthDp - (START_DP + pageWidthDp(viewportWidthDp, pageCount) + SPACING_DP)
        }

    /**
     * Text column left for a TrackRow inside a page: page minus the row's own
     * chrome (24dp horizontal padding, 56dp art, 12dp art gap, 48dp ⋮ menu).
     * Must stay comfortably positive on the smallest supported width so long
     * titles truncate with an ellipsis instead of running into the peek — at
     * font scale 1.3 the dp column is identical, only the glyphs grow, which
     * is exactly what maxLines + ellipsis absorbs.
     */
    fun textColumnDp(viewportWidthDp: Float, pageCount: Int = Int.MAX_VALUE): Float =
        pageWidthDp(viewportWidthDp, pageCount) - 140f
}
