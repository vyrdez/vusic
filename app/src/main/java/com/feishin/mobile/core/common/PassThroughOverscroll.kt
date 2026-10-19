package com.feishin.mobile.core.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * A pass-through [OverscrollEffect]: the scroll is performed exactly as it
 * would be with no effect installed, and nothing is drawn.
 *
 * Needed by the queue sheet's track list, and the reason is a hard behaviour of
 * Compose rather than a preference.
 *
 * A LazyColumn installs an overscroll effect by default (the stretch on old
 * APIs, the system glow from API 31). Compose's own [androidx.compose.foundation.OverscrollEffect]
 * swallows the delta at a boundary: from the frame the list is at its first
 * item and the finger keeps moving down, the whole gesture is handed to the
 * effect and the delta never enters the nested-scroll chain. Measured on the
 * emulator, 106 consecutive drag frames at the list top produced
 * `onPreScroll(available.y = 0)` and `onPostScroll(consumed.y = -0.0,
 * available.y = 0)` - every one of them zero.
 *
 * That is why dragging down the song list could not close the queue sheet: the
 * handoff connection had nothing to receive, and only the header's own
 * `draggable` could move the sheet.
 *
 * Passing the delta through puts the delta back on the nested-scroll chain, so
 * [QueueHandoff] can decide its owner. What is given up is the stretch/glow
 * animation on THIS list, at the boundary where the sheet takes over instead -
 * which is exactly the behaviour that was asked for.
 */
@OptIn(ExperimentalFoundationApi::class)
internal object PassThroughOverscroll : OverscrollEffect {

    /** No animation, so nothing is ever "in progress". */
    override val isInProgress: Boolean get() = false

    override val effectModifier: Modifier get() = Modifier

    /**
     * Perform the scroll unchanged and report what it could not take. A real
     * edge effect keeps the un-scrolled remainder for its own animation; here
     * it stays un-consumed so it reaches an ancestor.
     */
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset = performScroll(delta)

    /** Flings pass straight through: no effect to settle. */
    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        performFling(velocity)
    }
}
