package com.feishin.mobile.domain.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics

/**
 * Resolves lyrics for one song from a specific source (LRCLIB, Navidrome, FileTags).
 *
 * Returns:
 *   - [Result.Ok] with [Lyrics] (which may have kind [LyricsKind.NONE] if no lyrics exist)
 *   - [Result.Err] if a transient network/I/O failure occurred (so caller knows not to cache negatively)
 */
interface LyricsProvider {
    suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String? = null,
        localModifiedSec: Long = 0L,
    ): Result<Lyrics>
}
