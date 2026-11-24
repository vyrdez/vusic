package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.CrashGuard
import com.feishin.mobile.core.common.FederatedDeduplicator
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.backend.SubsonicBackend
import com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.cache.toTrack
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.BackendRegistry
import com.feishin.mobile.domain.repo.FederatedHealthState
import com.feishin.mobile.domain.repo.MusicBackend
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ProfileHealth
import com.feishin.mobile.domain.repo.ScanStatus
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Multi-backend federated [MusicDataRepository] and [BackendRegistry].
 *
 * Maintains a live map of [MusicBackend] instances synchronized with [ProfileStore.profiles],
 * dispatches single-entity operations directly by [GlobalId], and fans out collection/search
 * reads concurrently under `supervisorScope` with per-backend timeout isolation, stale-cache
 * fallback, health tracking, and deterministic [FederatedDeduplicator] merging.
 */
@Singleton
class FederatedMusicRepository internal constructor(
    private val profilesFlow: StateFlow<List<ServerProfile>>,
    private val backendFactory: (ServerProfile, CoroutineScope) -> MusicBackend,
    private val settingsProvider: suspend () -> SettingsStore.Settings,
    private val refresh: LibraryRefresh,
    private val cachedTracks: dagger.Lazy<CachedTrackStore>? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler),
) : MusicDataRepository, BackendRegistry {

    @Inject
    constructor(
        profileStore: ProfileStore,
        clientFactory: BackendClientFactory,
        cache: MetadataCache,
        refresh: LibraryRefresh,
        network: NetworkMonitor,
        settings: SettingsStore,
        offlineSync: dagger.Lazy<OfflineSyncStore>,
        playlistEvents: dagger.Lazy<PlaylistEvents>,
        cachedTracks: dagger.Lazy<CachedTrackStore>,
    ) : this(
        profilesFlow = profileStore.profiles,
        backendFactory = defaultBackendFactory(
            clientFactory = clientFactory,
            cache = cache,
            refresh = refresh,
            network = network,
            settings = settings,
            offlineSync = offlineSync,
            playlistEvents = playlistEvents,
        ),
        settingsProvider = defaultSettingsProvider(settings),
        refresh = refresh,
        cachedTracks = cachedTracks,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler),
    )

    private val registryLock = Any()
    @Volatile
    private var lastSyncedProfiles: List<ServerProfile>? = null
    @Volatile
    private var orderedBackends: List<MusicBackend> = emptyList()
    @Volatile
    private var backendMap: Map<String, MusicBackend> = emptyMap()
    private val liveEntries = LinkedHashMap<String, Pair<ServerProfile, MusicBackend>>()

    private val _healthState = MutableStateFlow(FederatedHealthState())
    override val healthState: StateFlow<FederatedHealthState> = _healthState.asStateFlow()

    init {
        syncBackends(profilesFlow.value)
        scope.launch {
            profilesFlow.collect { list -> syncBackends(list) }
        }
    }

    private fun syncBackends(current: List<ServerProfile>) {
        if (lastSyncedProfiles === current) return
        val removedIds: Set<String>
        synchronized(registryLock) {
            if (lastSyncedProfiles === current) return
            val sorted = current.sortedWith(compareBy<ServerProfile> { it.priority }.thenBy { it.id })
            val nextIds = sorted.mapTo(HashSet(sorted.size)) { it.id }
            removedIds = liveEntries.keys.filterTo(HashSet()) { it !in nextIds }
            val nextEntries = LinkedHashMap<String, Pair<ServerProfile, MusicBackend>>(sorted.size)
            for (profile in sorted) {
                val existing = liveEntries[profile.id]
                val backend = if (existing != null && existing.first == profile) {
                    existing.second
                } else {
                    backendFactory(profile, scope)
                }
                nextEntries[profile.id] = profile to backend
            }
            liveEntries.clear()
            liveEntries.putAll(nextEntries)
            orderedBackends = nextEntries.values.map { it.second }
            backendMap = nextEntries.mapValues { it.value.second }
            lastSyncedProfiles = current
        }
        if (removedIds.isNotEmpty()) {
            _healthState.update { state ->
                val remaining = state.profiles.filterKeys { it !in removedIds }
                if (remaining.size == state.profiles.size) state else FederatedHealthState(remaining)
            }
        }
    }

    private fun ensureSynced() {
        val current = profilesFlow.value
        if (lastSyncedProfiles !== current) {
            syncBackends(current)
        }
    }

    override fun all(): List<MusicBackend> {
        ensureSynced()
        return orderedBackends
    }

    override fun enabled(): List<MusicBackend> {
        ensureSynced()
        return orderedBackends.filter { it.profile.enabled }
    }

    override fun get(profileId: String): MusicBackend? {
        ensureSynced()
        return backendMap[profileId]
    }

    override fun capabilitiesFor(id: String): BackendCapabilities {
        if (GlobalId.isDeviceLocal(id)) return BackendCapabilities.DEVICE_LOCAL
        val profileId = GlobalId.profileIdOrNull(id)
        if (profileId != null) {
            get(profileId)?.let { return it.capabilities }
        }
        return enabled().firstOrNull()?.capabilities ?: BackendCapabilities.forType(BackendType.NAVIDROME)
    }

    private fun resolveBackend(id: String): MusicBackend? {
        val parsed = GlobalId.parse(id)
        if (parsed != null) {
            return get(parsed.first)
        }
        if (GlobalId.isDeviceLocal(id) || GlobalId.isReserved(id)) return null
        val active = enabled()
        get("p1")?.takeIf { it.profile.enabled }?.let { return it }
        return if (active.size == 1) active.single() else null
    }

    private suspend fun readSettings(): SettingsStore.Settings =
        runCatching { settingsProvider() }.getOrElse { SettingsStore.Settings() }

    /**
     * Resolves enabled backends matching [SettingsStore.Settings.librarySourceFilter].
     *
     * Treats `"DEVICE"` as `"ALL"` inside [FederatedMusicRepository] so Home, Search,
     * and Radio never blank out while the Library tab is viewing local device files.
     */
    private fun matchingBackends(settings: SettingsStore.Settings): List<MusicBackend> {
        val active = enabled()
        val filter = settings.librarySourceFilter.trim()
        if (
            filter.isBlank() ||
            filter.equals(SOURCE_ALL, ignoreCase = true) ||
            filter.equals(SOURCE_DEVICE, ignoreCase = true)
        ) {
            return active
        }
        if (filter.startsWith(PREFIX_TYPE, ignoreCase = true)) {
            val typeName = filter.substring(PREFIX_TYPE.length).trim()
            return active.filter { it.profile.type.name.equals(typeName, ignoreCase = true) }
        }
        if (filter.startsWith(PREFIX_PROFILE, ignoreCase = true)) {
            val pid = filter.substring(PREFIX_PROFILE.length).trim()
            return active.filter { it.profile.id == pid }
        }
        return active.filter { it.profile.id == filter }
    }

    private fun profilePriorities(backends: List<MusicBackend>): Map<String, Int> =
        backends.associate { it.profile.id to it.profile.priority }

    private fun recordHealthy(profile: ServerProfile) {
        _healthState.update { state ->
            val current = state.profiles[profile.id]
            val next = ProfileHealth(profile.id, profile.name, degraded = false, lastError = null)
            if (current == next) state else FederatedHealthState(state.profiles + (profile.id to next))
        }
    }

    private fun recordDegraded(profile: ServerProfile, error: String?) {
        _healthState.update { state ->
            val next = ProfileHealth(profile.id, profile.name, degraded = true, lastError = error)
            FederatedHealthState(state.profiles + (profile.id to next))
        }
    }

    private data class BackendOutcome<T>(
        val backend: MusicBackend,
        val value: T?,
        val fromCache: Boolean = false,
        val error: String? = null,
    )

    private suspend fun <T> fanOut(
        backends: List<MusicBackend>,
        timeoutMs: Long,
        logicalRefreshKey: String? = null,
        isEmpty: (T) -> Boolean,
        fallbackCache: (suspend (MusicBackend) -> T?)? = null,
        call: suspend (MusicBackend) -> Result<T>,
    ): Result<List<BackendOutcome<T>>> {
        if (backends.isEmpty()) return Result.Ok(emptyList())
        val effectiveTimeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        val outcomes = supervisorScope {
            backends.map { backend ->
                async {
                    try {
                        when (val timed = withTimeoutOrNull(effectiveTimeout) { call(backend) }) {
                            is Result.Ok -> {
                                recordHealthy(backend.profile)
                                BackendOutcome(backend, timed.value)
                            }
                            is Result.Err -> {
                                recordDegraded(backend.profile, timed.message)
                                fallbackOutcome(backend, timed.message, isEmpty, fallbackCache)
                            }
                            null -> {
                                val msg = "Timed out after ${effectiveTimeout}ms"
                                recordDegraded(backend.profile, msg)
                                fallbackOutcome(backend, msg, isEmpty, fallbackCache)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = e.message ?: "Server error"
                        recordDegraded(backend.profile, msg)
                        fallbackOutcome(backend, msg, isEmpty, fallbackCache)
                    }
                }
            }.awaitAll()
        }
        val usable = outcomes.filter { it.value != null }
        if (usable.isEmpty()) {
            return Result.Err(outcomes.firstNotNullOfOrNull { it.error } ?: "All servers failed")
        }
        if (logicalRefreshKey != null && usable.any { it.fromCache }) {
            refresh.emitStale(logicalRefreshKey)
        }
        return Result.Ok(usable)
    }

    private suspend fun <T> fallbackOutcome(
        backend: MusicBackend,
        error: String,
        isEmpty: (T) -> Boolean,
        fallbackCache: (suspend (MusicBackend) -> T?)?,
    ): BackendOutcome<T> {
        val cached = fallbackCache?.let { runCatching { it(backend) }.getOrNull() }
        return if (cached != null && !isEmpty(cached)) {
            BackendOutcome(backend, cached, fromCache = true, error = error)
        } else {
            BackendOutcome(backend, null, fromCache = false, error = error)
        }
    }

    override suspend fun ping(): Result<Unit> {
        val settings = readSettings()
        val targets = enabled()
        if (targets.isEmpty()) return Result.Err("Not logged in")
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { false },
            ) { it.ping() }
        ) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    override suspend fun ping(profileId: String): Result<Unit> {
        val backend = get(profileId) ?: return Result.Err("Server not found")
        val res = backend.ping()
        when (res) {
            is Result.Ok -> recordHealthy(backend.profile)
            is Result.Err -> recordDegraded(backend.profile, res.message)
        }
        return res
    }

    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(emptyList())
        val logicalKey = if (genre.isNullOrBlank()) {
            MetadataCacheKeys.albums(type, offset)
        } else {
            "${MetadataCacheKeys.albums(type, offset)}:$genre"
        }
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                logicalRefreshKey = logicalKey,
                isEmpty = { it.isEmpty() },
                fallbackCache = { it.readCachedAlbumList(type, offset, genre) },
            ) { it.albumList(type, size, offset, genre) }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val lists = r.value.mapNotNull { it.value }
                val priorities = profilePriorities(targets)
                val merged = when (type) {
                    "frequent" -> lists.flatten().sortedWith(
                        compareByDescending<Album> { it.playCount }
                            .thenBy { priorities[it.sourceProfileId ?: GlobalId.profileIdOrNull(it.id)] ?: Int.MAX_VALUE }
                            .thenBy { it.id },
                    )
                    "newest" -> interleave(lists).sortedByDescending { it.year ?: 0 }
                    "alphabeticalByName" -> lists.flatten().sortedWith(
                        compareBy<Album> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id },
                    )
                    "alphabeticalByArtist" -> lists.flatten().sortedWith(
                        compareBy<Album> { it.artist.lowercase(Locale.ROOT) }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                            .thenBy { it.id },
                    )
                    else -> interleave(lists)
                }
                val deduped = FederatedDeduplicator.deduplicateAlbums(merged, priorities)
                val finalList = when (type) {
                    "alphabeticalByName", "alphabeticalByArtist", "byGenre" -> deduped
                    else -> if (size > 0) deduped.take(size) else deduped
                }
                Result.Ok(finalList)
            }
        }
    }

    override suspend fun artists(): Result<List<Artist>> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                logicalRefreshKey = MetadataCacheKeys.ARTISTS,
                isEmpty = { it.isEmpty() },
                fallbackCache = { it.readCachedArtists() },
            ) { it.artists() }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val merged = r.value.flatMap { it.value.orEmpty() }
                    .distinctBy { it.id }
                    .sortedWith(compareBy<Artist> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
                Result.Ok(merged)
            }
        }
    }

    override suspend fun genres(): Result<List<Genre>> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                logicalRefreshKey = MetadataCacheKeys.GENRES,
                isEmpty = { it.isEmpty() },
                fallbackCache = { it.readCachedGenres() },
            ) { it.genres() }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val merged = LinkedHashMap<String, Genre>()
                for (outcome in r.value) {
                    for (genre in outcome.value.orEmpty()) {
                        val trimmed = genre.name.trim()
                        if (trimmed.isEmpty()) continue
                        val key = trimmed.lowercase(Locale.ROOT)
                        val existing = merged[key]
                        if (existing == null) {
                            merged[key] = genre.copy(name = trimmed)
                        } else {
                            merged[key] = existing.copy(
                                songCount = existing.songCount + genre.songCount,
                                albumCount = existing.albumCount + genre.albumCount,
                            )
                        }
                    }
                }
                Result.Ok(merged.values.sortedBy { it.name.lowercase(Locale.ROOT) })
            }
        }
    }

    override suspend fun playlists(): Result<List<Playlist>> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                logicalRefreshKey = MetadataCacheKeys.PLAYLISTS,
                isEmpty = { it.isEmpty() },
                fallbackCache = { it.readCachedPlaylists() },
            ) { it.playlists() }
        ) {
            is Result.Err -> r
            is Result.Ok -> Result.Ok(r.value.flatMap { it.value.orEmpty() }.distinctBy { it.id })
        }
    }

    override suspend fun search(query: String): Result<SearchResults> =
        search(query, artistCount = 20, albumCount = 20, songCount = 50)

    override suspend fun search(
        query: String,
        artistCount: Int,
        albumCount: Int,
        songCount: Int,
    ): Result<SearchResults> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(SearchResults())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { it.artists.isEmpty() && it.albums.isEmpty() && it.tracks.isEmpty() && it.playlists.isEmpty() },
            ) { it.search(query, artistCount, albumCount, songCount) }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val results = r.value.mapNotNull { it.value }
                val priorities = profilePriorities(targets)
                Result.Ok(
                    SearchResults(
                        artists = interleave(results.map { it.artists }).distinctBy { it.id },
                        albums = FederatedDeduplicator.deduplicateAlbums(
                            interleave(results.map { it.albums }),
                            priorities,
                        ),
                        tracks = FederatedDeduplicator.deduplicateTracks(
                            interleave(results.map { it.tracks }),
                            priorities,
                        ),
                        playlists = interleave(results.map { it.playlists }).distinctBy { it.id },
                    ),
                )
            }
        }
    }

    override suspend fun serverInfo(): Result<ServerInfo> {
        val primary = enabled().firstOrNull() ?: return Result.Err("Not logged in")
        return primary.serverInfo()
    }

    override suspend fun serverInfo(profileId: String): Result<ServerInfo> =
        get(profileId)?.serverInfo() ?: Result.Err("Server not found")

    override suspend fun startScan(): Result<Unit> {
        val target = enabled().firstOrNull { it.capabilities.supportsLibraryScan }
            ?: return Result.Err("No scannable server configured")
        return target.startScan()
    }

    override suspend fun startScan(profileId: String): Result<Unit> =
        get(profileId)?.startScan() ?: Result.Err("Server not found")

    override suspend fun scanStatus(): Result<ScanStatus> {
        val target = enabled().firstOrNull { it.capabilities.supportsLibraryScan }
            ?: return Result.Err("No scannable server configured")
        return target.scanStatus()
    }

    override suspend fun scanStatus(profileId: String): Result<ScanStatus> =
        get(profileId)?.scanStatus() ?: Result.Err("Server not found")

    override suspend fun album(id: String): Result<AlbumDetail> {
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        return backend.album(id)
    }

    override suspend fun artist(id: String): Result<ArtistDetail> {
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        return backend.artist(id)
    }

    override suspend fun artistInfo(id: String): Result<ArtistInfo> {
        val backend = resolveBackend(id) ?: return Result.Err("Artist info not available")
        return backend.artistInfo(id)
    }

    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { it.isEmpty() },
            ) { it.randomSongs(size, genre) }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val priorities = profilePriorities(targets)
                val merged = interleave(r.value.mapNotNull { it.value })
                val deduped = FederatedDeduplicator.deduplicateTracks(merged, priorities)
                Result.Ok(if (size > 0) deduped.take(size) else deduped)
            }
        }
    }

    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> =
        topSongs(artist, count, preferredProfileId = null)

    override suspend fun topSongs(artist: String, count: Int, preferredProfileId: String?): Result<List<Track>> {
        val settings = readSettings()
        val baseTargets = matchingBackends(settings)
        val targets = if (preferredProfileId != null) {
            val preferred = get(preferredProfileId)?.takeIf { it.profile.enabled }
            if (preferred != null) {
                listOf(preferred) + baseTargets.filter { it.profile.id != preferredProfileId }
            } else {
                baseTargets
            }
        } else {
            baseTargets
        }
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { it.isEmpty() },
            ) { it.topSongs(artist, count) }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val priorities = profilePriorities(targets).toMutableMap()
                if (preferredProfileId != null) {
                    priorities[preferredProfileId] = Int.MIN_VALUE / 2
                }
                val preferredTracks = if (preferredProfileId != null) {
                    r.value.firstOrNull { it.backend.profile.id == preferredProfileId }?.value.orEmpty()
                } else {
                    emptyList()
                }
                val otherLists = r.value
                    .filter { preferredProfileId == null || it.backend.profile.id != preferredProfileId }
                    .mapNotNull { it.value }
                val combined = preferredTracks + interleave(otherLists)
                val deduped = FederatedDeduplicator.deduplicateTracks(combined, priorities)
                Result.Ok(if (count > 0) deduped.take(count) else deduped)
            }
        }
    }

    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> {
        val seedId = songId ?: artistId
        val seedProfileId = seedId?.let { GlobalId.profileIdOrNull(it) }
        if (seedProfileId != null) {
            val backend = get(seedProfileId) ?: return Result.Err("Similar songs not available")
            if (!backend.capabilities.supportsRadioMix) return Result.Ok(emptyList())
            return backend.similarSongs(songId, artistId, count)
        }
        val settings = readSettings()
        val targets = matchingBackends(settings).filter { it.capabilities.supportsRadioMix }
        if (targets.isEmpty()) return Result.Ok(emptyList())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { it.isEmpty() },
            ) { it.similarSongs(songId, artistId, count) }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val priorities = profilePriorities(targets)
                val deduped = FederatedDeduplicator.deduplicateTracks(
                    interleave(r.value.mapNotNull { it.value }),
                    priorities,
                )
                Result.Ok(if (count > 0) deduped.take(count) else deduped)
            }
        }
    }

    override suspend fun playlist(id: String): Result<PlaylistDetail> {
        if (id == FAVORITE_SONGS_PLAYLIST_ID) {
            return when (val r = starred()) {
                is Result.Ok -> {
                    val tracks = r.value.tracks
                    Result.Ok(
                        PlaylistDetail(
                            id = FAVORITE_SONGS_PLAYLIST_ID,
                            name = "Favorite songs",
                            songCount = tracks.size,
                            durationSec = tracks.sumOf { it.durationSec },
                            tracks = tracks,
                        ),
                    )
                }
                is Result.Err -> r
            }
        }
        if (id == CACHED_SONGS_PLAYLIST_ID) {
            val tracks = cachedTracks?.get()?.all()?.map { it.toTrack() }.orEmpty()
            return Result.Ok(
                PlaylistDetail(
                    id = CACHED_SONGS_PLAYLIST_ID,
                    name = "Cached",
                    songCount = tracks.size,
                    durationSec = tracks.sumOf { it.durationSec },
                    tracks = tracks,
                ),
            )
        }
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        return backend.playlist(id)
    }

    override suspend fun cachedPlaylist(id: String): PlaylistDetail? {
        if (id in GlobalId.VIRTUAL_IDS) return null
        return resolveBackend(id)?.cachedPlaylist(id)
    }

    override suspend fun starred(): Result<StarredCollection> {
        val settings = readSettings()
        val targets = matchingBackends(settings)
        if (targets.isEmpty()) return Result.Ok(StarredCollection())
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                logicalRefreshKey = MetadataCacheKeys.STARRED,
                isEmpty = { it.artists.isEmpty() && it.albums.isEmpty() && it.tracks.isEmpty() },
                fallbackCache = { it.readCachedStarred() },
            ) { it.starred() }
        ) {
            is Result.Err -> r
            is Result.Ok -> {
                val collections = r.value.mapNotNull { it.value }
                val priorities = profilePriorities(targets)
                Result.Ok(
                    StarredCollection(
                        artists = collections.flatMap { it.artists }.distinctBy { it.id },
                        albums = FederatedDeduplicator.deduplicateAlbums(
                            collections.flatMap { it.albums },
                            priorities,
                        ),
                        tracks = FederatedDeduplicator.deduplicateTracks(
                            collections.flatMap { it.tracks },
                            priorities,
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(Unit)
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        val res = backend.starTrack(id, starred)
        if (res is Result.Ok) refresh.policy.forceLogical(MetadataCacheKeys.STARRED)
        return res
    }

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(Unit)
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        val res = backend.starAlbum(id, starred)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.STARRED)
            refresh.policy.forceLogical(MetadataCacheKeys.album(id))
        }
        return res
    }

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(Unit)
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        val res = backend.starArtist(id, starred)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.STARRED)
            refresh.policy.forceLogical(MetadataCacheKeys.artist(id))
        }
        return res
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> =
        createPlaylist(name, trackIds, profileId = null)

    override suspend fun createPlaylist(
        name: String,
        trackIds: List<String>,
        profileId: String?,
    ): Result<String> {
        val target = when {
            profileId != null -> get(profileId)
            else -> {
                val trackProfile = trackIds.firstNotNullOfOrNull { GlobalId.profileIdOrNull(it) }
                trackProfile?.let { get(it) }
                    ?: matchingBackends(readSettings()).firstOrNull { it.capabilities.supportsServerPlaylists }
                    ?: enabled().firstOrNull { it.capabilities.supportsServerPlaylists }
            }
        } ?: return Result.Err("No server available for playlist creation")

        val compatibleTrackIds = trackIds.filter { tid ->
            if (GlobalId.isDeviceLocal(tid)) return@filter false
            val owner = GlobalId.profileIdOrNull(tid)
            owner == null || owner == target.profile.id
        }
        val res = target.createPlaylist(name, compatibleTrackIds)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.PLAYLISTS)
        }
        return res
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> {
        val backend = resolveBackend(playlistId) ?: return Result.Err("Server unavailable")
        val compatible = trackIds.filter { tid ->
            if (GlobalId.isDeviceLocal(tid)) return@filter false
            val owner = GlobalId.profileIdOrNull(tid)
            owner == null || owner == backend.profile.id
        }
        val res = backend.addToPlaylist(playlistId, compatible)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.playlist(playlistId))
            refresh.policy.forceLogical(MetadataCacheKeys.PLAYLISTS)
        }
        return res
    }

    override suspend fun addToPlaylistOptimistic(
        playlistId: String,
        tracks: List<Track>,
        playlistName: String,
        onRollback: (() -> Unit)?,
    ): Result<Unit> {
        val backend = resolveBackend(playlistId) ?: return Result.Err("Server unavailable")
        val compatible = tracks.filter { track ->
            if (track.isLocal || GlobalId.isDeviceLocal(track.id)) return@filter false
            val owner = track.sourceProfileId ?: GlobalId.profileIdOrNull(track.id)
            owner == null || owner == backend.profile.id
        }
        return backend.addToPlaylistOptimistic(playlistId, compatible, playlistName, onRollback)
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> {
        val backend = resolveBackend(playlistId) ?: return Result.Err("Server unavailable")
        val res = backend.removeFromPlaylist(playlistId, indexes)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.playlist(playlistId))
            refresh.policy.forceLogical(MetadataCacheKeys.PLAYLISTS)
        }
        return res
    }

    override suspend fun deletePlaylist(id: String): Result<Unit> {
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        val res = backend.deletePlaylist(id)
        if (res is Result.Ok) {
            refresh.policy.forceLogical(MetadataCacheKeys.PLAYLISTS)
        }
        return res
    }

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(Unit)
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        if (!backend.capabilities.supportsScrobbling) return Result.Ok(Unit)
        return backend.scrobble(id, submission)
    }

    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(Unit)
        val backend = resolveBackend(id) ?: return Result.Err("Server unavailable")
        if (!backend.capabilities.supportsScrobbling) return Result.Ok(Unit)
        return backend.scrobbleAt(id, timeMs, submission)
    }

    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> {
        if (GlobalId.isDeviceLocal(id)) return Result.Ok(SyncedLyrics.EMPTY)
        val backend = resolveBackend(id) ?: return Result.Ok(SyncedLyrics.EMPTY)
        if (!backend.capabilities.supportsServerLyrics) return Result.Ok(SyncedLyrics.EMPTY)
        return backend.lyricsForTrack(id, artist, title)
    }

    override suspend fun lyrics(artist: String, title: String): Result<String> {
        val settings = readSettings()
        val targets = matchingBackends(settings).filter { it.capabilities.supportsServerLyrics }
        if (targets.isEmpty()) return Result.Ok("")
        return when (
            val r = fanOut(
                backends = targets,
                timeoutMs = settings.federatedTimeoutMs,
                isEmpty = { it.isBlank() },
            ) { it.lyrics(artist, title) }
        ) {
            is Result.Err -> r
            is Result.Ok -> Result.Ok(r.value.firstNotNullOfOrNull { it.value?.takeIf(String::isNotBlank) }.orEmpty())
        }
    }

    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String {
        if (track.isLocal) return track.localUri.orEmpty()
        val backend = track.sourceProfileId?.let { get(it) } ?: resolveBackend(track.id)
            ?: throw IllegalStateException("No backend registered for track ${track.id}")
        return backend.streamUrl(track, maxBitrate, format)
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
        return resolveBackend(coverArtId)?.coverArtUrl(coverArtId, size)
    }

    override fun downloadUrl(track: Track): String {
        if (track.isLocal) return track.localUri.orEmpty()
        val backend = track.sourceProfileId?.let { get(it) } ?: resolveBackend(track.id)
            ?: throw IllegalStateException("No backend registered for track ${track.id}")
        return backend.downloadUrl(track)
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists[0]
        val maxLen = lists.maxOf { it.size }
        val result = ArrayList<T>(lists.sumOf { it.size })
        for (i in 0 until maxLen) {
            for (list in lists) {
                if (i < list.size) result.add(list[i])
            }
        }
        return result
    }

    companion object {
        const val SOURCE_ALL = "ALL"
        const val SOURCE_DEVICE = "DEVICE"
        const val PREFIX_TYPE = "TYPE:"
        const val PREFIX_PROFILE = "PROFILE:"
        private const val MIN_TIMEOUT_MS = 2_000L
        private const val MAX_TIMEOUT_MS = 10_000L

        private fun defaultBackendFactory(
            clientFactory: BackendClientFactory,
            cache: MetadataCache,
            refresh: LibraryRefresh,
            network: NetworkMonitor,
            settings: SettingsStore,
            offlineSync: dagger.Lazy<OfflineSyncStore>,
            playlistEvents: dagger.Lazy<PlaylistEvents>,
        ): (ServerProfile, CoroutineScope) -> MusicBackend = { profile, repoScope ->
            when (profile.type) {
                BackendType.NAVIDROME,
                BackendType.OPENSUBSONIC -> SubsonicBackend(
                    profile = profile,
                    api = clientFactory.subsonicApi(profile),
                    cache = cache,
                    refresh = refresh,
                    network = network,
                    settings = settings,
                    offlineSync = offlineSync,
                    playlistEvents = playlistEvents,
                    cacheScope = repoScope,
                )
                BackendType.JELLYFIN,
                BackendType.GOOGLE_DRIVE -> UnsupportedBackend(profile)
            }
        }

        private fun defaultSettingsProvider(
            settings: SettingsStore,
        ): suspend () -> SettingsStore.Settings = {
            try {
                settings.settingsNow()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                settings.latestSettings ?: SettingsStore.Settings()
            }
        }
    }
}

/** Placeholder adapter for backend types implemented in later phases (12.1 Jellyfin, 12.2 Drive). */
private class UnsupportedBackend(
    override val profile: ServerProfile,
) : MusicBackend {
    override val capabilities: BackendCapabilities = BackendCapabilities.forType(profile.type)
    private fun unsupported(): Result.Err = Result.Err("${profile.type} backend is not supported yet")

    override suspend fun ping(): Result<Unit> = unsupported()
    override suspend fun serverInfo(): Result<ServerInfo> = unsupported()
    override suspend fun startScan(): Result<Unit> = unsupported()
    override suspend fun scanStatus(): Result<ScanStatus> = unsupported()
    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> = unsupported()
    override suspend fun artists(): Result<List<Artist>> = unsupported()
    override suspend fun genres(): Result<List<Genre>> = unsupported()
    override suspend fun playlists(): Result<List<Playlist>> = unsupported()
    override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults> = unsupported()
    override suspend fun album(id: String): Result<AlbumDetail> = unsupported()
    override suspend fun artist(id: String): Result<ArtistDetail> = unsupported()
    override suspend fun artistInfo(id: String): Result<ArtistInfo> = unsupported()
    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = unsupported()
    override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = unsupported()
    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> = unsupported()
    override suspend fun playlist(id: String): Result<PlaylistDetail> = unsupported()
    override suspend fun starred(): Result<StarredCollection> = unsupported()
    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = unsupported()
    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = unsupported()
    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = unsupported()
    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> = unsupported()
    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = unsupported()
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = unsupported()
    override suspend fun deletePlaylist(id: String): Result<Unit> = unsupported()
    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = unsupported()
    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = unsupported()
    override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> = unsupported()
    override suspend fun lyrics(artist: String, title: String): Result<String> = unsupported()
    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String =
        throw UnsupportedOperationException("${profile.type} backend is not supported yet")
    override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
    override fun downloadUrl(track: Track): String =
        throw UnsupportedOperationException("${profile.type} backend is not supported yet")
}
