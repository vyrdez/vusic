package com.feishin.mobile.playback

import com.feishin.mobile.domain.model.Track
import kotlin.math.abs

/**
 * Pure, Android-free coordinate and current-item resolution helpers shared by
 * [BoundedQueuePlayer] and [PlayerManager].
 *
 * Keeps the 100-item published MediaSession window and the full in-memory
 * queue in consistent coordinates without ever indexing the full queue by a
 * window-relative controller index when `queue.size > controllerMediaItemCount`.
 */
object QueueWindow {
    const val DEFAULT_MAX_WINDOW: Int = 100

    const val REPEAT_OFF: Int = 0
    const val REPEAT_ONE: Int = 1
    const val REPEAT_ALL: Int = 2

    data class ResolvedCurrent(
        val index: Int,
        val track: Track?,
    )

    data class ChunkedSetPlan(
        val initialRange: IntRange,
        val suffixRanges: List<IntRange>,
        val prefixRangesReversed: List<IntRange>,
    )

    /** Start index in the full timeline for the bounded window centered on [currentFullIndex]. */
    fun windowStart(
        fullCount: Int,
        currentFullIndex: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): Int {
        if (fullCount <= 0 || maxWindow <= 0 || fullCount <= maxWindow) return 0
        val current = currentFullIndex.coerceIn(0, fullCount - 1)
        val half = maxWindow / 2
        return (current - half).coerceIn(0, maxOf(0, fullCount - maxWindow))
    }

    /** Number of items exposed in the bounded window for a full timeline of size [fullCount]. */
    fun windowCount(
        fullCount: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): Int {
        if (fullCount <= 0 || maxWindow <= 0) return 0
        return if (fullCount <= maxWindow) fullCount else minOf(maxWindow, fullCount)
    }

    /** Converts a full-queue index to a bounded-window index relative to [currentFullIndex]'s window. */
    fun toWindowIndex(
        fullIndex: Int,
        fullCount: Int,
        currentFullIndex: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): Int {
        if (fullCount <= 0 || maxWindow <= 0) return 0
        val clampedFull = fullIndex.coerceIn(0, fullCount - 1)
        if (fullCount <= maxWindow) return clampedFull
        val start = windowStart(fullCount, currentFullIndex, maxWindow)
        val count = minOf(maxWindow, fullCount - start)
        return (clampedFull - start).coerceIn(0, maxOf(0, count - 1))
    }

    /** Converts a bounded-window index back to the full-queue index for [currentFullIndex]'s window. */
    fun toFullIndex(
        windowIndex: Int,
        fullCount: Int,
        currentFullIndex: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): Int {
        if (fullCount <= 0) return 0
        if (maxWindow <= 0 || fullCount <= maxWindow) {
            return windowIndex.coerceIn(0, fullCount - 1)
        }
        val start = windowStart(fullCount, currentFullIndex, maxWindow)
        return (start + windowIndex).coerceIn(0, fullCount - 1)
    }

    /**
     * Window-relative `seekTo` steps (`0 until windowCount`) that reach [targetFullIndex]
     * from [currentFullIndex] through [BoundedQueuePlayer] without ever passing a negative
     * or out-of-window index or double-adding [windowStart].
     *
     * When [targetFullIndex] is already inside the active window (including adjacent skips
     * and nearby taps), returns a single window-relative index.
     */
    fun seekSteps(
        targetFullIndex: Int,
        fullCount: Int,
        currentFullIndex: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): List<Int> {
        if (fullCount <= 0 || maxWindow <= 0) return emptyList()
        val target = targetFullIndex.coerceIn(0, fullCount - 1)
        if (fullCount <= maxWindow) return listOf(target)
        val steps = ArrayList<Int>(4)
        var cur = currentFullIndex.coerceIn(0, fullCount - 1)
        var guard = 0
        val maxIterations = (fullCount / maxOf(1, maxWindow / 2)) + 4
        while (guard++ < maxIterations) {
            val start = windowStart(fullCount, cur, maxWindow)
            val endExclusive = minOf(fullCount, start + maxWindow)
            if (target in start until endExclusive) {
                steps += (target - start)
                break
            }
            val relStep = if (target < start) 0 else (endExclusive - start - 1)
            val nextCur = (start + relStep).coerceIn(0, fullCount - 1)
            if (nextCur == cur) {
                steps += (target - start).coerceIn(0, maxWindow - 1)
                break
            }
            steps += relStep
            cur = nextCur
        }
        return steps
    }

    /**
     * Single source of truth for resolving the UI's current `(index, track)` from
     * the player's current MediaItem:
     * 1. `queueId` (`EXTRA_QUEUE_ID` via `TrackMediaItems.queueIdOf`), when >= 0.
     * 2. `mediaId` (`MediaItem.mediaId`), picking the entry closest to [currentIndex]
     *    when the same song appears multiple times in [queue].
     * 3. Never indexes [queue] with [controllerMediaItemIndex] when
     *    `queue.size > controllerMediaItemCount`; falls back to [fallbackTrack] / [currentIndex].
     */
    fun resolveCurrent(
        queue: List<QueueItem>,
        currentIndex: Int,
        queueId: Long?,
        mediaId: String?,
        controllerMediaItemIndex: Int,
        controllerMediaItemCount: Int = windowCount(queue.size),
        fallbackTrack: Track? = null,
    ): ResolvedCurrent {
        if (queue.isEmpty()) {
            return ResolvedCurrent(index = 0, track = fallbackTrack)
        }
        val safeCurrent = currentIndex.coerceIn(0, queue.lastIndex)

        if (queueId != null && queueId >= 0L) {
            val byQid = queue.indexOfFirst { it.queueId == queueId }
            if (byQid >= 0) {
                return ResolvedCurrent(index = byQid, track = queue[byQid].song)
            }
        }

        if (!mediaId.isNullOrBlank()) {
            val byMediaId = closestIndexByMediaId(queue, mediaId, safeCurrent)
            if (byMediaId >= 0) {
                return ResolvedCurrent(index = byMediaId, track = queue[byMediaId].song)
            }
        }

        val canUseControllerIndex = controllerMediaItemCount > 0 &&
            queue.size <= controllerMediaItemCount &&
            controllerMediaItemIndex in queue.indices
        val resolvedIndex = if (canUseControllerIndex) controllerMediaItemIndex else safeCurrent
        val resolvedTrack = fallbackTrack ?: queue.getOrNull(resolvedIndex)?.song
        return ResolvedCurrent(index = resolvedIndex, track = resolvedTrack)
    }

    private fun closestIndexByMediaId(
        queue: List<QueueItem>,
        mediaId: String,
        currentIndex: Int,
    ): Int {
        var bestIdx = -1
        var bestDist = Int.MAX_VALUE
        for (i in queue.indices) {
            if (queue[i].song.id == mediaId) {
                val dist = abs(i - currentIndex)
                if (dist < bestDist || (dist == bestDist && i >= currentIndex)) {
                    bestDist = dist
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }

    /** Full-queue index after a next / auto-advance transition under [repeatMode]. */
    fun nextFullIndex(
        currentFullIndex: Int,
        fullCount: Int,
        repeatMode: Int,
        autoAdvance: Boolean = false,
    ): Int? {
        if (fullCount <= 0) return null
        val cur = currentFullIndex.coerceIn(0, fullCount - 1)
        if (autoAdvance && repeatMode == REPEAT_ONE) return cur
        return when {
            cur + 1 < fullCount -> cur + 1
            repeatMode == REPEAT_ALL -> 0
            repeatMode == REPEAT_ONE -> cur
            else -> null
        }
    }

    /** Full-queue index after a previous transition under [repeatMode]. */
    fun previousFullIndex(
        currentFullIndex: Int,
        fullCount: Int,
        repeatMode: Int,
    ): Int? {
        if (fullCount <= 0) return null
        val cur = currentFullIndex.coerceIn(0, fullCount - 1)
        return when {
            cur - 1 >= 0 -> cur - 1
            repeatMode == REPEAT_ALL -> fullCount - 1
            repeatMode == REPEAT_ONE -> cur
            else -> null
        }
    }

    /**
     * Slices a list of size [totalCount] into chunks of at most [chunkSize] anchored at
     * [startIndex] so `setMediaItems` can start directly on [startIndex] (index 0 of the
     * initial chunk) and append/prepend the rest without restarting the active item.
     */
    fun chunkedSetPlan(
        totalCount: Int,
        startIndex: Int,
        chunkSize: Int = DEFAULT_MAX_WINDOW,
    ): ChunkedSetPlan {
        if (totalCount <= 0 || chunkSize <= 0) {
            return ChunkedSetPlan(IntRange.EMPTY, emptyList(), emptyList())
        }
        val start = startIndex.coerceIn(0, totalCount - 1)
        val firstEnd = minOf(totalCount, start + chunkSize)
        val initial = start until firstEnd

        val suffix = ArrayList<IntRange>()
        var s = firstEnd
        while (s < totalCount) {
            val e = minOf(totalCount, s + chunkSize)
            suffix += (s until e)
            s = e
        }

        val prefix = ArrayList<IntRange>()
        var p = 0
        while (p < start) {
            val e = minOf(start, p + chunkSize)
            prefix += (p until e)
            p = e
        }
        return ChunkedSetPlan(
            initialRange = initial,
            suffixRanges = suffix,
            prefixRangesReversed = prefix.asReversed(),
        )
    }
}
