package com.feishin.mobile.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab { ALBUMS, ARTISTS, GENRES, PLAYLISTS, FAVORITES }
enum class SortOrder { NAME_ASC, NAME_DESC, YEAR_DESC, PLAYS_DESC }

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repo: MusicDataRepository) : ViewModel() {
    private val _tab = MutableStateFlow(LibraryTab.ALBUMS)
    val tab: StateFlow<LibraryTab> = _tab.asStateFlow()

    private val _grid = MutableStateFlow(true)
    val grid: StateFlow<Boolean> = _grid.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.NAME_ASC)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

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

    private var albumOffset = 0
    private val albumAccum = mutableListOf<Album>()
    private var albumEnd = false

    init { refresh() }

    fun selectTab(t: LibraryTab) { _tab.value = t }

    fun toggleGrid() { _grid.value = !_grid.value }

    fun setSort(s: SortOrder) {
        _sort.value = s
        applyAlbumSort()
    }

    fun refresh() {
        loadAlbums(reset = true)
        loadArtists()
        loadGenres()
        loadPlaylists()
        loadFavs()
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
        val sorted = when (_sort.value) {
            SortOrder.NAME_ASC -> albumAccum.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> albumAccum.sortedByDescending { it.name.lowercase() }
            SortOrder.YEAR_DESC -> albumAccum.sortedByDescending { it.year ?: 0 }
            SortOrder.PLAYS_DESC -> albumAccum.sortedByDescending { it.playCount }
        }
        _albums.value = if (sorted.isEmpty()) UiState.Empty else UiState.Data(sorted)
    }

    private fun loadArtists() {
        viewModelScope.launch {
            _artists.value = UiState.Loading
            _artists.value = when (val r = repo.artists()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(
                    when (_sort.value) {
                        SortOrder.NAME_DESC -> r.value.sortedByDescending { it.name.lowercase() }
                        else -> r.value.sortedBy { it.name.lowercase() }
                    },
                )
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            _genres.value = UiState.Loading
            _genres.value = when (val r = repo.genres()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = UiState.Loading
            _playlists.value = when (val r = repo.playlists()) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value)
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
}
