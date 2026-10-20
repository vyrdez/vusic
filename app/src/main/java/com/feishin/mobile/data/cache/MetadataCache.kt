package com.feishin.mobile.data.cache

import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Room-backed JSON cache for library payloads.
 *
 * Enables stale-while-revalidate: a screen can show the last known data
 * instantly (and keep working with no network) while a refresh runs in the
 * background and overwrites the entry. Keys are stable strings such as
 * `artists`, `albums:alphabeticalByName:0`, `album:<id>`, `starred`.
 */
@Singleton
class MetadataCache @Inject constructor(
    private val dao: MetadataCacheDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    // Decoding a 500-track payload is not free, and the reads happen right
    // before the list is published: never on the caller's thread, which for the
    // library is the main thread.
    private suspend fun <T> io(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = io {
        runCatching { dao.get(key)?.let { json.decodeFromString(serializer, it) } }.getOrNull()
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        io { runCatching { dao.put(CachedMetadataEntity(key, json.encodeToString(serializer, value))) } }
    }

    /** True when an entry exists (used to decide whether a miss should error). */
    suspend fun has(key: String): Boolean = io { runCatching { dao.get(key) != null }.getOrDefault(false) }

    suspend fun remove(key: String) {
        io { runCatching { dao.remove(key) } }
    }

    suspend fun clear() {
        io { runCatching { dao.clear() } }
    }

    companion object
}

/** Cache keys, centralised so readers and writers cannot drift. */
object MetadataCacheKeys {
    const val ARTISTS = "artists"
    const val GENRES = "genres"
    const val PLAYLISTS = "playlists"
    const val STARRED = "starred"
    fun albums(type: String, offset: Int) = "albums:$type:$offset"
    fun album(id: String) = "album:$id"
    fun artist(id: String) = "artist:$id"
    fun playlist(id: String) = "playlist:$id"
    fun songs(size: Int) = "songs:$size"
}
