package com.feishin.mobile.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the 5 -> 6 Room migration adds the lyrics_cache table and preserves
 * existing data.
 *
 * The database is created at the current version first (so the v5 tables come
 * from Room itself, not a hand-copied DDL), then rewound to look like v5: the
 * new table is dropped and user_version is set back to 5. Re-opening it with
 * [MIGRATION_5_6] therefore exercises the real migration path.
 */
class LyricsCacheMigrationTest {

    @Test
    fun migrationFiveToSixAddsLyricsCacheTableAndKeepsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = File(context.cacheDir, "lyrics_migration_${System.currentTimeMillis()}.db")

        try {
            // --- seed data, then rewind the schema to version 5 ---
            val seed = Room.databaseBuilder(
                context,
                MusicDatabase::class.java,
                dbFile.absolutePath,
            )
                .fallbackToDestructiveMigration()
                .build()
            runBlocking {
                seed.downloadDao().upsert(
                    DownloadEntity(
                        songId = "song-1",
                        title = "Old song",
                        artist = "Old artist",
                        album = "Old album",
                        status = DownloadStatus.COMPLETED.name,
                    ),
                )
                assertEquals(1, seed.downloadDao().completedIds().size)
            }
            seed.openHelper.writableDatabase.apply {
                execSQL("DROP TABLE IF EXISTS lyrics_cache")
                execSQL("PRAGMA user_version = 5")
            }
            seed.close()

            // --- version 6: the migration must run, lyrics_cache appear, old data kept ---
            val v6 = Room.databaseBuilder(
                context,
                MusicDatabase::class.java,
                dbFile.absolutePath,
            )
                .addMigrations(MIGRATION_5_6)
                .build()
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
