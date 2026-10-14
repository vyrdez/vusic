package com.feishin.mobile.core.common

/**
 * Artist image fallback rules.
 *
 * Some external providers (Last.fm's `getArtistInfo2`) return a generic default
 * image instead of nothing. Treated as "missing" so the UI falls through to the
 * artist's own cover art rather than showing a star placeholder.
 */
object AvatarFallback {
    /** Substrings that identify a provider's generic "no image" asset. */
    private val PLACEHOLDER_MARKERS = listOf(
        "2a96cbd8b46e442fc41c2b86b821562f", // Last.fm default star
        "default_avatar",
        "noimage",
        "no_image",
        "placeholder",
    )

    fun isPlaceholderUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return PLACEHOLDER_MARKERS.any { lower.contains(it) }
    }

    /**
     * First usable image id/url in the documented priority order:
     * provider info image, then the artist's cover art, then the artist's first
     * album cover, then null (the caller draws an initial).
     */
    fun pick(infoImageUrl: String?, coverArt: String?, firstAlbumCoverArt: String?): String? =
        listOf(infoImageUrl, coverArt, firstAlbumCoverArt).firstOrNull { !isPlaceholderUrl(it) }
}
