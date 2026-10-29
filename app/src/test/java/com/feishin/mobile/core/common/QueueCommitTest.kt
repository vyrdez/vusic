package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueCommitTest {

    @Test
    fun singleMove_detectsUpwardAndDownwardDrag() {
        // Drag down: index 0 to 2
        val liveDown = listOf(10L, 20L, 30L, 40L)
        val shownDown = listOf(20L, 30L, 10L, 40L)
        assertEquals(0 to 2, QueueCommit.singleMove(liveDown, shownDown))

        // Drag up: index 3 to 1
        val liveUp = listOf(10L, 20L, 30L, 40L)
        val shownUp = listOf(10L, 40L, 20L, 30L)
        assertEquals(3 to 1, QueueCommit.singleMove(liveUp, shownUp))

        // Adjacent swap: index 1 to 2
        val liveSwap = listOf(10L, 20L, 30L)
        val shownSwap = listOf(10L, 30L, 20L)
        assertEquals(1 to 2, QueueCommit.singleMove(liveSwap, shownSwap))

        // Duplicate songs with distinct queueIds:
        // song "s" queued at queueId 10L and 30L. Drag first copy (0) to index 2.
        val liveDups = listOf(10L, 20L, 30L, 40L)
        val shownDups = listOf(20L, 30L, 10L, 40L)
        assertEquals(0 to 2, QueueCommit.singleMove(liveDups, shownDups))
    }

    @Test
    fun singleMove_returnsNullForIdenticalOrInvalidOrMultiMove() {
        // Identical: no-op
        assertNull(QueueCommit.singleMove(listOf(1L, 2L, 3L), listOf(1L, 2L, 3L)))

        // Stale target: different size / set
        assertNull(QueueCommit.singleMove(listOf(1L, 2L), listOf(1L, 2L, 3L)))
        assertNull(QueueCommit.singleMove(listOf(1L, 2L, 3L), listOf(1L, 2L, 4L)))

        // Multi-move: full reverse
        assertNull(QueueCommit.singleMove(listOf(1L, 2L, 3L, 4L), listOf(4L, 3L, 2L, 1L)))

        // Multi-move: two separate swaps
        assertNull(QueueCommit.singleMove(listOf(1L, 2L, 3L, 4L), listOf(2L, 1L, 4L, 3L)))
    }

    @Test
    fun sliceAroundKeep_splitsHeadAndTailPreservingActiveItem() {
        // 1. Shuffling tail: active item 20L stays at index 1, head untouched
        val before = listOf(10L, 20L, 30L, 40L, 50L)
        val shuffled = listOf(10L, 20L, 50L, 30L, 40L)
        val tailPlan = ShufflePlan.sliceAroundKeep(
            beforeCount = before.size,
            currentIdx = 1,
            reordered = shuffled,
            keepId = 20L,
        ) { it }

        assertEquals(1, tailPlan.targetIndex)
        assertEquals(listOf(10L), tailPlan.head)
        assertEquals(listOf(50L, 30L, 40L), tailPlan.tail)
        assertEquals(1, tailPlan.beforeCount) // [0, 1) in live controller
        assertEquals(3, tailPlan.afterCount)  // 5 - (1 + 1) = 3 in live controller

        // 2. Restoring unshuffled order: active item 20L moved from index 2 to index 1
        val liveShuffled = listOf(10L, 50L, 20L, 40L, 30L)
        val restored = listOf(10L, 20L, 30L, 40L, 50L)
        val restorePlan = ShufflePlan.sliceAroundKeep(
            beforeCount = liveShuffled.size,
            currentIdx = 2,
            reordered = restored,
            keepId = 20L,
        ) { it }

        assertEquals(1, restorePlan.targetIndex)
        assertEquals(listOf(10L), restorePlan.head)
        assertEquals(listOf(30L, 40L, 50L), restorePlan.tail)
        assertEquals(2, restorePlan.beforeCount) // [0, 2) in live controller
        assertEquals(2, restorePlan.afterCount)  // 5 - (2 + 1) = 2 in live controller
    }

    @Test
    fun sliceAroundKeep_edgeCases() {
        // Empty list
        val emptyPlan = ShufflePlan.sliceAroundKeep(
            beforeCount = 0,
            currentIdx = 0,
            reordered = emptyList<Long>(),
            keepId = null,
        ) { it }
        assertEquals(0, emptyPlan.targetIndex)
        assertEquals(emptyList<Long>(), emptyPlan.head)
        assertEquals(emptyList<Long>(), emptyPlan.tail)
        assertEquals(0, emptyPlan.beforeCount)
        assertEquals(0, emptyPlan.afterCount)

        // Single item active at 0
        val singlePlan = ShufflePlan.sliceAroundKeep(
            beforeCount = 1,
            currentIdx = 0,
            reordered = listOf(99L),
            keepId = 99L,
        ) { it }
        assertEquals(0, singlePlan.targetIndex)
        assertEquals(emptyList<Long>(), singlePlan.head)
        assertEquals(emptyList<Long>(), singlePlan.tail)
        assertEquals(0, singlePlan.beforeCount)
        assertEquals(0, singlePlan.afterCount)

        // Active at head (index 0) with multiple items
        val headActivePlan = ShufflePlan.sliceAroundKeep(
            beforeCount = 3,
            currentIdx = 0,
            reordered = listOf(1L, 3L, 2L),
            keepId = 1L,
        ) { it }
        assertEquals(0, headActivePlan.targetIndex)
        assertEquals(emptyList<Long>(), headActivePlan.head)
        assertEquals(listOf(3L, 2L), headActivePlan.tail)
        assertEquals(0, headActivePlan.beforeCount)
        assertEquals(2, headActivePlan.afterCount)

        // Active at tail (last index) with multiple items
        val tailActivePlan = ShufflePlan.sliceAroundKeep(
            beforeCount = 3,
            currentIdx = 2,
            reordered = listOf(2L, 1L, 3L),
            keepId = 3L,
        ) { it }
        assertEquals(2, tailActivePlan.targetIndex)
        assertEquals(listOf(2L, 1L), tailActivePlan.head)
        assertEquals(emptyList<Long>(), tailActivePlan.tail)
        assertEquals(2, tailActivePlan.beforeCount)
        assertEquals(0, tailActivePlan.afterCount)

        // KeepId null or not found: falls back to currentIdx
        val notFoundPlan = ShufflePlan.sliceAroundKeep(
            beforeCount = 3,
            currentIdx = 1,
            reordered = listOf(1L, 2L, 3L),
            keepId = 999L,
        ) { it }
        assertEquals(1, notFoundPlan.targetIndex)
        assertEquals(listOf(1L), notFoundPlan.head)
        assertEquals(listOf(3L), notFoundPlan.tail)
    }
}
