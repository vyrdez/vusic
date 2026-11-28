package com.feishin.mobile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies Room [MIGRATION_7_8]:
 * - preserves all v7 rows across `downloads`, `download_groups`, `metadata_cache`,
 *   `cached_covers`, `pending_actions`, `cached_tracks`, and `lyrics_cache`,
 * - creates `drive_tracks`, `local_playlists`, `local_playlist_tracks` (with CASCADE FK),
 *   `local_favorites`, and `local_play_history` matching `schemas/8.json`,
 * - creates all expected v8 indices.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java,
    )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration7To8_preservesV7RowsAndCreatesDriveAndLocalTables() {
        helper.createDatabase(TEST_DB, 7).use { v7 ->
            seedV7(v7)
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { v8 ->
            assertV7RowsPreserved(v8)
            assertV8IndicesPresent(v8)
            assertV8InsertsAndCascadeWork(v8)
        }
    }

    private fun seedV7(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO downloads (
                songId, title, artist, album, albumId, artistId, coverArt,
                durationSec, path, bytesDownloaded, expectedBytes, status, progress,
                filePath, groupId, enqueuedAt, profileId
            ) VALUES (
                'p1:song-1', 'Track 1', 'Artist 1', 'Album 1', 'p1:al-1', 'p1:ar-1', 'p1:cov-1',
                200, '/m/1.flac', 1000, 1000, 'COMPLETED', 1.0,
                '/downloads/p1/song-1.flac', 'album:p1:al-1', 100, 'p1'
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO download_groups (groupId, type, name, keepDownloaded, updatedAt, profileId)
            VALUES ('album:p1:al-1', 'album', 'Album 1', 1, 100, 'p1')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO metadata_cache (`key`, json, updatedAt)
            VALUES ('p1:artists', '[{"id":"p1:ar-1"}]', 1000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO cached_covers (coverArtId, filePath, sizeBytes)
            VALUES ('p1:cov-1', '/covers/p1_cov-1.jpg', 500)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO pending_actions (type, targetId, submission, timeMs, profileId)
            VALUES ('SCROBBLE', 'p1:song-1', 1, 1000, 'p1')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO cached_tracks (
                songId, title, artist, album, durationSec, cacheKey, sizeBytes, cachedAt, lastPlayedAt, profileId
            ) VALUES (
                'p1:song-1', 'Track 1', 'Artist 1', 'Album 1', 200,
                'https://srv/rest/stream.view?id=song-1', 12345, 1000, 1500, 'p1'
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO lyrics_cache (songId, json, updatedAt, profileId)
            VALUES ('p1:song-1', '{"kind":"SYNCED"}', 1000, 'p1')
            """.trimIndent(),
        )
    }

    private fun assertV7RowsPreserved(db: SupportSQLiteDatabase) {
        db.query("SELECT songId, profileId, filePath FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:song-1", c.getString(0))
            assertEquals("p1", c.getString(1))
            assertEquals("/downloads/p1/song-1.flac", c.getString(2))
        }
        db.query("SELECT groupId, profileId FROM download_groups").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("album:p1:al-1", c.getString(0))
            assertEquals("p1", c.getString(1))
        }
        db.query("SELECT `key`, json FROM metadata_cache").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:artists", c.getString(0))
        }
        db.query("SELECT coverArtId, filePath FROM cached_covers").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:cov-1", c.getString(0))
        }
        db.query("SELECT targetId, profileId FROM pending_actions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:song-1", c.getString(0))
            assertEquals("p1", c.getString(1))
        }
        db.query("SELECT songId, cacheKey, profileId FROM cached_tracks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:song-1", c.getString(0))
            assertEquals("https://srv/rest/stream.view?id=song-1", c.getString(1))
            assertEquals("p1", c.getString(2))
        }
        db.query("SELECT songId, profileId FROM lyrics_cache").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p1:song-1", c.getString(0))
            assertEquals("p1", c.getString(1))
        }
    }

    private fun assertV8IndicesPresent(db: SupportSQLiteDatabase) {
        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            while (c.moveToNext()) {
                indices += c.getString(0)
            }
        }
        val expected = listOf(
            "index_drive_tracks_profileId",
            "index_drive_tracks_profileId_albumKey",
            "index_drive_tracks_profileId_artistKey",
            "index_drive_tracks_profileId_parentFolderId",
            "index_local_playlist_tracks_playlistId",
            "index_local_favorites_kind",
            "index_local_favorites_profileId",
            "index_local_play_history_profileId_playedAtMs",
            "index_local_play_history_albumKey",
        )
        for (idx in expected) {
            assertTrue("Missing expected index: $idx (found=$indices)", idx in indices)
        }
    }

    private fun assertV8InsertsAndCascadeWork(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")

        db.execSQL(
            """
            INSERT INTO drive_tracks (
                songId, profileId, driveFileId, fileName, parentFolderId,
                title, artist, album, albumKey, artistKey
            ) VALUES (
                'gd1:file-1', 'gd1', 'file-1', '01 - Track.flac', 'folder-1',
                'Track', 'Artist', 'Album', 'gd1:al:abc', 'gd1:ar:def'
            )
            """.trimIndent(),
        )
        db.query("SELECT parentFolderPath, albumArtist, durationSec, sizeBytes, tagsParsed FROM drive_tracks WHERE songId = 'gd1:file-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0L, c.getLong(3))
            assertEquals(0, c.getInt(4))
        }

        db.execSQL(
            """
            INSERT INTO local_playlists (playlistId, name, sourceM3uName, createdAtMs, updatedAtMs)
            VALUES ('local_pl:1', 'Mix', 'mix.m3u8', 1000, 1000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO local_playlist_tracks (playlistId, trackId, position, trackJson, addedAtMs)
            VALUES ('local_pl:1', 'gd1:file-1', 0, '{"id":"gd1:file-1"}', 1000)
            """.trimIndent(),
        )
        db.execSQL("DELETE FROM local_playlists WHERE playlistId = 'local_pl:1'")
        db.query("SELECT COUNT(*) FROM local_playlist_tracks WHERE playlistId = 'local_pl:1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.execSQL(
            """
            INSERT INTO local_favorites (targetId, kind, profileId, payloadJson, starredAtMs)
            VALUES ('gd1:file-1', 'TRACK', 'gd1', '{"id":"gd1:file-1"}', 2000)
            """.trimIndent(),
        )
        db.query("SELECT kind, profileId FROM local_favorites WHERE targetId = 'gd1:file-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("TRACK", c.getString(0))
            assertEquals("gd1", c.getString(1))
        }

        db.execSQL(
            """
            INSERT INTO local_play_history (trackId, profileId, albumKey, artistKey, trackJson, albumJson, playedAtMs)
            VALUES ('gd1:file-1', 'gd1', 'gd1:al:abc', 'gd1:ar:def', '{"id":"gd1:file-1"}', '{"id":"gd1:al:abc"}', 3000)
            """.trimIndent(),
        )
        db.query("SELECT trackId, albumKey, playedAtMs FROM local_play_history WHERE profileId = 'gd1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("gd1:file-1", c.getString(0))
            assertEquals("gd1:al:abc", c.getString(1))
            assertEquals(3000L, c.getLong(2))
        }
    }

    companion object {
        private const val TEST_DB = "migration_7_to_8_test.db"
    }
}
