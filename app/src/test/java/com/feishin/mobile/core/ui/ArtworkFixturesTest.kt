package com.feishin.mobile.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFixturesTest {

    private val expectedNames = listOf(
        "white",
        "near_white_pastel",
        "white_blue_logo",
        "gray",
        "black",
        "red",
        "blue",
        "multicolor",
        "transparent_edge",
    )

    @Test
    fun allFixturesPresentWithCorrectDimensions() {
        assertEquals(expectedNames.toSet(), ArtworkFixtures.ALL.keys)
        assertEquals(expectedNames.toSet(), ArtworkFixtures.EXPECTED_HUE_FAMILIES.keys)

        for ((name, pixels) in ArtworkFixtures.ALL) {
            assertEquals("width for $name", ArtworkFixtures.DEFAULT_SIZE, pixels.width)
            assertEquals("height for $name", ArtworkFixtures.DEFAULT_SIZE, pixels.height)
            assertEquals(
                "pixel count for $name",
                ArtworkFixtures.DEFAULT_SIZE * ArtworkFixtures.DEFAULT_SIZE,
                pixels.argb.size,
            )
        }
    }

    @Test
    fun generatorsAreDeterministic() {
        val first = listOf(
            ArtworkFixtures.white(),
            ArtworkFixtures.nearWhitePastel(),
            ArtworkFixtures.whiteBlueLogo(),
            ArtworkFixtures.gray(),
            ArtworkFixtures.black(),
            ArtworkFixtures.red(),
            ArtworkFixtures.blue(),
            ArtworkFixtures.multicolor(),
            ArtworkFixtures.transparentEdge(),
        )
        val second = listOf(
            ArtworkFixtures.white(),
            ArtworkFixtures.nearWhitePastel(),
            ArtworkFixtures.whiteBlueLogo(),
            ArtworkFixtures.gray(),
            ArtworkFixtures.black(),
            ArtworkFixtures.red(),
            ArtworkFixtures.blue(),
            ArtworkFixtures.multicolor(),
            ArtworkFixtures.transparentEdge(),
        )

        assertEquals(first, second)
    }

    @Test
    fun whiteBlueLogoHasOneToSixPercentBlueAndRestWhite() {
        val pixels = ArtworkFixtures.whiteBlueLogo()
        var blueCount = 0
        var whiteCount = 0
        for (c in pixels.argb) {
            when (c) {
                ArtworkFixtures.COLOR_LOGO_BLUE -> blueCount++
                ArtworkFixtures.COLOR_WHITE -> whiteCount++
                else -> throw AssertionError("Unexpected pixel color: 0x${c.toUInt().toString(16)}")
            }
        }
        val ratio = blueCount.toDouble() / pixels.argb.size
        assertTrue("Expected 1%-6% blue pixels, got $ratio", ratio in 0.01..0.06)
        assertEquals(pixels.argb.size, blueCount + whiteCount)
    }

    @Test
    fun transparentEdgeHasMoreThanFiftyPercentTransparentPixelsAndVisibleCenter() {
        val pixels = ArtworkFixtures.transparentEdge()
        var transparentCount = 0
        var opaqueColoredCount = 0
        for (c in pixels.argb) {
            val alpha = (c ushr 24) and 0xFF
            if (alpha == 0) {
                transparentCount++
            } else if (alpha == 255) {
                opaqueColoredCount++
            }
        }
        val transparentRatio = transparentCount.toDouble() / pixels.argb.size
        assertTrue("Expected >50% alpha==0 pixels, got $transparentRatio", transparentRatio > 0.50)
        assertTrue("Expected visible opaque center pixels", opaqueColoredCount > 0)
    }

    @Test
    fun multicolorHasFourDistinctRegionsWithOneClearlyLargest() {
        val pixels = ArtworkFixtures.multicolor()
        val counts = pixels.argb.asIterable().groupingBy { it }.eachCount()
        assertEquals(4, counts.size)
        val sorted = counts.values.sortedDescending()
        assertTrue("Largest region should exceed 50%", sorted[0] > pixels.argb.size * 0.50)
        assertTrue("Largest region should be >2x second largest", sorted[0] > sorted[1] * 2)
    }
}
