package com.feishin.mobile.widget

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part C logic: widget state mapping and layout choice. */
class WidgetStateTest {

    private fun track(id: String, title: String, starred: Boolean = false) =
        Track(id, title, "Al", "al", "Ar", "ar", "cover1", 180, 1, 2024, "Music", "mp3", starred)

    @Test
    fun emptyQueue_mapsToEmptyState() {
        val s = WidgetStateMapper.map(
            queueEmpty = true, current = null, isPlaying = false,
            favLoaded = true, favTrackIds = emptySet(),
            persistedTrackId = "t1", persistedStarred = true,
        )
        assertFalse(s.hasQueue)
        assertEquals("Nothing playing", s.title)
        assertFalse(s.isPlaying)
        assertFalse(s.starred)
    }

    @Test
    fun currentTrack_mapsTitleArtistCoverAndPlayState() {
        val s = WidgetStateMapper.map(
            queueEmpty = false, current = track("t1", "Magnet"), isPlaying = true,
            favLoaded = true, favTrackIds = emptySet(),
            persistedTrackId = null, persistedStarred = false,
        )
        assertTrue(s.hasQueue)
        assertEquals("Magnet", s.title)
        assertEquals("Ar", s.artist)
        assertEquals("cover1", s.coverArtId)
        assertEquals("t1", s.trackId)
        assertTrue(s.isPlaying)
        assertFalse(s.starred)
    }

    @Test
    fun favoritesCollection_winsOverTrackFlag() {
        // Server says starred even though the queued copy is stale-unstarred.
        val s = WidgetStateMapper.map(
            queueEmpty = false, current = track("t1", "T", starred = false), isPlaying = false,
            favLoaded = true, favTrackIds = setOf("t1"),
            persistedTrackId = "t1", persistedStarred = false,
        )
        assertTrue(s.starred)
        // And the reverse: removed on the server clears a stale track flag.
        val s2 = WidgetStateMapper.map(
            queueEmpty = false, current = track("t1", "T", starred = true), isPlaying = false,
            favLoaded = true, favTrackIds = emptySet(),
            persistedTrackId = null, persistedStarred = false,
        )
        assertFalse(s2.starred)
    }

    @Test
    fun beforeFavoritesLoad_trackFlagAndPersistedStateApply() {
        // Track's own flag wins when the collection hasn't loaded.
        val s = WidgetStateMapper.map(
            queueEmpty = false, current = track("t1", "T", starred = true), isPlaying = false,
            favLoaded = false, favTrackIds = emptySet(),
            persistedTrackId = null, persistedStarred = false,
        )
        assertTrue(s.starred)
        // Service-alive/app-dead path: persisted widget heart for the same track.
        val s2 = WidgetStateMapper.map(
            queueEmpty = false, current = track("t1", "T", starred = false), isPlaying = true,
            favLoaded = false, favTrackIds = emptySet(),
            persistedTrackId = "t1", persistedStarred = true,
        )
        assertTrue(s2.starred)
        // A different track does not inherit the old heart.
        val s3 = WidgetStateMapper.map(
            queueEmpty = false, current = track("t2", "T2", starred = false), isPlaying = true,
            favLoaded = false, favTrackIds = emptySet(),
            persistedTrackId = "t1", persistedStarred = true,
        )
        assertFalse(s3.starred)
    }

    @Test
    fun layoutPicker_largeAt260AndUp() {
        assertEquals(WidgetLayout.COMPACT, WidgetLayoutPicker.forMinWidthDp(0))
        assertEquals(WidgetLayout.COMPACT, WidgetLayoutPicker.forMinWidthDp(180))
        assertEquals(WidgetLayout.COMPACT, WidgetLayoutPicker.forMinWidthDp(259))
        assertEquals(WidgetLayout.LARGE, WidgetLayoutPicker.forMinWidthDp(260))
        assertEquals(WidgetLayout.LARGE, WidgetLayoutPicker.forMinWidthDp(640))
    }
}
