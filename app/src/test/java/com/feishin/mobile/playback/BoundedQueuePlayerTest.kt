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

    private fun createMockPlayer(count: Int, currentIndex: Int): Player {
        val timeline = FakeTimeline(count)
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentTimeline" -> timeline
                "getCurrentMediaItemIndex" -> currentIndex
                "getMediaItemCount" -> count
                "getMediaItemAt" -> {
                    val idx = args[0] as Int
                    MediaItem.Builder().setMediaId("song_$idx").build()
                }
                "toString" -> "MockPlayer($count, $currentIndex)"
                "hashCode" -> count.hashCode()
                "equals" -> false
                else -> null
            }
        } as Player
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
}
