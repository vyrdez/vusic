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
    private val maxWindow: Int = QueueWindow.DEFAULT_MAX_WINDOW,
) : ForwardingPlayer(player) {

    private fun windowStart(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return 0
        return QueueWindow.windowStart(
            fullCount = full.windowCount,
            currentFullIndex = super.getCurrentMediaItemIndex(),
            maxWindow = maxWindow,
        )
    }

    override fun getCurrentTimeline(): Timeline {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return full

        val start = windowStart()
        val count = minOf(maxWindow, full.windowCount - start)

        return object : Timeline() {
            override fun getWindowCount(): Int = count
            override fun getPeriodCount(): Int = count

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val realIndex = (start + windowIndex).coerceIn(0, full.windowCount - 1)
                full.getWindow(realIndex, window, defaultPositionProjectionUs)
                window.firstPeriodIndex = windowIndex
                window.lastPeriodIndex = windowIndex
                return window
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                val realIndex = (start + periodIndex).coerceIn(0, full.periodCount - 1)
                full.getPeriod(realIndex, period, setIds)
                period.windowIndex = periodIndex
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

    override fun getCurrentPeriodIndex(): Int = getCurrentMediaItemIndex()

    override fun getCurrentMediaItemIndex(): Int {
        val full = super.getCurrentTimeline()
        val current = super.getCurrentMediaItemIndex()
        if (full.isEmpty || full.windowCount <= maxWindow) return current
        return QueueWindow.toWindowIndex(
            fullIndex = current,
            fullCount = full.windowCount,
            currentFullIndex = current,
            maxWindow = maxWindow,
        )
    }

    override fun getMediaItemCount(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getMediaItemCount()
        return QueueWindow.windowCount(full.windowCount, maxWindow)
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getMediaItemAt(index)
        val realIndex = QueueWindow.toFullIndex(
            windowIndex = index,
            fullCount = full.windowCount,
            currentFullIndex = super.getCurrentMediaItemIndex(),
            maxWindow = maxWindow,
        ).coerceIn(0, super.getMediaItemCount() - 1)
        return super.getMediaItemAt(realIndex)
    }

    override fun seekToNext() {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekToNext()
            return
        }
        val next = QueueWindow.nextFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
            autoAdvance = false,
        ) ?: return
        super.seekTo(next, 0L)
    }

    override fun seekToNextMediaItem() {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekToNextMediaItem()
            return
        }
        val next = QueueWindow.nextFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
            autoAdvance = false,
        ) ?: return
        super.seekTo(next, 0L)
    }

    override fun seekToPrevious() {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekToPrevious()
            return
        }
        val maxSeekToPrev = super.getMaxSeekToPreviousPosition().takeIf { it > 0L } ?: 3_000L
        if (super.getCurrentPosition() > maxSeekToPrev) {
            super.seekTo(super.getCurrentMediaItemIndex(), 0L)
            return
        }
        val prev = QueueWindow.previousFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
        )
        if (prev != null) {
            super.seekTo(prev, 0L)
        } else {
            super.seekTo(super.getCurrentMediaItemIndex(), 0L)
        }
    }

    override fun seekToPreviousMediaItem() {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekToPreviousMediaItem()
            return
        }
        val prev = QueueWindow.previousFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
        ) ?: return
        super.seekTo(prev, 0L)
    }

    override fun hasNextMediaItem(): Boolean {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.hasNextMediaItem()
        return QueueWindow.nextFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
            autoAdvance = false,
        ) != null
    }

    override fun hasPreviousMediaItem(): Boolean {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.hasPreviousMediaItem()
        return QueueWindow.previousFullIndex(
            currentFullIndex = super.getCurrentMediaItemIndex(),
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
        ) != null
    }

    override fun getNextMediaItemIndex(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getNextMediaItemIndex()
        val cur = super.getCurrentMediaItemIndex()
        val nextFull = QueueWindow.nextFullIndex(
            currentFullIndex = cur,
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
            autoAdvance = false,
        ) ?: return androidx.media3.common.C.INDEX_UNSET
        return QueueWindow.toWindowIndex(
            fullIndex = nextFull,
            fullCount = full.windowCount,
            currentFullIndex = cur,
            maxWindow = maxWindow,
        )
    }

    override fun getPreviousMediaItemIndex(): Int {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) return super.getPreviousMediaItemIndex()
        val cur = super.getCurrentMediaItemIndex()
        val prevFull = QueueWindow.previousFullIndex(
            currentFullIndex = cur,
            fullCount = full.windowCount,
            repeatMode = super.getRepeatMode(),
        ) ?: return androidx.media3.common.C.INDEX_UNSET
        return QueueWindow.toWindowIndex(
            fullIndex = prevFull,
            fullCount = full.windowCount,
            currentFullIndex = cur,
            maxWindow = maxWindow,
        )
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekTo(mediaItemIndex, positionMs)
            return
        }
        val realIndex = QueueWindow.toFullIndex(
            windowIndex = mediaItemIndex,
            fullCount = full.windowCount,
            currentFullIndex = super.getCurrentMediaItemIndex(),
            maxWindow = maxWindow,
        )
        super.seekTo(realIndex, positionMs)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        val full = super.getCurrentTimeline()
        if (full.isEmpty || full.windowCount <= maxWindow) {
            super.seekToDefaultPosition(mediaItemIndex)
            return
        }
        val realIndex = QueueWindow.toFullIndex(
            windowIndex = mediaItemIndex,
            fullCount = full.windowCount,
            currentFullIndex = super.getCurrentMediaItemIndex(),
            maxWindow = maxWindow,
        )
        super.seekToDefaultPosition(realIndex)
    }

    override fun seekTo(positionMs: Long) {
        super.seekTo(super.getCurrentMediaItemIndex(), positionMs)
    }
}
