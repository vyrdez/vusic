package com.feishin.mobile.core.network

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricsCacheKeyTest {

    private fun serverTrack() = Track(
        id = "srv-42",
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = "al-1",
        artistId = "ar-1",
        coverArt = null,
        durationSec = 200,
        trackNo = 1,
        year = 2024,
        genre = null,
        suffix = "flac",
        starred = false,
    )

    private fun localTrack() = serverTrack().copy(
        id = "local-123",
        localUri = "content://media/external/audio/media/123",
        localModifiedSec = 1_700_000_000L,
    )

    @Test
    fun `server songs are keyed by song id`() {
        assertEquals("srv-42", LyricsCacheKey.of(serverTrack()))
    }

    @Test
    fun `device files are keyed by uri and modification time`() {
        assertEquals(
            "local:content://media/external/audio/media/123@1700000000",
            LyricsCacheKey.of(localTrack()),
        )
    }

    @Test
    fun `re-tagged device file gets a fresh cache key`() {
        val before = LyricsCacheKey.of(localTrack())
        val after = LyricsCacheKey.of(localTrack().copy(localModifiedSec = 1_700_000_100L))

        assertNotEquals(before, after)
    }
}
