@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.data.cache

import com.feishin.mobile.core.common.CachedMembership
import com.feishin.mobile.data.db.CachedTrackDao
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Byte-level view of the stream cache, behind an interface so unit tests can
 * fake eviction without Media3 on the JVM.
 */
interface CacheBytes {
    fun isCached(cacheKey: String, sizeBytes: Long): Boolean
    fun removeResource(cacheKey: String)
}

@Singleton
class Media3CacheBytes @Inject constructor(
    private val cache: androidx.media3.datasource.cache.SimpleCache,
) : CacheBytes {
    override fun isCached(cacheKey: String, sizeBytes: Long): Boolean {
        if (cacheKey.isBlank() || sizeBytes <= 0) return false
        return runCatching { cache.isCached(cacheKey, 0, sizeBytes) }.getOrDefault(false)
    }

    override fun removeResource(cacheKey: String) {
        runCatching { cache.removeResource(cacheKey) }
    }
}

/** Virtual playlist id for the built-in Cached collection. */
const val CACHED_SONGS_PLAYLIST_ID = "__cached__"

fun CachedTrackEntity.toTrack(): Track = Track(
    id = songId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    durationSec = durationSec,
    trackNo = trackNo,
    year = year,
    genre = genre,
    suffix = suffix,
    starred = false,
)

/**
 * Registry of fully cached songs. Bytes live in Media3's SimpleCache (LRU);
 * this table is what the "Cached" collection lists with no network, and what
 * gets pruned when bytes evaporate or the user clears the song cache.
 */
@Singleton
class CachedTrackStore @Inject constructor(
    private val dao: CachedTrackDao,
    private val bytes: CacheBytes,
) {
    /** Newest first, matching the collection's display order. */
    val tracks: Flow<List<CachedTrackEntity>> = dao.observeAll()

    suspend fun all(): List<CachedTrackEntity> = dao.all()

    suspend fun get(songId: String): CachedTrackEntity? = dao.byId(songId)

    suspend fun count(): Int = dao.count()

    /** Records a completed full fetch, keeping the original cachedAt. */
    suspend fun recordCached(track: Track, cacheKey: String, sizeBytes: Long) {
        val prior = dao.byId(track.id)
        dao.upsert(
            CachedTrackEntity(
                songId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                artistId = track.artistId,
                coverArt = track.coverArt,
                durationSec = track.durationSec,
                trackNo = track.trackNo,
                year = track.year,
                genre = track.genre,
                suffix = track.suffix,
                cacheKey = cacheKey,
                sizeBytes = sizeBytes,
                cachedAt = prior?.cachedAt ?: System.currentTimeMillis(),
                lastPlayedAt = prior?.lastPlayedAt ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun touchPlayed(songId: String) {
        if (dao.byId(songId) != null) dao.touchPlayed(songId)
    }

    /**
     * Drops rows whose bytes are no longer fully in the cache (LRU eviction or
     * an external clear). Returns the removed ids.
     */
    suspend fun pruneNotCached(isFullyCached: (String) -> Boolean): List<String> {
        val rows = dao.all()
        val (keep, remove) = CachedMembership.partition(rows, { it.cacheKey }, isFullyCached)
        if (keep.size == rows.size) return emptyList()
        remove.forEach { dao.delete(it.songId) }
        return remove.map { it.songId }
    }

    suspend fun remove(songId: String) = dao.delete(songId)

    suspend fun clear() = dao.clear()

    /** A row counts only while its bytes are still fully in the cache. */
    fun isFullyCached(cacheKey: String, sizeBytes: Long): Boolean =
        bytes.isCached(cacheKey, sizeBytes)

    /**
     * The stream quality that a fully cached copy was stored at, parsed from its
     * stable key. Offline playback must request this exact quality or the cache
     * key will not match and ExoPlayer would try the network instead.
     */
    suspend fun cachedQuality(songId: String): Pair<Int, String>? {
        val row = dao.byId(songId) ?: return null
        if (!isFullyCached(row.cacheKey, row.sizeBytes)) return null
        val params = row.cacheKey.substringAfter('?', "")
            .split('&').mapNotNull { p ->
                val i = p.indexOf('='); if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
            }.toMap()
        val bitrate = params["maxBitRate"]?.toIntOrNull() ?: 0
        val format = params["format"] ?: "raw"
        return bitrate to format
    }

    /** Drops rows the LRU evictor (or an external clear) already removed. */
    suspend fun pruneEvicted(): List<String> {
        val gone = dao.all().filter { row -> !isFullyCached(row.cacheKey, row.sizeBytes) }
        gone.forEach { dao.delete(it.songId) }
        return gone.map { it.songId }
    }

    /**
     * "Clear song cache": evicts every cached song's bytes and drops the rows,
     * so the Cached collection empties too.
     */
    suspend fun clearSongCache() {
        val rows = dao.all()
        rows.forEach { row -> bytes.removeResource(row.cacheKey) }
        dao.clear()
    }

    /** Total cached-audio bytes currently registered. */
    suspend fun usageBytes(): Long = dao.all().fold(0L) { acc, e -> acc + e.sizeBytes }
}
