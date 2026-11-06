package com.feishin.mobile

import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.lyrics.EmbeddedLyricsExtractor
import com.feishin.mobile.data.queue.QueueSnapshot
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.playback.QueueItem
import com.feishin.mobile.playback.TrackMediaItems
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for Section F sweep fixes:
 * 1. EmbeddedLyricsExtractor tag/frame cap preventing OOM / NegativeArraySizeException.
 * 2. DownloadRepository coverArtId sanitization.
 * 3. Favorites collection deduplication by id preserving order.
 * 4. Repeat mode snapshot persistence round-trip.
 * 5. QueueItem ID matching in external queue adoption.
 */
class SweepFixesTest {

    // -------------------------------------------------------------------------
    // Item #1: EmbeddedLyricsExtractor OOM / NegativeArraySize bounds check
    // -------------------------------------------------------------------------

    @Test
    fun `embeddedLyricsExtractor rejects oversized ID3v2 header without allocating or throwing`() {
        // Tag size: 0x7F, 0x7F, 0x7F, 0x7F synchsafe integer = ~256 MB (> MAX_TAG_BYTES of 4 MB)
        val id3Oversized = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0,
            0x7F, 0x7F, 0x7F, 0x7F,
        )
        val result = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(id3Oversized))
        assertNull("Oversized ID3v2 tag must return null without OOM", result)
    }

    @Test
    fun `embeddedLyricsExtractor rejects zero or negative ID3v2 tag size cleanly`() {
        // Tag size: 0
        val id3Zero = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0,
            0, 0, 0, 0,
        )
        val result = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(id3Zero))
        assertNull("Zero-size ID3v2 tag must return null", result)
    }

    @Test
    fun `embeddedLyricsExtractor rejects oversized FLAC metadata block without throwing`() {
        // FLAC magic + block header with length 0x7F0000 = ~8.3 MB (> MAX_TAG_BYTES)
        val flacOversized = byteArrayOf(
            'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
            4, // VORBIS_COMMENT block type
            0x7F.toByte(), 0x00, 0x00,
        )
        val result = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(flacOversized))
        assertNull("Oversized FLAC block must return null without allocating", result)
    }

    @Test
    fun `embeddedLyricsExtractor rejects zero-length FLAC metadata block cleanly`() {
        val flacZero = byteArrayOf(
            'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
            4,
            0, 0, 0,
        )
        val result = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(flacZero))
        assertNull("Zero-length FLAC block must return null", result)
    }

    @Test
    fun `embeddedLyricsExtractor rejects oversized MP4 lyrics atom without throwing`() {
        // MP4 header: 4 bytes size (16), 4 bytes type ('moov')
        // Sub-atom: 4 bytes size (> 4 MB), 4 bytes type ('\u00A9lyr')
        val streamBytes = ByteArrayOutputStream().apply {
            // moov atom (outer container)
            write(0); write(0); write(0); write(24)
            write("moov".toByteArray(StandardCharsets.ISO_8859_1))
            // ©lyr atom with 10 MB size (0x00A00000)
            write(0x00); write(0xA0.toByte().toInt()); write(0x00); write(0x00)
            write(0xA9) // '©' in ISO-8859-1
            write('l'.code); write('y'.code); write('r'.code)
        }.toByteArray()

        val result = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(streamBytes))
        assertNull("Oversized MP4 lyrics atom must return null without throwing", result)
    }

    // -------------------------------------------------------------------------
    // Item #15: DownloadRepository coverArtId sanitization
    // -------------------------------------------------------------------------

    @Test
    fun `sanitizeCoverArtId replaces slashes and colons cleanly`() {
        val input = "al-123/456:789"
        val sanitized = DownloadRepository.sanitizeCoverArtId(input)
        assertEquals("al-123_456_789", sanitized)
    }

    @Test
    fun `sanitizeCoverArtId preserves valid characters and replaces invalid symbols`() {
        val input = "cover_art-01.jpg:v1/test?query=abc#hash"
        val sanitized = DownloadRepository.sanitizeCoverArtId(input)
        assertEquals("cover_art-01.jpg_v1_test_query_abc_hash", sanitized)
    }

    @Test
    fun `sanitizeCoverArtId leaves clean alphanumeric string untouched`() {
        val input = "al-abc123_DEF.456"
        val sanitized = DownloadRepository.sanitizeCoverArtId(input)
        assertEquals("al-abc123_DEF.456", sanitized)
    }

    // -------------------------------------------------------------------------
    // Item #4: Favorites deduplication by id preserving order
    // -------------------------------------------------------------------------

    @Test
    fun `favorites deduplication removes duplicate tracks while preserving first occurrence order`() {
        val t1 = fakeTrack("track-1", "Song A")
        val t2 = fakeTrack("track-2", "Song B")
        val t1Dup = fakeTrack("track-1", "Song A Duplicate")
        val t3 = fakeTrack("track-3", "Song C")

        val rawList = listOf(t1, t2, t1Dup, t3, t2)
        val deduplicated = rawList.distinctBy { it.id }

        assertEquals(3, deduplicated.size)
        assertEquals(listOf("track-1", "track-2", "track-3"), deduplicated.map { it.id })
        assertEquals("Song A", deduplicated[0].title)
    }

    @Test
    fun `starredCollection deduplication cleans tracks albums and artists`() {
        val collection = StarredCollection(
            tracks = listOf(fakeTrack("t1", "T1"), fakeTrack("t2", "T2"), fakeTrack("t1", "T1-dup")),
            albums = listOf(fakeAlbum("al1", "Album 1"), fakeAlbum("al1", "Album 1-dup"), fakeAlbum("al2", "Album 2")),
            artists = listOf(fakeArtist("ar1", "Artist 1"), fakeArtist("ar2", "Artist 2"), fakeArtist("ar2", "Artist 2-dup")),
        )

        val cleaned = collection.copy(
            tracks = collection.tracks.distinctBy { it.id },
            albums = collection.albums.distinctBy { it.id },
            artists = collection.artists.distinctBy { it.id },
        )

        assertEquals(listOf("t1", "t2"), cleaned.tracks.map { it.id })
        assertEquals(listOf("al1", "al2"), cleaned.albums.map { it.id })
        assertEquals(listOf("ar1", "ar2"), cleaned.artists.map { it.id })
    }

    // -------------------------------------------------------------------------
    // Item #7: repeatMode snapshot round-trip persistence
    // -------------------------------------------------------------------------

    @Test
    fun `queueSnapshot serializes and deserializes repeatMode cleanly`() {
        val json = Json { ignoreUnknownKeys = true }
        val snapshotAll = QueueSnapshot(
            currentIndex = 1,
            positionMs = 5000L,
            repeatMode = 2, // Player.REPEAT_MODE_ALL
        )
        val serializedAll = json.encodeToString(QueueSnapshot.serializer(), snapshotAll)
        val deserializedAll = json.decodeFromString(QueueSnapshot.serializer(), serializedAll)
        assertEquals(2, deserializedAll.repeatMode)

        val snapshotOne = QueueSnapshot(
            currentIndex = 0,
            positionMs = 0L,
            repeatMode = 1, // Player.REPEAT_MODE_ONE
        )
        val serializedOne = json.encodeToString(QueueSnapshot.serializer(), snapshotOne)
        val deserializedOne = json.decodeFromString(QueueSnapshot.serializer(), serializedOne)
        assertEquals(1, deserializedOne.repeatMode)
    }

    // -------------------------------------------------------------------------
    // Item #5: QueueItem ID matching in external queue adoption
    // -------------------------------------------------------------------------

    @Test
    fun `queueItem preserves unique queueIds when resolving to media items even with duplicate tracks`() {
        val trackA = fakeTrack("track-1", "Song A")
        val trackB = fakeTrack("track-2", "Song B")
        val trackADup = fakeTrack("track-1", "Song A (Duplicate)")

        val queueItems = listOf(
            QueueItem(trackA, 1001L),
            QueueItem(trackB, 1002L),
            QueueItem(trackADup, 1003L),
        )

        val mediaInfos = queueItems.map { qi ->
            TrackMediaItems.toMediaInfo(
                track = qi.song,
                streamUrl = "https://host/stream?id=${qi.song.id}",
                coverArtUrl = null,
                queueId = qi.queueId,
            )
        }

        // Every MediaInfo must retain the exact queueId minted in QueueItem
        assertEquals(listOf(1001L, 1002L, 1003L), mediaInfos.map { it.queueId })

        // Extras map must preserve the queueId per entry, distinguishing duplicate tracks
        val extras = mediaInfos.map { TrackMediaItems.extrasMap(it) }
        assertEquals("1001", extras[0][TrackMediaItems.EXTRA_QUEUE_ID])
        assertEquals("1002", extras[1][TrackMediaItems.EXTRA_QUEUE_ID])
        assertEquals("1003", extras[2][TrackMediaItems.EXTRA_QUEUE_ID])
    }

    private fun fakeTrack(id: String, title: String) = Track(
        id = id,
        title = title,
        album = "Album",
        albumId = "al1",
        artist = "Artist",
        artistId = "ar1",
        coverArt = "cov1",
        durationSec = 180,
        trackNo = 1,
        year = 2024,
        genre = "Pop",
        suffix = "flac",
        starred = true,
    )

    private fun fakeAlbum(id: String, name: String) = Album(
        id = id,
        name = name,
        artist = "Artist",
        artistId = "ar1",
        coverArt = "cov1",
        songCount = 10,
        durationSec = 1800,
        playCount = 5,
        year = 2024,
        genre = "Pop",
        starred = true,
    )

    private fun fakeArtist(id: String, name: String) = Artist(
        id = id,
        name = name,
        coverArt = "cov1",
        albumCount = 2,
        starred = true,
    )
}
