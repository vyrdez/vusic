package com.feishin.mobile.domain.model

/** Domain models. The UI only ever sees these — never wire DTOs. */

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
)

data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    val starred: Boolean,
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    val starred: Boolean,
    val albums: List<Album>,
)

data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int,
    val durationSec: Int,
    val coverArt: String?,
)

data class PlaylistDetail(
    val id: String,
    val name: String,
    val songCount: Int,
    val durationSec: Int,
    val tracks: List<Track>,
)

data class Genre(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

data class StarredCollection(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
)
