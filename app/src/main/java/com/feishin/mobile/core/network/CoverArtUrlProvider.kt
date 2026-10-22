package com.feishin.mobile.core.network

/**
 * Builds authenticated `getCoverArt` URLs.
 *
 * Unlike per-request stream URLs, the salt here is stable per cover-art id, so
 * the resulting URL is stable and Coil's disk/memory cache can hit. The token
 * is still md5(password + salt); the plain password is never sent.
 */
object CoverArtUrlProvider {
    fun build(
        serverUrl: String,
        username: String,
        password: String,
        coverArtId: String?,
        size: Int,
    ): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (coverArtId.startsWith("content://") || coverArtId.startsWith("file://")) {
            return coverArtId
        }
        val salt = SubsonicAuth.stableSalt("cover:$coverArtId")
        val query = mapOf(
            "id" to coverArtId,
            "size" to size.toString(),
            "u" to username,
            "t" to SubsonicAuth.token(password, salt),
            "s" to salt,
            "v" to SubsonicAuth.API_VERSION,
            "c" to SubsonicAuth.CLIENT_NAME,
            "f" to "json",
        )
        return SubsonicAuth.buildUrl(serverUrl, "rest/getCoverArt.view", query)
    }
}
