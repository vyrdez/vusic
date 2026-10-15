package com.feishin.mobile.core.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast maths plus the small HSL toolkit the artwork color scheme is
 * built from. Everything here operates on packed `0xAARRGGBB` Longs and has no
 * Android/Compose dependency, so it is unit-testable on the JVM.
 */
object Contrast {

    fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()
    fun red(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()
    fun green(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()
    fun blue(argb: Long): Int = (argb and 0xFF).toInt()

    fun argb(a: Int, r: Int, g: Int, b: Int): Long =
        ((a.coerceIn(0, 255).toLong() shl 24) or
            (r.coerceIn(0, 255).toLong() shl 16) or
            (g.coerceIn(0, 255).toLong() shl 8) or
            b.coerceIn(0, 255).toLong())

    private fun linearize(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** WCAG relative luminance, 0 (black) .. 1 (white). */
    fun luminance(argb: Long): Double {
        val r = linearize(red(argb) / 255.0)
        val g = linearize(green(argb) / 255.0)
        val b = linearize(blue(argb) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG contrast ratio, 1 (same) .. 21 (black on white). */
    fun ratio(fgArgb: Long, bgArgb: Long): Double {
        val a = luminance(fgArgb)
        val b = luminance(bgArgb)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Opaque source-over blend: [fg] at [fgAlpha] over an opaque [bg]. */
    fun blend(fgArgb: Long, bgArgb: Long, fgAlpha: Float): Long {
        val t = fgAlpha.coerceIn(0f, 1f).toDouble()
        fun ch(f: Int, b: Int): Int = ((f * t) + (b * (1.0 - t))).toInt()
        return argb(255, ch(red(fgArgb), red(bgArgb)), ch(green(fgArgb), green(bgArgb)), ch(blue(fgArgb), blue(bgArgb)))
    }

    data class Hsl(val h: Float, val s: Float, val l: Float)

    /** Hue 0..360, saturation/lightness 0..1. Grayscale yields h = 0. */
    fun toHsl(argb: Long): Hsl {
        val r = red(argb) / 255.0
        val g = green(argb) / 255.0
        val b = blue(argb) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = ((mx + mn) / 2.0).toFloat()
        if (mx == mn) return Hsl(0f, 0f, l)
        val d = mx - mn
        val s = (if (l > 0.5f) d / (2.0 - mx - mn) else d / (mx + mn)).toFloat()
        var h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0))
            g -> ((b - r) / d + 2)
            else -> ((r - g) / d + 4)
        } * 60.0
        if (h < 0) h += 360.0
        return Hsl(h.toFloat(), s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Long {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        if (ss == 0f) {
            val v = (ll * 255).toInt()
            return argb(255, v, v, v)
        }
        val q = (if (ll < 0.5f) ll * (1 + ss) else ll + ss - ll * ss).toDouble()
        val p = 2 * ll.toDouble() - q
        fun hue(t: Double): Int {
            var tt = t
            if (tt < 0) tt += 1.0
            if (tt > 1) tt -= 1.0
            val c = when {
                tt < 1.0 / 6 -> p + (q - p) * 6 * tt
                tt < 1.0 / 2 -> q
                tt < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - tt) * 6
                else -> p
            }
            return (c * 255).toInt()
        }
        val hk = hh / 360.0
        return argb(255, hue(hk + 1.0 / 3), hue(hk), hue(hk - 1.0 / 3))
    }

    fun saturationOf(argb: Long): Float = toHsl(argb).s

    fun withLightness(argb: Long, l: Float): Long {
        val hsl = toHsl(argb)
        return fromHsl(hsl.h, hsl.s, l)
    }

    fun withSaturation(argb: Long, s: Float): Long {
        val hsl = toHsl(argb)
        return fromHsl(hsl.h, s, hsl.l)
    }

    /**
     * The higher-contrast of [a] vs [b] on [bg]. In particular one of pure
     * black / pure white always reaches 4.5:1, so text chosen this way from
     * that pair can never fail.
     */
    fun pickBest(a: Long, b: Long, bg: Long): Long =
        if (ratio(a, bg) >= ratio(b, bg)) a else b
}
