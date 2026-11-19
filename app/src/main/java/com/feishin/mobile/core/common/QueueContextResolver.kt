package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

data class PlayContext(
    val tracks: List<Track>,
    val startIndex: Int,
)

/**
 * Resolves the full-context playback queue when a user taps a track inside a
 * filtered or searched list, preserving active sort order and duplicate entries.
 */
object QueueContextResolver {
    fun resolvePlayContext(
        fullSortedTracks: List<Track>,
        clickedTrack: Track,
        fullSortedSourceIndices: List<Int>? = null,
        clickedSourceIndex: Int? = null,
    ): PlayContext {
        if (fullSortedTracks.isEmpty()) {
            return PlayContext(listOf(clickedTrack), 0)
        }
        val resolvedIndex = if (
            clickedSourceIndex != null &&
            fullSortedSourceIndices != null &&
            fullSortedSourceIndices.size == fullSortedTracks.size
        ) {
            fullSortedSourceIndices.indexOf(clickedSourceIndex)
                .takeIf { it >= 0 }
                ?: fullSortedTracks.indexOfFirst { it.id == clickedTrack.id }
        } else {
            fullSortedTracks.indexOfFirst { it.id == clickedTrack.id }
        }
        return if (resolvedIndex >= 0) {
            PlayContext(fullSortedTracks, resolvedIndex)
        } else {
            PlayContext(listOf(clickedTrack) + fullSortedTracks, 0)
        }
    }
}
