package com.feishin.mobile.core.common

import java.util.Locale

/**
 * How a playlist's tracks are ordered on screen.
 *
 * The order is a pure view concern: nothing here is ever written back to the
 * server. A sorted or filtered list is read-only (see [canMutateOrder]).
 */
enum class PlaylistSortKey(val label: String) {
    /** Exactly what the server returned: the order tracks were added. */
    PLAYLIST_ORDER("Playlist order"),
    TITLE("Title"),
    ARTIST("Artist"),
    DATE_ADDED("Date added"),
    DURATION("Duration"),
}

/**
 * A sort selection. [descending] is per-key so switching keys always starts
 * ascending and a second tap on the same key flips the direction.
 */
data class PlaylistSort(
    val key: PlaylistSortKey = PlaylistSortKey.PLAYLIST_ORDER,
    val descending: Boolean = false,
) {
    /** True for the untouched "as stored" order, ascending. */
    val isDefault: Boolean
        get() = key == PlaylistSortKey.PLAYLIST_ORDER && !descending

    fun serialize(): String = "${key.name}|$descending"

    companion object {
        /**
         * Tolerant parse: an unknown or corrupt stored value falls back to the
         * default rather than throwing, so a downgrade cannot crash the screen.
         */
        fun parse(raw: String?): PlaylistSort {
            if (raw.isNullOrBlank()) return PlaylistOrdering.DEFAULT
            val parts = raw.split('|')
            val key = runCatching { PlaylistSortKey.valueOf(parts[0]) }
                .getOrDefault(PlaylistSortKey.PLAYLIST_ORDER)
            return PlaylistSort(key, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false)
        }
    }
}

/**
 * Sort and filter rules for a playlist's track list, expressed over INDICES
 * into the source list.
 *
 * Working in indices rather than domain objects is what keeps this unit
 * testable, and it is also what keeps the dangerous mapping honest: a visible
 * row is `visibleIndices[n]`, and the server index for a row is always looked
 * up separately by track id, never by the position the user sees.
 */
object PlaylistOrdering {

    val DEFAULT = PlaylistSort()

    /** Tapping a key: same key flips direction, a new key starts ascending. */
    fun toggle(current: PlaylistSort, key: PlaylistSortKey): PlaylistSort =
        if (current.key == key) current.copy(descending = !current.descending)
        else PlaylistSort(key, descending = false)

    /**
     * Reorder + remove act on server positions, so they are only safe when the
     * list the user sees IS the stored order with nothing hidden. Any sort or
     * an active query disables them.
     */
    fun canMutateOrder(sort: PlaylistSort, query: String): Boolean =
        sort.isDefault && query.isBlank()

    /** Case- and whitespace-insensitive fold used for both sort and filter. */
    fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT)

    /**
     * Title, artist or album contains the query. Every whitespace-separated
     * term must match somewhere, so "yoasobi love" narrows progressively
     * instead of requiring that exact phrase.
     */
    fun matches(query: String, title: String, artist: String, album: String): Boolean {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = normalize("$title $artist $album")
        return terms.all { haystack.contains(it) }
    }

    /**
     * Source indices in display order.
     *
     * [PlaylistSortKey.DATE_ADDED] has no per-track timestamp to work from:
     * Navidrome exposes no "added at" on playlist entries, and the only
     * deterministic proxy is the order the server returned, which is insertion
     * order for a playlist (newest first for the Cached collection). So it is
     * deliberately defined as "stored order, optionally reversed" rather than
     * being faked from a release year.
     *
     * @param sort which ordering to apply
     * @param titleAt/artistAt/durationAt lazily read a row's fields
     */
    fun order(
        size: Int,
        sort: PlaylistSort,
        titleAt: (Int) -> String,
        artistAt: (Int) -> String,
        durationAt: (Int) -> Int,
    ): List<Int> {
        if (size <= 0) return emptyList()
        val indices = List(size) { it }
        // Stored order IS insertion order, so these two keys keep the source
        // sequence and only the direction differs.
        if (sort.key == PlaylistSortKey.PLAYLIST_ORDER ||
            sort.key == PlaylistSortKey.DATE_ADDED
        ) {
            return if (sort.descending) indices.reversed() else indices
        }
        val base: Comparator<Int> = when (sort.key) {
            PlaylistSortKey.PLAYLIST_ORDER, PlaylistSortKey.DATE_ADDED ->
                return if (sort.descending) indices.reversed() else indices
            PlaylistSortKey.TITLE -> compareBy { normalize(titleAt(it)) }
            PlaylistSortKey.ARTIST -> compareBy { normalize(artistAt(it)) }
            PlaylistSortKey.DURATION -> compareBy { durationAt(it) }
        }
        val directed = if (sort.descending) base.reversed() else base
        // sortedWith is stable, so equal keys keep the stored order, which is
        // what makes ties deterministic across reloads.
        return indices.sortedWith(directed)
    }

    /**
     * Filter first, then order, so the result is the indices of the rows the
     * user can actually see - in the order they can see them.
     */
    fun visibleIndices(
        size: Int,
        sort: PlaylistSort,
        query: String,
        titleAt: (Int) -> String,
        artistAt: (Int) -> String,
        albumAt: (Int) -> String,
        durationAt: (Int) -> Int,
    ): List<Int> {
        val kept = if (query.isBlank()) {
            List(size) { it }
        } else {
            List(size) { it }.filter {
                matches(query, titleAt(it), artistAt(it), albumAt(it))
            }
        }
        if (kept.isEmpty()) return kept
        val base: Comparator<Int> = when (sort.key) {
            PlaylistSortKey.PLAYLIST_ORDER, PlaylistSortKey.DATE_ADDED ->
                return if (sort.descending) kept.reversed() else kept
            PlaylistSortKey.TITLE -> compareBy { normalize(titleAt(it)) }
            PlaylistSortKey.ARTIST -> compareBy { normalize(artistAt(it)) }
            PlaylistSortKey.DURATION -> compareBy { durationAt(it) }
        }
        val directed = if (sort.descending) base.reversed() else base
        return kept.sortedWith(directed)
    }
}