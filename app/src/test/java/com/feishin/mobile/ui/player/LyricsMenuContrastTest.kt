package com.feishin.mobile.ui.player

import com.feishin.mobile.ui.theme.VusicColorMath
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG AA contrast for the Lyrics menu sheet (specs/section_f_spec.md, sections 1, 3, 4).
 * Sheet color is `surfaceContainerLow`; the control colors come from `VusicControlColors`.
 * Both sit behind @Composable getters or private scheme literals, so Theme.kt hexes are pinned.
 */
class LyricsMenuContrastTest {

    // Theme.kt: Dark surfaceContainerLow = DarkBackground, Light surfaceContainerLow.
    private val darkSheet = 0xFF0F0F0F
    private val lightSheet = 0xFFF2F5F9

    // Theme.kt: Dark/Light onSurface and onSurfaceVariant (row titles, helper text).
    private val darkOnSurface = 0xFFFFFFFF
    private val darkOnSurfaceVariant = 0xFF9AA0A6
    private val lightOnSurface = 0xFF0B0F14
    private val lightOnSurfaceVariant = 0xFF3F4A52

    // Theme.kt: VusicControlColors switch OFF colors.
    private val darkOffTrack = 0xFF3A3A3A
    private val darkOffOutline = 0xFF8A8A8A
    private val darkOffThumb = 0xFFB8B8B8
    private val lightOffTrack = 0xFFD3D6DA
    private val lightOffOutline = 0xFF5F6368
    private val lightOffThumb = 0xFF5F6368

    private fun luminance(argb: Long): Float = VusicColorMath.relativeLuminance(
        r = ((argb shr 16) and 0xFF) / 255f,
        g = ((argb shr 8) and 0xFF) / 255f,
        b = (argb and 0xFF) / 255f,
    )

    private fun contrast(foreground: Long, background: Long): Float =
        VusicColorMath.contrastRatio(luminance(foreground), luminance(background))

    private fun assertContrast(name: String, foreground: Long, background: Long, minimum: Float) {
        val ratio = contrast(foreground, background)
        assertTrue("$name contrast $ratio must be >= $minimum", ratio >= minimum)
    }

    @Test
    fun dark_sheetTextClearsAa() {
        assertContrast("dark onSurface", darkOnSurface, darkSheet, 4.5f)
        assertContrast("dark onSurfaceVariant", darkOnSurfaceVariant, darkSheet, 4.5f)
    }

    @Test
    fun light_sheetTextClearsAa() {
        assertContrast("light onSurface", lightOnSurface, lightSheet, 4.5f)
        assertContrast("light onSurfaceVariant", lightOnSurfaceVariant, lightSheet, 4.5f)
    }

    @Test
    fun dark_switchOffThumbAndOutlineClearThreeToOne() {
        assertContrast("dark off thumb", darkOffThumb, darkSheet, 3.0f)
        assertContrast("dark off outline", darkOffOutline, darkSheet, 3.0f)
    }

    @Test
    fun light_switchOffThumbAndOutlineClearThreeToOne() {
        assertContrast("light off thumb", lightOffThumb, lightSheet, 3.0f)
        assertContrast("light off outline", lightOffOutline, lightSheet, 3.0f)
    }

    /**
     * Known gap pinned: the OFF track is a fill, not a boundary, so neither theme reaches
     * 3:1 against the sheet (dark 1.69:1, light 1.33:1). The OFF boundary is the outline,
     * asserted above. If the track is ever darkened this fails, and it can move to 3:1.
     */
    @Test
    fun switchOffTrack_isFillOnly_belowThreeToOne() {
        assertTrue("dark off track must stay a fill", contrast(darkOffTrack, darkSheet) < 3.0f)
        assertTrue("light off track must stay a fill", contrast(lightOffTrack, lightSheet) < 3.0f)
    }
}
