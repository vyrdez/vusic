package com.feishin.mobile.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.LibrarySorts
import com.feishin.mobile.core.common.LibraryOrdering
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { PLAYLISTS, DOWNLOADS, ALBUMS, ARTISTS, SONGS, FAVORITES }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val playlistEvents: PlaylistEvents,
    private val settings: SettingsStore,
    private val cachedTracks: CachedTrackStore,
    savedStateHandle: SavedStateHandle,
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

    private val _favs = MutableStateFlow<UiState<StarredCollection>>(UiState.Loading)
    val favs: StateFlow<UiState<StarredCollection>> = _favs.asStateFlow()

    private val _songs = MutableStateFlow<UiState<List<Track>>>(UiState.Loading)
    val songs: StateFlow<UiState<List<Track>>> = _songs.asStateFlow()

    private var albumOffset = 0
    private val albumAccum = mutableListOf<Album>()
    private var albumEnd = false

    companion object {
        const val KEY_TAB = "selected_tab"
    }

    init {
        refresh()
        // Stay in sync: starring anywhere refreshes favorites, and adding
        // tracks to a playlist refreshes covers/counts.
        viewModelScope.launch {
            playlistEvents.events.collect { e ->
                when (e) {
                    is PlaylistEvent.FavoritesChanged -> loadFavs()
                    // Silent: adding tracks shouldn't flash the list to Loading.
                    is PlaylistEvent.TracksAdded -> loadPlaylists(silent = true)
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
        loadAlbums(reset = true)
        loadArtists()
        refreshGenres()
        loadPlaylists()
        loadFavs()
        loadSongs()
    }

    fun loadAlbums(reset: Boolean = false) {
        if (reset) {
            albumOffset = 0; albumAccum.clear(); albumEnd = false
        }
        if (albumEnd) return
        viewModelScope.launch {
            if (reset) _albums.value = UiState.Loading
            when (val r = repo.albumList("alphabeticalByName", 100, albumOffset)) {
                is Result.Ok -> {
                    if (r.value.isEmpty()) albumEnd = true
                    albumOffset += r.value.size
                    albumAccum.addAll(r.value)
                    applyAlbumSort()
                }
                is Result.Err -> if (albumAccum.isEmpty()) _albums.value = UiState.Error(r.message)
            }
        }
    }

    private fun applyAlbumSort() {
        val sorted = LibraryOrdering.sortAlbums(albumAccum, sortFor(LibraryTab.ALBUMS))
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
                _albums.value = UiState.Data(LibraryOrdering.sortAlbums(current, sortFor(LibraryTab.ALBUMS)))
            }
        }
        if (LibraryTab.ARTISTS in changed) {
            val current = (_artists.value as? UiState.Data)?.value
            if (current != null) {
                _artists.value = UiState.Data(LibraryOrdering.sortArtists(current, sortFor(LibraryTab.ARTISTS)))
            }
        }
        if (LibraryTab.SONGS in changed) {
            val current = (_songs.value as? UiState.Data)?.value
            if (current != null) {
                _songs.value = UiState.Data(LibraryOrdering.sortSongs(current, sortFor(LibraryTab.SONGS)))
            }
        }
        if (LibraryTab.PLAYLISTS in changed) {
            val current = (_playlists.value as? UiState.Data)?.value
            if (current != null) {
                _playlists.value = UiState.Data(LibraryOrdering.sortPlaylists(current, sortFor(LibraryTab.PLAYLISTS)))
            }
        }
    }

    private fun loadArtists() {
        viewModelScope.launch {
            _artists.value = UiState.Loading
            _artists.value = when (val r = repo.artists()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortArtists(r.value, sortFor(LibraryTab.ARTISTS)))
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    fun refreshGenres() {
        viewModelScope.launch {
            _genres.value = UiState.Loading
            _genres.value = when (val r = repo.genres()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    fun loadPlaylists(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _playlists.value = UiState.Loading
            _playlists.value = when (val r = repo.playlists()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortPlaylists(r.value, sortFor(LibraryTab.PLAYLISTS)))
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    private fun loadFavs() {
        viewModelScope.launch {
            _favs.value = UiState.Loading
            _favs.value = when (val r = repo.starred()) {
                is Result.Ok -> {
                    val v = r.value
                    if (v.artists.isEmpty() && v.albums.isEmpty() && v.tracks.isEmpty()) UiState.Empty else UiState.Data(v)
                }
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            _songs.value = UiState.Loading
            _songs.value = when (val r = repo.randomSongs(500)) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty
                else UiState.Data(LibraryOrdering.sortSongs(r.value, sortFor(LibraryTab.SONGS)))
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }
}
