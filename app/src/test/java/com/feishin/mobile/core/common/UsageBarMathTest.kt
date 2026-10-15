package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part C4: the usage bar's segments must be proportional to bytes. */
class UsageBarMathTest {

    private val mb = 1L * 1024 * 1024
    private val gb = 1024L * mb

    private fun seg(label: String, bytes: Long) = UsageBarMath.Segment(label, bytes, 0)

    @Test
    fun allZero_isSafeAndEmpty() {
        val r = UsageBarMath.compute(listOf(seg("a", 0), seg("b", 0)), freeBytes = 0)
        assertEquals(0L, r.totalBytes)
        assertEquals(0f, r.fractions.sum(), 1e-4f)
        assertTrue(r.fractions.all { it == 0f })
    }

    @Test
    fun tinyUsageAgainstHugeFreeSpace_isProportional() {
        // The reported bug: 347 MB of song cache filled the whole bar while
        // 3.94 GB was free. The cache must now be a small slice.
        val r = UsageBarMath.compute(listOf(seg("cache", 347L * mb)), freeBytes = 4038L * mb)
        assertEquals(2, r.fractions.size)
        val used = r.fractions[0]
        val free = r.fractions[1]
        assertTrue("used $used free $free", used < 0.15f)
        assertTrue("used $used free $free", free > 0.85f)
        assertEquals(1f, r.fractions.sum(), 1e-3f)
    }

    @Test
    fun fractionsAlwaysSumToOne() {
        val cases = listOf(
            listOf(seg("a", 1L), seg("b", 1L), seg("c", 1L)) to 1L,
            listOf(seg("a", 5_000_000_000L), seg("b", 3L)) to gb,
            listOf(seg("a", 0L), seg("b", 0L)) to 2L * gb,
            listOf(seg("a", gb), seg("b", gb), seg("c", gb)) to gb,
        )
        for ((segments, free) in cases) {
            val r = UsageBarMath.compute(segments, free)
            assertEquals(
                "case $segments free=$free",
                1f, r.fractions.sum(), 1e-3f,
            )
        }
    }

    @Test
    fun nonZeroSegmentsGetAMinimumWidth() {
        // 1 byte next to 1 TB is below the minimum, but must still be visible.
        val r = UsageBarMath.compute(listOf(seg("tiny", 1L)), freeBytes = 1024L * gb)
        assertTrue(r.fractions[0] >= UsageBarMath.MIN_SEGMENT_FRACTION)
    }

    @Test
    fun zeroSegmentsStayAtZero() {
        val r = UsageBarMath.compute(listOf(seg("a", 0L), seg("b", 512L * mb)), freeBytes = gb)
        assertEquals(0f, r.fractions[0], 0f)
        assertTrue(r.fractions[1] > 0f)
    }

    @Test
    fun negativeAndOverflowingInputsAreClamped() {
        val r = UsageBarMath.compute(
            listOf(seg("neg", -5L), seg("big", Long.MAX_VALUE / 4)),
            freeBytes = -1L,
        )
        assertTrue(r.fractions.all { it in 0f..1f })
        assertEquals(1f, r.fractions.sum(), 1e-3f)
        assertEquals(0L, r.freeBytes)
    }

    @Test
    fun usedAndFreeAreReportedSeparately() {
        val r = UsageBarMath.compute(listOf(seg("a", 100L), seg("b", 300L)), freeBytes = 600L)
        assertEquals(400L, r.usedBytes)
        assertEquals(600L, r.freeBytes)
        assertEquals(1000L, r.totalBytes)
    }

    @Test
    fun emptyVusicUsageIsAllFree() {
        val r = UsageBarMath.compute(emptyList(), freeBytes = gb)
        assertEquals(1, r.fractions.size)
        assertEquals(1f, r.fractions[0], 1e-4f)
        assertEquals(0L, r.usedBytes)
    }

    @Test
    fun segmentOrderIsPreservedAndFreeIsLast() {
        val r = UsageBarMath.compute(
            listOf(seg("images", 100L), seg("downloads", 200L), seg("cache", 300L)),
            freeBytes = 400L,
        )
        assertEquals(listOf(100L, 200L, 300L, 400L), r.bytes)
    }

    @Test
    fun totalLabelReadsAsUsedAndFree() {
        val label = UsageBarMath.totalLabel(1024L, 2048L) { "$it B" }
        assertEquals("1024 B used by Vusic, 2048 B free", label)
    }

    @Test
    fun moreSegmentsThanSpaceStillSumsToOne() {
        // 6 tiny segments: the minimum-width floor would overshoot, so the
        // correction has to pull the largest back down.
        val segments = (1..6).map { seg("s$it", 1L) }
        val r = UsageBarMath.compute(segments, freeBytes = 4L)
        assertEquals(1f, r.fractions.sum(), 1e-3f)
        assertTrue(r.fractions.all { it >= 0f })
    }
}