package com.feishin.mobile.ui

import androidx.lifecycle.ViewModel
import com.feishin.mobile.data.repo.MusicRepository
import com.feishin.mobile.data.store.CredentialsStore
import com.feishin.mobile.data.store.SettingsStore
import com.feishin.mobile.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val player: PlayerManager,
    val repo: MusicRepository,
    val creds: CredentialsStore,
    val settings: SettingsStore,
) : ViewModel()
