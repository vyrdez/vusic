package com.feishin.mobile.ui.player

import androidx.compose.ui.unit.Dp

enum class ScrollAction {
    SNAP,
    ANIMATE,
}

fun computeEdgeFadeAlpha(y: Float, height: Float, fadeLength: Float): Float {
    if (height <= 0f || fadeLength <= 0f) return 1f
    val topAlpha = y / fadeLength
    val bottomAlpha = (height - y) / fadeLength
    return minOf(topAlpha, bottomAlpha).coerceIn(0f, 1f)
}

fun computeLyricsBottomPadding(stripHeight: Dp, bottomInset: Dp, clearance: Dp): Dp =
    stripHeight + bottomInset + clearance

fun shouldRunLyricsScroll(
    isLyricsTabActive: Boolean,
    isScreenLeaving: Boolean,
    userScrolledAway: Boolean,
): Boolean = isLyricsTabActive && !isScreenLeaving && !userScrolledAway

fun resolveReturnScrollAction(
    isFirstLayout: Boolean,
    isReturningFromTab: Boolean,
): ScrollAction = if (isFirstLayout || isReturningFromTab) ScrollAction.SNAP else ScrollAction.ANIMATE

/**
 * Pixel distance from the top of the lyrics viewport where the active line's top edge
 * should be aligned (typically 35% of the total viewport height).
 */
fun computeLyricsAnchorPx(viewportHeight: Int, anchorFraction: Float = 0.35f): Int =
    (viewportHeight * anchorFraction).toInt().coerceAtLeast(0)

/**
 * Single unified scrollOffset to pass to LazyListState.scrollToItem(index, scrollOffset).
 *
 * In Compose Foundation LazyColumn:
 * Item `index` is placed at `contentPaddingTopPx - scrollOffset`.
 * To place item `index` at `anchorPx`:
 *   anchorPx = contentPaddingTopPx - scrollOffset
 *   scrollOffset = -(anchorPx - contentPaddingTopPx)
 *
 * This accounts accurately for `contentPadding.calculateTopPadding()` so that
 * snap/re-entry and normal auto-scroll agree down to the exact pixel.
 */
fun computeLyricsScrollOffset(
    viewportHeight: Int,
    contentPaddingTopPx: Int = 0,
    anchorFraction: Float = 0.35f,
): Int {
    val anchorPx = computeLyricsAnchorPx(viewportHeight, anchorFraction)
    return -(anchorPx - contentPaddingTopPx).coerceAtLeast(0)
}

/**
 * Single unified delta to pass to LazyListState.animateScrollBy(delta).
 *
 * Positive delta scrolls the list forward (content moves up).
 * `itemTopOffset` is `itemInfo.offset` from Compose LazyListItemInfo.
 */
fun computeLyricsScrollDelta(
    itemTopOffset: Int,
    viewportHeight: Int,
    anchorFraction: Float = 0.35f,
): Float {
    val anchorPx = computeLyricsAnchorPx(viewportHeight, anchorFraction)
    return (itemTopOffset - anchorPx).toFloat()
}
