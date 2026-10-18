package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue highlight must follow the current ENTRY through a live reorder,
 * not the slot it used to occupy. Ids: 1..5 are distinct entries; 90/91 are two
 * entries holding the same song (two ids, one track).
 */
class QueueHighlightTest {

    private val noHighlight = QueueHighlight.State()

    // ---- the current entry, dragged -------------------------------------

    @Test
    fun currentDraggedDown_highlightFollowsTheRow() {
        // current = 2 (index 1); drag it down past 3.
        val display = listOf(1L, 3L, 2L, 4L)
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, rowIndex = 2, currentQueueId = 2L),
        )
        // The slot it came from is now a played row, not the current one.
        assertEquals(
            QueueHighlight.State(isCurrent = false, isPlayed = true),
            QueueHighlight.state(display, rowIndex = 1, currentQueueId = 2L),
        )
    }

    @Test
    fun currentDraggedUp_highlightFollowsTheRow() {
        // current = 4 (index 3); drag it to the very top.
        val display = listOf(4L, 1L, 2L, 3L)
        assertTrue(QueueHighlight.state(display, 0, 4L).isCurrent)
        // Nothing is behind a current entry that sits first.
        assertFalse(QueueHighlight.state(display, 0, 4L).isPlayed)
        // Everything else now comes NEXT, so it is not a played row either.
        for (i in 1..3) {
            assertEquals(
                QueueHighlight.State(isCurrent = false, isPlayed = false),
                QueueHighlight.state(display, i, 4L),
            )
        }
    }

    // ---- another entry dragged across the current ------------------------

    @Test
    fun anotherRowDraggedPastCurrent_highlightStaysOnTheCurrentSong() {
        // current = 2 (index 1). Drag 4 from the bottom to the top.
        val display = listOf(4L, 1L, 2L, 3L)
        // The highlight is on the ROW now holding 2, which shifted right.
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, rowIndex = 2, currentQueueId = 2L),
        )
        // The row that moved past it is NOT the current row, even though it
        // now sits where the current entry used to be. Having moved ABOVE the
        // current entry it is a played (dimmed) row.
        assertEquals(
            QueueHighlight.State(isCurrent = false, isPlayed = true),
            QueueHighlight.state(display, rowIndex = 0, currentQueueId = 2L),
        )
    }

    @Test
    fun anotherRowDraggedPastCurrent_fromAbove() {
        // current = 3 (index 2). Drag 1 from the top down past it.
        val display = listOf(2L, 3L, 1L, 4L)
        assertTrue(QueueHighlight.state(display, 1, 3L).isCurrent)
        // 1 is now below the current entry, i.e. still to come, not played.
        assertFalse(QueueHighlight.state(display, 2, 3L).isCurrent)
        assertFalse(QueueHighlight.state(display, 2, 3L).isPlayed)
        // 2 stayed above the current entry, so it is a played row.
        assertTrue(QueueHighlight.state(display, 0, 3L).isPlayed)
    }

    // ---- ends ------------------------------------------------------------

    @Test
    fun firstAndLastRows() {
        val display = listOf(1L, 2L, 3L)
        // The first row is current: nothing is behind it yet.
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, 0, 1L),
        )
        // The last row is current: the two above it are played.
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, 2, 3L),
        )
        assertEquals(
            QueueHighlight.State(isCurrent = false, isPlayed = true),
            QueueHighlight.state(display, 1, 3L),
        )
        assertEquals(
            QueueHighlight.State(isCurrent = false, isPlayed = true),
            QueueHighlight.state(display, 0, 3L),
        )
    }

    @Test
    fun singleEntryQueue() {
        val display = listOf(7L)
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, 0, 7L),
        )
    }

    // ---- duplicates: the same song twice ---------------------------------

    @Test
    fun duplicateSong_marksOnlyTheEntryThatIsPlaying() {
        // Two entries of one song, ids 90 and 91. The second copy plays.
        val display = listOf(90L, 91L)
        assertTrue(QueueHighlight.state(display, 1, 91L).isCurrent)
        assertFalse(QueueHighlight.state(display, 0, 91L).isCurrent)
        // A position-based check (index 0) would have marked the WRONG copy.
        assertTrue(QueueHighlight.state(display, 0, 91L).isPlayed)
    }

    @Test
    fun duplicateSong_afterDraggingThePlayingCopyToTheTop() {
        val display = listOf(91L, 90L)
        assertEquals(
            QueueHighlight.State(isCurrent = true, isPlayed = false),
            QueueHighlight.state(display, 0, 91L),
        )
        // The other copy is still to come, so it is not a played row.
        assertEquals(
            QueueHighlight.State(isCurrent = false, isPlayed = false),
            QueueHighlight.state(display, 1, 91L),
        )
    }

    // ---- absence ---------------------------------------------------------

    @Test
    fun nothingPlaying_meansNoHighlight() {
        assertEquals(noHighlight, QueueHighlight.state(listOf(1L, 2L), 0, null))
        assertEquals(noHighlight, QueueHighlight.state(listOf(1L, 2L), 1, null))
    }

    @Test
    fun currentIdNotInTheList_meansNoHighlight() {
        // A stale id (queue replaced mid-composition) must not paint a row.
        assertEquals(noHighlight, QueueHighlight.state(listOf(1L, 2L), 0, 99L))
    }

    @Test
    fun outOfRangeIndex_meansNoHighlight() {
        assertEquals(noHighlight, QueueHighlight.state(listOf(1L, 2L), 5, 1L))
        assertEquals(noHighlight, QueueHighlight.state(emptyList(), 0, 1L))
    }

    // ---- index mapping ---------------------------------------------------

    @Test
    fun indexOfMapsIdToItsLivePosition() {
        assertEquals(2, QueueHighlight.indexOf(listOf(1L, 3L, 2L), 2L))
        assertEquals(0, QueueHighlight.indexOf(listOf(4L, 1L, 2L), 4L))
        assertEquals(-1, QueueHighlight.indexOf(listOf(1L), 2L))
        assertEquals(-1, QueueHighlight.indexOf(listOf(1L), null))
    }
}
