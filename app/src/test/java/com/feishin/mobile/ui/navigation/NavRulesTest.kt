package com.feishin.mobile.ui.navigation

import com.feishin.mobile.ui.Routes
import com.feishin.mobile.ui.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRulesTest {

    @Test
    fun avatarUsesTheSettingsTab_soHomeAlwaysWorks() {
        // Root cause guard: the avatar must land on the same target as the nav bar.
        assertEquals(NavRules.bottomNavTarget("Settings"), NavRules.avatarTarget())
        assertEquals(Routes.SETTINGS, NavRules.avatarTarget())
    }

    @Test
    fun bottomNavTargetsAreTopLevel() {
        assertEquals(Routes.HOME, NavRules.bottomNavTarget("Home"))
        assertEquals("library?tab=ALBUMS", NavRules.bottomNavTarget("Library"))
        assertEquals(Routes.SEARCH, NavRules.bottomNavTarget("Search"))
        assertEquals(Routes.SETTINGS, NavRules.bottomNavTarget("Settings"))
        // Every label must resolve to a real route, never to another tab's.
        val targets = NavRules.BOTTOM_NAV_LABELS.map(NavRules::bottomNavTarget)
        assertEquals(targets.size, targets.distinct().size)
    }

    @Test
    fun selectedLabel_matchesEachTopLevelRoute() {
        assertEquals("Home", NavRules.selectedLabel(Routes.HOME))
        assertEquals("Home", NavRules.selectedLabel(null))
        assertEquals("Library", NavRules.selectedLabel(Routes.LIBRARY_PATTERN))
        assertEquals("Search", NavRules.selectedLabel(Routes.SEARCH))
        assertEquals("Settings", NavRules.selectedLabel(Routes.SETTINGS))
        // Detail routes belong to no tab.
        assertEquals(null, NavRules.selectedLabel(Routes.ALBUM))
    }

    @Test
    fun detailRoutes_requireBackAffordance() {
        assertTrue(NavRules.isDetail(Routes.ALBUM))
        assertTrue(NavRules.isDetail(Routes.ARTIST))
        assertTrue(NavRules.isDetail(Routes.PLAYLIST))
        assertTrue(NavRules.isDetail(Routes.GENRE))
        assertTrue(NavRules.isDetail(Routes.GENRES))
        assertTrue(NavRules.isDetail(Routes.DOWNLOADS))
        assertFalse(NavRules.isDetail(Routes.HOME))
        assertFalse(NavRules.isDetail(Routes.LIBRARY_PATTERN))
        assertFalse(NavRules.isDetail(Routes.SEARCH))
        assertFalse(NavRules.isDetail(Routes.SETTINGS))
    }

    @Test
    fun libraryTabSwitch_replacesTheEntry() {
        assertTrue(NavRules.libraryNeedsReplace(Routes.LIBRARY_PATTERN))
        // From anywhere else we simply navigate.
        assertFalse(NavRules.libraryNeedsReplace(Routes.HOME))
        assertFalse(NavRules.libraryNeedsReplace(null))
    }

    @Test
    fun libraryRoutes_encodeEveryTab() {
        LibraryTab.entries.forEach { tab ->
            assertEquals("library?tab=${tab.name}", Routes.library(tab))
        }
        // Default is Albums, matching the bottom-nav Library target.
        assertEquals(Routes.library(LibraryTab.ALBUMS), NavRules.bottomNavTarget("Library"))
    }

    @Test
    fun onlyNowPlayingCoversTheBottomBar() {
        assertTrue(NavRules.overlaysCoverBottomBar(showNowPlaying = true))
        assertFalse(NavRules.overlaysCoverBottomBar(showNowPlaying = false))
    }

    @Test
    fun miniPlayer_hiddenWhenQueueEmpty() {
        assertFalse(NavRules.shouldShowMiniPlayer(queueEmpty = true))
        assertTrue(NavRules.shouldShowMiniPlayer(queueEmpty = false))
    }

    @Test
    fun nowPlaying_dismissedWhenNothingPlaying() {
        // Clearing the queue must close the overlay, never show a placeholder.
        assertFalse(NavRules.shouldShowNowPlaying(requested = true, currentTrackNull = true))
        assertFalse(NavRules.shouldShowNowPlaying(requested = false, currentTrackNull = true))
        assertFalse(NavRules.shouldShowNowPlaying(requested = false, currentTrackNull = false))
        assertTrue(NavRules.shouldShowNowPlaying(requested = true, currentTrackNull = false))
    }
}
