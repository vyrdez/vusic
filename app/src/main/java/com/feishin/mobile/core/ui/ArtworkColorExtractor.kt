package com.feishin.mobile.core.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-JVM dominant-cluster color extractor for album artwork.
 *
 * Unlike `androidx.palette.graphics.Palette` (which prefers vibrant swatches over
 * dominant areas), this extractor picks the most-used color cluster across the
 * image without penalizing neutral colors (white, near-white, gray, black).
 *
 * Zero `android.*` imports so unit tests can invoke it directly on the JVM.
 */
object ArtworkColorExtractor {

    /** Maximum perceptual distance (CIELAB Delta E*ab) from a cluster's peak leader. */
    const val CLUSTER_MERGE_DELTA_E = 12.0

    /** Minimum alpha (0..255) for a pixel to be considered non-transparent. */
    const val MIN_OPAQUE_ALPHA = 128

    private const val HIST_SIZE = 32768

    private class HistogramScratch {
        val counts = IntArray(HIST_SIZE)
        val sumR = LongArray(HIST_SIZE)
        val sumG = LongArray(HIST_SIZE)
        val sumB = LongArray(HIST_SIZE)
        val touched = IntArray(HIST_SIZE)
        var touchedCount = 0

        fun clearTouched() {
            for (i in 0 until touchedCount) {
                val idx = touched[i]
                counts[idx] = 0
                sumR[idx] = 0L
                sumG[idx] = 0L
                sumB[idx] = 0L
            }
            touchedCount = 0
        }
    }

    private val threadScratch = object : ThreadLocal<HistogramScratch>() {
        override fun initialValue(): HistogramScratch = HistogramScratch()
    }

    private class Bucket(
        val key: Int,
        val count: Int,
        val sumR: Long,
        val sumG: Long,
        val sumB: Long,
        val l: Double,
        val a: Double,
        val b: Double,
    )

    private class Cluster(
        val leaderKey: Int,
        val leaderL: Double,
        val leaderA: Double,
        val leaderB: Double,
        var count: Int,
        var sumR: Long,
        var sumG: Long,
        var sumB: Long,
    ) {
        fun meanArgbLong(): Long {
            val r = (sumR.toDouble() / count).roundToInt().coerceIn(0, 255)
            val g = (sumG.toDouble() / count).roundToInt().coerceIn(0, 255)
            val b = (sumB.toDouble() / count).roundToInt().coerceIn(0, 255)
            return Contrast.argb(255, r, g, b)
        }
    }

    /**
     * Extracts the most-used color from [pixels] (`width * height` packed ARGB ints)
     * as a packed `0xFFRRGGBBL` Long, or `null` if no opaque pixels exist.
     */
    fun extractSourceColor(pixels: IntArray, width: Int, height: Int): Long? {
        if (pixels.isEmpty() || width <= 0 || height <= 0 || pixels.size < width * height) {
            return null
        }

        val stepX = if (width > 128) max(1, width / 96) else 1
        val stepY = if (height > 128) max(1, height / 96) else 1

        val scratch = threadScratch.get() ?: HistogramScratch()
        scratch.clearTouched()
        val counts = scratch.counts
        val sumR = scratch.sumR
        val sumG = scratch.sumG
        val sumB = scratch.sumB
        val touched = scratch.touched

        var y = 0
        while (y < height) {
            val rowOffset = y * width
            var x = 0
            while (x < width) {
                val argb = pixels[rowOffset + x]
                val alpha = argb ushr 24
                if (alpha >= MIN_OPAQUE_ALPHA) {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    val key = ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)
                    if (counts[key] == 0) {
                        touched[scratch.touchedCount++] = key
                    }
                    counts[key] += 1
                    sumR[key] += r.toLong()
                    sumG[key] += g.toLong()
                    sumB[key] += b.toLong()
                }
                x += stepX
            }
            y += stepY
        }

        val activeCount = scratch.touchedCount
        if (activeCount == 0) {
            return null
        }

        val buckets = ArrayList<Bucket>(activeCount)
        for (i in 0 until activeCount) {
            val key = touched[i]
            val c = counts[key]
            val sr = sumR[key]
            val sg = sumG[key]
            val sb = sumB[key]
            val mr = (sr.toDouble() / c).roundToInt().coerceIn(0, 255)
            val mg = (sg.toDouble() / c).roundToInt().coerceIn(0, 255)
            val mb = (sb.toDouble() / c).roundToInt().coerceIn(0, 255)
            val meanArgb = Contrast.argb(255, mr, mg, mb)
            val (l, a, b) = Contrast.toLab(meanArgb)
            buckets.add(Bucket(key, c, sr, sg, sb, l, a, b))
        }
        scratch.clearTouched()

        // Sort descending by pixel count; deterministic tie-break on lower bucket key.
        buckets.sortWith { a, b ->
            val cmp = b.count.compareTo(a.count)
            if (cmp != 0) cmp else a.key.compareTo(b.key)
        }

        // Greedy clustering against each cluster's initial peak leader bucket
        // (Delta E*ab <= 12.0), preventing centroid drift across gradients.
        val clusters = ArrayList<Cluster>()
        for (i in 0 until buckets.size) {
            val b = buckets[i]
            var bestCluster: Cluster? = null
            var bestDist = Double.MAX_VALUE
            for (j in 0 until clusters.size) {
                val cl = clusters[j]
                val dist = Contrast.deltaE(b.l, b.a, b.b, cl.leaderL, cl.leaderA, cl.leaderB)
                if (dist <= CLUSTER_MERGE_DELTA_E && dist < bestDist) {
                    bestDist = dist
                    bestCluster = cl
                }
            }
            if (bestCluster != null) {
                bestCluster.count += b.count
                bestCluster.sumR += b.sumR
                bestCluster.sumG += b.sumG
                bestCluster.sumB += b.sumB
            } else {
                clusters.add(
                    Cluster(
                        leaderKey = b.key,
                        leaderL = b.l,
                        leaderA = b.a,
                        leaderB = b.b,
                        count = b.count,
                        sumR = b.sumR,
                        sumG = b.sumG,
                        sumB = b.sumB,
                    ),
                )
            }
        }

        // Winner selection: largest pixel count.
        // Deterministic tie-break: (1) higher floor(L* * 100), (2) lower packed 0xFFRRGGBB.
        var winner = clusters[0]
        var winnerArgb = winner.meanArgbLong()
        var winnerLightnessKey = floor(Contrast.toneOf(winnerArgb) * 100.0).toInt()

        for (i in 1 until clusters.size) {
            val cand = clusters[i]
            if (cand.count > winner.count) {
                winner = cand
                winnerArgb = cand.meanArgbLong()
                winnerLightnessKey = floor(Contrast.toneOf(winnerArgb) * 100.0).toInt()
            } else if (cand.count == winner.count) {
                val candArgb = cand.meanArgbLong()
                val candLightnessKey = floor(Contrast.toneOf(candArgb) * 100.0).toInt()
                val candWins = when {
                    candLightnessKey != winnerLightnessKey -> candLightnessKey > winnerLightnessKey
                    else -> candArgb < winnerArgb
                }
                if (candWins) {
                    winner = cand
                    winnerArgb = candArgb
                    winnerLightnessKey = candLightnessKey
                }
            }
        }

        return winnerArgb
    }

    /** Convenience overload accepting an [ArtworkPixels] fixture directly. */
    fun extractSourceColor(artwork: ArtworkPixels): Long? =
        extractSourceColor(artwork.argb, artwork.width, artwork.height)

    /**
     * Pure-JVM Int-returning entry point `(argb: IntArray, width: Int, height: Int) -> Int`
     * returning the packed `0xFFRRGGBB` Int (or `0` if all pixels are transparent).
     */
    fun extractMostUsedColor(argb: IntArray, width: Int, height: Int): Int =
        extractSourceColor(argb, width, height)?.toInt() ?: 0
}
