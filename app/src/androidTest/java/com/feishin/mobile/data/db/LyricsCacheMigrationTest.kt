package com.feishin.mobile.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the 5 -> 6 Room migration adds the lyrics_cache table and preserves existing data. */
class LyricsCacheMigrationTest {

    @Test
    fun migrationFiveToSixAddsLyricsCacheTableAndKeepsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = File(context.cacheDir, "lyrics_migration_${System.currentTimeMillis()}.db")

        try {
            // --- version 5: existing schema, no migration ---
            val v5 = Room.databaseBuilder(
                context,
                MusicDatabase::class.java,
                dbFile.absolutePath,
            )
                .fallbackToDestructiveMigration()
                .build()
            runBlocking {
                v5.downloadDao().upsert(
                    DownloadEntity(
                        songId = "song-1",
                        title = "Old song",
                        artist = "Old artist",
                        album = "Old album",
                        status = DownloadStatus.COMPLETED.name,
                    ),
                )
                assertEquals(1, v5.downloadDao().completedIds().size)
            }
            v5.close()

            // --- version 6: migration runs, lyrics_cache appears, old data kept ---
            val v6 = Room.databaseBuilder(
                context,
                MusicDatabase::class.java,
                dbFile.absolutePath,
            )
                .addMigrations(MIGRATION_5_6)
                .build()
            assertEquals(5, MIGRATION_5_6.startVersion)
            assertEquals(6, MIGRATION_5_6.endVersion)
            runBlocking {
                assertEquals(1, v6.downloadDao().completedIds().size)

                val dao = v6.lyricsCacheDao()
                assertNull(dao.get("song-1"))
                dao.put(LyricsCacheEntity("song-1", "{\"kind\":\"SYNCED\"}", System.currentTimeMillis()))
                assertNotNull(dao.get("song-1"))
                dao.remove("song-1")
                assertNull(dao.get("song-1"))
            }
            v6.close()
        } finally {
            dbFile.delete()
        }
    }
}
