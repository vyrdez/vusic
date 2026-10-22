package com.feishin.mobile.data.repo

import android.content.Context
import android.net.Uri
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.mapper.toDomain
import com.feishin.mobile.data.model.ArtistInfo2Dto
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ScanStatus
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubsonicApi,
    private val creds: CredentialsStore,
    private val auth: AuthInterceptor,
    private val cache: com.feishin.mobile.data.cache.MetadataCache,
    private val refresh: com.feishin.mobile.data.cache.LibraryRefresh,
    private val network: com.feishin.mobile.data.network.NetworkMonitor,
    private val settings: com.feishin.mobile.data.settings.SettingsStore,
    // Lazy breaks the cycle: the sync store replays through this repository.
    private val offlineSync: dagger.Lazy<com.feishin.mobile.data.download.OfflineSyncStore>,
    private val playlistEvents: dagger.Lazy<com.feishin.mobile.data.favorites.PlaylistEvents>,
) : MusicDataRepository {
    /** Background revalidations outlive any one screen. */
    private val cacheScope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() + Dispatchers.IO + com.feishin.mobile.core.common.CrashGuard.handler,
    )

    private fun base(): String {
        val c = creds.state.value ?: throw IllegalStateException("Not logged in")
        // normalizeServerUrl keeps any path prefix and strips trailing slashes;
        // the /rest/ segment has to be added here, or endpoints concatenate
        // straight onto the host (https://hostdownload.view).
        return SubsonicAuth.normalizeServerUrl(c.serverUrl).trimEnd('/') + "/rest/"
    }

    /** True when the app should not attempt network reads at all. */
    private suspend fun offline(): Boolean {
        val s = runCatching { settings.settingsNow() }.getOrDefault(null) ?: return true
        val auto = s.autoOffline && !network.isOnline.value
        return s.offlineMode || auto || !network.isOnline.value
    }

    /**
     * Stale-while-revalidate read.
     *
     *  - A stored payload is served IMMEDIATELY, whether the network is up or
     *    not: opening a tab never waits for a request when there is something
     *    to show. A background revalidation follows (throttled per key, see
     *    [com.feishin.mobile.core.common.CachePolicy]).
     *  - No stored payload: the read blocks on the network (the only case with
     *    a spinner), and a timeout surfaces as an error with Retry.
     *  - A failed refresh keeps the old payload and never becomes an error
     *    screen; a failed *first* load still reports the error.
     *  - Pull-to-refresh and post-edit reads bypass the cache via
     *    [LibraryRefresh.invalidateAll] / [LibraryRefresh.invalidate].
     */
    private suspend fun <T> cached(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        // Offline: the cache is the whole library, and there is nothing to
        // revalidate. Same instant path as online-cached.
        if (offline()) {
            val hit = cache.read(key, serializer)
            return if (hit != null) Result.Ok(hit) else Result.Err("You're offline")
        }
        val forced = refresh.policy.consumeForced(key)
        val hit = cache.read(key, serializer)
        if (!forced && hit != null) {
            revalidate(key, serializer, fetch)
            return Result.Ok(hit)
        }
        // The indicator only runs when a refresh replaces something already on
        // screen; a first load has its own spinner.
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
                cache.write(key, serializer, result.value)
                refresh.emitRefreshed(key)
                result
            }
            is Result.Err -> {
                // A refresh that fails keeps what is on screen.
                if (hit != null) {
                    refresh.emitStale(key)
                    Result.Ok(hit)
                } else {
                    result
                }
            }
        }
    }

    /**
     * Background refresh of one entry, at most once per policy window. Runs on
     * its own scope: leaving the tab or the screen does not cancel it, and it
     * never touches the caller's suspension.
     */
    private fun <T> revalidate(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fetch: suspend () -> Result<T>,
    ) {
        if (!refresh.policy.shouldRefresh(key)) return
        refresh.beginRefresh()
        cacheScope.launch {
            try {
                val result = fetch()
                if (result is Result.Ok) {
                    cache.write(key, serializer, result.value)
                    refresh.emitRefreshed(key)
                } else {
                    // Old data stays; the UI may show one small "couldn't
                    // refresh" line and nothing else.
                    refresh.emitStale(key)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh.emitStale(key)
            } finally {
                refresh.endRefresh()
            }
        }
    }

    private fun <T> unwrap(status: String, error: String?, get: () -> T): Result<T> =
        if (status == "ok") Result.Ok(get())
        else Result.Err(SubsonicAuth.userMessage(
            null,
            error,
            null,
        ).ifBlank { "Server error" })

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Result.Err(SubsonicAuth.userMessage(null, null, e), e)
        }
    }

    override suspend fun ping(): Result<Unit> = call {
        val r = api.ping().response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.albums(type, offset), ListSerializer(Album.serializer())) {
            call {
                val r = api.getAlbumList(type, size, offset, genre).response
                unwrap(r.status, r.error?.message) {
                    val direct = r.albumList?.album.orEmpty()
                    val fallback = r.albumList2?.album.orEmpty()
                    (direct + fallback).distinctBy { it.id }.map { it.toDomain() }
                }
            }
        }

    override suspend fun artists(): Result<List<Artist>> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.ARTISTS, ListSerializer(Artist.serializer())) {
            call {
                val r = api.getArtists().response
                unwrap(r.status, r.error?.message) {
                    r.artists?.index.orEmpty().flatMap { it.artist }.map { it.toDomain() }
                }
            }
        }

    override suspend fun genres(): Result<List<Genre>> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.GENRES, ListSerializer(Genre.serializer())) {
            call {
                val r = api.getGenres().response
                unwrap(r.status, r.error?.message) { r.genres?.genre.orEmpty().map { it.toDomain() } }
            }
        }

    override suspend fun playlists(): Result<List<Playlist>> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer())) {
            call {
                val r = api.getPlaylists().response
                unwrap(r.status, r.error?.message) { r.playlists?.playlist.orEmpty().map { it.toDomain() } }
            }
        }

    override suspend fun search(query: String): Result<SearchResults> =
        search(query, artistCount = 20, albumCount = 20, songCount = 50)

    override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults> =
        call {
            val r = api.search3(query, artistCount, albumCount, songCount).response
            unwrap(r.status, r.error?.message) {
                SearchResults(
                    artists = r.searchResult3?.artist.orEmpty().map { it.toDomain() },
                    albums = r.searchResult3?.album.orEmpty().map { it.toDomain() },
                    tracks = r.searchResult3?.song.orEmpty().map { it.toDomain() },
                    playlists = r.searchResult3?.playlist.orEmpty().map { it.toDomain() },
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
            // Navidrome advertises transcode/formats support; plain Subsonic
            // servers reject the extensions endpoint entirely.
            extensions.any { it.equals("transcode", true) || it.equals("format", true) } -> "Navidrome"
            extensions.isNotEmpty() -> "OpenSubsonic"
            else -> "Subsonic-compatible"
        }
        Result.Ok(ServerInfo(apiVersion = ping.version.ifBlank { SubsonicAuth.API_VERSION }, product = product, extensions = extensions))
    }

    override suspend fun startScan(): Result<Unit> = call {
        val r = api.startScan().response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scanStatus(): Result<ScanStatus> = call {
        val r = api.getScanStatus().response
        unwrap(r.status, r.error?.message) {
            val s = r.scanStatus
            ScanStatus(scanning = s?.scanning == true, count = s?.count ?: 0)
        }
    }

    override suspend fun album(id: String): Result<AlbumDetail> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.album(id), AlbumDetail.serializer()) {
            call {
                val r = api.getAlbum(id).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.album).toDomain() }
            }
        }

    override suspend fun artist(id: String): Result<ArtistDetail> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.artist(id), ArtistDetail.serializer()) {
            call {
                val r = api.getArtist(id).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.artist).toDomain() }
            }
        }

    override suspend fun artistInfo(id: String): Result<ArtistInfo> = call {
        val r = runCatching { api.getArtistInfo2(id).response }.getOrNull()
            ?: return@call Result.Err("Artist info not available")
        unwrap(r.status, r.error?.message) { (r.artistInfo2 ?: ArtistInfo2Dto()).toDomain() }
    }

    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = call {
        val r = api.getRandomSongs(size, genre).response
        unwrap(r.status, r.error?.message) { r.randomSongs?.song.orEmpty().map { it.toDomain() } }
    }

    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = call {
        val r = runCatching { api.getTopSongs(artist, count).response }.getOrNull()
            ?: return@call Result.Err("Top songs not available")
        unwrap(r.status, r.error?.message) {
            ((r.topSongs?.track ?: emptyList()) + (r.topSongs?.song ?: emptyList()))
                .distinctBy { it.id }.map { it.toDomain() }
        }
    }

    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> = call {
        val seed = songId ?: artistId
        val r = runCatching { api.getSimilarSongs2(seed, count).response }.getOrNull()
            ?: return@call Result.Err("Similar songs not available")
        unwrap(r.status, r.error?.message) { r.similarSongs2?.song.orEmpty().map { it.toDomain() } }
    }

    override suspend fun playlist(id: String): Result<PlaylistDetail> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.playlist(id), PlaylistDetail.serializer()) {
            call {
                val r = api.getPlaylist(id).response
                unwrap(r.status, r.error?.message) { requireNotNull(r.playlist).toDomain() }
            }
        }

    override suspend fun starred(): Result<StarredCollection> =
        cached(com.feishin.mobile.data.cache.MetadataCacheKeys.STARRED, StarredCollection.serializer()) {
            call {
                val r = try {
                    api.getStarred2().response
                } catch (_: Exception) {
                    api.getStarred().response
                }
                unwrap(r.status, r.error?.message) {
                    StarredCollection(
                        artists = ((r.starred2?.artist ?: r.starred?.artist).orEmpty()).map { it.toDomain() },
                        albums = ((r.starred2?.album ?: r.starred?.album).orEmpty()).map { it.toDomain() },
                        tracks = ((r.starred2?.song ?: r.starred?.song).orEmpty()).map { it.toDomain() },
                    )
                }
            }
        }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = call {
        // Offline: remember the intent and let the sync worker replay it.
        if (offline()) {
            offlineSync.get().queueStar(id, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(ids = listOf(id)).response else api.unstar(ids = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = call {
        if (offline()) {
            offlineSync.get().queueStarAlbum(id, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(albumIds = listOf(id)).response else api.unstar(albumIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = call {
        if (offline()) {
            offlineSync.get().queueStarArtist(id, starred)
            return@call Result.Ok(Unit)
        }
        val r = if (starred) api.star(artistIds = listOf(id)).response else api.unstar(artistIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> = call {
        val r = api.createPlaylist(name, com.feishin.mobile.core.common.PlaylistBatch.firstCreateChunk(trackIds)).response
        val playlistId = runCatching {
            r.playlist?.id ?: throw IllegalStateException("no playlist id")
        }.getOrNull()
        if (playlistId == null) return@call Result.Err(r.error?.message ?: "Create failed")
        // Add any remaining tracks in batches via updatePlaylist.
        for (batch in com.feishin.mobile.core.common.PlaylistBatch.remainingChunks(trackIds)) {
            val r2 = api.updatePlaylist(playlistId, songIdToAdd = batch).response
            when (val r = unwrap(r2.status, r2.error?.message) { }) {
                is Result.Ok -> Unit
                is Result.Err -> return@call r
            }
        }
        Result.Ok(playlistId)
    }

    override suspend fun cachedPlaylist(id: String): PlaylistDetail? =
        cache.read(com.feishin.mobile.data.cache.MetadataCacheKeys.playlist(id), PlaylistDetail.serializer())

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = call {
        // Subsonic GET URLs have practical length limits; add in batches so a
        // large artist (500+ songs) fits in a single request per batch.
        for (batch in com.feishin.mobile.core.common.PlaylistBatch.chunks(trackIds)) {
            val r = api.updatePlaylist(playlistId, songIdToAdd = batch).response
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
        val detailKey = com.feishin.mobile.data.cache.MetadataCacheKeys.playlist(playlistId)
        val playlistsKey = com.feishin.mobile.data.cache.MetadataCacheKeys.PLAYLISTS

        // Snapshot original cached state for rollback
        val originalDetail = cache.read(detailKey, PlaylistDetail.serializer())
        val originalPlaylists = cache.read(
            playlistsKey,
            kotlinx.serialization.builtins.ListSerializer(Playlist.serializer()),
        )

        // Optimistically update local Room cache
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
                if (p.id == playlistId) {
                    p.copy(
                        songCount = p.songCount + tracks.size,
                        durationSec = p.durationSec + tracks.sumOf { it.durationSec },
                    )
                } else p
            }
            cache.write(
                playlistsKey,
                kotlinx.serialization.builtins.ListSerializer(Playlist.serializer()),
                updatedPlaylists,
            )
        }
        playlistEvents.get().emit(com.feishin.mobile.data.favorites.PlaylistEvent.TracksAdded(playlistId))

        // Background network sync
        cacheScope.launch {
            val res = addToPlaylist(playlistId, tracks.map { it.id })
            if (res is Result.Err) {
                // Roll back local Room cache
                if (originalDetail != null) {
                    cache.write(detailKey, PlaylistDetail.serializer(), originalDetail)
                } else {
                    cache.remove(detailKey)
                }
                if (originalPlaylists != null) {
                    cache.write(
                        playlistsKey,
                        kotlinx.serialization.builtins.ListSerializer(Playlist.serializer()),
                        originalPlaylists,
                    )
                }
                playlistEvents.get().emit(com.feishin.mobile.data.favorites.PlaylistEvent.TracksAdded(playlistId))
                onRollback?.invoke()
            }
        }
        return Result.Ok(Unit)
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = call {
        val r = api.updatePlaylist(playlistId, songIndexToRemove = indexes).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun deletePlaylist(id: String): Result<Unit> = call {
        val r = api.deletePlaylist(id).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = call {
        // Offline: queue with the original time so history lands on the right day.
        if (offline()) {
            offlineSync.get().queueScrobble(id, submission, System.currentTimeMillis())
            return@call Result.Ok(Unit)
        }
        val r = api.scrobble(id, time = System.currentTimeMillis(), submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = call {
        val r = api.scrobble(id, time = timeMs, submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun lyrics(artist: String, title: String): Result<String> = call {
        val r = api.getLyrics(artist, title).response
        unwrap(r.status, r.error?.message) { r.lyrics?.value ?: "" }
    }

    /**
     * Prefers OpenSubsonic synced lines, falls back to plain lyrics from the same
     * response, then to getLyrics. Never throws: a lyrics failure is an empty
     * result so playback is unaffected.
     */
    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> =
        withContext(Dispatchers.IO) {
            runCatching {
                val structured = runCatching { api.getLyricsBySongId(id).response }.getOrNull()
                val synced = structured?.lyricsList?.syncedLyrics
                if (synced != null && synced.line.isNotEmpty()) {
                    // OpenSubsonic reports the offset in milliseconds (may be negative).
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
        val b = Uri.parse(base() + "stream.view").buildUpon()
            .appendQueryParameter("id", track.id)
        if (maxBitrate > 0) b.appendQueryParameter("maxBitRate", maxBitrate.toString())
        if (format != "raw") b.appendQueryParameter("format", format)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }

    override fun coverArtUrl(coverArtId: String?, size: Int): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (coverArtId.startsWith("content://") || coverArtId.startsWith("file://")) {
            return coverArtId
        }
        val c = creds.state.value ?: return null
        // Stable salt per cover so Coil's disk/memory cache can hit.
        return CoverArtUrlProvider.build(c.serverUrl, c.username, c.password, coverArtId, size)
    }

    override fun downloadUrl(track: Track): String {
        val b = Uri.parse(base() + "download.view").buildUpon()
            .appendQueryParameter("id", track.id)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }
}

/** kotlinx-serialization list serializer, for the JSON metadata cache. */
private fun <T> ListSerializer(element: kotlinx.serialization.KSerializer<T>) =
    kotlinx.serialization.builtins.ListSerializer(element)
