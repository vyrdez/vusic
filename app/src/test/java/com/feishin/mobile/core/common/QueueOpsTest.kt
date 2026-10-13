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

    @Test
    fun shuffled_preservesAllElements() {
        val q = listOf("a", "b", "c", "d", "e")
        val s = QueueOps.shuffled(q, kotlin.random.Random(42))
        assertEquals(q.sorted(), s.sorted())
        assertEquals(5, s.size)
    }
}
