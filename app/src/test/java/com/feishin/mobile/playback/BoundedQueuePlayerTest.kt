package com.feishin.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class BoundedQueuePlayerTest {

    private class FakeTimeline(private val count: Int) : Timeline() {
        override fun getWindowCount(): Int = count
        override fun getPeriodCount(): Int = count
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            window.set(
                /* uid = */ windowIndex,
                /* mediaItem = */ MediaItem.Builder().setMediaId("song_$windowIndex").build(),
                /* manifest = */ null,
                /* presentationStartTimeMs = */ 0,
                /* windowStartTimeMs = */ 0,
                /* elapsedRealtimeEpochOffsetMs = */ 0,
                /* isSeekable = */ true,
                /* isDynamic = */ false,
                /* liveConfiguration = */ null,
                /* defaultPositionUs = */ 0,
                /* durationUs = */ 180_000_000L,
                /* firstPeriodIndex = */ windowIndex,
                /* lastPeriodIndex = */ windowIndex,
                /* positionInFirstPeriodUs = */ 0,
            )
            return window
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            period.set(
                /* id = */ periodIndex,
                /* uid = */ periodIndex,
                /* windowIndex = */ periodIndex,
                /* durationUs = */ 180_000_000L,
                /* positionInWindowUs = */ 0,
            )
            return period
        }

        override fun getIndexOfPeriod(uid: Any): Int = uid as? Int ?: -1
        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
    }

    private data class SeekCall(val index: Int, val positionMs: Long)

    private class MockPlayerState(
        val count: Int,
        var currentIndex: Int,
        var repeatMode: Int = Player.REPEAT_MODE_OFF,
        var currentPositionMs: Long = 0L,
        val seekCalls: MutableList<SeekCall> = mutableListOf(),
    )

    private fun createTrackingPlayer(
        count: Int,
        currentIndex: Int,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        currentPositionMs: Long = 0L,
    ): Pair<Player, MockPlayerState> {
        val state = MockPlayerState(count, currentIndex, repeatMode, currentPositionMs)
        val timeline = FakeTimeline(count)
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentTimeline" -> timeline
                "getCurrentMediaItemIndex" -> state.currentIndex
                "getCurrentPeriodIndex" -> state.currentIndex
                "getMediaItemCount" -> count
                "getRepeatMode" -> state.repeatMode
                "setRepeatMode" -> {
                    state.repeatMode = args[0] as Int
                    null
                }
                "getCurrentPosition" -> state.currentPositionMs
                "getMaxSeekToPreviousPosition" -> 3_000L
                "getMediaItemAt" -> {
                    val idx = args[0] as Int
                    MediaItem.Builder().setMediaId("song_$idx").build()
                }
                "seekTo" -> {
                    when (args?.size) {
                        1 -> {
                            val pos = args[0] as Long
                            state.currentPositionMs = pos
                            state.seekCalls.add(SeekCall(state.currentIndex, pos))
                        }
                        2 -> {
                            val idx = args[0] as Int
                            val pos = args[1] as Long
                            state.currentIndex = idx
                            state.currentPositionMs = pos
                            state.seekCalls.add(SeekCall(idx, pos))
                        }
                    }
                    null
                }
                "seekToDefaultPosition" -> {
                    val idx = if (args != null && args.isNotEmpty()) args[0] as Int else state.currentIndex
                    state.currentIndex = idx
                    state.currentPositionMs = 0L
                    state.seekCalls.add(SeekCall(idx, 0L))
                    null
                }
                "seekToNext", "seekToNextMediaItem" -> {
                    val next = QueueWindow.nextFullIndex(state.currentIndex, count, state.repeatMode)
                    if (next != null) {
                        state.currentIndex = next
                        state.currentPositionMs = 0L
                        state.seekCalls.add(SeekCall(next, 0L))
                    }
                    null
                }
                "seekToPrevious", "seekToPreviousMediaItem" -> {
                    val prev = QueueWindow.previousFullIndex(state.currentIndex, count, state.repeatMode)
                    if (prev != null) {
                        state.currentIndex = prev
                        state.currentPositionMs = 0L
                        state.seekCalls.add(SeekCall(prev, 0L))
                    }
                    null
                }
                "hasNextMediaItem" -> QueueWindow.nextFullIndex(state.currentIndex, count, state.repeatMode) != null
                "hasPreviousMediaItem" -> QueueWindow.previousFullIndex(state.currentIndex, count, state.repeatMode) != null
                "toString" -> "MockPlayer($count, ${state.currentIndex})"
                "hashCode" -> count.hashCode()
                "equals" -> false
                else -> null
            }
        } as Player
        return player to state
    }

    private fun createMockPlayer(count: Int, currentIndex: Int): Player =
        createTrackingPlayer(count, currentIndex).first

    private fun getWindowStart(player: BoundedQueuePlayer): Int {
        val method = BoundedQueuePlayer::class.java.declaredMethods.firstOrNull { it.name.startsWith("windowStart") }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(player) as Int
        }
        val full = player.currentTimeline
        if (full.isEmpty || full.windowCount <= 100) return 0
        val current = player.currentMediaItemIndex
        val half = 100 / 2
        return (current - half).coerceIn(0, maxOf(0, full.windowCount - 100))
    }

    @Test
    fun smallQueue_isNotBounded() {
        val player = createMockPlayer(count = 30, currentIndex = 5)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)
        assertEquals(30, bounded.mediaItemCount)
        assertEquals(5, bounded.currentMediaItemIndex)
        assertEquals(30, bounded.currentTimeline.windowCount)
        assertEquals("song_5", bounded.getMediaItemAt(5).mediaId)
    }

    @Test
    fun largeQueue_isBoundedToMaxWindow() {
        val player = createMockPlayer(count = 500, currentIndex = 250)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)
        assertEquals(100, bounded.mediaItemCount)
        // With current = 250 and maxWindow = 100, windowStart = 250 - 50 = 200.
        // Relative index of 250 is 250 - 200 = 50.
        assertEquals(50, bounded.currentMediaItemIndex)
        assertEquals(100, bounded.currentTimeline.windowCount)

        // Index 0 in bounded queue corresponds to 200 in full player
        assertEquals("song_200", bounded.getMediaItemAt(0).mediaId)
        // Index 50 corresponds to 250
        assertEquals("song_250", bounded.getMediaItemAt(50).mediaId)
    }

    @Test
    fun largeQueue_nearStart_boundsCorrectly() {
        val player = createMockPlayer(count = 500, currentIndex = 10)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)
        assertEquals(100, bounded.mediaItemCount)
        // current = 10, half = 50 -> start is clamped to 0.
        assertEquals(10, bounded.currentMediaItemIndex)
        assertEquals("song_0", bounded.getMediaItemAt(0).mediaId)
        assertEquals("song_10", bounded.getMediaItemAt(10).mediaId)
    }

    @Test
    fun largeQueue_nearEnd_boundsCorrectly() {
        val player = createMockPlayer(count = 500, currentIndex = 490)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)
        assertEquals(100, bounded.mediaItemCount)
        // start is clamped to 500 - 100 = 400.
        // relative index of 490 is 490 - 400 = 90.
        assertEquals(90, bounded.currentMediaItemIndex)
        assertEquals("song_400", bounded.getMediaItemAt(0).mediaId)
        assertEquals("song_490", bounded.getMediaItemAt(90).mediaId)
    }

    // -------------------------------------------------------------------------
    // Phase 10.2 Requirements
    // -------------------------------------------------------------------------

    @Test
    fun largeQueue_nineHundredSixItems_windowStartCentersAroundCurrentIndex() {
        val (player, _) = createTrackingPlayer(count = 906, currentIndex = 850)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        // Window centers around 850: 850 - (100 / 2) = 800 (clamped in 0..806)
        assertEquals(800, getWindowStart(bounded))
        // Relative index in the 100-item bounded window is 850 - 800 = 50
        assertEquals(50, bounded.currentMediaItemIndex)
        assertEquals(100, bounded.currentTimeline.windowCount)

        // Edge media items map correctly from relative window to absolute indices
        assertEquals("song_800", bounded.getMediaItemAt(0).mediaId)
        assertEquals("song_850", bounded.getMediaItemAt(50).mediaId)
        assertEquals("song_899", bounded.getMediaItemAt(99).mediaId)
    }

    @Test
    fun largeQueue_seekToRelativeIndex_forwardsRealIndexToWrappedPlayer() {
        val (player, state) = createTrackingPlayer(count = 906, currentIndex = 850)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        // Seeking to relative index 50 within window starting at 800 must translate to real index 850
        bounded.seekTo(50, 15_000L)
        assertEquals(1, state.seekCalls.size)
        assertEquals(850, state.seekCalls[0].index)
        assertEquals(15_000L, state.seekCalls[0].positionMs)
    }

    @Test
    fun largeQueue_seekToPositionOnly_forwardsRealIndexToWrappedPlayer() {
        val (player, state) = createTrackingPlayer(count = 906, currentIndex = 850)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        // Seeking by position only forwards to wrapped player at current real index 850
        bounded.seekTo(15_000L)
        assertEquals(1, state.seekCalls.size)
        assertEquals(850, state.seekCalls[0].index)
        assertEquals(15_000L, state.seekCalls[0].positionMs)
    }

    @Test
    fun smallQueue_twentyItems_delegatesWithoutIndexAlteration() {
        val (player, state) = createTrackingPlayer(count = 20, currentIndex = 8)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        assertEquals(20, bounded.mediaItemCount)
        assertEquals(8, bounded.currentMediaItemIndex)
        assertEquals(20, bounded.currentTimeline.windowCount)
        assertEquals("song_8", bounded.getMediaItemAt(8).mediaId)

        // Seek with small queue must not alter index
        bounded.seekTo(8, 12_000L)
        assertEquals(1, state.seekCalls.size)
        assertEquals(8, state.seekCalls[0].index)
        assertEquals(12_000L, state.seekCalls[0].positionMs)
    }

    @Test
    fun largeQueue_seekStepsFromFullIndex_neverDoubleAddsWindowStart() {
        val (player, state) = createTrackingPlayer(count = 906, currentIndex = 250)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        // In-window full-queue target 251 -> single window-relative seek 51 -> real index 251
        val inWindowSteps = QueueWindow.seekSteps(targetFullIndex = 251, fullCount = 906, currentFullIndex = 250)
        assertEquals(listOf(51), inWindowSteps)
        for (step in inWindowSteps) bounded.seekTo(step, 0L)
        assertEquals(251, state.currentIndex)

        // Out-of-window full-queue target 10 from 251 -> lands on real index 10
        val toHeadSteps = QueueWindow.seekSteps(targetFullIndex = 10, fullCount = 906, currentFullIndex = state.currentIndex)
        for (step in toHeadSteps) bounded.seekTo(step, 0L)
        assertEquals(10, state.currentIndex)

        // Out-of-window full-queue target 856 from 10 -> lands on real index 856
        val toTailSteps = QueueWindow.seekSteps(targetFullIndex = 856, fullCount = 906, currentFullIndex = state.currentIndex)
        for (step in toTailSteps) bounded.seekTo(step, 0L)
        assertEquals(856, state.currentIndex)
    }

    @Test
    fun largeQueue_timelineWindowAndPeriodIndicesAreWindowRelativeAt865() {
        val (player, state) = createTrackingPlayer(count = 915, currentIndex = 865)
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)
        val tl = bounded.currentTimeline

        // windowStart = 815 (clamped to 915 - 100)
        assertEquals(100, tl.windowCount)
        assertEquals(100, tl.periodCount)
        assertEquals(50, bounded.currentMediaItemIndex)
        assertEquals(50, bounded.currentPeriodIndex)

        val window = Timeline.Window()
        val period = Timeline.Period()
        for (i in 0 until tl.windowCount) {
            tl.getWindow(i, window)
            tl.getPeriod(i, period, true)
            assertEquals("window.firstPeriodIndex at $i", i, window.firstPeriodIndex)
            assertEquals("window.lastPeriodIndex at $i", i, window.lastPeriodIndex)
            assertEquals("period.windowIndex at $i", i, period.windowIndex)
            val uid = tl.getUidOfPeriod(i)
            assertEquals("getIndexOfPeriod(getUidOfPeriod($i))", i, tl.getIndexOfPeriod(uid))
            assertEquals("song_${815 + i}", window.mediaItem.mediaId)
        }

        // Advance across 850..875 and verify period.windowIndex never leaks full index (815..914)
        state.currentIndex = 850
        for (idx in 850..875) {
            state.currentIndex = idx
            val curTl = bounded.currentTimeline
            val winIdx = bounded.currentMediaItemIndex
            curTl.getWindow(winIdx, window)
            curTl.getPeriod(winIdx, period, true)
            assertEquals(winIdx, window.firstPeriodIndex)
            assertEquals(winIdx, period.windowIndex)
            assertEquals(winIdx, curTl.getIndexOfPeriod(curTl.getUidOfPeriod(winIdx)))
            assertEquals("song_$idx", window.mediaItem.mediaId)
        }
    }

    @Test
    fun largeQueue_repeatAllWrap_seekToNextAt914LandsOn0_andPreviousAt0LandsOn914() {
        val (player, state) = createTrackingPlayer(
            count = 915,
            currentIndex = 914,
            repeatMode = Player.REPEAT_MODE_ALL,
        )
        val bounded = BoundedQueuePlayer(player, maxWindow = 100)

        // At last item 914 (windowStart = 815, windowIndex = 99), seekToNext / seekToNextMediaItem
        // must wrap to real index 0, never windowStart (815).
        assertEquals(true, bounded.hasNextMediaItem())
        bounded.seekToNextMediaItem()
        assertEquals(0, state.currentIndex)
        assertEquals(0, bounded.currentMediaItemIndex)
        assertEquals("song_0", bounded.getMediaItemAt(0).mediaId)

        // Reset to 914 and test seekToNext() as well
        state.currentIndex = 914
        bounded.seekToNext()
        assertEquals(0, state.currentIndex)

        // At index 0 with REPEAT_MODE_ALL, seekToPreviousMediaItem must wrap to 914
        assertEquals(true, bounded.hasPreviousMediaItem())
        bounded.seekToPreviousMediaItem()
        assertEquals(914, state.currentIndex)
        assertEquals(99, bounded.currentMediaItemIndex)
        assertEquals("song_914", bounded.getMediaItemAt(99).mediaId)
    }
}
