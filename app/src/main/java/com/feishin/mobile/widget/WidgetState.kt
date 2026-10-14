package com.feishin.mobile.widget

import com.feishin.mobile.domain.model.Track

/**
 * Everything the home widget renders. Pure mapping (no Android types) so the
 * state rules are unit-testable; [WidgetUpdater] applies a state to RemoteViews.
 */
data class WidgetState(
    val hasQueue: Boolean,
    val title: String,
    val artist: String,
    val coverArtId: String?,
    val trackId: String?,
    val isPlaying: Boolean,
    val starred: Boolean,
) {
    companion object {
        fun empty() = WidgetState(
            hasQueue = false,
            title = "Nothing playing",
            artist = "Tap to open Vusic",
            coverArtId = null,
            trackId = null,
            isPlaying = false,
            starred = false,
        )
    }
}

object WidgetStateMapper {
    /**
     * Maps player state to widget state. Starred comes from the favorites
     * collection when it has loaded (reactive to phone-side toggles);
     * otherwise the track's own flag, otherwise the last widget-confirmed
     * value (covers the service-alive/app-dead path).
     */
    fun map(
        queueEmpty: Boolean,
        current: Track?,
        isPlaying: Boolean,
        favLoaded: Boolean,
        favTrackIds: Set<String>,
        persistedTrackId: String?,
        persistedStarred: Boolean,
    ): WidgetState {
        if (queueEmpty || current == null) return WidgetState.empty()
        val starred = when {
            favLoaded -> current.id in favTrackIds
            current.starred -> true
            current.id == persistedTrackId -> persistedStarred
            else -> false
        }
        return WidgetState(
            hasQueue = true,
            title = current.title,
            artist = current.artist,
            coverArtId = current.coverArt,
            trackId = current.id,
            isPlaying = isPlaying,
            starred = starred,
        )
    }
}

/** Which RemoteViews layout fits the allocated widget width. */
enum class WidgetLayout { COMPACT, LARGE }

object WidgetLayoutPicker {
    /**
     * Large (4x2 with big cover) at 260dp and up, compact (4x1) below.
     * Unknown/zero widths fall back to compact.
     */
    fun forMinWidthDp(minWidthDp: Int): WidgetLayout =
        if (minWidthDp >= 260) WidgetLayout.LARGE else WidgetLayout.COMPACT
}
