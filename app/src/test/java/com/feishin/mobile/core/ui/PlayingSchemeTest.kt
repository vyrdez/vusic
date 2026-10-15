package com.feishin.mobile.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part C: readability is a hard requirement. Every text/background pair the
 * scheme produces is asserted here — titles and secondary text at 4.5:1,
 * icons and the ring at 3:1 — over 8 seeds in both themes.
 *
 * Each case also prints its measured ratios; the test XML's system-out is the
 * contrast log for the release report.
 */
class PlayingSchemeTest {

    private data class Seed(val name: String, val argb: Long?)

    private val seeds = listOf(
        Seed("white", 0xFFFFFFFFL),
        Seed("black", 0xFF000000L),
        Seed("mid-gray", 0xFF808080L),
        Seed("sat-yellow", 0xFFFFD400L),
        Seed("sat-red", 0xFFE8112DL),
        Seed("sat-blue", 0xFF1445DCL),
        Seed("pastel-pink", 0xFFF4B8D0L),
        Seed("null", null),
    )

    // App theme fallback pairs (dark/light primaryContainer + onPrimaryContainer).
    private val darkFallback = 0xFF00497BL to 0xFFD1E4FFL
    private val lightFallback = 0xFFD1E4FFL to 0xFF0B0F14L

    private fun check(seed: Seed, dark: Boolean) {
        val (fc, fo) = if (dark) darkFallback else lightFallback
        val s = PlayingColors.schemeFor(seed.argb, fc, fo, dark)
        val title = Contrast.ratio(s.onContainer, s.container)
        val secondary = Contrast.ratio(s.secondary, s.container)
        val ring = Contrast.ratio(s.primary, s.container)
        val ringTrack = Contrast.ratio(s.primary, s.track)
        val onPrimary = Contrast.ratio(s.onPrimary, s.primary)
        val surfTop = Contrast.ratio(s.surfaceText, s.surfaceTop)
        val surfBottom = Contrast.ratio(s.surfaceText, s.surfaceBottom)
        val surfSecTop = Contrast.ratio(s.surfaceSecondary, s.surfaceTop)
        val surfSecBottom = Contrast.ratio(s.surfaceSecondary, s.surfaceBottom)
        println(
            "CONTRAST seed=${seed.name} dark=$dark " +
                "title=${fmt(title)} secondary=${fmt(secondary)} " +
                "ring=${fmt(ring)} ringTrack=${fmt(ringTrack)} " +
                "onPrimary=${fmt(onPrimary)} surfTop=${fmt(surfTop)} " +
                "surfBottom=${fmt(surfBottom)} surfSec=${fmt(surfSecTop)}/${fmt(surfSecBottom)}",
        )
        val tag = "${seed.name} dark=$dark"
        assertTrue("$tag title $title", title >= 4.5)
        assertTrue("$tag secondary $secondary", secondary >= 4.5)
        assertTrue("$tag ring $ring", ring >= 3.0)
        assertTrue("$tag ringTrack $ringTrack", ringTrack >= 3.0)
        assertTrue("$tag onPrimary $onPrimary", onPrimary >= 4.5)
        assertTrue("$tag surfTop $surfTop", surfTop >= 4.5)
        assertTrue("$tag surfBottom $surfBottom", surfBottom >= 4.5)
        assertTrue("$tag surfSecTop $surfSecTop", surfSecTop >= 4.5)
        assertTrue("$tag surfSecBottom $surfSecBottom", surfSecBottom >= 4.5)
    }

    @Test
    fun allSeedsPassInDarkTheme() {
        for (seed in seeds) check(seed, dark = true)
    }

    @Test
    fun allSeedsPassInLightTheme() {
        for (seed in seeds) check(seed, dark = false)
    }

    @Test
    fun graySeedFallsBackToThemePair() {
        val s = PlayingColors.schemeFor(0xFF808080L, darkFallback.first, darkFallback.second, dark = true)
        assertTrue("gray must be unusable", s.seedArgb == null)
    }

    @Test
    fun nullSeedFallsBackToThemePair() {
        val s = PlayingColors.schemeFor(null, lightFallback.first, lightFallback.second, dark = false)
        assertTrue(s.container == lightFallback.first)
        assertTrue(s.onContainer == lightFallback.second)
    }

    @Test
    fun contrastFunctionSanity() {
        assertTrue(Contrast.ratio(0xFFFFFFFFL, 0xFF000000L) > 20.9)
        assertTrue(Contrast.ratio(0xFFFFFFFFL, 0xFFFFFFFFL) < 1.001)
        // Tinted container keeps its hue while the text stays black/white-safe.
        val s = PlayingColors.schemeFor(0xFFE8112DL, darkFallback.first, darkFallback.second, dark = true)
        assertTrue(s.seedArgb == 0xFFE8112DL)
    }

    private fun fmt(d: Double): String = "%.2f".format(d)
}
