package com.feishin.mobile.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports how Now Playing wants the system bars to look, so MainActivity can
 * colour the status/navigation icons from the surface that is actually behind
 * them.
 *
 * `null` means "Now Playing is not on screen", and the app falls back to the
 * app theme's own light/dark decision.
 *
 * This exists because the theme flag is not enough: a tinted Now Playing can
 * put a pale surface under the status bar in the Dark theme, and the old code
 * kept white icons there.
 */
object NowPlayingBarTint {
    private val _lightBackdrop = MutableStateFlow<Boolean?>(null)
    val lightBackdrop: StateFlow<Boolean?> = _lightBackdrop.asStateFlow()

    fun report(light: Boolean) {
        _lightBackdrop.value = light
    }

    fun clear() {
        _lightBackdrop.value = null
    }
}