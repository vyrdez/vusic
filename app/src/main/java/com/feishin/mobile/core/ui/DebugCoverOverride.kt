package com.feishin.mobile.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug-test hook allowing debug receivers to override the active cover pixels.
 * In release builds nothing calls [set], so [current] remains null.
 */
object DebugCoverOverride {
    private val _current = MutableStateFlow<ArtworkPixels?>(null)
    val current: StateFlow<ArtworkPixels?> = _current.asStateFlow()

    fun set(pixels: ArtworkPixels?) {
        _current.value = pixels
    }
}
