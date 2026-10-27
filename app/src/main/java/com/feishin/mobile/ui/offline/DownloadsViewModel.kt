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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sort preference key for the Downloaded list, in the same store as playlists. */
private const val DOWNLOADS_SORT_KEY = "__downloads__"

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
    private val settings: com.feishin.mobile.data.settings.SettingsStore,
    private val network: com.feishin.mobile.data.network.NetworkMonitor,
    val messenger: com.feishin.mobile.ui.components.UiMessenger? = null,
) : ViewModel() {

    private val coverBytes = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /**
     * Why a queued download is not running right now, or null when it may run.
     *
     * A job blocked by the network constraint simply never starts, so the row
     * stays QUEUED and the reason has to come from the network state rather
     * than from the row. "Queued" on its own hides the fact that the phone is
     * on mobile data and the user asked for Wi-Fi only.
     */
    val waitingReason: StateFlow<String?> = combine(
        settings.flow,
        network.isOnline,
        network.unmetered,
        network.roaming,
    ) { s, online, unmetered, roaming ->
        com.feishin.mobile.core.common.MobileDataPolicy.waitingReason(
            online = online,
            unmetered = unmetered,
            roaming = roaming,
            allowMobileData = !s.downloadWifiOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Remembered per list, under the same key scheme as playlists. */
    private val _sort = kotlinx.coroutines.flow.MutableStateFlow(
        com.feishin.mobile.core.common.PlaylistOrdering.DEFAULT,
    )
    val sort: StateFlow<com.feishin.mobile.core.common.PlaylistSort> = _sort.asStateFlow()
    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        refreshCoverSize()
        viewModelScope.launch {
            settings.playlistSortFlow(DOWNLOADS_SORT_KEY).collect { _sort.value = it }
        }
    }

    /** Sets key and direction outright (the sort sheet owns the direction). */
    fun setSort(key: com.feishin.mobile.core.common.PlaylistSortKey, descending: Boolean) {
        val next = com.feishin.mobile.core.common.PlaylistSort(key, descending)
        if (next == _sort.value) return
        _sort.value = next
        viewModelScope.launch { settings.setPlaylistSort(DOWNLOADS_SORT_KEY, next) }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

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

    fun clearQueue() = viewModelScope.launch {
        repo.clearQueue()
        messenger?.show("Download queue cleared")
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
