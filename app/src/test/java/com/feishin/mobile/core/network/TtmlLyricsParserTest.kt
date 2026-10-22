package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlLyricsParserTest {

    @Test
    fun `parseTimestampMs handles multiple formats`() {
        assertEquals(33642L, TtmlLyricsParser.parseTimestampMs("33.642"))
        assertEquals(33642L, TtmlLyricsParser.parseTimestampMs("33.642s"))
        assertEquals(64304L, TtmlLyricsParser.parseTimestampMs("1:04.304"))
        assertEquals(64304L, TtmlLyricsParser.parseTimestampMs("01:04.304"))
        assertEquals(3664304L, TtmlLyricsParser.parseTimestampMs("01:01:04.304"))
        assertEquals(500L, TtmlLyricsParser.parseTimestampMs("500ms"))
    }

    @Test
    fun `parse extracts lines with word spans and agents`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="33.642" end="35.909" ttm:agent="v1">
                    <span begin="33.642" end="33.958">Look</span>
                    <span begin="33.958" end="34.278">at</span>
                    <span begin="34.278" end="34.577">the</span>
                    <span begin="34.577" end="35.909">stars</span>
                  </p>
                  <p begin="36.381" end="40.029" ttm:agent="v2">
                    <span begin="36.381" end="37.000">Look</span>
                    <span begin="37.000" end="38.000">how</span>
                    <span begin="38.000" end="40.029">they shine</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = TtmlLyricsParser.parse(ttml, LyricsSource.BETTER_LYRICS)
        assertEquals(LyricsKind.SYNCED, lyrics.kind)
        assertEquals(LyricsSource.BETTER_LYRICS, lyrics.source)
        assertEquals(2, lyrics.lines.size)

        val line1 = lyrics.lines[0]
        assertEquals(33642L, line1.startMs)
        assertEquals("v1", line1.agent)
        assertEquals(4, line1.words.size)
        assertEquals("Look at the stars", line1.text)
        assertEquals(33642L, line1.words[0].startMs)
        assertEquals("Look", line1.words[0].text)
        assertEquals("stars", line1.words[3].text)

        val line2 = lyrics.lines[1]
        assertEquals(36381L, line2.startMs)
        assertEquals("v2", line2.agent)
        assertEquals("Look how they shine", line2.text)
        assertEquals(3, line2.words.size)
    }

    @Test
    fun `parse line without spans falls back to paragraph text`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="10.500" end="15.000">Simple unsyllable line</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lyrics = TtmlLyricsParser.parse(ttml)
        assertEquals(1, lyrics.lines.size)
        assertEquals(10500L, lyrics.lines[0].startMs)
        assertEquals("Simple unsyllable line", lyrics.lines[0].text)
        assertTrue(lyrics.lines[0].words.isEmpty())
    }
}
