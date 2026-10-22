package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLookupTest {

    @Test
    fun `lineIndexAt binary search basic`() {
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(0, "a"),
                LyricLine(10000, "b"),
                LyricLine(20000, "c"),
                LyricLine(30000, "d"),
            ),
        )
        assertEquals(-1, lyrics.lineIndexAt(-1))
        assertEquals(0, lyrics.lineIndexAt(0))
        assertEquals(0, lyrics.lineIndexAt(9999))
        assertEquals(1, lyrics.lineIndexAt(10000))
        assertEquals(2, lyrics.lineIndexAt(25000))
        assertEquals(3, lyrics.lineIndexAt(30000))
        assertEquals(3, lyrics.lineIndexAt(999999))
    }

    @Test
    fun `lineIndexAt applies offset`() {
        val lyrics = Lyrics(
            lines = listOf(LyricLine(0, "a"), LyricLine(10000, "b")),
            offsetMs = 5000,
        )
        assertEquals(-1, lyrics.lineIndexAt(4999))
        assertEquals(0, lyrics.lineIndexAt(5000))
        assertEquals(1, lyrics.lineIndexAt(15000))
    }

    @Test
    fun `lineIndexAt empty is minus one`() {
        assertEquals(-1, Lyrics(emptyList()).lineIndexAt(100000))
    }

    @Test
    fun `wordIndexAt returns active word`() {
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(
                    0, "hello world",
                    words = listOf(LyricWord(0, "hello"), LyricWord(500, "world")),
                ),
            ),
        )
        assertEquals(-1, lyrics.wordIndexAt(0, -1))
        assertEquals(0, lyrics.wordIndexAt(0, 0))
        assertEquals(0, lyrics.wordIndexAt(0, 499))
        assertEquals(1, lyrics.wordIndexAt(0, 500))
        assertEquals(1, lyrics.wordIndexAt(0, 9999))
    }

    @Test
    fun `wordIndexAt without words is minus one`() {
        val lyrics = Lyrics(lines = listOf(LyricLine(0, "a")))
        assertEquals(-1, lyrics.wordIndexAt(0, 100))
    }
}
