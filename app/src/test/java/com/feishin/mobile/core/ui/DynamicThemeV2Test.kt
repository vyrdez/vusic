package com.feishin.mobile.core.ui

import androidx.compose.ui.graphics.Color
import com.feishin.mobile.ui.theme.toMaterialColorScheme
import com.feishin.mobile.ui.theme.toVusicColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure-JVM unit tests for Dynamic Theme v2 (dominant-cluster artwork extractor,
 * CIELAB tonal mapping in Dark & Light modes, contrast guards, and static fallback).
 */
class DynamicThemeV2Test {

    private fun hueDistanceDeg(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return min(diff, 360.0 - diff)
    }

    // ---- 1. Dominant-color selection across all 12 ArtworkFixtures.ALL -------

    @Test
    fun fixtures_whiteAndWhiteBlueLogo_selectNeutralWhiteNeverBlue() {
        for (name in listOf(ArtworkFixtures.NAME_WHITE, ArtworkFixtures.NAME_WHITE_BLUE_LOGO)) {
            val pixels = ArtworkFixtures.ALL.getValue(name)
            val seed = ArtworkColorExtractor.extractSourceColor(pixels)
            assertNotNull("$name must yield a non-null seed", seed)
            seed!!
            val chroma = Contrast.chromaOf(seed)
            val tone = Contrast.toneOf(seed)
            assertTrue("$name seed chroma must be neutral (< 4), got $chroma", chroma < 4.0)
            assertTrue("$name seed tone must be white (> 95), got $tone", tone > 95.0)

            // Regression: white + 3% blue logo must keep Dark & Light schemes neutral, never blue.
            for (dark in listOf(true, false)) {
                val scheme = PlayingColors.buildMaterialScheme(seed, dark = dark)
                assertTrue(
                    "$name dark=$dark primary chroma must be neutral (< 4), got ${Contrast.chromaOf(scheme.primary)}",
                    Contrast.chromaOf(scheme.primary) < 4.0,
                )
                assertTrue(
                    "$name dark=$dark miniContainer chroma must be neutral (< 4), got ${Contrast.chromaOf(scheme.miniContainer)}",
                    Contrast.chromaOf(scheme.miniContainer) < 4.0,
                )
            }
        }
    }

    @Test
    fun fixtures_nearWhitePastel_selectsHighLightnessPastel() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_NEAR_WHITE_PASTEL),
        )
        assertNotNull(seed)
        seed!!
        val tone = Contrast.toneOf(seed)
        val chroma = Contrast.chromaOf(seed)
        assertTrue("near_white_pastel L* must be > 85, got $tone", tone > 85.0)
        assertTrue("near_white_pastel C* must stay soft (< 12), got $chroma", chroma < 12.0)
    }

    @Test
    fun fixtures_gray_selectsMidGrayNeutral() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_GRAY),
        )
        assertNotNull(seed)
        seed!!
        val chroma = Contrast.chromaOf(seed)
        val tone = Contrast.toneOf(seed)
        assertTrue("gray C* must be < 4, got $chroma", chroma < 4.0)
        assertTrue("gray L* must be in 35..65, got $tone", tone in 35.0..65.0)
    }

    @Test
    fun fixtures_black_selectsNearBlackNeutral() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_BLACK),
        )
        assertNotNull(seed)
        seed!!
        val chroma = Contrast.chromaOf(seed)
        val tone = Contrast.toneOf(seed)
        assertTrue("black C* must be < 4, got $chroma", chroma < 4.0)
        assertTrue("black L* must be < 15, got $tone", tone < 15.0)
    }

    @Test
    fun fixtures_red_selectsRedHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_RED),
        )!!
        val expected = ArtworkFixtures.COLOR_RED.toLong() and 0xFFFFFFFFL
        assertTrue("red chroma > 40, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 40.0)
        assertTrue(
            "red hue mismatch: ${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
        assertTrue(Contrast.red(seed) > Contrast.green(seed) && Contrast.red(seed) > Contrast.blue(seed))
    }

    @Test
    fun fixtures_blue_selectsBlueHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_BLUE),
        )!!
        val expected = ArtworkFixtures.COLOR_BLUE.toLong() and 0xFFFFFFFFL
        assertTrue("blue chroma > 40, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 40.0)
        assertTrue(
            "blue hue mismatch: ${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
        assertTrue(Contrast.blue(seed) > Contrast.red(seed) && Contrast.blue(seed) > Contrast.green(seed))
    }

    @Test
    fun fixtures_purple_selectsPurpleHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_PURPLE),
        )!!
        val expected = ArtworkFixtures.COLOR_PURPLE.toLong() and 0xFFFFFFFFL
        assertTrue("purple chroma > 40, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 40.0)
        assertTrue(
            "purple hue mismatch: ${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
    }

    @Test
    fun fixtures_brown_selectsBrownHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_BROWN),
        )!!
        val expected = ArtworkFixtures.COLOR_BROWN.toLong() and 0xFFFFFFFFL
        assertTrue("brown chroma > 18, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 18.0)
        assertTrue(
            "brown hue mismatch: ${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
    }

    @Test
    fun fixtures_yellow_selectsYellowHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_YELLOW),
        )!!
        val expected = ArtworkFixtures.COLOR_YELLOW.toLong() and 0xFFFFFFFFL
        assertTrue("yellow chroma > 40, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 40.0)
        assertTrue(
            "yellow hue mismatch: ${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
    }

    @Test
    fun fixtures_multicolor_selectsLargestRegionGreenHue() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_MULTICOLOR),
        )!!
        val expected = ArtworkFixtures.COLOR_MULTI_GREEN.toLong() and 0xFFFFFFFFL
        assertTrue("multicolor chroma > 40, got ${Contrast.chromaOf(seed)}", Contrast.chromaOf(seed) > 40.0)
        assertTrue(
            "multicolor must pick green (~55%), got hue=${Contrast.labHue(seed)} vs ${Contrast.labHue(expected)}",
            hueDistanceDeg(Contrast.labHue(seed), Contrast.labHue(expected)) < 5.0,
        )
        assertTrue(Contrast.green(seed) > Contrast.red(seed) && Contrast.green(seed) > Contrast.blue(seed))
    }

    @Test
    fun fixtures_transparentEdge_ignoresTransparentBorderAndSelectsPurpleCenter() {
        val seed = ArtworkColorExtractor.extractSourceColor(
            ArtworkFixtures.ALL.getValue(ArtworkFixtures.NAME_TRANSPARENT_EDGE),
        )!!
        val expected = ArtworkFixtures.COLOR_CENTER_PURPLE.toLong() and 0xFFFFFFFFL
        assertEquals(expected, seed)
    }

    @Test
    fun allTransparentOrEmpty_returnsNullAndZero() {
        val allTransparent = IntArray(64 * 64) { ArtworkFixtures.COLOR_TRANSPARENT }
        assertNull(ArtworkColorExtractor.extractSourceColor(allTransparent, 64, 64))
        assertEquals(0, ArtworkColorExtractor.extractMostUsedColor(allTransparent, 64, 64))
        assertNull(PlayingColors.schemeFromPixels(allTransparent, 64, 64, dark = true))
        assertNull(ArtworkColorExtractor.extractSourceColor(IntArray(0), 0, 0))
    }

    // ---- 2. Near-color perceptual merge ------------------------------------

    @Test
    fun nearColorMerge_twoCloseShades30EachBeatSolid40() {
        val shadeA = 0xFF2058B0.toInt() // 30%
        val shadeB = 0xFF2860B8.toInt() // 30%, within Delta E <= 12 of shadeA
        val solidC = 0xFFD03030.toInt() // 40%, distinct red hue

        val (la, aa, ba) = Contrast.toLab(shadeA.toLong() and 0xFFFFFFFFL)
        val (lb, ab, bb) = Contrast.toLab(shadeB.toLong() and 0xFFFFFFFFL)
        val deltaE = Contrast.deltaE(la, aa, ba, lb, ab, bb)
        assertTrue("test shades must be within Delta E <= 12.0, got $deltaE", deltaE <= 12.0)

        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total) { idx ->
            when {
                idx < 3000 -> shadeA
                idx < 6000 -> shadeB
                else -> solidC
            }
        }

        val winner = ArtworkColorExtractor.extractSourceColor(pixels, width, height)!!
        // Combined 60% blue cluster beats the 40% red cluster.
        assertTrue(
            "expected merged blue cluster to win over red, got 0x${winner.toString(16)}",
            Contrast.blue(winner) > Contrast.red(winner),
        )
    }

    @Test
    fun distantColors_doNotMergeAcrossThreshold() {
        val distinctA = 0xFF195AE6.toInt() // 30% blue
        val distinctB = 0xFF22A84A.toInt() // 30% green (Delta E >> 12 from blue)
        val solidC = 0xFFE61919.toInt() // 40% red

        val (la, aa, ba) = Contrast.toLab(distinctA.toLong() and 0xFFFFFFFFL)
        val (lb, ab, bb) = Contrast.toLab(distinctB.toLong() and 0xFFFFFFFFL)
        assertTrue(Contrast.deltaE(la, aa, ba, lb, ab, bb) > ArtworkColorExtractor.CLUSTER_MERGE_DELTA_E)

        val pixels = IntArray(100 * 100) { idx ->
            when {
                idx < 3000 -> distinctA
                idx < 6000 -> distinctB
                else -> solidC
            }
        }

        val winner = ArtworkColorExtractor.extractSourceColor(pixels, 100, 100)!!
        assertEquals(solidC.toLong() and 0xFFFFFFFFL, winner)
    }

    // ---- 3. Determinism ----------------------------------------------------

    @Test
    fun determinism_repeatedCallsAndTieBreakAreIdentical() {
        assertEquals(12, ArtworkFixtures.ALL.size)
        for ((name, artwork) in ArtworkFixtures.ALL) {
            val first = ArtworkColorExtractor.extractMostUsedColor(artwork.argb, artwork.width, artwork.height)
            repeat(5) {
                val again = ArtworkColorExtractor.extractMostUsedColor(artwork.argb, artwork.width, artwork.height)
                assertEquals("$name must be deterministic across repeated calls", first, again)
            }
        }

        // 50/50 split tie-break image: half red, half blue; independent of pixel ordering.
        val size = 64
        val half = (size * size) / 2
        val redFirst = IntArray(size * size) { i ->
            if (i < half) ArtworkFixtures.COLOR_RED else ArtworkFixtures.COLOR_BLUE
        }
        val blueFirst = redFirst.reversedArray()
        val checkerboard = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if ((x + y) % 2 == 0) ArtworkFixtures.COLOR_RED else ArtworkFixtures.COLOR_BLUE
        }

        val expected = ArtworkColorExtractor.extractMostUsedColor(redFirst, size, size)
        assertTrue("tie-break result must be non-zero", expected != 0)
        repeat(5) {
            assertEquals(expected, ArtworkColorExtractor.extractMostUsedColor(redFirst, size, size))
            assertEquals(expected, ArtworkColorExtractor.extractMostUsedColor(blueFirst, size, size))
            assertEquals(expected, ArtworkColorExtractor.extractMostUsedColor(checkerboard, size, size))
        }
    }

    @Test
    fun subsampling_largeWhiteWithBlueLogoStillSelectsNeutralWhite() {
        val large = ArtworkFixtures.whiteBlueLogo(size = 256)
        val seed = ArtworkColorExtractor.extractSourceColor(large)!!
        assertTrue(Contrast.chromaOf(seed) < 4.0)
        assertTrue(Contrast.toneOf(seed) > 95.0)
    }

    // ---- 4. Tone & luminance bounds across all 12 fixtures -----------------

    @Test
    fun darkTheme_surfacesStayDarkAcrossAllTwelveFixtures() {
        for ((name, artwork) in ArtworkFixtures.ALL) {
            val scheme = PlayingColors.schemeFromPixels(artwork.argb, artwork.width, artwork.height, dark = true)!!
            val bgTone = Contrast.toneOf(scheme.background)
            val surfTone = Contrast.toneOf(scheme.surface)
            val contTone = Contrast.toneOf(scheme.surfaceContainer)

            assertTrue("$name dark background L* out of bounds: $bgTone", bgTone in 2.0..12.0)
            assertTrue("$name dark surface L* out of bounds: $surfTone", surfTone in 7.0..16.0)
            assertTrue("$name dark surfaceContainer L* out of bounds: $contTone", contTone in 7.0..16.0)
            assertTrue(
                "$name dark background luminance too bright: ${Contrast.luminance(scheme.background)}",
                Contrast.luminance(scheme.background) < 0.05,
            )
            assertTrue(
                "$name dark surface luminance too bright: ${Contrast.luminance(scheme.surface)}",
                Contrast.luminance(scheme.surface) < 0.05,
            )
            assertTrue(
                "$name dark surfaceContainer luminance too bright: ${Contrast.luminance(scheme.surfaceContainer)}",
                Contrast.luminance(scheme.surfaceContainer) < 0.05,
            )
        }
    }

    @Test
    fun lightTheme_surfacesStayLightAcrossAllTwelveFixturesEvenWithBlackCover() {
        val neutralNames = setOf(
            ArtworkFixtures.NAME_WHITE,
            ArtworkFixtures.NAME_NEAR_WHITE_PASTEL,
            ArtworkFixtures.NAME_WHITE_BLUE_LOGO,
            ArtworkFixtures.NAME_GRAY,
            ArtworkFixtures.NAME_BLACK,
        )
        for (tint in listOf(PlayingColors.DefaultLightTintStrength, 1.0f)) {
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val scheme = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = false,
                    lightTintStrength = tint,
                )!!
                val bgTone = Contrast.toneOf(scheme.background)
                val surfTone = Contrast.toneOf(scheme.surface)
                val contTone = Contrast.toneOf(scheme.surfaceContainer)
                val highTone = Contrast.toneOf(scheme.surfaceContainerHigh)
                assertTrue("$name (tint=$tint) light background L* must be in [93.5, 95.5], got $bgTone", bgTone in 93.5..95.5)
                assertTrue("$name (tint=$tint) light surface L* must be in [93.5, 95.5], got $surfTone", surfTone in 93.5..95.5)
                assertTrue("$name (tint=$tint) light surfaceContainer L* must be in [89.5, 92.5], got $contTone", contTone in 89.5..92.5)
                assertTrue("$name (tint=$tint) light surfaceContainerHigh L* must be in [89.5, 92.5], got $highTone", highTone in 89.5..92.5)
                assertTrue("$name (tint=$tint) light surfaceContainer ($contTone) must be < background ($bgTone)", contTone < bgTone)
                assertTrue(
                    "$name (tint=$tint) light background luminance must stay light",
                    Contrast.luminance(scheme.background) > 0.70,
                )
                assertTrue(
                    "$name (tint=$tint) light surface luminance must stay light",
                    Contrast.luminance(scheme.surface) > 0.75,
                )
                if (name in neutralNames) {
                    assertTrue(
                        "$name neutral background C* must be < 1.5, got ${Contrast.chromaOf(scheme.background)}",
                        Contrast.chromaOf(scheme.background) < 1.5,
                    )
                } else {
                    assertTrue(
                        "$name colorful surfaceContainer C* must be >= 12.0, got ${Contrast.chromaOf(scheme.surfaceContainer)}",
                        Contrast.chromaOf(scheme.surfaceContainer) >= 12.0,
                    )
                }
            }
        }
    }

    // ---- 5. Contrast guards across all 12 fixtures in BOTH Dark & Light ----

    @Test
    fun contrastGuards_allTextAndIconPairsMeetWcagAcrossAllFixturesDarkAndLight() {
        for (dark in listOf(true, false)) {
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val s = PlayingColors.schemeFromPixels(artwork.argb, artwork.width, artwork.height, dark = dark)!!
                val tag = "$name(dark=$dark)"

                // Text pairs >= 4.5:1
                assertRatio("$tag onSurface/surface", s.onSurface, s.surface, 4.5)
                assertRatio("$tag onSurface/background", s.onSurface, s.background, 4.5)
                assertRatio("$tag onBackground/background", s.onBackground, s.background, 4.5)
                assertRatio("$tag onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 4.5)
                assertRatio("$tag onSurfaceVariant/background", s.onSurfaceVariant, s.background, 4.5)
                assertRatio("$tag onPrimary/primary", s.onPrimary, s.primary, 4.5)
                assertRatio("$tag onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
                assertRatio("$tag onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, 4.5)
                // MiniPlayer title (miniInk) & artist (miniSubtleInk) use scheme.primary on miniContainer
                assertRatio("$tag miniInk/miniContainer", s.primary, s.miniContainer, 4.5)
                assertRatio("$tag miniSubtleInk/miniContainer", s.primary, s.miniContainer, 4.5)
                // Controls & Now Playing gradients
                assertRatio("$tag chipOffText/chipOffFill", s.chipOffText, s.chipOffFill, 4.5)
                assertRatio("$tag surfaceText/surfaceTop", s.surfaceText, s.surfaceTop, 4.5)
                assertRatio("$tag surfaceText/surfaceBottom", s.surfaceText, s.surfaceBottom, 4.5)
                assertRatio("$tag surfaceSecondary/surfaceTop", s.surfaceSecondary, s.surfaceTop, 4.5)
                assertRatio("$tag surfaceSecondary/surfaceBottom", s.surfaceSecondary, s.surfaceBottom, 4.5)
                assertRatio("$tag rowHighlightSecondary/primaryContainer", s.rowHighlightSecondary, s.primaryContainer, 4.5)

                // Icon / control pairs >= 3.0:1
                assertRatio("$tag primary/surface", s.primary, s.surface, 3.0)
                assertRatio("$tag primary/background", s.primary, s.background, 3.0)
                assertRatio("$tag primary/miniContainer", s.primary, s.miniContainer, 3.0)
                assertRatio("$tag ringTrack/primary", s.ringTrack, s.primary, 3.0)
                assertRatio("$tag switchOffOutline/surface", s.switchOffOutline, s.surface, 3.0)
                assertRatio("$tag switchOffThumb/switchOffTrack", s.switchOffThumb, s.switchOffTrack, 3.0)

                // End-to-end NowPlayingColors adapter also clears WCAG
                val np = NowPlayingColors.fromDynamic(s)
                assertRatio("$tag np.content/backdropTop", np.content, np.backdropTop, 4.5)
                assertRatio("$tag np.content/backdropBottom", np.content, np.backdropBottom, 4.5)
                assertRatio("$tag np.dim/backdropTop", np.dim, np.backdropTop, 4.5)
                assertRatio("$tag np.dim/backdropBottom", np.dim, np.backdropBottom, 4.5)
                assertRatio("$tag np.onAccent/accent", np.onAccent, np.accent, 4.5)
                assertRatio("$tag np.accent/backdropTop", np.accent, np.backdropTop, 3.0)
                assertRatio("$tag np.ringTrack/accent", np.ringTrack, np.accent, 3.0)
            }
        }
    }

    private fun assertRatio(label: String, fg: Long, bg: Long, minRatio: Double) {
        val actual = Contrast.ratio(fg, bg)
        assertTrue(
            "$label contrast ratio $actual < $minRatio (fg=0x${fg.toString(16)} bg=0x${bg.toString(16)})",
            actual >= minRatio,
        )
    }

    // ---- 6. Nothing-playing / Dynamic OFF static default -------------------

    @Test
    fun staticDefault_nullSeedMatchesExactStaticDarkAndLightPalettesAndTokens() {
        val darkDyn = PlayingColors.buildMaterialScheme(seedArgb = null, dark = true)
        assertEquals(PlayingColors.staticMaterialScheme(dark = true), darkDyn)
        assertNull(darkDyn.seedArgb)
        assertTrue(darkDyn.dark)

        val darkMaterial = darkDyn.toMaterialColorScheme()
        assertEquals(Color.White, darkMaterial.primary)
        assertEquals(Color.Black, darkMaterial.onPrimary)
        assertEquals(Color(0xFF3A3F46), darkMaterial.primaryContainer)
        assertEquals(Color(0xFFEAF0F7), darkMaterial.onPrimaryContainer)
        assertEquals(Color(0xFF9AA0A6), darkMaterial.secondary)
        assertEquals(Color.White, darkMaterial.onSecondary)
        assertEquals(Color(0xFF0F0F0F), darkMaterial.background)
        assertEquals(Color.White, darkMaterial.onBackground)
        assertEquals(Color(0xFF1E1E1E), darkMaterial.surface)
        assertEquals(Color.White, darkMaterial.onSurface)
        assertEquals(Color(0xFF2A2A2A), darkMaterial.surfaceVariant)
        assertEquals(Color(0xFF9AA0A6), darkMaterial.onSurfaceVariant)
        assertEquals(Color.Black, darkMaterial.surfaceContainerLowest)
        assertEquals(Color(0xFF0F0F0F), darkMaterial.surfaceContainerLow)
        assertEquals(Color(0xFF1E1E1E), darkMaterial.surfaceContainer)
        assertEquals(Color(0xFF2A2A2A), darkMaterial.surfaceContainerHigh)
        assertEquals(Color(0xFF3A3A3A), darkMaterial.outline)
        assertEquals(Color(0xFF2A2A2A), darkMaterial.outlineVariant)

        val darkTokens = darkDyn.toVusicColorTokens()
        assertEquals(Color(0xFF0F0F0F), darkTokens.background)
        assertEquals(Color(0xFF1E1E1E), darkTokens.surface)
        assertEquals(Color(0xFF2A2A2A), darkTokens.surfaceHigh)
        assertEquals(Color(0xFF9AA0A6), darkTokens.secondaryText)
        assertEquals(Color.White, darkTokens.accent)
        assertEquals(Color.Black, darkTokens.onAccent)
        assertEquals(Color(0xFF3A3A3A), darkTokens.switchOffTrack)
        assertEquals(Color(0xFF8A8A8A), darkTokens.switchOffOutline)
        assertEquals(Color(0xFFB8B8B8), darkTokens.switchOffThumb)
        assertEquals(Color(0xFF232323), darkTokens.disabledTrack)
        assertEquals(Color(0xFF4A4A4A), darkTokens.disabledOutline)
        assertEquals(Color(0xFF5A5A5A), darkTokens.disabledThumb)
        assertEquals(Color(0xFF6E6E6E), darkTokens.sliderInactiveTrack)
        assertEquals(Color(0xFF2A2A2A), darkTokens.chipOffFill)
        assertEquals(Color(0xFFEDEDED), darkTokens.chipOffText)

        val lightDyn = PlayingColors.buildMaterialScheme(seedArgb = null, dark = false)
        assertEquals(PlayingColors.staticMaterialScheme(dark = false), lightDyn)
        assertNull(lightDyn.seedArgb)
        assertTrue(!lightDyn.dark)

        val lightMaterial = lightDyn.toMaterialColorScheme()
        assertEquals(Color(0xFF0B6BA8), lightMaterial.primary)
        assertEquals(Color.White, lightMaterial.onPrimary)
        assertEquals(Color(0xFFCFE8FF), lightMaterial.primaryContainer)
        assertEquals(Color(0xFF001D33), lightMaterial.onPrimaryContainer)
        assertEquals(Color(0xFF48626F), lightMaterial.secondary)
        assertEquals(Color.White, lightMaterial.onSecondary)
        assertEquals(Color(0xFFCFE8FF), lightMaterial.secondaryContainer)
        assertEquals(Color(0xFF001D33), lightMaterial.onSecondaryContainer)
        assertEquals(Color(0xFFF7F9FB), lightMaterial.background)
        assertEquals(Color(0xFF0B0F14), lightMaterial.onBackground)
        assertEquals(Color(0xFFFFFFFF), lightMaterial.surface)
        assertEquals(Color(0xFF0B0F14), lightMaterial.onSurface)
        assertEquals(Color(0xFFE7EDF3), lightMaterial.surfaceVariant)
        assertEquals(Color(0xFF3F4A52), lightMaterial.onSurfaceVariant)
        assertEquals(Color(0xFFFFFFFF), lightMaterial.surfaceContainerLowest)
        assertEquals(Color(0xFFF2F5F9), lightMaterial.surfaceContainerLow)
        assertEquals(Color(0xFFECEFF4), lightMaterial.surfaceContainer)
        assertEquals(Color(0xFFE4E8EE), lightMaterial.surfaceContainerHigh)
        assertEquals(Color(0xFF6E7A85), lightMaterial.outline)
        assertEquals(Color(0xFFC3CCD5), lightMaterial.outlineVariant)
        assertEquals(Color(0xFFB3261E), lightMaterial.error)
        assertEquals(Color.White, lightMaterial.onError)

        val lightTokens = lightDyn.toVusicColorTokens()
        assertEquals(Color(0xFFF7F9FB), lightTokens.background)
        assertEquals(Color(0xFFFFFFFF), lightTokens.surface)
        assertEquals(Color(0xFFE4E8EE), lightTokens.surfaceHigh)
        assertEquals(Color(0xFF3F4A52), lightTokens.secondaryText)
        assertEquals(Color(0xFF0B6BA8), lightTokens.accent)
        assertEquals(Color.White, lightTokens.onAccent)
        assertEquals(Color(0xFFD3D6DA), lightTokens.switchOffTrack)
        assertEquals(Color(0xFF5F6368), lightTokens.switchOffOutline)
        assertEquals(Color(0xFF5F6368), lightTokens.switchOffThumb)
        assertEquals(Color(0xFFE8EAED), lightTokens.disabledTrack)
        assertEquals(Color(0xFFBDC1C6), lightTokens.disabledOutline)
        assertEquals(Color(0xFFBDC1C6), lightTokens.disabledThumb)
        assertEquals(Color(0xFF8A9096), lightTokens.sliderInactiveTrack)
        assertEquals(Color(0xFFE7EDF3), lightTokens.chipOffFill)
        assertEquals(Color(0xFF10141A), lightTokens.chipOffText)
    }
}
