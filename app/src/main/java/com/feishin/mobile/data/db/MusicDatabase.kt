package com.feishin.mobile.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Lifecycle of a stored download. Stored as its name, so it is migration-safe. */
enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED }

/**
 * One downloadable song. Holds everything needed to play and display the track
 * with no network at all, plus the transfer state so downloads resume after a
 * process kill.
 */
@Entity(tableName = "downloads", indices = [Index("groupId"), Index("status")])
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val durationSec: Int = 0,
    val trackNo: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val suffix: String? = null,
    val path: String = "",
    val replayGainTrack: Double? = null,
    val replayGainAlbum: Double? = null,
    val replayGainPeak: Double? = null,
    /** Finished file. Null until the transfer completes. */
    val filePath: String? = null,
    /** Partial file used for Range resume. */
    val tempPath: String? = null,
    val bytesDownloaded: Long = 0,
    val expectedBytes: Long = 0,
    val status: String = DownloadStatus.QUEUED.name,
    val progress: Float = 0f,
    val error: String? = null,
    /** "album:<id>", "playlist:<id>", "favorites" or null for a one-off. */
    val groupId: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED)

    /** Bytes on disk: equals the finished size once the transfer completes. */
    val sizeBytes: Long get() = bytesDownloaded
}

/** A collection the user asked to keep downloaded, so it can stay in sync. */
@Entity(tableName = "download_groups")
data class DownloadGroupEntity(
    @PrimaryKey val groupId: String,
    /** album | playlist | favorites */
    val type: String,
    val name: String = "",
    val keepDownloaded: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Library payload cache (single JSON blob per kind) for offline browsing. */
@Entity(tableName = "metadata_cache")
data class CachedMetadataEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Cover art saved next to its song so artwork renders with no network. */
@Entity(tableName = "cached_covers")
data class CachedCoverEntity(
    @PrimaryKey val coverArtId: String,
    val filePath: String,
    val sizeBytes: Long = 0,
)

/**
 * A side effect performed while offline (scrobble, star, unstar) that must be
 * replayed against the server with its original timestamp once online.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** scrobble | star | unstar | starAlbum | unstarAlbum | starArtist | unstarArtist */
    val type: String,
    val targetId: String,
    val submission: Boolean = true,
    /** Original wall-clock time, so scrobbles keep the right timestamp. */
    val timeMs: Long = System.currentTimeMillis(),
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY enqueuedAt ASC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE songId = :id LIMIT 1")
    suspend fun byId(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE songId = :id LIMIT 1")
    fun observeById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completed(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','PAUSED') ORDER BY enqueuedAt ASC")
    suspend fun pending(): List<DownloadEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE status IN ('QUEUED','DOWNLOADING'))")
    suspend fun hasPending(): Boolean

    @Query(
        "UPDATE downloads SET status = 'QUEUED', error = NULL " +
            "WHERE status = 'DOWNLOADING' OR (status = 'PAUSED' AND error = :waitingWifi) " +
            "OR (status = 'FAILED' AND error LIKE 'Network error:%')",
    )
    suspend fun requeueInterrupted(
        waitingWifi: String = com.feishin.mobile.core.common.MobileDataPolicy.WAITING_FOR_WIFI,
    )

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY enqueuedAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE groupId = :groupId")
    suspend fun byGroup(groupId: String): List<DownloadEntity>

    @Query("SELECT songId FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<DownloadEntity>)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE songId = :id")
    suspend fun setStatus(id: String, status: String, error: String? = null)

    @Query(
        "UPDATE downloads SET bytesDownloaded = :bytes, progress = :progress, tempPath = :tempPath, status = :status " +
            "WHERE songId = :id",
    )
    suspend fun setProgress(id: String, bytes: Long, progress: Float, tempPath: String?, status: String)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', filePath = :filePath, tempPath = NULL, " +
            "bytesDownloaded = :size, expectedBytes = :size, progress = 1.0, error = NULL, completedAt = :now " +
            "WHERE songId = :id",
    )
    suspend fun markCompleted(id: String, filePath: String, size: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED')")
    suspend fun clearPending()

    @Query("SELECT filePath FROM downloads WHERE status IN ('DOWNLOADING', 'PAUSED') AND filePath IS NOT NULL")
    suspend fun partialFilePaths(): List<String>

    @Query("DELETE FROM downloads WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM downloads WHERE coverArt = :coverArtId AND status = 'COMPLETED'")
    suspend fun completedCoverCount(coverArtId: String): Int

    @Query("SELECT DISTINCT coverArt FROM downloads WHERE status = 'COMPLETED' AND coverArt IS NOT NULL AND coverArt NOT IN (SELECT coverArtId FROM cached_covers)")
    suspend fun missingCoverArtIds(): List<String>
}

@Dao
interface DownloadGroupDao {
    @Query("SELECT * FROM download_groups")
    fun observeAll(): Flow<List<DownloadGroupEntity>>

    @Query("SELECT * FROM download_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun byId(groupId: String): DownloadGroupEntity?

    @Query("SELECT groupId FROM download_groups WHERE keepDownloaded = 1")
    suspend fun keepIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: DownloadGroupEntity)

    @Query("DELETE FROM download_groups WHERE groupId = :groupId")
    suspend fun delete(groupId: String)

    @Query("DELETE FROM download_groups")
    suspend fun clear()
}

@Dao
interface MetadataCacheDao {
    @Query("SELECT json FROM metadata_cache WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CachedMetadataEntity)

    @Query("DELETE FROM metadata_cache WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM metadata_cache")
    suspend fun clear()
}

@Dao
interface CachedCoverDao {
    @Query("SELECT filePath FROM cached_covers WHERE coverArtId = :id LIMIT 1")
    suspend fun path(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cover: CachedCoverEntity)

    @Query("SELECT sizeBytes FROM cached_covers")
    suspend fun allSizes(): List<Long>

    @Query("DELETE FROM cached_covers WHERE coverArtId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_covers")
    suspend fun clear()
}

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY timeMs ASC")
    suspend fun all(): List<PendingActionEntity>

    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}

/**
 * Stream-cache registry: one row per fully cached song. The bytes themselves
 * live in Media3's SimpleCache (LRU); this table is what makes the "Cached"
 * collection work with no network, and what gets pruned when bytes evaporate.
 */
@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val durationSec: Int = 0,
    val trackNo: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val suffix: String? = null,
    /** Stable cache key (song + quality, no per-request salt). */
    val cacheKey: String,
    val sizeBytes: Long = 0,
    val cachedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
)

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks ORDER BY cachedAt DESC")
    suspend fun all(): List<CachedTrackEntity>

    @Query("SELECT * FROM cached_tracks WHERE songId = :id LIMIT 1")
    suspend fun byId(id: String): CachedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: CachedTrackEntity)

    @Query("UPDATE cached_tracks SET lastPlayedAt = :now WHERE songId = :id")
    suspend fun touchPlayed(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM cached_tracks WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cached_tracks")
    suspend fun count(): Int
}

/** Cached lyrics for a song, keyed by song id (or local URI key), so they open instantly offline. */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val songId: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE songId = :id LIMIT 1")
    suspend fun getEntry(id: String): LyricsCacheEntity?

    @Query("SELECT json FROM lyrics_cache WHERE songId = :id LIMIT 1")
    suspend fun get(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE songId = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clear()
}

/** 5 -> 6: adds the lyrics_cache table for instant offline lyrics. */
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                songId TEXT NOT NULL,
                json TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY (songId)
            )
        """.trimIndent())
    }
}

@androidx.room.Database(
    entities = [
        DownloadEntity::class,
        DownloadGroupEntity::class,
        CachedMetadataEntity::class,
        CachedCoverEntity::class,
        PendingActionEntity::class,
        CachedTrackEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun downloadGroupDao(): DownloadGroupDao
    abstract fun metadataCacheDao(): MetadataCacheDao
    abstract fun cachedCoverDao(): CachedCoverDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}
