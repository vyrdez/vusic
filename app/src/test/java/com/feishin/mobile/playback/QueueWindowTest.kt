package com.feishin.mobile.playback

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueWindowTest {

    private val queueSizes = listOf(1, 2, 99, 100, 101, 500, 906)

    private fun makeTrack(
        id: String,
        title: String = "Title $id",
        localUri: String? = null,
    ): Track = Track(
        id = id,
        title = title,
        album = "Album",
        albumId = "alb_1",
        artist = "Artist",
        artistId = "art_1",
        coverArt = "cov_1",
        durationSec = 180,
        trackNo = 1,
        year = 2024,
        genre = "Pop",
        suffix = "flac",
        starred = false,
        localUri = localUri,
    )

    private fun makeQueue(size: Int, baseQueueId: Long = 1_000L): List<QueueItem> =
        List(size) { i ->
            val downloaded = if (i % 3 == 0) "file:///storage/song_$i.flac" else null
            QueueItem(
                song = makeTrack(id = "song_$i", title = "Song $i", localUri = downloaded),
                queueId = baseQueueId + i,
            )
        }

    /** Simulates BoundedQueuePlayer.seekTo(relIdx) for each step in [steps]. */
    private fun replaySeekSteps(
        fullCount: Int,
        startFullIndex: Int,
        steps: List<Int>,
        maxWindow: Int = QueueWindow.DEFAULT_MAX_WINDOW,
    ): Int {
        var cur = startFullIndex
        for (rel in steps) {
            cur = QueueWindow.toFullIndex(
                windowIndex = rel,
                fullCount = fullCount,
                currentFullIndex = cur,
                maxWindow = maxWindow,
            )
        }
        return cur
    }

    @Test
    fun allQueueSizes_windowBoundsAndRoundTripMapping() {
        for (size in queueSizes) {
            val q = makeQueue(size)
            val expectedWindowCount = minOf(size, QueueWindow.DEFAULT_MAX_WINDOW)
            assertEquals("windowCount for size=$size", expectedWindowCount, QueueWindow.windowCount(size))

            val sampleCurrents = listOf(
                0,
                1.coerceAtMost(size - 1),
                (size / 2).coerceIn(0, size - 1),
                49.coerceAtMost(size - 1),
                50.coerceAtMost(size - 1),
                51.coerceAtMost(size - 1),
                99.coerceAtMost(size - 1),
                100.coerceAtMost(size - 1),
                101.coerceAtMost(size - 1),
                855.coerceAtMost(size - 1),
                856.coerceAtMost(size - 1),
                857.coerceAtMost(size - 1),
                size - 1,
            ).distinct()

            for (cur in sampleCurrents) {
                val start = QueueWindow.windowStart(size, cur)
                if (size <= 100) {
                    assertEquals("small queue start must be 0 (size=$size)", 0, start)
                    assertEquals(cur, QueueWindow.toWindowIndex(cur, size, cur))
                    assertEquals(cur, QueueWindow.toFullIndex(cur, size, cur))
                } else {
                    assertTrue(start in 0..(size - 100))
                    val rel = QueueWindow.toWindowIndex(cur, size, cur)
                    assertTrue(rel in 0 until 100)
                    // Translating current -> window -> full must never double-add windowStart
                    assertEquals(cur, QueueWindow.toFullIndex(rel, size, cur))
                }

                // Every sample target is reachable from cur via seekSteps with valid window indices
                for (target in sampleCurrents) {
                    val steps = QueueWindow.seekSteps(
                        targetFullIndex = target,
                        fullCount = size,
                        currentFullIndex = cur,
                    )
                    assertTrue(steps.isNotEmpty())
                    assertTrue(steps.all { it in 0 until expectedWindowCount })
                    val landed = replaySeekSteps(size, cur, steps)
                    assertEquals("size=$size cur=$cur target=$target", target, landed)
                }

                // Resolve current item by queueId for every sampled index
                val item = q[cur]
                val controllerIdx = QueueWindow.toWindowIndex(cur, size, cur)
                val resolved = QueueWindow.resolveCurrent(
                    queue = q,
                    currentIndex = (cur - 1).coerceAtLeast(0),
                    queueId = item.queueId,
                    mediaId = item.song.id,
                    controllerMediaItemIndex = controllerIdx,
                    controllerMediaItemCount = expectedWindowCount,
                )
                assertEquals(cur, resolved.index)
                assertEquals(item.song, resolved.track)
            }
        }
    }

    @Test
    fun windowEdges_49_50_51_99_100_101_855_856_857_and_905_to_0() {
        val size = 906
        val q = makeQueue(size)
        val controllerCount = QueueWindow.windowCount(size)
        assertEquals(100, controllerCount)

        // 1. Edge 49 -> 50 -> 51 (windowStart shifts from 0 to 1 at 51; windowIndex pins at 50)
        assertEquals(0, QueueWindow.windowStart(size, 49))
        assertEquals(49, QueueWindow.toWindowIndex(49, size, 49))
        assertEquals(0, QueueWindow.windowStart(size, 50))
        assertEquals(50, QueueWindow.toWindowIndex(50, size, 50))
        assertEquals(1, QueueWindow.windowStart(size, 51))
        assertEquals(50, QueueWindow.toWindowIndex(51, size, 51))

        // 2. Edge 99 -> 100 -> 101 (crosses 100-item boundary while windowIndex stays 50)
        assertEquals(49, QueueWindow.windowStart(size, 99))
        assertEquals(50, QueueWindow.toWindowIndex(99, size, 99))
        assertEquals(50, QueueWindow.windowStart(size, 100))
        assertEquals(50, QueueWindow.toWindowIndex(100, size, 100))
        assertEquals(51, QueueWindow.windowStart(size, 101))
        assertEquals(50, QueueWindow.toWindowIndex(101, size, 101))

        // 3. Edge 855 -> 856 -> 857 (windowStart clamps at 806; windowIndex unpins from 50 to 51 at 857)
        assertEquals(805, QueueWindow.windowStart(size, 855))
        assertEquals(50, QueueWindow.toWindowIndex(855, size, 855))
        assertEquals(806, QueueWindow.windowStart(size, 856))
        assertEquals(50, QueueWindow.toWindowIndex(856, size, 856))
        assertEquals(806, QueueWindow.windowStart(size, 857))
        assertEquals(51, QueueWindow.toWindowIndex(857, size, 857))

        // 4. Edge 905 -> 0 (wrap from tail window 806..905 back to head window 0..99)
        assertEquals(806, QueueWindow.windowStart(size, 905))
        assertEquals(99, QueueWindow.toWindowIndex(905, size, 905))
        assertEquals(0, QueueWindow.windowStart(size, 0))
        assertEquals(0, QueueWindow.toWindowIndex(0, size, 0))

        val edgeChains = listOf(
            listOf(49, 50, 51),
            listOf(99, 100, 101),
            listOf(855, 856, 857),
            listOf(905, 0),
        )
        for (chain in edgeChains) {
            var curIdx = chain.first()
            for (nextIdx in chain) {
                val winIdx = QueueWindow.toWindowIndex(nextIdx, size, nextIdx)
                // Resolve by queueId
                val byQid = QueueWindow.resolveCurrent(
                    queue = q,
                    currentIndex = curIdx,
                    queueId = q[nextIdx].queueId,
                    mediaId = q[nextIdx].song.id,
                    controllerMediaItemIndex = winIdx,
                    controllerMediaItemCount = controllerCount,
                )
                assertEquals(nextIdx, byQid.index)
                assertEquals("song_$nextIdx", byQid.track?.id)

                // Resolve by mediaId alone (when queueId is missing / -1L)
                val byMediaId = QueueWindow.resolveCurrent(
                    queue = q,
                    currentIndex = curIdx,
                    queueId = -1L,
                    mediaId = q[nextIdx].song.id,
                    controllerMediaItemIndex = winIdx,
                    controllerMediaItemCount = controllerCount,
                )
                assertEquals(nextIdx, byMediaId.index)
                assertEquals("song_$nextIdx", byMediaId.track?.id)

                // Seek steps from curIdx -> nextIdx land on nextIdx without double-adding windowStart
                val steps = QueueWindow.seekSteps(nextIdx, size, curIdx)
                assertEquals(nextIdx, replaySeekSteps(size, curIdx, steps))
                curIdx = nextIdx
            }
        }
    }

    @Test
    fun duplicates_sameSongId_distinctQueueId_and_closestFallback() {
        val size = 906
        val base = makeQueue(size).toMutableList()
        val dupTrack = makeTrack(id = "dup_song", title = "Duplicate Song")
        val dupPositions = listOf(10, 50, 500, 856)
        for (pos in dupPositions) {
            base[pos] = QueueItem(song = dupTrack, queueId = 9_000L + pos)
        }

        // 1. With distinct queueId, always resolves the exact duplicate regardless of currentIndex
        for (pos in dupPositions) {
            val resolved = QueueWindow.resolveCurrent(
                queue = base,
                currentIndex = 10,
                queueId = 9_000L + pos,
                mediaId = "dup_song",
                controllerMediaItemIndex = 50,
                controllerMediaItemCount = 100,
            )
            assertEquals(pos, resolved.index)
            assertEquals("dup_song", resolved.track?.id)
        }

        // 2. When queueId is null, mediaId resolves the duplicate closest to currentIndex
        val near50 = QueueWindow.resolveCurrent(
            queue = base,
            currentIndex = 49,
            queueId = null,
            mediaId = "dup_song",
            controllerMediaItemIndex = 50,
            controllerMediaItemCount = 100,
        )
        assertEquals(50, near50.index)

        val near500 = QueueWindow.resolveCurrent(
            queue = base,
            currentIndex = 499,
            queueId = null,
            mediaId = "dup_song",
            controllerMediaItemIndex = 50,
            controllerMediaItemCount = 100,
        )
        assertEquals(500, near500.index)

        val near856 = QueueWindow.resolveCurrent(
            queue = base,
            currentIndex = 855,
            queueId = null,
            mediaId = "dup_song",
            controllerMediaItemIndex = 50,
            controllerMediaItemCount = 100,
        )
        assertEquals(856, near856.index)
    }

    @Test
    fun neverIndexesFullQueueWithWindowRelativeControllerIndexWhenQueueExceedsWindow() {
        val q = makeQueue(906)
        val fallback = makeTrack(id = "external_1", title = "External Track")
        // Player is at full index 500 (window-relative controllerMediaItemIndex = 50).
        // If both queueId and mediaId fail, must NEVER jump currentIndex to 50.
        val resolved = QueueWindow.resolveCurrent(
            queue = q,
            currentIndex = 500,
            queueId = null,
            mediaId = null,
            controllerMediaItemIndex = 50,
            controllerMediaItemCount = 100,
            fallbackTrack = fallback,
        )
        assertEquals(500, resolved.index)
        assertEquals(fallback, resolved.track)
    }

    @Test
    fun repeatModes_off_all_one_acrossQueueSizesAndWindowEdges() {
        for (size in queueSizes) {
            val q = makeQueue(size)
            val last = size - 1

            // REPEAT_OFF: advances up to last, stops at end on auto-advance / next
            if (size > 1) {
                assertEquals(1, QueueWindow.nextFullIndex(0, size, QueueWindow.REPEAT_OFF, autoAdvance = true))
                assertEquals(last - 1, QueueWindow.previousFullIndex(last, size, QueueWindow.REPEAT_OFF))
            }
            assertEquals(null, QueueWindow.nextFullIndex(last, size, QueueWindow.REPEAT_OFF, autoAdvance = true))
            assertEquals(null, QueueWindow.previousFullIndex(0, size, QueueWindow.REPEAT_OFF))

            // REPEAT_ALL: 905 -> 0 on next/auto-advance, 0 -> 905 on previous
            val wrappedNext = QueueWindow.nextFullIndex(last, size, QueueWindow.REPEAT_ALL, autoAdvance = true)
            assertEquals(0, wrappedNext)
            val resolvedWrap = QueueWindow.resolveCurrent(
                queue = q,
                currentIndex = last,
                queueId = q[0].queueId,
                mediaId = q[0].song.id,
                controllerMediaItemIndex = 0,
                controllerMediaItemCount = QueueWindow.windowCount(size),
            )
            assertEquals(0, resolvedWrap.index)
            assertEquals(q[0].song, resolvedWrap.track)

            val wrappedPrev = QueueWindow.previousFullIndex(0, size, QueueWindow.REPEAT_ALL)
            assertEquals(last, wrappedPrev)

            // REPEAT_ONE: auto-advance stays on current item
            val mid = size / 2
            assertEquals(mid, QueueWindow.nextFullIndex(mid, size, QueueWindow.REPEAT_ONE, autoAdvance = true))
            val resolvedOne = QueueWindow.resolveCurrent(
                queue = q,
                currentIndex = mid,
                queueId = q[mid].queueId,
                mediaId = q[mid].song.id,
                controllerMediaItemIndex = QueueWindow.toWindowIndex(mid, size, mid),
                controllerMediaItemCount = QueueWindow.windowCount(size),
            )
            assertEquals(mid, resolvedOne.index)
            assertEquals(q[mid].song, resolvedOne.track)
        }
    }

    @Test
    fun chunkedSetPlan_keepsStartItemActiveAndReconstructsExactQueue() {
        for (size in queueSizes) {
            val startCandidates = listOf(0, (size / 2).coerceAtMost(size - 1), size - 1).distinct()
            for (start in startCandidates) {
                val plan = QueueWindow.chunkedSetPlan(size, start, chunkSize = 100)
                assertTrue(plan.initialRange.count() in 1..100)
                assertEquals(start, plan.initialRange.first)
                assertTrue(plan.suffixRanges.all { it.count() in 1..100 })
                assertTrue(plan.prefixRangesReversed.all { it.count() in 1..100 })

                val simulated = mutableListOf<Int>()
                simulated.addAll(plan.initialRange.toList())
                var activeItem = simulated[0]
                assertEquals(start, activeItem)

                for (r in plan.suffixRanges) {
                    simulated.addAll(r.toList())
                }
                for (r in plan.prefixRangesReversed) {
                    simulated.addAll(0, r.toList())
                }
                assertEquals((0 until size).toList(), simulated)
                assertEquals(start, simulated.indexOf(activeItem))
            }
        }
    }

    @Test
    fun currentTrackResolution_across850To875_andWrap914To0_in915ItemQueue() {
        val size = 915
        val q = makeQueue(size)
        val controllerCount = QueueWindow.windowCount(size)
        assertEquals(100, controllerCount)

        // Step through 850..875 (crossing window clamp at 865 where windowStart pins at 815)
        var prevIdx = 849
        for (idx in 850..875) {
            val winIdx = QueueWindow.toWindowIndex(idx, size, idx)
            val resolved = QueueWindow.resolveCurrent(
                queue = q,
                currentIndex = prevIdx,
                queueId = q[idx].queueId,
                mediaId = q[idx].song.id,
                controllerMediaItemIndex = winIdx,
                controllerMediaItemCount = controllerCount,
            )
            assertEquals("index at $idx", idx, resolved.index)
            assertEquals("song_$idx", resolved.track?.id)
            prevIdx = resolved.index
        }

        // Repeat-All wrap 914 -> 0
        val nextAfter914 = QueueWindow.nextFullIndex(914, size, QueueWindow.REPEAT_ALL)
        assertEquals(0, nextAfter914)
        val resolvedAt0 = QueueWindow.resolveCurrent(
            queue = q,
            currentIndex = 914,
            queueId = q[0].queueId,
            mediaId = q[0].song.id,
            controllerMediaItemIndex = 0,
            controllerMediaItemCount = controllerCount,
        )
        assertEquals(0, resolvedAt0.index)
        assertEquals("song_0", resolvedAt0.track?.id)
    }
}
