package com.feishin.mobile.core.common

/**
 * When a cached payload may be revalidated in the background, and when a read
 * must wait for the network.
 *
 * Rules, all keyed per cache entry:
 *  - a read never blocks on the network while a usable payload is stored;
 *  - at most one background refresh per entry per [freshForMs] window, and an
 *    attempt counts even when it fails, so a dead server cannot be hammered by
 *    every recomposition or tab switch;
 *  - pull-to-refresh (or an optimistic local change) forces the next read of an
 *    entry - or of everything - to go to the network.
 *
 * Pure and clock-injected: the refresh policy is unit-tested without Android,
 * Room or a server.
 */
class CachePolicy(
    private val freshForMs: Long = DEFAULT_FRESH_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lastAttempt = HashMap<String, Long>()
    private val forced = HashSet<String>()
    private val consumedGeneration = HashMap<String, Long>()
    private var forceGeneration = 0L
    private var forceAllUntil = 0L

    /** The next [freshForMs] of reads skip the cache and wait for the network. */
    @Synchronized
    fun forceAll(durationMs: Long = FORCE_WINDOW_MS) {
        forceGeneration++
        forceAllUntil = clock() + durationMs
    }

    /** The next read of [key] skips the cache (e.g. after a favorite toggle). */
    @Synchronized
    fun force(key: String) {
        forced += key
    }

    /**
     * True when this read must go to the network instead of serving the cache.
     * Consumes the force for [key]: a refresh pass fires several reads, and the
     * read-backs that follow it must not trigger a second network round.
     */
    @Synchronized
    fun consumeForced(key: String): Boolean {
        if (clock() < forceAllUntil && consumedGeneration[key] != forceGeneration) {
            consumedGeneration[key] = forceGeneration
            return true
        }
        return forced.remove(key)
    }

    /**
     * True when a background refresh should start for [key]; records the
     * attempt, so a second call inside the window answers false even if the
     * first refresh failed.
     */
    @Synchronized
    fun shouldRefresh(key: String): Boolean {
        val now = clock()
        val last = lastAttempt[key]
        if (last != null && now - last < freshForMs) return false
        lastAttempt[key] = now
        return true
    }

    /** For tests and for "clear cache" style resets. */
    @Synchronized
    fun reset() {
        lastAttempt.clear()
        forced.clear()
        consumedGeneration.clear()
        forceGeneration = 0L
        forceAllUntil = 0L
    }

    companion object {
        /** A tab is revalidated at most once per this window. */
        const val DEFAULT_FRESH_MS = 3 * 60_000L

        /** A forced window covers one refresh pass, which fires several reads. */
        const val FORCE_WINDOW_MS = 4_000L
    }
}
