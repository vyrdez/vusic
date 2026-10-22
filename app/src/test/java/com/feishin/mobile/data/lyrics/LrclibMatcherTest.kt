package com.feishin.mobile.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrclibMatcherTest {

    @Test
    fun `cleanTitle strips bracketed suffixes`() {
        assertEquals("Yellow", LrclibMatcher.cleanTitle("Yellow (Original Mix)"))
        assertEquals("Song Title", LrclibMatcher.cleanTitle("Song Title [Remastered 2021]"))
        assertEquals("Track Name", LrclibMatcher.cleanTitle("Track Name (feat. Artist B)"))
        assertEquals("Bansanka", LrclibMatcher.cleanTitle("Bansanka (Live)"))
    }

    @Test
    fun `splitTitleParts splits bilingual dash titles`() {
        val parts = LrclibMatcher.splitTitleParts("晩餐歌 - Bansanka")
        assertTrue(parts.contains("晩餐歌 - Bansanka"))
        assertTrue(parts.contains("晩餐歌"))
        assertTrue(parts.contains("Bansanka"))
    }

    @Test
    fun `splitArtists splits multi-artist strings`() {
        val artists = LrclibMatcher.splitArtists("Wuthering Waves / baitian / Victor Bo")
        assertTrue(artists.contains("Wuthering Waves"))
        assertTrue(artists.contains("baitian"))
        assertTrue(artists.contains("Victor Bo"))
    }

    @Test
    fun `scoreCandidate accepts confident match within duration tolerance`() {
        val candidate = LrclibResponseDto(
            trackName = "Yellow",
            artistName = "Coldplay",
            duration = 269.0,
            syncedLyrics = "[00:10.00]Look at the stars",
        )
        val score = LrclibMatcher.scoreCandidate(
            candidate = candidate,
            targetTitle = "Yellow (Remastered)",
            targetArtist = "Coldplay",
            targetDurationSec = 270L,
        )
        assertTrue("Expected score >= 0.75, got $score", score >= LrclibMatcher.CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `scoreCandidate rejects duration difference greater than 5 seconds`() {
        val candidate = LrclibResponseDto(
            trackName = "Yellow",
            artistName = "Coldplay",
            duration = 180.0,
        )
        val score = LrclibMatcher.scoreCandidate(
            candidate = candidate,
            targetTitle = "Yellow",
            targetArtist = "Coldplay",
            targetDurationSec = 270L,
        )
        assertEquals(0.0, score, 0.001)
    }
}
