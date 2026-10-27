package com.feishin.mobile.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class LyricsAnimationTest {

    private fun alphaForDistance(distance: Int): Float = when (abs(distance)) {
        0 -> 1.0f
        1 -> 0.42f
        2 -> 0.22f
        else -> 0.10f
    }

    private fun scaleForDistance(distance: Int): Float =
        if (abs(distance) == 0) 1.06f else 1.0f

    private fun romajiAlpha(lineAlpha: Float, isActive: Boolean): Float =
        lineAlpha * (if (isActive) 0.70f else 0.85f)

    @Test
    fun distanceBasedOpacityFalloff_matchesCurve() {
        assertEquals(1.0f, alphaForDistance(0), 1e-4f)
        assertEquals(0.42f, alphaForDistance(1), 1e-4f)
        assertEquals(0.22f, alphaForDistance(2), 1e-4f)
        assertEquals(0.10f, alphaForDistance(3), 1e-4f)
        assertEquals(0.10f, alphaForDistance(10), 1e-4f)
        assertEquals(0.42f, alphaForDistance(-1), 1e-4f)
    }

    @Test
    fun scaleValues_activeEnlargedInactiveNormal() {
        assertEquals(1.06f, scaleForDistance(0), 1e-4f)
        assertEquals(1.0f, scaleForDistance(1), 1e-4f)
        assertEquals(1.0f, scaleForDistance(2), 1e-4f)
        assertEquals(1.0f, scaleForDistance(5), 1e-4f)
    }

    @Test
    fun romajiOpacityScaling_appliesCorrectMultiplier() {
        val activeLineAlpha = alphaForDistance(0)
        assertEquals(0.70f, romajiAlpha(activeLineAlpha, isActive = true), 1e-4f)

        val inactiveLineAlpha1 = alphaForDistance(1)
        assertEquals(0.42f * 0.85f, romajiAlpha(inactiveLineAlpha1, isActive = false), 1e-4f)

        val inactiveLineAlpha2 = alphaForDistance(2)
        assertEquals(0.22f * 0.85f, romajiAlpha(inactiveLineAlpha2, isActive = false), 1e-4f)

        val inactiveLineAlpha3 = alphaForDistance(3)
        assertEquals(0.10f * 0.85f, romajiAlpha(inactiveLineAlpha3, isActive = false), 1e-4f)
    }
}
