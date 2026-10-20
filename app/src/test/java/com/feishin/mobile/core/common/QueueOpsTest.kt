package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOpsTest {

    @Test
    fun insertNext_placesAfterCurrent() {
        assertEquals(listOf("a", "x", "b", "c"), QueueOps.insertNext(listOf("a", "b", "c"), "x", 0))
        assertEquals(listOf("a", "b", "c", "x"), QueueOps.insertNext(listOf("a", "b", "c"), "x", 2))
    }

    @Test
    fun insertNext_emptyQueue_goesToHead() {
        assertEquals(listOf("x"), QueueOps.insertNext(emptyList(), "x", -1))
    }

    @Test
    fun append_addsToEnd() {
        assertEquals(listOf("a", "x"), QueueOps.append(listOf("a"), "x"))
        assertEquals(listOf("a", "b", "c"), QueueOps.appendAll(listOf("a"), listOf("b", "c")))
    }

    @Test
    fun remove_currentItem_movesToSameSlot() {
        val (next, idx) = QueueOps.removeAt(listOf("a", "b", "c"), 1, 1)
        assertEquals(listOf("a", "c"), next)
        assertEquals(1, idx)
    }

    @Test
    fun remove_lastItem_movesIndexBack() {
        val (next, idx) = QueueOps.removeAt(listOf("a", "b", "c"), 2, 2)
        assertEquals(listOf("a", "b"), next)
        assertEquals(1, idx)
    }

    @Test
    fun remove_itemBeforeCurrent_shiftsIndexBack() {
        val (next, idx) = QueueOps.removeAt(listOf("a", "b", "c"), 0, 2)
        assertEquals(listOf("b", "c"), next)
        assertEquals(1, idx)
    }

    @Test
    fun remove_itemAfterCurrent_keepsIndex() {
        val (next, idx) = QueueOps.removeAt(listOf("a", "b", "c"), 2, 0)
        assertEquals(listOf("a", "b"), next)
        assertEquals(0, idx)
    }

    @Test
    fun remove_onlyItem_emptiesQueue() {
        val (next, idx) = QueueOps.removeAt(listOf("a"), 0, 0)
        assertTrue(next.isEmpty())
        assertEquals(0, idx)
    }

    @Test
    fun remove_outOfBounds_isNoop() {
        val q = listOf("a", "b")
        val (next, idx) = QueueOps.removeAt(q, 5, 1)
        assertEquals(q, next)
        assertEquals(1, idx)
    }

    @Test
    fun move_keepsCurrentPointingAtSameItem() {
        // Queue a b c, current is c (2). Move a(0) -> 2 gives b c a; c is now at 1.
        val (next, idx) = QueueOps.move(listOf("a", "b", "c"), 0, 2, 2)
        assertEquals(listOf("b", "c", "a"), next)
        assertEquals(1, idx)
    }

    @Test
    fun move_currentItem_tracksIt() {
        // Move b(1) -> 0 gives b a c; b is now at 0.
        val (next, idx) = QueueOps.move(listOf("a", "b", "c"), 1, 0, 1)
        assertEquals(listOf("b", "a", "c"), next)
        assertEquals(0, idx)
    }

    @Test
    fun move_outOfBounds_isNoop() {
        val q = listOf("a", "b")
        val (next, idx) = QueueOps.move(q, 0, 9, 0)
        assertEquals(q, next)
        assertEquals(0, idx)
    }

    @Test
    fun clear_empties() {
        val (next, idx) = QueueOps.clear<String>()
        assertTrue(next.isEmpty())
        assertEquals(0, idx)
    }

    // ---- by-key removal (queue swipe) -------------------------------------

    private data class Entry(val song: String, val id: Long)

    private fun removeById(list: List<Entry>, id: Long?, current: Int) =
        QueueOps.removeById(list, id, Entry::id, current)

    @Test
    fun removeById_removesOnlyThePointedCopy_ofADuplicateSong() {
        val q = listOf(Entry("a", 1), Entry("a", 2), Entry("b", 3))
        val (next, idx) = removeById(q, 2, 0)
        // The second copy goes; the first stays, so the song is still queued.
        assertEquals(listOf(Entry("a", 1), Entry("b", 3)), next)
        assertEquals(0, idx)
    }

    @Test
    fun removeById_repeatedCallback_isANoop() {
        val q = listOf(Entry("a", 1), Entry("b", 2), Entry("c", 3))
        val (first, current) = removeById(q, 2, 1)
        // The row's settle replays: the same key arrives again after the list
        // already shrank. It must not take the neighbour that slid into place.
        val (second, after) = removeById(first, 2, current)
        assertEquals(listOf(Entry("a", 1), Entry("c", 3)), second)
        assertEquals(1, after)
    }

    @Test
    fun removeById_currentEntry_movesToTheSameSlot() {
        val q = listOf(Entry("a", 1), Entry("b", 2), Entry("c", 3))
        val (next, idx) = removeById(q, 2, 1)
        assertEquals(listOf(Entry("a", 1), Entry("c", 3)), next)
        assertEquals(1, idx)
    }

    @Test
    fun removeById_lastEntry_clampsCurrentBack() {
        val q = listOf(Entry("a", 1), Entry("b", 2), Entry("c", 3))
        val (next, idx) = removeById(q, 3, 2)
        assertEquals(listOf(Entry("a", 1), Entry("b", 2)), next)
        assertEquals(1, idx)
    }

    @Test
    fun removeById_onlyEntry_emptiesQueue() {
        val (next, idx) = removeById(listOf(Entry("a", 1)), 1, 0)
        assertTrue(next.isEmpty())
        assertEquals(0, idx)
    }

    @Test
    fun removeById_updatesTheKey_evenAfterTheListMoved() {
        // The user reordered b to the top before swiping it. A position from
        // the first composition (1) would now point at c.
        val q = listOf(Entry("b", 2), Entry("a", 1), Entry("c", 3))
        val (next, idx) = removeById(q, 2, 2)
        assertEquals(listOf(Entry("a", 1), Entry("c", 3)), next)
        assertEquals(1, idx)
    }

    @Test
    fun removeById_afterAnEarlierRemoval_doesNotTakeTheNeighbour() {
        // Reproduces the reported defect at the mapping level: row at slot 3
        // was swiped after slot 2 had already gone. The stale slot 3 is now a
        // different song; the key is not.
        var q = listOf(Entry("a", 1), Entry("b", 2), Entry("c", 3), Entry("d", 4))
        val (shrunk, current) = removeById(q, 2, 0) // "b" gone: c d shift up
        val (next, idx) = removeById(shrunk, 4, current) // swipe "d" by key
        assertEquals(listOf(Entry("a", 1), Entry("c", 3)), next)
        assertEquals(0, idx)
        q = next
        assertTrue(q.none { it.id == 4L })
    }

    @Test
    fun removeById_unknownOrNullId_isANoop() {
        val q = listOf(Entry("a", 1), Entry("b", 2))
        assertEquals(q, removeById(q, 99, 1).first)
        assertEquals(1, removeById(q, 99, 1).second)
        assertEquals(q, removeById(q, null, 0).first)
    }

    @Test
    fun shuffled_preservesAllElements() {
        val q = listOf("a", "b", "c", "d", "e")
        val s = QueueOps.shuffled(q, kotlin.random.Random(42))
        assertEquals(q.sorted(), s.sorted())
        assertEquals(5, s.size)
    }
}
