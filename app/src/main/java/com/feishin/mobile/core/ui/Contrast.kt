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

    // ---- CIELAB: tone (L*) and chroma (C*ab) --------------------------------
    //
    // The dark toning spec is written in Material/HCT terms, where a color is
    // "tone T, chroma C". Tone is L* (0 black .. 100 white). Chroma here is
    // CIELAB C*ab, which is the same perceptual scale family and magnitude as
    // HCT chroma (both cap around 100-130 for sRGB), so the caps in
    // DarkToneSpec read the same way. Unlike HSL lightness, L* is perceptually
    // uniform: "tone 22" means the same visual darkness for every hue, which is
    // what the spec's ranges assume.

    private const val LAB_EPS = 216.0 / 24389.0
    private const val LAB_KAPPA = 24389.0 / 27.0
    private const val WHITE_X = 0.95047
    private const val WHITE_Y = 1.0
    private const val WHITE_Z = 1.08883

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    private fun labF(t: Double): Double =
        if (t > LAB_EPS) t.pow(1.0 / 3.0) else (LAB_KAPPA * t + 16.0) / 116.0

    private fun labFInv(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > LAB_EPS) t3 else (116.0 * t - 16.0) / LAB_KAPPA
    }

    private fun toLab(argb: Long): Triple<Double, Double, Double> {
        val r = srgbToLinear(red(argb) / 255.0)
        val g = srgbToLinear(green(argb) / 255.0)
        val b = srgbToLinear(blue(argb) / 255.0)
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val fx = labF(x / WHITE_X)
        val fy = labF(y / WHITE_Y)
        val fz = labF(z / WHITE_Z)
        return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** CIELAB lightness (tone), 0..100. */
    fun toneOf(argb: Long): Double = toLab(argb).first

    /** CIELAB chroma C*ab (distance from the neutral axis), >= 0. */
    fun chromaOf(argb: Long): Double {
        val (_, a, b) = toLab(argb)
        return kotlin.math.sqrt(a * a + b * b)
    }

    /**
     * The seed's hue as a Lab hue angle in degrees. Derived from the seed
     * itself (not from HSL hue) so the toned color keeps the cover's hue
     * family accurately.
     */
    fun labHue(argb: Long): Double {
        val (_, a, b) = toLab(argb)
        if (a == 0.0 && b == 0.0) return 0.0
        val deg = Math.toDegrees(kotlin.math.atan2(b, a))
        return if (deg < 0) deg + 360.0 else deg
    }

    /**
     * Builds the sRGB color at [tone] L* and [chroma] C*ab on [hueDeg].
     *
     * If the exact pair is outside the sRGB gamut (common for a high chroma at
     * an extreme tone), the chroma is binary-searched down to the highest
     * representable value, so the result honors the tone exactly and the chroma
     * as closely as the gamut allows — never above the requested cap.
     */
    fun fromToneChroma(hueDeg: Double, tone: Float, chroma: Float): Long {
        val t = tone.toDouble().coerceIn(0.0, 100.0)
        val c = chroma.toDouble().coerceAtLeast(0.0)
        labToArgb(t, c, hueDeg)?.let { return it }
        var lo = 0.0
        var hi = c
        var best = labToArgb(t, 0.0, hueDeg) ?: argb(255, 128, 128, 128)
        repeat(24) {
            val mid = (lo + hi) / 2.0
            val cand = labToArgb(t, mid, hueDeg)
            if (cand != null) {
                best = cand
                lo = mid
            } else {
                hi = mid
            }
        }
        return best
    }

    /** Null when the Lab triple falls outside the sRGB gamut. */
    private fun labToArgb(tone: Double, chroma: Double, hueDeg: Double): Long? {
        val rad = Math.toRadians(hueDeg)
        val a = chroma * kotlin.math.cos(rad)
        val b = chroma * kotlin.math.sin(rad)
        val fy = (tone + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = WHITE_X * labFInv(fx)
        val y = WHITE_Y * labFInv(fy)
        val z = WHITE_Z * labFInv(fz)
        val lr = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val lg = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val lb = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        // Small tolerance: 8-bit rounding can sit a hair outside.
        val tol = 0.0015
        if (lr < -tol || lr > 1 + tol || lg < -tol || lg > 1 + tol || lb < -tol || lb > 1 + tol) {
            return null
        }
        val r = (linearToSrgb(lr.coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
        val g = (linearToSrgb(lg.coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
        val bl = (linearToSrgb(lb.coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
        return argb(255, r, g, bl)
    }

    /** Tone moved by [delta], keeping hue and chroma (gamut-clamped). */
    fun withToneDelta(argb: Long, delta: Float): Long =
        fromToneChroma(labHue(argb), (toneOf(argb) + delta).toFloat(), chromaOf(argb).toFloat())

    /** Same tone and hue, chroma forced (gamut-clamped). */
    fun withChroma(argb: Long, chroma: Float): Long =
        fromToneChroma(labHue(argb), toneOf(argb).toFloat(), chroma)
}
