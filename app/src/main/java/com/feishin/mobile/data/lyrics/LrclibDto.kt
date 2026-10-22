package com.feishin.mobile.data.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LrclibResponseDto(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
