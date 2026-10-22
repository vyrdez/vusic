package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `centisecond and millisecond forms both parse to ms`() {
        val text = """
            [00:01.5]centi
            [00:02.500]milli
        """.trimIndent()
        val out = LrcParser.parse(text)
        assertEquals(2, out.lines.size)
        assertEquals(1500L, out.lines[0].startMs)
        assertEquals(2500L, out.lines[1].startMs)
    }

    @Test
    fun `multiple stamps on one line produce multiple timed lines`() {
        val text = "[00:10.00][00:30.00]Chorus line"
        val out = LrcParser.parse(text)
        assertEquals(2, out.lines.size)
        assertEquals(10000L, out.lines[0].startMs)
        assertEquals("Chorus line", out.lines[0].text)
        assertEquals(30000L, out.lines[1].startMs)
        assertEquals("Chorus line", out.lines[1].text)
    }

    @Test
    fun `offset tag with plus and minus shifts offsetMs`() {
        val textPlus = "[offset:+500]\n[00:01.00]line"
        val outPlus = LrcParser.parse(textPlus)
        assertEquals(500L, outPlus.offsetMs)

        val textMinus = "[offset:-200]\n[00:01.00]line"
        val outMinus = LrcParser.parse(textMinus)
        assertEquals(-200L, outMinus.offsetMs)
    }

    @Test
    fun `blank timed line becomes an instrumental gap`() {
        val text = "[00:05.00]\n[00:10.00]sung"
        val out = LrcParser.parse(text)
        assertEquals(2, out.lines.size)
        assertEquals("", out.lines[0].text)
        assertEquals(5000L, out.lines[0].startMs)
        assertEquals("sung", out.lines[1].text)
    }

    @Test
    fun `enhanced karaoke stamps produce words`() {
        val text = "[00:01.00]<00:01.00>hel<00:01.50>lo world"
        val out = LrcParser.parse(text)
        assertEquals(1, out.lines.size)
        assertEquals(2, out.lines[0].words.size)
        assertEquals("hel", out.lines[0].words[0].text)
        assertEquals("lo world", out.lines[0].words[1].text)
    }

    @Test
    fun `unsorted stamps are sorted by time`() {
        val text = "[00:20.00]c\n[00:00.00]a\n[00:10.00]b"
        val out = LrcParser.parse(text)
        assertEquals(listOf(0L, 10000L, 20000L), out.lines.map { it.startMs })
    }

    @Test
    fun `text with no stamps yields NONE`() {
        val out = LrcParser.parse("just plain text\nno timing here")
        assertTrue(out.lines.isEmpty())
        assertEquals(LyricsKind.NONE, out.kind)
    }

    @Test
    fun `headers are ignored`() {
        val text = "[ti:Title]\n[ar:Artist]\n[al:Album]\n[00:01.00]line"
        val out = LrcParser.parse(text)
        assertEquals(1, out.lines.size)
        assertEquals("line", out.lines[0].text)
    }

    @Test
    fun `malformed lines are skipped without crashing`() {
        val text = "[bad tag]\n[99:99.99]\n[00:05.00]valid"
        val out = LrcParser.parse(text)
        assertEquals(1, out.lines.size)
        assertEquals("valid", out.lines[0].text)
    }
}
