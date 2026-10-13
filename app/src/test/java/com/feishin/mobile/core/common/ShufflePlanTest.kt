package com.feishin.mobile.core.common

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShufflePlanTest {

    @Test
    fun shuffledTail_keepsPlayedHeadAndCurrent() {
        val order = listOf(10L, 20L, 30L, 40L, 50L)
        // Current is index 1 (20), so 10 and 20 must stay put.
        val out = ShufflePlan.shuffledTail(order, fromIndex = 2, random = Random(7))
        assertEquals(listOf(10L, 20L), out.take(2))
        assertEquals(order.toSet(), out.toSet())
        assertEquals(5, out.size)
    }

    @Test
    fun shuffledTail_fromZeroShufflesEverything() {
        val order = listOf(1L, 2L, 3L, 4L)
        val out = ShufflePlan.shuffledTail(order, fromIndex = 0, random = Random(3))
        assertEquals(order.toSet(), out.toSet())
    }

    @Test
    fun shuffledTail_pastEndIsNoop() {
        val order = listOf(1L, 2L)
        assertEquals(order, ShufflePlan.shuffledTail(order, fromIndex = 5, random = Random(1)))
    }

    @Test
    fun restore_returnsSnapshotOrder() {
        val snapshot = listOf(1L, 2L, 3L, 4L)
        val live = listOf(3L, 1L, 4L, 2L) // shuffled
        assertEquals(snapshot, ShufflePlan.restore(live, snapshot))
    }

    @Test
    fun restore_keepsCurrentInTheMiddle() {
        // Current is the entry with key 30, originally at index 2.
        val snapshot = listOf(10L, 20L, 30L, 40L, 50L)
        val live = listOf(50L, 30L, 10L, 40L, 20L)
        val restored = ShufflePlan.restore(live, snapshot)
        assertEquals(2, restored.indexOf(30L))
        // The song that originally followed the current one is next.
        assertEquals(40L, restored[3])
    }

    @Test
    fun restore_dropsRemovedEntries() {
        val snapshot = listOf(1L, 2L, 3L, 4L)
        val live = listOf(4L, 1L, 3L) // 2 was removed while shuffled
        assertEquals(listOf(1L, 3L, 4L), ShufflePlan.restore(live, snapshot))
    }

    @Test
    fun restore_appendsEntriesAddedWhileShuffled() {
        val snapshot = listOf(1L, 2L, 3L)
        // 9 = "add to queue"/radio append, 5 = "play next" (already placed in the
        // snapshot by insertAfter, so here it is exercised as an orphan too).
        val live = listOf(2L, 1L, 3L, 9L)
        assertEquals(listOf(1L, 2L, 3L, 9L), ShufflePlan.restore(live, snapshot))
    }

    @Test
    fun insertAfter_placesPlayNextRightAfterCurrent() {
        val snapshot = listOf(1L, 2L, 3L)
        assertEquals(listOf(1L, 2L, 99L, 3L), ShufflePlan.insertAfter(snapshot, afterId = 2L, id = 99L))
    }

    @Test
    fun insertAfter_missingAnchorAppends() {
        assertEquals(listOf(1L, 2L, 99L), ShufflePlan.insertAfter(listOf(1L, 2L), afterId = 7L, id = 99L))
    }

    @Test
    fun insertAllAfter_placesBlockAfterCurrent() {
        val out = ShufflePlan.insertAllAfter(listOf(1L, 2L, 3L), afterId = 1L, ids = listOf(8L, 9L))
        assertEquals(listOf(1L, 8L, 9L, 2L, 3L), out)
    }

    @Test
    fun moveSeq_reachesTargetAndIsMinimalForAlreadyOrdered() {
        assertEquals(emptyList<Pair<Int, Int>>(), ShufflePlan.moveSeq(listOf(1L, 2L, 3L), listOf(1L, 2L, 3L)))
        val moves = ShufflePlan.moveSeq(listOf(1L, 2L, 3L), listOf(3L, 1L, 2L))
        assertEquals(1, moves.size)
    }

    @Test
    fun moveSeq_canBeReplayedToProduceTarget() {
        val current = listOf(5L, 1L, 9L, 2L, 7L)
        val target = listOf(9L, 5L, 2L, 7L, 1L)
        val working = current.toMutableList()
        for ((from, to) in ShufflePlan.moveSeq(current, target)) {
            working.add(to, working.removeAt(from))
        }
        assertEquals(target, working)
    }

    @Test
    fun duplicatesOfTheSameSong_stayDistinctByQueueKey() {
        // Two entries of the same song: keys 1 and 2. Restoring must not collapse
        // them and must keep the right one current.
        val snapshot = listOf(1L, 2L, 3L)
        val live = listOf(3L, 2L, 1L)
        val restored = ShufflePlan.restore(live, snapshot)
        assertEquals(listOf(1L, 2L, 3L), restored)
        assertEquals(3, restored.distinct().size)
        assertEquals(1, restored.indexOf(2L))
    }

    @Test
    fun applyOrder_keepsEveryEntryAndAppendsUnknown() {
        data class Item(val key: Long, val label: String)
        val items = listOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val out = ShufflePlan.applyOrder(listOf(3L, 1L), items) { it.key }
        assertEquals(listOf(3L, 1L, 2L), out.map { it.key })
        assertEquals(3, out.size)
    }

    @Test
    fun onThenOff_roundTripsThroughTheRealSequence() {
        // Mirrors PlayerManager: snapshot -> shuffle tail -> restore.
        val original = (1L..8L).toList()
        val snapshot = original
        val shuffled = ShufflePlan.shuffledTail(original, fromIndex = 1, random = Random(11))
        assertEquals(original.toSet(), shuffled.toSet())
        assertEquals(original.first(), shuffled.first())
        val restored = ShufflePlan.restore(shuffled, snapshot)
        assertEquals(original, restored)
    }
}
