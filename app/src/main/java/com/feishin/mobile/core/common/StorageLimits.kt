package com.feishin.mobile.core.common

/**
 * Pure storage-limit policy for the cache/download sliders.
 *
 * A linear 256 MB .. 500 GB slider is unusable, so the UI offers discrete,
 * human-sized steps and always includes the device maximum as the last step.
 * Anything that reads the device (StatFs) is injected, so this stays testable.
 */
object StorageLimits {
    const val MB = 1024L * 1024
    const val GB = 1024L * MB

    /** Fallback maximum when the device size cannot be read. */
    const val DEFAULT_MAX_BYTES = 100L * GB

    /** Caching and prefetch pause below this much free space. */
    const val LOW_SPACE_BYTES = 1L * GB

    /** Human-sized steps, ascending. */
    private val NOMINAL = listOf(
        256L * MB, 512L * MB,
        1L * GB, 2L * GB, 5L * GB, 10L * GB, 20L * GB, 50L * GB,
        100L * GB, 200L * GB, 500L * GB, 1000L * GB, 2000L * GB,
    )

    /**
     * The device's total data-partition size, or [DEFAULT_MAX_BYTES] when the
     * read fails or returns something implausible (< 1 GB).
     */
    fun deviceMaxBytes(readTotal: () -> Long): Long {
        val total = runCatching { readTotal() }.getOrDefault(0L)
        return if (total in (1L * GB)..(8000L * GB)) total else DEFAULT_MAX_BYTES
    }

    /**
     * Discrete slider steps up to [maxBytes], always ending with the exact
     * maximum (so the user can pick "all of it") and always non-empty.
     */
    fun steps(maxBytes: Long): List<Long> {
        val capped = maxBytes.coerceAtLeast(64L * MB)
        val out = ArrayList<Long>()
        for (step in NOMINAL) if (step < capped) out += step
        // Always include the exact maximum as the last step.
        if (out.isEmpty() || out.last() != capped) out += capped
        return out
    }

    /**
     * Slider steps for a cache whose supported sizes are [supportedBytes],
     * bounded by [deviceMaxBytes].
     *
     * Why this is separate from [steps]: a cache is only ever built at one of
     * its supported sizes, so a rail built from the raw device steps used to
     * offer sizes the cache silently rejected - dragging a 512 GB rail past
     * 5 GB snapped straight back to 5 GB (the "5 GB plateau"). Intersecting the
     * two keeps every step settable and never offers more than the device holds.
     *
     * Never empty: a device smaller than the smallest supported size falls back
     * to that single smallest size, since no positive value would fit anyway.
     */
    fun cacheSteps(supportedBytes: List<Long>, deviceMaxBytes: Long): List<Long> {
        val kept = supportedBytes.filter { it <= deviceMaxBytes.coerceAtLeast(0L) }
        return kept.ifEmpty { listOfNotNull(supportedBytes.minOrNull()) }
    }

    /** The step nearest to [value] (used to snap a stored value back onto the rail). */
    fun nearestStep(steps: List<Long>, value: Long): Long =
        steps.minByOrNull { kotlin.math.abs(it - value) } ?: value

    /** Slider index for a byte value on [steps]. */
    fun indexOf(steps: List<Long>, value: Long): Int =
        steps.indexOf(nearestStep(steps, value)).coerceAtLeast(0)

    /** Human label: "256 MB", "8.0 GB", "100 GB". */
    fun label(bytes: Long): String {
        if (bytes < MB) return "${bytes / 1024} KB"
        if (bytes < GB) return "${bytes / MB} MB"
        val gb = bytes.toDouble() / GB
        return if (gb >= 10) "%.0f GB".format(gb) else "%.1f GB".format(gb)
    }

    /**
     * True when [limitBytes] cannot be honoured even now: the limit is bigger
     * than what is already used plus what is free (i.e. bigger than the device).
     */
    fun exceedsAvailable(limitBytes: Long, usedBytes: Long, freeBytes: Long): Boolean =
        usedBytes >= 0 && freeBytes >= 0 && limitBytes > usedBytes + freeBytes

    /**
     * The warning to show under the song-cache slider, or null when the choice
     * fits. Pure so the decision is testable without a device.
     *
     * A limit larger than "what the cache already holds plus what is free" is
     * asking for more disk than the phone has. The choice is still allowed -
     * the user may be about to free space, and a hard block would be a worse
     * failure than a useless setting.
     */
    fun cacheSpaceWarning(
        limitBytes: Long,
        usedBytes: Long,
        freeBytes: Long,
        human: (Long) -> String,
    ): String? =
        if (!exceedsAvailable(limitBytes, usedBytes, freeBytes)) null
        else "Only ${human(freeBytes.coerceAtLeast(0L))} is free on this phone. " +
            "The cache can't grow past what's free."

    /**
     * The ceiling the cache may actually reach, given real free space.
     *
     * Above [LOW_SPACE_BYTES] this is just the user's limit. Below it, the
     * cache is held at the size it already is: new songs are no longer added,
     * which is what keeps the phone from filling up. Nothing is deleted - not
     * downloads, not even cached songs the user might be listening to right
     * now - so playback is never interrupted. Downloads and prefetch have their
     * own gates on the same reserve.
     */
    fun effectiveCacheLimit(limitBytes: Long, usedBytes: Long, freeBytes: Long): Long {
        if (limitBytes <= 0L) return 0L
        if (freeBytes >= LOW_SPACE_BYTES) return limitBytes
        return minOf(limitBytes, usedBytes.coerceAtLeast(0L))
    }

    /** Caching/prefetch are allowed only above the low-space floor. */
    fun cachingAllowed(freeBytes: Long): Boolean = freeBytes >= LOW_SPACE_BYTES

    val LOW_SPACE_MESSAGE = "Caching paused: less than 1 GB of free space."
}
