package com.feishin.mobile.data.queue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
) {
    fun toDomain() = Track(
        id, title, album, albumId, artist, artistId, coverArt, durationSec, trackNo, year, genre, suffix, starred,
        sizeBytes, bitRate, path,
    )

    companion object {
        fun from(t: Track, queueId: Long = -1L) = PersistedTrack(
            t.id, t.title, t.album, t.albumId, t.artist, t.artistId, t.coverArt,
            t.durationSec, t.trackNo, t.year, t.genre, t.suffix, t.starred,
            t.sizeBytes, t.bitRate, t.path, queueId,
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
    ) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        val snapshot = QueueSnapshot(
            tracks = tracks.mapIndexed { i, t -> PersistedTrack.from(t, queueIds?.getOrNull(i) ?: -1L) },
            currentIndex = currentIndex.coerceIn(0, tracks.size - 1),
            positionMs = positionMs.coerceAtLeast(0),
            sourceName = sourceName,
            radioActive = radioActive,
            radioSeedId = radioSeedId,
            shuffleActive = shuffleActive,
            shuffleOrder = shuffleOrder,
            queueSeq = queueSeq,
        )
        runCatching { store.edit { it[key] = json.encodeToString(QueueSnapshot.serializer(), snapshot) } }
    }

    suspend fun load(): QueueSnapshot? = runCatching {
        val raw = store.data.first()[key] ?: return@runCatching null
        json.decodeFromString(QueueSnapshot.serializer(), raw)
    }.getOrNull()?.takeIf { it.tracks.isNotEmpty() }

    suspend fun loadTracks(): List<Track> = load()?.tracks?.map { it.toDomain() }.orEmpty()

    suspend fun clear() = runCatching { store.edit { it.remove(key) } }.let { }
}
