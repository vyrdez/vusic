package com.feishin.mobile.data.backend

import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.PlaylistBatch
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.mapper.toDomain
import com.feishin.mobile.data.model.ArtistInfo2Dto
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicBackend
import com.feishin.mobile.domain.repo.ScanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Single-server Subsonic / Navidrome / OpenSubsonic [MusicBackend] adapter.
 *
 * Scopes all Room metadata cache entries under `{profileId}:` via [MetadataCacheKeys.scoped],
 * wraps every returned remote entity ID with [GlobalId.format], sets `sourceProfileId` /
 * `sourceLabel` on [Track] and [Album], and unwraps [GlobalId.rawId] before calling [SubsonicApi].
 */
class SubsonicBackend(
    override val profile: ServerProfile,
    private val api: SubsonicApi,
    private val cache: MetadataCache,
    private val refresh: LibraryRefresh,
    private val network: NetworkMonitor,
    private val settings: SettingsStore,
    private val offlineSync: dagger.Lazy<OfflineSyncStore>,
    private val playlistEvents: dagger.Lazy<PlaylistEvents>,
    private val cacheScope: CoroutineScope,
) : MusicBackend {

    override val capabilities: BackendCapabilities = BackendCapabilities.forType(profile.type)

    private val subsonicCreds: ProfileCredentials.Subsonic
        get() = profile.credentials as? ProfileCredentials.Subsonic
            ?: throw IllegalStateException("Profile ${profile.id} does not hold Subsonic credentials")

    private suspend fun offline(): Boolean {
        val s = runCatching { settings.settingsNow() }.getOrDefault(null) ?: return true
        val auto = s.autoOffline && !network.isOnline.value
        return s.offlineMode || auto || !network.isOnline.value
    }

    private fun scopedKey(logicalKey: String): String =
        MetadataCacheKeys.scoped(profile.id, logicalKey)

    private fun albumListLogicalKey(type: String, offset: Int, genre: String?): String =
        if (genre.isNullOrBlank()) MetadataCacheKeys.albums(type, offset)
        else "${MetadataCacheKeys.albums(type, offset)}:$genre"

    private suspend fun <T> cached(
        logicalKey: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        val storageKey = scopedKey(logicalKey)
        if (offline()) {
            val hit = cache.read(storageKey, serializer)
            return if (hit != null) Result.Ok(hit) else Result.Err("You're offline")
        }
        val forced = refresh.policy.consumeForced(storageKey, logicalKey)
        val hit = cache.read(storageKey, serializer)
        if (!forced && hit != null) {
            revalidate(storageKey, logicalKey, serializer, fetch)
            return Result.Ok(hit)
        }
        val result = if (hit != null) {
            refresh.beginRefresh()
            try {
                fetch()
            } finally {
                refresh.endRefresh()
            }
        } else {
            fetch()
        }
        return when (result) {
            is Result.Ok -> {
                cache.write(storageKey, serializer, result.value)
                refresh.emitRefreshed(logicalKey)
                result
            }
            is Result.Err -> {
                if (hit != null) {
                    refresh.emitStale(logicalKey)
                    Result.Ok(hit)
                } else {
                    result
                }
            }
        }
    }

    private fun <T> revalidate(
        storageKey: String,
        logicalKey: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> Result<T>,
    ) {
        if (!refresh.policy.shouldRefresh(storageKey)) return
        refresh.beginRefresh()
        cacheScope.launch {
            try {
                val result = fetch()
                if (result is Result.Ok) {
                    cache.write(storageKey, serializer, result.value)
                    refresh.emitRefreshed(logicalKey)
                } else {
                    refresh.emitStale(logicalKey)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                refresh.emitStale(logicalKey)
            } finally {
                refresh.endRefresh()
            }
        }
    }

    private fun <T> unwrap(status: String, error: String?, get: () -> T): Result<T> =
        if (status == "ok") Result.Ok(get())
        else Result.Err(SubsonicAuth.userMessage(null, error, null).ifBlank { "Server error" })

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Err(SubsonicAuth.userMessage(null, null, e), e)
        }
    }

    private fun scopeId(raw: String): String =
        if (raw.isBlank()) raw else GlobalId.format(profile.id, raw)

    private fun scopeNullableId(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }?.let { GlobalId.format(profile.id, it) }

    private fun scopeCoverArt(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (
            raw.startsWith("content://") ||
            raw.startsWith("file://") ||
            raw.startsWith("http://") ||
            raw.startsWith("https://")
        ) {
            return raw
        }
        return GlobalId.format(profile.id, raw)
    }

    private fun Album.scoped(): Album = copy(
        id = scopeId(id),
        artistId = scopeNullableId(artistId),
        coverArt = scopeCoverArt(coverArt),
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
    )

    private fun Track.scoped(): Track = copy(
        id = scopeId(id),
        albumId = scopeNullableId(albumId),
        artistId = scopeNullableId(artistId),
        coverArt = scopeCoverArt(coverArt),
        sourceProfileId = profile.id,
        sourceLabel = profile.name,
    )

    private fun AlbumDetail.scoped(): AlbumDetail = copy(
        id = scopeId(id),
        artistId = scopeNullableId(artistId),
        coverArt = scopeCoverArt(coverArt),
        tracks = tracks.map { it.scoped() },
    )

    private fun Artist.scoped(): Artist = copy(
        id = scopeId(id),
        coverArt = scopeCoverArt(coverArt),
    )

    private fun ArtistDetail.scoped(): ArtistDetail = copy(
        id = scopeId(id),
        coverArt = scopeCoverArt(coverArt),
        albums = albums.map { it.scoped() },
    )

    private fun Playlist.scoped(): Playlist = copy(
        id = scopeId(id),
        coverArt = scopeCoverArt(coverArt),
    )

    private fun PlaylistDetail.scoped(): PlaylistDetail = copy(
        id = scopeId(id),
        tracks = tracks.map { it.scoped() },
    )

    private fun ArtistInfo.scoped(): ArtistInfo = copy(
        similarArtists = similarArtists.map { it.scoped() },
    )

    override suspend fun ping(): Result<Unit> = call {
        val r = api.ping().response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> =
        cached(albumListLogicalKey(type, offset, genre), ListSerializer(Album.serializer())) {
            call {
                val r = api.getAlbumList(type, size, offset, genre).response
                unwrap(r.status, r.error?.message) {
                    val direct = r.albumList?.album.orEmpty()
                    val fallback = r.albumList2?.album.orEmpty()
                    (direct + fallback).distinctBy { it.id }.map { it.toDomain().scoped() }
                }
            }
        }

    override suspend fun artists(): Result<List<Artist>> =
        cached(MetadataCacheKeys.ARTISTS, ListSerializer(Artist.serializer())) {
            call {
                val r = api.getArtists().response
                unwrap(r.status, r.error?.message) {
                    r.artists?.index.orEmpty().flatMap { it.artist }.map { it.toDomain().scoped() }
                }
            }
        }

    override suspend fun genres(): Result<List<Genre>> =
        cached(MetadataCacheKeys.GENRES, ListSerializer(Genre.serializer())) {
            call {
                val r = api.getGenres().response
                unwrap(r.status, r.error?.message) { r.genres?.genre.orEmpty().map { it.toDomain() } }
            }
        }

    override suspend fun playlists(): Result<List<Playlist>> =
        cached(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer())) {
            call {
                val r = api.getPlaylists().response
                unwrap(r.status, r.error?.message) { r.playlists?.playlist.orEmpty().map { it.toDomain().scoped() } }
            }
        }

    override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults> =
        call {
            val r = api.search3(query, artistCount, albumCount, songCount).response
            unwrap(r.status, r.error?.message) {
                SearchResults(
                    artists = r.searchResult3?.artist.orEmpty().map { it.toDomain().scoped() },
                    albums = r.searchResult3?.album.orEmpty().map { it.toDomain().scoped() },
                    tracks = r.searchResult3?.song.orEmpty().map { it.toDomain().scoped() },
                    playlists = r.searchResult3?.playlist.orEmpty().map { it.toDomain().scoped() },
                )
            }
        }

    override suspend fun serverInfo(): Result<ServerInfo> = call {
        val ping = api.ping().response
        if (ping.status != "ok") {
            return@call Result.Err(ping.error?.message?.ifBlank { null } ?: "Server error")
        }
        val extensions = runCatching {
            api.getOpenSubsonicExtensions().response.openSubsonicExtensions.orEmpty()
                .map { it.name }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        val product = when {
            extensions.any { it.equals("transcode", true) || it.equals("format", true) } -> "Navidrome"
            extensions.isNotEmpty() -> "OpenSubsonic"
            else -> "Subsonic-compatible"
        }
        Result.Ok(
            ServerInfo(
                apiVersion = ping.version.ifBlank { SubsonicAuth.API_VERSION },
                product = product,
                extensions = extensions,
            ),
        )
    }

    override suspend fun startScan(): Result<Unit> = call {
        val r = api.startScan().response
        if (BuildConfig.DEBUG) {
            VLog.d("ScanStatus", "startScan[${profile.id}]: status=${r.status} error=${r.error}")
        }
        if (r.status == "ok") {
            Result.Ok(Unit)
        } else {
            val code = r.error?.code
            val msg = r.error?.message
            val errText = if (code == 50 || msg?.contains("authorized", ignoreCase = true) == true) {
                "Only admin accounts can scan the library"
            } else {
                SubsonicAuth.userMessage(code, msg, null).ifBlank { "Server error" }
            }
            Result.Err(errText)
        }
    }

    override suspend fun scanStatus(): Result<ScanStatus> = call {
        val r = api.getScanStatus().response
        val s = r.scanStatus
        if (BuildConfig.DEBUG) {
            VLog.d(
                "ScanStatus",
                "raw[${profile.id}]: status=${r.status} scanning=${s?.scanning} count=${s?.count} folderCount=${s?.folderCount} lastScan=${s?.lastScan} error=${r.error}",
            )
        }
        unwrap(r.status, r.error?.message) {
            ScanStatus(
                scanning = s?.scanning == true,
                count = s?.count ?: 0,
                folderCount = s?.folderCount,
                lastScan = s?.lastScan,
            )
        }
    }

    override suspend fun album(id: String): Result<AlbumDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.album(globalId), AlbumDetail.serializer()) {
            call {
                val r = api.getAlbum(rawId).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.album).toDomain().scoped() }
            }
        }
    }

    override suspend fun artist(id: String): Result<ArtistDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.artist(globalId), ArtistDetail.serializer()) {
            call {
                val r = api.getArtist(rawId).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.artist).toDomain().scoped() }
            }
        }
    }

    override suspend fun artistInfo(id: String): Result<ArtistInfo> = call {
        val rawId = GlobalId.rawId(id)
        val r = runCatching { api.getArtistInfo2(rawId).response }.getOrNull()
            ?: return@call Result.Err("Artist info not available")
        unwrap(r.status, r.error?.message) { (r.artistInfo2 ?: ArtistInfo2Dto()).toDomain().scoped() }
    }

    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = call {
        val r = api.getRandomSongs(size, genre).response
        unwrap(r.status, r.error?.message) { r.randomSongs?.song.orEmpty().map { it.toDomain().scoped() } }
    }

    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = call {
        val r = runCatching { api.getTopSongs(artist, count).response }.getOrNull()
            ?: return@call Result.Err("Top songs not available")
        unwrap(r.status, r.error?.message) {
            ((r.topSongs?.track ?: emptyList()) + (r.topSongs?.song ?: emptyList()))
                .distinctBy { it.id }.map { it.toDomain().scoped() }
        }
    }

    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> = call {
        val seed = (songId ?: artistId)?.let { GlobalId.rawId(it) }
        val r = runCatching { api.getSimilarSongs2(seed, count).response }.getOrNull()
            ?: return@call Result.Err("Similar songs not available")
        unwrap(r.status, r.error?.message) { r.similarSongs2?.song.orEmpty().map { it.toDomain().scoped() } }
    }

    override suspend fun playlist(id: String): Result<PlaylistDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.playlist(globalId), PlaylistDetail.serializer()) {
            call {
                val r = api.getPlaylist(rawId).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.playlist).toDomain().scoped() }
            }
        }
    }

    override suspend fun cachedPlaylist(id: String): PlaylistDetail? {
        val globalId = scopeId(id)
        return cache.read(scopedKey(MetadataCacheKeys.playlist(globalId)), PlaylistDetail.serializer())
    }

    override suspend fun starred(): Result<StarredCollection> =
        cached(MetadataCacheKeys.STARRED, StarredCollection.serializer()) {
            call {
                val r = try {
                    api.getStarred2().response
                } catch (_: Exception) {
                    api.getStarred().response
                }
                unwrap(r.status, r.error?.message) {
                    StarredCollection(
                        artists = ((r.starred2?.artist ?: r.starred?.artist).orEmpty()).map { it.toDomain().scoped() },
                        albums = ((r.starred2?.album ?: r.starred?.album).orEmpty()).map { it.toDomain().scoped() },
                        tracks = ((r.starred2?.song ?: r.starred?.song).orEmpty()).map { it.toDomain().scoped() },
                    )
                }
            }
        }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStar(globalId, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(ids = listOf(rawId)).response else api.unstar(ids = listOf(rawId)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStarAlbum(globalId, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(albumIds = listOf(rawId)).response else api.unstar(albumIds = listOf(rawId)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStarArtist(globalId, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(artistIds = listOf(rawId)).response else api.unstar(artistIds = listOf(rawId)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> = call {
        val rawTrackIds = trackIds.map { GlobalId.rawId(it) }
        val r = api.createPlaylist(name, PlaylistBatch.firstCreateChunk(rawTrackIds)).response
        val createdRawId = r.playlist?.id?.takeIf { it.isNotBlank() }?.let { GlobalId.rawId(it) }
            ?: return@call Result.Err(r.error?.message ?: "Create failed")
        for (batch in PlaylistBatch.remainingChunks(rawTrackIds)) {
            val r2 = api.updatePlaylist(createdRawId, songIdToAdd = batch).response
            when (val res = unwrap(r2.status, r2.error?.message) { }) {
                is Result.Ok -> Unit
                is Result.Err -> return@call res
            }
        }
        Result.Ok(GlobalId.format(profile.id, createdRawId))
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = call {
        val rawPlaylistId = GlobalId.rawId(playlistId)
        val rawTrackIds = trackIds.map { GlobalId.rawId(it) }
        for (batch in PlaylistBatch.chunks(rawTrackIds)) {
            val r = api.updatePlaylist(rawPlaylistId, songIdToAdd = batch).response
            when (val res = unwrap(r.status, r.error?.message) { }) {
                is Result.Ok -> Unit
                is Result.Err -> return@call res
            }
        }
        Result.Ok(Unit)
    }

    override suspend fun addToPlaylistOptimistic(
        playlistId: String,
        tracks: List<Track>,
        playlistName: String,
        onRollback: (() -> Unit)?,
    ): Result<Unit> {
        val globalPlaylistId = scopeId(playlistId)
        val detailKey = scopedKey(MetadataCacheKeys.playlist(globalPlaylistId))
        val playlistsKey = scopedKey(MetadataCacheKeys.PLAYLISTS)

        val originalDetail = cache.read(detailKey, PlaylistDetail.serializer())
        val originalPlaylists = cache.read(playlistsKey, ListSerializer(Playlist.serializer()))

        if (originalDetail != null) {
            val updatedDetail = originalDetail.copy(
                songCount = originalDetail.songCount + tracks.size,
                durationSec = originalDetail.durationSec + tracks.sumOf { it.durationSec },
                tracks = originalDetail.tracks + tracks,
            )
            cache.write(detailKey, PlaylistDetail.serializer(), updatedDetail)
        }
        if (originalPlaylists != null) {
            val updatedPlaylists = originalPlaylists.map { p ->
                if (p.id == globalPlaylistId) {
                    p.copy(
                        songCount = p.songCount + tracks.size,
                        durationSec = p.durationSec + tracks.sumOf { it.durationSec },
                    )
                } else p
            }
            cache.write(playlistsKey, ListSerializer(Playlist.serializer()), updatedPlaylists)
        }
        cacheScope.launch {
            val res = addToPlaylist(globalPlaylistId, tracks.map { it.id })
            if (res is Result.Err) {
                if (originalDetail != null) {
                    cache.write(detailKey, PlaylistDetail.serializer(), originalDetail)
                } else {
                    cache.remove(detailKey)
                }
                if (originalPlaylists != null) {
                    cache.write(playlistsKey, ListSerializer(Playlist.serializer()), originalPlaylists)
                }
                playlistEvents.get().emit(PlaylistEvent.TracksAdded(globalPlaylistId))
                onRollback?.invoke()
            } else {
                playlistEvents.get().emit(PlaylistEvent.TracksAdded(globalPlaylistId))
            }
        }
        return Result.Ok(Unit)
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = call {
        val r = api.updatePlaylist(GlobalId.rawId(playlistId), songIndexToRemove = indexes).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun deletePlaylist(id: String): Result<Unit> = call {
        val r = api.deletePlaylist(GlobalId.rawId(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueScrobble(globalId, submission, System.currentTimeMillis())
            return@call Result.Ok(Unit)
        }
        val r = api.scrobble(rawId, time = System.currentTimeMillis(), submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = call {
        val r = api.scrobble(GlobalId.rawId(id), time = timeMs, submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun lyrics(artist: String, title: String): Result<String> = call {
        val r = api.getLyrics(artist, title).response
        unwrap(r.status, r.error?.message) { r.lyrics?.value ?: "" }
    }

    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawId = GlobalId.rawId(id)
                val structured = runCatching { api.getLyricsBySongId(rawId).response }.getOrNull()
                val synced = structured?.lyricsList?.syncedLyrics
                if (synced != null && synced.line.isNotEmpty()) {
                    return@runCatching Result.Ok(
                        SyncedLyrics.parse(synced.line.map { it.start to it.value }, synced.offset),
                    )
                }
                val plain = structured?.lyricsList?.plainLyrics?.value
                    ?: structured?.lyrics?.value
                    ?: api.getLyrics(artist, title).response.lyrics?.value
                Result.Ok(SyncedLyrics.plain(plain.orEmpty()))
            }.getOrElse { Result.Err(it.message ?: "lyrics unavailable", it) }
        }

    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String {
        val c = subsonicCreds
        val query = linkedMapOf("id" to GlobalId.rawId(track.id))
        if (maxBitrate > 0) query["maxBitRate"] = maxBitrate.toString()
        if (format != "raw") query["format"] = format
        query.putAll(SubsonicAuth.authQuery(c.username, c.password))
        return SubsonicAuth.buildUrl(profile.url, "rest/stream.view", query)
    }

    override fun coverArtUrl(coverArtId: String?, size: Int): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (
            coverArtId.startsWith("content://") ||
            coverArtId.startsWith("file://") ||
            coverArtId.startsWith("http://") ||
            coverArtId.startsWith("https://")
        ) {
            return coverArtId
        }
        val c = subsonicCreds
        return CoverArtUrlProvider.build(profile.url, c.username, c.password, GlobalId.rawId(coverArtId), size)
    }

    override fun downloadUrl(track: Track): String {
        val c = subsonicCreds
        val query = linkedMapOf("id" to GlobalId.rawId(track.id))
        query.putAll(SubsonicAuth.authQuery(c.username, c.password))
        return SubsonicAuth.buildUrl(profile.url, "rest/download.view", query)
    }

    override suspend fun readCachedAlbumList(type: String, offset: Int, genre: String?): List<Album>? =
        cache.read(scopedKey(albumListLogicalKey(type, offset, genre)), ListSerializer(Album.serializer()))

    override suspend fun readCachedArtists(): List<Artist>? =
        cache.read(scopedKey(MetadataCacheKeys.ARTISTS), ListSerializer(Artist.serializer()))

    override suspend fun readCachedGenres(): List<Genre>? =
        cache.read(scopedKey(MetadataCacheKeys.GENRES), ListSerializer(Genre.serializer()))

    override suspend fun readCachedPlaylists(): List<Playlist>? =
        cache.read(scopedKey(MetadataCacheKeys.PLAYLISTS), ListSerializer(Playlist.serializer()))

    override suspend fun readCachedStarred(): StarredCollection? =
        cache.read(scopedKey(MetadataCacheKeys.STARRED), StarredCollection.serializer())
}
