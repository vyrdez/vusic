package com.feishin.mobile.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens Now Playing from outside the composition (widget cover tap).
 * MainActivity nudges; MainScaffold consumes. Counter-based so rapid taps
 * never coalesce into nothing.
 */
object NowPlayingNudge {
    private val _requests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = _requests.asStateFlow()

    fun nudge() {
        _requests.value++
    }
}
