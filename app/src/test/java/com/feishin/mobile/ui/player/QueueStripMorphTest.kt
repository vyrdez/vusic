package com.feishin.mobile.ui.player

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.BlurScrim
import com.feishin.mobile.core.ui.Contrast
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.ui.theme.toMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Pure-JVM unit tests for Phase 11.2 queue-strip morph, lyrics clearance/edge fade,
 * and collapsed strip WCAG contrast across all [BackgroundStyle]s and Dynamic themes.
 */
class QueueStripMorphTest {

    private fun Color.toPackedArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private fun Color.compositeOver(bgArgb: Long): Long {
        val fgArgb = toPackedArgb()
        return if (alpha >= 0.999f) fgArgb else Contrast.blend(fgArgb, bgArgb, alpha)
    }

    private fun assertContrast(label: String, fgArgb: Long, bgArgb: Long, minRatio: Double) {
        val actual = Contrast.ratio(fgArgb, bgArgb)
        assertTrue(
            "$label contrast $actual < $minRatio (fg=0x${fgArgb.toString(16)} bg=0x${bgArgb.toString(16)})",
            actual >= minRatio,
        )
    }

    private fun grayscaleForLuminance(l: Float): Long {
        val clamped = l.coerceIn(0f, 1f)
        val channel = if (clamped <= 0.0031308f) {
            (12.92f * clamped * 255f).toInt()
        } else {
            ((1.055 * clamped.toDouble().pow(1.0 / 2.4) - 0.055) * 255.0).toInt()
        }.coerceIn(0, 255)
        return Contrast.argb(255, channel, channel, channel)
    }

    private fun allColorSchemes(): List<Pair<String, Pair<Boolean, ColorScheme>>> {
        val result = mutableListOf<Pair<String, Pair<Boolean, ColorScheme>>>()
        for (dark in listOf(true, false)) {
            val staticCs = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
                .toMaterialColorScheme()
            result += "static(dark=$dark)" to (dark to staticCs)
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val dynCs = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!.toMaterialColorScheme()
                result += "$name(dark=$dark)" to (dark to dynCs)
            }
        }
        return result
    }

    // ---- 1. Queue sheet surface alpha morph --------------------------------

    @Test
    fun queueSheetSurfaceAlpha_isZeroAtZeroNegativeAndNaN() {
        assertEquals(0.25f, QueuePolishSpecs.QUEUE_SURFACE_FADE_IN_END_PROGRESS, 1e-6f)
        assertEquals(0f, queueSheetSurfaceAlpha(0f), 1e-6f)
        assertEquals(0f, queueSheetSurfaceAlpha(-0.01f), 1e-6f)
        assertEquals(0f, queueSheetSurfaceAlpha(-1f), 1e-6f)
        assertEquals(0f, queueSheetSurfaceAlpha(Float.NaN), 1e-6f)
    }

    @Test
    fun queueSheetSurfaceAlpha_isStrictlyMonotonicAcrossFadeInWindow() {
        val steps = listOf(0f, 0.05f, 0.10f, 0.15f, 0.20f, 0.25f)
        val alphas = steps.map { queueSheetSurfaceAlpha(it) }
        for (i in 0 until alphas.lastIndex) {
            assertTrue(
                "Expected strictly increasing alpha at ${steps[i]} -> ${steps[i + 1]}: ${alphas[i]} vs ${alphas[i + 1]}",
                alphas[i] < alphas[i + 1],
            )
        }
        assertEquals(0f, alphas.first(), 1e-6f)
        assertEquals(1f, alphas.last(), 1e-6f)
    }

    @Test
    fun queueSheetSurfaceAlpha_isOneFromEndProgressToOne() {
        for (p in listOf(0.25f, 0.30f, 0.50f, 0.75f, 1.0f, 1.25f)) {
            assertEquals("Expected alpha == 1f at progress=$p", 1f, queueSheetSurfaceAlpha(p), 1e-6f)
        }
    }

    // ---- 2. Lyrics bottom clearance & edge fade ----------------------------

    @Test
    fun computeLyricsBottomClearanceDp_stopsAboveStripPlusNavBarInset() {
        assertEquals(80, computeLyricsBottomClearanceDp())
        assertEquals(80, computeLyricsBottomClearanceDp(stripHeightDp = 80, navBarBottomInsetDp = 0))
        assertEquals(104, computeLyricsBottomClearanceDp(stripHeightDp = 80, navBarBottomInsetDp = 24))
        assertEquals(128, computeLyricsBottomClearanceDp(stripHeightDp = 80, navBarBottomInsetDp = 48))
        assertEquals(112, computeLyricsBottomClearanceDp(stripHeightDp = 96, navBarBottomInsetDp = 16))
    }

    @Test
    fun computeLyricsEdgeFadeAlpha_reachesZeroAtBottomEdgeAndIsOpaqueInCenter() {
        val panelHeight = 400f
        val edgeFade = 48f

        // Bottom edge reaches 0f (and stays 0f beyond bottom).
        assertEquals(0f, computeLyricsEdgeFadeAlpha(panelHeight, panelHeight, edgeFade), 1e-6f)
        assertEquals(0f, computeLyricsEdgeFadeAlpha(panelHeight + 10f, panelHeight, edgeFade), 1e-6f)

        // Center of the panel is fully opaque.
        assertEquals(1f, computeLyricsEdgeFadeAlpha(panelHeight / 2f, panelHeight, edgeFade), 1e-6f)

        // Within bottom fade zone, alpha decreases monotonically toward 0f at panelHeight.
        val nearBottom = listOf(
            panelHeight - edgeFade,
            panelHeight - edgeFade * 0.5f,
            panelHeight - edgeFade * 0.1f,
            panelHeight,
        ).map { computeLyricsEdgeFadeAlpha(it, panelHeight, edgeFade) }
        for (i in 0 until nearBottom.lastIndex) {
            assertTrue(
                "Expected decreasing alpha toward bottom edge: ${nearBottom[i]} vs ${nearBottom[i + 1]}",
                nearBottom[i] > nearBottom[i + 1],
            )
        }
        assertEquals(0f, nearBottom.last(), 1e-6f)

        // Degenerate geometry never produces NaN.
        assertFalse(computeLyricsEdgeFadeAlpha(0f, 0f, edgeFade).isNaN())
        assertFalse(computeLyricsEdgeFadeAlpha(10f, panelHeight, 0f).isNaN())
    }

    // ---- 3. Collapsed strip WCAG contrast ----------------------------------

    @Test
    fun resolveCollapsedStripColors_meetsWcagContrastAcrossAllStylesAndThemes() {
        for ((tag, pair) in allColorSchemes()) {
            val (isDark, cs) = pair

            // DEFAULT: check against surface & surfaceContainer (including dynamic surface tints).
            val defaultColors = resolveCollapsedStripColors(
                playerStyle = BackgroundStyle.DEFAULT,
                isDark = isDark,
                colorScheme = cs,
                playerSurfaceColor = cs.surface,
                contentColor = cs.onSurface,
                dimColor = cs.onSurfaceVariant,
            )
            for (bg in listOf(cs.surface.toPackedArgb(), cs.surfaceContainer.toPackedArgb())) {
                assertContrast(
                    "$tag DEFAULT text",
                    defaultColors.textColor.compositeOver(bg),
                    bg,
                    4.5,
                )
                assertContrast(
                    "$tag DEFAULT handle",
                    defaultColors.handleColor.compositeOver(bg),
                    bg,
                    3.0,
                )
            }

            // GRADIENT: check against surfaceContainer & primaryContainer surface tints.
            for (gradSurface in listOf(cs.surface, cs.surfaceContainer, cs.primaryContainer)) {
                val gradBg = gradSurface.toPackedArgb()
                val gradColors = resolveCollapsedStripColors(
                    playerStyle = BackgroundStyle.GRADIENT,
                    isDark = isDark,
                    colorScheme = cs,
                    playerSurfaceColor = gradSurface,
                    contentColor = cs.onSurface,
                    dimColor = cs.onSurfaceVariant,
                )
                assertContrast(
                    "$tag GRADIENT text",
                    gradColors.textColor.compositeOver(gradBg),
                    gradBg,
                    4.5,
                )
                assertContrast(
                    "$tag GRADIENT handle",
                    gradColors.handleColor.compositeOver(gradBg),
                    gradBg,
                    3.0,
                )
            }

            // BLUR: sweep full [0f, 1f] artwork luminance range.
            val surfaceArgb = cs.surface.toPackedArgb()
            for (step in 0..20) {
                val l = step / 20f
                val art = grayscaleForLuminance(l)
                val blurBg = Contrast.blend(surfaceArgb, art, BlurScrim.scrimAlpha(l, isDark))
                val blurColors = resolveCollapsedStripColors(
                    playerStyle = BackgroundStyle.BLUR,
                    isDark = isDark,
                    colorScheme = cs,
                    playerSurfaceColor = Color(blurBg.toInt()),
                    contentColor = cs.onSurface,
                    dimColor = cs.onSurfaceVariant,
                    avgArtworkLuminance = l,
                )
                assertContrast(
                    "$tag BLUR L=$l text",
                    blurColors.textColor.compositeOver(blurBg),
                    blurBg,
                    4.5,
                )
                assertContrast(
                    "$tag BLUR L=$l handle",
                    blurColors.handleColor.compositeOver(blurBg),
                    blurBg,
                    3.0,
                )
            }
        }
    }
}
