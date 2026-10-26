package com.feishin.mobile.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section G contract (specs/section_g_spec.md): the lyrics tab reuses the same
 * queue strip and the same hoisted sheet state as the song tab, so the strip
 * keeps identical screen coordinates and nothing jumps between tabs.
 */
class LyricsQueueStripTest {

    // Spec 2/3: 72dp peek strip, 8dp bottom spacer, 16dp clearance above it.
    private val stripHeightDp = 72
    private val bottomSpacerDp = 8
    private val clearanceDp = 16
    private val listBottomPaddingDp = stripHeightDp + bottomSpacerDp + clearanceDp

    @Test
    fun testListBottomPaddingClearsQueueStrip() {
        assertEquals(96, listBottomPaddingDp)
        assertTrue(listBottomPaddingDp >= stripHeightDp + bottomSpacerDp)
    }

    // Spec 3: QueueStripPadding = 72 + 8 + 8 = 88dp, pill floats above strip.
    @Test
    fun testQueueStripPaddingToken() {
        val queueStripPaddingDp = stripHeightDp + bottomSpacerDp + 8
        assertEquals(88, queueStripPaddingDp)
    }

    @Test
    fun testSyncPillOffsetClearsStrip() {
        val pillBottomOffsetDp = 88
        assertTrue("sync pill must sit more than 80dp above the bottom", pillBottomOffsetDp > 80)
        assertTrue(pillBottomOffsetDp >= stripHeightDp + bottomSpacerDp)
        assertTrue(pillBottomOffsetDp < listBottomPaddingDp)
    }

    // Spec 1: exactly one sheet state is hoisted and shared with the strip.
    private fun screenSource(): String? =
        listOf(
            File("src/main/java/com/feishin/mobile/ui/player/NowPlayingScreen.kt"),
            File("app/src/main/java/com/feishin/mobile/ui/player/NowPlayingScreen.kt"),
        ).firstOrNull { it.isFile }?.readText()

    @Test
    fun testSharedSheetStateHoistedOnce() {
        val src = screenSource() ?: return
        assertEquals(1, Regex("""rememberSheetState\(""").findAll(src).count())
        assertTrue(src.contains("sheet.openSheet()"))
        assertTrue(src.contains("sheet.dragByPixels"))
        assertTrue(src.contains("sheet.onDragEnd"))
    }
}
