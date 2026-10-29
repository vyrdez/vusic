package com.feishin.mobile.core.common

import kotlin.random.Random

/**
 * Pure ordering rules for app-side shuffle and its restore.
 *
 * Everything works on *queue ids* (one per queue entry), never on song ids or
 * positions, so two copies of the same song stay distinct and removing an item
 * cannot shift the meaning of a snapshot.
 *
 * Rules implemented here:
 *  - Turning shuffle on snapshots the current order and permutes only the
 *    not-yet-played tail, so the current item and history keep their slots.
 *  - Turning shuffle off restores the snapshot order: entries removed while
 *    shuffled are dropped, and entries added while shuffled (which are not in
 *    the snapshot) are appended in the order they were added.
 *  - A manual drag while shuffled is treated as the new base order (documented).
 */
object ShufflePlan {

    /** Permutes the tail from [fromIndex] while keeping the head untouched. */
    fun shuffledTail(order: List<Long>, fromIndex: Int, random: Random): List<Long> {
        if (fromIndex <= 0) return order.shuffled(random)
        if (fromIndex >= order.size) return order
        val head = order.subList(0, fromIndex)
        val tail = order.subList(fromIndex, order.size).shuffled(random)
        return head + tail
    }

    /**
     * The order to restore: [snapshotIds] filtered to surviving entries (kept in
     * snapshot order), followed by any live entry missing from the snapshot
     * (added while shuffled) in its current relative order.
     */
    fun restore(liveIds: List<Long>, snapshotIds: List<Long>): List<Long> {
        val live = liveIds.toSet()
        val out = ArrayList<Long>(liveIds.size)
        val seen = HashSet<Long>(seenSize(liveIds))
        for (id in snapshotIds) {
            if (id in live && seen.add(id)) out += id
        }
        for (id in liveIds) {
            if (seen.add(id)) out += id
        }
        return out
    }

    /** Inserts [id] directly after [afterId] (appended when it is absent). */
    fun insertAfter(order: List<Long>, afterId: Long?, id: Long): List<Long> {
        val at = order.indexOf(afterId)
        return if (at < 0) order + id
        else order.toMutableList().apply { add(at + 1, id) }
    }

    fun insertAllAfter(order: List<Long>, afterId: Long?, ids: List<Long>): List<Long> {
        if (ids.isEmpty()) return order
        val at = order.indexOf(afterId)
        return if (at < 0) order + ids
        else order.toMutableList().apply { addAll(at + 1, ids) }
    }

    /**
     * Minimal `moveMediaItem(from, to)` sequence turning [current] into
     * [target]. Indices are live for the controller as the moves are applied in
     * order (each step mutates the list the next index is read from).
     *
     * Moves are used rather than remove+insert so nothing is ever removed,
     * re-inserted or re-prepared: the playing item keeps playing.
     */
    fun moveSeq(current: List<Long>, target: List<Long>): List<Pair<Int, Int>> {
        if (current == target) return emptyList()
        val working = current.toMutableList()
        val moves = ArrayList<Pair<Int, Int>>()
        for (i in target.indices) {
            val want = target[i]
            val at = working.indexOf(want)
            if (at < 0 || at == i) continue
            working.add(i, working.removeAt(at))
            moves += at to i
        }
        return moves
    }

    /** Reorders [items] into [order], appending anything not listed. */
    fun <T> applyOrder(order: List<Long>, items: List<T>, key: (T) -> Long): List<T> {
        val byId = HashMap<Long, T>(items.size)
        for (item in items) byId[key(item)] = item
        val out = ArrayList<T>(items.size)
        val seen = HashSet<Long>(items.size)
        for (id in order) {
            val item = byId[id]
            if (item != null && seen.add(id)) out += item
        }
        for (item in items) {
            if (seen.add(key(item))) out += item
        }
        return out
    }

    data class SlicePlan<T>(
        val head: List<T>,
        val tail: List<T>,
        val targetIndex: Int,
        val beforeCount: Int,
        val afterCount: Int,
    )

    /**
     * Slices [reordered] around the item identified by [keepId] (or [currentIdx]),
     * preserving the active item across shuffle or reorder operations.
     */
    fun <T> sliceAroundKeep(
        beforeCount: Int,
        currentIdx: Int,
        reordered: List<T>,
        keepId: Long?,
        key: (T) -> Long,
    ): SlicePlan<T> {
        if (reordered.isEmpty()) {
            return SlicePlan(
                head = emptyList(),
                tail = emptyList(),
                targetIndex = 0,
                beforeCount = 0,
                afterCount = 0,
            )
        }
        val found = if (keepId != null) reordered.indexOfFirst { key(it) == keepId } else -1
        val targetIndex = if (found >= 0) found else currentIdx.coerceIn(0, reordered.lastIndex)
        val head = reordered.subList(0, targetIndex)
        val tail = reordered.subList(targetIndex + 1, reordered.size)
        val safeCurrent = if (beforeCount > 0) currentIdx.coerceIn(0, beforeCount - 1) else 0
        val itemsBefore = if (beforeCount > 0) safeCurrent else 0
        val itemsAfter = if (beforeCount > 0) (beforeCount - (safeCurrent + 1)).coerceAtLeast(0) else 0
        return SlicePlan(
            head = head,
            tail = tail,
            targetIndex = targetIndex,
            beforeCount = itemsBefore,
            afterCount = itemsAfter,
        )
    }

    private fun seenSize(ids: List<Long>): Int = ids.size.coerceAtLeast(4)
}

typealias SlicePlan<T> = ShufflePlan.SlicePlan<T>
