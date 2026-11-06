package com.feishin.mobile.core.common

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleModelTest {

    @Test
    fun shuffle_movesCurrentTrackToIndexZeroAndPermutesAllOthers() {
        val ids = (1L..50L).toList()
        val initial = ShuffleState(
            displayedIds = ids,
            originalIds = emptyList(),
            currentQueueId = 5L, // index 4
            shuffleEnabled = false,
        )

        val shuffled = ShuffleModel.shuffle(initial, Random(42))

        assertTrue(shuffled.shuffleEnabled)
        assertEquals(5L, shuffled.currentQueueId)
        assertEquals(5L, shuffled.displayedIds.first())
        assertEquals(ids, shuffled.originalIds)
        assertEquals(4, shuffled.anchorOriginalIndex)
        assertEquals(50, shuffled.displayedIds.size)
        assertEquals(ids.toSet(), shuffled.displayedIds.toSet())
        // Items 1..4 (originally before index 4) must be in the tail (indices 1..49)
        assertTrue(shuffled.displayedIds.drop(1).containsAll(listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun shuffle_whenAlreadyShuffled_preservesExistingOriginalIds() {
        val catalog = listOf(10L, 20L, 30L, 40L, 50L)
        val initial = ShuffleState(
            displayedIds = catalog,
            currentQueueId = 20L,
        )

        val firstShuffle = ShuffleModel.shuffle(initial, Random(1))
        val tapped = ShuffleModel.select(firstShuffle, 40L)
        val secondShuffle = ShuffleModel.shuffle(tapped, Random(2))

        assertEquals(catalog, secondShuffle.originalIds)
        assertEquals(40L, secondShuffle. displayedIds.first())
        assertEquals(40L, secondShuffle.currentQueueId)
        assertEquals(3, secondShuffle.anchorOriginalIndex)
    }

    @Test
    fun shuffle_whenCurrentQueueIdNullOrAbsent_permutesAllAndSetsFirstAsCurrent() {
        val ids = listOf(10L, 20L, 30L, 40L)
        val nullCurrent = ShuffleState(displayedIds = ids, currentQueueId = null)

        val res1 = ShuffleModel.shuffle(nullCurrent, Random(7))
        assertTrue(res1.shuffleEnabled)
        assertEquals(ids, res1.originalIds)
        assertEquals(ids.toSet(), res1.displayedIds.toSet())
        assertEquals(res1.displayedIds.first(), res1.currentQueueId)
        assertEquals(ids.indexOf(res1.currentQueueId), res1.anchorOriginalIndex)

        val absentCurrent = ShuffleState(displayedIds = ids, currentQueueId = 999L)
        val res2 = ShuffleModel.shuffle(absentCurrent, Random(7))
        assertEquals(res2.displayedIds.first(), res2.currentQueueId)
    }

    @Test
    fun shuffle_emptyQueue_staysEmpty() {
        val empty = ShuffleState()
        val shuffled = ShuffleModel.shuffle(empty, Random(1))

        assertTrue(shuffled.shuffleEnabled)
        assertTrue(shuffled.displayedIds.isEmpty())
        assertTrue(shuffled.originalIds.isEmpty())
        assertNull(shuffled.currentQueueId)
        assertEquals(0, shuffled.anchorOriginalIndex)
    }

    @Test
    fun unshuffle_restoresExactOriginalOrderAndPlacesCurrentAtOriginalSlot() {
        val ids = (100L..149L).toList()
        val initial = ShuffleState(displayedIds = ids, currentQueueId = 104L) // slot 4

        val shuffled = ShuffleModel.shuffle(initial, Random(99))
        assertEquals(0, shuffled.displayedIds.indexOf(104L))

        val unshuffled = ShuffleModel.unshuffle(shuffled)
        assertFalse(unshuffled.shuffleEnabled)
        assertTrue(unshuffled.originalIds.isEmpty())
        assertEquals(ids, unshuffled.displayedIds)
        assertEquals(104L, unshuffled.currentQueueId)
        assertEquals(4, unshuffled.displayedIds.indexOf(104L))
        assertEquals(0, unshuffled.anchorOriginalIndex)
    }

    @Test
    fun unshuffle_afterSelectingDifferentSongWhileShuffled_placesSelectedSongAtItsOriginalSlot() {
        val ids = (1L..20L).toList()
        val initial = ShuffleState(displayedIds = ids, currentQueueId = 3L)

        val shuffled = ShuffleModel.shuffle(initial, Random(12))
        // User taps track 15L while shuffled
        val afterTap = ShuffleModel.select(shuffled, 15L)
        assertEquals(15L, afterTap.currentQueueId)

        val unshuffled = ShuffleModel.unshuffle(afterTap)
        assertFalse(unshuffled.shuffleEnabled)
        assertEquals(ids, unshuffled.displayedIds)
        assertEquals(15L, unshuffled.currentQueueId)
        assertEquals(14, unshuffled.displayedIds.indexOf(15L))
    }

    @Test
    fun select_onlyUpdatesCurrentQueueIdWithoutMutatingOrdersOrShuffleFlag() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        val shuffled = ShuffleModel.shuffle(
            ShuffleState(displayedIds = ids, currentQueueId = 2L),
            Random(5),
        )

        val selected = ShuffleModel.select(shuffled, 4L)
        assertEquals(4L, selected.currentQueueId)
        assertEquals(shuffled.displayedIds, selected.displayedIds)
        assertEquals(shuffled.originalIds, selected.originalIds)
        assertEquals(shuffled.shuffleEnabled, selected.shuffleEnabled)

        // Ignored when tappedQueueId is not in displayedIds
        val ignored = ShuffleModel.select(selected, 999L)
        assertEquals(4L, ignored.currentQueueId)
        assertEquals(selected, ignored)
    }

    @Test
    fun unshuffle_fallback_whenPriorSlotsDeletedWhileShuffled_shiftsCurrentSlotCleanly() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L)
        val initial = ShuffleState(displayedIds = ids, currentQueueId = 40L) // slot 3

        var state = ShuffleModel.shuffle(initial, Random(3))
        // Delete 10L and 30L (both prior to 40L in originalIds)
        state = ShuffleModel.remove(state, 10L)
        state = ShuffleModel.remove(state, 30L)

        val unshuffled = ShuffleModel.unshuffle(state)
        assertEquals(listOf(20L, 40L, 50L), unshuffled.displayedIds)
        assertEquals(40L, unshuffled.currentQueueId)
        assertEquals(1, unshuffled.displayedIds.indexOf(40L))
    }

    @Test
    fun unshuffle_fallback_whenCurrentTrackIsOrphanAddedWhileShuffled_insertsAtAnchorSlot() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L)
        // Shuffle started at 30L (original index 2)
        val shuffled = ShuffleModel.shuffle(
            ShuffleState(displayedIds = ids, currentQueueId = 30L),
            Random(8),
        )
        assertEquals(2, shuffled.anchorOriginalIndex)

        // Simulate orphan tracks 90L and 91L present in displayedIds but not in originalIds
        val withOrphans = shuffled.copy(
            displayedIds = shuffled.displayedIds + listOf(90L, 91L),
            currentQueueId = 90L,
        )

        val unshuffled = ShuffleModel.unshuffle(withOrphans)
        assertFalse(unshuffled.shuffleEnabled)
        assertEquals(90L, unshuffled.currentQueueId)
        // 90L inserted at anchorOriginalIndex = 2 inside survivingOrig, 91L appended at end
        assertEquals(listOf(10L, 20L, 90L, 30L, 40L, 50L, 91L), unshuffled.displayedIds)
        assertEquals(2, unshuffled.displayedIds.indexOf(90L))
    }

    @Test
    fun unshuffle_fallback_whenCurrentTrackRemovedOrMissing_resolvesToFirstItem() {
        val ids = listOf(10L, 20L, 30L, 40L)
        val shuffled = ShuffleModel.shuffle(
            ShuffleState(displayedIds = ids, currentQueueId = 20L),
            Random(4),
        )

        // Simulate missing currentQueueId (not in liveSet)
        val corrupted = shuffled.copy(currentQueueId = 999L)
        val unshuffled = ShuffleModel.unshuffle(corrupted)

        assertFalse(unshuffled.shuffleEnabled)
        assertEquals(ids, unshuffled.displayedIds)
        assertEquals(10L, unshuffled.currentQueueId)

        // Null currentQueueId
        val nullCur = shuffled.copy(currentQueueId = null)
        val unshuffledNull = ShuffleModel.unshuffle(nullCur)
        assertEquals(ids, unshuffledNull.displayedIds)
        assertEquals(10L, unshuffledNull.currentQueueId)
    }

    @Test
    fun playNext_whileUnshuffled_insertsAfterCurrentOnlyInDisplayedIds() {
        val state = ShuffleState(
            displayedIds = listOf(1L, 2L, 3L),
            currentQueueId = 2L,
            shuffleEnabled = false,
        )

        val next = ShuffleModel.playNext(state, listOf(8L, 9L))
        assertEquals(listOf(1L, 2L, 8L, 9L, 3L), next.displayedIds)
        assertTrue(next.originalIds.isEmpty())
        assertEquals(2L, next.currentQueueId)
    }

    @Test
    fun playNext_whileShuffled_insertsAfterCurrentInBothDisplayedAndOriginalIds() {
        val initial = ShuffleState(
            displayedIds = listOf(10L, 20L, 30L, 40L),
            currentQueueId = 20L,
        )
        val shuffled = ShuffleModel.shuffle(initial, Random(11))
        // 20L is at index 0 in displayedIds, and index 1 in originalIds
        val updated = ShuffleModel.playNext(shuffled, listOf(99L, 100L))

        assertEquals(listOf(20L, 99L, 100L), updated.displayedIds.take(3))
        assertEquals(listOf(10L, 20L, 99L, 100L, 30L, 40L), updated.originalIds)

        val unshuffled = ShuffleModel.unshuffle(updated)
        assertEquals(listOf(10L, 20L, 99L, 100L, 30L, 40L), unshuffled.displayedIds)
        assertEquals(20L, unshuffled.currentQueueId)
    }

    @Test
    fun playNext_whileShuffledWithOrphanCurrent_insertsAtAnchorInOriginalIds() {
        val shuffled = ShuffleState(
            displayedIds = listOf(90L, 10L, 20L, 30L),
            originalIds = listOf(10L, 20L, 30L),
            currentQueueId = 90L, // orphan
            shuffleEnabled = true,
            anchorOriginalIndex = 1,
        )

        val updated = ShuffleModel.playNext(shuffled, listOf(95L))
        assertEquals(listOf(90L, 95L, 10L, 20L, 30L), updated.displayedIds)
        assertEquals(listOf(10L, 95L, 20L, 30L), updated.originalIds)
    }

    @Test
    fun addToQueue_appendsToDisplayedAndWhenShuffledToOriginal() {
        val unshuffled = ShuffleState(
            displayedIds = listOf(1L, 2L),
            currentQueueId = 1L,
            shuffleEnabled = false,
        )
        val addedUnshuffled = ShuffleModel.addToQueue(unshuffled, listOf(3L, 4L))
        assertEquals(listOf(1L, 2L, 3L, 4L), addedUnshuffled.displayedIds)
        assertTrue(addedUnshuffled.originalIds.isEmpty())

        val shuffled = ShuffleModel.shuffle(unshuffled, Random(2))
        val addedShuffled = ShuffleModel.addToQueue(shuffled, listOf(3L, 4L))
        assertEquals(listOf(3L, 4L), addedShuffled.displayedIds.takeLast(2))
        assertEquals(listOf(1L, 2L, 3L, 4L), addedShuffled.originalIds)

        val restored = ShuffleModel.unshuffle(addedShuffled)
        assertEquals(listOf(1L, 2L, 3L, 4L), restored.displayedIds)
    }

    @Test
    fun remove_removesFromBothListsAndAdvancesCurrentWhenActiveRemoved() {
        val state = ShuffleState(
            displayedIds = listOf(20L, 40L, 10L, 30L),
            originalIds = listOf(10L, 20L, 30L, 40L),
            currentQueueId = 40L, // index 1 in displayedIds
            shuffleEnabled = true,
        )

        // Removing non-current item preserves currentQueueId
        val afterNonCurrent = ShuffleModel.remove(state, 20L)
        assertEquals(40L, afterNonCurrent.currentQueueId)
        assertEquals(listOf(40L, 10L, 30L), afterNonCurrent.displayedIds)
        assertEquals(listOf(10L, 30L, 40L), afterNonCurrent.originalIds)

        // Removing current item (40L at index 0) advances to next item at index 0 (10L)
        val afterCurrent = ShuffleModel.remove(afterNonCurrent, 40L)
        assertEquals(10L, afterCurrent.currentQueueId)
        assertEquals(listOf(10L, 30L), afterCurrent.displayedIds)
        assertEquals(listOf(10L, 30L), afterCurrent.originalIds)

        // Removing last item when it is current clamps to new last item
        val atEnd = afterCurrent.copy(currentQueueId = 30L)
        val afterEndRemoved = ShuffleModel.remove(atEnd, 30L)
        assertEquals(10L, afterEndRemoved.currentQueueId)

        // Removing final remaining item sets currentQueueId to null
        val empty = ShuffleModel.remove(afterEndRemoved, 10L)
        assertTrue(empty.displayedIds.isEmpty())
        assertTrue(empty.originalIds.isEmpty())
        assertNull(empty.currentQueueId)
    }

    @Test
    fun reorder_updatesDisplayedIdsAndLeavesOriginalIdsUntouched() {
        val catalog = listOf(1L, 2L, 3L, 4L, 5L)
        val shuffled = ShuffleModel.shuffle(
            ShuffleState(displayedIds = catalog, currentQueueId = 3L),
            Random(6),
        )

        // User manually drags items while shuffled
        val manualOrder = listOf(3L, 5L, 4L, 2L, 1L)
        val reordered = ShuffleModel.reorder(shuffled, manualOrder)

        assertEquals(manualOrder, reordered.displayedIds)
        assertEquals(catalog, reordered.originalIds)
        assertEquals(3L, reordered.currentQueueId)

        // Unshuffling still restores the original pre-shuffle catalog order!
        val unshuffled = ShuffleModel.unshuffle(reordered)
        assertEquals(catalog, unshuffled.displayedIds)
        assertEquals(3L, unshuffled.currentQueueId)
    }

    @Test
    fun reorder_rejectsInvalidPermutation() {
        val state = ShuffleState(
            displayedIds = listOf(1L, 2L, 3L),
            originalIds = listOf(1L, 2L, 3L),
            currentQueueId = 1L,
            shuffleEnabled = true,
        )

        // Mismatched set of IDs leaves state unchanged
        val bad1 = ShuffleModel.reorder(state, listOf(1L, 2L, 99L))
        assertEquals(state, bad1)

        val bad2 = ShuffleModel.reorder(state, listOf(1L, 2L))
        assertEquals(state, bad2)
    }

    @Test
    fun duplicatesWithDistinctQueueIds_arePreservedAcrossAllOperations() {
        // Two copies of the same track receive distinct queueIds (101L and 102L)
        val queueIds = listOf(101L, 102L, 103L, 104L)
        val initial = ShuffleState(displayedIds = queueIds, currentQueueId = 102L)

        val shuffled = ShuffleModel.shuffle(initial, Random(19))
        assertEquals(4, shuffled.displayedIds.size)
        assertEquals(102L, shuffled.displayedIds.first())
        assertTrue(101L in shuffled.displayedIds)

        val unshuffled = ShuffleModel.unshuffle(shuffled)
        assertEquals(queueIds, unshuffled.displayedIds)
        assertEquals(102L, unshuffled.currentQueueId)
        assertEquals(1, unshuffled.displayedIds.indexOf(102L))
    }

    @Test
    fun fromPlaylistShuffle_buildsShuffledStateWithStartSongAtZeroAndFullCatalogInOriginal() {
        val catalog = (1L..30L).toList()

        val withExplicitStart = ShuffleModel.fromPlaylistShuffle(
            allQueueIds = catalog,
            startQueueId = 17L,
            random = Random(23),
        )
        assertTrue(withExplicitStart.shuffleEnabled)
        assertEquals(catalog, withExplicitStart.originalIds)
        assertEquals(17L, withExplicitStart.currentQueueId)
        assertEquals(17L, withExplicitStart.displayedIds.first())
        assertEquals(16, withExplicitStart.anchorOriginalIndex)
        assertEquals(catalog.toSet(), withExplicitStart.displayedIds.toSet())

        val withRandomStart = ShuffleModel.fromPlaylistShuffle(
            allQueueIds = catalog,
            startQueueId = null,
            random = Random(23),
        )
        assertTrue(withRandomStart.shuffleEnabled)
        assertEquals(catalog, withRandomStart.originalIds)
        assertEquals(withRandomStart.displayedIds.first(), withRandomStart.currentQueueId)
        assertEquals(catalog.toSet(), withRandomStart.displayedIds.toSet())

        // Unshuffling after header shuffle restores exact catalog order
        val unshuffled = ShuffleModel.unshuffle(withExplicitStart)
        assertEquals(catalog, unshuffled.displayedIds)
        assertEquals(17L, unshuffled.currentQueueId)
        assertEquals(16, unshuffled.displayedIds.indexOf(17L))
    }

    @Test
    fun property_roundTripAndInvariantsHoldAcrossRandomSequences() {
        val rng = Random(12345)
        repeat(50) { trial ->
            val size = rng.nextInt(1, 80)
            val baseIds = (1L..size.toLong()).toList()
            val startId = baseIds[rng.nextInt(size)]
            val initial = ShuffleState(displayedIds = baseIds, currentQueueId = startId)

            var state = ShuffleModel.shuffle(initial, rng)
            assertEquals("trial $trial", startId, state.displayedIds.first())
            assertEquals("trial $trial", baseIds.toSet(), state.displayedIds.toSet())
            assertEquals("trial $trial", baseIds, state.originalIds)

            // Random tap
            val tappedId = state.displayedIds[rng.nextInt(state.displayedIds.size)]
            val beforeTapDisplayed = state.displayedIds
            state = ShuffleModel.select(state, tappedId)
            assertEquals(beforeTapDisplayed, state.displayedIds)
            assertEquals(tappedId, state.currentQueueId)

            // Unshuffle must restore exact baseIds with tappedId at its original index
            val restored = ShuffleModel.unshuffle(state)
            assertEquals(baseIds, restored.displayedIds)
            assertEquals(tappedId, restored.currentQueueId)
            assertEquals(baseIds.indexOf(tappedId), restored.displayedIds.indexOf(tappedId))
            assertFalse(restored.shuffleEnabled)
            assertTrue(restored.originalIds.isEmpty())
        }
    }

    @Test
    fun scale_5000Items_shuffleAndUnshuffleAreFastAndExact() {
        val ids = (1L..5_000L).toList()
        val initial = ShuffleState(displayedIds = ids, currentQueueId = 2_500L)

        val startNanos = System.nanoTime()
        val shuffled = ShuffleModel.shuffle(initial, Random(77))
        val tapped = ShuffleModel.select(shuffled, 4_321L)
        val unshuffled = ShuffleModel.unshuffle(tapped)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        assertEquals(2_500L, shuffled.displayedIds.first())
        assertEquals(5_000, shuffled.displayedIds.size)
        assertEquals(ids, unshuffled.displayedIds)
        assertEquals(4_321L, unshuffled.currentQueueId)
        assertEquals(4_320, unshuffled.displayedIds.indexOf(4_321L))
        assertTrue("Expected < 200ms for 5,000 items, took ${elapsedMs}ms", elapsedMs < 200)
    }
}
