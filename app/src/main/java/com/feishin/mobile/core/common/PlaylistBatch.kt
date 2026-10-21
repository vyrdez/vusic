package com.feishin.mobile.core.common

/**
 * Splitting song id lists for Subsonic playlist calls.
 *
 * `updatePlaylist` sends every `songIdToAdd` in the query string, so a 500-song
 * artist would produce a URL a server or proxy refuses. Both create and add go
 * through here so the limit lives in one place and is unit-tested.
 */
object PlaylistBatch {
    /** Song ids per request. Comfortably inside common 8 KB request-line limits. */
    const val SIZE = 100

    fun chunks(ids: List<String>): List<List<String>> = ids.chunked(SIZE)

    /** First request for a new playlist; null means "create it empty". */
    fun firstCreateChunk(ids: List<String>): List<String>? =
        ids.take(SIZE).ifEmpty { null }

    /** Everything after [firstCreateChunk], in batches. */
    fun remainingChunks(ids: List<String>): List<List<String>> =
        ids.drop(SIZE).chunked(SIZE)
}
