package com.feishin.mobile.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure Jellyfin server URL normalizer (JVM-safe via OkHttp, no `android.net.Uri`).
 *
 * Ensures an `http://` or `https://` scheme (defaulting to `https://` when omitted),
 * validates host/port syntax, and strips trailing `/` and `/web` web-UI path suffixes.
 */
object JellyfinUrl {

    fun normalize(raw: String): String {
        var s = raw.trim()
        require(s.isNotEmpty()) { "Server URL is empty" }
        val schemeSep = s.indexOf("://")
        if (schemeSep >= 0) {
            require(
                s.startsWith("http://", ignoreCase = true) ||
                    s.startsWith("https://", ignoreCase = true),
            ) { "Invalid server URL scheme: $raw" }
        } else {
            s = "https://$s"
        }

        val parsed = s.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL: $raw")
        var cleaned = parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')

        while (cleaned.endsWith("/web", ignoreCase = true)) {
            cleaned = cleaned.dropLast(4).trimEnd('/')
        }
        return cleaned
    }
}
