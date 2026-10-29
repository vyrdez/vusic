package com.feishin.mobile.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section D: Block touch bleed through Now Playing to search bar.
 * Pure helpers and unit tests for focus clearing, keyboard hiding,
 * and pointer input consumption.
 */
class NowPlayingTouchTest {

    /**
     * Pure helper to evaluate whether Now Playing expansion state warrants input dismissal.
     */
    fun shouldDismissInputOnNowPlaying(expanded: Boolean): Boolean {
        return NowPlayingTouchHelper.shouldDismissInputOnExpand(expanded)
    }

    /**
     * Pure helper to simulate Now Playing expansion triggering focus clear and keyboard hide.
     */
    fun simulateNowPlayingExpand(
        expanded: Boolean,
        onClearFocus: () -> Unit,
        onHideKeyboard: () -> Unit,
    ): Boolean {
        return NowPlayingTouchHelper.onNowPlayingExpand(
            expanded = expanded,
            clearFocus = onClearFocus,
            hideKeyboard = onHideKeyboard,
        )
    }

    /**
     * Pure helper to simulate composition entry triggering focus clear and keyboard hide.
     */
    fun simulateCompositionEntry(
        onClearFocus: () -> Unit,
        onHideKeyboard: () -> Unit,
    ) {
        NowPlayingTouchHelper.onCompositionEntry(
            clearFocus = onClearFocus,
            hideKeyboard = onHideKeyboard,
        )
    }

    /**
     * Pure helper representing touch bleed blocking decision.
     * Always returns true because Now Playing root and QueueSheet container consume 100% of bounds.
     */
    fun shouldBlockTouchBleed(isOverlayActive: Boolean): Boolean {
        return isOverlayActive && NowPlayingTouchHelper.consumesPointerInput()
    }

    @Test
    fun testNowPlayingExpandDismissesInput() {
        var focusCleared = false
        var keyboardHidden = false

        val handled = simulateNowPlayingExpand(
            expanded = true,
            onClearFocus = { focusCleared = true },
            onHideKeyboard = { keyboardHidden = true },
        )

        assertTrue(handled)
        assertTrue("Focus must be cleared when Now Playing expands", focusCleared)
        assertTrue("Keyboard must be hidden when Now Playing expands", keyboardHidden)
    }

    @Test
    fun testNowPlayingCollapseDoesNotDismissInput() {
        var focusCleared = false
        var keyboardHidden = false

        val handled = simulateNowPlayingExpand(
            expanded = false,
            onClearFocus = { focusCleared = true },
            onHideKeyboard = { keyboardHidden = true },
        )

        assertFalse(handled)
        assertFalse("Focus should not be cleared when not expanded", focusCleared)
        assertFalse("Keyboard should not be hidden when not expanded", keyboardHidden)
    }

    @Test
    fun testCompositionEntryDismissesInput() {
        var focusCleared = false
        var keyboardHidden = false

        simulateCompositionEntry(
            onClearFocus = { focusCleared = true },
            onHideKeyboard = { keyboardHidden = true },
        )

        assertTrue("Focus must be cleared on composition entry", focusCleared)
        assertTrue("Keyboard must be hidden on composition entry", keyboardHidden)
    }

    @Test
    fun testTouchBleedBlockedWhenOverlayActive() {
        assertTrue("Touch bleed must be blocked when overlay is active", shouldBlockTouchBleed(true))
        assertFalse("No touch block when overlay is inactive", shouldBlockTouchBleed(false))
    }

    private fun readSourceFile(path: String): String {
        val candidates = listOf(
            File(path),
            File("app/$path"),
            File("../$path"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw java.io.FileNotFoundException("Could not find $path in candidates: $candidates")
        return file.readText()
    }

    @Test
    fun testNavKtHandlesNowPlayingExpansion() {
        val navSource = readSourceFile("src/main/java/com/feishin/mobile/ui/Nav.kt")
        assertTrue(
            "Nav.kt must clear focus when showNowPlaying is true",
            navSource.contains("focusManager.clearFocus()"),
        )
        assertTrue(
            "Nav.kt must hide keyboard when showNowPlaying is true",
            navSource.contains("keyboardController?.hide()"),
        )
        assertTrue(
            "Nav.kt must observe showNowPlaying",
            navSource.contains("LaunchedEffect(showNowPlaying)"),
        )
    }

    @Test
    fun testNowPlayingScreenDismissesInputOnCompositionEntry() {
        val npSource = readSourceFile("src/main/java/com/feishin/mobile/ui/player/NowPlayingScreen.kt")
        assertTrue(
            "NowPlayingScreen must clear focus on composition entry",
            npSource.contains("focusManager.clearFocus()"),
        )
        assertTrue(
            "NowPlayingScreen must hide keyboard on composition entry",
            npSource.contains("keyboard?.hide()"),
        )
        assertTrue(
            "NowPlayingScreen must have LaunchedEffect(Unit) for composition entry",
            npSource.contains("LaunchedEffect(Unit)"),
        )
    }

    @Test
    fun testNowPlayingScreenConsumesPointerInputOnRootAndQueueSheet() {
        val npSource = readSourceFile("src/main/java/com/feishin/mobile/ui/player/NowPlayingScreen.kt")
        val pointerConsumePattern = "pointerInput(Unit) { detectTapGestures { /* consume */ } }"
        val occurrences = npSource.split(pointerConsumePattern).size - 1
        assertTrue(
            "NowPlayingScreen must consume pointer input on both root Box and QueueSheet container (found $occurrences)",
            occurrences >= 2,
        )
    }
}
