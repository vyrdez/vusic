package com.feishin.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse(),
)

@Serializable
data class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val error: SubsonicError? = null,
    val albumList: AlbumList? = null,
    val albumList2: AlbumList2? = null,
    val searchResult3: SearchResult3? = null,
    val album: AlbumDetail? = null,
    val artist: ArtistDetail? = null,
    val artists: Artists? = null,
    val playlists: Playlists? = null,
    val playlist: PlaylistDetail? = null,
    val starred: Starred? = null,
    val starred2: Starred2? = null,
    val genres: Genres? = null,
    val lyrics: Lyrics? = null,
)

@Serializable
data class SubsonicError(val code: Int = 0, val message: String = "")

@Serializable
data class AlbumList(val album: List<Album> = emptyList())

@Serializable
data class AlbumList2(val album: List<Album> = emptyList())

@Serializable
data class Album(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Long = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
)

@Serializable
data class AlbumDetail(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val album: String = "",
    val albumId: String? = null,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long = 0,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val starred: String? = null,
    val path: String = "",
)

@Serializable
data class Artists(val index: List<ArtistIndex> = emptyList())

@Serializable
data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())

@Serializable
data class Artist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
)

@Serializable
data class ArtistDetail(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<Album> = emptyList(),
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Playlists(val playlist: List<Playlist> = emptyList())

@Serializable
data class Playlist(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
)

@Serializable
data class PlaylistDetail(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class Starred(val artist: List<Artist> = emptyList(), val album: List<Album> = emptyList(), val song: List<Song> = emptyList())

@Serializable
data class Starred2(val artist: List<Artist> = emptyList(), val album: List<Album> = emptyList(), val song: List<Song> = emptyList())

@Serializable
data class Genres(val genre: List<Genre> = emptyList())

@Serializable
data class Genre(val name: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class Lyrics(val artist: String = "", val title: String = "", val value: String = "")
