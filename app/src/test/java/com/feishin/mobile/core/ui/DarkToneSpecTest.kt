package com.feishin.mobile.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 6.8: the DARK tint must stay muted. Every tone and chroma produced for
 * the dark scheme is asserted inside the [DarkToneSpec] caps, and every
 * contrast requirement still passes. The eight seeds cover the spec's edge
 * cases (extreme tone, extreme chroma, gray, missing art).
 *
 * Each case also prints its measured tones/chromas/ratios: the test XML's
 * system-out is the tuning log for the report.
 */
class DarkToneSpecTest {

    private data class Seed(val name: String, val argb: Long?)

    private val seeds = listOf(
        Seed("white", 0xFFFFFFFFL),
        Seed("black", 0xFF000000L),
        Seed("gray", 0xFF808080L),
        Seed("yellow", 0xFFFFD400L),
        Seed("red", 0xFFE8112DL),
        Seed("blue", 0xFF1445DCL),
        Seed("pastel-pink", 0xFFF4B8D0L),
        Seed("null", null),
    )

    private val darkFallback = 0xFF3A3F46L to 0xFFEAF0F7L

    private fun scheme(seed: Seed) = PlayingColors.schemeFor(
        seedArgb = seed.argb,
        fallbackContainer = darkFallback.first,
        fallbackOnContainer = darkFallback.second,
        dark = true,
    )

    private fun tone(c: Long) = Contrast.toneOf(c)
    private fun chroma(c: Long) = Contrast.chromaOf(c)

    /** Seeds with a usable hue: the tinted path. */
    private val chromatic = listOf(
        Seed("yellow", 0xFFFFD400L),
        Seed("red", 0xFFE8112DL),
        Seed("blue", 0xFF1445DCL),
        Seed("pastel-pink", 0xFFF4B8D0L),
    )

    /** Seeds that must take the neutral theme fallback (no usable hue). */
    private val neutral = listOf(
        Seed("white", 0xFFFFFFFFL),
        Seed("black", 0xFF000000L),
        Seed("gray", 0xFF808080L),
        Seed("null", null),
    )

    @Test
    fun tonesAndChromasStayInsideTheCaps() {
        for (seed in chromatic) {
            val s = scheme(seed)
            val tag = seed.name
            // Highlight container: tone ~22, chroma capped ~22.
            assertTrue(
                "$tag highlight tone ${tone(s.container)}",
                tone(s.container) in 17.0..27.0,
            )
            assertTrue(
                "$tag highlight chroma ${chroma(s.container)}",
                chroma(s.container) <= DarkToneSpec.HIGHLIGHT_CHROMA + 2.0,
            )
            // Mini player: markedly darker than the highlight, faint hue.
            assertTrue("$tag mini tone ${tone(s.miniContainer)}", tone(s.miniContainer) in 8.0..17.0)
            assertTrue(
                "$tag mini chroma ${chroma(s.miniContainer)}",
                chroma(s.miniContainer) <= DarkToneSpec.MINI_CHROMA + 2.0,
            )
            assertTrue(
                "$tag mini must be darker than highlight",
                tone(s.miniContainer) < tone(s.container) - 5.0,
            )
            // Accent: tone ~75, chroma capped ~34 (never neon).
            assertTrue("$tag accent tone ${tone(s.primary)}", tone(s.primary) in 65.0..85.0)
            assertTrue(
                "$tag accent chroma ${chroma(s.primary)}",
                chroma(s.primary) <= DarkToneSpec.ACCENT_CHROMA + 2.5,
            )
            // Gradients: both darker than the highlight, top lighter.
            assertTrue(
                "$tag surfaceTop ${tone(s.surfaceTop)} vs highlight ${tone(s.container)}",
                tone(s.surfaceTop) < tone(s.container),
            )
            assertTrue(
                "$tag surfaceBottom ${tone(s.surfaceBottom)} vs highlight",
                tone(s.surfaceBottom) < tone(s.container),
            )
            assertTrue(
                "$tag top >= bottom",
                tone(s.surfaceTop) >= tone(s.surfaceBottom) - 0.5,
            )
            assertTrue(
                "$tag surface chroma ${chroma(s.surfaceTop)}",
                chroma(s.surfaceTop) <= DarkToneSpec.SURFACE_CHROMA + 2.0,
            )
        }
    }

    @Test
    fun toneCapsApplyWheneverThereIsArtwork() {
        for (seed in seeds) {
            val s = scheme(seed)
            val tag = seed.name
            // Gradients: both darker than the highlight, top lighter. True for
            // the neutral fallback too (it uses the theme's dark surfaces).
            assertTrue("$tag top >= bottom", tone(s.surfaceTop) >= tone(s.surfaceBottom) - 0.5)
            assertTrue(
                "$tag top below highlight",
                tone(s.surfaceTop) < tone(s.container) + 0.5,
            )
            // With artwork the mini player takes the spec's near-black tone and
            // the accent its muted tone. Without artwork every surface is the
            // neutral app theme, so those caps deliberately do not apply.
            if (s.seedArgb != null) {
                assertTrue("$tag mini tone ${tone(s.miniContainer)}", tone(s.miniContainer) in 8.0..17.0)
                assertTrue(
                    "$tag mini is darker than highlight",
                    tone(s.miniContainer) < tone(s.container),
                )
                assertTrue("$tag accent tone ${tone(s.primary)}", tone(s.primary) in 65.0..85.0)
                assertTrue(
                    "$tag accent chroma ${chroma(s.primary)}",
                    chroma(s.primary) <= DarkToneSpec.ACCENT_CHROMA + 2.5,
                )
            } else {
                // Neutral fallback: identical whichever way it is reached, and
                // carrying essentially no hue.
                assertTrue("$tag neutral mini", chroma(s.miniContainer) <= 6.0)
                assertTrue("$tag neutral highlight", chroma(s.container) <= 6.0)
            }
        }
    }

    @Test
    fun contrastStillPasses() {
        for (seed in seeds) {
            val s = scheme(seed)
            val tag = seed.name
            val title = Contrast.ratio(s.onContainer, s.container)
            val secondary = Contrast.ratio(s.secondary, s.container)
            val ringOnHighlight = Contrast.ratio(s.primary, s.container)
            val ringVsTrack = Contrast.ratio(s.primary, s.track)
            val ringOnMini = Contrast.ratio(s.primary, s.miniContainer)
            val onPrimary = Contrast.ratio(s.onPrimary, s.primary)
            val surfTop = Contrast.ratio(s.surfaceText, s.surfaceTop)
            val surfBottom = Contrast.ratio(s.surfaceText, s.surfaceBottom)
            val surfSecTop = Contrast.ratio(s.surfaceSecondary, s.surfaceTop)
            val surfSecBottom = Contrast.ratio(s.surfaceSecondary, s.surfaceBottom)
            println(
                "DARK TONE seed=${seed.name} " +
                    "hl(tone=${f(tone(s.container))} chroma=${f(chroma(s.container))}) " +
                    "mini(tone=${f(tone(s.miniContainer))} chroma=${f(chroma(s.miniContainer))}) " +
                    "accent(tone=${f(tone(s.primary))} chroma=${f(chroma(s.primary))}) " +
                    "grad(tone=${f(tone(s.surfaceTop))}->${f(tone(s.surfaceBottom))} " +
                    "chroma=${f(chroma(s.surfaceTop))}) " +
                    "ratios[title=${r(title)} secondary=${r(secondary)} " +
                    "ringHl=${r(ringOnHighlight)} ringTrack=${r(ringVsTrack)} " +
                    "ringMini=${r(ringOnMini)} onPrimary=${r(onPrimary)} " +
                    "surf=${r(surfTop)}/${r(surfBottom)} sec=${r(surfSecTop)}/${r(surfSecBottom)}]",
            )
            assertTrue("$tag title $title", title >= 4.5)
            assertTrue("$tag secondary $secondary", secondary >= 4.5)
            assertTrue("$tag ring on highlight $ringOnHighlight", ringOnHighlight >= 3.0)
            assertTrue("$tag ring vs track $ringVsTrack", ringVsTrack >= 3.0)
            assertTrue("$tag ring on mini $ringOnMini", ringOnMini >= 3.0)
            assertTrue("$tag onPrimary $onPrimary", onPrimary >= 4.5)
            assertTrue("$tag surface top $surfTop", surfTop >= 4.5)
            assertTrue("$tag surface bottom $surfBottom", surfBottom >= 4.5)
            assertTrue("$tag surfaceSecondary top $surfSecTop", surfSecTop >= 4.5)
            assertTrue("$tag surfaceSecondary bottom $surfSecBottom", surfSecBottom >= 4.5)
        }
    }

    @Test
    fun highlySaturatedSeedIsStillMuted() {
        // A max-saturation green must not survive as a vivid tint.
        val vivid = 0xFF00FF00L
        val s = scheme(Seed("vivid-green", vivid))
        assertTrue(chroma(s.container) <= DarkToneSpec.HIGHLIGHT_CHROMA + 2.0)
        assertTrue(chroma(s.container) < Contrast.chromaOf(vivid) / 2.0)
        assertTrue(chroma(s.miniContainer) <= DarkToneSpec.MINI_CHROMA + 2.0)
    }

    @Test
    fun grayAndMissingArtAreNeutral() {
        for (seed in neutral) {
            val s = scheme(seed)
            // Neutral: no artwork tint at all — the scheme is the app theme's
            // own container pair (whose chroma is a hair off-gray by design),
            // and the seed is reported as absent.
            assertTrue("${seed.name} seed is null", s.seedArgb == null)
            assertTrue("${seed.name} container chroma ${chroma(s.container)}", chroma(s.container) <= 6.0)
            assertTrue("${seed.name} mini chroma ${chroma(s.miniContainer)}", chroma(s.miniContainer) <= 6.0)
            assertTrue("${seed.name} surface chroma ${chroma(s.surfaceTop)}", chroma(s.surfaceTop) <= 6.0)
        }
    }

    @Test
    fun darkMiniIsDistinctFromPageBackground() {
        // The page background in Dark is #0F0F0F; the mini pill must be its own
        // shape there (tone differs) and carry a visible hairline.
        val pageBg = 0xFF0F0F0FL
        for (seed in seeds) {
            val s = scheme(seed)
            assertTrue(
                "${seed.name} mini vs page bg",
                Contrast.ratio(s.miniContainer, pageBg) > 1.05,
            )
            assertTrue(
                "${seed.name} outline vs mini",
                Contrast.ratio(s.miniOutline, s.miniContainer) >= 1.2,
            )
        }
    }

    private fun f(d: Double) = "%.1f".format(d)
    private fun r(d: Double) = "%.2f".format(d)
}
