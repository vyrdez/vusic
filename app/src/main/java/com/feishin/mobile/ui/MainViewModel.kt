package com.feishin.mobile.ui

import androidx.lifecycle.ViewModel
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.components.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val player: PlayerManager,
    val repo: MusicDataRepository,
    val auth: AuthRepository,
    val settings: SettingsStore,
    val messenger: UiMessenger,
    val favs: FavoritesStore,
    val playlistEvents: PlaylistEvents,
    val downloads: com.feishin.mobile.data.download.DownloadRepository,
    val offlineSync: com.feishin.mobile.data.download.OfflineSyncStore,
    val network: com.feishin.mobile.data.network.NetworkMonitor,
) : ViewModel()
