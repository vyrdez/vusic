package com.feishin.mobile.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
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
                is Result.Ok -> Unit
                is Result.Err -> _ui.value = UiState.Data(current)
            }
        }
    }
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
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
                is Result.Ok -> Unit
                is Result.Err -> _ui.value = UiState.Data(current)
            }
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ui = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<PlaylistDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.playlist(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
            _refreshing.value = false
        }
    }

    fun removeAt(index: Int) {
        viewModelScope.launch {
            repo.removeFromPlaylist(id, listOf(index))
            refresh()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deletePlaylist(id)
            onDone()
        }
    }
}
