package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

/**
 * Filter and sorting logic for device files. Pure logic, unit-testable.
 */
object DeviceFilesFilter {

    val DURATION_CHOICES = listOf(0, 15, 30, 60)

    fun durationLabel(sec: Int): String = when (sec) {
        0 -> "Off"
        15 -> "15 s"
        30 -> "30 s"
        60 -> "60 s"
        else -> "${sec} s"
    }

    fun minDurationMs(choiceSec: Int): Long = choiceSec.toLong() * 1000L

    fun shouldInclude(
        durationMs: Long,
        minDurationSec: Int,
        isMusic: Boolean = true,
        isRingTone: Boolean = false,
        isAlarm: Boolean = false,
        isNotification: Boolean = false,
    ): Boolean {
        if (!isMusic) return false
        if (isRingTone || isAlarm || isNotification) return false
        val threshold = minDurationMs(minDurationSec)
        return durationMs >= threshold
    }

    /** Which track actions must be hidden when operating on local device tracks. */
    fun isHiddenForLocal(actionKey: String): Boolean = when (actionKey.uppercase()) {
        "FAVORITE", "STAR", "UNSTAR",
        "DOWNLOAD", "REMOVE_DOWNLOAD", "KEEP_DOWNLOADED",
        "ADD_TO_PLAYLIST", "SAVE_TO_PLAYLIST",
        "START_RADIO", "RADIO",
        "SHARE",
        "PIN", "SPEED_DIAL",
        -> true
        else -> false
    }

    /** Filters and sorts a list of local tracks according to search query and sort choice. */
    fun filterAndSort(
        tracks: List<Track>,
        query: String,
        sort: SortChoice,
    ): List<Track> {
        val filtered = if (query.isBlank()) {
            tracks
        } else {
            val q = query.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }

        val sorted = when (sort.option.uppercase()) {
            "TITLE", "NAME" -> filtered.sortedBy { it.title.lowercase() }
            "ARTIST" -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            "ALBUM" -> filtered.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo ?: 0 }, { it.title.lowercase() }))
            "DURATION" -> filtered.sortedBy { it.durationSec }
            "YEAR" -> filtered.sortedWith(compareBy({ it.year ?: 0 }, { it.title.lowercase() }))
            "DATE_MODIFIED", "RECENT" -> filtered.sortedByDescending { it.localModifiedSec }
            else -> filtered.sortedBy { it.title.lowercase() }
        }

        return if (sort.ascending) sorted else sorted.reversed()
    }
}
