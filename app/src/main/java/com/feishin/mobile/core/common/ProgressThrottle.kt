package com.feishin.mobile.core.common

/**
 * Rate limiter for per-chunk download side effects (Room writes, foreground
 * notification updates).
 *
 * A download reports progress for every 64 KB chunk. A 5 MB song is ~80
 * reports, so two concurrent songs can produce ~160 Room writes and ~160
 * notification builds per few seconds. Each write invalidates the downloads
 * flow and recomposes the screens observing it, and each notification build
 * is a binder call: that is what made "keep downloaded" janky.
 *
 * [shouldEmit] answers "is this update worth publishing?":
 *  - at most one emit per [minIntervalMs] overall (so a flood is capped), and
 *  - only when the percentage moved by [minStepPct] for the same song, or the
 *    song changed (so two songs at 40% do not block each other).
 *
 * State is tiny and guarded, so it is safe from the concurrent transfer
 * coroutines. Callers must only call it when they intend to act on a `true`.
 */
class ProgressThrottle(
    private val minIntervalMs: Long,
    private val minStepPct: Int = 1,
) {
    private var lastAt = Long.MIN_VALUE
    private var lastPct = -1
    private var lastKey: String? = null

    @Synchronized
    fun shouldEmit(key: String, pct: Int, nowMs: Long): Boolean {
        if (key == lastKey && pct - lastPct < minStepPct) return false
        if (lastAt != Long.MIN_VALUE && nowMs - lastAt < minIntervalMs) return false
        lastAt = nowMs
        lastPct = pct
        lastKey = key
        return true
    }
}
