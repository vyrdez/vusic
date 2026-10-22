package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KrcLyricsParserTest {

    @Test
    fun `parseText parses timed lines and words`() {
        val sampleKrcText = """
            [id:${'$'}00000000]
            [ar:Coldplay]
            [ti:Yellow]
            [offset:250]
            [33580,2120]<0,290,0>Look <290,290,0>at <580,290,0>the <870,1250,0>stars
            [36300,3909]<0,300,0>Look <300,319,0>how <619,360,0>they <979,650,0>shine
        """.trimIndent()

        val lyrics = KrcLyricsParser.parseText(sampleKrcText, LyricsSource.KUGOU)

        assertEquals(LyricsKind.SYNCED, lyrics.kind)
        assertEquals(LyricsSource.KUGOU, lyrics.source)
        assertEquals(250L, lyrics.offsetMs)
        assertEquals(2, lyrics.lines.size)

        val line1 = lyrics.lines[0]
        assertEquals(33580L, line1.startMs)
        assertEquals("Look at the stars", line1.text)
        assertEquals(4, line1.words.size)
        assertEquals(33580L, line1.words[0].startMs)
        assertEquals("Look ", line1.words[0].text)
        assertEquals(33580L + 290L, line1.words[1].startMs)
        assertEquals("at ", line1.words[1].text)
        assertEquals(33580L + 580L, line1.words[2].startMs)
        assertEquals("the ", line1.words[2].text)
        assertEquals(33580L + 870L, line1.words[3].startMs)
        assertEquals("stars", line1.words[3].text)
    }

    @Test
    fun `decryptKrc roundtrip`() {
        val plain = "krc1hello world test string"
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val decrypted = KrcLyricsParser.decryptKrc(bytes)
        assertTrue(decrypted != null)
        assertTrue(decrypted!!.isNotEmpty())
    }
}
