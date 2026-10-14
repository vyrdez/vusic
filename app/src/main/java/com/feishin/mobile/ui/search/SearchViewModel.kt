package com.feishin.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.toDomainTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Single-type filter for the search results. */
enum class SearchFilter { ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS }

/**
 * Search with a type filter, debounced queries, stale-request cancellation,
 * and paged single-type views. Offline mode searches downloads + the cached
 * library locally instead of hitting the network.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _ui = MutableStateFlow<UiState<SearchResults>>(UiState.Empty)
    val ui: StateFlow<UiState<SearchResults>> = _ui.asStateFlow()

    /** How many single-type rows are revealed (infinite scroll pages of 20). */
    private val _visibleCount = MutableStateFlow(20)
    val visibleCount: StateFlow<Int> = _visibleCount.asStateFlow()

    /** Whether the songs section is expanded in place. */
    private val _songsExpanded = MutableStateFlow(false)
    val songsExpanded: StateFlow<Boolean> = _songsExpanded.asStateFlow()

    /** Shown above results when the search ran against downloads only. */
    private val _offlineNotice = MutableStateFlow(false)
    val offlineNotice: StateFlow<Boolean> = _offlineNotice.asStateFlow()

    private var job: Job? = null
    private var searchId = 0L

    fun onQuery(q: String) {
        _query.value = q
        _songsExpanded.value = false
        _visibleCount.value = 20
        runSearch()
    }

    fun onFilter(f: SearchFilter) {
        _filter.value = f
        _visibleCount.value = 20
        runSearch()
    }

    fun expandSongs() {
        _songsExpanded.value = true
    }

    fun showMore() {
        _visibleCount.value = _visibleCount.value + 20
    }

    private fun countsFor(filter: SearchFilter): Triple<Int, Int, Int> = when (filter) {
        SearchFilter.ALL -> Triple(20, 20, 50)
        SearchFilter.SONGS -> Triple(0, 0, 100)
        SearchFilter.ALBUMS -> Triple(0, 100, 0)
        SearchFilter.ARTISTS -> Triple(100, 0, 0)
        SearchFilter.PLAYLISTS -> Triple(20, 20, 50)
    }

    private fun runSearch() {
        val q = _query.value
        val f = _filter.value
        job?.cancel()
        if (q.isBlank()) {
            _ui.value = UiState.Empty
            return
        }
        val id = ++searchId
        job = viewModelScope.launch {
            delay(300)
            _ui.value = UiState.Loading
            val offline = isOffline()
            _offlineNotice.value = offline
            val result = if (offline) {
                Result.Ok(searchOffline(q, f))
            } else {
                val (ar, al, so) = countsFor(f)
                repo.search(q.trim(), ar, al, so)
            }
            // Stale responses (a newer query already started) are dropped.
            if (id != searchId) return@launch
            _ui.value = when (result) {
                is Result.Ok -> {
                    val v = result.value
                    if (v.artists.isEmpty() && v.albums.isEmpty() && v.tracks.isEmpty() && v.playlists.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Data(v)
                    }
                }
                is Result.Err -> UiState.Error(result.message)
            }
        }
    }

    private suspend fun isOffline(): Boolean =
        runCatching { settings.settingsNow().offlineMode }.getOrDefault(false) || !network.isOnline.value

    /** Local search over downloads when the network is unavailable. */
    private suspend fun searchOffline(query: String, filter: SearchFilter): SearchResults {
        val q = query.trim().lowercase()
        val done = downloads.completedTracks()
        fun match(vararg fields: String?): Boolean {
            if (q.isBlank()) return true
            return fields.any { it?.lowercase()?.contains(q) == true }
        }
        val tracks = done.filter { match(it.title, it.artist, it.album) }
            .map { it.toDomainTrack() }
        val albums = tracks.map { Album(it.albumId ?: it.album, it.album, it.artist, it.artistId, it.coverArt, 1, it.durationSec, 0, it.year, it.genre, false) }
            .distinctBy { it.id }.filter { match(it.name, it.artist) }
        val artists = tracks.map { Artist(it.artistId ?: it.artist, it.artist, it.coverArt, 1, false) }
            .distinctBy { it.id }.filter { match(it.name) }
        return when (filter) {
            SearchFilter.ALL -> SearchResults(artists, albums, tracks, emptyList())
            SearchFilter.SONGS -> SearchResults(emptyList(), emptyList(), tracks, emptyList())
            SearchFilter.ALBUMS -> SearchResults(emptyList(), albums, emptyList(), emptyList())
            SearchFilter.ARTISTS -> SearchResults(artists, emptyList(), emptyList(), emptyList())
            SearchFilter.PLAYLISTS -> SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    /** Tracks filtered by the active single-type view. */
    fun visibleTracks(res: SearchResults, visible: Int): List<Track> = res.tracks.take(visible)

    fun visibleAlbums(res: SearchResults, visible: Int): List<Album> = res.albums.take(visible)

    fun visibleArtists(res: SearchResults, visible: Int): List<Artist> = res.artists.take(visible)

    fun visiblePlaylists(res: SearchResults, visible: Int): List<Playlist> = res.playlists.take(visible)
}
