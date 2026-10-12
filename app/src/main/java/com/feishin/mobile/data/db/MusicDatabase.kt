package com.feishin.mobile.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadedTrack(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloads WHERE songId = :id LIMIT 1")
    suspend fun byId(id: String): DownloadedTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: DownloadedTrack)

    @Query("DELETE FROM downloads WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}

@androidx.room.Database(entities = [DownloadedTrack::class], version = 1, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
