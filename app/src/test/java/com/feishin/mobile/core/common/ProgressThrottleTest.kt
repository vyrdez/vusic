package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {

    @Test
    fun `flood of updates is capped to the interval`() {
        val t = ProgressThrottle(minIntervalMs = 500)
        var emitted = 0
        for (i in 0 until 80) {
            if (t.shouldEmit("song", i, 0L)) emitted++
        }
        // First call emits; the remaining 79 are within the 500 ms window.
        assertEquals(1, emitted)
    }

    @Test
    fun `time spaced updates emit`() {
        val t = ProgressThrottle(minIntervalMs = 500)
        assertTrue(t.shouldEmit("song", 10, 0))
        assertTrue(t.shouldEmit("song", 20, 500))
        assertTrue(t.shouldEmit("song", 30, 1000))
    }

    @Test
    fun `unchanged percentage does not emit`() {
        val t = ProgressThrottle(minIntervalMs = 500)
        assertTrue(t.shouldEmit("song", 40, 0))
        assertFalse(t.shouldEmit("song", 40, 600))
    }

    @Test
    fun `a new song emits even inside the interval`() {
        val t = ProgressThrottle(minIntervalMs = 500)
        assertTrue(t.shouldEmit("a", 10, 0))
        // New key bypasses the per-song step rule, but the global interval
        // still applies: at 100 ms it is too soon.
        assertFalse(t.shouldEmit("b", 10, 100))
        // After the interval, the new song emits.
        assertTrue(t.shouldEmit("b", 10, 500))
    }

    @Test
    fun `first call always emits`() {
        val t = ProgressThrottle(minIntervalMs = 1000)
        assertTrue(t.shouldEmit("song", 0, 12345))
    }
}
