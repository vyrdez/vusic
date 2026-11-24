package com.feishin.mobile.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the 5 -> 6 -> 7 Room migration path adds the lyrics_cache table,
 * migrates to v7 with profile namespacing, and preserves existing data.
 *
 * A real v6 schema is created via [MigrationTestHelper], seeded with a legacy
 * download, and rewound to v5 by dropping `lyrics_cache` and resetting
 * `user_version = 5` (since v6 only added `lyrics_cache`). Running
 * [MIGRATION_5_6] followed by [MIGRATION_6_7] exercises the full upgrade path.
 */
@RunWith(AndroidJUnit4::class)
class LyricsCacheMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java,
    )

    @Test
    fun migrationFiveToSixAddsLyricsCacheTableAndKeepsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "lyrics_migration_${System.currentTimeMillis()}.db"

        try {
            // --- create real v6 DB, seed legacy data, then rewind to version 5 ---
            helper.createDatabase(dbName, 6).use { db ->
                db.execSQL(
                    """
                    INSERT INTO downloads (
                        songId, title, artist, album, durationSec, path,
                        bytesDownloaded, expectedBytes, status, progress, enqueuedAt
                    ) VALUES (
                        'song-1', 'Old song', 'Old artist', 'Old album', 180, '',
                        100, 100, 'COMPLETED', 1.0, 1000
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE IF EXISTS lyrics_cache")
                db.execSQL("PRAGMA user_version = 5")
            }

            // --- migrate 5 -> 6 -> 7 and validate against schemas/7.json ---
            helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_5_6, MIGRATION_6_7).close()

            // --- open via Room v7: old download kept & backfilled, lyrics_cache usable ---
            val v7 = Room.databaseBuilder(
                context,
                MusicDatabase::class.java,
                dbName,
            )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                .build()
            try {
                runBlocking {
                    assertEquals(listOf("p1:song-1"), v7.downloadDao().completedIds())

                    val dao = v7.lyricsCacheDao()
                    assertNull(dao.get("p1:song-1"))
                    dao.put(
                        LyricsCacheEntity(
                            songId = "p1:song-1",
                            json = "{\"kind\":\"SYNCED\"}",
                            updatedAt = System.currentTimeMillis(),
                            profileId = "p1",
                        ),
                    )
                    assertNotNull(dao.get("p1:song-1"))
                    dao.remove("p1:song-1")
                    assertNull(dao.get("p1:song-1"))
                }
            } finally {
                v7.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
