package com.feishin.mobile

import androidx.compose.ui.graphics.TransformOrigin
import com.feishin.mobile.core.common.PagerPeek
import com.feishin.mobile.core.common.SheetPhysics
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.ui.player.computeLyricsAnchorPx
import com.feishin.mobile.ui.player.computeLyricsScrollDelta
import com.feishin.mobile.ui.player.computeLyricsScrollOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit tests for the Phase 10.1 contracts: Sections A, B, C, D and F. */
class Phase10_1LogicTest {

    // -------------------------------------------------------------------------
    // Section A: mini player upward drag drives Now Playing 0f -> 1f
    // -------------------------------------------------------------------------

    private val dragRangePx = 600f

    private fun upwardProgress(dy: Float): Float =
        SheetPhysics.progressAfterDragDown(
            progress = SheetPhysics.CLOSED,
            delta = dy,
            sheetHeightPx = dragRangePx,
        )

    @Test
    fun sectionA_upwardDragProgressRunsFromZeroToOne() {
        assertEquals(0f, upwardProgress(0f), 1e-6f)
        assertEquals(0.25f, upwardProgress(-150f), 1e-6f)
        assertEquals(0.5f, upwardProgress(-300f), 1e-6f)
        assertEquals(1f, upwardProgress(-600f), 1e-6f)
        // Past the travel range it clamps at fully open, never above 1.
        assertEquals(1f, upwardProgress(-1200f), 1e-6f)
        // Downward travel cannot open the sheet.
        assertEquals(0f, upwardProgress(200f), 1e-6f)
    }

    @Test
    fun sectionA_settleCommitsOpenPastThresholdOrOnPositiveVelocity() {
        val threshold = 0.3f
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.31f, 0f, positionThreshold = threshold), 0f)
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.29f, 0f, positionThreshold = threshold), 0f)
        // A decisive positive flick opens even from almost closed.
        assertEquals(SheetPhysics.OPEN, SheetPhysics.settleTarget(0.05f, 0.6f, positionThreshold = threshold), 0f)
        // A negative flick closes even from almost open.
        assertEquals(SheetPhysics.CLOSED, SheetPhysics.settleTarget(0.95f, -0.6f, positionThreshold = threshold), 0f)
    }

    @Test
    fun sectionA_directionalSettleUsesTheDragStartAnchor() {
        val threshold = 0.3f
        assertEquals(
            SheetPhysics.OPEN,
            SheetPhysics.settleTargetFrom(0f, 0.31f, 0f, positionThreshold = threshold),
            0f,
        )
        assertEquals(
            SheetPhysics.CLOSED,
            SheetPhysics.settleTargetFrom(0f, 0.1f, 0f, positionThreshold = threshold),
            0f,
        )
        assertEquals(
            SheetPhysics.CLOSED,
            SheetPhysics.settleTargetFrom(1f, 0.69f, 0f, positionThreshold = threshold),
            0f,
        )
    }

    // -------------------------------------------------------------------------
    // Section B: one bottom indicator, crossfaded with the queue header
    // -------------------------------------------------------------------------

    private object BottomIndicator {
        const val PLAYING_FROM_FADE_END = 0.35f
        const val QUEUE_HEADER_FADE_START = 0.15f
        const val QUEUE_HEADER_FADE_LENGTH = 0.5f
        const val HANDLE_ALPHA = 0.5f

        fun handleAlpha(progress: Float): Float = HANDLE_ALPHA

        fun playingFromAlpha(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return (1f - p / PLAYING_FROM_FADE_END).coerceIn(0f, 1f)
        }

        fun queueHeaderAlpha(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return ((p - QUEUE_HEADER_FADE_START) / QUEUE_HEADER_FADE_LENGTH).coerceIn(0f, 1f)
        }
    }

    @Test
    fun sectionB_atRestHandleAndPlayingFromVisibleQueueHeaderHidden() {
        assertEquals(1f, BottomIndicator.playingFromAlpha(0f), 1e-6f)
        assertEquals(0f, BottomIndicator.queueHeaderAlpha(0f), 1e-6f)
        assertTrue(BottomIndicator.handleAlpha(0f) > 0f)
    }

    @Test
    fun sectionB_playingFromFadesWhileTheSheetRises() {
        val atRest = BottomIndicator.playingFromAlpha(0f)
        val atTwentyPercent = BottomIndicator.playingFromAlpha(0.2f)
        assertTrue("playing-from must fade by p=0.2", atTwentyPercent < atRest)
        assertTrue("playing-from must still be partly visible", atTwentyPercent > 0f)
        assertEquals(0f, BottomIndicator.playingFromAlpha(0.35f), 1e-6f)
    }

    @Test
    fun sectionB_queueHeaderIsFullyVisibleFromSixtyFivePercent() {
        assertEquals(1f, BottomIndicator.queueHeaderAlpha(0.65f), 1e-6f)
        assertEquals(1f, BottomIndicator.queueHeaderAlpha(1f), 1e-6f)
        assertTrue(BottomIndicator.queueHeaderAlpha(0.2f) < 1f)
    }

    // -------------------------------------------------------------------------
    // Section C: uniform page width with 16dp padding, last page flush right
    // -------------------------------------------------------------------------

    private data class PagerGeometry(
        val pageWidthDp: Float,
        val lastPageLeftDp: Float,
        val lastPageRightDp: Float,
    )

    private fun pagerGeometry(viewportDp: Float, pageCount: Int): PagerGeometry {
        val padding = PagerPeek.PAGE_H_PADDING_DP
        val spacing = PagerPeek.SPACING_DP
        val pageWidth = viewportDp - PagerPeek.PAGE_TRIM_DP
        val contentWidth = padding * 2f + pageWidth * pageCount + spacing * (pageCount - 1)
        val maxScroll = (contentWidth - viewportDp).coerceAtLeast(0f)
        val lastPageLeft = padding + (pageCount - 1) * (pageWidth + spacing) - maxScroll
        return PagerGeometry(pageWidth, lastPageLeft, lastPageLeft + pageWidth)
    }

    @Test
    fun sectionC_everyPageHasTheSameWidthWithSixteenDpPadding() {
        assertEquals(16f, PagerPeek.PAGE_H_PADDING_DP, 0f)
        for (viewport in listOf(320f, 360f, 411f, 600f)) {
            val geometry = pagerGeometry(viewport, pageCount = 6)
            assertEquals(viewport - PagerPeek.PAGE_TRIM_DP, geometry.pageWidthDp, 1e-6f)
        }
    }

    @Test
    fun sectionC_lastPageSitsFlushRightAndPreviousPagePeeksLeft() {
        for (viewport in listOf(320f, 360f, 411f, 600f)) {
            val geometry = pagerGeometry(viewport, pageCount = 6)
            assertEquals(
                viewport - PagerPeek.PAGE_H_PADDING_DP,
                geometry.lastPageRightDp,
                1e-6f,
            )
            assertTrue(
                "previous page must peek on the left",
                geometry.lastPageLeftDp - PagerPeek.SPACING_DP > 0f,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Section D: Now Playing tab expiry and lyrics anchor on reopen
    // -------------------------------------------------------------------------

    private object NowPlayingTabExpiry {
        const val TAB_EXPIRY_MS = 5 * 60 * 1000L

        fun expires(lastBackgroundedAtMs: Long, nowMs: Long): Boolean =
            lastBackgroundedAtMs > 0L && nowMs - lastBackgroundedAtMs >= TAB_EXPIRY_MS

        fun tabAfterBackground(lyricsMode: Boolean, backgroundedAtMs: Long, nowMs: Long): Boolean =
            if (expires(backgroundedAtMs, nowMs)) false else lyricsMode
    }

    @Test
    fun sectionD_coldStartDefaultsToSongTab() {
        assertFalse(SettingsStore.Settings().nowPlayingLyricsMode)
    }

    @Test
    fun sectionD_shortBackgroundKeepsTheLyricsTab() {
        val backgroundedAt = 1_000_000L
        val twoMinutesLater = backgroundedAt + 2 * 60 * 1000L
        assertTrue(NowPlayingTabExpiry.tabAfterBackground(true, backgroundedAt, twoMinutesLater))
    }

    @Test
    fun sectionD_fiveMinutesInBackgroundExpiresBackToSong() {
        val backgroundedAt = 1_000_000L
        val justBefore = backgroundedAt + NowPlayingTabExpiry.TAB_EXPIRY_MS - 1L
        val expired = backgroundedAt + NowPlayingTabExpiry.TAB_EXPIRY_MS + 1_000L
        assertTrue(NowPlayingTabExpiry.tabAfterBackground(true, backgroundedAt, justBefore))
        assertFalse(NowPlayingTabExpiry.tabAfterBackground(true, backgroundedAt, expired))
    }

    @Test
    fun sectionD_syncedLyricsAnchorIsThirtyFivePercentOfTheViewport() {
        val viewportHeightPx = 2400
        val contentPaddingTopPx = 120
        val anchorPx = computeLyricsAnchorPx(viewportHeightPx, 0.35f)
        assertEquals(840, anchorPx)

        // On reopen the active line returns to that anchor.
        val scrollOffset = computeLyricsScrollOffset(viewportHeightPx, contentPaddingTopPx, 0.35f)
        assertEquals(-720, scrollOffset)
        assertEquals(anchorPx, contentPaddingTopPx - scrollOffset)

        assertEquals(60f, computeLyricsScrollDelta(900, viewportHeightPx, 0.35f), 1e-6f)
    }

    // -------------------------------------------------------------------------
    // Section F: dropdown flips above near the bottom edge, no jump
    // -------------------------------------------------------------------------

    private data class DropdownPlacement(val y: Int, val above: Boolean, val transformOrigin: TransformOrigin)

    private fun placeDropdown(
        anchorTop: Int,
        anchorBottom: Int,
        popupHeight: Int,
        windowHeight: Int,
        marginPx: Int = 8,
        gapPx: Int = 6,
    ): DropdownPlacement {
        val fitsBelow = anchorBottom + gapPx + popupHeight <= windowHeight - marginPx
        return if (fitsBelow) {
            DropdownPlacement(
                y = anchorBottom + gapPx,
                above = false,
                transformOrigin = TransformOrigin(1f, 0f),
            )
        } else {
            DropdownPlacement(
                y = anchorTop - popupHeight - gapPx,
                above = true,
                transformOrigin = TransformOrigin(1f, 1f),
            )
        }
    }

    @Test
    fun sectionF_anchorNearTheBottomPlacesThePopupAbove() {
        val placement = placeDropdown(
            anchorTop = 1900,
            anchorBottom = 1960,
            popupHeight = 300,
            windowHeight = 2000,
        )
        assertTrue(placement.above)
        assertEquals(1900 - 300 - 6, placement.y)
        assertEquals(TransformOrigin(1f, 1f), placement.transformOrigin)
    }

    @Test
    fun sectionF_enoughSpaceBelowPlacesThePopupBelow() {
        val placement = placeDropdown(
            anchorTop = 100,
            anchorBottom = 160,
            popupHeight = 300,
            windowHeight = 2000,
        )
        assertFalse(placement.above)
        assertEquals(160 + 6, placement.y)
        assertEquals(TransformOrigin(1f, 0f), placement.transformOrigin)
    }
}
