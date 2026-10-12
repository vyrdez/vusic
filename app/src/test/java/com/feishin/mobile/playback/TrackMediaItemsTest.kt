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
    fun toTrack_returnsNullForItemWithoutId() {
        assertNull(TrackMediaItems.toTrack(androidx.media3.common.MediaItem.Builder().setUri("https://host/x").build()))
    }
}