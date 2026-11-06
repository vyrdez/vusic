package com.feishin.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.feishin.mobile.core.common.ShuffleModel
import com.feishin.mobile.core.common.ShuffleState
import com.feishin.mobile.domain.model.Track
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun track(id: String, title: String) = Track(
    id = id,
    title = title,
    album = "Album",
    albumId = null,
    artist = "Artist",
    artistId = null,
    coverArt = null,
    durationSec = 180,
    trackNo = 1,
    year = 2024,
    genre = "Genre",
    suffix = "mp3",
    starred = false,
)

/**
 * Verifies the Metrolist-parity ShuffleModel + PlayerManager timeline pipeline:
 * - Shuffle ON moves the active item to index 0 (#1) and permutes all others without restarting playback.
 * - Tapping a song in the queue only updates currentQueueId and seeks (0 timeline reorder calls).
 * - Manual drag-reorder while shuffled updates displayedIds only and preserves originalIds.
 * - Shuffle OFF restores originalIds with the current/tapped item at its original catalog slot without restarting.
 * - Rapid toggle spam coalesces via generation counter and NonCancellable commit without truncating the timeline.
 */
class QueueShufflePipelineTest {

    private class FakePlayer(
        initialItems: List<MediaItem>,
        initialIndex: Int,
        var position: Long,
        var playing: Boolean,
    ) {
        val timeline = initialItems.toMutableList()
        var currentIndex = initialIndex
        var setMediaItemsCalls = 0
        var removeCalls = 0
        var addCalls = 0
        var moveCalls = 0
        var seekCalls = 0
        var activeItemRemoved = false
        private val trackedActiveItem: MediaItem?
            get() = timeline.getOrNull(currentIndex)

        fun resetCounters() {
            setMediaItemsCalls = 0
            removeCalls = 0
            addCalls = 0
            moveCalls = 0
            seekCalls = 0
        }

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, m, a ->
            when (m.name) {
                "getMediaItemCount" -> timeline.size
                "getCurrentMediaItemIndex" -> currentIndex
                "getCurrentMediaItem" -> timeline.getOrNull(currentIndex)
                "getMediaItemAt" -> timeline[a[0] as Int]
                "getCurrentPosition" -> position
                "isPlaying" -> playing
                "play" -> {
                    playing = true
                    null
                }
                "pause" -> {
                    playing = false
                    null
                }
                "setMediaItems" -> {
                    setMediaItemsCalls++
                    @Suppress("UNCHECKED_CAST")
                    val newItems = a[0] as List<MediaItem>
                    timeline.clear()
                    timeline.addAll(newItems)
                    if (a.size >= 2 && a[1] is Int) currentIndex = a[1] as Int
                    if (a.size >= 3 && a[2] is Long) position = a[2] as Long
                    null
                }
                "removeMediaItems" -> {
                    removeCalls++
                    val from = a[0] as Int
                    val to = a[1] as Int
                    val activeBefore = trackedActiveItem
                    if (currentIndex in from until to) {
                        activeItemRemoved = true
                    }
                    timeline.subList(from, to).clear()
                    if (activeBefore != null) {
                        val newIdx = timeline.indexOfFirst { it === activeBefore }
                        if (newIdx >= 0) currentIndex = newIdx
                    }
                    null
                }
                "addMediaItems" -> {
                    addCalls++
                    val activeBefore = trackedActiveItem
                    if (a.size == 2 && a[0] is Int) {
                        val index = a[0] as Int
                        @Suppress("UNCHECKED_CAST")
                        val added = a[1] as List<MediaItem>
                        timeline.addAll(index, added)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val added = a[0] as List<MediaItem>
                        timeline.addAll(added)
                    }
                    if (activeBefore != null) {
                        val newIdx = timeline.indexOfFirst { it === activeBefore }
                        if (newIdx >= 0) currentIndex = newIdx
                    }
                    null
                }
                "moveMediaItem" -> {
                    moveCalls++
                    val from = a[0] as Int
                    val to = a[1] as Int
                    val activeBefore = trackedActiveItem
                    val moved = timeline.removeAt(from)
                    timeline.add(to.coerceIn(0, timeline.size), moved)
                    if (activeBefore != null) {
                        val newIdx = timeline.indexOfFirst { it === activeBefore }
                        if (newIdx >= 0) currentIndex = newIdx
                    }
                    null
                }
                "seekTo" -> {
                    seekCalls++
                    if (a.size >= 2 && a[0] is Int && a[1] is Long) {
                        currentIndex = a[0] as Int
                        position = a[1] as Long
                    } else if (a.size == 1 && a[0] is Long) {
                        position = a[0] as Long
                    }
                    null
                }
                "toString" -> "FakePlayer"
                else -> null
            }
        } as Player
    }

    private val ids = (0L until 533L).toList()
    private val queueItems = ids.map { id ->
        QueueItem(
            song = track("song_$id", "Song $id"),
            queueId = id,
        )
    }
    private val byId = queueItems.associateBy { it.queueId }
    private val mediaItemsById = ids.associateWith { id ->
        MediaItem.Builder().setMediaId("song_$id").build()
    }
    private val initialMediaItems = ids.map { mediaItemsById.getValue(it) }
    private val mediaIds = ids.map { "song_$it" }

    private fun applyStateToPlayer(
        fake: FakePlayer,
        beforeQueue: List<QueueItem>,
        beforeIndex: Int,
        nextState: ShuffleState,
    ): Pair<List<QueueItem>, Int> {
        val reorderedQueue = nextState.displayedIds.map { byId.getValue(it) }
        val reorderedMedia = nextState.displayedIds.map { mediaItemsById.getValue(it) }
        val targetIdx = PlayerManager.applyTimelineReorder(
            player = fake.player,
            before = beforeQueue,
            currentIdx = beforeIndex,
            keepId = nextState.currentQueueId,
            reordered = reorderedQueue,
            reorderedMediaItems = reorderedMedia,
            mediaItemLookup = { mediaItemsById[it] },
            chunkForBoundedController = false,
        )
        return reorderedQueue to targetIdx
    }

    @Test
    fun shuffleOn_533Items_movesCurrentToIndexZero_withoutRestartingActiveItem() {
        val startSlot = 50
        val startQueueId = 50L
        val fake = FakePlayer(
            initialItems = initialMediaItems,
            initialIndex = startSlot,
            position = 42_000L,
            playing = true,
        )
        val activeInstanceBefore = fake.timeline[startSlot]

        val initialState = ShuffleState(
            displayedIds = ids,
            originalIds = emptyList(),
            currentQueueId = startQueueId,
            shuffleEnabled = false,
        )
        val shuffledState = ShuffleModel.shuffle(initialState, Random(7))
        val (reorderedQueue, targetIdx) = applyStateToPlayer(
            fake = fake,
            beforeQueue = queueItems,
            beforeIndex = startSlot,
            nextState = shuffledState,
        )

        // Active item moved to index 0 (#1) and all 533 items preserved
        assertEquals(0, targetIdx)
        assertEquals(0, fake.currentIndex)
        assertEquals(startQueueId, shuffledState.displayedIds.first())
        assertEquals("song_50", fake.timeline.first().mediaId)
        assertEquals(533, fake.timeline.size)
        assertEquals(mediaIds.toSet(), fake.timeline.map { it.mediaId }.toSet())
        assertEquals(ids, shuffledState.originalIds)

        // Prior items (0..49) were also permuted into the tail (1..532)
        assertTrue(reorderedQueue.drop(1).map { it.queueId }.containsAll((0L until 50L).toList()))

        // Zero restarts: setMediaItems was never called, active item was never removed, position intact
        assertEquals(0, fake.setMediaItemsCalls)
        assertFalse(fake.activeItemRemoved)
        assertSame(activeInstanceBefore, fake.timeline[0])
        assertEquals(42_000L, fake.position)
        assertTrue(fake.playing)
        // Exactly 2 removals (suffix + prefix) and 1 tail insertion (head is empty since targetIndex == 0)
        assertEquals(2, fake.removeCalls)
        assertEquals(1, fake.addCalls)
    }

    @Test
    fun tapWhileShuffled_neverReorders_andShuffleOff_restoresOriginalOrderAtTappedSlot() {
        val startSlot = 25
        val fake = FakePlayer(
            initialItems = initialMediaItems,
            initialIndex = startSlot,
            position = 10_000L,
            playing = true,
        )
        var state = ShuffleModel.shuffle(
            ShuffleState(displayedIds = ids, currentQueueId = 25L),
            Random(11),
        )
        var (liveQueue, liveIdx) = applyStateToPlayer(fake, queueItems, startSlot, state)
        fake.resetCounters()

        // Rule 3: User taps song 312L in the shuffled queue
        val tappedQueueId = 312L
        val tappedShuffledIdx = liveQueue.indexOfFirst { it.queueId == tappedQueueId }
        assertTrue(tappedShuffledIdx > 0)

        val orderBeforeTap = state.displayedIds
        val origBeforeTap = state.originalIds
        state = ShuffleModel.select(state, tappedQueueId)
        fake.player.seekTo(tappedShuffledIdx, 0L)
        fake.position = 18_500L // user listens for 18.5s
        liveIdx = tappedShuffledIdx

        // Tap NEVER reorders displayedIds or originalIds or touches timeline items
        assertEquals(orderBeforeTap, state.displayedIds)
        assertEquals(origBeforeTap, state.originalIds)
        assertTrue(state.shuffleEnabled)
        assertEquals(0, fake.removeCalls)
        assertEquals(0, fake.addCalls)
        assertEquals(0, fake.moveCalls)
        assertEquals(0, fake.setMediaItemsCalls)
        assertEquals(1, fake.seekCalls)

        val activeTappedInstance = fake.timeline[liveIdx]
        fake.resetCounters()

        // Rule 2: User turns Shuffle OFF -> restores original order with 312L at slot 312, no restart
        val unshuffledState = ShuffleModel.unshuffle(state)
        val (restoredQueue, restoredIdx) = applyStateToPlayer(fake, liveQueue, liveIdx, unshuffledState)

        assertFalse(unshuffledState.shuffleEnabled)
        assertTrue(unshuffledState.originalIds.isEmpty())
        assertEquals(ids, restoredQueue.map { it.queueId })
        assertEquals(mediaIds, fake.timeline.map { it.mediaId })
        assertEquals(312, restoredIdx)
        assertEquals(312, fake.currentIndex)
        assertSame(activeTappedInstance, fake.timeline[312])
        assertFalse(fake.activeItemRemoved)
        assertEquals(0, fake.setMediaItemsCalls)
        assertEquals(18_500L, fake.position)
    }

    @Test
    fun dragReorderWhileShuffled_preservesOriginalIds_andUnshuffleRestoresOriginalOrder() {
        val fake = FakePlayer(
            initialItems = initialMediaItems,
            initialIndex = 10,
            position = 5_000L,
            playing = true,
        )
        var state = ShuffleModel.shuffle(
            ShuffleState(displayedIds = ids, currentQueueId = 10L),
            Random(19),
        )
        var (liveQueue, liveIdx) = applyStateToPlayer(fake, queueItems, 10, state)

        // Rule 4: User drags item at index 1 to index 5 in QueueSheet while shuffled
        val draggedOrder = state.displayedIds.toMutableList().apply {
            val moved = removeAt(1)
            add(5, moved)
        }
        state = ShuffleModel.reorder(state, draggedOrder)
        fake.player.moveMediaItem(1, 5)
        liveQueue = state.displayedIds.map { byId.getValue(it) }

        assertEquals(draggedOrder, state.displayedIds)
        // originalIds MUST remain the untouched catalog order!
        assertEquals(ids, state.originalIds)

        // Turning Shuffle OFF restores the exact original catalog order (0..532)
        val unshuffled = ShuffleModel.unshuffle(state)
        val (restoredQueue, restoredIdx) = applyStateToPlayer(fake, liveQueue, liveIdx, unshuffled)
        assertEquals(ids, restoredQueue.map { it.queueId })
        assertEquals(mediaIds, fake.timeline.map { it.mediaId })
        assertEquals(10, restoredIdx)
        assertEquals(10, fake.currentIndex)
    }

    @Test
    fun toggleSpam_20RapidToggles_coalescesToLatestWin_withoutTruncatingTimeline() = runTest {
        val fake = FakePlayer(
            initialItems = initialMediaItems,
            initialIndex = 20,
            position = 33_000L,
            playing = true,
        )
        var liveQueue = queueItems
        var liveIdx = 20
        var shuffleState = ShuffleState(
            displayedIds = ids,
            originalIds = emptyList(),
            currentQueueId = 20L,
            shuffleEnabled = false,
        )
        val generation = AtomicLong(0L)
        var job: kotlinx.coroutines.Job? = null
        var atomicApplies = 0

        // 20 rapid toggles ending on true (1..19 alternate, 20th is true)
        val toggles = (1..19).map { it % 2 == 1 } + listOf(true)
        for ((step, enabled) in toggles.withIndex()) {
            val gen = generation.incrementAndGet()
            job?.cancel()
            if (enabled && shuffleState.originalIds.isEmpty()) {
                shuffleState = shuffleState.copy(
                    originalIds = liveQueue.map { it.queueId },
                    shuffleEnabled = true,
                    anchorOriginalIndex = liveIdx,
                )
            }
            val planInput = shuffleState.copy(
                displayedIds = liveQueue.map { it.queueId },
                originalIds = shuffleState.originalIds.ifEmpty { liveQueue.map { it.queueId } },
                currentQueueId = liveQueue[liveIdx].queueId,
            )
            job = launch {
                delay(5)
                if (gen != generation.get()) return@launch
                val nextState = if (enabled) {
                    ShuffleModel.shuffle(planInput, Random(step))
                } else {
                    ShuffleModel.unshuffle(planInput)
                }
                withContext(NonCancellable) {
                    if (gen != generation.get()) return@withContext
                    val (reordered, targetIdx) = applyStateToPlayer(fake, liveQueue, liveIdx, nextState)
                    liveQueue = reordered
                    liveIdx = targetIdx
                    shuffleState = nextState.copy(
                        originalIds = if (enabled) nextState.originalIds else emptyList(),
                        shuffleEnabled = enabled,
                    )
                    atomicApplies++
                }
            }
        }
        advanceUntilIdle()

        assertEquals(1, atomicApplies)
        assertEquals(533, fake.timeline.size)
        assertEquals(mediaIds.toSet(), fake.timeline.map { it.mediaId }.toSet())
        assertNotEquals(mediaIds, fake.timeline.map { it.mediaId })
        assertEquals("song_20", fake.timeline.first().mediaId)
        assertEquals(0, fake.currentIndex)
        assertEquals(33_000L, fake.position)
        assertFalse(fake.activeItemRemoved)
    }
}
