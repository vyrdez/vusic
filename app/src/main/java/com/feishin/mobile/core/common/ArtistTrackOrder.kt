package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

/**
 * Ordering for "save every song by this artist to a playlist".
 *
 * The artist detail response gives albums but not their songs, so the caller
 * fetches each album and hands the results here as `(albumYear, tracks)`.
 * This keeps the fiddly part - de-duplication across albums and a stable,
 * meaningful order - pure and testable:
 *
 *  1. a song id is kept once, first album wins,
 *  2. newest album first (year 0 / unknown last),
 *  3. then track number,
 *  4. then song id, so equal track numbers are deterministic.
 */
object ArtistTrackOrder {
    fun albumsToTracks(albums: List<Pair<Int, List<Track>>>): List<Track> {
        val seen = HashSet<String>()
        val pairs = ArrayList<Pair<Track, Int>>()
        for ((year, tracks) in albums) {
            for (t in tracks) {
                if (!seen.add(t.id)) continue
                pairs += t to year
            }
        }
        return pairs
            .sortedWith(
                compareBy(
                    { -it.second },
                    { it.first.trackNo ?: 0 },
                    { it.first.id },
                ),
            )
            .map { it.first }
    }
}
