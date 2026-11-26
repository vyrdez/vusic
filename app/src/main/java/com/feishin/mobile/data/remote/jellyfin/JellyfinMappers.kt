package com.feishin.mobile.data.remote.jellyfin

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track

private const val TICKS_PER_SECOND = 10_000_000L

private fun JellyfinBaseItemDto.durationSeconds(): Int =
    ((runTimeTicks ?: 0L) / TICKS_PER_SECOND).toInt()

private fun JellyfinBaseItemDto.primaryCoverArtId(profileId: String): String? =
    if (imageTags?.primary != null && id.isNotBlank()) {
        GlobalId.format(profileId, id)
    } else {
        null
    }

private fun JellyfinBaseItemDto.trackCoverArtId(profileId: String): String? =
    primaryCoverArtId(profileId)
        ?: albumId?.takeIf { it.isNotBlank() }?.let { GlobalId.format(profileId, it) }

fun List<JellyfinBaseItemDto>.sortedByDiscAndTrack(): List<JellyfinBaseItemDto> =
    sortedWith(
        compareBy<JellyfinBaseItemDto>(
            { it.parentIndexNumber ?: 1 },
            { it.indexNumber ?: Int.MAX_VALUE },
            { it.name },
        ),
    )

fun JellyfinBaseItemDto.toAlbum(profile: ServerProfile): Album {
    val resolvedArtist = albumArtist?.takeIf { it.isNotBlank() }
        ?: albumArtists.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: artistItems.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: "Unknown artist"
    val resolvedArtistId = (albumArtists.firstOrNull()?.id ?: artistItems.firstOrNull()?.id)
        ?.takeIf { it.isNotBlank() }
        ?.let { GlobalId.format(profile.id, it) }

    return Album(
        id = GlobalId.format(profile.id, id),
        name = name.ifBlank { "Unknown album" },
        artist = resolvedArtist,
        artistId = resolvedArtistId,
        coverArt = primaryCoverArtId(profile.id),
        songCount = childCount ?: recursiveItemCount ?: songCount ?: 0,
        durationSec = durationSeconds(),
        playCount = userData?.playCount ?: 0L,
        year = productionYear,
        genre = genres.firstOrNull()?.takeIf { it.isNotBlank() },
        starred = userData?.isFavorite == true,
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
    )
}

fun JellyfinBaseItemDto.toAlbumDetail(
    profile: ServerProfile,
    tracks: List<JellyfinBaseItemDto>,
): AlbumDetail {
    val mappedTracks = tracks.sortedByDiscAndTrack().map { it.toTrack(profile) }
    val albumModel = toAlbum(profile)
    return AlbumDetail(
        id = albumModel.id,
        name = albumModel.name,
        artist = albumModel.artist,
        artistId = albumModel.artistId,
        coverArt = albumModel.coverArt,
        songCount = if (albumModel.songCount > 0) albumModel.songCount else mappedTracks.size,
        durationSec = if (albumModel.durationSec > 0) albumModel.durationSec else mappedTracks.sumOf { it.durationSec },
        year = albumModel.year,
        genre = albumModel.genre,
        starred = albumModel.starred,
        tracks = mappedTracks,
    )
}

fun JellyfinBaseItemDto.toTrack(profile: ServerProfile): Track {
    val primarySource = mediaSources.firstOrNull()
    val resolvedArtist = artistItems.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: artists.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: albumArtist?.takeIf { it.isNotBlank() }
        ?: "Unknown artist"
    val resolvedArtistId = (artistItems.firstOrNull()?.id ?: albumArtists.firstOrNull()?.id)
        ?.takeIf { it.isNotBlank() }
        ?.let { GlobalId.format(profile.id, it) }

    return Track(
        id = GlobalId.format(profile.id, id),
        title = name.ifBlank { "Unknown track" },
        album = album.orEmpty(),
        albumId = albumId?.takeIf { it.isNotBlank() }?.let { GlobalId.format(profile.id, it) },
        artist = resolvedArtist,
        artistId = resolvedArtistId,
        coverArt = trackCoverArtId(profile.id),
        durationSec = durationSeconds(),
        trackNo = indexNumber,
        year = productionYear,
        genre = genres.firstOrNull()?.takeIf { it.isNotBlank() },
        suffix = primarySource?.container ?: container,
        starred = userData?.isFavorite == true,
        sizeBytes = primarySource?.size ?: 0L,
        // Normalize Jellyfin bps to kbps so FederatedDeduplicator compares on one scale.
        bitRate = primarySource?.bitrate?.let { it / 1000 },
        path = primarySource?.path ?: path.orEmpty(),
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
        playlistItemId = playlistItemId?.takeIf { it.isNotBlank() },
    )
}

fun JellyfinBaseItemDto.toArtist(profile: ServerProfile): Artist = Artist(
    id = GlobalId.format(profile.id, id),
    name = name.ifBlank { "Unknown artist" },
    coverArt = primaryCoverArtId(profile.id),
    albumCount = albumCount ?: childCount ?: recursiveItemCount ?: 0,
    starred = userData?.isFavorite == true,
)

fun JellyfinBaseItemDto.toArtistDetail(
    profile: ServerProfile,
    albums: List<JellyfinBaseItemDto>,
): ArtistDetail {
    val artistModel = toArtist(profile)
    val mappedAlbums = albums.map { it.toAlbum(profile) }
    return ArtistDetail(
        id = artistModel.id,
        name = artistModel.name,
        coverArt = artistModel.coverArt,
        albumCount = if (artistModel.albumCount > 0) artistModel.albumCount else mappedAlbums.size,
        starred = artistModel.starred,
        albums = mappedAlbums,
    )
}

fun JellyfinBaseItemDto.toPlaylist(profile: ServerProfile): Playlist = Playlist(
    id = GlobalId.format(profile.id, id),
    name = name.ifBlank { "Unknown playlist" },
    songCount = childCount ?: recursiveItemCount ?: songCount ?: 0,
    durationSec = durationSeconds(),
    coverArt = primaryCoverArtId(profile.id),
)

fun JellyfinBaseItemDto.toPlaylistDetail(
    profile: ServerProfile,
    tracks: List<JellyfinBaseItemDto>,
): PlaylistDetail {
    val playlistModel = toPlaylist(profile)
    val mappedTracks = tracks.map { it.toTrack(profile) }
    return PlaylistDetail(
        id = playlistModel.id,
        name = playlistModel.name,
        songCount = if (playlistModel.songCount > 0) playlistModel.songCount else mappedTracks.size,
        durationSec = if (playlistModel.durationSec > 0) playlistModel.durationSec else mappedTracks.sumOf { it.durationSec },
        tracks = mappedTracks,
    )
}

fun JellyfinBaseItemDto.toGenre(): Genre = Genre(
    name = name.ifBlank { "Unknown" },
    songCount = songCount ?: recursiveItemCount ?: 0,
    albumCount = albumCount ?: childCount ?: 0,
)
