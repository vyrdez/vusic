package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDuplicateCheckTest {

    private fun track(id: String, title: String) = Track(
        id = id,
        title = title,
        album = "Album",
        albumId = "al",
        artist = "Artist",
        artistId = "ar",
        coverArt = "c",
        durationSec = 200,
        trackNo = 1,
        year = 2024,
        genre = "Music",
        suffix = "flac",
        starred = false,
    )

    @Test
    fun queueTwoSongsSavedToPlaylistWithOneDuplicate_addsOnlyNewSongWithoutDialog() {
        // Queue: ["hymn", "yellow"]
        // test 3: ["hymn"]
        val candidates = listOf(track("hymn", "Hymn for the Weekend"), track("yellow", "Yellow"))
        val existingIds = setOf("hymn")

        val res = PlaylistAddPlanner.plan(candidates, "test 3", existingIds)

        assertTrue(res is PlaylistAddResolution.AddFilteredImmediately)
        val filtered = res as PlaylistAddResolution.AddFilteredImmediately
        assertEquals(listOf("yellow"), filtered.songsToAdd.map { it.id })
        assertEquals(1, filtered.skippedCount)
        assertEquals("Added 1 songs to test 3 (1 already there)", filtered.toastMessage)
    }

    @Test
    fun playlistLargeSubsetSavedToExistingPlaylist_addsOnlyMissingSongs() {
        // test 2 (569 songs) saved into test (516 songs) where 516 overlap and 53 are new
        val overlapping = (1..516).map { track("song_$it", "Song $it") }
        val newSongsList = (517..569).map { track("song_$it", "Song $it") }
        val candidates = overlapping + newSongsList
        val existingIds = overlapping.map { it.id }.toSet()

        val res = PlaylistAddPlanner.plan(candidates, "test", existingIds)

        assertTrue(res is PlaylistAddResolution.AddFilteredImmediately)
        val filtered = res as PlaylistAddResolution.AddFilteredImmediately
        assertEquals(53, filtered.songsToAdd.size)
        assertEquals(516, filtered.skippedCount)
        assertEquals("Added 53 songs to test (516 already there)", filtered.toastMessage)
    }

    @Test
    fun zeroOverlap_addsAllImmediatelyWithoutDialog() {
        val candidates = listOf(track("a", "Song A"), track("b", "Song B"))
        val existingIds = setOf("c", "d")

        val res = PlaylistAddPlanner.plan(candidates, "My Playlist", existingIds)

        assertTrue(res is PlaylistAddResolution.AddImmediately)
        val added = res as PlaylistAddResolution.AddImmediately
        assertEquals(listOf("a", "b"), added.songsToAdd.map { it.id })
        assertEquals("Added 2 songs to My Playlist", added.toastMessage)
    }

    @Test
    fun fullOverlapMultipleSongs_triggersAlreadyInPlaylistDialog() {
        val candidates = listOf(track("hymn", "Hymn for the Weekend"), track("yellow", "Yellow"))
        val existingIds = setOf("hymn", "yellow", "clocks")

        val res = PlaylistAddPlanner.plan(candidates, "test 3", existingIds)

        assertTrue(res is PlaylistAddResolution.RequireConfirmation)
        val conf = res as PlaylistAddResolution.RequireConfirmation
        assertEquals(2, conf.songsToAdd.size)
        assertEquals("All 2 songs are already in \"test 3\". Add anyway?", conf.dialogMessage)
    }

    @Test
    fun singleSongAlreadyInPlaylist_triggersAlreadyInPlaylistDialogWithSongTitle() {
        val candidates = listOf(track("hymn", "Hymn for the Weekend"))
        val existingIds = setOf("hymn")

        val res = PlaylistAddPlanner.plan(candidates, "test 3", existingIds)

        assertTrue(res is PlaylistAddResolution.RequireConfirmation)
        val conf = res as PlaylistAddResolution.RequireConfirmation
        assertEquals(1, conf.songsToAdd.size)
        assertEquals("\"Hymn for the Weekend\" is already in \"test 3\". Add anyway?", conf.dialogMessage)
    }

    @Test
    fun singleSongNotInPlaylist_addsImmediatelyWithoutDialog() {
        val candidates = listOf(track("yellow", "Yellow"))
        val existingIds = setOf("hymn")

        val res = PlaylistAddPlanner.plan(candidates, "test 3", existingIds)

        assertTrue(res is PlaylistAddResolution.AddImmediately)
        val added = res as PlaylistAddResolution.AddImmediately
        assertEquals(listOf("yellow"), added.songsToAdd.map { it.id })
        assertEquals("Added to test 3", added.toastMessage)
    }
}
