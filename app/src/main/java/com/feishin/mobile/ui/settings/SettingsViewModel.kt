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
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.FederatedHealthState
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

private class SettingsOptionalDeps(
    val downloads: DownloadRepository,
    val cache: MetadataCache,
    val offlineSync: OfflineSyncStore,
    val network: NetworkMonitor,
    val cachedTracks: CachedTrackStore,
    val streamCache: androidx.media3.datasource.cache.SimpleCache,
    val cacheEvictor: com.feishin.mobile.data.cache.LiveLruCacheEvictor,
    val downloadScheduler: com.feishin.mobile.data.download.DownloadScheduler,
    val imageLoader: coil.ImageLoader,
    val lyricsRepo: com.feishin.mobile.data.repo.LyricsRepository,
    val context: Context,
)

@HiltViewModel
class SettingsViewModel private constructor(
    val store: SettingsStore,
    private val repo: MusicDataRepository,
    private val auth: AuthRepository,
    deps: SettingsOptionalDeps?,
    val scanCoordinator: ScanCoordinator? = null,
    val libraryRefresh: com.feishin.mobile.data.cache.LibraryRefresh? = null,
    val messenger: com.feishin.mobile.ui.components.UiMessenger? = null,
    profileAuthStateStore: ProfileAuthStateStore? = null,
) : ViewModel() {

    val downloads: DownloadRepository? = deps?.downloads
    val cache: MetadataCache? = deps?.cache
    val offlineSync: OfflineSyncStore? = deps?.offlineSync
    val network: NetworkMonitor? = deps?.network
    private val cachedTracks: CachedTrackStore? = deps?.cachedTracks
    private val streamCache: androidx.media3.datasource.cache.SimpleCache? = deps?.streamCache
    private val cacheEvictor: com.feishin.mobile.data.cache.LiveLruCacheEvictor? = deps?.cacheEvictor
    private val downloadScheduler: com.feishin.mobile.data.download.DownloadScheduler? = deps?.downloadScheduler
    private val imageLoader: coil.ImageLoader? = deps?.imageLoader
    private val lyricsRepo: com.feishin.mobile.data.repo.LyricsRepository? = deps?.lyricsRepo
    private val context: Context? = deps?.context

    @Inject
    constructor(
        store: SettingsStore,
        repo: MusicDataRepository,
        auth: AuthRepository,
        downloads: DownloadRepository,
        cache: MetadataCache,
        offlineSync: OfflineSyncStore,
        network: NetworkMonitor,
        cachedTracks: CachedTrackStore,
        streamCache: androidx.media3.datasource.cache.SimpleCache,
        cacheEvictor: com.feishin.mobile.data.cache.LiveLruCacheEvictor,
        downloadScheduler: com.feishin.mobile.data.download.DownloadScheduler,
        imageLoader: coil.ImageLoader,
        lyricsRepo: com.feishin.mobile.data.repo.LyricsRepository,
        @ApplicationContext context: Context,
        profileAuthStateStore: ProfileAuthStateStore,
        scanCoordinator: ScanCoordinator? = null,
        libraryRefresh: com.feishin.mobile.data.cache.LibraryRefresh? = null,
        messenger: com.feishin.mobile.ui.components.UiMessenger? = null,
    ) : this(
        store = store,
        repo = repo,
        auth = auth,
        deps = SettingsOptionalDeps(
            downloads = downloads,
            cache = cache,
            offlineSync = offlineSync,
            network = network,
            cachedTracks = cachedTracks,
            streamCache = streamCache,
            cacheEvictor = cacheEvictor,
            downloadScheduler = downloadScheduler,
            imageLoader = imageLoader,
            lyricsRepo = lyricsRepo,
            context = context,
        ),
        scanCoordinator = scanCoordinator,
        libraryRefresh = libraryRefresh,
        messenger = messenger,
        profileAuthStateStore = profileAuthStateStore,
    )

    internal constructor(
        store: SettingsStore,
        repo: MusicDataRepository,
        auth: AuthRepository,
        scanCoordinator: ScanCoordinator? = null,
        libraryRefresh: com.feishin.mobile.data.cache.LibraryRefresh? = null,
        messenger: com.feishin.mobile.ui.components.UiMessenger? = null,
        profileAuthStateStore: ProfileAuthStateStore? = null,
    ) : this(
        store = store,
        repo = repo,
        auth = auth,
        deps = null,
        scanCoordinator = scanCoordinator,
        libraryRefresh = libraryRefresh,
        messenger = messenger,
        profileAuthStateStore = profileAuthStateStore,
    )

    val isOnline: StateFlow<Boolean> = network?.isOnline ?: MutableStateFlow(true)

    val uiMessenger: com.feishin.mobile.ui.components.UiMessenger =
        messenger ?: com.feishin.mobile.ui.components.UiMessenger()

    val coordinator: ScanCoordinator = scanCoordinator ?: ScanCoordinator(
        repo = repo,
        libraryRefresh = libraryRefresh ?: com.feishin.mobile.data.cache.LibraryRefresh(),
        messenger = uiMessenger,
        externalScope = viewModelScope,
    )

    val scanState: StateFlow<ScanUiState> = coordinator.state
    val profileScanStates: StateFlow<Map<String, ScanUiState>> = coordinator.profileStates
    val healthState: StateFlow<FederatedHealthState> = repo.healthState

    init {
        viewModelScope.launch {
            coordinator.state.collect { s ->
                if (s is ScanUiState.Finished) {
                    refreshLibraryCounts()
                }
            }
        }
        viewModelScope.launch {
            coordinator.profileStates.collect { map ->
                if (map.values.any { it is ScanUiState.Finished }) {
                    refreshLibraryCounts()
                }
            }
        }
    }

    val profiles: StateFlow<List<ServerProfile>> = auth.profiles

    /** Credentials compatibility shim for the Account section until P12.0-I. */
    val credentials: StateFlow<com.feishin.mobile.data.auth.Credentials?> = auth.profiles
        .map { list ->
            list.firstOrNull()?.let { p ->
                val sub = p.credentials as? com.feishin.mobile.domain.model.ProfileCredentials.Subsonic
                com.feishin.mobile.data.auth.Credentials(
                    serverUrl = p.url,
                    username = sub?.username ?: p.userId,
                    password = sub?.password.orEmpty(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val authStateStore: ProfileAuthStateStore = profileAuthStateStore ?: ProfileAuthStateStore()
    val authStates: StateFlow<Map<String, ProfileAuthStatus>> = authStateStore.states

    private val _expandedProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedProfileIds: StateFlow<Set<String>> = _expandedProfileIds.asStateFlow()

    fun toggleExpanded(profileId: String) {
        _expandedProfileIds.update { current ->
            if (profileId in current) current - profileId else current + profileId
        }
    }

    fun collapseAllProfiles() {
        if (_expandedProfileIds.value.isNotEmpty()) {
            _expandedProfileIds.value = emptySet()
        }
    }

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    private val _profileTestStates = MutableStateFlow<Map<String, TestState>>(emptyMap())
    val profileTestStates: StateFlow<Map<String, TestState>> = _profileTestStates.asStateFlow()

    private val _profileServerInfos = MutableStateFlow<Map<String, ServerInfo>>(emptyMap())
    val profileServerInfos: StateFlow<Map<String, ServerInfo>> = _profileServerInfos.asStateFlow()

    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }
    fun setWifi(br: Int, fmt: String) = viewModelScope.launch { store.setWifi(br, fmt) }
    fun setMobile(br: Int, fmt: String) = viewModelScope.launch { store.setMobile(br, fmt) }
    fun setSelfSigned(v: Boolean) = viewModelScope.launch { store.setAllowSelfSigned(v) }
    fun setCleartext(v: Boolean) = viewModelScope.launch { store.setAllowCleartext(v) }
    fun setTimeout(v: Int) = viewModelScope.launch { store.setTimeout(v) }
    fun setCache(v: Int) = viewModelScope.launch {
        store.setCacheSizeMb(v)
        // Live: a smaller limit trims the cache right away.
        withContext(Dispatchers.IO) { runCatching { cacheEvictor?.trimNow() } }
        refreshStorageStats()
    }

    fun setImageCache(v: Int) = viewModelScope.launch {
        val previous = runCatching { store.settingsNow().imageCacheMb }.getOrDefault(v)
        store.setImageCacheMb(v)
        // Coil's DiskCache capacity is fixed at construction: shrinking reclaims
        // space immediately by clearing, growing needs a restart (the UI offers
        // a "Restart now" button rather than silent text).
        if (v < previous) {
            withContext(Dispatchers.IO) { runCatching { imageLoader?.diskCache?.clear() } }
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
        val ctx = context ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
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
    /**
     * "Download over mobile data". Flipping it re-evaluates the queued work
     * against the new constraint straight away: a download that was waiting
     * for Wi-Fi starts on mobile data, and one that is running on mobile data
     * is paused until Wi-Fi is back. The progress is kept - it lives in the
     * database, not in the worker.
     */
    fun setDownloadOverMobileData(v: Boolean) = viewModelScope.launch {
        store.setDownloadWifiOnly(!v)
        runCatching { downloadScheduler?.reEvaluate() }
    }
    /**
     * "Auto-cache over mobile data". The prefetcher re-reads the setting on
     * every pass, so the next pass picks it up; the in-flight ones are dropped
     * so a cached song never half-arrives on the old rule.
     */
    fun setCacheOverMobileData(v: Boolean) = viewModelScope.launch { store.setCacheWifiOnly(!v) }
    fun setDownloadOriginal(v: Boolean) = viewModelScope.launch { store.setDownloadOriginal(v) }
    fun setDownloadBitrate(v: Int) = viewModelScope.launch { store.setDownloadBitrate(v) }
    fun setDownloadFormat(v: String) = viewModelScope.launch { store.setDownloadFormat(v) }
    fun setDynamicNpColor(v: Boolean) = viewModelScope.launch { store.setDynamicNpColor(v) }
    fun setMiniPlayerBgStyle(style: BackgroundStyle) = viewModelScope.launch { store.setMiniPlayerBgStyle(style) }
    fun setPlayerBgStyle(style: BackgroundStyle) = viewModelScope.launch { store.setPlayerBgStyle(style) }
    fun setQueueBgStyle(style: BackgroundStyle) = viewModelScope.launch { store.setQueueBgStyle(style) }
    fun setDefaultLayout(v: LayoutMode) = viewModelScope.launch { store.setDefaultLayout(v) }
    fun setShowOfflineChip(v: Boolean) = viewModelScope.launch { store.setShowOfflineChip(v) }
    fun setAutoOffline(v: Boolean) = viewModelScope.launch { store.setAutoOffline(v) }
    fun setRadioBatchSize(v: Int) = viewModelScope.launch { store.setRadioBatchSize(v) }
    fun setCacheUpcoming(v: Boolean) = viewModelScope.launch { store.setCacheUpcoming(v) }
    fun setTracksToCache(v: Int) = viewModelScope.launch { store.setTracksToCache(v) }
    fun setCachePlayedSongs(v: Boolean) = viewModelScope.launch { store.setCachePlayedSongs(v) }
    fun setMaxConcurrentDownloads(v: Int) = viewModelScope.launch { store.setMaxConcurrentDownloads(v) }
    fun setMaxDownloadMb(v: Int) = viewModelScope.launch {
        store.setMaxDownloadMb(v)
        refreshStorageStats()
    }
    fun setLyricsFetchOnline(v: Boolean) = viewModelScope.launch { store.setLyricsFetchOnline(v) }
    fun setLyricsSourcePriority(v: String) = viewModelScope.launch { store.setLyricsSourcePriority(v) }
    fun setLyricsAlignment(v: String) = viewModelScope.launch { store.setLyricsAlignment(v) }
    fun setLyricsTextSize(v: String) = viewModelScope.launch { store.setLyricsTextSize(v) }
    fun setLyricsAutoScroll(v: Boolean) = viewModelScope.launch { store.setLyricsAutoScroll(v) }
    fun setLyricsTapToSeek(v: Boolean) = viewModelScope.launch { store.setLyricsTapToSeek(v) }
    fun setLyricsWordHighlight(v: Boolean) = viewModelScope.launch { store.setLyricsWordHighlight(v) }
    fun setLyricsLineGapPercent(v: Int) = viewModelScope.launch { store.setLyricsLineGapPercent(v) }
    fun resetLyricsLineGap() = viewModelScope.launch { store.resetLyricsLineGap() }
    fun setLyricsKeepScreenOn(v: Boolean) = viewModelScope.launch { store.setLyricsKeepScreenOn(v) }
    fun setLyricsSyncOffsetMs(v: Int) = viewModelScope.launch { store.setLyricsSyncOffsetMs(v) }
    fun setLyricsShowUnsynced(v: Boolean) = viewModelScope.launch { store.setLyricsShowUnsynced(v) }
    fun setLyricsProviderOrder(order: List<String>) = viewModelScope.launch { store.setLyricsProviderOrder(order) }
    fun setLyricsProviderEnabled(enabled: Set<String>) = viewModelScope.launch { store.setLyricsProviderEnabled(enabled) }
    fun setLyricsPreferWordByWord(v: Boolean) = viewModelScope.launch { store.setLyricsPreferWordByWord(v) }
    fun setLyricsRomanizeAutomatically(v: Boolean) = viewModelScope.launch { store.setLyricsRomanizeAutomatically(v) }
    fun resetLyricsProviders() = viewModelScope.launch { store.resetLyricsProviders() }
    fun clearLyricsCache() = viewModelScope.launch { lyricsRepo?.clearCache() }
    fun setShowDeviceFiles(v: Boolean) = viewModelScope.launch { store.setShowDeviceFiles(v) }
    fun setDeviceFilesMinDurationSec(v: Int) = viewModelScope.launch { store.setDeviceFilesMinDurationSec(v) }
    fun setLibraryViewMode(v: String) = viewModelScope.launch { store.setLibraryViewMode(v) }

    /** Device total storage for the slider maxima (StatFs on the data dir). */
    fun deviceMaxBytes(): Long = com.feishin.mobile.core.common.StorageLimits.deviceMaxBytes {
        val path = context?.filesDir?.absolutePath ?: "/"
        val stat = StatFs(path)
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
    fun clearMetadataCache() = viewModelScope.launch { cache?.clear() }

    fun clearImageCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { imageLoader?.diskCache?.clear() } }
        downloads?.clearCoverCache()
        refreshStorageStats()
    }

    fun deleteAllDownloads() = viewModelScope.launch {
        downloads?.removeAll()
        refreshStorageStats()
    }

    fun clearPendingActions() = viewModelScope.launch { offlineSync?.clear() }

    fun setServerUrl(url: String) = viewModelScope.launch { auth.updateServerUrl(url) }

    fun setProfileEnabled(profileId: String, enabled: Boolean) = viewModelScope.launch {
        auth.setProfileEnabled(profileId, enabled)
        libraryRefresh?.invalidateAll()
        refreshLibraryCounts()
    }

    fun reorderProfiles(orderedIds: List<String>) = viewModelScope.launch {
        auth.reorderProfiles(orderedIds)
        libraryRefresh?.invalidateAll()
    }

    fun moveProfile(profileId: String, delta: Int) = viewModelScope.launch {
        val current = auth.profiles.value
        val idx = current.indexOfFirst { it.id == profileId }
        if (idx < 0) return@launch
        val target = (idx + delta).coerceIn(0, current.lastIndex)
        if (target == idx) return@launch
        val ids = current.map { it.id }.toMutableList()
        val moved = ids.removeAt(idx)
        ids.add(target, moved)
        auth.reorderProfiles(ids)
        libraryRefresh?.invalidateAll()
    }

    fun updateProfile(profile: ServerProfile) = viewModelScope.launch {
        auth.updateProfile(profile)
        libraryRefresh?.invalidateAll()
        refreshProfileInfo(profile.id)
        refreshLibraryCounts()
    }

    fun testProfileConnection(profile: ServerProfile) {
        _profileTestStates.update { it + (profile.id to TestState.Testing) }
        viewModelScope.launch {
            val sub = profile.credentials as? ProfileCredentials.Subsonic
            if (sub != null) {
                when (
                    val res = auth.probeSubsonic(
                        serverUrl = profile.url,
                        username = sub.username,
                        password = sub.password,
                        allowSelfSigned = profile.allowSelfSigned,
                        allowCleartext = profile.allowCleartext,
                    )
                ) {
                    is Result.Ok -> {
                        val ext = res.value.openSubsonicExtensions
                        if (ext != profile.openSubsonicExtensions) {
                            auth.updateProfile(profile.copy(openSubsonicExtensions = ext))
                        }
                        refreshProfileInfo(profile.id)
                        _profileTestStates.update { it + (profile.id to TestState.Ok) }
                    }
                    is Result.Err -> {
                        _profileTestStates.update { it + (profile.id to TestState.Failed(res.message)) }
                    }
                }
            } else {
                when (val r = repo.ping(profile.id)) {
                    is Result.Ok -> {
                        authStateStore.clear(profile.id)
                        refreshProfileInfo(profile.id)
                        _profileTestStates.update { it + (profile.id to TestState.Ok) }
                    }
                    is Result.Err -> {
                        _profileTestStates.update { it + (profile.id to TestState.Failed(r.message)) }
                    }
                }
            }
        }
    }

    fun startScan(profileId: String) {
        coordinator.startScan(profileId)
    }

    fun refreshScanStatus(profileId: String) {
        coordinator.refreshStatus(profileId)
    }

    fun updateProfileUrl(profileId: String, url: String) = viewModelScope.launch {
        val existing = auth.profiles.value.firstOrNull { it.id == profileId } ?: return@launch
        auth.updateProfile(existing.copy(url = url))
    }

    fun removeProfile(profileId: String, keepDownloads: Boolean, onAllRemoved: () -> Unit = {}) = viewModelScope.launch {
        val currentSettings = runCatching { store.settingsNow() }.getOrNull() ?: store.latestSettings
        val activeFilter = currentSettings?.librarySourceFilter
        if (activeFilter == "PROFILE:$profileId" || activeFilter == profileId) {
            store.setLibrarySourceFilter("ALL")
        }
        auth.removeProfile(profileId, keepDownloads)
        _expandedProfileIds.update { it - profileId }
        _profileTestStates.update { it - profileId }
        _profileServerInfos.update { it - profileId }
        refreshLibraryCounts()
        refreshStorageStats()
        if (auth.profiles.value.isEmpty()) {
            val standalone = currentSettings?.deviceFilesStandalone ?: false
            if (!standalone) {
                onAllRemoved()
            }
        }
    }

    /** Logout, optionally keeping downloads on disk. */
    fun logout(keepDownloads: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        if (!keepDownloads) runCatching { downloads?.removeAll() }
        cache?.clear()
        runCatching { downloads?.clearCoverCache() }
        onDone()
    }

    // ---------- Server & Account ----------

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    val scanStatus: StateFlow<ScanStatus?> = coordinator.state.map { s ->
        when (s) {
            is ScanUiState.Scanning -> ScanStatus(scanning = true, count = s.count, folderCount = s.folderCount, lastScan = s.lastScan)
            is ScanUiState.Finished -> ScanStatus(scanning = false, count = s.count, folderCount = s.folderCount, lastScan = s.lastScan)
            is ScanUiState.Idle -> ScanStatus(scanning = false, count = s.count, folderCount = s.folderCount, lastScan = s.lastScan)
            is ScanUiState.Error -> ScanStatus(scanning = false, count = s.count, folderCount = s.folderCount, lastScan = s.lastScan)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _libraryCounts = MutableStateFlow(LibraryCounts())
    val libraryCounts: StateFlow<LibraryCounts> = _libraryCounts.asStateFlow()

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "This device" }

    fun refreshServerInfo() {
        viewModelScope.launch {
            _serverInfo.value = (repo.serverInfo() as? Result.Ok)?.value
            refreshScanStatus()
            auth.profiles.value.forEach { profile ->
                if (profile.enabled) {
                    refreshProfileInfo(profile.id)
                    if (BackendCapabilities.forType(profile.type).supportsLibraryScan) {
                        coordinator.refreshStatus(profile.id)
                    }
                }
            }
            refreshLibraryCounts()
        }
    }

    private suspend fun refreshProfileInfo(profileId: String) {
        val info = (repo.serverInfo(profileId) as? Result.Ok)?.value ?: return
        _profileServerInfos.update { it + (profileId to info) }
    }

    fun startScan() {
        coordinator.startScan()
    }

    fun refreshScanStatus() {
        coordinator.refreshStatus()
    }

    fun refreshLibraryCounts() {
        viewModelScope.launch {
            val artists = (repo.artists() as? com.feishin.mobile.core.common.Result.Ok)?.value.orEmpty()
            val playlists = (repo.playlists() as? com.feishin.mobile.core.common.Result.Ok)?.value.orEmpty()
            val downloaded = downloads?.completedIds()?.size ?: 0
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
        val dl = downloads ?: return
        val ct = cachedTracks ?: return
        val ctx = context ?: return
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val done = dl.completedTracks()
                StorageStats(
                    downloadBytes = done.fold(0L) { acc, e -> acc + e.sizeBytes },
                    downloadSongs = done.size,
                    coverBytes = dl.coverCacheBytes(),
                    streamCacheBytes = runCatching { streamCache?.cacheSpace ?: 0L }.getOrDefault(0L),
                    imageCacheBytes = dirSize(File(ctx.cacheDir, "coil")),
                    cachedSongs = ct.count(),
                    cachedBytes = ct.all().fold(0L) { acc, e -> acc + e.sizeBytes },
                    freeBytes = runCatching {
                        val stat = StatFs(ctx.filesDir.absolutePath)
                        stat.availableBlocksLong * stat.blockSizeLong
                    }.getOrDefault(0L),
                    activeDownloads = dl.activeCount(),
                    queuedDownloads = dl.queuedCount(),
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
        withContext(Dispatchers.IO) { cachedTracks?.clearSongCache() }
        refreshStorageStats()
    }

    /** Battery exemption state + the system request row. */
    fun batteryExempted(): Boolean {
        val ctx = context ?: return true
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun requestBatteryExemption() {
        val ctx = context ?: return
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}
