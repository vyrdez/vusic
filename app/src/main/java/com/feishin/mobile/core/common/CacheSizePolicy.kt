package com.feishin.mobile.core.common

/**
 * Pure stream-cache size policy. Media3's LRU evictor has a fixed capacity, so
 * the app picks one of the supported sizes and applies it when the cache is
 * built (documented in PLAN.md: the new size takes effect on next launch).
 */
object CacheSizePolicy {
    /** Supported stream-cache sizes in MB. */
    val STREAM_SIZES_MB = listOf(256, 512, 1024, 2048, 5120)

    /** Supported image-cache sizes in MB. */
    val IMAGE_SIZES_MB = listOf(100, 250, 500, 1024)

    const val DEFAULT_STREAM_MB = 512
    const val DEFAULT_IMAGE_MB = 250

    /** Snaps an arbitrary stored value to the nearest supported option. */
    fun normalizeStream(mb: Int): Int = nearest(mb, STREAM_SIZES_MB, DEFAULT_STREAM_MB)

    fun normalizeImage(mb: Int): Int = nearest(mb, IMAGE_SIZES_MB, DEFAULT_IMAGE_MB)

    fun bytes(mb: Int): Long = mb.toLong() * 1024 * 1024

    /**
     * True when a download of [sizeBytes] would exceed the free space left after
     * keeping [reserveBytes] in hand. Used to pause instead of filling the disk.
     */
    fun fits(sizeBytes: Long, freeBytes: Long, reserveBytes: Long = 64L * 1024 * 1024): Boolean =
        freeBytes - sizeBytes >= reserveBytes

    /** Human-readable size, e.g. "1.4 GB". */
    fun human(sizeBytes: Long): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val kb = sizeBytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun nearest(mb: Int, options: List<Int>, fallback: Int): Int =
        options.minByOrNull { kotlin.math.abs(it - mb) } ?: fallback
}
