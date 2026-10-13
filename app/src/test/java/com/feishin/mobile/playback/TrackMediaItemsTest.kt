package com.feishin.mobile.playback

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMediaItemsTest {

    private val track = Track(
        id = "trk1",
        title = "Charon",
        album = "Frozen Flower",
        albumId = "al1",
        artist = "Rokudenashi",
        artistId = "ar1",
        coverArt = "al-1",
        durationSec = 222,
        trackNo = 2,
        year = 2023,
        genre = "Music",
        suffix = "flac",
        starred = false,
    )

    private val streamUrl = "https://host/rest/stream.view?id=trk1"
    private val coverUrl = "https://host/rest/getCoverArt.view?id=al-1"

    @Test
    fun mediaInfo_mapsEveryField() {
        val info = TrackMediaItems.toMediaInfo(track, streamUrl, coverUrl)
        assertEquals("trk1", info.mediaId)
        assertEquals(streamUrl, info.streamUrl)
        assertEquals(coverUrl, info.coverArtUrl)
        assertEquals("Charon", info.title)
        assertEquals("Rokudenashi", info.artist)
        assertEquals("Frozen Flower", info.album)
        assertEquals(2, info.trackNo)
        assertEquals(222_000L, info.durationMs)
    }

    @Test
    fun mediaInfo_withoutArtworkAndDuration() {
        val noDur = track.copy(durationSec = 0)
        val info = TrackMediaItems.toMediaInfo(noDur, streamUrl, null)
        assertNull(info.coverArtUrl)
        assertEquals(0L, info.durationMs)
    }

    @Test
    fun mediaItem_carriesIdAndMetadata() {
        val item = TrackMediaItems.toMediaItem(track, streamUrl, coverUrl)
        assertEquals("trk1", item.mediaId)
        assertEquals("Charon", item.mediaMetadata.title?.toString())
        assertEquals("Rokudenashi", item.mediaMetadata.artist?.toString())
        assertEquals("Frozen Flower", item.mediaMetadata.albumTitle?.toString())
        assertEquals(2, item.mediaMetadata.trackNumber)
        assertEquals(222_000L, item.mediaMetadata.durationMs)
    }

    @Test
    fun mediaItem_fromInfo_keepsValues() {
        val info = TrackMediaItems.toMediaInfo(track, streamUrl, coverUrl)
        val item = TrackMediaItems.toMediaItem(info)
        assertEquals(info.mediaId, item.mediaId)
        assertEquals(info.title, item.mediaMetadata.title?.toString())
        assertEquals(info.durationMs, item.mediaMetadata.durationMs)
    }

    @Test
    fun toTrack_roundTripsCoreFields() {
        val item = TrackMediaItems.toMediaItem(track, streamUrl)
        val back = TrackMediaItems.toTrack(item)
        assertNotNull(back)
        assertEquals("trk1", back!!.id)
        assertEquals("Charon", back.title)
        assertEquals("Rokudenashi", back.artist)
        assertEquals("Frozen Flower", back.album)
        assertEquals(222, back.durationSec)
        assertEquals(2, back.trackNo)
    }

    @Test
    fun extrasMap_carriesCoverArtAndNavIds() {
        // Bundle itself is stubbed on the JVM, so the mapping is tested as a pure
        // Map; the Bundle round-trip is verified on-device (queue art after restore).
        val info = TrackMediaItems.toMediaInfo(track, streamUrl, coverUrl)
        val packed = TrackMediaItems.extrasMap(info)
        assertEquals("al-1", packed[TrackMediaItems.EXTRA_COVER_ART])
        assertEquals("ar1", packed[TrackMediaItems.EXTRA_ARTIST_ID])
        assertEquals("al1", packed[TrackMediaItems.EXTRA_ALBUM_ID])
        assertNull(packed[TrackMediaItems.EXTRA_QUEUE_ID])
    }

    @Test
    fun extrasMap_carriesPerEntryQueueKey() {
        // Two copies of the same song must be distinguishable in the queue.
        val a = TrackMediaItems.toMediaInfo(track, streamUrl, coverUrl, queueId = 41L)
        val b = TrackMediaItems.toMediaInfo(track, streamUrl, coverUrl, queueId = 42L)
        assertEquals("41", TrackMediaItems.extrasMap(a)[TrackMediaItems.EXTRA_QUEUE_ID])
        assertEquals("42", TrackMediaItems.extrasMap(b)[TrackMediaItems.EXTRA_QUEUE_ID])
        // The song id itself is unchanged, so server calls keep working.
        assertEquals("trk1", a.mediaId)
        assertEquals("trk1", b.mediaId)
    }

    @Test
    fun toMediaInfo_keepsQueueId() {
        assertEquals(7L, TrackMediaItems.toMediaInfo(track, streamUrl, null, queueId = 7L).queueId)
        assertNull(TrackMediaItems.toMediaInfo(track, streamUrl, null).queueId)
    }

    @Test
    fun toTrack_returnsNullForItemWithoutId() {
        assertNull(TrackMediaItems.toTrack(androidx.media3.common.MediaItem.Builder().setUri("https://host/x").build()))
    }
}