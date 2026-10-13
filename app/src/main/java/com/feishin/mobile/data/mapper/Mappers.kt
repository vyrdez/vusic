package com.feishin.mobile.data.mapper

import com.feishin.mobile.data.model.AlbumDetailDto
import com.feishin.mobile.data.model.AlbumDto
import com.feishin.mobile.data.model.ArtistDetailDto
import com.feishin.mobile.data.model.ArtistDto
import com.feishin.mobile.data.model.ArtistInfo2Dto
import com.feishin.mobile.data.model.GenreDto
import com.feishin.mobile.data.model.PlaylistDetailDto
import com.feishin.mobile.data.model.PlaylistDto
import com.feishin.mobile.data.model.SongDto
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.Track

fun AlbumDto.toDomain() = Album(
    id = id,
    name = name.ifBlank { "Unknown album" },
    artist = artist.ifBlank { "Unknown artist" },
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    durationSec = duration,
    playCount = playCount,
    year = year,
    genre = genre,
    starred = starred != null,
)

fun AlbumDetailDto.toDomain() = AlbumDetail(
    id = id,
    name = name.ifBlank { "Unknown album" },
    artist = artist.ifBlank { "Unknown artist" },
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    durationSec = duration,
    year = year,
    genre = genre,
    starred = starred != null,
    tracks = song.map { it.toDomain() },
)

fun SongDto.toDomain() = Track(
    id = id,
    title = title.ifBlank { "Unknown track" },
    album = album,
    albumId = albumId,
    artist = artist.ifBlank { "Unknown artist" },
    artistId = artistId,
    coverArt = coverArt,
    durationSec = duration,
    trackNo = track,
    year = year,
    genre = genre,
    suffix = suffix,
    starred = starred != null,
    sizeBytes = size,
    bitRate = bitRate,
    path = path,
)

fun ArtistDto.toDomain() = Artist(
    id = id,
    name = name.ifBlank { "Unknown artist" },
    coverArt = coverArt,
    albumCount = albumCount,
    starred = starred != null,
)

fun ArtistDetailDto.toDomain() = ArtistDetail(
    id = id,
    name = name.ifBlank { "Unknown artist" },
    coverArt = coverArt,
    albumCount = albumCount,
    starred = starred != null,
    albums = album.map { it.toDomain() },
)

fun PlaylistDto.toDomain() = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    durationSec = duration,
    coverArt = coverArt,
)

fun PlaylistDetailDto.toDomain() = PlaylistDetail(
    id = id,
    name = name,
    songCount = songCount,
    durationSec = duration,
    tracks = entry.map { it.toDomain() },
)

fun GenreDto.toDomain() = Genre(
    name = name.ifBlank { value }.ifBlank { "Unknown" },
    songCount = songCount,
    albumCount = albumCount,
)

fun ArtistInfo2Dto.toDomain() = ArtistInfo(
    biography = biography,
    imageUrl = largeImage ?: mediumImage ?: smallImage,
    similarArtists = similarArtist.map { it.toDomain() },
)
