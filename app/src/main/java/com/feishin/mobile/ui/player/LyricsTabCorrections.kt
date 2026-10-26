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
