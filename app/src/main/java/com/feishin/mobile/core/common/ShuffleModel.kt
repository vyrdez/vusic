package com.feishin.mobile.core.common

import kotlin.random.Random

/**
 * Immutable state for app-side queue shuffle and catalog order restoration.
 *
 * Invariants:
 * - Every entry is a distinct, stable [Long] `queueId` (never a track ID or index).
 * - When [shuffleEnabled] is `false`, [originalIds] is empty and [displayedIds] holds
 *   the active playback/catalog order.
 * - When [shuffleEnabled] is `true`, [originalIds] holds the pre-shuffle catalog order
 *   (plus edits) and [displayedIds] is a permutation of the active queue items.
 */
data class ShuffleState(
    val displayedIds: List<Long> = emptyList(),
    val originalIds: List<Long> = emptyList(),
    val currentQueueId: Long? = null,
    val shuffleEnabled: Boolean = false,
    val anchorOriginalIndex: Int = 0,
)

/**
 * Pure, O(N) operations on [ShuffleState] with zero Android or Media3 dependencies.
 */
object ShuffleModel {

    /**
     * Enables shuffle: places [ShuffleState.currentQueueId] at index `0` (`#1`) and
     * permutes all remaining items in [ShuffleState.displayedIds] (including items that
     * were previously before the active track).
     */
    fun shuffle(state: ShuffleState, random: Random = Random.Default): ShuffleState {
        val displayed = state.displayedIds
        if (displayed.isEmpty()) {
            return ShuffleState(
                displayedIds = emptyList(),
                originalIds = emptyList(),
                currentQueueId = null,
                shuffleEnabled = true,
                anchorOriginalIndex = 0,
            )
        }

        val orig = if (state.shuffleEnabled && state.originalIds.isNotEmpty()) {
            state.originalIds
        } else {
            displayed
        }

        val curId = state.currentQueueId
        val hasCurrent = curId != null && displayed.contains(curId)

        val newDisplayed: List<Long>
        val resolvedCurrent: Long?
        if (hasCurrent && curId != null) {
            val others = ArrayList<Long>(displayed.size - 1)
            for (id in displayed) {
                if (id != curId) others.add(id)
            }
            others.shuffle(random)
            val result = ArrayList<Long>(displayed.size)
            result.add(curId)
            result.addAll(others)
            newDisplayed = result
            resolvedCurrent = curId
        } else {
            newDisplayed = displayed.shuffled(random)
            resolvedCurrent = newDisplayed.firstOrNull()
        }

        val anchorIdx = if (resolvedCurrent != null) {
            val idx = orig.indexOf(resolvedCurrent)
            if (idx >= 0) idx else state.anchorOriginalIndex.coerceIn(0, orig.size)
        } else {
            0
        }

        return ShuffleState(
            displayedIds = newDisplayed,
            originalIds = orig,
            currentQueueId = resolvedCurrent,
            shuffleEnabled = true,
            anchorOriginalIndex = anchorIdx,
        )
    }

    /**
     * Disables shuffle and restores catalog order relative to [ShuffleState.currentQueueId]
     * with explicit fallbacks for deleted prior tracks, orphan active tracks, and removed active tracks.
     */
    fun unshuffle(state: ShuffleState): ShuffleState {
        val displayed = state.displayedIds
        if (displayed.isEmpty()) {
            return ShuffleState(
                displayedIds = emptyList(),
                originalIds = emptyList(),
                currentQueueId = null,
                shuffleEnabled = false,
                anchorOriginalIndex = 0,
            )
        }

        val liveSet = HashSet<Long>(displayed.size * 2)
        liveSet.addAll(displayed)

        val origSet = HashSet<Long>(state.originalIds.size * 2)
        val survivingOrig = ArrayList<Long>(state.originalIds.size)
        for (id in state.originalIds) {
            if (id in liveSet && origSet.add(id)) {
                survivingOrig.add(id)
            } else {
                origSet.add(id)
            }
        }

        val orphans = ArrayList<Long>()
        val seenOrphan = HashSet<Long>()
        for (id in displayed) {
            if (id !in origSet && seenOrphan.add(id)) {
                orphans.add(id)
            }
        }

        val curId = state.currentQueueId
        val restored: List<Long>
        val resolvedCurrent: Long?

        when {
            // (a) Normal: currentQueueId is in survivingOrig
            curId != null && curId in origSet && curId in liveSet -> {
                val out = ArrayList<Long>(survivingOrig.size + orphans.size)
                out.addAll(survivingOrig)
                out.addAll(orphans)
                restored = out
                resolvedCurrent = curId
            }
            // (b) Orphan current track: added while shuffled and not in originalIds
            curId != null && curId in liveSet && curId !in origSet -> {
                val insertAt = state.anchorOriginalIndex.coerceIn(0, survivingOrig.size)
                val out = ArrayList<Long>(survivingOrig.size + orphans.size)
                out.addAll(survivingOrig.subList(0, insertAt))
                out.add(curId)
                out.addAll(survivingOrig.subList(insertAt, survivingOrig.size))
                for (orphan in orphans) {
                    if (orphan != curId) out.add(orphan)
                }
                restored = out
                resolvedCurrent = curId
            }
            // (c) Missing or removed current track
            else -> {
                val combined = ArrayList<Long>(survivingOrig.size + orphans.size)
                combined.addAll(survivingOrig)
                combined.addAll(orphans)
                restored = combined.ifEmpty { displayed }
                resolvedCurrent = restored.firstOrNull()
            }
        }

        return ShuffleState(
            displayedIds = restored,
            originalIds = emptyList(),
            currentQueueId = resolvedCurrent,
            shuffleEnabled = false,
            anchorOriginalIndex = 0,
        )
    }

    /**
     * Selects a track by [tappedQueueId] without modifying [ShuffleState.displayedIds],
     * [ShuffleState.originalIds], or [ShuffleState.shuffleEnabled].
     */
    fun select(state: ShuffleState, tappedQueueId: Long): ShuffleState {
        if (tappedQueueId !in state.displayedIds) return state
        if (state.currentQueueId == tappedQueueId) return state
        return state.copy(currentQueueId = tappedQueueId)
    }

    /**
     * Inserts [newQueueIds] immediately after [ShuffleState.currentQueueId] in [ShuffleState.displayedIds],
     * and when [ShuffleState.shuffleEnabled] is true, also in [ShuffleState.originalIds].
     */
    fun playNext(state: ShuffleState, newQueueIds: List<Long>): ShuffleState {
        if (newQueueIds.isEmpty()) return state
        val existingDisplayed = state.displayedIds.toHashSet()
        val deduped = newQueueIds.distinct().filterNot { it in existingDisplayed }
        if (deduped.isEmpty()) return state

        val curId = state.currentQueueId
        val newDisplayed = insertAfterOrAt(
            list = state.displayedIds,
            anchorId = curId,
            toInsert = deduped,
            fallbackIndex = 0,
        )

        val newOriginal = if (state.shuffleEnabled) {
            val existingOrig = state.originalIds.toHashSet()
            val dedupedOrig = deduped.filterNot { it in existingOrig }
            val fallbackIdx = state.anchorOriginalIndex.coerceIn(0, state.originalIds.size)
            insertAfterOrAt(
                list = state.originalIds,
                anchorId = curId,
                toInsert = dedupedOrig,
                fallbackIndex = fallbackIdx,
            )
        } else {
            emptyList()
        }

        val resolvedCurrent = curId ?: newDisplayed.firstOrNull()
        return state.copy(
            displayedIds = newDisplayed,
            originalIds = newOriginal,
            currentQueueId = resolvedCurrent,
        )
    }

    /**
     * Appends [newQueueIds] to [ShuffleState.displayedIds] and, when [ShuffleState.shuffleEnabled]
     * is true, to [ShuffleState.originalIds].
     */
    fun addToQueue(state: ShuffleState, newQueueIds: List<Long>): ShuffleState {
        if (newQueueIds.isEmpty()) return state
        val existingDisplayed = state.displayedIds.toHashSet()
        val deduped = newQueueIds.distinct().filterNot { it in existingDisplayed }
        if (deduped.isEmpty()) return state

        val newDisplayed = state.displayedIds + deduped
        val newOriginal = if (state.shuffleEnabled) {
            val existingOrig = state.originalIds.toHashSet()
            state.originalIds + deduped.filterNot { it in existingOrig }
        } else {
            emptyList()
        }
        val resolvedCurrent = state.currentQueueId ?: newDisplayed.firstOrNull()
        return state.copy(
            displayedIds = newDisplayed,
            originalIds = newOriginal,
            currentQueueId = resolvedCurrent,
        )
    }

    /**
     * Removes [removedQueueId] from [ShuffleState.displayedIds] and [ShuffleState.originalIds].
     * If [removedQueueId] was the active item, advances [ShuffleState.currentQueueId] to the item
     * now occupying the removed index (or the new last item, or `null` if empty).
     */
    fun remove(state: ShuffleState, removedQueueId: Long): ShuffleState {
        val removedDispIdx = state.displayedIds.indexOf(removedQueueId)
        if (removedDispIdx < 0 && removedQueueId !in state.originalIds) return state

        val newDisplayed = if (removedDispIdx >= 0) {
            ArrayList<Long>(state.displayedIds.size - 1).apply {
                for (id in state.displayedIds) {
                    if (id != removedQueueId) add(id)
                }
            }
        } else {
            state.displayedIds
        }

        val newOriginal = if (state.shuffleEnabled && state.originalIds.isNotEmpty()) {
            ArrayList<Long>(state.originalIds.size).apply {
                for (id in state.originalIds) {
                    if (id != removedQueueId) add(id)
                }
            }
        } else {
            emptyList()
        }

        val newCurrent = if (state.currentQueueId == removedQueueId) {
            if (newDisplayed.isEmpty()) {
                null
            } else {
                newDisplayed[removedDispIdx.coerceIn(0, newDisplayed.lastIndex)]
            }
        } else {
            state.currentQueueId
        }

        val newAnchor = if (state.shuffleEnabled) {
            state.anchorOriginalIndex.coerceIn(0, newOriginal.size)
        } else {
            0
        }

        return state.copy(
            displayedIds = newDisplayed,
            originalIds = newOriginal,
            currentQueueId = newCurrent,
            anchorOriginalIndex = newAnchor,
        )
    }

    /**
     * Updates [ShuffleState.displayedIds] to [newDisplayedIds] (validating that it contains the
     * exact same set of IDs) and leaves [ShuffleState.originalIds] untouched so unshuffling
     * still restores the original pre-shuffle order.
     */
    fun reorder(state: ShuffleState, newDisplayedIds: List<Long>): ShuffleState {
        if (newDisplayedIds.size != state.displayedIds.size) return state
        val currentSet = HashSet<Long>(state.displayedIds.size * 2)
        currentSet.addAll(state.displayedIds)
        val newSet = HashSet<Long>(newDisplayedIds.size * 2)
        for (id in newDisplayedIds) {
            if (id !in currentSet || !newSet.add(id)) return state
        }
        if (newSet.size != currentSet.size) return state
        return state.copy(displayedIds = newDisplayedIds)
    }

    /**
     * Builds initial shuffled state for the stateless playlist/album header "Shuffle" action.
     * Sets [ShuffleState.originalIds] to [allQueueIds], places [startQueueId] (or a randomly
     * chosen track) at index `0` of [ShuffleState.displayedIds], and sets `shuffleEnabled = true`.
     */
    fun fromPlaylistShuffle(
        allQueueIds: List<Long>,
        startQueueId: Long? = null,
        random: Random = Random.Default,
    ): ShuffleState {
        if (allQueueIds.isEmpty()) {
            return ShuffleState(
                displayedIds = emptyList(),
                originalIds = emptyList(),
                currentQueueId = null,
                shuffleEnabled = true,
                anchorOriginalIndex = 0,
            )
        }
        val initial = ShuffleState(
            displayedIds = allQueueIds,
            originalIds = allQueueIds,
            currentQueueId = startQueueId,
            shuffleEnabled = false,
        )
        return shuffle(initial, random)
    }

    private fun insertAfterOrAt(
        list: List<Long>,
        anchorId: Long?,
        toInsert: List<Long>,
        fallbackIndex: Int,
    ): List<Long> {
        if (toInsert.isEmpty()) return list
        if (list.isEmpty()) return toInsert
        val idx = if (anchorId != null) list.indexOf(anchorId) else -1
        val insertPos = if (idx >= 0) idx + 1 else fallbackIndex.coerceIn(0, list.size)
        val out = ArrayList<Long>(list.size + toInsert.size)
        out.addAll(list.subList(0, insertPos))
        out.addAll(toInsert)
        out.addAll(list.subList(insertPos, list.size))
        return out
    }
}
