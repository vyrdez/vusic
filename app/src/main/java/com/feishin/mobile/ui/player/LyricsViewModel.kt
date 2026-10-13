package com.feishin.mobile.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUi {
    data object Loading : LyricsUi
    data class Synced(val lyrics: SyncedLyrics) : LyricsUi
    data class Plain(val text: String) : LyricsUi
    data object None : LyricsUi
}

/**
 * Loads lyrics per track with an in-memory cache. Failures degrade to [LyricsUi.None]
 * and never affect playback.
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repo: MusicDataRepository,
) : ViewModel() {
    private val cache = LinkedHashMap<String, LyricsUi>(32)
    private val _ui = MutableStateFlow<LyricsUi>(LyricsUi.None)
    val ui: StateFlow<LyricsUi> = _ui.asStateFlow()
    private var job: Job? = null
    private var lastId: String? = null

    fun load(track: Track?) {
        val id = track?.id
        if (id == null) {
            job?.cancel()
            lastId = null
            _ui.value = LyricsUi.None
            return
        }
        if (id == lastId) return
        lastId = id
        job?.cancel()
        cache[id]?.let {
            _ui.value = it
            return
        }
        job = viewModelScope.launch {
            _ui.value = LyricsUi.Loading
            val next = when (val r = repo.lyricsForTrack(id, track.artist, track.title)) {
                is Result.Ok -> {
                    val parsed = r.value
                    when {
                        parsed.isEmpty -> LyricsUi.None
                        // A single "synced" line at 0 is plain text in practice.
                        parsed.lines.size == 1 -> LyricsUi.Plain(parsed.lines[0].text)
                        else -> LyricsUi.Synced(parsed)
                    }
                }
                is Result.Err -> LyricsUi.None
            }
            if (lastId == id) {
                cache[id] = next
                if (cache.size > 64) cache.remove(cache.keys.first())
                _ui.value = next
            }
        }
    }

    /**
     * Sets the star state optimistically: the caller flips its UI first, and this
     * reports back the confirmed state (reverted on server failure).
     */
    fun setStarred(track: Track, starred: Boolean, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            when (repo.starTrack(track.id, starred)) {
                is Result.Ok -> onDone(starred)
                is Result.Err -> onDone(track.starred)
            }
        }
    }
}
