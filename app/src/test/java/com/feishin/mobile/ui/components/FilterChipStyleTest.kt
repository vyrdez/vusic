package com.feishin.mobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.Contrast
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.ui.theme.toMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterChipStyleTest {

    private fun Color.toPackedArgb(): Long {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        return Contrast.argb(a, r, g, b)
    }

    private fun assertRatio(label: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val actual = Contrast.ratio(fg.toPackedArgb(), bg.toPackedArgb())
        assertTrue("$label contrast $actual < $minRatio", actual >= minRatio)
    }

    @Test
    fun staticSchemes_darkAndLight_meetContrastAndNoBorderTokens() {
        for (dark in listOf(true, false)) {
            val scheme = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
                .toMaterialColorScheme()
            val selected = resolveFilterChipColors(selected = true, colorScheme = scheme)
            val unselected = resolveFilterChipColors(selected = false, colorScheme = scheme)

            assertEquals(FontWeight.Medium, selected.fontWeight)
            assertFalse(selected.hasBorder)
            assertEquals(0f, selected.borderAlpha, 0f)

            assertFalse(unselected.hasBorder)
            assertEquals(0f, unselected.borderAlpha, 0f)
            assertNotEquals(selected.containerColor, unselected.containerColor)
            assertNotEquals(scheme.background, unselected.containerColor)
            assertNotEquals(scheme.background, selected.containerColor)

            assertRatio("static(dark=$dark) selected", selected.labelColor, selected.containerColor)
            assertRatio("static(dark=$dark) unselected", unselected.labelColor, unselected.containerColor)
        }
    }

    @Test
    fun dynamicSchemes_allNineFixtures_darkAndLight_meetContrastAndNoBorderTokens() {
        for (dark in listOf(true, false)) {
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val dyn = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!
                val scheme = dyn.toMaterialColorScheme()
                val selected = resolveFilterChipColors(selected = true, colorScheme = scheme)
                val unselected = resolveFilterChipColors(selected = false, colorScheme = scheme)

                assertEquals("$name(dark=$dark) selected fontWeight", FontWeight.Medium, selected.fontWeight)
                assertFalse("$name(dark=$dark) selected border", selected.hasBorder)
                assertEquals(0f, selected.borderAlpha, 0f)

                assertFalse("$name(dark=$dark) unselected border", unselected.hasBorder)
                assertEquals(0f, unselected.borderAlpha, 0f)
                assertNotEquals("$name(dark=$dark) containers distinct", selected.containerColor, unselected.containerColor)
                assertNotEquals("$name(dark=$dark) unselected distinct from bg", scheme.background, unselected.containerColor)

                assertRatio("$name(dark=$dark) selected", selected.labelColor, selected.containerColor)
                assertRatio("$name(dark=$dark) unselected", unselected.labelColor, unselected.containerColor)
            }
        }
    }

    @Test
    fun miniPlayerBorderAlpha_isSubtleInDarkAndLight() {
        val darkAlpha = resolveMiniPlayerBorderAlpha(dark = true)
        val lightAlpha = resolveMiniPlayerBorderAlpha(dark = false)

        assertTrue("dark border alpha $darkAlpha must be in (0, 0.12]", darkAlpha > 0f && darkAlpha <= 0.12f)
        assertTrue("light border alpha $lightAlpha must be in (0, 0.08]", lightAlpha > 0f && lightAlpha <= 0.08f)
    }

    @Test
    fun miniPlayerPillTokens_meetContrastAndSubtleOutline_acrossStaticAndAllFixtures() {
        for (dark in listOf(true, false)) {
            val staticDyn = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
            val staticTokens = resolveMiniPlayerPillTokens(
                dark = dark,
                colorScheme = staticDyn.toMaterialColorScheme(),
                playingScheme = null,
            )
            val maxAlpha = if (dark) 0.12f else 0.08f
            assertTrue("static(dark=$dark) borderAlpha", staticTokens.borderAlpha <= maxAlpha)
            assertEquals(0.5f, staticTokens.borderWidthDp, 0.001f)
            assertRatio("static(dark=$dark) mini ink", staticTokens.inkColor, staticTokens.containerColor)
            assertRatio("static(dark=$dark) mini subInk", staticTokens.subInkColor, staticTokens.containerColor)

            for ((name, artwork) in ArtworkFixtures.ALL) {
                val dyn = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!
                val tokens = resolveMiniPlayerPillTokens(
                    dark = dark,
                    colorScheme = dyn.toMaterialColorScheme(),
                    playingScheme = dyn.toPlayingScheme(),
                )
                assertTrue("$name(dark=$dark) borderAlpha", tokens.borderAlpha <= maxAlpha)
                assertEquals(0.5f, tokens.borderWidthDp, 0.001f)
                assertRatio("$name(dark=$dark) mini ink", tokens.inkColor, tokens.containerColor)
                assertRatio("$name(dark=$dark) mini subInk", tokens.subInkColor, tokens.containerColor)
            }
        }
    }
}
