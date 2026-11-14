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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for Phase 11.1 Section D queue polish specs (`QueuePolishSpecs.kt`):
 * - D1: Row gap (`6..8 dp`), min row height (`>= 64 dp`), stride/drop-target math, bottom inset padding
 * - D2: Transparent bottom bar (`alpha == 0f`) and `>= 3.0:1` icon contrast across all styles & Dark/Light
 * - D3: Concentric radii (`coverRadius == highlightRadius - innerPadding`, equal padding, uniform cover size)
 * - D4: Highlight style contrast (`>= 4.5:1` text, `>= 3.0:1` icons) across `DEFAULT`, `GRADIENT`, and
 *   `BLUR` (swept across `[0.0f, 1.0f]` artwork luminance) for static + all 9 [ArtworkFixtures.ALL].
 */
class QueuePolishTest {

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
            ((1.055 * Math.pow(clamped.toDouble(), 1.0 / 2.4) - 0.055) * 255.0).toInt()
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

    // ---- D1 & D3: Spacing, Sizing & Concentric Geometry --------------------

    @Test
    fun d1AndD3_spacingSizingAndConcentricRadii_matchSpec() {
        assertTrue(
            "ROW_VERTICAL_GAP_DP must be in 6..8",
            QueuePolishSpecs.ROW_VERTICAL_GAP_DP in 6..8,
        )
        assertTrue(
            "ROW_MIN_HEIGHT_DP must be >= 64",
            QueuePolishSpecs.ROW_MIN_HEIGHT_DP >= 64,
        )
        assertTrue(
            "LIST_HORIZONTAL_PADDING_DP must be in 12..16",
            QueuePolishSpecs.LIST_HORIZONTAL_PADDING_DP in 12..16,
        )
        assertEquals(20, QueuePolishSpecs.HIGHLIGHT_CORNER_RADIUS_DP)
        assertEquals(10, QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP)
        assertEquals(
            QueuePolishSpecs.HIGHLIGHT_CORNER_RADIUS_DP - QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP,
            QueuePolishSpecs.COVER_CORNER_RADIUS_DP,
        )
        assertEquals(10, QueuePolishSpecs.COVER_CORNER_RADIUS_DP)
        assertEquals(48, QueuePolishSpecs.COVER_SIZE_DP)
        assertTrue(
            "Cover + equal 10dp top/bottom padding must satisfy min row height",
            QueuePolishSpecs.COVER_SIZE_DP + 2 * QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP >=
                QueuePolishSpecs.ROW_MIN_HEIGHT_DP,
        )
        assertEquals(0.40f, QueuePolishSpecs.COVER_SCRIM_ALPHA, 1e-6f)
        assertEquals(200, QueuePolishSpecs.HIGHLIGHT_CROSSFADE_MS)
    }

    @Test
    fun d1_strideDropTargetAndBottomContentPadding_areAccurate() {
        val stride = computeQueueItemStridePx(itemHeightPx = 192, rowGapPx = 21)
        assertEquals(213, stride)

        assertEquals(0, computeDropTargetIndex(dragTopPx = -40f, itemStridePx = stride, itemCount = 5))
        assertEquals(0, computeDropTargetIndex(dragTopPx = 0f, itemStridePx = stride, itemCount = 5))
        assertEquals(1, computeDropTargetIndex(dragTopPx = 110f, itemStridePx = stride, itemCount = 5))
        assertEquals(2, computeDropTargetIndex(dragTopPx = 426f, itemStridePx = stride, itemCount = 5))
        assertEquals(4, computeDropTargetIndex(dragTopPx = 1200f, itemStridePx = stride, itemCount = 5))
        assertEquals(0, computeDropTargetIndex(dragTopPx = Float.NaN, itemStridePx = stride, itemCount = 5))
        assertEquals(0, computeDropTargetIndex(dragTopPx = 100f, itemStridePx = stride, itemCount = 0))
        assertEquals(
            3,
            computeDropTargetIndex(dragTopPx = 640f, itemHeightPx = 192, rowGapPx = 21, itemCount = 5),
        )

        assertEquals(
            56 + 24 + 8,
            computeListBottomContentPaddingDp(controlsHeightDp = 56, navBarBottomInsetDp = 24),
        )
        assertEquals(
            56 + 0 + 12,
            computeListBottomContentPaddingDp(controlsHeightDp = 56, navBarBottomInsetDp = 0, extraClearanceDp = 12),
        )
        assertEquals(
            56,
            computeListBottomContentPaddingDp(controlsHeightDp = 56, navBarBottomInsetDp = -4, extraClearanceDp = -2),
        )
    }

    @Test
    fun d2_bottomFadeStops_stayOrderedInZeroToOneAcrossViewports() {
        assertEquals(null, computeQueueBottomFadeStops(0f, 84f, 168f, 72f))
        assertEquals(null, computeQueueBottomFadeStops(Float.NaN, 84f, 168f, 72f))
        assertEquals(null, computeQueueBottomFadeStops(1000f, 0f, 168f, 72f))

        val normal = computeQueueBottomFadeStops(
            viewportHeightPx = 1000f,
            fadeHeightPx = 84f,
            bottomControlsHeightPx = 168f,
            navBarBottomPx = 72f,
        )!!
        assertEquals(0.76f, normal.first, 1e-4f)
        assertEquals(0.844f, normal.second, 1e-4f)
        assertTrue(normal.first in 0f..normal.second && normal.second <= 1f)

        val noNav = computeQueueBottomFadeStops(
            viewportHeightPx = 1000f,
            fadeHeightPx = 84f,
            bottomControlsHeightPx = 168f,
            navBarBottomPx = 0f,
        )!!
        assertEquals(0.832f, noNav.first, 1e-4f)
        assertEquals(0.916f, noNav.second, 1e-4f)

        val tiny = computeQueueBottomFadeStops(
            viewportHeightPx = 100f,
            fadeHeightPx = 84f,
            bottomControlsHeightPx = 168f,
            navBarBottomPx = 72f,
        )!!
        assertEquals(0f, tiny.first, 1e-4f)
        assertEquals(0.84f, tiny.second, 1e-4f)
    }

    @Test
    fun breakUi_queueEdgeCases_emptySingleShort500ItemsAndFontScale13() {
        val stride = computeQueueItemStridePx(itemHeightPx = 192, rowGapPx = 21)

        // 0 items (empty) and invalid strides never throw or produce negative bounds
        for (offset in listOf(-500f, 0f, 213f, 10_000f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0, computeDropTargetIndex(offset, stride, itemCount = 0))
            assertEquals(0, computeDropTargetIndex(offset, itemStridePx = 0, itemCount = 5))
            assertEquals(0, computeDropTargetIndex(offset, itemStridePx = -10, itemCount = 5))
        }

        // 1 item (single), 3 items (short), 500 & 1200 items (huge): always in 0 until itemCount
        for (count in listOf(1, 3, 500, 1200)) {
            assertEquals(0, computeDropTargetIndex(-250f, stride, count))
            assertEquals(0, computeDropTargetIndex(0f, stride, count))
            assertEquals(count - 1, computeDropTargetIndex(count * stride * 2f, stride, count))
            assertEquals(0, computeDropTargetIndex(Float.POSITIVE_INFINITY, stride, count))
            assertEquals(0, computeDropTargetIndex(Float.NEGATIVE_INFINITY, stride, count))
            val mid = count / 2
            val midIdx = computeDropTargetIndex(mid * stride.toFloat(), stride, count)
            assertEquals(mid, midIdx)
            assertTrue(midIdx in 0 until count)
        }

        // Bottom content padding at normal (24dp) and large (72dp, 120dp) nav bar insets
        assertEquals(88, computeListBottomContentPaddingDp(QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP, 24))
        assertEquals(136, computeListBottomContentPaddingDp(QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP, 72))
        assertEquals(184, computeListBottomContentPaddingDp(QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP, 120))
        assertEquals(64, computeListBottomContentPaddingDp(QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP, -12))

        // Concentric radii, equal 10dp padding, and row min-height invariants at fontScale = 1.3
        val fontScale = 1.3f
        val concentricCoverRadius =
            QueuePolishSpecs.HIGHLIGHT_CORNER_RADIUS_DP - QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP
        assertEquals(QueuePolishSpecs.COVER_CORNER_RADIUS_DP, concentricCoverRadius)
        assertTrue(QueuePolishSpecs.ROW_MIN_HEIGHT_DP >= 64)
        assertTrue(
            QueuePolishSpecs.COVER_SIZE_DP + 2 * QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP >=
                QueuePolishSpecs.ROW_MIN_HEIGHT_DP,
        )
        val scaledTextBlockDp = (16f * fontScale) + 2f + (14f * fontScale)
        val scaledRowMinNeededDp = scaledTextBlockDp + 2 * QueuePolishSpecs.HIGHLIGHT_INNER_PADDING_DP
        assertTrue("Scaled text + 10dp top/bottom padding must stay positive", scaledRowMinNeededDp > 60f)
    }

    // ---- D2: Transparent Bottom Bar & Control Contrast ---------------------

    @Test
    fun d2_bottomBarIsTransparentAndIconsClearMinimumContrastAcrossAllStyles() {
        assertEquals(56, QueuePolishSpecs.BOTTOM_CONTROLS_HEIGHT_DP)
        assertEquals(28, QueuePolishSpecs.BOTTOM_FADE_HEIGHT_DP)
        assertEquals(0f, QueuePolishSpecs.BOTTOM_BAR_CONTAINER_ALPHA, 1e-6f)

        for ((tag, pair) in allColorSchemes()) {
            val (isDark, cs) = pair
            val surfaceArgb = cs.surface.toPackedArgb()
            for (style in BackgroundStyle.entries) {
                val controls = resolveQueueBottomControlColors(
                    style = style,
                    isDark = isDark,
                    colorScheme = cs,
                    onSheetColor = cs.onSurface,
                )
                assertEquals(
                    "$tag $style container alpha must be 0f",
                    0f,
                    controls.containerColor.alpha,
                    0.005f,
                )

                val chevronArgb = controls.chevronTint.toPackedArgb()
                val inactiveArgb = controls.inactiveIconTint.toPackedArgb()

                when (style) {
                    BackgroundStyle.DEFAULT,
                    BackgroundStyle.GRADIENT,
                    -> {
                        val bgs = listOf(surfaceArgb, cs.surfaceContainer.toPackedArgb())
                        for (bg in bgs) {
                            assertContrast("$tag $style chevron", chevronArgb, bg, 3.0)
                            assertContrast("$tag $style inactiveIcon", inactiveArgb, bg, 3.0)
                        }
                        val pillBg = controls.activePillColor.compositeOver(surfaceArgb)
                        val activeIcon = controls.activeIconTint.compositeOver(pillBg)
                        assertContrast("$tag $style activeIcon/pill", activeIcon, pillBg, 3.0)
                    }

                    BackgroundStyle.BLUR -> {
                        for (step in 0..20) {
                            val l = step / 20f
                            val art = grayscaleForLuminance(l)
                            val blurBg = Contrast.blend(surfaceArgb, art, BlurScrim.scrimAlpha(l, isDark))
                            assertContrast("$tag BLUR L=$l chevron", chevronArgb, blurBg, 3.0)
                            assertContrast("$tag BLUR L=$l inactiveIcon", inactiveArgb, blurBg, 3.0)
                            val pillBg = controls.activePillColor.compositeOver(blurBg)
                            val activeIcon = controls.activeIconTint.compositeOver(pillBg)
                            assertContrast("$tag BLUR L=$l activeIcon/pill", activeIcon, pillBg, 3.0)
                        }
                    }
                }
            }
        }
    }

    // ---- D4: Highlight Style Per BackgroundStyle ---------------------------

    @Test
    fun d4_defaultAndGradient_useSecondaryContainerAndMeetContrast() {
        for ((tag, pair) in allColorSchemes()) {
            val (isDark, cs) = pair
            for (style in listOf(BackgroundStyle.DEFAULT, BackgroundStyle.GRADIENT)) {
                val resolved = resolveQueueHighlightStyle(style, isDark, cs)
                assertEquals("$tag $style container", cs.secondaryContainer, resolved.containerColor)
                assertEquals("$tag $style borderAlpha", 0f, resolved.borderAlpha, 1e-6f)

                val bg = resolved.containerColor.toPackedArgb()
                val title = resolved.titleColor.compositeOver(bg)
                val sub = resolved.subtitleColor.compositeOver(bg)
                val icon = resolved.iconColor.compositeOver(bg)

                assertContrast("$tag $style title", title, bg, 4.5)
                assertContrast("$tag $style subtitle", sub, bg, 4.5)
                assertContrast("$tag $style icon", icon, bg, 3.0)
            }
        }
    }

    @Test
    fun d4_blur_usesFrostedFillWithHairlineAndClearsContrastAcrossFullLuminanceRange() {
        for ((tag, pair) in allColorSchemes()) {
            val (isDark, cs) = pair
            val resolved = resolveQueueHighlightStyle(BackgroundStyle.BLUR, isDark, cs)

            assertEquals("$tag BLUR borderAlpha", 0.12f, resolved.borderAlpha, 1e-6f)
            assertEquals("$tag BLUR borderColor alpha", 0.12f, resolved.borderColor.alpha, 0.005f)
            assertEquals(1f, resolved.containerColor.red, 1e-3f)
            assertEquals(1f, resolved.containerColor.green, 1e-3f)
            assertEquals(1f, resolved.containerColor.blue, 1e-3f)

            val expectedAlphaRange = if (isDark) {
                (QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_CLAMP_MIN - 1e-4f)..(QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_MAX + 1e-4f)
            } else {
                (QueuePolishSpecs.BLUR_LIGHT_FILL_ALPHA_MIN - 1e-4f)..(QueuePolishSpecs.BLUR_LIGHT_FILL_ALPHA_MAX + 1e-4f)
            }
            assertTrue(
                "$tag BLUR fillAlpha ${resolved.containerColor.alpha} out of $expectedAlphaRange",
                resolved.containerColor.alpha in expectedAlphaRange,
            )

            val scrimArgb = cs.surface.toPackedArgb()
            // Verify across entire artwork luminance range [0.0f, 1.0f], including worst-case endpoints.
            for (step in 0..20) {
                val l = step / 20f
                val art = grayscaleForLuminance(l)
                val blurBg = Contrast.blend(scrimArgb, art, BlurScrim.scrimAlpha(l, isDark))
                val rowBg = resolved.containerColor.compositeOver(blurBg)

                val title = resolved.titleColor.compositeOver(rowBg)
                val sub = resolved.subtitleColor.compositeOver(rowBg)
                val icon = resolved.iconColor.compositeOver(rowBg)

                assertContrast("$tag BLUR L=$l title", title, rowBg, 4.5)
                assertContrast("$tag BLUR L=$l subtitle", sub, rowBg, 4.5)
                assertContrast("$tag BLUR L=$l icon", icon, rowBg, 3.0)
            }
        }
    }
}
