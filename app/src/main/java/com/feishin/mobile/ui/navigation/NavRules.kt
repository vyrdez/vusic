package com.feishin.mobile.ui.navigation

import com.feishin.mobile.ui.Routes

/**
 * Pure navigation rules, extracted so the tab/back-stack behaviour is testable
 * without a live NavController.
 */
object NavRules {

    /** Routes that get the shared back-arrow top app bar. */
    val DETAIL_ROUTES = setOf(
        Routes.ALBUM, Routes.ARTIST, Routes.PLAYLIST, Routes.GENRE, Routes.GENRES, Routes.DOWNLOADS,
    )

    fun isDetail(route: String?): Boolean = route in DETAIL_ROUTES

    /** Bottom-nav labels in order. */
    val BOTTOM_NAV_LABELS = listOf("Home", "Library", "Search", "Settings")

    /**
     * The Home avatar and the bottom nav both resolve to the Settings *tab*, so
     * they must produce the identical route. The old code pushed a second
     * Settings destination outside the tab back stack, so Home stopped working
     * until back was pressed.
     */
    fun avatarTarget(): String = bottomNavTarget("Settings")

    fun bottomNavTarget(label: String): String = when (label) {
        "Home" -> Routes.HOME
        "Library" -> Routes.library()
        "Search" -> Routes.SEARCH
        else -> Routes.SETTINGS
    }

    /** Which bottom-nav item should look selected for [route]. */
    fun selectedLabel(route: String?): String? = when {
        route == Routes.HOME || route == null -> "Home"
        route == Routes.LIBRARY_PATTERN -> "Library"
        route == Routes.SEARCH -> "Search"
        route == Routes.SETTINGS -> "Settings"
        else -> null
    }

    /**
     * Switching Library tabs must replace the existing Library entry: restored
     * state would otherwise keep the previously-saved tab argument.
     */
    fun libraryNeedsReplace(currentRoute: String?): Boolean = currentRoute == Routes.LIBRARY_PATTERN

    /** The mini player and nav bar are hidden only by these overlays. */
    fun overlaysCoverBottomBar(showNowPlaying: Boolean): Boolean = showNowPlaying
}
