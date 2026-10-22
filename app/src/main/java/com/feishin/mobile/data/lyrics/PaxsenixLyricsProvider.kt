package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.domain.repo.LyricsProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder for Paxsenix lyrics provider.
 *
 * Disabled by default: The public Paxsenix domain (api.paxsenix.biz.id) is unreachable,
 * and remaining endpoints on lyrics.paxsenix.org require proprietary Apple Music track IDs
 * or return 403 Forbidden.
 */
@Singleton
class PaxsenixLyricsProvider @Inject constructor() : LyricsProvider {

    companion object {
        const val DISABLED_REASON =
            "Public endpoint is defunct (DNS unreachable); lyrics.paxsenix.org requires Apple Music IDs or returns 403."
    }

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> {
        // Disabled placeholder: returns NONE without making network requests
        return Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.PAXSENIX))
    }
}
