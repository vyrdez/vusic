package com.feishin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.data.model.Album
import com.feishin.mobile.data.model.Song
import com.feishin.mobile.data.repo.MusicRepository
import com.feishin.mobile.util.Result
import com.feishin.mobile.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val recent: List<Album> = emptyList(),
    val newest: List<Album> = emptyList(),
    val frequent: List<Album> = emptyList(),
    val random: List<Album> = emptyList(),
    val starredSongs: List<Song> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: MusicRepository) : ViewModel() {
    private val _ui = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val ui: StateFlow<UiState<HomeData>> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = UiState.Loading
            val recent = async { repo.albumList("recent", 20, 0) }
            val newest = async { repo.albumList("newest", 20, 0) }
            val frequent = async { repo.albumList("frequent", 20, 0) }
            val random = async { repo.albumList("random", 20, 0) }
            val starred = async { repo.starred() }
            val data = HomeData(
                recent = (recent.await() as? Result.Ok)?.value.orEmpty(),
                newest = (newest.await() as? Result.Ok)?.value.orEmpty(),
                frequent = (frequent.await() as? Result.Ok)?.value.orEmpty(),
                random = (random.await() as? Result.Ok)?.value.orEmpty(),
                starredSongs = (starred.await() as? Result.Ok)?.value?.third.orEmpty(),
            )
            _ui.value = if (data.recent.isEmpty() && data.newest.isEmpty() && data.frequent.isEmpty() &&
                data.random.isEmpty() && data.starredSongs.isEmpty()
            ) {
                UiState.Empty
            } else {
                UiState.Data(data)
            }
        }
    }
}
