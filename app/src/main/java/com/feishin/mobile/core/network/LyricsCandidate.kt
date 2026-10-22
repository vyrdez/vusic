package com.feishin.mobile.core.network

/**
 * A search candidate returned when the user manually searches for lyrics.
 */
data class LyricsCandidate(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Long = 0L,
    val source: LyricsSource = LyricsSource.LRCLIB,
    val isSynced: Boolean = false,
    val lyrics: Lyrics = Lyrics(emptyList(), 0L, LyricsKind.NONE),
)
