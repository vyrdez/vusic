package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun track(id: String, artist: String = "Ar", artistId: String? = "ar") = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = artist, artistId = artistId, coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = false,
)

class RadioProgrammerTest {

    @Test
    fun shouldAppend_withinTwoOfEnd() {
        // 18th of 20 (index 17) triggers; 17th (index 16) does not.
        assertTrue(RadioProgrammer.shouldAppend(17, 20))
        assertTrue(RadioProgrammer.shouldAppend(18, 20))
        assertTrue(RadioProgrammer.shouldAppend(19, 20))
        assertFalse(RadioProgrammer.shouldAppend(16, 20))
        assertFalse(RadioProgrammer.shouldAppend(0, 20))
    }

    @Test
    fun shouldAppend_emptyOrDegenerate() {
        assertFalse(RadioProgrammer.shouldAppend(0, 0))
        assertFalse(RadioProgrammer.shouldAppend(-1, 20))
        assertFalse(RadioProgrammer.shouldAppend(0, 1 - 3))
    }

    @Test
    fun shouldAppend_smallQueue() {
        // A 2-song queue: index 0 is already within 2 of the end.
        assertTrue(RadioProgrammer.shouldAppend(0, 2))
        assertTrue(RadioProgrammer.shouldAppend(1, 2))
    }

    @Test
    fun buildBatch_dedupsAgainstQueueAndHistory() {
        val cands = listOf(track("a"), track("b"), track("c"))
        val out = RadioProgrammer.buildBatch(cands, setOf("a", "c"), want = 20)
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun buildBatch_capsAtWant() {
        val cands = (1..30).map { track("t$it", artist = "A$it", artistId = "a$it") }
        assertEquals(20, RadioProgrammer.buildBatch(cands, emptySet()).size)
    }

    @Test
    fun buildBatch_spreadsArtists_maxTwoInARow() {
        val cands = (1..6).map { track("a$it", artist = "Same", artistId = "same") } +
            (1..6).map { track("b$it", artist = "Other", artistId = "other") }
        val out = RadioProgrammer.buildBatch(cands, emptySet(), want = 12)
        assertEquals(12, out.size)
        var run = 1
        for (i in 1 until out.size) {
            run = if (out[i].artistId == out[i - 1].artistId) run + 1 else 1
            assertTrue("3 in a row from ${out[i].artistId} at $i", run <= 2)
        }
    }

    @Test
    fun buildBatch_singleArtist_onlyUnavoidableRuns() {
        val cands = (1..5).map { track("a$it", artist = "Only", artistId = "only") }
        val out = RadioProgrammer.buildBatch(cands, emptySet(), want = 5)
        assertEquals(5, out.size)
    }

    @Test
    fun buildBatch_emptyCandidates() {
        assertTrue(RadioProgrammer.buildBatch(emptyList(), emptySet()).isEmpty())
        assertTrue(RadioProgrammer.buildBatch(listOf(track("a")), setOf("a")).isEmpty())
    }

    @Test
    fun trigger_noDoubleAppend() {
        val t = RadioTrigger()
        val gen = t.newSession()
        assertTrue(t.tryBegin(gen))
        // Second begin while in flight is blocked.
        assertFalse(t.tryBegin(gen))
        t.end(gen)
        assertTrue(t.tryBegin(gen))
    }

    @Test
    fun trigger_newSessionInvalidatesStale() {
        val t = RadioTrigger()
        val old = t.newSession()
        assertTrue(t.tryBegin(old))
        val next = t.newSession()
        // Old generation can no longer begin or end the new one.
        assertFalse(t.tryBegin(old))
        t.end(old)
        assertTrue(t.tryBegin(next))
    }

    @Test
    fun trigger_cancelBlocksStale() {
        val t = RadioTrigger()
        val gen = t.newSession()
        t.cancel()
        assertFalse(t.tryBegin(gen))
        assertTrue(t.tryBegin(t.currentGeneration()))
    }

    @Test
    fun memory_neverRepeatsWithinSession() {
        val m = RadioMemory()
        m.remember("a")
        m.remember("b")
        m.remember("a")
        assertEquals(setOf("a", "b"), m.snapshot())
    }

    @Test
    fun memory_clearResetsSession() {
        val m = RadioMemory()
        m.remember("a")
        m.clear()
        assertTrue(m.snapshot().isEmpty())
    }

    @Test
    fun memory_trimsOldestPastCap() {
        val m = RadioMemory(cap = 3)
        m.remember("a")
        m.remember("b")
        m.remember("c")
        m.remember("d")
        assertEquals(setOf("b", "c", "d"), m.snapshot())
    }
}
