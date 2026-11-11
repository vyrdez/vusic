package com.feishin.mobile.core.ui

/**
 * Pure JVM luminance and scrim alpha calculations for blurred artwork backgrounds.
 *
 * Guarantees WCAG AA (>= 4.5:1) text contrast in both dark and light themes
 * across all possible artwork relative luminances `L in 0..1`.
 */
object BlurScrim {

    /** Dark theme minimum scrim alpha (on pitch-black artwork, L = 0). */
    const val DARK_MIN_ALPHA = 0.38f

    /** Dark theme maximum scrim alpha (on pure-white artwork, L = 1). */
    const val DARK_MAX_ALPHA = 0.72f

    /** Light theme minimum scrim alpha (on pure-white artwork, L = 1). */
    const val LIGHT_MIN_ALPHA = 0.62f

    /** Light theme maximum scrim alpha (on pitch-black artwork, L = 0). */
    const val LIGHT_MAX_ALPHA = 0.84f

    /**
     * Computes the scrim overlay alpha for an artwork backdrop with [avgLuminance] (0..1).
     *
     * - Clamped to `[DARK_MIN_ALPHA, DARK_MAX_ALPHA]` when [isDark] is true, and
     *   `[LIGHT_MIN_ALPHA, LIGHT_MAX_ALPHA]` when [isDark] is false.
     * - Monotonic: in dark theme, brighter artwork produces a stronger dark scrim;
     *   in light theme, darker artwork produces a stronger light scrim so dark text
     *   (`#0B0F14`) always achieves >= 4.5:1 contrast even on worst-case black art.
     */
    fun scrimAlpha(avgLuminance: Float, isDark: Boolean): Float {
        val l = avgLuminance.coerceIn(0f, 1f)
        return if (isDark) {
            (DARK_MIN_ALPHA + l * (DARK_MAX_ALPHA - DARK_MIN_ALPHA))
                .coerceIn(DARK_MIN_ALPHA, DARK_MAX_ALPHA)
        } else {
            (LIGHT_MAX_ALPHA - l * (LIGHT_MAX_ALPHA - LIGHT_MIN_ALPHA))
                .coerceIn(LIGHT_MIN_ALPHA, LIGHT_MAX_ALPHA)
        }
    }

    /**
     * Mean WCAG relative luminance (0..1) across packed `0xAARRGGBB` [pixels].
     * Returns 0.5f for an empty array. Pure JVM (no `android.*` dependencies).
     */
    fun averageLuminance(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0.5f
        var sum = 0.0
        for (px in pixels) {
            val argb = px.toLong() and 0xFFFFFFFFL
            sum += Contrast.luminance(argb)
        }
        return (sum / pixels.size).toFloat().coerceIn(0f, 1f)
    }
}

/** Top-level alias for [BlurScrim.scrimAlpha]. */
fun scrimAlpha(avgLuminance: Float, isDark: Boolean): Float =
    BlurScrim.scrimAlpha(avgLuminance, isDark)

/** Top-level alias for [BlurScrim.averageLuminance]. */
fun averageLuminance(pixels: IntArray): Float =
    BlurScrim.averageLuminance(pixels)
