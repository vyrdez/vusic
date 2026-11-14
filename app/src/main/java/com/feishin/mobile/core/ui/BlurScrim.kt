package com.feishin.mobile.core.ui

/**
 * Surface role selecting the Metrolist-style scrim geometry for [BlurredArtworkBackground].
 */
enum class BlurSurfaceRole {
    PLAYER,
    MINI_PLAYER,
    QUEUE,
}

/**
 * Gradient direction for Layer 3 of [OpaqueBlurStackSpec].
 */
enum class BlurGradientAxis {
    VERTICAL,
    HORIZONTAL,
}

/**
 * Pure JVM specification of the 3-layer opaque blur background stack:
 * 1. Solid opaque base ([baseColorArgb] at [baseAlpha] == 1.0f).
 * 2. Clipped blurred artwork at [artworkAlpha] in `0.80f..0.90f` so spatial artwork colors show through.
 * 3. Uniform contrast scrim ([scrimStartArgb] at [scrimStartAlpha]..[scrimEndAlpha],
 *    delta <= `0.05f`) preserving the top, middle, and bottom colors of the blurred artwork.
 */
data class OpaqueBlurStackSpec(
    val role: BlurSurfaceRole,
    val isDark: Boolean,
    val baseColorArgb: Long,
    val baseAlpha: Float,
    val artworkAlpha: Float,
    val gradientAxis: BlurGradientAxis,
    val scrimStartArgb: Long,
    val scrimStartAlpha: Float,
    val scrimEndArgb: Long,
    val scrimEndAlpha: Float,
)

/**
 * Pure JVM luminance and scrim alpha calculations for blurred artwork backgrounds.
 *
 * Guarantees WCAG AA (>= 4.5:1) text contrast in both dark and light themes
 * across all possible artwork relative luminances `L in 0..1`.
 */
object BlurScrim {

    /** Layer 1 base alpha is always 1.0f at rest so underlying views never bleed through. */
    const val BASE_ALPHA = 1.0f

    /** Layer 2 artwork alpha bounds. */
    const val ARTWORK_MIN_ALPHA = 0.80f
    const val ARTWORK_MAX_ALPHA = 0.90f

    /** Dark theme minimum scrim alpha (on pitch-black artwork, L = 0). */
    const val DARK_MIN_ALPHA = 0.32f

    /** Dark theme maximum scrim alpha (on pure-white artwork, L = 1). */
    const val DARK_MAX_ALPHA = 0.72f

    /** Light theme minimum scrim alpha (on pure-white artwork, L = 1). */
    const val LIGHT_MIN_ALPHA = 0.54f

    /** Light theme maximum scrim alpha (on pitch-black artwork, L = 0). */
    const val LIGHT_MAX_ALPHA = 0.82f

    /**
     * Computes Layer 2 blurred artwork alpha in `[0.80f, 0.90f]` from [avgLuminance] and [isDark].
     */
    fun artworkAlpha(avgLuminance: Float, isDark: Boolean): Float {
        val l = avgLuminance.coerceIn(0f, 1f)
        return if (isDark) {
            (ARTWORK_MAX_ALPHA - l * (ARTWORK_MAX_ALPHA - ARTWORK_MIN_ALPHA))
                .coerceIn(ARTWORK_MIN_ALPHA, ARTWORK_MAX_ALPHA)
        } else {
            (ARTWORK_MIN_ALPHA + l * (ARTWORK_MAX_ALPHA - ARTWORK_MIN_ALPHA))
                .coerceIn(ARTWORK_MIN_ALPHA, ARTWORK_MAX_ALPHA)
        }
    }

    /**
     * Resolves the complete 3-layer [OpaqueBlurStackSpec] for [role] using a uniform
     * contrast scrim so the background follows the blurred artwork spatially.
     */
    fun resolveStack(
        role: BlurSurfaceRole,
        avgLuminance: Float,
        isDark: Boolean,
        baseColorArgb: Long,
        accentTintArgb: Long = baseColorArgb,
    ): OpaqueBlurStackSpec {
        val l = avgLuminance.coerceIn(0f, 1f)
        val opaqueBase = baseColorArgb or 0xFF000000L
        val artAlpha = artworkAlpha(l, isDark)
        val uniformScrimAlpha = scrimAlpha(l, isDark)
        val axis = when (role) {
            BlurSurfaceRole.PLAYER,
            BlurSurfaceRole.QUEUE -> BlurGradientAxis.VERTICAL
            BlurSurfaceRole.MINI_PLAYER -> BlurGradientAxis.HORIZONTAL
        }
        return OpaqueBlurStackSpec(
            role = role,
            isDark = isDark,
            baseColorArgb = opaqueBase,
            baseAlpha = BASE_ALPHA,
            artworkAlpha = artAlpha,
            gradientAxis = axis,
            scrimStartArgb = opaqueBase,
            scrimStartAlpha = uniformScrimAlpha,
            scrimEndArgb = opaqueBase,
            scrimEndAlpha = uniformScrimAlpha,
        )
    }

    /**
     * Composites Layer 1 (opaque [baseColorArgb]) -> Layer 2 ([artworkArgb] at [artworkAlpha]) ->
     * Layer 3 ([scrimColorArgb] at [scrimAlpha]) into an opaque `0xFFRRGGBBL` background.
     */
    fun compositeLayerColor(
        baseColorArgb: Long,
        artworkArgb: Long,
        artworkAlpha: Float,
        scrimColorArgb: Long,
        scrimAlpha: Float,
    ): Long {
        val opaqueBase = baseColorArgb or 0xFF000000L
        val opaqueArt = artworkArgb or 0xFF000000L
        val opaqueScrim = scrimColorArgb or 0xFF000000L
        val afterArt = Contrast.blend(opaqueArt, opaqueBase, artworkAlpha)
        return Contrast.blend(opaqueScrim, afterArt, scrimAlpha)
    }

    /**
     * Computes the scrim overlay alpha for an artwork backdrop with [avgLuminance] (0..1).
     *
     * - Clamped to `[DARK_MIN_ALPHA, DARK_MAX_ALPHA]` when [isDark] is true, and
     *   `[LIGHT_MIN_ALPHA, LIGHT_MAX_ALPHA]` when [isDark] is false.
     * - Monotonic: in dark theme, brighter artwork produces a stronger dark scrim
     *   (perceptually weighted with `L^0.45` so bright midtones stay >= 4.5:1);
     *   in light theme, darker artwork produces a stronger light scrim so dark text
     *   (`#0B0F14`) always achieves >= 4.5:1 contrast even on worst-case black art.
     */
    fun scrimAlpha(avgLuminance: Float, isDark: Boolean): Float {
        val l = avgLuminance.coerceIn(0f, 1f)
        return if (isDark) {
            val perceptual = Math.pow(l.toDouble(), 0.45).toFloat()
            (DARK_MIN_ALPHA + perceptual * (DARK_MAX_ALPHA - DARK_MIN_ALPHA))
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
