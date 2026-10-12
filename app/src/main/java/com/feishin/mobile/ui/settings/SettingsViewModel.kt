package com.feishin.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: SettingsStore,
) : ViewModel() {
    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }
    fun setWifi(br: Int, fmt: String) = viewModelScope.launch { store.setWifi(br, fmt) }
    fun setMobile(br: Int, fmt: String) = viewModelScope.launch { store.setMobile(br, fmt) }
    fun setSelfSigned(v: Boolean) = viewModelScope.launch { store.setAllowSelfSigned(v) }
    fun setCache(v: Int) = viewModelScope.launch { store.setCacheSizeMb(v) }
    fun setCrossfade(v: Boolean) = viewModelScope.launch { store.setCrossfade(v) }
    fun setReplayGain(v: Boolean) = viewModelScope.launch { store.setReplayGain(v) }
    fun setGapless(v: Boolean) = viewModelScope.launch { store.setGapless(v) }
}
