package com.feishin.mobile.core.network

import com.feishin.mobile.data.model.SubsonicEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedLyricsTest {

    @Test
    fun parse_ordersAndCleansLines() {
        val parsed = SyncedLyrics.parse(
            listOf(
                30_000L to "third",
                0L to "  first  ",
                10_000L to "second",
                20_000L to "   ",
            ),
        )
        assertEquals(3, parsed.lines.size)
        assertEquals("first", parsed.lines[0].text)
        assertEquals(0L, parsed.lines[0].startMs)
        assertEquals("second", parsed.lines[1].text)
        assertEquals("third", parsed.lines[2].text)
    }

    @Test
    fun parse_emptyInput_isEmpty() {
        assertTrue(SyncedLyrics.parse(emptyList()).isEmpty)
        assertTrue(SyncedLyrics.parse(listOf(0L to "  ")).isEmpty)
    }

    @Test
    fun plain_singleBlock() {
        val p = SyncedLyrics.plain("hello\nworld")
        assertEquals(1, p.lines.size)
        assertEquals(0L, p.lines[0].startMs)
        assertEquals("hello\nworld", p.lines[0].text)
        assertTrue(SyncedLyrics.plain("  ").isEmpty)
    }

    @Test
    fun lineIndexAt_binarySearch() {
        val lyrics = SyncedLyrics.parse(
            listOf(0L to "a", 10_000L to "b", 20_000L to "c", 30_000L to "d"),
        )
        assertEquals(-1, lyrics.lineIndexAt(-1))
        assertEquals(0, lyrics.lineIndexAt(0))
        assertEquals(0, lyrics.lineIndexAt(9_999))
        assertEquals(1, lyrics.lineIndexAt(10_000))
        assertEquals(2, lyrics.lineIndexAt(25_000))
        assertEquals(3, lyrics.lineIndexAt(30_000))
        assertEquals(3, lyrics.lineIndexAt(999_999))
    }

    @Test
    fun lineIndexAt_respectsOffset() {
        val lyrics = SyncedLyrics.parse(listOf(0L to "a", 10_000L to "b"), offsetMs = 5_000)
        assertEquals(-1, lyrics.lineIndexAt(4_999))
        assertEquals(0, lyrics.lineIndexAt(5_000))
        assertEquals(1, lyrics.lineIndexAt(15_000))
    }

    @Test
    fun lineIndexAt_empty_isMinusOne() {
        assertEquals(-1, SyncedLyrics.EMPTY.lineIndexAt(100_000))
    }

    @Test
    fun parsesRealOpenSubsonicPayload() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "lyricsList":{"syncedLyrics":{"offset":0,"line":[
            {"start":1200,"value":"first line"},
            {"start":5400,"value":"second line"}]}}}}"""
        val r = SubsonicAuth.json.decodeFromString(SubsonicEnvelope.serializer(), body).response
        val synced = r.lyricsList?.syncedLyrics
        assertEquals(2, synced?.line?.size)
        val parsed = SyncedLyrics.parse(synced!!.line.map { it.start to it.value }, 0)
        assertEquals(1, parsed.lineIndexAt(6_000))
    }

    @Test
    fun singleObjectLine_isWrapped() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "lyricsList":{"syncedLyrics":{"offset":0,"line":{"start":500,"value":"only"}}}}}"""
        val r = SubsonicAuth.json.decodeFromString(SubsonicEnvelope.serializer(), body).response
        assertEquals(1, r.lyricsList?.syncedLyrics?.line?.size)
    }

    @Test
    fun malformedLyrics_degradesToEmpty() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1","lyricsList":{}}}"""
        val r = SubsonicAuth.json.decodeFromString(SubsonicEnvelope.serializer(), body).response
        assertTrue((r.lyricsList?.syncedLyrics?.line ?: emptyList()).isEmpty())
        // ... and the plain builder treats that as "no lyrics".
        assertTrue(SyncedLyrics.plain("").isEmpty)
    }
}
