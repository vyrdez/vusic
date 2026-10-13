package com.feishin.mobile.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.data.db.DownloadEntity
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.data.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the Downloads screen. */
data class DownloadRow(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val sizeBytes: Long,
    val status: DownloadStatus,
    val progress: Float,
    val error: String?,
    val localPath: String?,
)

data class DownloadsUi(
    val rows: List<DownloadRow> = emptyList(),
    val totalBytes: Long = 0,
    val coverBytes: Long = 0,
    val activeCount: Int = 0,
) {
    val totalLabel: String get() = CacheSizePolicy.human(totalBytes)
    val coverLabel: String get() = CacheSizePolicy.human(coverBytes)
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
) : ViewModel() {

    private val coverBytes = kotlinx.coroutines.flow.MutableStateFlow(0L)

    val ui: StateFlow<DownloadsUi> = combine(
        repo.downloads,
        coverBytes,
    ) { list, covers ->
        DownloadsUi(
            rows = list.map { it.toRow() },
            totalBytes = list.filter { it.statusEnum == DownloadStatus.COMPLETED }
                .fold(0L) { acc, e -> acc + e.sizeBytes },
            coverBytes = covers,
            activeCount = list.count {
                it.statusEnum == DownloadStatus.QUEUED || it.statusEnum == DownloadStatus.DOWNLOADING
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUi())

    /** Kept for compatibility with the older call sites. */
    val busy = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    init {
        refreshCoverSize()
    }

    fun refreshCoverSize() {
        viewModelScope.launch { coverBytes.value = repo.coverCacheBytes() }
    }

    fun pause(songId: String) = viewModelScope.launch { repo.pause(songId) }
    fun resume(songId: String) = viewModelScope.launch { repo.resume(songId) }
    fun remove(songId: String) = viewModelScope.launch {
        repo.removeFromLibrary(songId)
        refreshCoverSize()
    }

    fun clearAll() = viewModelScope.launch {
        repo.removeAll()
        refreshCoverSize()
    }

    fun clearCompleted() = viewModelScope.launch {
        repo.clearCompleted()
        refreshCoverSize()
    }

    fun clearCoverCache() = viewModelScope.launch {
        repo.clearCoverCache()
        refreshCoverSize()
    }

    private fun DownloadEntity.toRow() = DownloadRow(
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        coverArt = coverArt,
        sizeBytes = sizeBytes,
        status = statusEnum,
        progress = progress,
        error = error,
        localPath = filePath,
    )
}
