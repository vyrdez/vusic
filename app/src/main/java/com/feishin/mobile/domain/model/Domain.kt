package com.feishin.mobile.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain models. The UI only ever sees these — never wire DTOs.
 *
 * `@Serializable` so the library cache can persist them as JSON in Room, which
 * is what lets screens keep rendering with no network.
 */

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val durationSec: Int,
    val playCount: Long,
    val year: Int?,
    val genre: String?,
    val starred: Boolean,
)

@Serializable
data class AlbumDetail(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val durationSec: Int,
    val year: Int?,
    val genre: String?,
    val starred: Boolean,
    val tracks: List<Track>,
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val album: String,
    val albumId: String?,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val durationSec: Int,
    val trackNo: Int?,
    val year: Int?,
    val genre: String?,
    val suffix: String?,
    val starred: Boolean,
    val sizeBytes: Long = 0,
    val bitRate: Int? = null,
    val path: String = "",
    /** ReplayGain (dB gains and linear peak) carried from the server. */
    val replayGainTrack: Double? = null,
    val replayGainAlbum: Double? = null,
    val replayGainPeak: Double? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    val starred: Boolean,
)

@Serializable
data class ArtistDetail(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    val starred: Boolean,
    val albums: List<Album>,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int,
    val durationSec: Int,
    val coverArt: String?,
)

@Serializable
data class PlaylistDetail(
    val id: String,
    val name: String,
    val songCount: Int,
    val durationSec: Int,
    val tracks: List<Track>,
)

@Serializable
data class Genre(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Serializable
data class StarredCollection(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class ArtistInfo(
    val biography: String = "",
    val imageUrl: String? = null,
    val similarArtists: List<Artist> = emptyList(),
)
