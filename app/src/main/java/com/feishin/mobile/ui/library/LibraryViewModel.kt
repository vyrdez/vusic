package com.feishin.mobile.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.FederatedHealthState
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.LibrarySorts
import com.feishin.mobile.core.common.LibraryOrdering
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.playlist.LocalPlaylistRepository
import com.feishin.mobile.data.playlist.M3uFileTooLargeException
import com.feishin.mobile.data.playlist.M3uImportResult
import com.feishin.mobile.data.playlist.M3uImporter
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { PLAYLISTS, DOWNLOADS, ALBUMS, ARTISTS, SONGS, FAVORITES }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val playlistEvents: PlaylistEvents,
    private val settings: SettingsStore,
    private val cachedTracks: CachedTrackStore,
    private val refresh: LibraryRefresh,
    savedStateHandle: SavedStateHandle,
    private val auth: com.feishin.mobile.data.auth.AuthRepository? = null,
    private val localPlaylists: LocalPlaylistRepository? = null,
    private val m3uImporter: M3uImporter? = null,
) : ViewModel() {
    private val handle: SavedStateHandle = savedStateHandle

    /**
     * The selected tab. Written through to the SavedStateHandle on every user
     * change, so back navigation (which recreates this ViewModel from the
     * entry's saved state) returns to the exact tab instead of the default.
     * Fresh entries default to Playlists.
     */
    private val _tab = MutableStateFlow(
        runCatching {
            LibraryTab.valueOf(
                handle.get<String>(KEY_TAB) ?: handle.get<String>("tab") ?: "PLAYLISTS",
            )
        }.getOrDefault(LibraryTab.PLAYLISTS),
    )
    val tab: StateFlow<LibraryTab> = _tab.asStateFlow()

    /** Grid/list layout per tab (Appearance setting, falls back to the default). */
    private val _layouts = MutableStateFlow<Map<LibraryTab, LayoutMode>>(emptyMap())
    val layouts: StateFlow<Map<LibraryTab, LayoutMode>> = _layouts.asStateFlow()

    /** Sort choice per tab, persisted in DataStore. */
    private val _sorts = MutableStateFlow<Map<LibraryTab, SortChoice>>(emptyMap())
    val sorts: StateFlow<Map<LibraryTab, SortChoice>> = _sorts.asStateFlow()

    /** Inline search queries, kept in memory while the tab is open. */
    private val _queries = MutableStateFlow<Map<LibraryTab, String>>(emptyMap())
    val queries: StateFlow<Map<LibraryTab, String>> = _queries.asStateFlow()

    /**
     * Nav-arg tabs already honoured. The route carries a *default* tab, so
     * re-entering the screen (e.g. back from Downloads & storage) would
     * re-apply that stale default and clobber the user's selected tab. Applying
     * each distinct arg once keeps Explore pills working while leaving a
     * user-driven selection untouched on re-entry.
     */
    private val appliedNavTabs = mutableSetOf<LibraryTab>()

    val settingsFlow = settings.flow

    val profiles: StateFlow<List<ServerProfile>> =
        auth?.profiles ?: MutableStateFlow(emptyList())

    val librarySourceFilter: StateFlow<String> = settings.flow
        .map { it.librarySourceFilter }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settings.latestSettings?.librarySourceFilter ?: "ALL",
        )

    val healthState: StateFlow<FederatedHealthState> = repo.healthState

    val serverUrl: StateFlow<String> = combine(
        profiles,
        settings.flow.map { it.librarySourceFilter },
    ) { profileList, filter ->
        val matched = if (filter != "ALL" && filter != "DEVICE") {
            val pid = if (filter.startsWith("PROFILE:", ignoreCase = true)) {
                filter.substringAfter(":").trim()
            } else {
                filter
            }
            profileList.firstOrNull { it.id == pid }
        } else {
            null
        }
        (matched ?: profileList.firstOrNull { it.enabled } ?: profileList.firstOrNull())?.url.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setLibraryViewMode(mode: String) = viewModelScope.launch {
        settings.setLibraryViewMode(mode)
    }

    fun setLibrarySourceFilter(filterKey: String) {
        val normalized = filterKey.trim().ifBlank { "ALL" }
        viewModelScope.launch {
            settings.setLibrarySourceFilter(normalized)
            settings.setLibraryViewMode(
                if (normalized.equals("DEVICE", ignoreCase = true)) "DEVICE" else "SERVER",
            )
            if (!normalized.equals("DEVICE", ignoreCase = true)) {
                val changed = lastObservedSourceFilter != normalized
                lastObservedSourceFilter = normalized
                if (changed) loadAll()
                refresh.emitRefreshed(KEY_SOURCE_FILTER_RELOAD)
            }
        }
    }

    /** Cached songs for the pinned Cached collection row. */
    val cached: StateFlow<List<CachedTrackEntity>> = cachedTracks.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Resolved artist profile pictures (artistInfo image wins, cover art is the
     * synchronous fallback). Fetched lazily per visible artist and cached in
     * memory for the VM lifetime.
     */
    private val _artistImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val artistImages: StateFlow<Map<String, String>> = _artistImages.asStateFlow()

    /** Best-known picture right now: info image if loaded, else cover art. */
    fun artistPhotoUrl(artist: com.feishin.mobile.domain.model.Artist): String? =
        _artistImages.value[artist.id]?.takeIf { it.isNotBlank() } ?: artist.coverArt

    fun prefetchArtistPhoto(artist: com.feishin.mobile.domain.model.Artist) {
        if (_artistImages.value.containsKey(artist.id)) return
        viewModelScope.launch {
            val info = (repo.artistInfo(artist.id) as? Result.Ok)?.value?.imageUrl
                ?.takeIf { it.isNotBlank() && !com.feishin.mobile.core.common.AvatarFallback.isPlaceholderUrl(it) }
            // Navidrome artist records frequently carry no cover art, so the
            // first album cover is the last visual fallback before the initial.
            val albumCover = if (info == null) {
                (repo.artist(artist.id) as? Result.Ok)?.value?.albums
                    ?.firstOrNull { it.coverArt?.isNotBlank() == true }?.coverArt
                    ?.let { repo.coverArtUrl(it, 256) }
            } else null
            _artistImages.value = _artistImages.value + (artist.id to (info ?: albumCover.orEmpty()))
        }
    }

    private val _albums = MutableStateFlow<UiState<List<Album>>>(UiState.Loading)
    val albums: StateFlow<UiState<List<Album>>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<UiState<List<Artist>>>(UiState.Loading)
    val artists: StateFlow<UiState<List<Artist>>> = _artists.asStateFlow()

    private val _genres = MutableStateFlow<UiState<List<Genre>>>(UiState.Loading)
    val genres: StateFlow<UiState<List<Genre>>> = _genres.asStateFlow()

    private val _playlists = MutableStateFlow<UiState<List<Playlist>>>(UiState.Loading)
    val playlists: StateFlow<UiState<List<Playlist>>> = _playlists.asStateFlow()

    private val _localPlaylistSources = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val localPlaylistSources: StateFlow<Map<String, Set<String>>> = _localPlaylistSources.asStateFlow()

    private val _favs = MutableStateFlow<UiState<StarredCollection>>(UiState.Loading)
    val favs: StateFlow<UiState<StarredCollection>> = _favs.asStateFlow()

    private val _songs = MutableStateFlow<UiState<List<Track>>>(UiState.Loading)
    val songs: StateFlow<UiState<List<Track>>> = _songs.asStateFlow()

    /**
     * True while a network refresh runs over data that is already on screen:
     * drives the small inline indicator, never a full-screen spinner.
     */
    val refreshing: StateFlow<Boolean> = refresh.active
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), false)

    /** A background reload failed; old data stays and a small note appears. */
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed.asStateFlow()
    private var failedJob: Job? = null

    private var albumOffset = 0
    private val albumAccum = mutableListOf<Album>()
    private var albumEnd = false
    private var albumJob: Job? = null
    private var albumLoadGen = 0L
    private var artistsJob: Job? = null
    private var genresJob: Job? = null
    private var playlistsJob: Job? = null
    private var favsJob: Job? = null
    private var songsJob: Job? = null
    private var lastObservedSourceFilter: String? = null

    companion object {
        const val KEY_TAB = "selected_tab"
        const val KEY_SOURCE_FILTER_RELOAD = "__source_filter_reload__"
    }

    init {
        loadAll()
        // A background revalidation landed: read the entry back (instant, and
        // throttled by the policy, so this cannot loop) and swap the list in
        // place. Same payload = no visual change, so nothing jumps.
        viewModelScope.launch {
            refresh.refreshed.collect { key -> reloadFor(key) }
        }
        viewModelScope.launch {
            refresh.stale.collect {
                _refreshFailed.value = true
                failedJob?.cancel()
                failedJob = viewModelScope.launch {
                    delay(3_000)
                    _refreshFailed.value = false
                }
            }
        }
        // Stay in sync: starring anywhere refreshes favorites, and adding
        // tracks to a playlist refreshes covers/counts.
        viewModelScope.launch {
            playlistEvents.events.collect { e ->
                when (e) {
                    is PlaylistEvent.FavoritesChanged -> {
                        // The change is already local; make sure the read
                        // behind it does not serve the pre-star payload.
                        refresh.invalidate(MetadataCacheKeys.STARRED)
                        loadFavs(silent = true)
                    }
                    // Silent: adding tracks shouldn't flash the list to Loading.
                    is PlaylistEvent.TracksAdded -> {
                        // Cover/count changed on the server; skip the stale copy.
                        refresh.invalidate(MetadataCacheKeys.PLAYLISTS)
                        loadPlaylists(silent = true)
                    }
                }
            }
        }
        // Per-tab layout + sort follow the Appearance settings live.
        viewModelScope.launch {
            settings.flow.collect { s ->
                _layouts.value = LibraryTab.entries.associateWith { tab ->
                    s.libraryLayouts[tab.name] ?: s.defaultLayout
                }
                val next = LibraryTab.entries.associateWith { tab ->
                    s.librarySorts[tab.name] ?: LibrarySorts.default(tab.name)
                }
                val changed = next.filter { (tab, choice) -> _sorts.value[tab] != choice }.keys
                _sorts.value = next
                if (changed.isNotEmpty()) reapplySorts(changed)

                val filter = s.librarySourceFilter
                if (lastObservedSourceFilter == null) {
                    lastObservedSourceFilter = filter
                } else if (lastObservedSourceFilter != filter) {
                    lastObservedSourceFilter = filter
                    if (!filter.equals("DEVICE", ignoreCase = true)) {
                        loadAll()
                    }
                }
            }
        }
        auth?.let { authRepo ->
            viewModelScope.launch {
                var first = true
                authRepo.profiles.collect {
                    if (first) {
                        first = false
                    } else if (!librarySourceFilter.value.equals("DEVICE", ignoreCase = true)) {
                        loadAll()
                    }
                }
            }
        }
    }

    fun selectTab(t: LibraryTab) {
        handle.set(KEY_TAB, t.name)
        _tab.value = t
    }

    /**
     * Applies a nav-arg tab at most once per distinct value, so a re-entry with
     * the route's default does not reset a tab the user picked themselves.
     */
    fun applyNavTab(t: LibraryTab?) {
        if (t == null) return
        if (!appliedNavTabs.add(t)) return
        if (_tab.value == t) return
        selectTab(t)
    }

    fun layoutFor(tab: LibraryTab): LayoutMode =
        _layouts.value[tab] ?: LayoutMode.GRID

    fun toggleLayout(tab: LibraryTab) {
        val next = if (layoutFor(tab) == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID
        viewModelScope.launch { settings.setLibraryLayout(tab.name, next) }
    }

    fun sortFor(tab: LibraryTab): SortChoice =
        _sorts.value[tab] ?: LibrarySorts.default(tab.name)

    fun setSort(tab: LibraryTab, choice: SortChoice) {
        viewModelScope.launch { settings.setLibrarySort(tab.name, choice) }
    }

    fun queryFor(tab: LibraryTab): String = _queries.value[tab].orEmpty()

    fun setQuery(tab: LibraryTab, query: String) {
        _queries.value = _queries.value + (tab to query)
    }

    fun clearQuery(tab: LibraryTab) {
        _queries.value = _queries.value - tab
    }

    fun refresh() {
        // Pull-to-refresh is the manual full refresh: the next pass bypasses
        // the cache, and it runs silently so the lists that are already on
        // screen never flash back to a spinner.
        refresh.invalidateAll()
        loadAll()
    }

    /**
     * First paint: whatever the cache holds comes up at once, each tab then
     * revalidates itself in the background. Nothing is forced here.
     */
    private fun loadAll() {
        loadAlbums(reset = true, silent = true)
        loadArtists(silent = true)
        refreshGenres(silent = true)
        loadPlaylists(silent = true)
        loadFavs(silent = true)
        loadSongs(silent = true)
    }

    /**
     * One refreshed cache entry -> the matching tab. Read-back only, so it is
     * instant; album paging is never thrown away for a first-page refresh.
     */
    private fun reloadFor(key: String) {
        val logicalKey = when {
            key.startsWith("albums:") ||
                key.startsWith("songs:") ||
                key == MetadataCacheKeys.ARTISTS ||
                key == MetadataCacheKeys.GENRES ||
                key == MetadataCacheKeys.PLAYLISTS ||
                key == MetadataCacheKeys.STARRED -> key
            ':' in key -> key.substringAfter(':')
            else -> key
        }
        when {
            logicalKey.startsWith("albums:") -> if (albumAccum.size <= 100) loadAlbums(reset = true, silent = true)
            logicalKey == MetadataCacheKeys.ARTISTS -> loadArtists(silent = true)
            logicalKey == MetadataCacheKeys.GENRES -> refreshGenres(silent = true)
            logicalKey == MetadataCacheKeys.PLAYLISTS -> loadPlaylists(silent = true)
            logicalKey == MetadataCacheKeys.STARRED -> loadFavs(silent = true)
            logicalKey.startsWith("songs:") -> loadSongs(silent = true)
        }
    }

    fun loadAlbums(reset: Boolean = false, silent: Boolean = false) {
        if (reset) {
            albumJob?.cancel()
            albumOffset = 0
            albumAccum.clear()
            albumEnd = false
        } else if (albumEnd || albumJob?.isActive == true) {
            return
        }
        val gen = if (reset) ++albumLoadGen else albumLoadGen
        albumJob = viewModelScope.launch {
            if (reset && !silent) _albums.value = UiState.Loading
            when (val r = repo.albumList("alphabeticalByName", 100, albumOffset)) {
                is Result.Ok -> {
                    if (gen != albumLoadGen || !isActive) return@launch
                    if (r.value.isEmpty()) {
                        albumEnd = true
                    } else {
                        albumOffset += r.value.size
                        val seen = albumAccum.mapTo(HashSet(albumAccum.size)) { it.id }
                        for (album in r.value) {
                            if (seen.add(album.id)) {
                                albumAccum.add(album)
                            }
                        }
                    }
                    applyAlbumSort()
                }
                is Result.Err -> {
                    if (gen != albumLoadGen || !isActive) return@launch
                    if (albumAccum.isEmpty() && _albums.value !is UiState.Data) {
                        _albums.value = UiState.Error(r.message)
                    }
                }
            }
        }
    }

    private fun applyAlbumSort() {
        val sorted = LibraryOrdering.sortAlbums(albumAccum.distinctBy { it.id }, sortFor(LibraryTab.ALBUMS))
        _albums.value = if (sorted.isEmpty()) UiState.Empty else UiState.Data(sorted)
    }

    /** Re-sorts already-loaded lists when a per-tab choice changes. */
    private fun reapplySorts(changed: Set<LibraryTab>) {
        if (LibraryTab.ALBUMS in changed) {
            // Same guard as every other list here, and for the same reason: a
            // re-sort may only rewrite a list that actually rendered. Without
            // it, the settings flow's first emission (which always reports
            // "changed", because _sorts starts empty) called applyAlbumSort()
            // unconditionally and overwrote an in-flight Loading - or a real
            // Error - with Empty. On a slow machine that clobber landed after
            // loadAlbums() had published its result, so a genuine load failure
            // was replaced by "no albums" and the message was lost.
            val current = (_albums.value as? UiState.Data)?.value
            if (current != null) {
                _albums.value = UiState.Data(
                    LibraryOrdering.sortAlbums(current.distinctBy { it.id }, sortFor(LibraryTab.ALBUMS)),
                )
            }
        }
        if (LibraryTab.ARTISTS in changed) {
            val current = (_artists.value as? UiState.Data)?.value
            if (current != null) {
                _artists.value = UiState.Data(
                    LibraryOrdering.sortArtists(current.distinctBy { it.id }, sortFor(LibraryTab.ARTISTS)),
                )
            }
        }
        if (LibraryTab.SONGS in changed) {
            val current = (_songs.value as? UiState.Data)?.value
            if (current != null) {
                _songs.value = UiState.Data(
                    LibraryOrdering.sortSongs(current.distinctBy { it.id }, sortFor(LibraryTab.SONGS)),
                )
            }
        }
        if (LibraryTab.PLAYLISTS in changed) {
            val current = (_playlists.value as? UiState.Data)?.value
            if (current != null) {
                _playlists.value = UiState.Data(
                    LibraryOrdering.sortPlaylists(current.distinctBy { it.id }, sortFor(LibraryTab.PLAYLISTS)),
                )
            }
        }
    }

    private fun loadArtists(silent: Boolean = false) {
        artistsJob?.cancel()
        artistsJob = viewModelScope.launch {
            if (!silent) _artists.value = UiState.Loading
            val r = repo.artists()
            if (!isActive) return@launch
            _artists.value = when (r) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortArtists(r.value.distinctBy { it.id }, sortFor(LibraryTab.ARTISTS)))
                is Result.Err -> if (_artists.value is UiState.Data) _artists.value else UiState.Error(r.message)
            }
        }
    }

    fun refreshGenres(silent: Boolean = false) {
        genresJob?.cancel()
        genresJob = viewModelScope.launch {
            if (!silent) _genres.value = UiState.Loading
            val r = repo.genres()
            if (!isActive) return@launch
            _genres.value = when (r) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value.distinctBy { it.name })
                is Result.Err -> if (_genres.value is UiState.Data) _genres.value else UiState.Error(r.message)
            }
        }
    }

    fun loadPlaylists(silent: Boolean = false) {
        playlistsJob?.cancel()
        playlistsJob = viewModelScope.launch {
            if (!silent) _playlists.value = UiState.Loading
            _localPlaylistSources.value = localPlaylists?.allPlaylistSourceMap().orEmpty()
            val r = repo.playlists()
            if (!isActive) return@launch
            _playlists.value = when (r) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortPlaylists(r.value.distinctBy { it.id }, sortFor(LibraryTab.PLAYLISTS)))
                is Result.Err -> if (_playlists.value is UiState.Data) _playlists.value else UiState.Error(r.message)
            }
        }
    }

    fun importM3uUri(
        contentResolver: ContentResolver,
        uri: Uri,
        onResult: (Result<M3uImportResult>) -> Unit,
    ) {
        val importer = m3uImporter ?: run {
            onResult(Result.Err("M3U importer unavailable"))
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = resolveDisplayName(contentResolver, uri) ?: "Imported Playlist.m3u"
                    val playlistName = fileName
                        .removeSuffix(".m3u8")
                        .removeSuffix(".M3U8")
                        .removeSuffix(".m3u")
                        .removeSuffix(".M3U")
                        .trim()
                        .ifBlank { "Imported Playlist" }
                    val loadedSongs = (_songs.value as? UiState.Data)?.value.orEmpty()
                    val lookup: (suspend () -> List<Track>)? = if (loadedSongs.isEmpty()) {
                        { (repo.randomSongs(500) as? Result.Ok)?.value.orEmpty() }
                    } else {
                        null
                    }
                    contentResolver.openInputStream(uri)?.use { stream ->
                        importer.import(
                            inputStream = stream,
                            playlistName = playlistName,
                            sourceM3uName = fileName,
                            candidateTracks = loadedSongs,
                            candidateTrackLookup = lookup,
                        )
                    } ?: throw IllegalStateException("Could not open selected file")
                }
            }
            outcome.fold(
                onSuccess = { imported ->
                    refresh.invalidate(MetadataCacheKeys.PLAYLISTS)
                    loadPlaylists(silent = true)
                    playlistEvents.emit(PlaylistEvent.TracksAdded(imported.playlistId))
                    onResult(Result.Ok(imported))
                },
                onFailure = { error ->
                    val message = when (error) {
                        is M3uFileTooLargeException -> "Playlist file exceeds the 5 MB limit"
                        else -> error.message ?: "Could not import playlist"
                    }
                    onResult(Result.Err(message))
                },
            )
        }
    }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun loadFavs(silent: Boolean = false) {
        favsJob?.cancel()
        favsJob = viewModelScope.launch {
            if (!silent) _favs.value = UiState.Loading
            val r = repo.starred()
            if (!isActive) return@launch
            _favs.value = when (r) {
                is Result.Ok -> {
                    val v = r.value
                    val unique = v.copy(
                        artists = v.artists.distinctBy { it.id },
                        albums = v.albums.distinctBy { it.id },
                        tracks = v.tracks.distinctBy { it.id },
                    )
                    if (unique.artists.isEmpty() && unique.albums.isEmpty() && unique.tracks.isEmpty()) UiState.Empty
                    else UiState.Data(unique)
                }
                is Result.Err -> if (_favs.value is UiState.Data) _favs.value else UiState.Error(r.message)
            }
        }
    }

    private fun loadSongs(silent: Boolean = false) {
        songsJob?.cancel()
        songsJob = viewModelScope.launch {
            if (!silent) _songs.value = UiState.Loading
            val r = repo.randomSongs(500)
            if (!isActive) return@launch
            _songs.value = when (r) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortSongs(r.value.distinctBy { it.id }, sortFor(LibraryTab.SONGS)))
                is Result.Err -> if (_songs.value is UiState.Data) _songs.value else UiState.Error(r.message)
            }
        }
    }
}
