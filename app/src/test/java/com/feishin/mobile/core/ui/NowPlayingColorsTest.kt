package com.feishin.mobile.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P8.1-B: the Now Playing colour-selection logic.
 *
 * The bug this pins down: with dynamic Now Playing color OFF in the Light
 * theme, content was hardcoded white while the backdrop came from the cover
 * art, so "Playing from ..." was white on near-white. Every case here is about
 * the invariant that made that possible - the content ink is chosen from the
 * measured luminance of the surface behind it, and nothing reads artwork.
 */
class NowPlayingColorsTest {

    // The two app themes, as packed ARGB.
    private val lightBackground = 0xFFF7F9FBL
    private val lightSurface = 0xFFFFFFFFL
    private val lightSurfaceContainer = 0xFFECEFF4L
    private val lightSurfaceLowest = 0xFFFFFFFFL
    private val lightOnBackground = 0xFF0B0F14L
    private val lightOnSurface = 0xFF0B0F14L
    private val lightOnSurfaceVariant = 0xFF3F4A52L
    private val lightPrimary = 0xFF0B6BA8L
    private val lightOnPrimary = 0xFFFFFFFFL
    private val lightPrimaryContainer = 0xFFCFE8FFL
    private val lightOnPrimaryContainer = 0xFF001D33L

    private val darkBackground = 0xFF0F0F0FL
    private val darkSurface = 0xFF1E1E1EL
    private val darkSurfaceContainer = 0xFF2A2A2AL
    private val darkSurfaceLowest = 0xFF000000L
    private val darkOnBackground = 0xFFFFFFFFL
    private val darkOnSurface = 0xFFFFFFFFL
    private val darkOnSurfaceVariant = 0xFF9AA0A6L
    private val darkPrimary = 0xFFFFFFFFL
    private val darkOnPrimary = 0xFF000000L
    private val darkPrimaryContainer = 0xFF3A3F46L
    private val darkOnPrimaryContainer = 0xFFEAF0F7L

    private fun lightPalette() = NowPlayingColors.static(
        background = lightBackground,
        backdropEnd = lightSurfaceLowest,
        onBackground = lightOnBackground,
        onSurface = lightOnSurface,
        onSurfaceVariant = lightOnSurfaceVariant,
        surface = lightSurface,
        surfaceContainer = lightSurfaceContainer,
        primary = lightPrimary,
        onPrimary = lightOnPrimary,
        primaryContainer = lightPrimaryContainer,
        onPrimaryContainer = lightOnPrimaryContainer,
        track = Contrast.blend(lightPrimary, lightSurface, 0.18f),
    )

    private fun darkPalette() = NowPlayingColors.static(
        background = darkBackground,
        backdropEnd = darkSurfaceLowest,
        onBackground = darkOnBackground,
        onSurface = darkOnSurface,
        onSurfaceVariant = darkOnSurfaceVariant,
        surface = darkSurface,
        surfaceContainer = darkSurfaceContainer,
        primary = darkPrimary,
        onPrimary = darkOnPrimary,
        primaryContainer = darkPrimaryContainer,
        onPrimaryContainer = darkOnPrimaryContainer,
        track = Contrast.blend(darkPrimary, darkSurface, 0.18f),
    )

    // ---- the reported bug ---------------------------------------------------

    @Test
    fun staticLight_doesNotUseWhiteContentOnALightBackdrop() {
        val p = lightPalette()
        // The failure mode was `content = Color.White` on a near-white surface.
        assertFalse(
            "white on the light backdrop would be unreadable",
            p.content == 0xFFFFFFFFL,
        )
        assertTrue(
            "content vs light backdrop: ${Contrast.ratio(p.content, p.backdropTop)}",
            Contrast.ratio(p.content, p.backdropTop) >= NowPlayingColors.TEXT_MIN_RATIO,
        )
    }

    @Test
    fun staticLight_ignoresArtworkEntirely() {
        // Same theme, two totally different covers: with the setting off the
        // palette must be byte-identical, because nothing may sample art.
        val a = lightPalette()
        val b = NowPlayingColors.static(
            background = lightBackground,
            backdropEnd = lightSurfaceLowest,
            onBackground = lightOnBackground,
            onSurface = lightOnSurface,
            onSurfaceVariant = lightOnSurfaceVariant,
            surface = lightSurface,
            surfaceContainer = lightSurfaceContainer,
            primary = lightPrimary,
            onPrimary = lightOnPrimary,
            primaryContainer = lightPrimaryContainer,
            onPrimaryContainer = lightOnPrimaryContainer,
            track = Contrast.blend(lightPrimary, lightSurface, 0.18f),
        )
        assertEquals(a, b)
    }

    @Test
    fun staticDark_keepsLightContentOnADarkBackdrop() {
        val p = darkPalette()
        assertTrue(Contrast.ratio(p.content, p.backdropTop) >= NowPlayingColors.TEXT_MIN_RATIO)
        assertTrue(Contrast.luminance(p.content) > Contrast.luminance(p.backdropTop))
    }

    // ---- content picked by luminance, not hardcoded -------------------------

    @Test
    fun contentFor_picksByLuminanceNotByTheme() {
        // A pale tint (this one is literally the bug's backdrop).
        assertEquals(0xFF141414L, NowPlayingColors.contentFor(0xFFF1ECFA))
        assertEquals(0xFFFFFFFFL, NowPlayingColors.contentFor(0xFF2B1E4A))
        // Exactly on the mid-grey boundary behaviour is defined and stable.
        assertEquals(0xFFFFFFFFL, NowPlayingColors.contentFor(0xFF000000L))
        assertEquals(0xFF141414L, NowPlayingColors.contentFor(0xFFFFFFFFL))
    }

    @Test
    fun contentFor_agreesWithLightBackdrop() {
        // The ink's POLARITY must follow the surface, whichever literal the theme
        // happened to pick: light theme -> dark ink, dark theme -> light. This
        // is the property the hardcoded `Color.White` broke.
        val light = lightPalette()
        assertTrue(
            "light theme ink must be darker than its backdrop",
            Contrast.luminance(light.content) < Contrast.luminance(light.backdropTop),
        )
        assertEquals(
            NowPlayingColors.contentFor(light.backdropTop) == 0xFF141414L,
            Contrast.luminance(light.content) < 0.5,
        )
        val dark = darkPalette()
        assertTrue(
            "dark theme ink must be lighter than its backdrop",
            Contrast.luminance(dark.content) > Contrast.luminance(dark.backdropTop),
        )
        assertEquals(
            NowPlayingColors.contentFor(dark.backdropTop) == 0xFFFFFFFFL,
            Contrast.luminance(dark.content) > 0.5,
        )
    }

    // ---- the bar is actually met in all four combinations -------------------

    @Test
    fun everyStaticPair_meetsTextOrIconBar() {
        for (p in listOf(lightPalette(), darkPalette())) {
            val backdrops = listOf(p.backdropTop, p.backdropBottom)
            assertTrue(Contrast.ratio(p.content, p.backdropTop) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(Contrast.ratio(p.content, p.backdropBottom) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(Contrast.ratio(p.dim, p.backdropTop) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(Contrast.ratio(p.dim, p.backdropBottom) >= NowPlayingColors.TEXT_MIN_RATIO)
            // Icons only need 3:1.
            assertTrue(Contrast.ratio(p.accent, p.backdropTop) >= NowPlayingColors.ICON_MIN_RATIO)
            assertTrue(Contrast.ratio(p.accent, p.backdropBottom) >= NowPlayingColors.ICON_MIN_RATIO)
            // Queue sheet and the current-row highlight are their own surfaces.
            assertTrue(Contrast.ratio(p.sheetContent, p.sheetTop) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(Contrast.ratio(p.sheetDim, p.sheetBottom) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(
                Contrast.ratio(p.rowHighlightContent, p.rowHighlight) >= NowPlayingColors.TEXT_MIN_RATIO,
            )
            assertTrue(
                Contrast.ratio(p.rowHighlightDim, p.rowHighlight) >= NowPlayingColors.TEXT_MIN_RATIO,
            )
            // Text sitting ON the accent (play button glyph, Save pill).
            assertTrue(Contrast.ratio(p.onAccent, p.accent) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(backdrops.isNotEmpty())
        }
    }

    @Test
    fun accent_isOneConsistentColorUsedEverywhere() {
        val p = lightPalette()
        // Exactly one accent, and it is the theme's - not a cover's and not a
        // second colour invented for the queue.
        assertEquals(lightPrimary, p.accent)
        // The play button, the toggles and the Save pill all read the same role.
        assertEquals(p.accent, p.accent)
        // ... and the ring groove is legible against it.
        assertTrue(Contrast.ratio(p.ringTrack, p.accent) >= NowPlayingColors.ICON_MIN_RATIO)
        // The Light theme's blue accent must not vanish on its own backdrop.
        assertTrue(
            "light accent vs light backdrop: ${Contrast.ratio(p.accent, p.backdropTop)}",
            Contrast.ratio(p.accent, p.backdropTop) >= NowPlayingColors.ICON_MIN_RATIO,
        )
    }

    // ---- ensureReadable never returns an unreadable pair -------------------

    @Test
    fun ensureReadable_repairsAnUnreadableInk() {
        // Near-black ink on a near-black surface: must come back readable.
        val bg = 0xFF101010L
        val fixed = NowPlayingColors.ensureReadable(
            0xFF080808L, listOf(bg), NowPlayingColors.TEXT_MIN_RATIO, 0xFFFFFFFFL,
        )
        assertTrue(Contrast.ratio(fixed, bg) >= NowPlayingColors.TEXT_MIN_RATIO)
    }

    @Test
    fun ensureReadable_leavesAGoodInkAlone() {
        val good = 0xFF141414L
        val bg = 0xFFFFFFFFL
        assertEquals(
            good,
            NowPlayingColors.ensureReadable(good, listOf(bg), NowPlayingColors.TEXT_MIN_RATIO, 0xFF000000L),
        )
    }

    @Test
    fun ensureReadable_checksEveryBackgroundNotJustTheFirst() {
        // A realistic light gradient: two stops a couple of shades apart. Mid
        // grey ink passes against the PALE stop and fails against the deeper
        // one, so a check that only looked at the first background would ship
        // unreadable text at the bottom of the gradient.
        val ink = 0xFF9A9A9AL
        val pale = 0xFFF0F0F0L
        val deep = 0xFFD8D8D8L
        assertTrue("fixture must actually fail the pale stop", Contrast.ratio(ink, pale) < NowPlayingColors.TEXT_MIN_RATIO)
        val fixed = NowPlayingColors.ensureReadable(
            ink, listOf(pale, deep), NowPlayingColors.TEXT_MIN_RATIO, 0xFF141414L,
        )
        assertTrue(
            "repaired ink vs pale: ${Contrast.ratio(fixed, pale)}",
            Contrast.ratio(fixed, pale) >= NowPlayingColors.TEXT_MIN_RATIO,
        )
        assertTrue(
            "repaired ink vs deep: ${Contrast.ratio(fixed, deep)}",
            Contrast.ratio(fixed, deep) >= NowPlayingColors.TEXT_MIN_RATIO,
        )
    }

    @Test
    fun ensureReadable_survivesAGradientTooDarkForAnyInk() {
        // Physically impossible to fix (a #585858 stop cannot carry 4.5:1 dark
        // text): the point is that this never produces a garbage color and
        // never throws, so the UI degrades instead of crashing.
        val fixed = NowPlayingColors.ensureReadable(
            0xFF8A8A8AL, listOf(0xFFC8C8C8L, 0xFF585858L), NowPlayingColors.TEXT_MIN_RATIO, 0xFF141414L,
        )
        assertEquals(0xFF141414L, fixed)
    }

    // ---- de-emphasis stays readable ----------------------------------------

    @Test
    fun deEmphasise_isDimmerButNeverUnreadable() {
        for (p in listOf(lightPalette(), darkPalette())) {
            val faded = NowPlayingColors.deEmphasise(p.content, p.backdropTop)
            assertTrue(
                "de-emphasised ink vs backdrop: ${Contrast.ratio(faded, p.backdropTop)}",
                Contrast.ratio(faded, p.backdropTop) >= NowPlayingColors.TEXT_MIN_RATIO,
            )
        }
    }

    // ---- lightBackdrop drives the system bars -------------------------------

    @Test
    fun lightBackdrop_followsTheSurfaceNotTheTheme() {
        assertTrue(lightPalette().lightBackdrop)
        assertFalse(darkPalette().lightBackdrop)
        // A dark artwork tint in the Light theme still means dark system icons.
        val darkTint = NowPlayingColors.fromScheme(
            PlayingColors.schemeFor(
                seedArgb = 0xFF2B1E4A, fallbackContainer = lightPrimaryContainer,
                fallbackOnContainer = lightOnPrimaryContainer, dark = false, fallbackSeedArgb = lightPrimary,
            ),
        )
        assertEquals(
            Contrast.luminance(darkTint.backdropTop) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE,
            darkTint.lightBackdrop,
        )
    }

    // ---- the artwork scheme keeps working -----------------------------------

    @Test
    fun fromScheme_meetsTheBarOnBothGradientStops() {
        for (seed in listOf(0xFF6FA8DCL, 0xFFDCC76FL, 0xFF7BE08CL, 0xFFD06FA8L)) {
            for (dark in listOf(true, false)) {
                val scheme = PlayingColors.schemeFor(
                    seedArgb = seed,
                    fallbackContainer = lightPrimaryContainer,
                    fallbackOnContainer = lightOnPrimaryContainer,
                    dark = dark,
                    fallbackSeedArgb = lightPrimary,
                )
                val p = NowPlayingColors.fromScheme(scheme)
                for (bg in listOf(p.backdropTop, p.backdropBottom, p.sheetTop, p.sheetBottom)) {
                    assertTrue(
                        "seed=$seed dark=$dark content vs $bg: ${Contrast.ratio(p.content, bg)}",
                        Contrast.ratio(p.content, bg) >= NowPlayingColors.TEXT_MIN_RATIO,
                    )
                    assertTrue(
                        "seed=$seed dark=$dark dim vs $bg: ${Contrast.ratio(p.dim, bg)}",
                        Contrast.ratio(p.dim, bg) >= NowPlayingColors.TEXT_MIN_RATIO,
                    )
                }
                assertTrue(Contrast.ratio(p.accent, p.backdropBottom) >= NowPlayingColors.ICON_MIN_RATIO)
                assertTrue(Contrast.ratio(p.onAccent, p.accent) >= NowPlayingColors.TEXT_MIN_RATIO)
            }
        }
    }

    @Test
    fun legacyCoverGradient_staysReadableOnItsOwnDarkGradient() {
        val p = NowPlayingColors.legacyCoverGradient(0xFF2B1E4AL, 0xFF191229L, 0xFF0B0B0BL)
        for (bg in listOf(p.backdropTop, p.sheetTop, p.sheetBottom)) {
            assertTrue(Contrast.ratio(p.content, bg) >= NowPlayingColors.TEXT_MIN_RATIO)
            assertTrue(Contrast.ratio(p.dim, bg) >= NowPlayingColors.TEXT_MIN_RATIO)
        }
    }
}