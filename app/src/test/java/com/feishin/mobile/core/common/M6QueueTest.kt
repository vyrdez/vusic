package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Queue drag-and-drop commit rules plus the clear/undo contract.
 *
 * - Reorder commits once on drop (never per frame), keyed by unique queue ids
 *   so duplicates of the same song stay distinct.
 * - A drop that races an add/remove is stale and must be ignored, never applied.
 * - Clearing snapshots everything Undo needs; restore brings back the same
 *   queue, item and position, paused.
 */
class M6QueueTest {

    // ---- Commit-on-drop -----------------------------------------------------

    @Test
    fun commit_identicalOrder_isNoOp() {
        assertNull(QueueCommit.targetIds(listOf(1L, 2L, 3L), listOf(1L, 2L, 3L)))
    }

    @Test
    fun commit_reorderedOrder_isApplied() {
        assertEquals(listOf(2L, 3L, 1L), QueueCommit.targetIds(listOf(1L, 2L, 3L), listOf(2L, 3L, 1L)))
    }

    @Test
    fun commit_staleDropAfterRemove_isIgnored() {
        // Swiped away entry 3 mid-drag: the drop targets a ghost set.
        assertNull(QueueCommit.targetIds(listOf(1L, 2L), listOf(2L, 3L, 1L)))
    }

    @Test
    fun commit_staleDropAfterRadioAppend_isIgnored() {
        // Radio appended entry 4 mid-drag: the drop doesn't know about it.
        assertNull(QueueCommit.targetIds(listOf(1L, 2L, 3L, 4L), listOf(2L, 3L, 1L)))
    }

    @Test
    fun commit_singleSwap_isApplied() {
        assertEquals(listOf(1L, 3L, 2L), QueueCommit.targetIds(listOf(1L, 2L, 3L), listOf(1L, 3L, 2L)))
    }

    @Test
    fun moveSeq_reordersDuplicatesByQueueId() {
        // Same song twice (song "s" at queue ids 10 and 30): the move language
        // is queue ids, so the two copies never collapse into each other.
        val current = listOf(10L, 20L, 30L)
        val target = listOf(30L, 10L, 20L)
        val moves = ShufflePlan.moveSeq(current, target)
        val replayed = current.toMutableList()
        for ((from, to) in moves) replayed.add(to, replayed.removeAt(from))
        assertEquals(target, replayed)
    }

    @Test
    fun applyOrder_keepsDuplicateEntriesDistinct() {
        data class Entry(val songId: String, val queueId: Long)
        val items = listOf(
            Entry("s", 10), Entry("t", 20), Entry("s", 30),
        )
        val reordered = ShufflePlan.applyOrder(listOf(30L, 10L, 20L), items) { it.queueId }
        assertEquals(listOf(30L, 10L, 20L), reordered.map { it.queueId })
        assertEquals(3, reordered.size)
    }

    @Test
    fun queueOps_move_keepsCurrentOnSameEntryWithDuplicates() {
        data class Entry(val songId: String, val queueId: Long)
        val q = listOf(Entry("s", 10), Entry("t", 20), Entry("s", 30))
        // Playing queue id 30 (the second "s"); drag the first "s" elsewhere.
        val (next, adjusted) = QueueOps.move(q, 0, 1, 2)
        assertEquals(listOf(20L, 10L, 30L), next.map { it.queueId })
        assertEquals(30L, next[adjusted].queueId)
    }

    // ---- Clear + Undo -------------------------------------------------------

    @Test
    fun clear_returnsEmptyQueueAtIndexZero() {
        val (next, adjusted) = QueueOps.clear<String>()
        assertEquals(emptyList<String>(), next)
        assertEquals(0, adjusted)
    }

    @Test
    fun undoRestore_rebuildsSameOrderAndIndex() {
        // The restore contract: same ids in the same order, same current entry
        // (found by queue key, safe with duplicates), same position.
        val ids = listOf(10L, 20L, 30L, 40L)
        val savedIndex = 2
        val restored = ShufflePlan.applyOrder(ids, ids.map { it to "song$it" }) { it.first }
        assertEquals(ids, restored.map { it.first })
        val keepId = ids[savedIndex]
        val idx = restored.indexOfFirst { it.first == keepId }
        assertEquals(savedIndex, idx)
    }

    @Test
    fun undoRestore_afterMidDragRemoval_dropsGhost() {
        // Entry removed while the Undo snackbar was up: restore keeps the
        // survivors in snapshot order and re-points at the same entry.
        val live = listOf(10L, 30L, 40L)
        val snapshot = listOf(10L, 20L, 30L, 40L)
        val target = ShufflePlan.restore(live, snapshot)
        assertEquals(listOf(10L, 30L, 40L), target)
    }
}
