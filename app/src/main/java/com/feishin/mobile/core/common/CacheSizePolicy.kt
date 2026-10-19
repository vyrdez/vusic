package com.feishin.mobile.core.common

/**
 * Pure stream-cache size policy. Media3's LRU evictor has a fixed capacity, so
 * the app picks one of the supported sizes and applies it when the cache is
 * built (documented in PLAN.md: the new size takes effect on next launch).
 */
object CacheSizePolicy {
    /**
     * Supported stream-cache sizes in MB.
     *
     * This table IS the song-cache slider: [StorageLimits.cacheSteps] can only
     * offer a size the cache is willing to hold, and [normalizeStream] snaps
     * anything else onto it. Phase 8 raised the slider to 5 GB by adding
     * 5120 here and left the table itself at five entries, which is why the
     * limit was still 5 GB afterwards - the slider was never the thing holding
     * it down.
     *
     * Ascending, ends exactly on 100 GB so the slider's full-travel position is
     * 100 GB. Fine at the bottom (a phone that only streams a little should not
     * have to jump to 1 GB steps), 1 GB steps through the range a real library
     * lands in, and coarser above 10 GB where the steps stop mattering.
     */
    val STREAM_SIZES_MB = listOf(
        256, 512,
        1024, 2048, 3072, 4096, 5120,
        6144, 8192, 10240,
        15360, 20480, 30720, 40960, 51200,
        76800, 102400,
    )

    /** The largest supported stream cache, in MB (100 GB). */
    const val MAX_STREAM_MB = 102400

    /** Supported image-cache sizes in MB. */
    val IMAGE_SIZES_MB = listOf(100, 250, 500, 1024)

    const val DEFAULT_STREAM_MB = 512
    const val DEFAULT_IMAGE_MB = 250

    /** Snaps an arbitrary stored value to the nearest supported option. */
    fun normalizeStream(mb: Int): Int = nearest(mb, STREAM_SIZES_MB, DEFAULT_STREAM_MB)
    fun normalizeImage(mb: Int): Int = nearest(mb, IMAGE_SIZES_MB, DEFAULT_IMAGE_MB)

    fun bytes(mb: Int): Long = mb.toLong() * 1024 * 1024

    /**
     * The supported stream-cache sizes in bytes, for the settings slider.
     *
     * The slider must offer exactly these: [normalizeStream] snaps anything else
     * back onto this set, so a rail with other values would silently bounce
     * under the user's finger.
     */
    fun streamStepBytes(): List<Long> = STREAM_SIZES_MB.map { bytes(it) }

    /** The supported image-cache sizes in bytes, for the settings slider. */
    fun imageStepBytes(): List<Long> = IMAGE_SIZES_MB.map { bytes(it) }

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
