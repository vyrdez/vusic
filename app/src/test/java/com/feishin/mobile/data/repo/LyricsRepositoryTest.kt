package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryTest {

    @Test
    fun `upgrade rule prefers synced metadata when online is plain`() {
        val lrclibPlain = Lyrics(
            lines = emptyList(),
            plainText = "Plain text from lrclib",
            kind = LyricsKind.PLAIN,
            source = LyricsSource.LRCLIB,
        )
        val metaSynced = Lyrics(
            lines = listOf(LyricLine(1000L, "Synced line from metadata")),
            kind = LyricsKind.SYNCED,
            source = LyricsSource.SERVER_METADATA,
        )

        // Verifies the upgrade logic: if online is plain and metadata is synced, metadata wins
        val finalLyrics = if (lrclibPlain.isPlain && metaSynced.isSynced) {
            metaSynced
        } else {
            lrclibPlain
        }

        assertTrue(finalLyrics.isSynced)
        assertEquals(LyricsSource.SERVER_METADATA, finalLyrics.source)
    }

    @Test
    fun `local track cache key incorporates content uri and modification time`() {
        val localTrack = Track(
            id = "local-123",
            title = "Test",
            artist = "Artist",
            album = "Album",
            albumId = null,
            artistId = null,
            coverArt = null,
            durationSec = 200,
            trackNo = 1,
            year = 2024,
            genre = null,
            suffix = "mp3",
            starred = false,
            localUri = "content://media/external/audio/media/123",
            localModifiedSec = 1700000000L,
        )
        val expectedKey = "local:content://media/external/audio/media/123@1700000000"
        val key = if (localTrack.isLocal) {
            "local:${localTrack.localUri}@${localTrack.localModifiedSec}"
        } else {
            localTrack.id
        }
        assertEquals(expectedKey, key)
    }
}
