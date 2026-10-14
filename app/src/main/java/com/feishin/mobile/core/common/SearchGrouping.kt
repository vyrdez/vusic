package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.SearchResults

/**
 * Pure search-result grouping for the All view: top result first, then the top
 * N songs with the remainder behind "View more".
 */
object SearchGrouping {
    /** Five songs fit under the fold; the rest hide behind "View more". */
    const val TOP_SONGS_PREVIEW = 5

    /** Best match: first artist, else first album, else null. */
    fun topResult(res: SearchResults): TopHit? {
        res.artists.firstOrNull()?.let { return TopHit.Artist(it.id, it.name) }
        res.albums.firstOrNull()?.let { return TopHit.Album(it.id, it.name) }
        return null
    }

    fun previewSongs(res: SearchResults): List<com.feishin.mobile.domain.model.Track> =
        res.tracks.take(TOP_SONGS_PREVIEW)

    fun remainingSongs(res: SearchResults): List<com.feishin.mobile.domain.model.Track> =
        res.tracks.drop(TOP_SONGS_PREVIEW)

    /** True when every section is empty (drives the "No results for …" state). */
    fun isEmpty(res: SearchResults): Boolean =
        res.artists.isEmpty() && res.albums.isEmpty() &&
            res.tracks.isEmpty() && res.playlists.isEmpty()

    sealed interface TopHit {
        data class Artist(val id: String, val name: String) : TopHit
        data class Album(val id: String, val name: String) : TopHit
    }
}
