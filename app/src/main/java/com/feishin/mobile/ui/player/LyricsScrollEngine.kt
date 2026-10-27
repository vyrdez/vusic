package com.feishin.mobile.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

object LyricsDisplayTokens {
    val LineGap: Dp = 22.dp
    fun lineGap(percent: Int): Dp = (22f * (percent.coerceIn(50, 200) / 100f)).dp
    val RomanizedGap: Dp = 4.dp
    const val InactiveAlpha: Float = 0.40f
    const val ActiveAlpha: Float = 1.0f
    const val ActiveScale: Float = 1.06f
}

fun computeScrollOffset(targetIndex: Int, viewportHeight: Int, itemHeight: Int, anchorRatio: Float = 0.35f): Int {
    if (targetIndex < 0) return 0
    return ((targetIndex * itemHeight) - (viewportHeight * anchorRatio).toInt()).coerceAtLeast(0)
}

fun computeScrollDelta(itemTop: Int, itemHeight: Int, viewportHeight: Int, anchorRatio: Float = 0.35f): Int =
    (itemTop - (viewportHeight * anchorRatio).toInt()).coerceAtLeast(0)

fun computeScrollDurationMs(lineDistance: Int): Int = when (val dist = abs(lineDistance)) {
    0, 1 -> 450
    in 2..5 -> 500
    in 6..10 -> 350
    else -> 250
}

fun shouldAutoScroll(activeIndex: Int, userScrolledAway: Boolean, autoScroll: Boolean, isSeeking: Boolean = false): Boolean =
    activeIndex >= 0 && autoScroll && !userScrolledAway && !isSeeking

fun evaluateRetargetDelta(currentAnimatedOffset: Float, newTargetOffset: Float, threshold: Float = 0.5f): Float =
    if (abs(newTargetOffset - currentAnimatedOffset) > threshold) newTargetOffset - currentAnimatedOffset else 0f
