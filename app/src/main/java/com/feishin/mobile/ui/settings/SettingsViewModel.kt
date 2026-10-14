@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.domain.model.ScanStatus
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of the "Test connection" button. */
sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data object Ok : TestState
    data class Failed(val message: String) : TestState
}

/** One-line library totals for Server & Account. */
data class LibraryCounts(
    val artists: Int = 0,
    val albums: Int = 0,
    val playlists: Int = 0,
    val downloaded: Int = 0,
)

/** Storage numbers for the Storage screen and usage bar. */
data class StorageStats(
    val downloadBytes: Long = 0,
    val downloadSongs: Int = 0,
    val coverBytes: Long = 0,
    val streamCacheBytes: Long = 0,
    val imageCacheBytes: Long = 0,
    val cachedSongs: Int = 0,
    val cachedBytes: Long = 0,
    val freeBytes: Long = 0,
    val activeDownloads: Int = 0,
    val queuedDownloads: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: SettingsStore,
    private val repo: MusicDataRepository,
    private val auth: AuthRepository,
    val downloads: DownloadRepository,
    val cache: MetadataCache,
    val offlineSync: OfflineSyncStore,
    val network: NetworkMonitor,
    private val cachedTracks: CachedTrackStore,
    private val streamCache: androidx.media3.datasource.cache.SimpleCache,
    private val cacheEvictor: com.feishin.mobile.data.cache.LiveLruCacheEvictor,
    private val imageLoader: coil.ImageLoader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Credentials for the Account section (server URL, username). */
    val credentials: StateFlow<com.feishin.mobile.data.auth.Credentials?> = auth.loginState

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }
    fun setWifi(br: Int, fmt: String) = viewModelScope.launch { store.setWifi(br, fmt) }
    fun setMobile(br: Int, fmt: String) = viewModelScope.launch { store.setMobile(br, fmt) }
    fun setSelfSigned(v: Boolean) = viewModelScope.launch { store.setAllowSelfSigned(v) }
    fun setCleartext(v: Boolean) = viewModelScope.launch { store.setAllowCleartext(v) }
    fun setTimeout(v: Int) = viewModelScope.launch { store.setTimeout(v) }
    fun setCache(v: Int) = viewModelScope.launch {
        store.setCacheSizeMb(v)
        // Live: a smaller limit trims the cache right away.
        withContext(Dispatchers.IO) { runCatching { cacheEvictor.trimNow() } }
        refreshStorageStats()
    }

    fun setImageCache(v: Int) = viewModelScope.launch {
        val previous = runCatching { store.settingsNow().imageCacheMb }.getOrDefault(v)
        store.setImageCacheMb(v)
        // Coil's DiskCache capacity is fixed at construction: shrinking reclaims
        // space immediately by clearing, growing needs a restart (the UI offers
        // a "Restart now" button rather than silent text).
        if (v < previous) {
            withContext(Dispatchers.IO) { runCatching { imageLoader.diskCache?.clear() } }
            _imageCacheRestartPending.value = false
        } else if (v > previous) {
            _imageCacheRestartPending.value = true
        }
        refreshStorageStats()
    }

    /** True when the image cache grew and a restart is needed to apply it. */
    private val _imageCacheRestartPending = MutableStateFlow(false)
    val imageCacheRestartPending: StateFlow<Boolean> = _imageCacheRestartPending.asStateFlow()

    /** Relaunch the app process so a pending cache size takes effect. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
    fun setCrossfade(v: Boolean) = viewModelScope.launch { store.setCrossfade(v) }
    fun setCrossfadeSeconds(v: Int) = viewModelScope.launch { store.setCrossfadeSeconds(v) }
    fun setReplayGain(v: Boolean) = viewModelScope.launch { store.setReplayGain(v) }
    fun setReplayGainMode(v: ReplayGainMode) = viewModelScope.launch { store.setReplayGainMode(v) }
    fun setReplayGainPreamp(v: Int) = viewModelScope.launch { store.setReplayGainPreamp(v) }
    fun setReplayGainFallback(v: Int) = viewModelScope.launch { store.setReplayGainFallback(v) }
    fun setGapless(v: Boolean) = viewModelScope.launch { store.setGapless(v) }
    fun setPauseOnHeadphone(v: Boolean) = viewModelScope.launch { store.setPauseOnHeadphone(v) }
    fun setResumeOnFocusGain(v: Boolean) = viewModelScope.launch { store.setResumeOnFocusGain(v) }
    fun setOfflineMode(v: Boolean) = viewModelScope.launch { store.setOfflineMode(v) }
    fun setDownloadWifiOnly(v: Boolean) = viewModelScope.launch { store.setDownloadWifiOnly(v) }
    fun setDownloadOriginal(v: Boolean) = viewModelScope.launch { store.setDownloadOriginal(v) }
    fun setDownloadBitrate(v: Int) = viewModelScope.launch { store.setDownloadBitrate(v) }
    fun setDownloadFormat(v: String) = viewModelScope.launch { store.setDownloadFormat(v) }
    fun setDynamicNpColor(v: Boolean) = viewModelScope.launch { store.setDynamicNpColor(v) }
    fun setDefaultLayout(v: LayoutMode) = viewModelScope.launch { store.setDefaultLayout(v) }
    fun setShowOfflineChip(v: Boolean) = viewModelScope.launch { store.setShowOfflineChip(v) }
    fun setAutoOffline(v: Boolean) = viewModelScope.launch { store.setAutoOffline(v) }
    fun setRadioBatchSize(v: Int) = viewModelScope.launch { store.setRadioBatchSize(v) }
    fun setCacheUpcoming(v: Boolean) = viewModelScope.launch { store.setCacheUpcoming(v) }
    fun setTracksToCache(v: Int) = viewModelScope.launch { store.setTracksToCache(v) }
    fun setCachePlayedSongs(v: Boolean) = viewModelScope.launch { store.setCachePlayedSongs(v) }
    fun setCacheWifiOnly(v: Boolean) = viewModelScope.launch { store.setCacheWifiOnly(v) }
    fun setMaxDownloadMb(v: Int) = viewModelScope.launch {
        store.setMaxDownloadMb(v)
        refreshStorageStats()
    }

    /** Device total storage for the slider maxima (StatFs on the data dir). */
    fun deviceMaxBytes(): Long = com.feishin.mobile.core.common.StorageLimits.deviceMaxBytes {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.totalBytes
    }
    fun setLibraryLayout(tab: String, v: LayoutMode) = viewModelScope.launch { store.setLibraryLayout(tab, v) }
    fun setLibrarySort(tab: String, option: String, ascending: Boolean) =
        viewModelScope.launch {
            store.setLibrarySort(tab, com.feishin.mobile.core.common.SortChoice(option, ascending))
        }
    fun resetPlaybackDefaults() = viewModelScope.launch { store.resetPlaybackDefaults() }

    fun testConnection() {
        _test.value = TestState.Testing
        viewModelScope.launch {
            _test.value = when (val r = repo.ping()) {
                is com.feishin.mobile.core.common.Result.Ok -> TestState.Ok
                is com.feishin.mobile.core.common.Result.Err -> TestState.Failed(r.message)
            }
        }
    }

    /** Clears the library metadata cache (screens fall back to the network). */
    fun clearMetadataCache() = viewModelScope.launch { cache.clear() }

    fun clearImageCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { imageLoader.diskCache?.clear() } }
        downloads.clearCoverCache()
        refreshStorageStats()
    }

    fun deleteAllDownloads() = viewModelScope.launch {
        downloads.removeAll()
        refreshStorageStats()
    }

    fun clearPendingActions() = viewModelScope.launch { offlineSync.clear() }

    fun setServerUrl(url: String) = viewModelScope.launch { auth.updateServerUrl(url) }

    /** Logout, optionally keeping downloads on disk. */
    fun logout(keepDownloads: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        if (!keepDownloads) runCatching { downloads.removeAll() }
        cache.clear()
        runCatching { downloads.clearCoverCache() }
        onDone()
    }

    // ---------- Server & Account ----------

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _scan = MutableStateFlow<ScanStatus?>(null)
    val scanStatus: StateFlow<ScanStatus?> = _scan.asStateFlow()

    private val _libraryCounts = MutableStateFlow(LibraryCounts())
    val libraryCounts: StateFlow<LibraryCounts> = _libraryCounts.asStateFlow()

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "This device" }

    fun refreshServerInfo() {
        viewModelScope.launch {
            _serverInfo.value = (repo.serverInfo() as? com.feishin.mobile.core.common.Result.Ok)?.value
            refreshScanStatus()
            refreshLibraryCounts()
        }
    }

    fun startScan() {
        viewModelScope.launch {
            if (repo.startScan() is com.feishin.mobile.core.common.Result.Ok) {
                _scan.value = ScanStatus(scanning = true, count = _scan.value?.count ?: 0)
            }
            refreshScanStatus()
        }
    }

    fun refreshScanStatus() {
        viewModelScope.launch {
            _scan.value = (repo.scanStatus() as? com.feishin.mobile.core.common.Result.Ok)?.value
        }
    }

    fun refreshLibraryCounts() {
        viewModelScope.launch {
            val artists = (repo.artists() as? com.feishin.mobile.core.common.Result.Ok)?.value.orEmpty()
            val playlists = (repo.playlists() as? com.feishin.mobile.core.common.Result.Ok)?.value.orEmpty()
            val downloaded = downloads.completedIds().size
            _libraryCounts.value = LibraryCounts(
                artists = artists.size,
                albums = artists.sumOf { it.albumCount },
                playlists = playlists.size,
                downloaded = downloaded,
            )
        }
    }

    // ---------- Storage ----------

    private val _storage = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storage.asStateFlow()

    fun refreshStorageStats() {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val done = downloads.completedTracks()
                StorageStats(
                    downloadBytes = done.fold(0L) { acc, e -> acc + e.sizeBytes },
                    downloadSongs = done.size,
                    coverBytes = downloads.coverCacheBytes(),
                    streamCacheBytes = runCatching { streamCache.cacheSpace }.getOrDefault(0L),
                    imageCacheBytes = dirSize(File(context.cacheDir, "coil")),
                    cachedSongs = cachedTracks.count(),
                    cachedBytes = cachedTracks.all().fold(0L) { acc, e -> acc + e.sizeBytes },
                    freeBytes = runCatching {
                        val stat = StatFs(context.filesDir.absolutePath)
                        stat.availableBlocksLong * stat.blockSizeLong
                    }.getOrDefault(0L),
                    activeDownloads = downloads.activeCount(),
                    queuedDownloads = downloads.queuedCount(),
                )
            }
            _storage.value = stats
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
        return total
    }

    /** Clears the streamed-song cache and drops the Cached collection rows. */
    fun clearSongCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { cachedTracks.clearSongCache() }
        refreshStorageStats()
    }

    /** Battery exemption state + the system request row. */
    fun batteryExempted(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryExemption() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}
