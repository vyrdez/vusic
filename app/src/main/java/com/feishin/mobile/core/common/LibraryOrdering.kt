package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.Track

/**
 * Pure Library ordering + local filtering. The ViewModel only decides *which*
 * choice is active; every comparison lives here so it is unit-testable.
 */
object LibraryOrdering {

    fun sortAlbums(list: List<Album>, choice: SortChoice): List<Album> {
        val by: Comparator<Album> = when (choice.option) {
            "artist" -> compareBy { it.artist.lowercase() }
            "year" -> compareBy { it.year ?: 0 }
            "plays" -> compareBy { it.playCount }
            else -> compareBy { it.name.lowercase() }
        }
        val sorted = list.sortedWith(by)
        return if (choice.ascending) sorted else sorted.reversed()
    }

    fun sortArtists(list: List<Artist>, choice: SortChoice): List<Artist> {
        val by: Comparator<Artist> = when (choice.option) {
            "albums" -> compareBy { it.albumCount }
            else -> compareBy { it.name.lowercase() }
        }
        val sorted = list.sortedWith(by)
        return if (choice.ascending) sorted else sorted.reversed()
    }

    fun sortSongs(list: List<Track>, choice: SortChoice): List<Track> {
        val by: Comparator<Track> = when (choice.option) {
            "artist" -> compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
            "duration" -> compareBy { it.durationSec }
            "year" -> compareBy { it.year ?: 0 }
            else -> compareBy { it.title.lowercase() }
        }
        val sorted = list.sortedWith(by)
        return if (choice.ascending) sorted else sorted.reversed()
    }

    fun sortPlaylists(list: List<Playlist>, choice: SortChoice): List<Playlist> {
        val by: Comparator<Playlist> = when (choice.option) {
            "songs" -> compareBy { it.songCount }
            else -> compareBy { it.name.lowercase() }
        }
        val sorted = list.sortedWith(by)
        return if (choice.ascending) sorted else sorted.reversed()
    }

    // ---------- local filtering (Library inline search) ----------

    private fun matches(query: String, vararg fields: String?): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return fields.any { it?.lowercase()?.contains(q) == true }
    }

    fun filterAlbums(list: List<Album>, query: String): List<Album> =
        if (query.isBlank()) list else list.filter { matches(query, it.name, it.artist) }

    fun filterArtists(list: List<Artist>, query: String): List<Artist> =
        if (query.isBlank()) list else list.filter { matches(query, it.name) }

    fun filterSongs(list: List<Track>, query: String): List<Track> =
        if (query.isBlank()) list else list.filter { matches(query, it.title, it.artist, it.album) }

    fun filterPlaylists(list: List<Playlist>, query: String): List<Playlist> =
        if (query.isBlank()) list else list.filter { matches(query, it.name) }
}
