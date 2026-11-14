package com.feishin.mobile.core.ui

import com.feishin.mobile.ui.theme.toMaterialColorScheme
import com.feishin.mobile.ui.theme.toVusicColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11.1 Section B unit tests:
 * Verifies calm, clearly visible song-tinted Light theme surfaces and containers
 * driven by [LightTintSpec] across all 12 [ArtworkFixtures.ALL], chroma scaling,
 * neutral handling, WCAG contrast, dark-mode isolation, and static fallback.
 */
class LightThemeTintTest {

    private val colorfulFixtures = listOf(
        ArtworkFixtures.NAME_RED,
        ArtworkFixtures.NAME_BLUE,
        ArtworkFixtures.NAME_PURPLE,
        ArtworkFixtures.NAME_BROWN,
        ArtworkFixtures.NAME_YELLOW,
        ArtworkFixtures.NAME_MULTICOLOR,
        ArtworkFixtures.NAME_TRANSPARENT_EDGE,
    )

    private val neutralFixtures = listOf(
        ArtworkFixtures.NAME_WHITE,
        ArtworkFixtures.NAME_NEAR_WHITE_PASTEL,
        ArtworkFixtures.NAME_WHITE_BLUE_LOGO,
        ArtworkFixtures.NAME_GRAY,
        ArtworkFixtures.NAME_BLACK,
    )

    private fun lightSchemeFor(name: String, tint: Float = 1.0f): VusicDynamicScheme {
        val art = ArtworkFixtures.ALL.getValue(name)
        return PlayingColors.schemeFromPixels(
            pixels = art.argb,
            width = art.width,
            height = art.height,
            dark = false,
            lightTintStrength = tint,
        )!!
    }

    @Test
    fun defaultLightTintStrength_isEnabledAtFull() {
        assertEquals(1.0f, PlayingColors.DefaultLightTintStrength, 0.0001f)
    }

    @Test
    fun lightTheme_backgroundAndSurfaceLightnessStayInTintedLightBand() {
        assertEquals(12, ArtworkFixtures.ALL.size)
        for (name in ArtworkFixtures.ALL.keys) {
            val s = lightSchemeFor(name)
            val bgTone = Contrast.toneOf(s.background)
            val surfTone = Contrast.toneOf(s.surface)
            assertTrue("$name background L*=$bgTone must be in [93.5, 95.5]", bgTone in 93.5..95.5)
            assertTrue("$name surface L*=$surfTone must be in [93.5, 95.5]", surfTone in 93.5..95.5)
        }
    }

    @Test
    fun lightTheme_containersStepStrictlyDarkerThanBackground() {
        for (name in ArtworkFixtures.ALL.keys) {
            val s = lightSchemeFor(name)
            val lLowest = Contrast.toneOf(s.surfaceContainerLowest)
            val lBackground = Contrast.toneOf(s.background)
            val lSurface = Contrast.toneOf(s.surface)
            val lLow = Contrast.toneOf(s.surfaceContainerLow)
            val lCont = Contrast.toneOf(s.surfaceContainer)
            val lHigh = Contrast.toneOf(s.surfaceContainerHigh)

            assertTrue("$name surfaceContainerLowest ($lLowest) must be in [95.5, 97.5]", lLowest in 95.5..97.5)
            assertTrue("$name surfaceContainerLowest ($lLowest) must be > surface ($lSurface)", lLowest > lSurface)
            assertTrue("$name surface ($lSurface) must be > surfaceContainerLow ($lLow)", lSurface > lLow)
            assertTrue("$name background ($lBackground) must be > surfaceContainerLow ($lLow)", lBackground > lLow)
            assertTrue("$name surfaceContainerLow ($lLow) must be > surfaceContainer ($lCont)", lLow > lCont)
            assertTrue("$name surfaceContainer ($lCont) must be > surfaceContainerHigh ($lHigh)", lCont > lHigh)
            assertTrue("$name surfaceContainerLow ($lLow) must be in [89.5, 92.5]", lLow in 89.5..92.5)
            assertTrue("$name surfaceContainer ($lCont) must be in [89.5, 92.5]", lCont in 89.5..92.5)
            assertTrue("$name surfaceContainerHigh ($lHigh) must be in [89.5, 92.5]", lHigh in 89.5..92.5)
            assertEquals("$name miniContainer must match surfaceContainer", s.surfaceContainer, s.miniContainer)
            assertEquals("$name surfaceVariant must match surfaceContainerHigh", s.surfaceContainerHigh, s.surfaceVariant)
        }
    }

    @Test
    fun lightTheme_colorfulFixturesHaveVisibleTintChroma_neutralFixturesStayNeutral() {
        for (name in colorfulFixtures) {
            val s = lightSchemeFor(name)
            val bgChroma = Contrast.chromaOf(s.background)
            val contChroma = Contrast.chromaOf(s.surfaceContainer)
            val highChroma = Contrast.chromaOf(s.surfaceContainerHigh)
            assertTrue(
                "$name colorful background C*=$bgChroma must be in [7.0, 14.5]",
                bgChroma in 7.0..14.5,
            )
            assertTrue(
                "$name colorful surfaceContainer C*=$contChroma must be in [12.0, 16.5]",
                contChroma in 12.0..16.5,
            )
            assertTrue(
                "$name colorful surfaceContainerHigh C*=$highChroma must be in [13.5, 17.0]",
                highChroma in 13.5..17.0,
            )
        }

        for (name in neutralFixtures) {
            val s = lightSchemeFor(name)
            val bgChroma = Contrast.chromaOf(s.background)
            val surfChroma = Contrast.chromaOf(s.surface)
            val contChroma = Contrast.chromaOf(s.surfaceContainer)
            val highChroma = Contrast.chromaOf(s.surfaceContainerHigh)
            assertTrue("$name neutral background C*=$bgChroma must be < 1.5", bgChroma < 1.5)
            assertTrue("$name neutral surface C*=$surfChroma must be < 1.5", surfChroma < 1.5)
            assertTrue("$name neutral surfaceContainer C*=$contChroma must be < 1.5", contChroma < 1.5)
            assertTrue("$name neutral surfaceContainerHigh C*=$highChroma must be < 1.5", highChroma < 1.5)
        }
    }

    @Test
    fun lightTheme_chromaScalesSmoothlyWithSeedChromaAndTintStrength() {
        // Seed just below floor stays neutral; mid-chroma scales smoothly; full pivot reaches target cap.
        val belowFloor = Contrast.fromToneChroma(240.0, 50f, 3.5f)
        val midChroma = Contrast.fromToneChroma(240.0, 50f, 14.0f)
        val highChroma = Contrast.fromToneChroma(240.0, 50f, 60.0f)

        val sFloor = PlayingColors.buildMaterialScheme(belowFloor, dark = false)
        val sMid = PlayingColors.buildMaterialScheme(midChroma, dark = false)
        val sHigh = PlayingColors.buildMaterialScheme(highChroma, dark = false)
        val sZeroTint = PlayingColors.buildMaterialScheme(highChroma, dark = false, lightTintStrength = 0f)

        assertTrue(Contrast.chromaOf(sFloor.surfaceContainerHigh) < 1.5)
        assertTrue(Contrast.chromaOf(sMid.surfaceContainerHigh) > 9.0)
        assertTrue(Contrast.chromaOf(sHigh.surfaceContainerHigh) > Contrast.chromaOf(sMid.surfaceContainerHigh))
        assertTrue(Contrast.chromaOf(sHigh.surfaceContainerHigh) >= 14.5)
        assertTrue(Contrast.chromaOf(sZeroTint.background) < 1.5)
        assertTrue(Contrast.chromaOf(sZeroTint.surfaceContainerHigh) < 1.5)
    }

    @Test
    fun lightTheme_onSurfaceOnSurfaceVariantAndPrimaryMeetWcagOnAllTintedSurfaces() {
        for (name in ArtworkFixtures.ALL.keys) {
            val s = lightSchemeFor(name)
            val surfaces = listOf(
                "surfaceContainerLowest" to s.surfaceContainerLowest,
                "background" to s.background,
                "surface" to s.surface,
                "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainer" to s.surfaceContainer,
                "miniContainer" to s.miniContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh,
                "surfaceVariant" to s.surfaceVariant,
            )
            for ((surfName, bg) in surfaces) {
                assertRatio("$name onSurface/$surfName", s.onSurface, bg, 4.5)
                assertRatio("$name onSurfaceVariant/$surfName", s.onSurfaceVariant, bg, 4.5)
                assertRatio("$name primary/$surfName", s.primary, bg, 4.5)
            }
            assertRatio("$name ringTrack/primary", s.ringTrack, s.primary, 3.0)
            assertRatio("$name miniOutline/background", s.miniOutline, s.background, 3.0)
            assertRatio("$name miniOutline/miniContainer", s.miniOutline, s.miniContainer, 3.0)
            assertRatio("$name switchOffOutline/surface", s.switchOffOutline, s.surface, 3.0)
            assertRatio("$name switchOffOutline/surfaceContainerHigh", s.switchOffOutline, s.surfaceContainerHigh, 3.0)
            assertRatio("$name switchOffThumb/switchOffTrack", s.switchOffThumb, s.switchOffTrack, 3.0)
        }
    }

    @Test
    fun darkTheme_backgroundLightnessAndChromaUnaffectedByLightTintStrength() {
        for ((name, art) in ArtworkFixtures.ALL) {
            val darkDefault = PlayingColors.schemeFromPixels(
                pixels = art.argb,
                width = art.width,
                height = art.height,
                dark = true,
            )!!
            val darkZero = PlayingColors.schemeFromPixels(
                pixels = art.argb,
                width = art.width,
                height = art.height,
                dark = true,
                lightTintStrength = 0f,
            )!!
            val darkFull = PlayingColors.schemeFromPixels(
                pixels = art.argb,
                width = art.width,
                height = art.height,
                dark = true,
                lightTintStrength = 1.0f,
            )!!
            assertEquals("$name dark scheme must be identical regardless of lightTintStrength", darkZero, darkFull)
            assertEquals("$name dark default must match", darkDefault, darkFull)
            assertTrue(
                "$name dark background L* must stay near black (<= 8.0), got ${Contrast.toneOf(darkFull.background)}",
                Contrast.toneOf(darkFull.background) <= 8.0,
            )
            assertTrue(
                "$name dark background C* must stay <= 6.5, got ${Contrast.chromaOf(darkFull.background)}",
                Contrast.chromaOf(darkFull.background) <= 6.5,
            )
        }
    }

    @Test
    fun dynamicOffOrNothingPlaying_matchesPlainStaticLightBackground() {
        val staticScheme = PlayingColors.buildMaterialScheme(seedArgb = null, dark = false)
        val staticMaterial = staticScheme.toMaterialColorScheme()
        val staticTokens = staticScheme.toVusicColorTokens()

        assertEquals(0xFFF7F9FBL, staticScheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF7F9FB), staticMaterial.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF7F9FB), staticTokens.background)
        assertEquals(0xFFFFFFFFL, staticScheme.surface)
    }

    private fun assertRatio(label: String, fg: Long, bg: Long, minRatio: Double) {
        val actual = Contrast.ratio(fg, bg)
        assertTrue(
            "$label contrast $actual < $minRatio (fg=0x${fg.toString(16)} bg=0x${bg.toString(16)})",
            actual >= minRatio,
        )
    }
}
