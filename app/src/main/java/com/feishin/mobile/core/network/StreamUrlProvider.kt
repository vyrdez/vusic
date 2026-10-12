package com.feishin.mobile.core.network

/**
 * Builds authenticated `stream` URLs for ExoPlayer.
 *
 * Fresh salt per call (stream URLs must not be cached), plain password never
 * sent, server path prefixes preserved.
 */
object StreamUrlProvider {
    const val DEFAULT_FORMAT = "raw"

    fun build(
        serverUrl: String,
        username: String,
        password: String,
        trackId: String,
        maxBitrate: Int = 0,
        format: String = DEFAULT_FORMAT,
    ): String {
        val salt = SubsonicAuth.newSalt()
        val query = linkedMapOf<String, String>(
            "id" to trackId,
            "u" to username,
            "t" to SubsonicAuth.token(password, salt),
            "s" to salt,
            "v" to SubsonicAuth.API_VERSION,
            "c" to SubsonicAuth.CLIENT_NAME,
            "f" to "json",
        )
        // 0 = original quality: omit maxBitRate entirely (server streams raw).
        if (maxBitrate > 0) query["maxBitRate"] = maxBitrate.toString()
        if (format.isNotBlank() && format != DEFAULT_FORMAT) query["format"] = format
        return SubsonicAuth.buildUrl(serverUrl, "rest/stream.view", query)
    }
}