package com.feishin.mobile.data.queue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.queueDataStore by preferencesDataStore(name = "feishin_queue")

@Serializable
data class PersistedTrack(
    val id: String,
    val title: String,
    val album: String = "",
    val albumId: String? = null,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val durationSec: Int = 0,
    val trackNo: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val suffix: String? = null,
    val starred: Boolean = false,
    val sizeBytes: Long = 0,
    val bitRate: Int? = null,
    val path: String = "",
    /** Per-entry queue key; -1 for snapshots written before it existed. */
    val queueId: Long = -1L,
    /**
     * Whether the entry was a finished download / a fully cached stream when
     * the queue was saved. Descriptive only: playback still resolves the real
     * file or cache entry at restore time, because a download can be deleted
     * and a cached span evicted while the app was gone.
     */
    val downloaded: Boolean = false,
    val cached: Boolean = false,
    val localUri: String? = null,
    val localModifiedSec: Long = 0L,
) {
    fun toDomain() = Track(
        id = id,
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
        starred = starred,
        sizeBytes = sizeBytes,
        bitRate = bitRate,
        path = path,
        localUri = localUri,
        localModifiedSec = localModifiedSec,
    )

    companion object {
        fun from(t: Track, queueId: Long = -1L, downloaded: Boolean = false, cached: Boolean = false) =
            PersistedTrack(
                id = t.id,
                title = t.title,
                album = t.album,
                albumId = t.albumId,
                artist = t.artist,
                artistId = t.artistId,
                coverArt = t.coverArt,
                durationSec = t.durationSec,
                trackNo = t.trackNo,
                year = t.year,
                genre = t.genre,
                suffix = t.suffix,
                starred = t.starred,
                sizeBytes = t.sizeBytes,
                bitRate = t.bitRate,
                path = t.path,
                queueId = queueId,
                downloaded = downloaded,
                cached = cached,
                localUri = t.localUri,
                localModifiedSec = t.localModifiedSec,
            )
    }
}

@Serializable
data class QueueSnapshot(
    val tracks: List<PersistedTrack> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val sourceName: String? = null,
    /** Radio auto-DJ survives restarts: keep appending after a process kill. */
    val radioActive: Boolean = false,
    val radioSeedId: String? = null,
    /** Shuffle state + the order to restore when it is turned off. */
    val shuffleActive: Boolean = false,
    val shuffleOrder: List<Long> = emptyList(),
    /** Next queue-key counter, so restored entries never collide. */
    val queueSeq: Long = 0L,
    /** Controller repeat mode (Media3 values: 0 off, 1 one, 2 all). */
    val repeatMode: Int = 0,
)

/**
 * Persists the queue + position so the app restores (paused) after being killed.
 * Takes the DataStore directly so unit tests can back it with a temp file.
 */
@Singleton
class QueueStore internal constructor(
    private val store: DataStore<Preferences>,
) {
    /** Hilt path: app-private DataStore file. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.queueDataStore)

    private val json = Json { ignoreUnknownKeys = true }
    private val key: Preferences.Key<String> = stringPreferencesKey("queue_snapshot")
    /**
     * The playback position lives in its OWN key. It moves every few seconds
     * while playing, and rewriting the whole 100-song JSON that often would be
     * pointless disk churn; a single long is cheap. [save] always writes both
     * keys in one edit, so a snapshot can never come back with a position that
     * belongs to a different track.
     */
    private val positionKey: Preferences.Key<Long> = longPreferencesKey("queue_position")

    suspend fun save(
        tracks: List<Track>,
        currentIndex: Int,
        positionMs: Long,
        sourceName: String? = null,
        radioActive: Boolean = false,
        radioSeedId: String? = null,
        queueIds: List<Long>? = null,
        shuffleActive: Boolean = false,
        shuffleOrder: List<Long> = emptyList(),
        queueSeq: Long = 0L,
        repeatMode: Int = 0,
        downloadedIds: Set<String> = emptySet(),
        cachedIds: Set<String> = emptySet(),
    ) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        val pos = positionMs.coerceAtLeast(0)
        val snapshot = QueueSnapshot(
            tracks = tracks.mapIndexed { i, t ->
                PersistedTrack.from(
                    t,
                    queueIds?.getOrNull(i) ?: -1L,
                    downloaded = t.id in downloadedIds,
                    cached = t.id in cachedIds,
                )
            },
            currentIndex = currentIndex.coerceIn(0, tracks.size - 1),
            positionMs = pos,
            sourceName = sourceName,
            radioActive = radioActive,
            radioSeedId = radioSeedId,
            shuffleActive = shuffleActive,
            shuffleOrder = shuffleOrder,
            queueSeq = queueSeq,
            repeatMode = repeatMode,
        )
        runCatching {
            store.edit {
                it[key] = json.encodeToString(QueueSnapshot.serializer(), snapshot)
                it[positionKey] = pos
            }
        }
    }

    /**
     * Cheap position-only write (every few seconds while playing). Does nothing
     * when there is no snapshot: a position without a queue means nothing, and
     * it must never resurrect a cleared queue.
     */
    suspend fun savePosition(positionMs: Long) {
        runCatching {
            store.edit {
                if (it.contains(key)) it[positionKey] = positionMs.coerceAtLeast(0)
            }
        }
    }

    suspend fun load(): QueueSnapshot? = runCatching {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return@runCatching null
        val snapshot = json.decodeFromString(QueueSnapshot.serializer(), raw)
        // The side key is written in the same edit as the snapshot, so it
        // always belongs to the current track.
        snapshot.copy(positionMs = prefs[positionKey] ?: snapshot.positionMs)
    }.getOrNull()?.takeIf { it.tracks.isNotEmpty() }

    suspend fun loadTracks(): List<Track> = load()?.tracks?.map { it.toDomain() }.orEmpty()

    suspend fun clear() = runCatching {
        store.edit {
            it.remove(key)
            it.remove(positionKey)
        }
    }.let { }
}
