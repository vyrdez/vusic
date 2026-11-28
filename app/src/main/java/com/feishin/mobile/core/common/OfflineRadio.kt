package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

/**
 * Pure offline-radio selection. With no network, radio draws only from what is
 * already downloaded: same-genre songs first, then anything else, minus what is
 * already queued or recently played. When online for a backend without server
 * radio mixes (such as Google Drive), indexed Drive tracks can be combined with
 * downloaded songs while keeping the same genre-first, duplicate-free behavior.
 */
object OfflineRadio {

    fun pick(
        downloaded: List<Track>,
        driveTracks: List<Track>,
        seedGenre: String?,
        excludeIds: Set<String>,
        want: Int = 20,
    ): List<Track> = pick(
        downloaded = downloaded + driveTracks,
        seedGenre = seedGenre,
        excludeIds = excludeIds,
        want = want,
    )

    fun pick(
        downloaded: List<Track>,
        seedGenre: String?,
        excludeIds: Set<String>,
        want: Int = 20,
    ): List<Track> {
        if (want <= 0) return emptyList()
        val pool = downloaded.filter { it.id !in excludeIds }
        if (pool.isEmpty()) return emptyList()
        val genre = seedGenre?.takeIf { it.isNotBlank() }
        val sameGenre = if (genre == null) emptyList() else pool.filter { it.genre.equals(genre, true) }
        val others = pool.filterNot { it.genre.equals(genre, true) }
        val out = ArrayList<Track>(minOf(want, pool.size))
        val seen = HashSet<String>()
        for (t in sameGenre + others) {
            if (out.size >= want) break
            if (seen.add(t.id)) out += t
        }
        return out
    }
}
