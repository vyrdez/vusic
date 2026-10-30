package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P10.1 Section G: the "Mixed for you" shelf always shows five real mixes. */
class MixGeneratorTest {

    private fun track(i: Int, artistCount: Int = 8): Track = Track(
        id = "t$i",
        title = "Track $i",
        album = "Album ${i % 12}",
        albumId = "alb-${i % 12}",
        artist = "Artist ${i % artistCount}",
        artistId = "art-${i % artistCount}",
        coverArt = "cover-${i % 12}",
        durationSec = 200,
        trackNo = i,
        year = 1990 + (i % 30),
        genre = listOf("Rock", "Jazz", "Pop", "Electronic").get(i % 4),
        suffix = "mp3",
        starred = i % 3 == 0,
    )

    private fun library(size: Int): List<Track> = (1..size).map { track(it) }

    private fun assertFiveWellFormedMixes(tracks: List<Track>, batchSize: Int) {
        val mixes = MixGenerator.generate(randomSongs = tracks, batchSize = batchSize, seed = 42L)

        assertEquals(5, mixes.size)
        assertEquals(
            listOf("My Mix 1", "My Mix 2", "My Mix 3", "My Mix 4", "My Mix 5"),
            mixes.map { it.name },
        )

        val poolIds = tracks.map { it.id }.toSet()
        for (mix in mixes) {
            assertTrue("${mix.name} must not be empty", mix.tracks.isNotEmpty())
            assertTrue("${mix.name} exceeds batch size", mix.tracks.size <= batchSize)
            assertEquals(
                "${mix.name} has duplicate tracks",
                mix.tracks.size,
                mix.tracks.map { it.id }.distinct().size,
            )
            assertTrue("${mix.name} contains a track outside the pool", mix.tracks.all { it.id in poolIds })
            val covers = mix.tracks.mapNotNull { it.coverArt }.distinct()
            assertTrue("${mix.name} must have cover art", covers.isNotEmpty())
            assertTrue("${mix.name} exposes more than 4 covers", covers.take(4).size <= 4)
        }
    }

    @Test
    fun smallLibraryStillProducesFiveMixes() {
        assertFiveWellFormedMixes(library(5), batchSize = 25)
        assertFiveWellFormedMixes(library(12), batchSize = 10)
        assertFiveWellFormedMixes(library(20), batchSize = 25)
    }

    @Test
    fun mediumLibraryProducesFiveMixes() {
        assertFiveWellFormedMixes(library(100), batchSize = 25)
    }

    @Test
    fun largeLibraryProducesFiveMixes() {
        assertFiveWellFormedMixes(library(500), batchSize = 25)
    }

    @Test
    fun fewerThanFiveTracksReturnsNoMixesGracefully() {
        assertTrue(MixGenerator.generate(randomSongs = emptyList()).isEmpty())
        assertTrue(MixGenerator.generate(randomSongs = library(4)).isEmpty())
    }

    @Test
    fun mixesAreDeterministicForTheSameSeed() {
        val tracks = library(50)
        val first = MixGenerator.generate(randomSongs = tracks, seed = 7L).map { it.tracks.map(Track::id) }
        val second = MixGenerator.generate(randomSongs = tracks, seed = 7L).map { it.tracks.map(Track::id) }
        assertEquals(first, second)
    }
}
