@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.feishin.mobile.domain.model.Track

/** Pure, Android-free description of a track's media payload. */
data class TrackMediaInfo(
    val mediaId: String,
    val streamUrl: String,
    val coverArtUrl: String?,
    val title: String,
    val artist: String,
    val album: String,
    val trackNo: Int?,
    val durationMs: Long,
)

/**
 * Domain [Track] -> Media3 [MediaItem] mapping. The [TrackMediaInfo] step is pure
 * (no Android classes) so it is unit-testable; only [toMediaItem] touches Uri.
 */
object TrackMediaItems {

    fun toMediaInfo(
        track: Track,
        streamUrl: String,
        coverArtUrl: String? = null,
    ): TrackMediaInfo = TrackMediaInfo(
        mediaId = track.id,
        streamUrl = streamUrl,
        coverArtUrl = coverArtUrl,
        title = track.title,
        artist = track.artist,
        album = track.album,
        trackNo = track.trackNo,
        durationMs = if (track.durationSec > 0) track.durationSec * 1000L else 0L,
    )

    @androidx.media3.common.util.UnstableApi
    fun toMediaItem(
        track: Track,
        streamUrl: String,
        coverArtUrl: String? = null,
        durationMs: Long = if (track.durationSec > 0) track.durationSec * 1000L else 0L,
    ): MediaItem = toMediaItem(
        toMediaInfo(track, streamUrl, coverArtUrl).copy(durationMs = durationMs),
    )

    fun toMediaItem(info: TrackMediaInfo): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(info.title)
            .setArtist(info.artist)
            .setAlbumTitle(info.album)
            .setTrackNumber(info.trackNo)
            .setDurationMs(info.durationMs)
            .apply { info.coverArtUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(info.mediaId)
            .setUri(info.streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Rebuilds a MediaItem with fresh (single-use) stream auth params. */
    fun withStreamUrl(item: MediaItem, streamUrl: String): MediaItem =
        item.buildUpon().setUri(streamUrl).build()

    @androidx.media3.common.util.UnstableApi
    fun toTrack(item: MediaItem): Track? {
        val id = item.mediaId.takeIf { it.isNotBlank() } ?: return null
        val meta = item.mediaMetadata
        return Track(
            id = id,
            title = meta.title?.toString() ?: "Unknown track",
            album = meta.albumTitle?.toString() ?: "",
            albumId = null,
            artist = meta.artist?.toString() ?: "Unknown artist",
            artistId = null,
            coverArt = null,
            durationSec = ((meta.durationMs ?: 0L) / 1000L).toInt(),
            trackNo = meta.trackNumber,
            year = null,
            genre = null,
            suffix = null,
            starred = false,
        )
    }
}