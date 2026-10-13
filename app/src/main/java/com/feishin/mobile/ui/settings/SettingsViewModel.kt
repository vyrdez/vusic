package com.feishin.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of the "Test connection" button. */
sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data object Ok : TestState
    data class Failed(val message: String) : TestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: SettingsStore,
    private val repo: MusicDataRepository,
    private val auth: AuthRepository,
    val downloads: DownloadRepository,
    val cache: MetadataCache,
    val offlineSync: OfflineSyncStore,
    val network: NetworkMonitor,
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
    fun setCache(v: Int) = viewModelScope.launch { store.setCacheSizeMb(v) }
    fun setImageCache(v: Int) = viewModelScope.launch { store.setImageCacheMb(v) }
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

    fun clearPendingActions() = viewModelScope.launch { offlineSync.clear() }

    fun setServerUrl(url: String) = viewModelScope.launch { auth.updateServerUrl(url) }

    /** Logout, optionally keeping downloads on disk. */
    fun logout(keepDownloads: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        if (!keepDownloads) runCatching { downloads.removeAll() }
        cache.clear()
        runCatching { downloads.clearCoverCache() }
        onDone()
    }
}
