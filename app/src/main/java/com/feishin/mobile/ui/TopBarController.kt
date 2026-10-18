package com.feishin.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Inline search for the shared top app bar. While the controller holds one, the
 * bar shows this field in place of the title, so a list page can filter without
 * stealing a row from its own content (and without resizing the list under the
 * keyboard).
 *
 * The query lives here as well as in the screen's ViewModel because the bar is
 * what renders it: the screen mirrors its state in through [query] and receives
 * edits back through [onQuery].
 */
class TopBarSearch(
    hint: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    var hint by mutableStateOf(hint)
    var query by mutableStateOf("")
    var onQuery by mutableStateOf(onQuery)
    var onClose by mutableStateOf(onClose)
}

/**
 * The single trailing action a screen may place in the shared bar. An icon and
 * a label rather than a composable lambda, so the controller stays free of
 * composition and cannot leak a stale receiver.
 */
class TopBarAction(
    val icon: ImageVector,
    val label: String,
    val onSelect: () -> Unit,
)

/**
 * Lets a detail screen publish its name to the shared top app bar so the title
 * can fade in as the screen scrolls (collapsing header) without every screen
 * having to own its own app bar. It also carries the bar's optional trailing
 * action and its inline search field.
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

    /** Trailing action for the current screen (playlist pages put search here). */
    var action by mutableStateOf<TopBarAction?>(null)

    /** Inline search shown in place of the title, or null when not searching. */
    var search by mutableStateOf<TopBarSearch?>(null)

    fun reset() {
        title = null
        scrolled = false
        alwaysShowTitle = false
        hideBar = false
        action = null
        search = null
    }
}

val LocalTopBarController = staticCompositionLocalOf { TopBarController() }
