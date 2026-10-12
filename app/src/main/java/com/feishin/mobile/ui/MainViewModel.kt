package com.feishin.mobile.ui

import androidx.lifecycle.ViewModel
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val player: PlayerManager,
    val repo: MusicDataRepository,
    val auth: AuthRepository,
    val settings: SettingsStore,
) : ViewModel()
