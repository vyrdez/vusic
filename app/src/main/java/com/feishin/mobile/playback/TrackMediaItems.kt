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
    /**
     * Unique per queue entry (not per song), so the same song queued twice still
     * has two distinguishable MediaItems. Null for ad-hoc items.
     */
    val queueId: Long? = null,
    /** Content URI for device files; null for server songs. */
    val localUri: String? = null,
    /** Device-file modification time, part of the lyrics cache key. */
    val localModifiedSec: Long = 0L,
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
        queueId: Long? = null,
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
        queueId = queueId,
        localUri = track.localUri,
        localModifiedSec = track.localModifiedSec,
    )

    @androidx.media3.common.util.UnstableApi
    fun toMediaItem(
        track: Track,
        streamUrl: String,
        coverArtUrl: String? = null,
        durationMs: Long = if (track.durationSec > 0) track.durationSec * 1000L else 0L,
        queueId: Long? = null,
    ): MediaItem = toMediaItem(
        toMediaInfo(track, streamUrl, coverArtUrl, queueId).copy(durationMs = durationMs),
    )

    // Round-trip payload keys. Kept as pure Map logic so the mapping itself is
    // unit-testable without Android's Bundle (which is stubbed on the JVM).
    internal fun extrasMap(info: TrackMediaInfo): Map<String, String?> = mapOf(
        EXTRA_COVER_ART to info.coverArtId,
        EXTRA_ARTIST_ID to info.artistId,
        EXTRA_ALBUM_ID to info.albumId,
        EXTRA_QUEUE_ID to info.queueId?.toString(),
        EXTRA_LOCAL_URI to info.localUri,
        EXTRA_LOCAL_MODIFIED to info.localModifiedSec.takeIf { it > 0L }?.toString(),
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
                        EXTRA_QUEUE_ID to packed[EXTRA_QUEUE_ID],
                        EXTRA_LOCAL_URI to packed[EXTRA_LOCAL_URI],
                        EXTRA_LOCAL_MODIFIED to packed[EXTRA_LOCAL_MODIFIED],
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

    /** The per-queue-entry key this item was built with, if any. */
    fun queueIdOf(item: MediaItem): Long? =
        item.mediaMetadata.extras?.getString(EXTRA_QUEUE_ID)?.toLongOrNull()

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
            localUri = extras?.getString(EXTRA_LOCAL_URI),
            localModifiedSec = extras?.getString(EXTRA_LOCAL_MODIFIED)?.toLongOrNull() ?: 0L,
        )
    }

    const val EXTRA_COVER_ART = "coverArt"
    const val EXTRA_ARTIST_ID = "artistId"
    const val EXTRA_ALBUM_ID = "albumId"
    const val EXTRA_QUEUE_ID = "queueId"
    const val EXTRA_LOCAL_URI = "localUri"
    const val EXTRA_LOCAL_MODIFIED = "localModifiedSec"
}