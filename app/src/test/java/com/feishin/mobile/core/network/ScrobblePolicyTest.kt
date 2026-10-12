package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobblePolicyTest {

    @Test
    fun threshold_isHalfOfDurationForShortTracks() {
        val p = ScrobblePolicy()
        // 3 min track -> 50% = 90s wins over the 4-minute rule.
        assertEquals(90_000L, p.thresholdMs(180_000L))
    }

    @Test
    fun threshold_isFourMinutesForLongTracks() {
        val p = ScrobblePolicy()
        // 10 min track -> 4 minutes wins over 50%.
        assertEquals(240_000L, p.thresholdMs(600_000L))
    }

    @Test
    fun threshold_unknownDuration_fallsBackToFourMinutes() {
        assertEquals(240_000L, ScrobblePolicy().thresholdMs(0L))
    }

    @Test
    fun submitsOnceAfterHalfPlayed() {
        val p = ScrobblePolicy()
        var fired = false
        for (pos in 0..100_000 step 1_000) {
            fired = p.onProgress("t1", pos.toLong(), 180_000L, isPlaying = true)
            if (fired) break
        }
        assertTrue("should submit at ~50% of 180s", fired)
        // Never fires again for the same track.
        assertFalse(p.onProgress("t1", 120_000L, 180_000L, isPlaying = true))
        assertFalse(p.onProgress("t1", 150_000L, 180_000L, isPlaying = true))
    }

    @Test
    fun longTrackSubmitsAtFourMinutes_notAtHalf() {
        val p = ScrobblePolicy()
        // Half of 20 min is 10 min; the 4-minute rule must win.
        assertFalse(p.onProgress("t1", 240_000L, 1_200_000L, isPlaying = true) &&
            p.onProgress("t1", 241_000L, 1_200_000L, isPlaying = true))
        // Walk up to 4 minutes in 1s steps.
        var fired = false
        for (pos in 0..240_000 step 1_000) {
            if (p.onProgress("t1", pos.toLong(), 1_200_000L, isPlaying = true)) { fired = true; break }
        }
        assertTrue(fired)
    }

    @Test
    fun seekJumpDoesNotSubmit() {
        val p = ScrobblePolicy()
        // Play a moment, then seek straight to the end.
        p.onProgress("t1", 2_000L, 180_000L, isPlaying = true)
        val submitted = p.onProgress("t1", 170_000L, 180_000L, isPlaying = true)
        assertFalse("a seek must not count as played time", submitted)
        assertFalse(p.isSubmitted())
        // Continuing normally from there still counts from where playback got to.
        assertTrue(p.onProgress("t1", 172_000L, 180_000L, isPlaying = true))
    }

    @Test
    fun pausedProgressIsNotCounted() {
        val p = ScrobblePolicy()
        p.onProgress("t1", 60_000L, 180_000L, isPlaying = true)
        assertFalse(p.onProgress("t1", 120_000L, 180_000L, isPlaying = false))
        assertFalse(p.isSubmitted())
    }

    @Test
    fun newTrackResetsPolicy() {
        val p = ScrobblePolicy()
        for (pos in 0..100_000 step 1_000) if (p.onProgress("t1", pos.toLong(), 180_000L, true)) break
        assertTrue(p.isSubmitted())
        assertFalse(p.onProgress("t2", 500L, 180_000L, true))
    }

    @Test
    fun trackEndSubmitsOnce() {
        val p = ScrobblePolicy()
        p.onProgress("t1", 1_000L, 180_000L, true)
        assertTrue(p.onTrackEnded("t1", 180_000L))
        assertFalse(p.onTrackEnded("t1", 180_000L))
    }

    @Test
    fun endedDoesNotDoubleSubmitAfterThreshold() {
        val p = ScrobblePolicy()
        for (pos in 0..100_000 step 1_000) if (p.onProgress("t1", pos.toLong(), 180_000L, true)) break
        assertFalse(p.onTrackEnded("t1", 180_000L))
    }
}