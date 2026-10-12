package com.feishin.mobile.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.data.model.AlbumDetail
import com.feishin.mobile.data.model.ArtistDetail
import com.feishin.mobile.data.model.PlaylistDetail
import com.feishin.mobile.data.repo.MusicRepository
import com.feishin.mobile.util.Result
import com.feishin.mobile.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repo: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _ui = MutableStateFlow<UiState<AlbumDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<AlbumDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.album(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }

    fun toggleStar(starred: Boolean) {
        viewModelScope.launch {
            repo.starAlbum(id, !starred)
            refresh()
        }
    }
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repo: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _ui = MutableStateFlow<UiState<ArtistDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<ArtistDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.artist(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repo: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = savedStateHandle.get<String>("id") ?: ""
    private val _ui = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Loading)
    val ui: StateFlow<UiState<PlaylistDetail>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.playlist(id)) {
                is Result.Ok -> UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
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
