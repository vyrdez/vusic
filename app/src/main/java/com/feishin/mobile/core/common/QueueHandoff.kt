package com.feishin.mobile.core.common

/**
 * Who owns a drag delta inside the queue sheet's track list: the list, or the
 * sheet that is about to close.
 *
 * The sheet may only take a delta when a FINGER is producing it. The 8.1 rule
 * - a fling the list consumed must never close the sheet just because it
 * reached the top - is the `userInput` gate here.
 *
 * Why this is a pure function and not a comment on a callback: the decision has
 * to be made in `onPreScroll`, BEFORE the list sees the delta, and the reason
 * is an implementation detail of Compose that is invisible at the call site.
 *
 * A LazyColumn overscrolls at its boundaries by default (the stretch/glow
 * effect). The first frame after the list reaches its first item, the effect
 * takes over: the list reports the delta as CONSUMED and dispatches nothing
 * onward, so the parent's `onPostScroll` sees `available.y == 0`. A handoff
 * written only there therefore moves the sheet for exactly one frame - about
 * 80px on this device - and then sticks, which is the reported bug
 * ("dragging down through the list never closes the queue"). Taking the delta
 * in `onPreScroll` means the list never sees it, the overscroll never starts,
 * and the sheet follows from the first pixel.
 *
 * @param deltaY pointer delta in px; positive means the finger moved DOWN,
 *        which closes the sheet.
 * @param sheetHeightPx measured sheet height, used to turn px into progress.
 * @param userInput true only for a finger drag (`NestedScrollSource.UserInput`).
 * @param listAtTop the list's first visible item is the first item, unscrolled.
 * @param sheetDragging a drag currently owns the sheet (the finger is already
 *        on it), so direction changes inside one gesture are handled here
 *        rather than by the header's `draggable`.
 * @param sheetAtOpen the sheet is back at (or indistinguishable from) OPEN.
 */
sealed interface ListHandoff {
    /** The list owns this delta. The sheet must not move. */
    data object PassToList : ListHandoff

    /** The sheet took the delta; the list must not see any of it. */
    data class TakeBySheet(val deltaProgress: Float) : ListHandoff
}

object QueueHandoff {

    /**
     * Below this a delta is noise (finger jitter, or the vertical component of
     * a horizontal swipe-to-dismiss). Letting those reach the sheet would
     * latch "the finger moved the sheet" for a gesture that never did.
     */
    const val MIN_TAKEN_DELTA_PX = 1.5f

    /**
     * Decides the owner of one drag delta. See [ListHandoff] for the meaning of
     * the flags and for why this runs in `onPreScroll`.
     */
    fun decide(
        deltaY: Float,
        sheetHeightPx: Float,
        userInput: Boolean,
        listAtTop: Boolean,
        sheetDragging: Boolean,
        sheetAtOpen: Boolean,
        minTakenDeltaPx: Float = MIN_TAKEN_DELTA_PX,
    ): ListHandoff {
        // A fling belongs to the list, full stop: it must be able to coast to
        // the top and stop there without the sheet following.
        if (!userInput) return ListHandoff.PassToList
        if (sheetHeightPx <= 0f) return ListHandoff.PassToList
        if (kotlin.math.abs(deltaY) < minTakenDeltaPx) return ListHandoff.PassToList

        // Mid-gesture: the sheet already owns the finger. It takes the rest of
        // the drag in either direction, so dragging back up closes the gap
        // first... until it is fully open again, and only then does the list
        // get the gesture back.
        if (sheetDragging) {
            if (deltaY < 0f && sheetAtOpen) return ListHandoff.PassToList
            return ListHandoff.TakeBySheet(-deltaY / sheetHeightPx)
        }

        // A fresh gesture: the list owns it until it is at its first item.
        // Dragging UP always belongs to the list, even at the top, so an
        // upward drag can never move the sheet.
        if (deltaY > 0f && listAtTop) return ListHandoff.TakeBySheet(-deltaY / sheetHeightPx)
        return ListHandoff.PassToList
    }
}
