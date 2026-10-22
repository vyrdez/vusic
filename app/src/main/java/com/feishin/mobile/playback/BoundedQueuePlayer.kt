package com.feishin.mobile.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Bounds the timeline and queue published through MediaSession to external controllers
 * (Bluetooth AVRCP, Android Auto, lockscreen).
 *
 * Exposes at most [maxWindow] items centered around the currently playing song.
 * Prevents Binder transaction buffer overflow and Bluetooth daemon crashes on huge playlists.
 */
@androidx.media3.common.util.UnstableApi
class BoundedQueuePlayer(
    player: Player,
    private val maxWindow: Int = 100,
) : ForwardingPlayer(player) {

    private fun windowStart(): Int {
        val full = currentTimeline
        if (full.isEmpty || full.windowCount <= maxWindow) return 0
        val current = currentMediaItemIndex
        val half = maxWindow / 2
        return (current - half).coerceIn(0, maxOf(0, full.windowCount - maxWindow))
    }

    override fun getCurrentTimeline(): Timeline {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return full

        val current = super.getCurrentMediaItemIndex()
        val half = maxWindow / 2
        val start = (current - half).coerceIn(0, maxOf(0, full.windowCount - maxWindow))
        val count = minOf(maxWindow, full.windowCount - start)

        return object : Timeline() {
            override fun getWindowCount(): Int = count
            override fun getPeriodCount(): Int = count

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val realIndex = (start + windowIndex).coerceIn(0, full.windowCount - 1)
                full.getWindow(realIndex, window, defaultPositionProjectionUs)
                return window
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                val realIndex = (start + periodIndex).coerceIn(0, full.periodCount - 1)
                full.getPeriod(realIndex, period, setIds)
                return period
            }

            override fun getIndexOfPeriod(uid: Any): Int {
                val fullIdx = full.getIndexOfPeriod(uid)
                if (fullIdx < 0) return -1
                val relative = fullIdx - start
                return if (relative in 0 until count) relative else -1
            }

            override fun getUidOfPeriod(periodIndex: Int): Any {
                val realIndex = (start + periodIndex).coerceIn(0, full.periodCount - 1)
                return full.getUidOfPeriod(realIndex)
            }
        }
    }

    override fun getCurrentMediaItemIndex(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getCurrentMediaItemIndex()
        val current = super.getCurrentMediaItemIndex()
        val half = maxWindow / 2
        val start = (current - half).coerceIn(0, maxOf(0, full.windowCount - maxWindow))
        return (current - start).coerceIn(0, maxWindow - 1)
    }

    override fun getMediaItemCount(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getMediaItemCount()
        return minOf(maxWindow, full.windowCount)
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getMediaItemAt(index)
        val current = super.getCurrentMediaItemIndex()
        val half = maxWindow / 2
        val start = (current - half).coerceIn(0, maxOf(0, full.windowCount - maxWindow))
        val realIndex = (start + index).coerceIn(0, super.getMediaItemCount() - 1)
        return super.getMediaItemAt(realIndex)
    }
}
