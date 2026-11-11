package com.feishin.mobile.core.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Downscaled (and on API < 31, pre-blurred) artwork entry for [BlurredArtworkBackground].
 *
 * @property coverId cache key identifying the cover artwork.
 * @property bitmap low-resolution (64-128px) [Bitmap]: raw downscaled on API 31+
 *           (where hardware `RenderEffect` blurs the small layer), or pre-blurred
 *           on API < 31 via [ArtworkBlurCache.boxBlur].
 * @property avgLuminance mean WCAG relative luminance (0..1) of the artwork,
 *           computed once off the main thread for [BlurScrim.scrimAlpha].
 * @property isPreBlurred true when [bitmap] already has software blur baked in (API < 31).
 */
data class BlurredArtwork(
    val coverId: String,
    val bitmap: Bitmap,
    val avgLuminance: Float,
    val isPreBlurred: Boolean,
)

/**
 * Small per-cover-id LRU cache of low-resolution (64-128px) artwork bitmaps.
 *
 * - API 31+: keeps the downscaled source bitmap so the composable can apply a
 *   hardware `Modifier.blur` on the small layer.
 * - API < 31: runs a 3-pass separable box blur (approximating a Gaussian blur
 *   equivalent to ~60-80dp when scaled to screen size) on a background dispatcher.
 */
object ArtworkBlurCache {

    /** Maximum cached covers (each ~96x96 ARGB_8888 is ~36 KB, so 24 entries < 1 MB). */
    const val MAX_ENTRIES = 24

    /** Target longest edge in pixels for the downscaled bitmap (within 64..128px). */
    const val TARGET_SIZE_PX = 96

    /**
     * Software blur radius in pixels on the [TARGET_SIZE_PX] bitmap.
     * At 96px scaled up ~4-5x to ~400dp screen width, radius 16px corresponds to ~64-80dp.
     */
    const val LEGACY_BLUR_RADIUS_PX = 16

    private val cache = object : LruCache<String, BlurredArtwork>(MAX_ENTRIES) {}

    @Synchronized
    fun peek(coverId: String): BlurredArtwork? = cache.get(coverId)

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    /**
     * Returns the cached [BlurredArtwork] for [coverId], or produces and caches it
     * off the main thread on [dispatcher] once.
     */
    suspend fun get(
        coverId: String,
        source: Bitmap,
        sdkInt: Int = Build.VERSION.SDK_INT,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): BlurredArtwork {
        peek(coverId)?.let { return it }
        val computed = withContext(dispatcher) {
            produce(coverId, source, sdkInt)
        }
        synchronized(this) {
            cache.get(coverId)?.let { return it }
            cache.put(coverId, computed)
        }
        return computed
    }

    private fun produce(coverId: String, source: Bitmap, sdkInt: Int): BlurredArtwork {
        val (dstW, dstH) = targetDimensions(source.width, source.height, TARGET_SIZE_PX)
        val softwareSrc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else {
            source
        }
        val scaled = if (softwareSrc.width == dstW && softwareSrc.height == dstH) {
            softwareSrc.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(softwareSrc, dstW, dstH, true)
        }
        val pixels = IntArray(dstW * dstH)
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        val avgLum = BlurScrim.averageLuminance(pixels)

        return if (sdkInt >= Build.VERSION_CODES.S) {
            BlurredArtwork(
                coverId = coverId,
                bitmap = scaled,
                avgLuminance = avgLum,
                isPreBlurred = false,
            )
        } else {
            val blurredPixels = boxBlur(pixels, dstW, dstH, LEGACY_BLUR_RADIUS_PX, passes = 3)
            val outBitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(blurredPixels, 0, dstW, 0, 0, dstW, dstH)
            BlurredArtwork(
                coverId = coverId,
                bitmap = outBitmap,
                avgLuminance = avgLum,
                isPreBlurred = true,
            )
        }
    }

    /**
     * Pure helper: computes downscaled `(width, height)` preserving aspect ratio
     * with the longest edge clamped into `64..128` (targeting [targetMaxEdge]).
     */
    fun targetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        targetMaxEdge: Int = TARGET_SIZE_PX,
    ): Pair<Int, Int> {
        val maxEdge = targetMaxEdge.coerceIn(64, 128)
        if (srcWidth <= 0 || srcHeight <= 0) return maxEdge to maxEdge
        return if (srcWidth >= srcHeight) {
            val w = maxEdge
            val h = ((srcHeight.toFloat() / srcWidth.toFloat()) * w).roundToInt().coerceAtLeast(1)
            w to h
        } else {
            val h = maxEdge
            val w = ((srcWidth.toFloat() / srcHeight.toFloat()) * h).roundToInt().coerceAtLeast(1)
            w to h
        }
    }

    /**
     * Pure JVM multi-pass separable box blur over packed `0xAARRGGBB` [pixels].
     *
     * Three passes of a box filter of [radius] closely approximate a Gaussian
     * kernel in `O(W * H)` time independent of radius. Clamps edge pixels so
     * transparent borders never bleed in.
     */
    fun boxBlur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int = LEGACY_BLUR_RADIUS_PX,
        passes: Int = 3,
    ): IntArray {
        if (width <= 0 || height <= 0 || pixels.size < width * height || radius <= 0 || passes <= 0) {
            return pixels.copyOf()
        }
        val r = radius.coerceAtMost(max(width, height))
        var src = pixels.copyOf()
        var dst = IntArray(width * height)
        val div = 2 * r + 1

        repeat(passes) {
            // Horizontal pass: src -> dst
            for (y in 0 until height) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                val rowOffset = y * width
                for (i in -r..r) {
                    val px = src[rowOffset + i.coerceIn(0, width - 1)]
                    rSum += (px ushr 16) and 0xFF
                    gSum += (px ushr 8) and 0xFF
                    bSum += px and 0xFF
                }
                for (x in 0 until width) {
                    dst[rowOffset + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    val outIdx = max(x - r, 0)
                    val inIdx = min(x + r + 1, width - 1)
                    val pOut = src[rowOffset + outIdx]
                    val pIn = src[rowOffset + inIdx]
                    rSum += ((pIn ushr 16) and 0xFF) - ((pOut ushr 16) and 0xFF)
                    gSum += ((pIn ushr 8) and 0xFF) - ((pOut ushr 8) and 0xFF)
                    bSum += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
            // Vertical pass: dst -> src
            for (x in 0 until width) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (i in -r..r) {
                    val py = i.coerceIn(0, height - 1)
                    val px = dst[py * width + x]
                    rSum += (px ushr 16) and 0xFF
                    gSum += (px ushr 8) and 0xFF
                    bSum += px and 0xFF
                }
                for (y in 0 until height) {
                    src[y * width + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                    val outIdx = max(y - r, 0)
                    val inIdx = min(y + r + 1, height - 1)
                    val pOut = dst[outIdx * width + x]
                    val pIn = dst[inIdx * width + x]
                    rSum += ((pIn ushr 16) and 0xFF) - ((pOut ushr 16) and 0xFF)
                    gSum += ((pIn ushr 8) and 0xFF) - ((pOut ushr 8) and 0xFF)
                    bSum += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
        }
        return src
    }
}
