package com.feishin.mobile.core.common

/**
 * Seek-bar math, kept pure so the tricky cases are unit-testable.
 *
 * The bug this replaces: the player exposes an unknown duration as 0, the range
 * collapsed to `0f..1f`, and the position saturated to 1f — so the bar rendered
 * permanently full and the remaining time read `-0:00`.
 */
object PlaybackProgress {
    /** Media3 `C.TIME_UNSET`. */
    const val TIME_UNSET = -9223372036854775807L
    const val UNKNOWN_CLOCK = "--:--"

    /** Durations <= 0 (or TIME_UNSET) mean "not known yet". */
    fun isUnknown(durationMs: Long): Boolean = durationMs <= 0L || durationMs == TIME_UNSET

    /** 0f..1f; 0f whenever the duration is unknown (never 1f). */
    fun progress(positionMs: Long, durationMs: Long): Float {
        if (isUnknown(durationMs)) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /** Progress while the user drags: the scrub target wins, clamped like above. */
    fun progressWithScrub(positionMs: Long, durationMs: Long, scrubMs: Long?): Float =
        progress(scrubMs ?: positionMs, durationMs)

    /** Time left, never negative and never larger than the duration. */
    fun remainingMs(positionMs: Long, durationMs: Long, scrubMs: Long? = null): Long {
        if (isUnknown(durationMs)) return 0L
        val p = (scrubMs ?: positionMs).coerceIn(0L, durationMs)
        return durationMs - p
    }

    /** Elapsed clock, `0:00` at or below zero. */
    fun formatElapsed(ms: Long): String = formatClock(ms.coerceAtLeast(0L))

    /** Remaining clock, `--:--` when the duration is unknown. */
    fun formatRemaining(positionMs: Long, durationMs: Long, scrubMs: Long? = null): String =
        if (isUnknown(durationMs)) UNKNOWN_CLOCK else formatClock(remainingMs(positionMs, durationMs, scrubMs))

    fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return if (m >= 60) {
            val h = m / 60
            "%d:%02d:%02d".format(h, m % 60, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
}
