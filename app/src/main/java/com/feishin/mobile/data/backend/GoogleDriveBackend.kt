package com.feishin.mobile.data.backend

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.db.DriveAlbumRow
import com.feishin.mobile.data.db.DriveArtistRow
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.db.LocalFavoriteDao
import com.feishin.mobile.data.db.LocalFavoriteEntity
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.buildGoogleDriveFileUrl
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicBackend
import com.feishin.mobile.domain.repo.ScanStatus
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Single-account Google Drive [MusicBackend] adapter.
 *
 * Serves library browse, search, album/artist details, and local favorites offline-first from
 * Room ([DriveTrackDao], [LocalFavoriteDao]), delegates indexing to [DriveLibraryIndexer], and
 * resolves `drive-cover://` / `alt=media` URIs consistently with `BackendUrlResolver`.
 */
class GoogleDriveBackend(
    override val profile: ServerProfile,
    private val api: DriveApi,
    private val driveTrackDao: DriveTrackDao,
    private val localFavoriteDao: LocalFavoriteDao,
    private val indexer: DriveLibraryIndexer,
    private val json: Json,
) : MusicBackend {

    override val capabilities: BackendCapabilities = BackendCapabilities.forType(profile.type)

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Err(mapError(e), e)
        }
    }

    private fun mapError(cause: Throwable): String {
        if (cause is HttpException) {
            return when (cause.code()) {
                401 -> "Google Drive authorization expired or invalid."
                403 -> "Access denied for this Google Drive account."
                404 -> "Google Drive item not found (HTTP 404)."
                else -> "Google Drive error (HTTP ${cause.code()})."
            }
        }
        if (cause is SerializationException || cause is IllegalArgumentException) {
            return "Unexpected response from Google Drive."
        }
        return SubsonicAuth.userMessage(code = null, serverText = null, cause = cause)
    }

    override suspend fun ping(): Result<Unit> = call {
        api.getAbout(fields = "user(emailAddress,displayName)")
        Result.Ok(Unit)
    }

    override suspend fun serverInfo(): Result<ServerInfo> =
        Result.Ok(
            ServerInfo(
                apiVersion = "v3",
                product = "Google Drive",
                extensions = emptyList(),
            ),
        )

    override suspend fun startScan(): Result<Unit> = indexer.startScan(profile.id)

    override suspend fun scanStatus(): Result<ScanStatus> = indexer.scanStatus(profile.id)

    override suspend fun albumList(
        type: String,
        size: Int,
        offset: Int,
        genre: String?,
    ): Result<List<Album>> = call {
        val safeSize = size.coerceAtLeast(1)
        val safeOffset = offset.coerceAtLeast(0)
        val starredAlbums = starredTargetIds(KIND_ALBUM)
        val rows = when (type) {
            "recent" -> driveTrackDao.albumsRecentFromHistory(profile.id, limit = safeSize, offset = safeOffset)
            "frequent" -> driveTrackDao.albumsFrequentFromHistory(profile.id, limit = safeSize, offset = safeOffset)
            "newest" -> sliceWindow(
                driveTrackDao.albumsForProfile(profile.id).sortedWith(
                    compareByDescending<DriveAlbumRow> { it.year ?: Int.MIN_VALUE }
                        .thenBy { it.album.lowercase(Locale.ROOT) },
                ),
                safeOffset,
                safeSize,
            )
            "alphabeticalByArtist" -> sliceWindow(
                driveTrackDao.albumsForProfile(profile.id).sortedWith(
                    compareBy<DriveAlbumRow> { it.artist.lowercase(Locale.ROOT) }
                        .thenBy { it.album.lowercase(Locale.ROOT) },
                ),
                safeOffset,
                safeSize,
            )
            "random" -> sliceWindow(
                driveTrackDao.albumsForProfile(profile.id).shuffled(),
                safeOffset,
                safeSize,
            )
            "byGenre" -> {
                val targetGenre = genre?.trim().orEmpty()
                val all = driveTrackDao.albumsForProfile(profile.id)
                val filtered = if (targetGenre.isEmpty()) {
                    all
                } else {
                    all.filter { it.genre.equals(targetGenre, ignoreCase = true) }
                }
                sliceWindow(filtered, safeOffset, safeSize)
            }
            "starred" -> {
                val starred = (starred() as? Result.Ok)?.value?.albums.orEmpty()
                return@call Result.Ok(sliceWindow(starred, safeOffset, safeSize))
            }
            else -> sliceWindow(
                driveTrackDao.albumsForProfile(profile.id),
                safeOffset,
                safeSize,
            )
        }
        Result.Ok(rows.map { it.toDomainAlbum(starredAlbums) })
    }

    override suspend fun artists(): Result<List<Artist>> = call {
        val starredArtists = starredTargetIds(KIND_ARTIST)
        Result.Ok(driveTrackDao.artistsForProfile(profile.id).map { it.toDomainArtist(starredArtists) })
    }

    override suspend fun genres(): Result<List<Genre>> = call {
        Result.Ok(
            driveTrackDao.genresForProfile(profile.id).map { row ->
                Genre(name = row.name, songCount = row.songCount, albumCount = row.albumCount)
            },
        )
    }

    override suspend fun playlists(): Result<List<Playlist>> = Result.Ok(emptyList())

    suspend fun allSongs(size: Int = 500, offset: Int = 0): Result<List<Track>> = call {
        val starredTracks = starredTargetIds(KIND_TRACK)
        val rows = driveTrackDao.allSongsForProfile(
            profileId = profile.id,
            limit = size.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0),
        )
        Result.Ok(rows.map { it.toDomainTrack(starredTracks) })
    }

    suspend fun getSong(id: String): Result<Track> = call {
        val songId = normalizeSongId(id)
        val row = driveTrackDao.trackById(songId) ?: return@call Result.Err("Track not found")
        val starred = localFavoriteDao.isStarred(row.songId)
        Result.Ok(row.toDomainTrack(starred = starred))
    }

    override suspend fun search(
        query: String,
        artistCount: Int,
        albumCount: Int,
        songCount: Int,
    ): Result<SearchResults> = call {
        val q = query.trim()
        if (q.isEmpty()) return@call Result.Ok(SearchResults())
        val starredArtists = starredTargetIds(KIND_ARTIST)
        val starredAlbums = starredTargetIds(KIND_ALBUM)
        val starredTracks = starredTargetIds(KIND_TRACK)

        val artists = if (artistCount > 0) {
            driveTrackDao.searchArtists(profile.id, q, artistCount).map { it.toDomainArtist(starredArtists) }
        } else {
            emptyList()
        }
        val albums = if (albumCount > 0) {
            driveTrackDao.searchAlbums(profile.id, q, albumCount).map { it.toDomainAlbum(starredAlbums) }
        } else {
            emptyList()
        }
        val tracks = if (songCount > 0) {
            driveTrackDao.searchTracks(profile.id, q, songCount).map { it.toDomainTrack(starredTracks) }
        } else {
            emptyList()
        }
        Result.Ok(SearchResults(artists = artists, albums = albums, tracks = tracks, playlists = emptyList()))
    }

    override suspend fun album(id: String): Result<AlbumDetail> = call {
        val albumKey = normalizeKeyId(id)
        val rows = driveTrackDao.tracksInAlbum(albumKey)
            .ifEmpty { driveTrackDao.tracksInAlbum(id) }
        if (rows.isEmpty()) return@call Result.Err("Album not found")

        val resolvedKey = rows.first().albumKey
        val starredTracks = starredTargetIds(KIND_TRACK)
        val albumStarred = localFavoriteDao.isStarred(resolvedKey)
        val first = rows.first()
        val albumArtist = rows.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: first.artist
        Result.Ok(
            AlbumDetail(
                id = resolvedKey,
                name = first.album,
                artist = albumArtist,
                artistId = first.artistKey,
                coverArt = rows.firstNotNullOfOrNull { it.coverArtId },
                songCount = rows.size,
                durationSec = rows.sumOf { it.durationSec },
                year = rows.firstNotNullOfOrNull { it.year },
                genre = rows.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) },
                starred = albumStarred,
                tracks = rows.map { it.toDomainTrack(starredTracks) },
            ),
        )
    }

    override suspend fun artist(id: String): Result<ArtistDetail> = call {
        val artistKey = normalizeKeyId(id)
        val rows = driveTrackDao.tracksByArtist(artistKey)
            .ifEmpty { driveTrackDao.tracksByArtist(id) }
        if (rows.isEmpty()) return@call Result.Err("Artist not found")

        val resolvedKey = rows.first().artistKey
        val artistStarred = localFavoriteDao.isStarred(resolvedKey)
        val starredAlbums = starredTargetIds(KIND_ALBUM)
        val artistName = rows.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: rows.first().artist

        val albums = rows.groupBy { it.albumKey }.values.map { group ->
            val head = group.first()
            val albumKey = head.albumKey
            Album(
                id = albumKey,
                name = head.album,
                artist = group.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: head.artist,
                artistId = resolvedKey,
                coverArt = group.firstNotNullOfOrNull { it.coverArtId },
                songCount = group.size,
                durationSec = group.sumOf { it.durationSec },
                playCount = 0L,
                year = group.firstNotNullOfOrNull { it.year },
                genre = group.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) },
                starred = albumKey in starredAlbums,
                sourceProfileId = profile.id,
                sourceLabel = profile.name,
            )
        }.sortedWith(
            compareByDescending<Album> { it.year ?: Int.MIN_VALUE }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )

        Result.Ok(
            ArtistDetail(
                id = resolvedKey,
                name = artistName,
                coverArt = albums.firstNotNullOfOrNull { it.coverArt },
                albumCount = albums.size,
                starred = artistStarred,
                albums = albums,
            ),
        )
    }

    override suspend fun artistInfo(id: String): Result<ArtistInfo> = Result.Ok(ArtistInfo())

    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = call {
        val starredTracks = starredTargetIds(KIND_TRACK)
        val rows = driveTrackDao.randomTracks(
            profileId = profile.id,
            limit = size.coerceAtLeast(1),
            genre = genre?.trim()?.takeIf { it.isNotEmpty() },
        )
        Result.Ok(rows.map { it.toDomainTrack(starredTracks) })
    }

    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = call {
        val needle = artist.trim()
        if (needle.isEmpty()) return@call Result.Ok(emptyList())
        val limit = count.coerceAtLeast(1)
        val starredTracks = starredTargetIds(KIND_TRACK)
        val candidates = driveTrackDao.searchTracks(profile.id, needle, limit = (limit * 3).coerceAtLeast(30))
        val exact = candidates.filter {
            it.artist.equals(needle, ignoreCase = true) || it.albumArtist.equals(needle, ignoreCase = true)
        }
        val selected = (if (exact.isNotEmpty()) exact else candidates).take(limit)
        Result.Ok(selected.map { it.toDomainTrack(starredTracks) })
    }

    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> =
        Result.Ok(emptyList())

    override suspend fun playlist(id: String): Result<PlaylistDetail> =
        Result.Err("Server playlists are not supported on Google Drive")

    override suspend fun starred(): Result<StarredCollection> = call {
        val rows = localFavoriteDao.byProfile(profile.id)
        val artists = ArrayList<Artist>()
        val albums = ArrayList<Album>()
        val tracks = ArrayList<Track>()
        for (row in rows) {
            when (row.kind) {
                KIND_ARTIST -> runCatching { json.decodeFromString(Artist.serializer(), row.payloadJson) }
                    .getOrNull()?.let { artists.add(it.copy(starred = true)) }
                KIND_ALBUM -> runCatching { json.decodeFromString(Album.serializer(), row.payloadJson) }
                    .getOrNull()?.let {
                        albums.add(it.copy(starred = true, sourceProfileId = profile.id, sourceLabel = profile.name))
                    }
                KIND_TRACK -> runCatching { json.decodeFromString(Track.serializer(), row.payloadJson) }
                    .getOrNull()?.let {
                        tracks.add(it.copy(starred = true, sourceProfileId = profile.id, sourceLabel = profile.name))
                    }
            }
        }
        Result.Ok(StarredCollection(artists = artists, albums = albums, tracks = tracks))
    }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = call {
        val targetId = normalizeSongId(id)
        if (!starred) {
            localFavoriteDao.delete(targetId)
            return@call Result.Ok(Unit)
        }
        val row = driveTrackDao.trackById(targetId) ?: return@call Result.Err("Track not found")
        val domain = row.toDomainTrack(starred = true)
        localFavoriteDao.upsert(
            LocalFavoriteEntity(
                targetId = domain.id,
                kind = KIND_TRACK,
                profileId = profile.id,
                payloadJson = json.encodeToString(Track.serializer(), domain),
            ),
        )
        Result.Ok(Unit)
    }

    suspend fun unstarTrack(id: String): Result<Unit> = starTrack(id, starred = false)

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = call {
        val targetKey = normalizeKeyId(id)
        if (!starred) {
            localFavoriteDao.delete(targetKey)
            return@call Result.Ok(Unit)
        }
        val rows = driveTrackDao.tracksInAlbum(targetKey)
            .ifEmpty { driveTrackDao.tracksInAlbum(id) }
        if (rows.isEmpty()) return@call Result.Err("Album not found")
        val head = rows.first()
        val album = Album(
            id = head.albumKey,
            name = head.album,
            artist = rows.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: head.artist,
            artistId = head.artistKey,
            coverArt = rows.firstNotNullOfOrNull { it.coverArtId },
            songCount = rows.size,
            durationSec = rows.sumOf { it.durationSec },
            playCount = 0L,
            year = rows.firstNotNullOfOrNull { it.year },
            genre = rows.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) },
            starred = true,
            sourceProfileId = profile.id,
            sourceLabel = profile.name,
        )
        localFavoriteDao.upsert(
            LocalFavoriteEntity(
                targetId = album.id,
                kind = KIND_ALBUM,
                profileId = profile.id,
                payloadJson = json.encodeToString(Album.serializer(), album),
            ),
        )
        Result.Ok(Unit)
    }

    suspend fun unstarAlbum(id: String): Result<Unit> = starAlbum(id, starred = false)

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = call {
        val targetKey = normalizeKeyId(id)
        if (!starred) {
            localFavoriteDao.delete(targetKey)
            return@call Result.Ok(Unit)
        }
        val rows = driveTrackDao.tracksByArtist(targetKey)
            .ifEmpty { driveTrackDao.tracksByArtist(id) }
        if (rows.isEmpty()) return@call Result.Err("Artist not found")
        val head = rows.first()
        val artist = Artist(
            id = head.artistKey,
            name = rows.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: head.artist,
            coverArt = rows.firstNotNullOfOrNull { it.coverArtId },
            albumCount = rows.mapTo(HashSet()) { it.albumKey }.size,
            starred = true,
        )
        localFavoriteDao.upsert(
            LocalFavoriteEntity(
                targetId = artist.id,
                kind = KIND_ARTIST,
                profileId = profile.id,
                payloadJson = json.encodeToString(Artist.serializer(), artist),
            ),
        )
        Result.Ok(Unit)
    }

    suspend fun unstarArtist(id: String): Result<Unit> = starArtist(id, starred = false)

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> =
        Result.Err("Server playlists are not supported on Google Drive")

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        Result.Err("Server playlists are not supported on Google Drive")

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> =
        Result.Err("Server playlists are not supported on Google Drive")

    override suspend fun deletePlaylist(id: String): Result<Unit> =
        Result.Err("Server playlists are not supported on Google Drive")

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = Result.Ok(Unit)

    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = Result.Ok(Unit)

    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> =
        Result.Ok(SyncedLyrics.EMPTY)

    override suspend fun lyrics(artist: String, title: String): Result<String> = Result.Ok("")

    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String {
        if (track.isLocal) return track.localUri.orEmpty()
        return buildGoogleDriveFileUrl(GlobalId.rawId(track.id))
    }

    override fun coverArtUrl(coverArtId: String?, size: Int): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (
            coverArtId.startsWith("drive-cover://", ignoreCase = true) ||
            coverArtId.startsWith("http://", ignoreCase = true) ||
            coverArtId.startsWith("https://", ignoreCase = true) ||
            coverArtId.startsWith("content://", ignoreCase = true) ||
            coverArtId.startsWith("file://", ignoreCase = true)
        ) {
            return coverArtId
        }
        val rawId = GlobalId.rawId(coverArtId)
        if (rawId.isBlank()) return null
        return if (rawId.startsWith("cov:")) {
            "drive-cover://${profile.id}/${rawId.removePrefix("cov:")}"
        } else {
            buildGoogleDriveFileUrl(rawId)
        }
    }

    override fun downloadUrl(track: Track): String {
        if (track.isLocal) return track.localUri.orEmpty()
        return buildGoogleDriveFileUrl(GlobalId.rawId(track.id))
    }

    override suspend fun readCachedAlbumList(type: String, offset: Int, genre: String?): List<Album>? =
        (albumList(type = type, size = 50, offset = offset, genre = genre) as? Result.Ok)?.value

    override suspend fun readCachedArtists(): List<Artist>? =
        (artists() as? Result.Ok)?.value

    override suspend fun readCachedGenres(): List<Genre>? =
        (genres() as? Result.Ok)?.value

    override suspend fun readCachedPlaylists(): List<Playlist>? = emptyList()

    override suspend fun readCachedStarred(): StarredCollection? =
        (starred() as? Result.Ok)?.value

    private suspend fun starredTargetIds(kind: String): Set<String> =
        localFavoriteDao.byProfileAndKind(profile.id, kind).mapTo(HashSet()) { it.targetId }

    private fun normalizeSongId(id: String): String {
        val raw = GlobalId.rawId(id)
        return GlobalId.format(profile.id, raw)
    }

    private fun normalizeKeyId(id: String): String {
        val raw = GlobalId.rawId(id)
        return GlobalId.format(profile.id, raw)
    }

    private fun DriveAlbumRow.toDomainAlbum(starredIds: Set<String>): Album = Album(
        id = albumKey,
        name = album,
        artist = artist,
        artistId = artistKey,
        coverArt = coverArtId,
        songCount = songCount,
        durationSec = durationSec,
        playCount = playCount,
        year = year,
        genre = genre,
        starred = albumKey in starredIds,
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
    )

    private fun DriveArtistRow.toDomainArtist(starredIds: Set<String>): Artist = Artist(
        id = artistKey,
        name = name,
        coverArt = coverArtId,
        albumCount = albumCount,
        starred = artistKey in starredIds,
    )

    private fun DriveTrackEntity.toDomainTrack(starredIds: Set<String>): Track =
        toDomainTrack(starred = songId in starredIds)

    private fun DriveTrackEntity.toDomainTrack(starred: Boolean): Track = Track(
        id = songId,
        title = title,
        album = album,
        albumId = albumKey,
        artist = artist,
        artistId = artistKey,
        coverArt = coverArtId,
        durationSec = durationSec,
        trackNo = trackNo,
        year = year,
        genre = genre,
        suffix = suffix,
        starred = starred,
        sizeBytes = sizeBytes,
        bitRate = normalizeKbps(bitRate),
        path = parentFolderPath,
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
    )

    companion object {
        const val KIND_TRACK = "TRACK"
        const val KIND_ALBUM = "ALBUM"
        const val KIND_ARTIST = "ARTIST"

        internal fun normalizeKbps(raw: Int?): Int? =
            raw?.takeIf { it > 0 }?.let { if (it > 10_000) it / 1000 else it }

        private fun <T> sliceWindow(items: List<T>, offset: Int, size: Int): List<T> {
            if (offset >= items.size) return emptyList()
            val end = (offset.toLong() + size.toLong()).coerceAtMost(items.size.toLong()).toInt()
            return items.subList(offset, end)
        }
    }
}
