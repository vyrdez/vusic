package com.feishin.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(private val repo: MusicDataRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _ui = MutableStateFlow<UiState<SearchResults>>(UiState.Empty)
    val ui: StateFlow<UiState<SearchResults>> = _ui.asStateFlow()

    private var job: Job? = null

    fun onQuery(q: String) {
        _query.value = q
        job?.cancel()
        if (q.isBlank()) {
            _ui.value = UiState.Empty
            return
        }
        job = viewModelScope.launch {
            delay(350)
            _ui.value = UiState.Loading
            _ui.value = when (val r = repo.search(q.trim())) {
                is Result.Ok -> {
                    val v = r.value
                    if (v.artists.isEmpty() && v.albums.isEmpty() && v.tracks.isEmpty()) UiState.Empty else UiState.Data(v)
                }
                is Result.Err -> UiState.Error(r.message)
            }
        }
    }
}
