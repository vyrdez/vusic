package com.feishin.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a detail screen publish its name to the shared top app bar so the title
 * can fade in as the screen scrolls (collapsing header) without every screen
 * having to own its own app bar.
 */
class TopBarController {
    /** Screen name, or null for top-level screens. */
    var title by mutableStateOf<String?>(null)

    /** True once the screen has scrolled away from the top. */
    var scrolled by mutableStateOf(false)

    /** Simple list screens (genres, downloads) show their title immediately. */
    var alwaysShowTitle by mutableStateOf(false)

    /**
     * True while a screen draws its own edge-to-edge hero and therefore hides
     * the shared top bar (the screen then provides its own back button and
     * collapsing title).
     */
    var hideBar by mutableStateOf(false)

    fun reset() {
        title = null
        scrolled = false
        alwaysShowTitle = false
        hideBar = false
    }
}

val LocalTopBarController = staticCompositionLocalOf { TopBarController() }
