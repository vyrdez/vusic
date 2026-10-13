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
    val coverArtId: String?,
    val artistId: String?,
    val albumId: String?,
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
        coverArtId = track.coverArt,
        artistId = track.artistId,
        albumId = track.albumId,
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

    // Round-trip payload keys. Kept as pure Map logic so the mapping itself is
    // unit-testable without Android's Bundle (which is stubbed on the JVM).
    internal fun extrasMap(info: TrackMediaInfo): Map<String, String?> = mapOf(
        EXTRA_COVER_ART to info.coverArtId,
        EXTRA_ARTIST_ID to info.artistId,
        EXTRA_ALBUM_ID to info.albumId,
    )

    fun toMediaItem(info: TrackMediaInfo): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(info.title)
            .setArtist(info.artist)
            .setAlbumTitle(info.album)
            .setTrackNumber(info.trackNo)
            .setDurationMs(info.durationMs)
            .apply {
                info.coverArtUrl?.let { setArtworkUri(Uri.parse(it)) }
                val packed = extrasMap(info)
                setExtras(
                    androidx.core.os.bundleOf(
                        EXTRA_COVER_ART to packed[EXTRA_COVER_ART],
                        EXTRA_ARTIST_ID to packed[EXTRA_ARTIST_ID],
                        EXTRA_ALBUM_ID to packed[EXTRA_ALBUM_ID],
                    ),
                )
            }
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
        val extras = meta.extras
        return Track(
            id = id,
            title = meta.title?.toString() ?: "Unknown track",
            album = meta.albumTitle?.toString() ?: "",
            albumId = extras?.getString(EXTRA_ALBUM_ID),
            artist = meta.artist?.toString() ?: "Unknown artist",
            artistId = extras?.getString(EXTRA_ARTIST_ID),
            coverArt = extras?.getString(EXTRA_COVER_ART),
            durationSec = ((meta.durationMs ?: 0L) / 1000L).toInt(),
            trackNo = meta.trackNumber,
            year = null,
            genre = null,
            suffix = null,
            starred = false,
        )
    }

    const val EXTRA_COVER_ART = "coverArt"
    const val EXTRA_ARTIST_ID = "artistId"
    const val EXTRA_ALBUM_ID = "albumId"
}