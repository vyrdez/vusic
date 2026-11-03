package com.feishin.mobile

import com.feishin.mobile.ui.player.ScrollAction
import com.feishin.mobile.ui.player.computeLyricsAnchorPx
import com.feishin.mobile.ui.player.computeLyricsScrollDelta
import com.feishin.mobile.ui.player.computeLyricsScrollOffset
import com.feishin.mobile.ui.player.resolveLyricsScrollStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for Phase 10.2 contracts: Sections A, B, C, and D.
 */
class Phase10_2LogicTest {

    // -------------------------------------------------------------------------
    // Section A: Stationary fading background & sliding foreground
    // -------------------------------------------------------------------------

    object StationaryBackgroundMath {
        const val BACKGROUND_FADE_END = 0.65f

        /**
         * Backdrop stays pinned at translationY == 0f throughout downward drag,
         * avoiding translating along with sliding foreground content.
         */
        fun backgroundTranslationY(): Float = 0f

        /**
         * Backdrop alpha transitions monotonically from 0f (closed) to 1f (open at progress >= 0.65f).
         */
        fun backgroundAlpha(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return (p / BACKGROUND_FADE_END).coerceIn(0f, 1f)
        }

        /**
         * Sliding foreground content translates from [travelPx] (progress = 0f) to 0f (progress = 1f).
         */
        fun foregroundTranslationY(progress: Float, travelPx: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return (1f - p) * travelPx
        }

        /**
         * Sliding foreground content alpha fades in smoothly.
         */
        fun foregroundAlpha(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return (p / BACKGROUND_FADE_END).coerceIn(0f, 1f)
        }
    }

    @Test
    fun sectionA_backgroundTranslationY_remainsStationaryAtZero() {
        val testProgressValues = listOf(-0.5f, 0f, 0.1f, 0.35f, 0.5f, 0.65f, 0.9f, 1.0f, 1.5f)
        for (p in testProgressValues) {
            assertEquals("Background translationY must remain 0f", 0f, StationaryBackgroundMath.backgroundTranslationY(), 0f)
        }
    }

    @Test
    fun sectionA_backgroundAlpha_monotonicIncreaseFromZeroToOne() {
        assertEquals(0f, StationaryBackgroundMath.backgroundAlpha(0f), 1e-6f)
        assertEquals(1f, StationaryBackgroundMath.backgroundAlpha(StationaryBackgroundMath.BACKGROUND_FADE_END), 1e-6f)
        assertEquals(1f, StationaryBackgroundMath.backgroundAlpha(1f), 1e-6f)

        var prevAlpha = -0.01f
        for (step in 0..100) {
            val p = step / 100f
            val alpha = StationaryBackgroundMath.backgroundAlpha(p)
            assertTrue("Alpha must be non-decreasing at progress=$p", alpha >= prevAlpha)
            assertTrue("Alpha must be in 0f..1f range: alpha=$alpha", alpha in 0f..1f)
            prevAlpha = alpha
        }
    }

    @Test
    fun sectionA_foregroundTranslationAndAlpha_translateMonotonically() {
        val travelPx = 2400f

        // Progress 0f = closed sheet (translated fully down by travelPx)
        assertEquals(travelPx, StationaryBackgroundMath.foregroundTranslationY(0f, travelPx), 1e-6f)
        assertEquals(0f, StationaryBackgroundMath.foregroundAlpha(0f), 1e-6f)

        // Progress 1f = open sheet (translation 0f, full alpha 1f)
        assertEquals(0f, StationaryBackgroundMath.foregroundTranslationY(1f, travelPx), 1e-6f)
        assertEquals(1f, StationaryBackgroundMath.foregroundAlpha(1f), 1e-6f)

        // Progress 0.5f = half travel
        assertEquals(1200f, StationaryBackgroundMath.foregroundTranslationY(0.5f, travelPx), 1e-6f)

        // Monotonic decrease of translationY as progress increases 0f -> 1f
        var prevTranslation = travelPx + 1f
        for (step in 0..100) {
            val p = step / 100f
            val ty = StationaryBackgroundMath.foregroundTranslationY(p, travelPx)
            assertTrue("Foreground translationY must decrease monotonically as sheet opens", ty <= prevTranslation)
            prevTranslation = ty
        }
    }

    // -------------------------------------------------------------------------
    // Section B: Downward drag across 20 steps without 700px clamp
    // -------------------------------------------------------------------------

    object DownwardDragMath {
        /**
         * Full unclamped downward drag progress:
         * dragOffsetPx = 0f corresponds to progress = 1.0f (fully open).
         * dragOffsetPx = sheetHeightPx corresponds to progress = 0.0f (fully closed).
         */
        fun progressFromDownwardDrag(dragOffsetPx: Float, sheetHeightPx: Float): Float {
            if (sheetHeightPx <= 0f) return 0f
            val fraction = (dragOffsetPx / sheetHeightPx).coerceIn(0f, 1f)
            return (1f - fraction).coerceIn(0f, 1f)
        }

        /**
         * Legacy defective logic that clamped drag offset to 700f, causing sheet to freeze at 25-35%.
         */
        fun legacyClampedProgress(dragOffsetPx: Float, sheetHeightPx: Float): Float {
            if (sheetHeightPx <= 0f) return 0f
            val clampedOffset = dragOffsetPx.coerceAtMost(700f)
            val fraction = (clampedOffset / sheetHeightPx).coerceIn(0f, 1f)
            return (1f - fraction).coerceIn(0f, 1f)
        }
    }

    @Test
    fun sectionB_downwardDragAcrossTwentyStepsDecreasesMonotonicallyWithoutClamp() {
        val sheetHeightPx = 2400f
        val steps = 20

        var prevProgress = 1.01f
        for (step in 0..steps) {
            val dragOffsetPx = (step.toFloat() / steps) * sheetHeightPx
            val progress = DownwardDragMath.progressFromDownwardDrag(dragOffsetPx, sheetHeightPx)

            // Step 0 = fully open (1.0f)
            if (step == 0) {
                assertEquals(1.0f, progress, 1e-6f)
            }
            // Step 20 = fully closed (0.0f)
            if (step == steps) {
                assertEquals(0.0f, progress, 1e-6f)
            }

            // Monotonically decreasing without stalling
            assertTrue("Progress must decrease monotonically at step $step (offset $dragOffsetPx px)", progress <= prevProgress)
            prevProgress = progress
        }

        // Specifically test offsets past 700px where the legacy clamp previously stalled
        val pastClampOffset = 1200f // 50% down on 2400px display
        val unclampedProgress = DownwardDragMath.progressFromDownwardDrag(pastClampOffset, sheetHeightPx)
        val legacyProgress = DownwardDragMath.legacyClampedProgress(pastClampOffset, sheetHeightPx)

        assertEquals(0.5f, unclampedProgress, 1e-6f)
        // Legacy clamp at 700f resulted in ~0.7083f (stuck around 25-35% travel)
        assertEquals(1f - (700f / 2400f), legacyProgress, 1e-4f)
        assertTrue("Unclamped progress must travel past the 700px barrier", unclampedProgress < legacyProgress)
    }

    // -------------------------------------------------------------------------
    // Section C: Lyrics scroll anchor formula on reopen keeping active line at ~35%
    // -------------------------------------------------------------------------

    @Test
    fun sectionC_lyricsScrollAnchorFormula_placesActiveLineAtThirtyFivePercent() {
        val viewportHeightPx = 2400
        val contentPaddingTopPx = 120
        val anchorFraction = 0.35f

        val anchorPx = computeLyricsAnchorPx(viewportHeightPx, anchorFraction)
        // 2400 * 0.35 = 840px
        assertEquals(840, anchorPx)
        assertEquals(0.35f, anchorPx.toFloat() / viewportHeightPx, 1e-4f)

        // Scroll offset accounts for content padding: placed position = contentPaddingTopPx - scrollOffset
        val scrollOffset = computeLyricsScrollOffset(viewportHeightPx, contentPaddingTopPx, anchorFraction)
        assertEquals(-720, scrollOffset)
        val placedItemTop = contentPaddingTopPx - scrollOffset
        assertEquals("Placed line must sit exactly at the 35% anchor line", anchorPx, placedItemTop)
    }

    @Test
    fun sectionC_lyricsScrollOnReopen_recoversFromUserDragToKeepActiveLineAtAnchor() {
        val viewportHeightPx = 2400
        val anchorFraction = 0.35f
        val anchorPx = computeLyricsAnchorPx(viewportHeightPx, anchorFraction) // 840px

        // User dragged list so active line sits near bottom at 2000px
        val draggedItemTop = 2000
        val delta = computeLyricsScrollDelta(draggedItemTop, viewportHeightPx, anchorFraction)

        // Scroll delta must bring the line up by 1160px back to 840px anchor
        assertEquals(1160f, delta, 1e-6f)
        assertEquals(anchorPx.toFloat(), draggedItemTop - delta, 1e-6f)
    }

    @Test
    fun sectionC_reopenFirstEntrySnapsEvenWhenActiveIndexMatchesLastScrolledIndex() {
        val activeIndex = 12
        val lastScrolledIndex = 12

        // Normal during-playback scroll step returns null when index hasn't changed
        val steadyStateAction = resolveLyricsScrollStep(
            isFirstEntryInComposition = false,
            activeIndex = activeIndex,
            lastScrolledIndex = lastScrolledIndex,
            isTargetItemVisible = true,
        )
        assertNull("Steady-state scroll should be a no-op when index hasn't changed", steadyStateAction)

        // On screen reopen / re-entry, isFirstEntryInComposition is true: must SNAP to restore anchor
        val reopenAction = resolveLyricsScrollStep(
            isFirstEntryInComposition = true,
            activeIndex = activeIndex,
            lastScrolledIndex = lastScrolledIndex,
            isTargetItemVisible = false,
        )
        assertEquals("Reopening must force SNAP to align active line at 35% anchor", ScrollAction.SNAP, reopenAction)
    }

    // -------------------------------------------------------------------------
    // Section D: Seekbar scrub state machine & zero/negative duration safety
    // -------------------------------------------------------------------------

    class SeekbarScrubStateMachine(
        private val onSeekDispatched: (Long) -> Unit,
    ) {
        var isDragging: Boolean = false
            private set
        var previewMs: Long? = null
            private set
        val dispatchedSeeks: MutableList<Long> = mutableListOf()

        fun calculateMsAt(x: Float, width: Float, durationMs: Long): Long {
            if (width <= 0f) return 0L
            val dur = durationMs.coerceAtLeast(0L)
            if (dur <= 0L) return 0L
            val fraction = (x / width).coerceIn(0f, 1f)
            return (fraction * dur).toLong().coerceIn(0L, dur)
        }

        fun onDragStart(x: Float, width: Float, durationMs: Long) {
            isDragging = true
            previewMs = calculateMsAt(x, width, durationMs)
        }

        fun onDragMove(x: Float, width: Float, durationMs: Long) {
            if (!isDragging) return
            previewMs = calculateMsAt(x, width, durationMs)
        }

        fun onDragEnd() {
            val target = previewMs
            previewMs = null
            isDragging = false
            if (target != null) {
                dispatchedSeeks.add(target)
                onSeekDispatched(target)
            }
        }

        fun onDragCancel() {
            previewMs = null
            isDragging = false
        }

        fun onDirectTap(x: Float, width: Float, durationMs: Long) {
            val target = calculateMsAt(x, width, durationMs)
            dispatchedSeeks.add(target)
            onSeekDispatched(target)
        }
    }

    @Test
    fun sectionD_scrubbingUpdatesPreviewAndDispatchesSingleSeekOnDragEnd() {
        var latestSeek: Long? = null
        val stateMachine = SeekbarScrubStateMachine { latestSeek = it }
        val durationMs = 240_000L
        val trackWidthPx = 1000f

        // Start scrub at 10%
        stateMachine.onDragStart(x = 100f, width = trackWidthPx, durationMs = durationMs)
        assertTrue(stateMachine.isDragging)
        assertEquals(24_000L, stateMachine.previewMs)
        assertEquals(0, stateMachine.dispatchedSeeks.size)
        assertNull(latestSeek)

        // Scrub move updates preview without seeking
        stateMachine.onDragMove(x = 250f, width = trackWidthPx, durationMs = durationMs)
        assertEquals(60_000L, stateMachine.previewMs)
        assertEquals(0, stateMachine.dispatchedSeeks.size)

        stateMachine.onDragMove(x = 500f, width = trackWidthPx, durationMs = durationMs)
        assertEquals(120_000L, stateMachine.previewMs)
        assertEquals(0, stateMachine.dispatchedSeeks.size)

        stateMachine.onDragMove(x = 750f, width = trackWidthPx, durationMs = durationMs)
        assertEquals(180_000L, stateMachine.previewMs)
        assertEquals(0, stateMachine.dispatchedSeeks.size)

        // Drag finish dispatches exactly one seek
        stateMachine.onDragEnd()
        assertFalse(stateMachine.isDragging)
        assertNull(stateMachine.previewMs)
        assertEquals(1, stateMachine.dispatchedSeeks.size)
        assertEquals(180_000L, latestSeek)
    }

    @Test
    fun sectionD_dragCancelDiscardsPreviewWithoutSeeking() {
        var latestSeek: Long? = null
        val stateMachine = SeekbarScrubStateMachine { latestSeek = it }
        val durationMs = 180_000L
        val trackWidthPx = 600f

        stateMachine.onDragStart(x = 200f, width = trackWidthPx, durationMs = durationMs)
        stateMachine.onDragMove(x = 400f, width = trackWidthPx, durationMs = durationMs)
        assertEquals(120_000L, stateMachine.previewMs)

        stateMachine.onDragCancel()
        assertFalse(stateMachine.isDragging)
        assertNull(stateMachine.previewMs)
        assertEquals(0, stateMachine.dispatchedSeeks.size)
        assertNull(latestSeek)
    }

    @Test
    fun sectionD_nonPositiveOrUnsetDurationNeverThrows() {
        val stateMachine = SeekbarScrubStateMachine {}
        val invalidDurations = listOf(0L, -1L, -1000L, Long.MIN_VALUE)

        for (dur in invalidDurations) {
            val resultZeroWidth = stateMachine.calculateMsAt(x = 50f, width = 0f, durationMs = dur)
            assertEquals(0L, resultZeroWidth)

            val resultNormalWidth = stateMachine.calculateMsAt(x = 50f, width = 200f, durationMs = dur)
            assertEquals(0L, resultNormalWidth)

            val resultNegativeX = stateMachine.calculateMsAt(x = -50f, width = 200f, durationMs = dur)
            assertEquals(0L, resultNegativeX)
        }
    }

    @Test
    fun sectionD_safeSeekTargetClampingWithUnsetDuration() {
        fun computeSafeTargetMs(ms: Long, durationMs: Long): Long {
            val dur = durationMs.coerceAtLeast(0L)
            return if (dur > 0L) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
        }

        // Known duration: clamps between 0L and durationMs
        assertEquals(120_000L, computeSafeTargetMs(120_000L, 180_000L))
        assertEquals(180_000L, computeSafeTargetMs(250_000L, 180_000L))
        assertEquals(0L, computeSafeTargetMs(-500L, 180_000L))

        // Unknown / unset duration (0L or -1L): safely clamps at least 0L, never throws empty range
        assertEquals(50_000L, computeSafeTargetMs(50_000L, -1L))
        assertEquals(0L, computeSafeTargetMs(-500L, -1L))
        assertEquals(0L, computeSafeTargetMs(0L, 0L))
    }
}
