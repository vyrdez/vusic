package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part B logic: Auto browse-tree structure, paging, voice mapping. */
class AutoBrowseTest {

    private fun track(id: String, title: String) =
        Track(id, title, "Al", "al", "Ar", "ar", null, 180, 1, 2024, "Music", "mp3", false)

    @Test
    fun rootTabs_areExactlyFour() {
        val tabs = AutoBrowse.rootTabs()
        assertEquals(4, tabs.size)
        assertEquals(
            listOf(AutoBrowse.TAB_HOME, AutoBrowse.TAB_LIBRARY, AutoBrowse.TAB_FAVORITES, AutoBrowse.TAB_DOWNLOADS),
            tabs.map { it.id },
        )
        assertTrue(tabs.all { it.browsable && !it.playable })
    }

    @Test
    fun homeChildren_coverRecentNewestRandomRadio() {
        val ids = AutoBrowse.homeChildren().map { it.id }
        assertEquals(
            listOf(AutoBrowse.HOME_RECENT, AutoBrowse.HOME_NEWEST, AutoBrowse.HOME_RANDOM, AutoBrowse.HOME_RADIO),
            ids,
        )
    }

    @Test
    fun libraryChildren_coverPlaylistsAlbumsArtists() {
        assertEquals(
            listOf(AutoBrowse.LIB_PLAYLISTS, AutoBrowse.LIB_ALBUMS, AutoBrowse.LIB_ARTISTS),
            AutoBrowse.libraryChildren().map { it.id },
        )
    }

    @Test
    fun collectionNode_roundTripsKindAndId() {
        val node = AutoBrowse.collectionNode("pl", "abc123")
        assertEquals("pl" to "abc123", AutoBrowse.collectionRefOf(node))
        assertNull(AutoBrowse.collectionRefOf(AutoBrowse.TAB_HOME))
        assertNull(AutoBrowse.collectionRefOf("col_"))
        assertNull(AutoBrowse.collectionRefOf("col_noseparator"))
    }

    @Test
    fun page_slicesLazily() {
        val list = (0 until 120).toList()
        assertEquals((0 until 50).toList(), AutoBrowse.page(list, 0, 50))
        assertEquals((50 until 100).toList(), AutoBrowse.page(list, 1, 50))
        assertEquals((100 until 120).toList(), AutoBrowse.page(list, 2, 50))
        assertTrue(AutoBrowse.page(list, 3, 50).isEmpty())
        assertTrue(AutoBrowse.page(list, -1, 50).isEmpty())
        assertTrue(AutoBrowse.page(list, 0, 0).isEmpty())
    }

    @Test
    fun normalizeVoiceQuery_stripsPlayPrefix() {
        assertEquals("Rokudenashi", AutoBrowse.normalizeVoiceQuery("play Rokudenashi"))
        assertEquals("Rokudenashi", AutoBrowse.normalizeVoiceQuery("  Play  Rokudenashi  "))
        assertEquals("lofi beats", AutoBrowse.normalizeVoiceQuery("listen to lofi beats"))
        assertEquals("play", AutoBrowse.normalizeVoiceQuery("play"))
        assertEquals("奏", AutoBrowse.normalizeVoiceQuery("奏"))
    }

    @Test
    fun voiceTracks_prefersExactTitleMatch() {
        val results = SearchResults(
            artists = emptyList(),
            albums = emptyList(),
            tracks = listOf(
                track("1", "Magnet intro"),
                track("2", "magnet"),
                track("3", "Magneto"),
            ),
            playlists = emptyList(),
        )
        val picked = AutoBrowse.voiceTracks(results, "magnet")
        assertEquals(listOf("2", "1", "3"), picked.map { it.id })
    }

    @Test
    fun voiceTracks_capsForTheCar() {
        val results = SearchResults(
            emptyList(), emptyList(),
            (0 until 100).map { track("$it", "Song $it") },
            emptyList(),
        )
        assertEquals(50, AutoBrowse.voiceTracks(results, "song").size)
        assertTrue(AutoBrowse.voiceTracks(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()), "x").isEmpty())
    }
}
