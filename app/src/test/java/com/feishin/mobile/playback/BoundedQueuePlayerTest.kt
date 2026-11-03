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
        val seekCalls: MutableList<SeekCall> = mutableListOf(),
    )

    private fun createTrackingPlayer(
        count: Int,
        currentIndex: Int,
    ): Pair<Player, MockPlayerState> {
        val state = MockPlayerState(count, currentIndex)
        val timeline = FakeTimeline(count)
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentTimeline" -> timeline
                "getCurrentMediaItemIndex" -> state.currentIndex
                "getMediaItemCount" -> count
                "getMediaItemAt" -> {
                    val idx = args[0] as Int
                    MediaItem.Builder().setMediaId("song_$idx").build()
                }
                "seekTo" -> {
                    when (args?.size) {
                        1 -> {
                            val pos = args[0] as Long
                            state.seekCalls.add(SeekCall(state.currentIndex, pos))
                        }
                        2 -> {
                            val idx = args[0] as Int
                            val pos = args[1] as Long
                            state.seekCalls.add(SeekCall(idx, pos))
                        }
                    }
                    null
                }
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
}
