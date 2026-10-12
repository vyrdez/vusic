package com.feishin.mobile.core.network

/**
 * Scrobble submission rule (MediaWiki / last.fm convention):
 * submit once the track has played 50% **or** 4 minutes, whichever comes first.
 *
 * Progress is measured as the furthest *continuously played* position, so
 * seeking ahead (a "seek-jump") never triggers a submission. The policy is
 * pure so the timing rules are unit-testable without a player.
 */
class ScrobblePolicy(
    private val fourMinutesMs: Long = 4 * 60 * 1000L,
    /** Max plausible progress advance per update; larger deltas are treated as seeks. */
    private val maxPlausibleJumpMs: Long = 3_000L,
) {
    private var trackId: String? = null
    private var furthestPlayedMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var submitted: Boolean = false

    /** Threshold for the current track: 50% of duration or 4 min, whichever is smaller. */
    fun thresholdMs(durationMs: Long): Long {
        if (durationMs <= 0) return fourMinutesMs
        val half = durationMs / 2
        return minOf(half, fourMinutesMs)
    }

    /** True when the current track already counts as played (e.g. ended). */
    fun shouldSubmit(durationMs: Long, playedMs: Long = furthestPlayedMs): Boolean =
        !submitted && playedMs >= thresholdMs(durationMs)

    /**
     * Feeds a position update. Returns true exactly once per track, at the
     * moment the submission rule is first satisfied.
     */
    fun onProgress(id: String, positionMs: Long, durationMs: Long, isPlaying: Boolean): Boolean {
        if (trackId != id) {
            trackId = id
            furthestPlayedMs = 0L
            lastPositionMs = 0L
            submitted = false
        }
        if (isPlaying) {
            val delta = positionMs - lastPositionMs
            // A large forward jump means the user seeked: do not count it as played time.
            if (delta in 0..maxPlausibleJumpMs) {
                if (positionMs > furthestPlayedMs) furthestPlayedMs = positionMs
            }
        }
        lastPositionMs = positionMs
        if (shouldSubmit(durationMs)) {
            submitted = true
            return true
        }
        return false
    }

    /** True once a submission has been sent for the current track. */
    fun isSubmitted(): Boolean = submitted

    /** Marks the current track as finished (natural end) and submits if not already. */
    fun onTrackEnded(id: String, durationMs: Long): Boolean {
        if (trackId != id) {
            trackId = id
            furthestPlayedMs = 0L
            submitted = false
        }
        if (submitted) return false
        submitted = true
        furthestPlayedMs = maxOf(furthestPlayedMs, durationMs)
        return true
    }

    fun reset() {
        trackId = null
        furthestPlayedMs = 0L
        lastPositionMs = 0L
        submitted = false
    }
}