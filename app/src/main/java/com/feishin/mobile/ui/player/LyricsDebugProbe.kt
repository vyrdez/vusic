package com.feishin.mobile.ui.player

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import com.feishin.mobile.BuildConfig
import kotlin.math.abs

/**
 * Debug-only diagnostic probe for Phase 10.3 Section C lyrics sync verification.
 * Completely no-op with zero allocations when [BuildConfig.DEBUG] is false.
 */
object LyricsDebugProbe {
    const val TAG = "LYRICS_PROBE"

    const val EV_ENTER_COMP = "ENTER_COMP"
    const val EV_PRE_SNAP = "PRE_SNAP"
    const val EV_VIEWPORT_MEASURED = "VIEWPORT_MEASURED"
    const val EV_SCROLL_STEP = "SCROLL_STEP"
    const val EV_FRAME_SETTLED = "FRAME_SETTLED"
    const val EV_LEAVE_START = "LEAVE_START"

    val isEnabled: Boolean
        inline get() = BuildConfig.DEBUG

    inline fun logIfEnabled(
        ev: String,
        posMs: Long,
        userOffMs: Int,
        songOffMs: Long,
        expIdx: Int,
        renIdx: Int,
        lastIdx: Int,
        listState: LazyListState,
        vpH: Int,
        padTop: Int,
        padBot: Int,
        anchorY: Int,
        tabVis: Boolean,
        leaving: Boolean,
        firstEntry: Boolean,
        settingsLoaded: Boolean,
        densityScale: Float,
        coordRunning: Boolean = false,
        includeAnchorErrDp: Boolean = false,
    ) {
        if (!BuildConfig.DEBUG) return
        emit(
            ev = ev,
            posMs = posMs,
            userOffMs = userOffMs,
            songOffMs = songOffMs,
            expIdx = expIdx,
            renIdx = renIdx,
            lastIdx = lastIdx,
            listState = listState,
            vpH = vpH,
            padTop = padTop,
            padBot = padBot,
            anchorY = anchorY,
            tabVis = tabVis,
            leaving = leaving,
            firstEntry = firstEntry,
            settingsLoaded = settingsLoaded,
            densityScale = densityScale,
            coordRunning = coordRunning,
            includeAnchorErrDp = includeAnchorErrDp,
        )
    }

    @PublishedApi
    internal fun emit(
        ev: String,
        posMs: Long,
        userOffMs: Int,
        songOffMs: Long,
        expIdx: Int,
        renIdx: Int,
        lastIdx: Int,
        listState: LazyListState,
        vpH: Int,
        padTop: Int,
        padBot: Int,
        anchorY: Int,
        tabVis: Boolean,
        leaving: Boolean,
        firstEntry: Boolean,
        settingsLoaded: Boolean,
        densityScale: Float,
        coordRunning: Boolean,
        includeAnchorErrDp: Boolean,
    ) {
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        val activeItem = visible.firstOrNull { it.index == renIdx }
        // In LazyColumn with contentPadding, itemInfo.offset is already the pixel offset
        // from the viewport top (first item starts at +padTop when scrollOffset == 0).
        val activeTopY = activeItem?.offset ?: -1
        val activeCenterY = if (activeItem != null) activeTopY + activeItem.size / 2 else -1

        val nearest = if (visible.isEmpty()) {
            emptyList()
        } else {
            visible.sortedBy { abs(it.index - renIdx.coerceAtLeast(0)) }.take(3)
        }
        val txYJson = nearest.joinToString(prefix = "[", postfix = "]") { item ->
            "{\"i\":${item.index},\"y\":0.0}"
        }

        val errField = if (includeAnchorErrDp) {
            val scale = if (densityScale > 0f) densityScale else 1f
            // Anchor error in dp: computeLyricsScrollOffset aligns activeTopY to anchorY,
            // and we take the smaller of |activeTopY - anchorY| and |activeCenterY - anchorY|
            // so single-line and multi-line items both report accurate anchor alignment.
            val errPx = if (activeTopY >= 0) {
                minOf(abs(activeTopY - anchorY), abs(activeCenterY - anchorY))
            } else {
                9999
            }
            val errDp = errPx / scale
            ",\"anchorErrDp\":${String.format(java.util.Locale.US, "%.2f", errDp)}"
        } else {
            ""
        }

        val json = buildString(256) {
            append("{\"ts\":").append(System.currentTimeMillis())
            append(",\"ev\":\"").append(ev).append('"')
            append(",\"posMs\":").append(posMs)
            append(",\"userOffMs\":").append(userOffMs)
            append(",\"songOffMs\":").append(songOffMs)
            append(",\"expIdx\":").append(expIdx)
            append(",\"renIdx\":").append(renIdx)
            append(",\"lastIdx\":").append(lastIdx)
            append(",\"fviIdx\":").append(listState.firstVisibleItemIndex)
            append(",\"fviOff\":").append(listState.firstVisibleItemScrollOffset)
            append(",\"vpH\":").append(vpH)
            append(",\"padTop\":").append(padTop)
            append(",\"padBot\":").append(padBot)
            append(",\"anchorY\":").append(anchorY)
            append(",\"activeTopY\":").append(activeTopY)
            append(",\"activeCenterY\":").append(activeCenterY)
            append(",\"tabVis\":").append(tabVis)
            append(",\"leaving\":").append(leaving)
            append(",\"firstEntry\":").append(firstEntry)
            append(",\"settingsLoaded\":").append(settingsLoaded)
            append(",\"coordRunning\":").append(coordRunning)
            append(",\"txY\":").append(txYJson)
            append(errField)
            append('}')
        }
        Log.d(TAG, json)
    }
}
