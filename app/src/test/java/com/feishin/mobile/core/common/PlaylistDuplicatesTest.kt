package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

private fun track(id: String) = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = "Ar", artistId = "ar", coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = false,
)

class PlaylistDuplicatesTest {

    @Test
    fun partition_splitsFreshAndDuplicates() {
        val cands = listOf(track("a"), track("b"), track("c"))
        val part = PlaylistDuplicates.partition(setOf("b"), cands)
        assertEquals(listOf("a", "c"), part.fresh.map { it.id })
        assertEquals(listOf("b"), part.duplicates.map { it.id })
    }

    @Test
    fun partition_noDuplicates() {
        val cands = listOf(track("a"), track("b"))
        val part = PlaylistDuplicates.partition(setOf("x"), cands)
        assertEquals(2, part.fresh.size)
        assertEquals(0, part.duplicates.size)
    }

    @Test
    fun partition_allDuplicates() {
        val cands = listOf(track("a"), track("b"))
        val part = PlaylistDuplicates.partition(setOf("a", "b", "c"), cands)
        assertEquals(0, part.fresh.size)
        assertEquals(2, part.duplicates.size)
    }

    @Test
    fun partition_emptyCandidates() {
        val part = PlaylistDuplicates.partition(setOf("a"), emptyList())
        assertEquals(0, part.fresh.size)
        assertEquals(0, part.duplicates.size)
    }
}
