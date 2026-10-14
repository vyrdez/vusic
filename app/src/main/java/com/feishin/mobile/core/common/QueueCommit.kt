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
}
