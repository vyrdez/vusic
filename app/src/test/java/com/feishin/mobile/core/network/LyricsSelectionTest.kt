package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSelectionTest {

    @Test
    fun `synced beats plain`() {
        val synced = Lyrics(listOf(LyricLine(0, "s")), kind = LyricsKind.SYNCED)
        val plain = Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = "p")
        assertEquals(synced, LyricsSelection.choose(listOf(plain, synced)))
    }

    @Test
    fun `plain beats none`() {
        val plain = Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = "p")
        val none = Lyrics(emptyList(), 0, LyricsKind.NONE)
        assertEquals(plain, LyricsSelection.choose(listOf(none, plain)))
    }

    @Test
    fun `empty candidates yields NONE`() {
        val out = LyricsSelection.choose(emptyList())
        assertTrue(out.isNone)
    }

    @Test
    fun `language preference matches device language`() {
        val enSynced = Lyrics(listOf(LyricLine(0, "en")), kind = LyricsKind.SYNCED, lang = "en")
        val jaSynced = Lyrics(listOf(LyricLine(0, "ja")), kind = LyricsKind.SYNCED, lang = "ja")
        assertEquals(jaSynced, LyricsSelection.choose(listOf(enSynced, jaSynced), "ja"))
    }
}
