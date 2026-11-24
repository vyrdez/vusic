package com.feishin.mobile.core.common

import java.util.Locale
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

    /** Lowercase query params that change the audio bytes and must stay in the key. */
    private val STABLE_PARAMS_LOWER = setOf(
        "id",
        "maxbitrate",
        "format",
        "static",
        "maxstreamingbitrate",
        "audiobitrate",
        "audiocodec",
        "container",
        "mediasourceid",
    )

    fun keyFor(uri: String, profileId: String? = null): String {
        val url = runCatching { uri.toHttpUrlOrNull() }.getOrNull() ?: return uri
        val kept = url.queryParameterNames
            .filter { it.lowercase(Locale.ROOT) in STABLE_PARAMS_LOWER }
            .sorted()
            .joinToString("&") { name ->
                url.queryParameterValues(name).filterNotNull().sorted()
                    .joinToString(",") { "$name=$it" }
            }
        val base = if (profileId == null || profileId == "p1") {
            "${url.scheme}://${url.host}${url.encodedPath}"
        } else {
            val isDefaultPort =
                (url.scheme == "http" && url.port == 80) ||
                    (url.scheme == "https" && url.port == 443)
            val portPart = if (isDefaultPort) "" else ":${url.port}"
            "$profileId:${url.scheme}://${url.host}$portPart${url.encodedPath}"
        }
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

    // The "may this run on this network" rule used to live here and keyed off
    // the transport name, which treated a metered Wi-Fi hotspot as unmetered.
    // It is now MobileDataPolicy, which uses the real NOT_METERED capability
    // and also refuses roaming.
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
