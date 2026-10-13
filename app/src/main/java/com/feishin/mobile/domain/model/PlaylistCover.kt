package com.feishin.mobile.domain.model

/**
 * Chooses what a playlist should show as its cover.
 *
 * Subsonic gives playlists a `coverArt` id only when the server has one (a
 * created playlist usually has none), so we fall back to a collage of the first
 * distinct track covers, and to a placeholder when the playlist is empty.
 */
sealed interface PlaylistCover {
    /** Server-provided artwork id. */
    data class Server(val coverArtId: String) : PlaylistCover

    /** 1..4 distinct track artwork ids; rendered as 1 cover or a 2x2 collage. */
    data class Collage(val coverArtIds: List<String>) : PlaylistCover

    /**
     * Built-in Favorite songs: a heart on a gradient, or a collage of the
     * latest favorites with a heart badge.
     */
    data class Favorites(val coverArtIds: List<String>) : PlaylistCover

    /** No artwork anywhere; render a styled music-note placeholder. */
    data object Placeholder : PlaylistCover

    companion object {
        fun select(serverCoverArtId: String?, trackCoverArtIds: List<String?>): PlaylistCover {
            if (!serverCoverArtId.isNullOrBlank()) return Server(serverCoverArtId)
            val distinct = trackCoverArtIds.filterNot { it.isNullOrBlank() }.map { it!! }.distinct().take(4)
            return if (distinct.isEmpty()) Placeholder else Collage(distinct)
        }
    }
}
