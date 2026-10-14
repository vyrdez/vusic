package com.feishin.mobile.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.cache.toTrack
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val playlistEvents: PlaylistEvents,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ui = MutableStateFlow<UiState<AlbumDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<AlbumDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.album(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
            _refreshing.value = false
        }
    }

    /** Optimistic star toggle: flips UI immediately, rolls back on failure. */
    fun toggleStar(starred: Boolean) {
        val current = (_ui.value as? UiState.Data)?.value ?: return
        _ui.value = UiState.Data(current.copy(starred = !starred))
        viewModelScope.launch {
            when (repo.starAlbum(id, !starred)) {
                is Result.Ok -> playlistEvents.emit(PlaylistEvent.FavoritesChanged)
                is Result.Err -> _ui.value = UiState.Data(current)
            }
        }
    }
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val playlistEvents: PlaylistEvents,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ui = MutableStateFlow<UiState<ArtistDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<ArtistDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.artist(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
            _refreshing.value = false
        }
    }

    /** Optimistic star toggle: flips UI immediately, rolls back on failure. */
    fun toggleStar(starred: Boolean) {
        val current = (_ui.value as? UiState.Data)?.value ?: return
        _ui.value = UiState.Data(current.copy(starred = !starred))
        viewModelScope.launch {
            when (repo.starArtist(id, !starred)) {
                is Result.Ok -> playlistEvents.emit(PlaylistEvent.FavoritesChanged)
                is Result.Err -> _ui.value = UiState.Data(current)
            }
        }
    }

    private val _info = MutableStateFlow<ArtistInfo?>(null)
    val info: StateFlow<ArtistInfo?> = _info.asStateFlow()

    private val _topSongs = MutableStateFlow<UiState<List<Track>>>(UiState.Loading)
    val topSongs: StateFlow<UiState<List<Track>>> = _topSongs.asStateFlow()

    fun loadExtras(artistName: String) {
        viewModelScope.launch {
            _info.value = (repo.artistInfo(id) as? Result.Ok)?.value
            _topSongs.value = when (val r = repo.topSongs(artistName, 10)) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value)
                is Result.Err -> UiState.Empty
            }
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val playlistEvents: PlaylistEvents,
    private val cachedTracks: com.feishin.mobile.data.cache.CachedTrackStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    /** The built-in Favorite songs collection is virtual: no delete/rename. */
    val isVirtualFavorites: Boolean = id == FAVORITE_SONGS_PLAYLIST_ID
    /** The built-in Cached collection is virtual: no delete/rename/remove. */
    val isVirtualCached: Boolean = id == com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ui = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<PlaylistDetail>> = _ui.asStateFlow()

    init {
        refresh()
        // Added tracks refresh this playlist's cover; star changes re-sync
        // the Favorite songs collection.
        viewModelScope.launch {
            playlistEvents.events.collect { e ->
                when (e) {
                    is PlaylistEvent.TracksAdded -> if (e.playlistId == id) refresh()
                    is PlaylistEvent.FavoritesChanged -> if (isVirtualFavorites) refresh()
                }
            }
        }
    }

    /** "Clear cache" from the Cached collection menu. */
    fun clearCachedCollection(onDone: () -> Unit = {}) {
        if (!isVirtualCached) return
        viewModelScope.launch {
            cachedTracks.clearSongCache()
            refresh()
            onDone()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _ui.value = UiState.Loading
            _ui.value = if (isVirtualCached) {
                // Drop rows the LRU evictor already removed, then list newest first.
                cachedTracks.pruneEvicted()
                val tracks = cachedTracks.all().map { it.toTrack() }
                if (tracks.isEmpty()) UiState.Empty
                else UiState.Data(
                    PlaylistDetail(
                        id = com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID,
                        name = "Cached",
                        songCount = tracks.size,
                        durationSec = tracks.sumOf { it.durationSec },
                        tracks = tracks,
                    ),
                )
            } else if (isVirtualFavorites) {
                when (val r = repo.starred()) {
                    is Result.Ok -> {
                        val tracks = r.value.tracks
                        if (tracks.isEmpty()) UiState.Empty
                        else UiState.Data(
                            PlaylistDetail(
                                id = FAVORITE_SONGS_PLAYLIST_ID,
                                name = "Favorite songs",
                                songCount = tracks.size,
                                durationSec = tracks.sumOf { it.durationSec },
                                tracks = tracks,
                            ),
                        )
                    }
                    is Result.Err -> UiState.Error(r.message)
                }
            } else {
                when (val r = repo.playlist(id)) {
                    is Result.Ok -> UiState.Data(r.value)
                    is Result.Err -> UiState.Error(r.message)
                }
            }
            _refreshing.value = false
        }
    }

    fun removeAt(index: Int) {
        if (isVirtualFavorites) return
        viewModelScope.launch {
            repo.removeFromPlaylist(id, listOf(index))
            refresh()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (isVirtualFavorites) return
        viewModelScope.launch {
            repo.deletePlaylist(id)
            onDone()
        }
    }
}
