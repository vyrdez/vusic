package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

/**
 * Pure duplicate detection for every add-to-playlist flow (single song, album,
 * queue save, favorites, radio queue). Songs are compared by server id.
 */
object PlaylistDuplicates {
    data class Partition(val fresh: List<Track>, val duplicates: List<Track>)

    fun partition(existingIds: Set<String>, candidates: List<Track>): Partition {
        val fresh = ArrayList<Track>()
        val dups = ArrayList<Track>()
        for (t in candidates) {
            if (t.id in existingIds) dups.add(t) else fresh.add(t)
        }
        return Partition(fresh, dups)
    }
}
