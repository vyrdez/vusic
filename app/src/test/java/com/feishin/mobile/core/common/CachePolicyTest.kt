package com.feishin.mobile.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {

    /** A clock the test moves by hand. */
    private class FakeClock(var now: Long = 1_000_000L) {
        fun advance(ms: Long) { now += ms }
    }

    @Test
    fun firstRead_refreshes() {
        val p = CachePolicy(freshForMs = 60_000, clock = { 0L })
        assertTrue(p.shouldRefresh("albums"))
    }

    @Test
    fun withinTheWindow_onlyTheFirstReadRefreshes() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        assertTrue(p.shouldRefresh("artists"))
        clock.advance(1_000)
        assertFalse(p.shouldRefresh("artists"))
        clock.advance(58_000)
        // 59 s after the attempt: still inside the window.
        assertFalse(p.shouldRefresh("artists"))
        clock.advance(1_000)
        // 60 s: a new window.
        assertTrue(p.shouldRefresh("artists"))
    }

    @Test
    fun aFailedAttemptStillCounts() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        // The caller records an attempt, the fetch fails, no success is stored.
        assertTrue(p.shouldRefresh("playlists"))
        clock.advance(5_000)
        // No retry storm: the window still holds.
        assertFalse(p.shouldRefresh("playlists"))
    }

    @Test
    fun entriesAreThrottledIndependently() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        assertTrue(p.shouldRefresh("albums:alphabeticalByName:0"))
        assertTrue(p.shouldRefresh("artists"))
        assertTrue(p.shouldRefresh("playlists"))
        clock.advance(10_000)
        assertFalse(p.shouldRefresh("albums:alphabeticalByName:0"))
        assertFalse(p.shouldRefresh("artists"))
    }

    @Test
    fun forceAll_makesTheNextReadsWaitForTheNetwork_untilTheWindowEnds() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        p.forceAll(durationMs = 4_000)
        // One pass: every entry is forced once, no further rounds.
        assertTrue(p.consumeForced("a"))
        assertTrue(p.consumeForced("b"))
        assertFalse(p.consumeForced("a"))
        // A new window (the user pulled again) forces them again.
        p.forceAll(durationMs = 4_000)
        assertTrue(p.consumeForced("a"))
        assertFalse(p.consumeForced("a"))
        // Once the window is gone, nothing is forced any more.
        clock.advance(5_000)
        assertFalse(p.consumeForced("a"))
    }

    @Test
    fun forceOneKey_isConsumedOnce() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        p.force("starred")
        assertTrue(p.consumeForced("starred"))
        assertFalse(p.consumeForced("starred"))
        assertFalse(p.consumeForced("artists"))
    }

    @Test
    fun reset_forgetsThrottleAndForces() {
        val clock = FakeClock()
        val p = CachePolicy(freshForMs = 60_000, clock = { clock.now })
        assertTrue(p.shouldRefresh("songs"))
        p.force("album:1")
        p.reset()
        assertTrue(p.shouldRefresh("songs"))
        assertFalse(p.consumeForced("album:1"))
    }
}
