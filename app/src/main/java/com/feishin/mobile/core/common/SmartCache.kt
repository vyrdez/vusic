package com.feishin.mobile.core.common

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Stable Media3 cache keys.
 *
 * Stream URLs carry per-request auth (salt/token), so the raw URI is a
 * different cache key on every play and nothing ever hits. The stable key
 * keeps the endpoint identity plus everything that changes the bytes —
 * song id and quality — and drops everything volatile.
 */
object StableCacheKey {

    /** Query params that change the audio bytes and must stay in the key. */
    private val STABLE_PARAMS = setOf("id", "maxBitRate", "format")

    fun keyFor(uri: String): String {
        val url = runCatching { uri.toHttpUrlOrNull() }.getOrNull() ?: return uri
        val kept = url.queryParameterNames
            .filter { it in STABLE_PARAMS }
            .sorted()
            .joinToString("&") { name ->
                url.queryParameterValues(name).filterNotNull().sorted()
                    .joinToString(",") { "$name=$it" }
            }
        val base = "${url.scheme}://${url.host}${url.encodedPath}"
        return if (kept.isEmpty()) base else "$base?$kept"
    }
}

/**
 * Prefetch window rules: which upcoming queue entries to fully cache, and
 * which in-flight fetches have gone stale.
 */
object PrefetchWindow {

    /**
     * Up to [n] queue ids after [currentIndex], in order, without duplicates.
     * The current item itself is never included (it is already streaming).
     */
    fun window(queueIds: List<Long>, currentIndex: Int, n: Int): List<Long> {
        if (n <= 0 || queueIds.isEmpty()) return emptyList()
        val out = ArrayList<Long>(n)
        val seen = HashSet<Long>()
        var i = (currentIndex + 1).coerceAtLeast(0)
        while (i < queueIds.size && out.size < n) {
            val id = queueIds[i]
            if (seen.add(id)) out += id
            i++
        }
        return out
    }

    /** In-flight keys that left the window and must be cancelled. */
    fun stale(active: Set<Long>, window: List<Long>): Set<Long> =
        active - window.toSet()

    /** True when a prefetch may run on this network class. */
    fun allowedOnNetwork(network: NetworkClass, wifiOnly: Boolean): Boolean =
        !wifiOnly || network != NetworkClass.MOBILE
}

/**
 * Cached-collection membership rules: which Room rows still count as cached.
 */
object CachedMembership {

    /**
     * Splits [rows] into (keep, remove): a row survives only while its bytes
     * are still fully in the cache. [isFullyCached] is injected (backed by
     * SimpleCache on device) so the rule itself is unit-testable.
     */
    fun <T> partition(
        rows: List<T>,
        keyOf: (T) -> String,
        isFullyCached: (String) -> Boolean,
    ): Pair<List<T>, List<T>> {
        val keep = ArrayList<T>(rows.size)
        val remove = ArrayList<T>()
        for (row in rows) {
            if (runCatching { isFullyCached(keyOf(row)) }.getOrDefault(false)) keep += row
            else remove += row
        }
        return keep to remove
    }
}
