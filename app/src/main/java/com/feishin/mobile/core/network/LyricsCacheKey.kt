package com.feishin.mobile.core.network

import com.feishin.mobile.domain.model.Track

/**
 * Stable Room cache key for a song's lyrics.
 *
 * Server songs are keyed by song id. Device files have no server id, so they
 * are keyed by content URI plus modification time: re-tagging or replacing the
 * file produces a new key instead of serving stale lyrics.
 */
object LyricsCacheKey {
    fun of(track: Track): String = if (track.isLocal) {
        "local:${track.localUri}@${track.localModifiedSec}"
    } else {
        track.id
    }
}
