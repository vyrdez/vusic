package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

/**
 * Pure radio (auto-DJ) policy and queue-building math. Kept free of Android and
 * repository types so it is fully unit-testable; [com.feishin.mobile.playback.RadioFetcher]
 * supplies the network candidates and [com.feishin.mobile.playback.PlayerManager]
 * owns the trigger wiring.
 */
object RadioProgrammer {
    /** Songs fetched per radio batch. */
    const val BATCH_SIZE = 20

    /**
     * Append trigger: playback at [index] is within two songs of the end.
     * For a 20-song queue that is index 17 (the 18th song).
     */
    const val APPEND_THRESHOLD = 3

    /** Never play more than this many songs by the same artist back-to-back. */
    const val MAX_CONSECUTIVE_SAME_ARTIST = 2

    /** How many played song ids are remembered for de-duplication. */
    const val RECENT_HISTORY_CAP = 50

    fun shouldAppend(index: Int, size: Int): Boolean =
        size > 0 && index >= 0 && index >= size - APPEND_THRESHOLD

    /**
     * De-duplicates [candidates] against [excludeIds] (current queue + recent
     * history) and orders the survivors so the same artist never appears more
     * than [MAX_CONSECUTIVE_SAME_ARTIST] times in a row where possible.
     * Returns at most [want] tracks.
     */
    fun buildBatch(
        candidates: List<Track>,
        excludeIds: Set<String>,
        want: Int = BATCH_SIZE,
    ): List<Track> {
        if (want <= 0) return emptyList()
        val fresh = candidates.filter { it.id !in excludeIds }
        if (fresh.isEmpty()) return emptyList()
        // Artist spread: each step picks the eligible artist with the most songs
        // left (ties keep insertion order). An artist is eligible unless it
        // would extend the current run past the cap; when only one artist
        // remains the run is unavoidable and the fallback takes it.
        val byArtist = LinkedHashMap<String, ArrayDeque<Track>>()
        for (t in fresh) byArtist.getOrPut(t.artistKey()) { ArrayDeque() }.add(t)
        val out = ArrayList<Track>(minOf(want, fresh.size))
        var lastArtist: String? = null
        var run = 0
        while (out.size < want && byArtist.isNotEmpty()) {
            var best: String? = null
            var bestLeft = -1
            for ((artist, q) in byArtist) {
                if (q.isEmpty()) continue
                if (artist == lastArtist && run >= MAX_CONSECUTIVE_SAME_ARTIST) continue
                if (q.size > bestLeft) {
                    best = artist
                    bestLeft = q.size
                }
            }
            val pick = best ?: byArtist.entries.firstOrNull { it.value.isNotEmpty() }?.key
            val q = pick?.let { byArtist[it] }
            if (pick == null || q == null || q.isEmpty()) break
            out += q.removeFirst()
            if (q.isEmpty()) byArtist.remove(pick)
            if (pick == lastArtist) run++ else {
                lastArtist = pick
                run = 1
            }
        }
        return out
    }

    private fun Track.artistKey(): String =
        artistId?.takeIf { it.isNotBlank() } ?: artist.ifBlank { "__unknown__" }
}

/**
 * Session-scoped memory of what radio has played or queued. Unlike the
 * rolling recent history, this only resets when radio stops, so a long
 * session never loops back to songs it already played. Oldest ids fall off
 * past [cap] so a marathon session can't grow it without bound.
 */
class RadioMemory(private val cap: Int = 500) {
    private val ids = LinkedHashSet<String>()

    fun clear() {
        ids.clear()
    }

    fun remember(id: String) {
        ids.add(id)
        while (ids.size > cap) {
            val it = ids.iterator()
            it.next()
            it.remove()
        }
    }

    fun snapshot(): Set<String> = ids.toSet()
}

/**
 * Guards radio background appends so fast taps, repeated track transitions and
 * retries can never stack two fetches or apply a stale batch.
 */
class RadioTrigger {
    private var generation = 0
    private var inFlight = false

    /** Starts a new radio session, invalidating any in-flight batch. */
    fun newSession(): Int {
        inFlight = false
        return ++generation
    }

    /** Cancels radio (new user playback, queue cleared): stale batches die. */
    fun cancel() {
        generation++
        inFlight = false
    }

    fun currentGeneration(): Int = generation

    /** True exactly once per generation until [end] / [cancel] / [newSession]. */
    fun tryBegin(gen: Int): Boolean {
        if (gen != generation || inFlight) return false
        inFlight = true
        return true
    }

    fun end(gen: Int) {
        if (gen == generation) inFlight = false
    }
}
