package com.feishin.mobile.data.backend

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.remote.jellyfin.CreateJellyfinPlaylistRequest
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.remote.jellyfin.JellyfinPlaybackStartInfo
import com.feishin.mobile.data.remote.jellyfin.JellyfinPlaybackStopInfo
import com.feishin.mobile.data.remote.jellyfin.toAlbum
import com.feishin.mobile.data.remote.jellyfin.toAlbumDetail
import com.feishin.mobile.data.remote.jellyfin.toArtist
import com.feishin.mobile.data.remote.jellyfin.toArtistDetail
import com.feishin.mobile.data.remote.jellyfin.toGenre
import com.feishin.mobile.data.remote.jellyfin.toPlaylist
import com.feishin.mobile.data.remote.jellyfin.toPlaylistDetail
import com.feishin.mobile.data.remote.jellyfin.toTrack
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException

/**
 * Single-server Jellyfin [MusicBackend] adapter.
 *
 * Scopes all Room metadata cache entries under `{profileId}:` via [MetadataCacheKeys.scoped],
 * returns `{profileId}:{rawId}` identifiers via [GlobalId.format], sets `sourceProfileId` /
 * `sourceLabel` on [Track] and [Album], and unwraps [GlobalId.rawId] before calling [JellyfinApi].
 */
class JellyfinBackend(
    override val profile: ServerProfile,
    private val api: JellyfinApi,
    private val cache: MetadataCache,
    private val refresh: LibraryRefresh,
    private val network: NetworkMonitor?,
    private val settings: SettingsStore,
    private val offlineSync: dagger.Lazy<OfflineSyncStore>,
    private val playlistEvents: dagger.Lazy<PlaylistEvents>,
    private val cacheScope: CoroutineScope,
) : MusicBackend {

    override val capabilities: BackendCapabilities = BackendCapabilities.forType(profile.type)

    private val jellyfinCreds: ProfileCredentials.Jellyfin
        get() = profile.credentials as? ProfileCredentials.Jellyfin
            ?: throw IllegalStateException("Profile ${profile.id} does not hold Jellyfin credentials")

    private val userId: String
        get() = profile.userId.ifBlank { jellyfinCreds.username }

    private suspend fun offline(): Boolean {
        val online = network?.isOnline?.value ?: true
        val s = runCatching { settings.settingsNow() }.getOrDefault(null) ?: return true
        val auto = s.autoOffline && !online
        return s.offlineMode || auto || !online
    }

    private fun scopedKey(logicalKey: String): String =
        MetadataCacheKeys.scoped(profile.id, logicalKey)

    private fun scopeId(raw: String): String =
        if (raw.isBlank()) raw else GlobalId.format(profile.id, raw)

    private fun albumListLogicalKey(type: String, offset: Int, genre: String?): String =
        if (genre.isNullOrBlank()) MetadataCacheKeys.albums(type, offset)
        else "${MetadataCacheKeys.albums(type, offset)}:$genre"

    private suspend fun <T> cached(
        logicalKey: String,
        serializer: KSerializer<T>,
        bypassOnlineHit: Boolean = false,
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        val storageKey = scopedKey(logicalKey)
        if (offline()) {
            val hit = cache.read(storageKey, serializer)
            return if (hit != null) Result.Ok(hit) else Result.Err("You're offline")
        }
        val forced = refresh.policy.consumeForced(storageKey, logicalKey)
        val hit = cache.read(storageKey, serializer)
        if (!bypassOnlineHit && !forced && hit != null) {
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
                401 -> "Session expired. Please sign in again."
                403 -> "Access denied for this Jellyfin account."
                404 -> "Jellyfin endpoint or item not found (HTTP 404)."
                else -> "Server error (HTTP ${cause.code()})."
            }
        }
        if (cause is SerializationException || cause is IllegalArgumentException) {
            return "Server returned an unexpected response."
        }
        return SubsonicAuth.userMessage(code = null, serverText = null, cause = cause)
    }

    override suspend fun ping(): Result<Unit> = call {
        api.getPublicSystemInfo()
        Result.Ok(Unit)
    }

    override suspend fun serverInfo(): Result<ServerInfo> = call {
        val info = api.getPublicSystemInfo()
        Result.Ok(
            ServerInfo(
                apiVersion = info.version,
                product = info.productName.ifBlank { "Jellyfin" },
                extensions = emptyList(),
            ),
        )
    }

    override suspend fun startScan(): Result<Unit> = call {
        api.refreshLibrary()
        Result.Ok(Unit)
    }

    override suspend fun scanStatus(): Result<ScanStatus> = Result.Ok(ScanStatus(scanning = false))

    override suspend fun albumList(
        type: String,
        size: Int,
        offset: Int,
        genre: String?,
    ): Result<List<Album>> {
        val logicalKey = albumListLogicalKey(type, offset, genre)
        return cached(
            logicalKey = logicalKey,
            serializer = ListSerializer(Album.serializer()),
            bypassOnlineHit = (type == "random"),
        ) {
            call {
                val items = when (type) {
                    "recent" -> {
                        val played = api.getItems(
                            userId = userId,
                            includeItemTypes = TYPE_MUSIC_ALBUM,
                            recursive = true,
                            sortBy = "DatePlayed",
                            sortOrder = ORDER_DESC,
                            filters = "IsPlayed",
                            fields = FIELDS_ALBUM_RECENT,
                            limit = size,
                            startIndex = offset,
                        ).items
                        if (played.isEmpty() && offset == 0) {
                            api.getItems(
                                userId = userId,
                                includeItemTypes = TYPE_MUSIC_ALBUM,
                                recursive = true,
                                sortBy = "DatePlayed",
                                sortOrder = ORDER_DESC,
                                fields = FIELDS_ALBUM_RECENT,
                                limit = size,
                                startIndex = 0,
                            ).items
                        } else {
                            played
                        }
                    }
                    "frequent" -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        sortBy = "PlayCount",
                        sortOrder = ORDER_DESC,
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset,
                    ).items
                    "newest" -> if (offset == 0) {
                        api.getLatestItems(
                            userId = userId,
                            includeItemTypes = TYPE_MUSIC_ALBUM,
                            fields = FIELDS_ALBUM,
                            limit = size,
                        )
                    } else {
                        api.getItems(
                            userId = userId,
                            includeItemTypes = TYPE_MUSIC_ALBUM,
                            recursive = true,
                            sortBy = "DateCreated",
                            sortOrder = ORDER_DESC,
                            fields = FIELDS_ALBUM,
                            limit = size,
                            startIndex = offset,
                        ).items
                    }
                    "random" -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        sortBy = "Random",
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset.takeIf { it > 0 },
                    ).items
                    "alphabeticalByArtist" -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        sortBy = "AlbumArtist,SortName",
                        sortOrder = ORDER_ASC,
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset,
                    ).items
                    "byGenre" -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        genres = genre?.takeIf { it.isNotBlank() },
                        sortBy = "SortName",
                        sortOrder = ORDER_ASC,
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset,
                    ).items
                    "starred" -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        filters = FILTER_FAVORITE,
                        sortBy = "SortName",
                        sortOrder = ORDER_ASC,
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset,
                    ).items
                    else -> api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        sortBy = "SortName",
                        sortOrder = ORDER_ASC,
                        fields = FIELDS_ALBUM,
                        limit = size,
                        startIndex = offset,
                    ).items
                }
                Result.Ok(items.map { it.toAlbum(profile) })
            }
        }
    }

    override suspend fun artists(): Result<List<Artist>> =
        cached(MetadataCacheKeys.ARTISTS, ListSerializer(Artist.serializer())) {
            call {
                val items = api.getAlbumArtists(
                    userId = userId,
                    fields = "PrimaryImageAspectRatio",
                    recursive = true,
                ).items
                Result.Ok(items.map { it.toArtist(profile) })
            }
        }

    override suspend fun genres(): Result<List<Genre>> =
        cached(MetadataCacheKeys.GENRES, ListSerializer(Genre.serializer())) {
            call {
                val items = api.getMusicGenres(
                    userId = userId,
                    recursive = true,
                ).items
                Result.Ok(items.map { it.toGenre() })
            }
        }

    override suspend fun playlists(): Result<List<Playlist>> =
        cached(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer())) {
            call {
                val items = api.getItems(
                    userId = userId,
                    includeItemTypes = TYPE_PLAYLIST,
                    recursive = true,
                    fields = "Genres,MediaSources",
                ).items
                Result.Ok(
                    items
                        .filter { it.mediaType.equals(TYPE_AUDIO, ignoreCase = true) }
                        .map { it.toPlaylist(profile) },
                )
            }
        }

    override suspend fun search(
        query: String,
        artistCount: Int,
        albumCount: Int,
        songCount: Int,
    ): Result<SearchResults> = call {
        if (query.isBlank()) return@call Result.Ok(SearchResults())
        supervisorScope {
            val artistsDef = if (artistCount > 0) {
                async {
                    api.getAlbumArtists(
                        userId = userId,
                        fields = "PrimaryImageAspectRatio",
                        recursive = true,
                        searchTerm = query,
                        limit = artistCount,
                    ).items.map { it.toArtist(profile) }
                }
            } else {
                null
            }
            val albumsDef = if (albumCount > 0) {
                async {
                    api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_MUSIC_ALBUM,
                        recursive = true,
                        fields = FIELDS_ALBUM,
                        searchTerm = query,
                        limit = albumCount,
                    ).items.map { it.toAlbum(profile) }
                }
            } else {
                null
            }
            val songsDef = if (songCount > 0) {
                async {
                    api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_AUDIO,
                        recursive = true,
                        fields = FIELDS_AUDIO,
                        searchTerm = query,
                        limit = songCount,
                    ).items.map { it.toTrack(profile) }
                }
            } else {
                null
            }
            val playlistLimit = maxOf(artistCount, albumCount, songCount).coerceAtLeast(DEFAULT_PLAYLIST_SEARCH_LIMIT)
            val playlistsDef = if (artistCount > 0 || albumCount > 0 || songCount > 0) {
                async {
                    api.getItems(
                        userId = userId,
                        includeItemTypes = TYPE_PLAYLIST,
                        recursive = true,
                        fields = "Genres,MediaSources",
                        searchTerm = query,
                        limit = playlistLimit,
                    ).items
                        .filter { it.mediaType.equals(TYPE_AUDIO, ignoreCase = true) }
                        .map { it.toPlaylist(profile) }
                }
            } else {
                null
            }

            val artistsRes = artistsDef?.let { deferred ->
                runCatching { deferred.await() }.onFailure { if (it is CancellationException) throw it }
            }
            val albumsRes = albumsDef?.let { deferred ->
                runCatching { deferred.await() }.onFailure { if (it is CancellationException) throw it }
            }
            val songsRes = songsDef?.let { deferred ->
                runCatching { deferred.await() }.onFailure { if (it is CancellationException) throw it }
            }
            val playlistsRes = playlistsDef?.let { deferred ->
                runCatching { deferred.await() }.onFailure { if (it is CancellationException) throw it }
            }

            val attempted = listOfNotNull(artistsRes, albumsRes, songsRes, playlistsRes)
            if (attempted.isNotEmpty() && attempted.all { it.isFailure }) {
                throw attempted.first().exceptionOrNull()!!
            }

            Result.Ok(
                SearchResults(
                    artists = artistsRes?.getOrNull().orEmpty(),
                    albums = albumsRes?.getOrNull().orEmpty(),
                    tracks = songsRes?.getOrNull().orEmpty(),
                    playlists = playlistsRes?.getOrNull().orEmpty(),
                ),
            )
        }
    }

    override suspend fun album(id: String): Result<AlbumDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.album(globalId), AlbumDetail.serializer()) {
            call {
                val albumDto = api.getItem(userId = userId, itemId = rawId)
                val tracks = api.getItems(
                    userId = userId,
                    parentId = rawId,
                    includeItemTypes = TYPE_AUDIO,
                    recursive = true,
                    sortBy = "ParentIndexNumber,IndexNumber",
                    fields = FIELDS_AUDIO,
                ).items
                Result.Ok(albumDto.toAlbumDetail(profile, tracks))
            }
        }
    }

    override suspend fun artist(id: String): Result<ArtistDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.artist(globalId), ArtistDetail.serializer()) {
            call {
                val artistDto = api.getItem(userId = userId, itemId = rawId)
                val albums = api.getItems(
                    userId = userId,
                    albumArtistIds = rawId,
                    includeItemTypes = TYPE_MUSIC_ALBUM,
                    recursive = true,
                    fields = FIELDS_ALBUM,
                ).items
                Result.Ok(artistDto.toArtistDetail(profile, albums))
            }
        }
    }

    override suspend fun artistInfo(id: String): Result<ArtistInfo> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            val cachedArtist = cache.read(scopedKey(MetadataCacheKeys.artist(globalId)), ArtistDetail.serializer())
            return@call if (cachedArtist != null) {
                Result.Ok(
                    ArtistInfo(
                        biography = "",
                        imageUrl = coverArtUrl(cachedArtist.coverArt),
                    ),
                )
            } else {
                Result.Err("You're offline")
            }
        }
        val dto = api.getItem(userId = userId, itemId = rawId)
        val imageUrl = if (dto.imageTags?.primary != null && dto.id.isNotBlank()) {
            coverArtUrl(GlobalId.format(profile.id, dto.id))
        } else {
            null
        }
        Result.Ok(
            ArtistInfo(
                biography = dto.overview.orEmpty(),
                imageUrl = imageUrl,
            ),
        )
    }

    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = call {
        val items = api.getItems(
            userId = userId,
            includeItemTypes = TYPE_AUDIO,
            recursive = true,
            sortBy = "Random",
            genres = genre?.takeIf { it.isNotBlank() },
            fields = FIELDS_AUDIO,
            limit = size,
        ).items
        Result.Ok(items.map { it.toTrack(profile) })
    }

    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = call {
        val cachedArtistId = readCachedArtists()
            ?.firstOrNull { it.name.equals(artist, ignoreCase = true) }
            ?.id
            ?.let { GlobalId.rawId(it) }
        val resolvedId = cachedArtistId ?: runCatching {
            api.getAlbumArtists(
                userId = userId,
                searchTerm = artist,
                limit = 5,
            ).items.firstOrNull { it.name.equals(artist, ignoreCase = true) }?.id
        }.getOrNull()

        val items = if (!resolvedId.isNullOrBlank()) {
            api.getItems(
                userId = userId,
                artistIds = resolvedId,
                includeItemTypes = TYPE_AUDIO,
                recursive = true,
                sortBy = "PlayCount",
                sortOrder = ORDER_DESC,
                fields = FIELDS_AUDIO,
                limit = count,
            ).items
        } else {
            api.getItems(
                userId = userId,
                searchTerm = artist,
                includeItemTypes = TYPE_AUDIO,
                recursive = true,
                sortBy = "PlayCount",
                sortOrder = ORDER_DESC,
                fields = FIELDS_AUDIO,
                limit = count,
            ).items
        }
        Result.Ok(items.map { it.toTrack(profile) })
    }

    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> {
        val seedRawId = (songId ?: artistId)?.takeIf { it.isNotBlank() }?.let { GlobalId.rawId(it) }
            ?: return randomSongs(size = count)
        return call {
            val items = api.getInstantMix(
                id = seedRawId,
                userId = userId,
                limit = count,
                fields = FIELDS_AUDIO,
            ).items
            Result.Ok(items.map { it.toTrack(profile) })
        }
    }

    override suspend fun playlist(id: String): Result<PlaylistDetail> {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        return cached(MetadataCacheKeys.playlist(globalId), PlaylistDetail.serializer()) {
            call {
                val playlistDto = api.getItem(userId = userId, itemId = rawId)
                val tracks = api.getPlaylistItems(
                    playlistId = rawId,
                    userId = userId,
                    fields = FIELDS_AUDIO,
                ).items
                Result.Ok(playlistDto.toPlaylistDetail(profile, tracks))
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
                val items = api.getItems(
                    userId = userId,
                    filters = FILTER_FAVORITE,
                    includeItemTypes = "$TYPE_AUDIO,$TYPE_MUSIC_ALBUM,$TYPE_MUSIC_ARTIST",
                    recursive = true,
                    fields = FIELDS_AUDIO,
                ).items
                Result.Ok(
                    StarredCollection(
                        artists = items
                            .filter { it.type.equals(TYPE_MUSIC_ARTIST, ignoreCase = true) }
                            .map { it.toArtist(profile) },
                        albums = items
                            .filter { it.type.equals(TYPE_MUSIC_ALBUM, ignoreCase = true) }
                            .map { it.toAlbum(profile) },
                        tracks = items
                            .filter { it.type.equals(TYPE_AUDIO, ignoreCase = true) }
                            .map { it.toTrack(profile) },
                    ),
                )
            }
        }

    internal suspend fun allSongs(size: Int = 500, offset: Int = 0): Result<List<Track>> = call {
        val items = api.getItems(
            userId = userId,
            includeItemTypes = TYPE_AUDIO,
            recursive = true,
            sortBy = "SortName",
            sortOrder = ORDER_ASC,
            fields = FIELDS_AUDIO,
            limit = size,
            startIndex = offset,
        ).items
        Result.Ok(items.map { it.toTrack(profile) })
    }

    internal suspend fun getSong(id: String): Result<Track> = call {
        val rawId = GlobalId.rawId(id)
        Result.Ok(api.getItem(userId = userId, itemId = rawId).toTrack(profile))
    }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStar(globalId, starred)
            return@call Result.Ok(Unit)
        }
        if (starred) api.markFavorite(userId, rawId) else api.unmarkFavorite(userId, rawId)
        Result.Ok(Unit)
    }

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStarAlbum(globalId, starred)
            return@call Result.Ok(Unit)
        }
        if (starred) api.markFavorite(userId, rawId) else api.unmarkFavorite(userId, rawId)
        Result.Ok(Unit)
    }

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueStarArtist(globalId, starred)
            return@call Result.Ok(Unit)
        }
        if (starred) api.markFavorite(userId, rawId) else api.unmarkFavorite(userId, rawId)
        Result.Ok(Unit)
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> = call {
        val rawIds = trackIds.map { GlobalId.rawId(it) }
        val response = api.createPlaylist(
            CreateJellyfinPlaylistRequest(
                name = name,
                ids = rawIds,
                userId = userId,
            ),
        )
        val rawPlaylistId = response.id.takeIf { it.isNotBlank() }?.let { GlobalId.rawId(it) }
            ?: return@call Result.Err("Create failed")
        Result.Ok(GlobalId.format(profile.id, rawPlaylistId))
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = call {
        val rawPlaylistId = GlobalId.rawId(playlistId)
        val rawIds = trackIds.map { GlobalId.rawId(it) }.filter { it.isNotBlank() }
        if (rawIds.isNotEmpty()) {
            api.addPlaylistItems(
                playlistId = rawPlaylistId,
                ids = rawIds.joinToString(","),
                userId = userId,
            )
        }
        Result.Ok(Unit)
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = call {
        if (indexes.isEmpty()) return@call Result.Ok(Unit)
        val rawPlaylistId = GlobalId.rawId(playlistId)
        val items = api.getPlaylistItems(playlistId = rawPlaylistId, userId = userId).items
        val entryIds = indexes.mapNotNull { idx ->
            items.getOrNull(idx)?.playlistItemId?.takeIf { it.isNotBlank() }
        }
        if (entryIds.isNotEmpty()) {
            api.removePlaylistItems(
                playlistId = rawPlaylistId,
                entryIds = entryIds.joinToString(","),
            )
        }
        Result.Ok(Unit)
    }

    override suspend fun deletePlaylist(id: String): Result<Unit> = call {
        api.deleteItem(GlobalId.rawId(id))
        Result.Ok(Unit)
    }

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = call {
        val globalId = scopeId(id)
        val rawId = GlobalId.rawId(id)
        if (offline()) {
            offlineSync.get().queueScrobble(globalId, submission, System.currentTimeMillis())
            return@call Result.Ok(Unit)
        }
        val sessionId = "${profile.id}-$rawId"
        if (submission) {
            api.reportPlaybackStopped(
                JellyfinPlaybackStopInfo(
                    itemId = rawId,
                    mediaSourceId = rawId,
                    positionTicks = 0L,
                    playSessionId = sessionId,
                ),
            )
        } else {
            api.reportPlaybackStart(
                JellyfinPlaybackStartInfo(
                    itemId = rawId,
                    mediaSourceId = rawId,
                    positionTicks = 0L,
                    isPaused = false,
                    playSessionId = sessionId,
                ),
            )
        }
        Result.Ok(Unit)
    }

    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = call {
        val rawId = GlobalId.rawId(id)
        val sessionId = "${profile.id}-$rawId-$timeMs"
        api.reportPlaybackStart(
            JellyfinPlaybackStartInfo(
                itemId = rawId,
                mediaSourceId = rawId,
                positionTicks = 0L,
                isPaused = false,
                playSessionId = sessionId,
            ),
        )
        if (submission) {
            api.reportPlaybackStopped(
                JellyfinPlaybackStopInfo(
                    itemId = rawId,
                    mediaSourceId = rawId,
                    positionTicks = 0L,
                    playSessionId = sessionId,
                ),
            )
        }
        Result.Ok(Unit)
    }

    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> =
        Result.Ok(SyncedLyrics.EMPTY)

    override suspend fun lyrics(artist: String, title: String): Result<String> =
        Result.Ok("")

    private fun normalizedBaseHttpUrl() = JellyfinUrl.normalize(profile.url).toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid Jellyfin server URL: ${profile.url}")

    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String {
        val rawId = GlobalId.rawId(track.id)
        val base = normalizedBaseHttpUrl().newBuilder()
        if (maxBitrate <= 0 && format == "raw") {
            return base
                .addPathSegment("Audio")
                .addPathSegment(rawId)
                .addPathSegment("stream")
                .addQueryParameter("static", "true")
                .build()
                .toString()
        }
        val codec = format.takeIf { it != "raw" && it.isNotBlank() } ?: "mp3"
        base.addPathSegment("Audio")
            .addPathSegment(rawId)
            .addPathSegment("universal")
            .addQueryParameter("UserId", userId)
            .addQueryParameter("DeviceId", jellyfinCreds.deviceId)
        if (maxBitrate > 0) {
            base.addQueryParameter("MaxStreamingBitrate", (maxBitrate * 1000).toString())
        }
        return base
            .addQueryParameter("AudioCodec", codec)
            .addQueryParameter("Container", codec)
            .addQueryParameter("EnableRedirection", "false")
            .build()
            .toString()
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
        val rawCoverId = GlobalId.rawId(coverArtId)
        if (rawCoverId.isBlank()) return null
        return normalizedBaseHttpUrl().newBuilder()
            .addPathSegment("Items")
            .addPathSegment(rawCoverId)
            .addPathSegment("Images")
            .addPathSegment("Primary")
            .addQueryParameter("maxWidth", size.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()
    }

    override fun downloadUrl(track: Track): String {
        val rawId = GlobalId.rawId(track.id)
        return normalizedBaseHttpUrl().newBuilder()
            .addPathSegment("Items")
            .addPathSegment(rawId)
            .addPathSegment("Download")
            .build()
            .toString()
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

    private companion object {
        const val TYPE_AUDIO = "Audio"
        const val TYPE_MUSIC_ALBUM = "MusicAlbum"
        const val TYPE_MUSIC_ARTIST = "MusicArtist"
        const val TYPE_PLAYLIST = "Playlist"
        const val ORDER_ASC = "Ascending"
        const val ORDER_DESC = "Descending"
        const val FILTER_FAVORITE = "IsFavorite"
        const val FIELDS_ALBUM = "Genres"
        const val FIELDS_ALBUM_RECENT = "Genres,DateCreated"
        const val FIELDS_AUDIO = "MediaSources,Genres"
        const val DEFAULT_PLAYLIST_SEARCH_LIMIT = 20
    }
}
