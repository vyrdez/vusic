package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue's drag handoff. Every case is a rule the gesture has to obey, so
 * each test names the rule rather than the code path.
 */
class QueueHandoffTest {

    private val height = 1872f

    private fun decide(
        deltaY: Float,
        userInput: Boolean = true,
        listAtTop: Boolean = false,
        sheetDragging: Boolean = false,
        sheetAtOpen: Boolean = true,
    ) = QueueHandoff.decide(
        deltaY = deltaY,
        sheetHeightPx = height,
        userInput = userInput,
        listAtTop = listAtTop,
        sheetDragging = sheetDragging,
        sheetAtOpen = sheetAtOpen,
    )

    private fun taken(d: ListHandoff): Float {
        assertTrue("expected the sheet to take the delta, got $d", d is ListHandoff.TakeBySheet)
        return (d as ListHandoff.TakeBySheet).deltaProgress
    }

    // ---- Rule: the list owns the gesture until it is at its first item ----

    @Test
    fun draggingDownWhileTheListCanStillScrollMovesNothing() {
        assertEquals(ListHandoff.PassToList, decide(deltaY = 40f, listAtTop = false))
    }

    @Test
    fun draggingUpAlwaysBelongsToTheListEvenAtItsTop() {
        assertEquals(ListHandoff.PassToList, decide(deltaY = -40f, listAtTop = true))
    }

    // ---- Rule: at the top, a downward drag is the sheet's ----

    @Test
    fun draggingDownAtTheTopHandsTheDeltaToTheSheet() {
        assertEquals(-40f / height, taken(decide(deltaY = 40f, listAtTop = true)), 1e-6f)
    }

    @Test
    fun theSheetReceivesTheWholeDeltaSoThereIsNoDeadZone() {
        // Progress the sheet must reach after 400px of finger travel.
        assertEquals(-400f / height, taken(decide(deltaY = 400f, listAtTop = true)), 1e-6f)
    }

    @Test
    fun draggingDownIsNegativeProgressBecauseDownCloses() {
        assertTrue(taken(decide(deltaY = 10f, listAtTop = true)) < 0f)
    }

    // ---- Rule: a fling never moves the sheet (8.1) ----

    @Test
    fun aFlingDownAtTheTopLeavesTheSheetAlone() {
        assertEquals(
            ListHandoff.PassToList,
            decide(deltaY = 60f, userInput = false, listAtTop = true),
        )
    }

    @Test
    fun aFlingDownNeverResumesEvenMidSheetDrag() {
        assertEquals(
            ListHandoff.PassToList,
            decide(deltaY = 60f, userInput = false, listAtTop = true, sheetDragging = true, sheetAtOpen = false),
        )
    }

    // ---- Rule: direction change inside ONE drag ----

    @Test
    fun onceTheSheetHasTheFingerAnUpwardDeltaStillGoesToTheSheet() {
        // Dragging back up must close the gap, not scroll the list behind it.
        val d = decide(
            deltaY = -60f,
            listAtTop = true,
            sheetDragging = true,
            sheetAtOpen = false,
        )
        assertEquals(60f / height, taken(d), 1e-6f)
    }

    @Test
    fun upwardIsHandedBackToTheListOnceTheSheetIsOpenAgain() {
        assertEquals(
            ListHandoff.PassToList,
            decide(
                deltaY = -60f,
                listAtTop = false,
                sheetDragging = true,
                sheetAtOpen = true,
            ),
        )
    }

    @Test
    fun aDownwardDeltaMidDragStaysWithTheSheetEvenIfTheListIsNotAtItsTop() {
        // The list cannot scroll upward while the sheet is being dragged down,
        // so it must not be handed a delta it cannot use.
        val d = decide(
            deltaY = 30f,
            listAtTop = false,
            sheetDragging = true,
            sheetAtOpen = false,
        )
        assertEquals(-30f / height, taken(d), 1e-6f)
    }

    // ---- Rule: the leftover after the list reaches its top ----

    @Test
    fun leftoverFromAPartlyConsumedFrameIsForwarded() {
        // `onPostScroll` gets the remainder with the list already AT the top:
        // that is the frame right after the list consumed its last pixel.
        val d = decide(deltaY = 12f, listAtTop = true, sheetDragging = false)
        assertEquals(-12f / height, taken(d), 1e-6f)
    }

    // ---- Rule: noise never latches a sheet drag ----

    @Test
    fun subPixelJitterNeverMovesTheSheet() {
        assertEquals(ListHandoff.PassToList, decide(deltaY = 0.4f, listAtTop = true))
    }

    @Test
    fun theVerticalWobbleOfASwipeToRemoveNeverMovesTheSheet() {
        assertEquals(ListHandoff.PassToList, decide(deltaY = 1.2f, listAtTop = true))
    }

    @Test
    fun anExactlyZeroDeltaIsIgnored() {
        assertEquals(ListHandoff.PassToList, decide(deltaY = 0f, listAtTop = true))
    }

    // ---- Rule: nothing is decided before the sheet has a size ----

    @Test
    fun anUnmeasuredSheetNeverEatsADelta() {
        assertEquals(
            ListHandoff.PassToList,
            QueueHandoff.decide(
                deltaY = 40f,
                sheetHeightPx = 0f,
                userInput = true,
                listAtTop = true,
                sheetDragging = false,
                sheetAtOpen = true,
            ),
        )
    }

    // ---- Rule: a short queue that cannot scroll still closes ----

    @Test
    fun aShortQueueIsAlwaysAtItsTopSoDownwardClosesAndUpwardDoesNothing() {
        // With one or two rows the list can never leave its first item, which
        // is the `listAtTop = true` case for the whole gesture.
        assertTrue(taken(decide(deltaY = 80f, listAtTop = true)) < 0f)
        assertEquals(ListHandoff.PassToList, decide(deltaY = -80f, listAtTop = true))
    }
}
