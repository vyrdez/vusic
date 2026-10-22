package com.feishin.mobile.core.network

/**
 * Parsed synced lyrics. Pure logic (no Android) so line lookup and parsing are
 * unit-testable.
 */
class SyncedLyrics private constructor(
    val lines: List<LyricLine>,
    val offsetMs: Long,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * Index of the line that should be highlighted at [positionMs], or -1 when
     * playback has not reached the first line. Binary search: O(log n).
     */
    fun lineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val adjusted = positionMs - offsetMs
        if (adjusted < lines.first().startMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= adjusted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = SyncedLyrics(emptyList(), 0)

        /**
         * Defensive parse: entries that are blank, out of order, or malformed are
         * dropped rather than failing the whole lyric. Start times are normalized
         * to be non-decreasing.
         */
        fun parse(
            entries: List<Pair<Long, String>>,
            offsetMs: Long = 0,
        ): SyncedLyrics {
            val cleaned = entries
                .filter { it.second.isNotBlank() }
                .sortedBy { it.first }
                .map { LyricLine(it.first, it.second.trim()) }
            if (cleaned.isEmpty()) return EMPTY.copy0(offsetMs)
            val normalized = ArrayList<LyricLine>(cleaned.size)
            var last = Long.MIN_VALUE
            cleaned.forEach { line ->
                val start = line.startMs.coerceAtLeast(last)
                normalized += line.copy(startMs = start)
                last = start
            }
            return SyncedLyrics(normalized, offsetMs)
        }

        /** Plain, unsynced lyrics still show as a single "whole song" block. */
        fun plain(text: String): SyncedLyrics =
            if (text.isBlank()) EMPTY.copy0(0) else SyncedLyrics(listOf(LyricLine(0, text.trim())), 0)

        private fun SyncedLyrics.copy0(offset: Long) = SyncedLyrics(emptyList(), offset)
    }
}