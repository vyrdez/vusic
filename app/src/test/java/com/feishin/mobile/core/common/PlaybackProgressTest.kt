package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {

    @Test
    fun unknownDuration_neverFillsTheBar() {
        // Regression: duration 0 used to collapse the range to 0f..1f and the
        // position saturated to 1f, rendering a permanently full bar.
        assertEquals(0f, PlaybackProgress.progress(0L, 0L), 0.0001f)
        assertEquals(0f, PlaybackProgress.progress(50_000L, 0L), 0.0001f)
        assertEquals(0f, PlaybackProgress.progress(50_000L, PlaybackProgress.TIME_UNSET), 0.0001f)
    }

    @Test
    fun timeUnset_isRecognised() {
        assertTrue(PlaybackProgress.isUnknown(PlaybackProgress.TIME_UNSET))
        assertTrue(PlaybackProgress.isUnknown(0L))
        assertTrue(PlaybackProgress.isUnknown(-1L))
        assertTrue(!PlaybackProgress.isUnknown(1L))
    }

    @Test
    fun progress_matchesPositionOverDuration() {
        assertEquals(0f, PlaybackProgress.progress(0L, 200_000L), 0.0001f)
        assertEquals(0.5f, PlaybackProgress.progress(100_000L, 200_000L), 0.0001f)
        assertEquals(1f, PlaybackProgress.progress(200_000L, 200_000L), 0.0001f)
        // Overshoot clamps instead of going past the end.
        assertEquals(1f, PlaybackProgress.progress(999_000L, 200_000L), 0.0001f)
        assertEquals(0f, PlaybackProgress.progress(-5_000L, 200_000L), 0.0001f)
    }

    @Test
    fun progressWithScrub_prefersScrubTarget() {
        assertEquals(0.25f, PlaybackProgress.progressWithScrub(10_000L, 200_000L, 50_000L), 0.0001f)
        assertEquals(0.05f, PlaybackProgress.progressWithScrub(10_000L, 200_000L, null), 0.0001f)
        // Scrubbing to the end is allowed; scrubbing while unknown stays at 0.
        assertEquals(1f, PlaybackProgress.progressWithScrub(10_000L, 200_000L, 200_000L), 0.0001f)
        assertEquals(0f, PlaybackProgress.progressWithScrub(10_000L, 0L, 50_000L), 0.0001f)
    }

    @Test
    fun remaining_isNeverNegativeOrOverlong() {
        assertEquals(100_000L, PlaybackProgress.remainingMs(100_000L, 200_000L))
        assertEquals(0L, PlaybackProgress.remainingMs(999_000L, 200_000L))
        assertEquals(0L, PlaybackProgress.remainingMs(10_000L, 0L))
        assertEquals(150_000L, PlaybackProgress.remainingMs(10_000L, 200_000L, 50_000L))
    }

    @Test
    fun remainingClock_showsPlaceholderWhenUnknown() {
        assertEquals("--:--", PlaybackProgress.formatRemaining(10_000L, 0L))
        assertEquals("--:--", PlaybackProgress.formatRemaining(10_000L, PlaybackProgress.TIME_UNSET))
        assertEquals("-2:34", "-" + PlaybackProgress.formatRemaining(0L, 154_000L))
    }

    @Test
    fun elapsedClock_formatsMinutesAndHours() {
        assertEquals("0:00", PlaybackProgress.formatElapsed(0L))
        assertEquals("0:05", PlaybackProgress.formatElapsed(5_000L))
        assertEquals("3:47", PlaybackProgress.formatElapsed(227_000L))
        assertEquals("1:01:05", PlaybackProgress.formatElapsed(3_665_000L))
        // Negative positions read as zero rather than counting backwards.
        assertEquals("0:00", PlaybackProgress.formatElapsed(-9_000L))
    }
}
