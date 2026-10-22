package com.feishin.mobile.playback

import com.feishin.mobile.core.common.ShufflePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class BigPlaylistShuffleTest {

    @Test
    fun benchmark_shuffleTail_scalesLinearly() {
        val sizes = listOf(500, 1000, 3000)
        for (n in sizes) {
            val list = (0L until n.toLong()).toList()
            val elapsed = measureTimeMillis {
                val shuffled = ShufflePlan.shuffledTail(list, fromIndex = 1, random = Random(42))
                assertEquals(n, shuffled.size)
                assertEquals(0L, shuffled[0])
            }
            // Must finish in < 50ms even on host JVM
            assertTrue("Shuffling $n items took ${elapsed}ms (> 50ms)", elapsed < 50)
        }
    }

    @Test
    fun benchmark_restore_scalesLinearly() {
        val sizes = listOf(500, 1000, 3000)
        for (n in sizes) {
            val original = (0L until n.toLong()).toList()
            val shuffled = ShufflePlan.shuffledTail(original, fromIndex = 1, random = Random(42))
            val elapsed = measureTimeMillis {
                val restored = ShufflePlan.restore(shuffled, snapshotIds = original)
                assertEquals(original, restored)
            }
            assertTrue("Restoring $n items took ${elapsed}ms (> 50ms)", elapsed < 50)
        }
    }

    @Test
    fun benchmark_applyOrder_scalesLinearly() {
        val sizes = listOf(500, 1000, 3000)
        for (n in sizes) {
            val original = (0L until n.toLong()).toList()
            val shuffled = ShufflePlan.shuffledTail(original, fromIndex = 1, random = Random(42))
            val items = original.map { it to "song_$it" }
            val elapsed = measureTimeMillis {
                val reordered = ShufflePlan.applyOrder(shuffled, items) { it.first }
                assertEquals(n, reordered.size)
                assertEquals(shuffled, reordered.map { it.first })
            }
            assertTrue("Applying order to $n items took ${elapsed}ms (> 50ms)", elapsed < 50)
        }
    }
}
