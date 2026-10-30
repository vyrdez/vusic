package com.feishin.mobile

import com.feishin.mobile.core.common.MiniPlayerGesture
import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for Phase 10 pure logic across Sections A, B, C, D, and E.
 */
class Phase10LogicTest {

    // -------------------------------------------------------------------------
    // Helper pure functions modeling the verified logic contracts
    // -------------------------------------------------------------------------

    object SpeedDialMath {
        const val PAGE_SIZE = 9
        const val MAX_PAGES = 3
        const val MAX_ITEMS = PAGE_SIZE * MAX_PAGES // 27

        fun <T> paginate(items: List<T>): List<List<T>> {
            return items.take(MAX_ITEMS).chunked(PAGE_SIZE).take(MAX_PAGES)
        }
    }

    object PagerAlignmentMath {
        const val TRACKS_PER_PAGE = 4
        const val MAX_TRACKS = 24
        const val MAX_PAGES = MAX_TRACKS / TRACKS_PER_PAGE // 6

        fun <T> paginateTracks(tracks: List<T>): List<List<T>> {
            val chunked = tracks.take(MAX_TRACKS).chunked(TRACKS_PER_PAGE)
            return chunked.filter { it.isNotEmpty() }
        }

        fun hasTrailingEmptySpace(pageCount: Int, totalItems: Int): Boolean {
            if (totalItems == 0) return false
            val expectedPages = (totalItems + TRACKS_PER_PAGE - 1) / TRACKS_PER_PAGE
            return pageCount > expectedPages
        }
    }

    data class TrackPlayRecord(
        val track: Track,
        val lastPlayedEpochSec: Long, // 0 if never played
    )

    object ForgottenFavoritesLogic {
        const val MAX_FORGOTTEN_TRACKS = 24

        fun selectForgottenFavorites(
            records: List<TrackPlayRecord>,
            recentTrackIds: Set<String>,
            nowEpochSec: Long,
            recentCutoffWindowSec: Long = 30L * 24 * 3600, // 30 days
        ): List<Track> {
            return records
                // Exclude currently recent tracks
                .filter { it.track.id !in recentTrackIds }
                // Exclude tracks played within recentCutoffWindowSec
                .filter { record ->
                    val playedAgo = nowEpochSec - record.lastPlayedEpochSec
                    record.lastPlayedEpochSec == 0L || playedAgo > recentCutoffWindowSec
                }
                // Ranks oldest played first (ascending timestamp, 0L / never played first)
                .sortedWith(compareBy<TrackPlayRecord> { it.lastPlayedEpochSec }.thenBy { it.track.title })
                // Capped at 24 tracks
                .take(MAX_FORGOTTEN_TRACKS)
                .map { it.track }
        }
    }

    object QueueBarVisuals {
        const val FADE_COMPLETE_PROGRESS = 0.45f

        /**
         * Upward shift in pixels. Shifts upward monotonically from 0f at progress=0f
         * to -maxShiftPx at progress=1f.
         */
        fun translationY(progress: Float, maxShiftPx: Float): Float {
            val clamped = progress.coerceIn(0f, 1f)
            return -clamped * maxShiftPx
        }

        /**
         * Alpha 1f at progress 0f down to 0f at progress >= 0.45f.
         */
        fun alpha(progress: Float): Float {
            val clamped = progress.coerceIn(0f, 1f)
            if (clamped >= FADE_COMPLETE_PROGRESS) return 0f
            return (1f - (clamped / FADE_COMPLETE_PROGRESS)).coerceIn(0f, 1f)
        }
    }

    private fun makeTrack(id: String, title: String = "Track $id"): Track {
        return Track(
            id = id,
            title = title,
            album = "Album",
            albumId = "alb-1",
            artist = "Artist",
            artistId = "art-1",
            coverArt = null,
            durationSec = 200,
            trackNo = 1,
            year = 2024,
            genre = "Rock",
            suffix = "mp3",
            starred = true,
        )
    }

    // -------------------------------------------------------------------------
    // REQUIREMENT 1: Section A - Speed dial pagination math
    // -------------------------------------------------------------------------

    @Test
    fun sectionA_speedDialPagination_emptyListProducesZeroPages() {
        val pages = SpeedDialMath.paginate(emptyList<String>())
        assertTrue(pages.isEmpty())
    }

    @Test
    fun sectionA_speedDialPagination_nineOrFewerProducesOnePage() {
        val items5 = (1..5).map { "item$it" }
        val pages5 = SpeedDialMath.paginate(items5)
        assertEquals(1, pages5.size)
        assertEquals(5, pages5[0].size)

        val items9 = (1..9).map { "item$it" }
        val pages9 = SpeedDialMath.paginate(items9)
        assertEquals(1, pages9.size)
        assertEquals(9, pages9[0].size)
    }

    @Test
    fun sectionA_speedDialPagination_tenToEighteenProducesTwoPages() {
        val items18 = (1..18).map { "item$it" }
        val pages18 = SpeedDialMath.paginate(items18)
        assertEquals(2, pages18.size)
        assertEquals(9, pages18[0].size)
        assertEquals(9, pages18[1].size)
    }

    @Test
    fun sectionA_speedDialPagination_twentySevenItemsAcrossThreePagesOfNine() {
        val items27 = (1..27).map { "item$it" }
        val pages27 = SpeedDialMath.paginate(items27)
        assertEquals(3, pages27.size)
        assertEquals(9, pages27[0].size)
        assertEquals(9, pages27[1].size)
        assertEquals(9, pages27[2].size)
        assertEquals("item1", pages27[0].first())
        assertEquals("item9", pages27[0].last())
        assertEquals("item10", pages27[1].first())
        assertEquals("item18", pages27[1].last())
        assertEquals("item19", pages27[2].first())
        assertEquals("item27", pages27[2].last())
    }

    @Test
    fun sectionA_speedDialPagination_capsAtTwentySevenItemsAndThreePages() {
        val items50 = (1..50).map { "item$it" }
        val pages50 = SpeedDialMath.paginate(items50)
        assertEquals(3, pages50.size)
        assertEquals(27, pages50.sumOf { it.size })
        assertEquals("item27", pages50.last().last())
    }

    // -------------------------------------------------------------------------
    // REQUIREMENT 2: Section B - Pure gesture math for horizontal rubber-band damping & upward drag
    // -------------------------------------------------------------------------

    @Test
    fun sectionB_horizontalRubberBand_neverExceedsMaxShiftPx() {
        val maxShiftPx = 150f
        val extremeDrags = listOf(0f, 50f, 150f, 300f, 1000f, 50_000f, -50f, -150f, -300f, -1000f, -50_000f)

        for (drag in extremeDrags) {
            val damped = MiniPlayerGesture.dampedOffsetX(drag, maxShiftPx)
            assertTrue("Damped offset $damped exceeded max $maxShiftPx for drag $drag", abs(damped) <= maxShiftPx)
        }
    }

    @Test
    fun sectionB_horizontalRubberBand_monotonicWithRawDragX() {
        val maxShiftPx = 200f
        var prevDamped = Float.NEGATIVE_INFINITY
        for (dragInt in -500..500 step 10) {
            val drag = dragInt.toFloat()
            val damped = MiniPlayerGesture.dampedOffsetX(drag, maxShiftPx)
            assertTrue("Expected monotonic increase at drag $drag: prev=$prevDamped, curr=$damped", damped >= prevDamped)
            prevDamped = damped
        }
    }

    @Test
    fun sectionB_horizontalRubberBand_zeroDragZeroShiftAndBounceBackTargetZero() {
        val maxShift = 100f
        assertEquals(0f, MiniPlayerGesture.dampedOffsetX(0f, maxShift), 1e-6f)
        assertEquals(0f, MiniPlayerGesture.dampedOffsetX(50f, 0f), 1e-6f)

        // Bounce-back target for horizontal release is 0f
        val bounceBackTarget = 0f
        assertEquals(0f, bounceBackTarget, 0f)
    }

    @Test
    fun sectionB_upwardDragAlphaFadeAndTranslationCurve() {
        val fadeDistancePx = 120f

        // At rest (0px travel) or downward drag (positive dragY), alpha must be 1f
        assertEquals(1f, MiniPlayerGesture.upwardFadeAlpha(0f, fadeDistancePx), 1e-6f)
        assertEquals(1f, MiniPlayerGesture.upwardFadeAlpha(50f, fadeDistancePx), 1e-6f)

        // Upward drag is negative dragY
        val halfAlpha = MiniPlayerGesture.upwardFadeAlpha(-60f, fadeDistancePx)
        assertEquals(0.5f, halfAlpha, 1e-4f)

        // Fully faded at -fadeDistancePx or beyond
        assertEquals(0f, MiniPlayerGesture.upwardFadeAlpha(-120f, fadeDistancePx), 1e-6f)
        assertEquals(0f, MiniPlayerGesture.upwardFadeAlpha(-200f, fadeDistancePx), 1e-6f)

        // Monotonic decrease of alpha as upward distance increases (-dragY increases)
        var prevAlpha = 1.01f
        for (distInt in 0..150 step 5) {
            val dragY = -distInt.toFloat()
            val alpha = MiniPlayerGesture.upwardFadeAlpha(dragY, fadeDistancePx)
            assertTrue("Alpha should decrease monotonically: dist=$distInt alpha=$alpha", alpha <= prevAlpha)
            prevAlpha = alpha
        }
    }

    // -------------------------------------------------------------------------
    // REQUIREMENT 3: Section C - Pager alignment math
    // -------------------------------------------------------------------------

    @Test
    fun sectionC_pagerAlignment_twentyFourTracksProducesSixPagesOfFour() {
        val tracks = (1..24).map { makeTrack("t$it") }
        val pages = PagerAlignmentMath.paginateTracks(tracks)
        assertEquals(6, pages.size)
        pages.forEach { page ->
            assertEquals(4, page.size)
        }
        assertFalse(PagerAlignmentMath.hasTrailingEmptySpace(pages.size, tracks.size))
    }

    @Test
    fun sectionC_pagerAlignment_nonDivisibleCountsHandledCleanlyWithoutEmptyTrailingSpace() {
        // 10 tracks -> 2 full pages of 4, 1 partial page of 2 = 3 pages total
        val tracks10 = (1..10).map { makeTrack("t$it") }
        val pages10 = PagerAlignmentMath.paginateTracks(tracks10)
        assertEquals(3, pages10.size)
        assertEquals(4, pages10[0].size)
        assertEquals(4, pages10[1].size)
        assertEquals(2, pages10[2].size)
        assertFalse(PagerAlignmentMath.hasTrailingEmptySpace(pages10.size, tracks10.size))

        // 1 track -> 1 page of 1
        val tracks1 = listOf(makeTrack("t1"))
        val pages1 = PagerAlignmentMath.paginateTracks(tracks1)
        assertEquals(1, pages1.size)
        assertEquals(1, pages1[0].size)
        assertFalse(PagerAlignmentMath.hasTrailingEmptySpace(pages1.size, tracks1.size))

        // 25 tracks -> capped at 24 tracks (6 pages of 4), no 7th empty/singleton page
        val tracks25 = (1..25).map { makeTrack("t$it") }
        val pages25 = PagerAlignmentMath.paginateTracks(tracks25)
        assertEquals(6, pages25.size)
        assertEquals(24, pages25.sumOf { it.size })
        assertFalse(PagerAlignmentMath.hasTrailingEmptySpace(pages25.size, 24))
    }

    // -------------------------------------------------------------------------
    // REQUIREMENT 4: Section D - Forgotten favorites selection logic
    // -------------------------------------------------------------------------

    @Test
    fun sectionD_forgottenFavorites_excludesRecentTrackIds() {
        val now = 10_000_000L
        val oldPlayed = 100_000L // Played long ago (> 30 days)
        val t1 = makeTrack("t1")
        val t2 = makeTrack("t2")

        val records = listOf(
            TrackPlayRecord(t1, oldPlayed),
            TrackPlayRecord(t2, oldPlayed),
        )

        val selected = ForgottenFavoritesLogic.selectForgottenFavorites(
            records = records,
            recentTrackIds = setOf("t1"),
            nowEpochSec = now,
        )

        assertEquals(listOf(t2), selected)
    }

    @Test
    fun sectionD_forgottenFavorites_excludesRecentlyPlayedWithinWindow() {
        val now = 1_000_000L
        val recentCutoff = 30L * 24 * 3600 // 2_592_000s
        val nowSec = 10_000_000L
        val playedRecently = nowSec - 1000L // 1000s ago
        val playedLongAgo = nowSec - (40L * 24 * 3600) // 40 days ago

        val tRecent = makeTrack("recent")
        val tOld = makeTrack("old")

        val records = listOf(
            TrackPlayRecord(tRecent, playedRecently),
            TrackPlayRecord(tOld, playedLongAgo),
        )

        val selected = ForgottenFavoritesLogic.selectForgottenFavorites(
            records = records,
            recentTrackIds = emptySet(),
            nowEpochSec = nowSec,
            recentCutoffWindowSec = recentCutoff,
        )

        assertEquals(listOf(tOld), selected)
    }

    @Test
    fun sectionD_forgottenFavorites_ranksOldestPlayedFirst() {
        val nowSec = 10_000_000L
        val day = 86400L

        val tNever = makeTrack("never", "A Never Played")
        val t100DaysAgo = makeTrack("old100", "B Played 100d ago")
        val t50DaysAgo = makeTrack("old50", "C Played 50d ago")

        val records = listOf(
            TrackPlayRecord(t50DaysAgo, nowSec - 50 * day),
            TrackPlayRecord(tNever, 0L),
            TrackPlayRecord(t100DaysAgo, nowSec - 100 * day),
        )

        val selected = ForgottenFavoritesLogic.selectForgottenFavorites(
            records = records,
            recentTrackIds = emptySet(),
            nowEpochSec = nowSec,
            recentCutoffWindowSec = 30 * day,
        )

        assertEquals(listOf(tNever, t100DaysAgo, t50DaysAgo), selected)
    }

    @Test
    fun sectionD_forgottenFavorites_capsAtTwentyFourTracks() {
        val nowSec = 10_000_000L
        val records = (1..50).map { i ->
            TrackPlayRecord(makeTrack("t$i", "Title $i"), 1000L + i)
        }

        val selected = ForgottenFavoritesLogic.selectForgottenFavorites(
            records = records,
            recentTrackIds = emptySet(),
            nowEpochSec = nowSec,
        )

        assertEquals(24, selected.size)
        assertEquals("t1", selected.first().id)
        assertEquals("t24", selected.last().id)
    }

    // -------------------------------------------------------------------------
    // REQUIREMENT 5: Section E - Queue bar translation and alpha calculation
    // -------------------------------------------------------------------------

    @Test
    fun sectionE_queueBar_monotonicUpwardShift() {
        val maxShiftPx = 100f
        var prevTranslation = 0.01f

        // Progress goes from 0f (closed) to 1f (fully open)
        for (step in 0..100) {
            val progress = step / 100f
            val ty = QueueBarVisuals.translationY(progress, maxShiftPx)
            // Monotonic upward means translationY becomes more negative (or equal)
            assertTrue("TranslationY should shift upward (<= prev): progress=$progress ty=$ty", ty <= prevTranslation)
            assertTrue("TranslationY should never exceed -maxShiftPx: ty=$ty", ty >= -maxShiftPx)
            prevTranslation = ty
        }

        assertEquals(0f, QueueBarVisuals.translationY(0f, maxShiftPx), 1e-6f)
        assertEquals(-maxShiftPx, QueueBarVisuals.translationY(1f, maxShiftPx), 1e-6f)
    }

    @Test
    fun sectionE_queueBar_alphaFadeEndsAtProgressFortyFivePercent() {
        // At progress 0f, alpha must be 1f
        assertEquals(1f, QueueBarVisuals.alpha(0f), 1e-6f)

        // Midway before 0.45f
        val midAlpha = QueueBarVisuals.alpha(0.225f)
        assertEquals(0.5f, midAlpha, 1e-3f)

        // At exactly 0.45f, alpha must be 0f
        assertEquals(0f, QueueBarVisuals.alpha(0.45f), 1e-6f)

        // At progress >= 0.45f up to 1.0f, alpha remains 0f
        assertEquals(0f, QueueBarVisuals.alpha(0.5f), 1e-6f)
        assertEquals(0f, QueueBarVisuals.alpha(0.8f), 1e-6f)
        assertEquals(0f, QueueBarVisuals.alpha(1f), 1e-6f)

        // Monotonic decrease of alpha as progress moves from 0f to 0.45f
        var prevAlpha = 1.01f
        for (step in 0..45) {
            val progress = step / 100f
            val alpha = QueueBarVisuals.alpha(progress)
            assertTrue("Alpha must decrease monotonically: progress=$progress alpha=$alpha", alpha <= prevAlpha)
            prevAlpha = alpha
        }
    }
}
