package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track

/**
 * Pure Android Auto browse-tree rules.
 *
 * Android Auto allows at most 4 root tabs: Home, Library, Favorites,
 * Downloads. Everything structural (node ids, paging, voice-query
 * normalization, search-to-tracks mapping) lives here so it is unit-testable
 * without Media3; `PlayerService` only renders these nodes as MediaItems.
 */
object AutoBrowse {

    const val ROOT_ID = "auto_root"

    // Root tabs (exactly 4 — the Auto limit).
    const val TAB_HOME = "tab_home"
    const val TAB_LIBRARY = "tab_library"
    const val TAB_FAVORITES = "tab_favorites"
    const val TAB_DOWNLOADS = "tab_downloads"

    // Home children.
    const val HOME_RECENT = "home_recent"
    const val HOME_NEWEST = "home_newest"
    const val HOME_RANDOM = "home_random"
    const val HOME_RADIO = "home_radio"

    // Library children.
    const val LIB_PLAYLISTS = "lib_playlists"
    const val LIB_ALBUMS = "lib_albums"
    const val LIB_ARTISTS = "lib_artists"

    // Favorites children.
    const val FAV_SONGS = "fav_songs"
    const val FAV_ALBUMS = "fav_albums"
    const val FAV_ARTISTS = "fav_artists"

    // Downloads child.
    const val DL_TRACKS = "dl_tracks"

    /** A browse node: folder, playable list, or single playable action. */
    data class Node(
        val id: String,
        val title: String,
        val browsable: Boolean,
        val playable: Boolean = false,
    )

    /** The 4 root tabs. Must stay exactly 4. */
    fun rootTabs(): List<Node> = listOf(
        Node(TAB_HOME, "Home", browsable = true),
        Node(TAB_LIBRARY, "Library", browsable = true),
        Node(TAB_FAVORITES, "Favorites", browsable = true),
        Node(TAB_DOWNLOADS, "Downloads", browsable = true),
    )

    fun homeChildren(): List<Node> = listOf(
        Node(HOME_RECENT, "Recently played", browsable = true),
        Node(HOME_NEWEST, "Newest albums", browsable = true),
        // Random mix is a folder of fresh random tracks (an action leaf would
        // arrive at onAddMediaItems without a song to resolve).
        Node(HOME_RANDOM, "Random mix", browsable = true),
        // "Start radio" is a playable action handled in onAddMediaItems.
        Node(HOME_RADIO, "Start radio", browsable = false, playable = true),
    )

    fun libraryChildren(): List<Node> = listOf(
        Node(LIB_PLAYLISTS, "Playlists", browsable = true),
        Node(LIB_ALBUMS, "Albums", browsable = true),
        Node(LIB_ARTISTS, "Artists", browsable = true),
    )

    fun favoritesChildren(): List<Node> = listOf(
        Node(FAV_SONGS, "Favorite songs", browsable = true),
        Node(FAV_ALBUMS, "Favorite albums", browsable = true),
        Node(FAV_ARTISTS, "Favorite artists", browsable = true),
    )

    fun downloadsChildren(): List<Node> = listOf(
        Node(DL_TRACKS, "Downloaded songs", browsable = true),
    )

    /** Tracks node for a playlist, album or artist collection. */
    fun collectionNode(collectionId: String): String = "col_$collectionId"

    /** Inverse of [collectionNode], or null when [nodeId] is not a collection. */
    fun collectionIdOf(nodeId: String): String? =
        nodeId.removePrefix("col_").takeIf { nodeId.startsWith("col_") && it.isNotEmpty() }

    /**
     * Encodes collection kinds that need different fetching: `pl:` playlists,
     * `al:` albums, `ar:` artists, `mix:` generated mixes.
     */
    fun collectionNode(kind: String, id: String): String = "col_$kind:$id"

    fun collectionRefOf(nodeId: String): Pair<String, String>? {
        if (!nodeId.startsWith("col_")) return null
        val rest = nodeId.removePrefix("col_")
        val sep = rest.indexOf(':')
        if (sep < 0) return null
        val kind = rest.substring(0, sep)
        val id = rest.substring(sep + 1)
        if (kind.isEmpty() || id.isEmpty()) return null
        return kind to id
    }

    /** Lazy page of [list]: page 0-based, standard Auto page sizes. */
    fun <T> page(list: List<T>, page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val from = page * pageSize
        if (from >= list.size) return emptyList()
        return list.subList(from, minOf(from + pageSize, list.size))
    }

    /**
     * Normalizes a voice query: trims, drops a leading "play"/"listen to" so
     * "play Rokudenashi" searches "Rokudenashi". Never returns blank.
     */
    fun normalizeVoiceQuery(query: String): String {
        val q = query.trim()
        for (prefix in listOf("play ", "listen to ", "hear ")) {
            if (q.startsWith(prefix, ignoreCase = true)) {
                val rest = q.substring(prefix.length).trim()
                if (rest.isNotEmpty()) return rest
            }
        }
        return q
    }

    /**
     * Tracks to queue for a voice request when the search already returned
     * songs: exact title matches first, then the rest, capped for the car.
     */
    fun voiceTracks(results: SearchResults, query: String, limit: Int = 50): List<Track> {
        if (results.tracks.isEmpty()) return emptyList()
        val q = query.lowercase()
        val exact = results.tracks.filter { it.title.lowercase() == q }
        val rest = results.tracks.filter { it.title.lowercase() != q }
        return (exact + rest).take(limit.coerceAtLeast(1))
    }
}
