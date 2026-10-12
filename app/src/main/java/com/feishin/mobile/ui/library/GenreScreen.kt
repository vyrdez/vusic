package com.feishin.mobile.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.ui.EmptyBox
import com.feishin.mobile.ui.ErrorBox
import com.feishin.mobile.ui.LoadingBox
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.rememberSavableGridState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GenreViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val genre: String = savedStateHandle.get<String>("name") ?: ""
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ui = MutableStateFlow<UiState<List<Album>>>(UiState.Loading)
    val ui: StateFlow<UiState<List<Album>>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.albumList("byGenre", 100, 0, genre)) {
                is Result.Ok -> if (r.value.isEmpty()) UiState.Empty else UiState.Data(r.value)
                is Result.Err -> UiState.Error(r.message)
            }
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreScreen(
    vm: GenreViewModel = hiltViewModel(),
    repo: MusicDataRepository,
    onAlbum: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    PullToRefreshBox(
        isRefreshing = ui is UiState.Loading,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val s = ui) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, vm::refresh)
            is UiState.Empty -> EmptyBox("No ${vm.genre} albums")
            is UiState.Data -> LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize(),
                state = rememberSavableGridState(),
                contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 160.dp),
            ) {
                items(s.value, key = { it.id }) { a ->
                    AlbumCard(a, repo.coverArtUrl(a.coverArt, 300), onClick = { onAlbum(a.id) })
                }
            }
        }
    }
}
