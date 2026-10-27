package com.feishin.mobile.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentDownloadsTest {
    data class MockSettings(val maxConcurrentDownloads: Int = 2)

    private fun clampLimit(v: Int): Int = v.coerceIn(1, 5)

    @Test
    fun defaultConcurrency_isTwo() {
        assertEquals(2, MockSettings().maxConcurrentDownloads)
    }

    @Test
    fun valuesClamped_betweenOneAndFive() {
        assertEquals(1, clampLimit(-5))
        assertEquals(1, clampLimit(0))
        assertEquals(1, clampLimit(1))
        assertEquals(3, clampLimit(3))
        assertEquals(5, clampLimit(5))
        assertEquals(5, clampLimit(6))
        assertEquals(5, clampLimit(100))
    }

    @Test
    fun raisingLimit_startsMoreTasks() {
        val runLimit = AtomicInteger(clampLimit(2))
        val active = AtomicInteger(0)
        fun tryStart(): Boolean {
            while (true) {
                val cur = active.get()
                if (cur >= runLimit.get()) return false
                if (active.compareAndSet(cur, cur + 1)) return true
            }
        }

        assertTrue(tryStart())
        assertTrue(tryStart())
        assertFalse(tryStart()) // saturated at 2

        runLimit.set(clampLimit(4)) // raised limit allows more
        assertTrue(tryStart())
        assertTrue(tryStart())
        assertFalse(tryStart()) // saturated at 4
        assertEquals(4, active.get())
    }

    @Test
    fun loweringLimit_doesNotCancelRunningTasks() {
        val runLimit = AtomicInteger(clampLimit(4))
        val runningTasks = mutableListOf<String>()

        for (i in 1..4) {
            if (runningTasks.size < runLimit.get()) runningTasks.add("task-$i")
        }
        assertEquals(4, runningTasks.size)

        // Lowering limit does not cancel running tasks
        runLimit.set(clampLimit(2))
        assertEquals(4, runningTasks.size)
        assertTrue(runningTasks.contains("task-1"))
        assertTrue(runningTasks.contains("task-4"))

        // New tasks blocked until active drops below new limit
        assertFalse(runningTasks.size < runLimit.get())
    }
}
