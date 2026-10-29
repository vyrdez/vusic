package com.feishin.mobile.core.common

/**
 * Commit-on-drop validation for queue drag-and-drop.
 *
 * The UI reorders a local copy during the drag and commits once on drop.
 * Between the last drag frame and the commit, the live queue can change
 * (radio appends, swipe-removes): applying a stale order would corrupt or drop
 * entries. Everything works on unique per-entry queue ids, so two copies of
 * the same song stay distinct.
 */
object QueueCommit {
    /**
     * Validates a drop: returns the order to apply, or null when there is
     * nothing to do (identical) or the target is stale (a different id set —
     * entries were added or removed mid-drag, so the drop is ignored).
     */
    fun targetIds(liveIds: List<Long>, shownIds: List<Long>): List<Long>? {
        if (liveIds == shownIds) return null
        if (shownIds.toSet() != liveIds.toSet()) return null
        return shownIds
    }

    /**
     * Returns the (fromIndex, toIndex) pair if [shownIds] is reached from [liveIds]
     * by moving a single item, or null if multiple moves are needed or the target
     * is invalid/identical.
     */
    fun singleMove(liveIds: List<Long>, shownIds: List<Long>): Pair<Int, Int>? {
        val target = targetIds(liveIds, shownIds) ?: return null
        if (liveIds.size != target.size) return null
        var firstDiff = -1
        for (i in liveIds.indices) {
            if (liveIds[i] != target[i]) {
                firstDiff = i
                break
            }
        }
        if (firstDiff < 0) return null
        var lastDiff = -1
        for (i in liveIds.indices.reversed()) {
            if (liveIds[i] != target[i]) {
                lastDiff = i
                break
            }
        }
        if (firstDiff == lastDiff) return null

        // Check drag down (firstDiff -> lastDiff)
        val testDown = liveIds.toMutableList().apply { add(lastDiff, removeAt(firstDiff)) }
        if (testDown == target) return firstDiff to lastDiff

        // Check drag up (lastDiff -> firstDiff)
        val testUp = liveIds.toMutableList().apply { add(firstDiff, removeAt(lastDiff)) }
        if (testUp == target) return lastDiff to firstDiff

        return null
    }
}
