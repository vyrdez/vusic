package com.feishin.mobile.core.ui

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-JVM ARGB pixel buffer for deterministic cover-art fixtures.
 * Zero android.* dependencies so unit tests and color engines can consume it directly.
 */
data class ArtworkPixels(
    val argb: IntArray,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive: ${width}x$height" }
        require(argb.size == width * height) {
            "Pixel count ${argb.size} does not match ${width}x$height"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtworkPixels) return false
        return width == other.width && height == other.height && argb.contentEquals(other.argb)
    }

    override fun hashCode(): Int {
        var result = argb.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Deterministic synthetic cover-art fixtures covering edge cases in dominant color
 * and palette extraction (white covers with small colored logos, transparent borders,
 * grayscale/black covers, saturated primaries, and multi-region covers).
 */
object ArtworkFixtures {

    const val DEFAULT_SIZE = 128

    const val NAME_WHITE = "white"
    const val NAME_NEAR_WHITE_PASTEL = "near_white_pastel"
    const val NAME_WHITE_BLUE_LOGO = "white_blue_logo"
    const val NAME_GRAY = "gray"
    const val NAME_BLACK = "black"
    const val NAME_RED = "red"
    const val NAME_BLUE = "blue"
    const val NAME_MULTICOLOR = "multicolor"
    const val NAME_TRANSPARENT_EDGE = "transparent_edge"

    // Expected dominant hue family per fixture (documentation & test oracle):
    // white, white, white, gray, black, red, blue, multicolor-largest, centerColor.
    const val EXPECTED_HUE_WHITE = "white"
    const val EXPECTED_HUE_NEAR_WHITE_PASTEL = "white"
    const val EXPECTED_HUE_WHITE_BLUE_LOGO = "white"
    const val EXPECTED_HUE_GRAY = "gray"
    const val EXPECTED_HUE_BLACK = "black"
    const val EXPECTED_HUE_RED = "red"
    const val EXPECTED_HUE_BLUE = "blue"
    const val EXPECTED_HUE_MULTICOLOR = "green" // ~55% green vs ~15% red / blue / amber
    const val EXPECTED_HUE_TRANSPARENT_EDGE = "purple" // center color 0xFF8E24AA

    val EXPECTED_HUE_FAMILIES: Map<String, String> = linkedMapOf(
        NAME_WHITE to EXPECTED_HUE_WHITE,
        NAME_NEAR_WHITE_PASTEL to EXPECTED_HUE_NEAR_WHITE_PASTEL,
        NAME_WHITE_BLUE_LOGO to EXPECTED_HUE_WHITE_BLUE_LOGO,
        NAME_GRAY to EXPECTED_HUE_GRAY,
        NAME_BLACK to EXPECTED_HUE_BLACK,
        NAME_RED to EXPECTED_HUE_RED,
        NAME_BLUE to EXPECTED_HUE_BLUE,
        NAME_MULTICOLOR to EXPECTED_HUE_MULTICOLOR,
        NAME_TRANSPARENT_EDGE to EXPECTED_HUE_TRANSPARENT_EDGE,
    )

    val COLOR_WHITE = 0xFFFFFFFF.toInt()
    val COLOR_GRAY = 0xFF808080.toInt()
    val COLOR_BLACK = 0xFF000000.toInt()
    val COLOR_RED = 0xFFE61919.toInt()
    val COLOR_BLUE = 0xFF195AE6.toInt()
    val COLOR_LOGO_BLUE = 0xFF1565C0.toInt()
    val COLOR_MULTI_GREEN = 0xFF22A84A.toInt()
    val COLOR_MULTI_RED = 0xFFE62E2E.toInt()
    val COLOR_MULTI_BLUE = 0xFF2662E6.toInt()
    val COLOR_MULTI_AMBER = 0xFFE6B819.toInt()
    const val COLOR_TRANSPARENT = 0x00000000
    val COLOR_CENTER_PURPLE = 0xFF8E24AA.toInt()

    fun white(size: Int = DEFAULT_SIZE): ArtworkPixels = solid(size, COLOR_WHITE)

    fun nearWhitePastel(size: Int = DEFAULT_SIZE): ArtworkPixels {
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = 247 + (x * 6) / size
                val g = 245 + (y * 6) / size
                val b = 250 + ((x + y) * 5) / (2 * size)
                pixels[y * size + x] = packArgb(255, r, g, b)
            }
        }
        return ArtworkPixels(pixels, size, size)
    }

    /** Bug case: mostly white cover (~97%) with a small centered blue logo (~3%). */
    fun whiteBlueLogo(size: Int = DEFAULT_SIZE): ArtworkPixels {
        val pixels = IntArray(size * size) { COLOR_WHITE }
        val logoSide = maxOf(1, sqrt(size * size * 0.03).roundToInt())
        val start = (size - logoSide) / 2
        val end = start + logoSide
        for (y in start until end) {
            for (x in start until end) {
                pixels[y * size + x] = COLOR_LOGO_BLUE
            }
        }
        return ArtworkPixels(pixels, size, size)
    }

    fun gray(size: Int = DEFAULT_SIZE): ArtworkPixels = solid(size, COLOR_GRAY)

    fun black(size: Int = DEFAULT_SIZE): ArtworkPixels = solid(size, COLOR_BLACK)

    fun red(size: Int = DEFAULT_SIZE): ArtworkPixels = solid(size, COLOR_RED)

    fun blue(size: Int = DEFAULT_SIZE): ArtworkPixels = solid(size, COLOR_BLUE)

    /** Four distinct hues in separate regions; green top band (~55%) is clearly largest. */
    fun multicolor(size: Int = DEFAULT_SIZE): ArtworkPixels {
        val pixels = IntArray(size * size)
        val splitY = (size * 55) / 100
        val thirdX = size / 3
        val twoThirdX = (size * 2) / 3
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = when {
                    y < splitY -> COLOR_MULTI_GREEN
                    x < thirdX -> COLOR_MULTI_RED
                    x < twoThirdX -> COLOR_MULTI_BLUE
                    else -> COLOR_MULTI_AMBER
                }
            }
        }
        return ArtworkPixels(pixels, size, size)
    }

    /** Transparent border (75% alpha == 0) around a visible opaque colored center (25%). */
    fun transparentEdge(size: Int = DEFAULT_SIZE): ArtworkPixels {
        val pixels = IntArray(size * size) { COLOR_TRANSPARENT }
        val start = size / 4
        val end = (size * 3) / 4
        for (y in start until end) {
            for (x in start until end) {
                pixels[y * size + x] = COLOR_CENTER_PURPLE
            }
        }
        return ArtworkPixels(pixels, size, size)
    }

    val ALL: Map<String, ArtworkPixels> = linkedMapOf(
        NAME_WHITE to white(),
        NAME_NEAR_WHITE_PASTEL to nearWhitePastel(),
        NAME_WHITE_BLUE_LOGO to whiteBlueLogo(),
        NAME_GRAY to gray(),
        NAME_BLACK to black(),
        NAME_RED to red(),
        NAME_BLUE to blue(),
        NAME_MULTICOLOR to multicolor(),
        NAME_TRANSPARENT_EDGE to transparentEdge(),
    )

    private fun solid(size: Int, argb: Int): ArtworkPixels =
        ArtworkPixels(IntArray(size * size) { argb }, size, size)

    private fun packArgb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or
            ((r and 0xFF) shl 16) or
            ((g and 0xFF) shl 8) or
            (b and 0xFF)
}
