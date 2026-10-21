package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistTrackOrderTest {

    private fun track(id: String, trackNo: Int? = 1, year: Int? = null) = Track(
        id = id, title = id, album = "a", albumId = "al", artist = "ar",
        artistId = "ari", coverArt = null, durationSec = 100, trackNo = trackNo,
        year = year, genre = null, suffix = null, starred = false,
    )

    @Test
    fun `empty input stays empty`() {
        assertEquals(emptyList<Track>(), ArtistTrackOrder.albumsToTracks(emptyList()))
    }

    @Test
    fun `single album keeps track order`() {
        val album = 2020 to listOf(track("b", 2, 2020), track("a", 1, 2020))
        val out = ArtistTrackOrder.albumsToTracks(listOf(album))
        assertEquals(listOf("a", "b"), out.map { it.id })
    }

    @Test
    fun `newest album comes first`() {
        val old = 2010 to listOf(track("old", 1, 2010))
        val new = 2022 to listOf(track("new", 1, 2022))
        val out = ArtistTrackOrder.albumsToTracks(listOf(old, new))
        assertEquals(listOf("new", "old"), out.map { it.id })
    }

    @Test
    fun `duplicate song id across albums is kept once`() {
        val a = 2020 to listOf(track("x", 1, 2020))
        val b = 2021 to listOf(track("x", 1, 2021), track("y", 2, 2021))
        val out = ArtistTrackOrder.albumsToTracks(listOf(a, b))
        assertEquals(listOf("y", "x"), out.map { it.id })
    }

    @Test
    fun `unknown year sorts last`() {
        val known = 2020 to listOf(track("k", 1, 2020))
        val unknown = 0 to listOf(track("u", 1, null))
        val out = ArtistTrackOrder.albumsToTracks(listOf(unknown, known))
        assertEquals(listOf("k", "u"), out.map { it.id })
    }

    @Test
    fun `equal track numbers fall back to id`() {
        val album = 2020 to listOf(track("z", 1, 2020), track("a", 1, 2020))
        val out = ArtistTrackOrder.albumsToTracks(listOf(album))
        assertEquals(listOf("a", "z"), out.map { it.id })
    }

    @Test
    fun `track number missing sorts as zero`() {
        val album = 2020 to listOf(track("b", 3, 2020), track("a", null, 2020))
        val out = ArtistTrackOrder.albumsToTracks(listOf(album))
        assertEquals(listOf("a", "b"), out.map { it.id })
    }
}
