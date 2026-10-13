package com.feishin.mobile.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour math for the Now Playing backdrop, kept free of Compose types so it is
 * unit-testable.
 *
 * The bug this replaces: a light or white cover art produced a washed-out grey
 * gradient with unreadable text, because the raw dominant colour was used as the
 * backdrop and the text colour flipped to dark on light. We now always darken the
 * dominant colour until white text has comfortable contrast.
 */
object VusicColorMath {
    /** Backdrop never exceeds this relative luminance (~#4a4a4a grey). */
    const val MAX_BACKDROP_LUMINANCE = 0.16f

    /** WCAG relative luminance from linear-ish sRGB channels in 0f..1f. */
    fun relativeLuminance(r: Float, g: Float, b: Float): Float {
        fun lin(c: Float): Float {
            val v = c.coerceIn(0f, 1f)
            return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }

    /** WCAG contrast ratio between two relative luminances. */
    fun contrastRatio(l1: Float, l2: Float): Float =
        (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)

    /** Contrast of white text on a backdrop of [backdropLuminance]. */
    fun whiteContrastOn(backdropLuminance: Float): Float = contrastRatio(1f, backdropLuminance)

    /** Scales RGB channels by [factor] (used to build opaque gradient stops). */
    fun scale(r: Float, g: Float, b: Float, factor: Float): Triple<Float, Float, Float> =
        Triple(
            (r * factor).coerceIn(0f, 1f),
            (g * factor).coerceIn(0f, 1f),
            (b * factor).coerceIn(0f, 1f),
        )

    /**
     * Scales all channels down by a constant factor until the colour is dark
     * enough for white text. Scaling (rather than clamping channels) preserves the
     * hue, so a pink cover still reads pink.
     */
    fun clampToDark(
        r: Float,
        g: Float,
        b: Float,
        maxLuminance: Float = MAX_BACKDROP_LUMINANCE,
    ): Triple<Float, Float, Float> {
        if (relativeLuminance(r, g, b) <= maxLuminance) return Triple(r, g, b)
        // Solve-by-iterate: 0.85^k shrinks fast, and 40 steps covers any input.
        var factor = 1f
        var guard = 0
        while (relativeLuminance(r * factor, g * factor, b * factor) > maxLuminance && guard++ < 40) {
            factor *= 0.85f
        }
        return Triple(
            (r * factor).coerceIn(0f, 1f),
            (g * factor).coerceIn(0f, 1f),
            (b * factor).coerceIn(0f, 1f),
        )
    }
}
