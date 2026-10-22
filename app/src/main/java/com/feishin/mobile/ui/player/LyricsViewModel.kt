package com.feishin.mobile.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsCandidate
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.lyrics.LrclibLyricsProvider
import com.feishin.mobile.data.repo.LyricsRepository
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LyricsUi {
    data object Loading : LyricsUi
    data class Synced(val lyrics: Lyrics) : LyricsUi
    data class Plain(val text: String, val source: LyricsSource = LyricsSource.LRCLIB) : LyricsUi
    data class Instrumental(val source: LyricsSource = LyricsSource.LRCLIB) : LyricsUi
    data object None : LyricsUi
}

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val lrclib: LrclibLyricsProvider,
    val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow<LyricsUi>(LyricsUi.None)
    val ui: StateFlow<LyricsUi> = _ui.asStateFlow()

    private val _romanizedTracks = MutableStateFlow<Set<String>>(emptySet())
    val romanizedTracks: StateFlow<Set<String>> = _romanizedTracks.asStateFlow()

    private var currentJob: Job? = null
    private var currentKey: String? = null

    fun load(track: Track?) {
        if (track == null) {
            currentJob?.cancel()
            currentKey = null
            _ui.value = LyricsUi.None
            return
        }

        val key = repository.cacheKeyForTrack(track)
        if (key == currentKey) return
        currentKey = key

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _ui.value = LyricsUi.Loading

            val result = repository.getLyrics(track, bypassCache = false)
            if (currentKey == key) {
                when (result) {
                    is Result.Ok -> _ui.value = toUi(result.value)
                    is Result.Err -> _ui.value = LyricsUi.None
                }
            }
        }
    }

    /** Manually triggers a fresh lookup ignoring any cached results for this song. */
    fun searchAgain(track: Track?) {
        if (track == null) return
        val key = repository.cacheKeyForTrack(track)
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _ui.value = LyricsUi.Loading
            val result = repository.getLyrics(track, bypassCache = true)
            if (currentKey == key) {
                when (result) {
                    is Result.Ok -> _ui.value = toUi(result.value)
                    is Result.Err -> _ui.value = LyricsUi.None
                }
            }
        }
    }

    fun saveEditedLyrics(track: Track, newText: String) {
        viewModelScope.launch {
            val parsed = LrcParser.parse(newText, source = LyricsSource.CACHE)
            val toSave = if (parsed.lines.isNotEmpty()) {
                parsed
            } else {
                Lyrics(
                    lines = newText.lineSequence()
                        .filter { it.isNotBlank() }
                        .map { LyricLine(0L, it.trim()) }
                        .toList(),
                    kind = if (newText.isNotBlank()) LyricsKind.PLAIN else LyricsKind.NONE,
                    plainText = newText.trim(),
                    source = LyricsSource.CACHE,
                )
            }
            repository.saveCustomLyrics(track, toSave)
            _ui.value = toUi(toSave)
        }
    }

    fun setPerSongOffset(track: Track, offsetMs: Long) {
        viewModelScope.launch {
            val currentLyrics = (_ui.value as? LyricsUi.Synced)?.lyrics ?: return@launch
            val updated = currentLyrics.copy(offsetMs = offsetMs)
            repository.saveCustomLyrics(track, updated)
            _ui.value = toUi(updated)
        }
    }

    fun searchCandidates(
        title: String,
        artist: String,
        onResults: (List<LyricsCandidate>) -> Unit,
    ) {
        viewModelScope.launch {
            val results = lrclib.searchCandidates(title, artist)
            onResults(results)
        }
    }

    fun applyCandidate(track: Track, candidate: LyricsCandidate) {
        viewModelScope.launch {
            repository.saveCustomLyrics(track, candidate.lyrics)
            _ui.value = toUi(candidate.lyrics)
        }
    }

    fun toggleRomanize(trackId: String) {
        val current = _romanizedTracks.value
        _romanizedTracks.value = if (trackId in current) current - trackId else current + trackId
    }

    /** Prefetches lyrics for the upcoming track when conditions permit. */
    fun prefetchNext(track: Track?) {
        if (track == null) return
        viewModelScope.launch {
            repository.prefetch(track)
        }
    }

    private fun toUi(lyrics: Lyrics): LyricsUi = when {
        lyrics.isInstrumental -> LyricsUi.Instrumental(lyrics.source)
        lyrics.isPlain -> LyricsUi.Plain(
            text = lyrics.plainText.ifBlank { lyrics.lines.joinToString("\n") { it.text } },
            source = lyrics.source,
        )
        lyrics.isSynced -> LyricsUi.Synced(lyrics)
        else -> LyricsUi.None
    }
}
