package com.feishin.mobile.core.common

/**
 * Where the "now playing" highlight belongs in the queue list.
 *
 * Identity, never position. The queue reorders a LOCAL copy of the list on
 * every drag frame, so a row's index changes many times during one gesture
 * while the player's current entry does not move at all. Anything derived from
 * an index therefore slides off the song and lands on whatever entry happens to
 * occupy that slot - which is exactly the "highlighted row stays put while the
 * song inside changes" bug.
 *
 * Matching the stable per-entry id instead keeps the highlight and the playing
 * indicator on the same row for the whole drag, while the other rows shift
 * around it, and still picks the right row after the drop. Two entries of the
 * same song carry two different ids, so the entry that is playing is
 * unambiguous.
 */
object QueueHighlight {

    /**
     * Resolved highlight for one row.
     *
     * [isCurrent] draws the container and the playing indicator; [isPlayed]
     * dims the rows the player has already passed.
     */
    data class State(val isCurrent: Boolean = false, val isPlayed: Boolean = false)

    /**
     * Highlight for the row at [rowIndex] of [displayIds].
     *
     * @param displayIds the queue ids in the order they are shown right now
     *  (the local, in-drag order - not the player's order).
     * @param currentQueueId the id of the entry the player is on, or null when
     *  nothing is playing.
     */
    fun state(displayIds: List<Long>, rowIndex: Int, currentQueueId: Long?): State {
        if (currentQueueId == null || rowIndex !in displayIds.indices) return State()
        val current = displayIds.indexOf(currentQueueId)
        if (current < 0) return State()
        return State(isCurrent = rowIndex == current, isPlayed = rowIndex < current)
    }

    /** Index of [currentQueueId] in [displayIds], or -1 when it is absent. */
    fun indexOf(displayIds: List<Long>, currentQueueId: Long?): Int =
        if (currentQueueId == null) -1 else displayIds.indexOf(currentQueueId)
}
