package com.feishin.mobile.playback

import com.feishin.mobile.core.common.RadioProgrammer
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import javax.inject.Inject

/**
 * Builds one radio batch (up to [RadioProgrammer.BATCH_SIZE] songs) for a seed
 * track, falling through silently: similar songs for the seed artist, then
 * same-genre randoms, then plain randoms. Stops as soon as the batch is full.
 *
 * Returns [Result.Err] only when every source failed (transient network/rate
 * limit); an empty [Result.Ok] means the library truly yielded nothing, so the
 * caller can tell "retry later" apart from "library empty".
 */
class RadioFetcher @Inject constructor(
    private val repo: MusicDataRepository,
) {
    suspend fun fetchBatch(
        seed: Track,
        excludeIds: Set<String>,
        want: Int = RadioProgrammer.BATCH_SIZE,
    ): Result<List<Track>> {
        val acc = ArrayList<Track>(want)
        var anyOk = false

        fun take(r: Result<List<Track>>) {
            if (acc.size >= want) return
            val list = (r as? Result.Ok)?.value
            if (list == null) return
            anyOk = true
            val accIds = acc.map { it.id }.toHashSet()
            for (t in list) {
                if (acc.size >= want) break
                if (t.id !in excludeIds && t.id !in accIds) {
                    acc.add(t)
                    accIds.add(t.id)
                }
            }
        }

        // 1. Similar songs for the seed artist (or the seed song itself).
        val artistId = seed.artistId?.takeIf { it.isNotBlank() }
        if (artistId != null || seed.artist.isNotBlank() || seed.id.isNotBlank()) {
            val sim = repo.similarSongs(
                songId = seed.id.takeIf { artistId == null },
                artistId = artistId,
                count = 50,
            )
            take((sim as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: sim)
        }
        // 2. Random songs of the same genre.
        if (acc.size < want && !seed.genre.isNullOrBlank()) {
            val g = repo.randomSongs(40, seed.genre)
            take((g as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: g)
        }
        // 3. Plain random songs.
        if (acc.size < want) {
            val r = repo.randomSongs(40, null)
            take((r as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: r)
        }
        if (!anyOk) return Result.Err("Radio unavailable")
        return Result.Ok(RadioProgrammer.buildBatch(acc, emptySet(), want))
    }
}
