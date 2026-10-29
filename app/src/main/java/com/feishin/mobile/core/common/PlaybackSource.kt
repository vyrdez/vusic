package com.feishin.mobile.core.common

/**
 * Chooses between a downloaded local file and the network stream. Kept free of
 * Media3/Android types so the decision is unit-testable; the caller applies the
 * choice to the MediaItem.
 */
object PlaybackSource {

    /** Where a track's bytes come from for playback. */
    enum class Kind { LOCAL, STREAM }

    /**
     * LOCAL whenever a finished download exists on disk. [fileExists] is injected
     * so the decision stays pure; production passes a real filesystem check.
     */
    fun kind(downloadedPath: String?, fileExists: (String) -> Boolean): Kind =
        if (downloadedPath != null && fileExists(downloadedPath)) Kind.LOCAL else Kind.STREAM

    fun shouldBypassStreamCache(scheme: String?): Boolean =
        scheme == "file" || scheme == "content" || scheme == "android.resource"

    fun isPlayableLocalFile(
        path: String?,
        fileExists: (String) -> Boolean,
        fileLength: (String) -> Long,
    ): Boolean =
        path != null &&
            !path.endsWith(".part") &&
            !path.endsWith(".tmp") &&
            fileExists(path) &&
            fileLength(path) >= DownloadStateMachine.MIN_VALID_AUDIO_BYTES

    fun mimeTypeForPath(path: String?): String? {
        if (path == null) return null
        val clean = path.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "opus", "ogg" -> "audio/ogg"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> null
        }
    }

    /** The URI string ExoPlayer should read for a local download. */
    fun localUri(path: String): String =
        if (path.startsWith("file:")) path else "file://$path"
}
