package com.feishin.mobile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies Room [MIGRATION_6_7]:
 * - adds `profileId` (default `'p1'`) and indices on profile-scoped tables,
 * - backfills legacy unprefixed IDs to `'p1:'` while leaving `local:` / `content://` / `file://`
 *   and already-prefixed IDs untouched,
 * - preserves `cached_tracks.cacheKey` 100% untouched so existing v6 SimpleCache spans hit on disk,
 * - clears unprefixed `metadata_cache` payloads.
 */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

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
    fun migration6To7_backfillsIdsPreservesCacheKeyAndClearsMetadata() {
        // 1. Seed a real v6 database from schemas/6.json
        helper.createDatabase(TEST_DB, 6).use { v6 ->
            seedV6(v6)
        }

        // 2. Run MIGRATION_6_7 and validate against schemas/7.json
        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { v7 ->
            assertDownloads(v7)
            assertDownloadGroups(v7)
            assertPendingActions(v7)
            assertCachedTracks(v7)
            assertLyricsCache(v7)
            assertCachedCovers(v7)
            assertMetadataCacheCleared(v7)
            assertProfileIndicesPresent(v7)
        }
    }

    private fun seedV6(db: SupportSQLiteDatabase) {
        // downloads: legacy remote album, legacy remote playlist, device-local, and already-prefixed
        db.execSQL(
            """
            INSERT INTO downloads (
                songId, title, artist, album, albumId, artistId, coverArt,
                durationSec, path, bytesDownloaded, expectedBytes, status, progress,
                filePath, groupId, enqueuedAt
            ) VALUES
            ('song-1', 'Track 1', 'Artist 1', 'Album 1', 'al-1', 'ar-1', 'cov-1',
             200, '/m/1.flac', 1000, 1000, 'COMPLETED', 1.0,
             '/downloads/legacy/song-1.flac', 'album:al-1', 100),
            ('song-2', 'Track 2', 'Artist 2', 'Album 2', 'al-2', 'ar-2', 'content://media/art/2',
             180, '/m/2.mp3', 0, 500, 'QUEUED', 0.0,
             NULL, 'playlist:pl-1', 200),
            ('local:9', 'Local Track', 'Local Artist', 'Local Album', 'local_album:3', 'local_artist:4', 'file:///storage/art.jpg',
             240, '', 0, 0, 'COMPLETED', 1.0,
             '/storage/local9.flac', 'favorites', 300),
            ('p2:song-9', 'Remote P2', 'Artist P2', 'Album P2', 'p2:al-9', 'p2:ar-9', 'p2:cov-9',
             210, '/m/9.mp3', 800, 800, 'COMPLETED', 1.0,
             '/downloads/p2/song-9.mp3', 'album:p2:al-9', 400)
            """.trimIndent(),
        )

        // download_groups
        db.execSQL(
            """
            INSERT INTO download_groups (groupId, type, name, keepDownloaded, updatedAt) VALUES
            ('album:al-1', 'album', 'Album 1', 1, 100),
            ('playlist:pl-1', 'playlist', 'Playlist 1', 1, 200),
            ('favorites', 'favorites', 'Favorites', 1, 300),
            ('playlist:p2:pl-2', 'playlist', 'P2 Playlist', 1, 400)
            """.trimIndent(),
        )

        // pending_actions
        db.execSQL(
            """
            INSERT INTO pending_actions (type, targetId, submission, timeMs) VALUES
            ('SCROBBLE', 'song-1', 1, 1000),
            ('STAR', 'p2:other', 1, 2000)
            """.trimIndent(),
        )

        // cached_tracks
        db.execSQL(
            """
            INSERT INTO cached_tracks (
                songId, title, artist, album, durationSec, cacheKey, sizeBytes, cachedAt, lastPlayedAt
            ) VALUES
            ('song-1', 'Track 1', 'Artist 1', 'Album 1', 200,
             'https://srv/rest/stream.view?id=song-1', 12345, 1000, 1500),
            ('local:5', 'Local 5', 'Local Artist', 'Local Album', 150,
             'content://5', 6789, 2000, 2500)
            """.trimIndent(),
        )

        // lyrics_cache
        db.execSQL(
            """
            INSERT INTO lyrics_cache (songId, json, updatedAt) VALUES
            ('song-1', '{"kind":"SYNCED"}', 1000),
            ('local:5', '{"kind":"PLAIN"}', 2000)
            """.trimIndent(),
        )

        // cached_covers
        db.execSQL(
            """
            INSERT INTO cached_covers (coverArtId, filePath, sizeBytes) VALUES
            ('cov-1', '/covers/cov-1.jpg', 500),
            ('content://media/art/2', '/covers/c2.jpg', 600),
            ('file:///storage/art.jpg', '/covers/c3.jpg', 700),
            ('p2:cov-9', '/covers/c4.jpg', 800)
            """.trimIndent(),
        )

        // metadata_cache
        db.execSQL(
            """
            INSERT INTO metadata_cache (`key`, json, updatedAt) VALUES
            ('artists', '[{"id":"ar-1"}]', 1000),
            ('albums:newest:0', '[{"id":"al-1"}]', 1000)
            """.trimIndent(),
        )
    }

    private fun assertDownloads(db: SupportSQLiteDatabase) {
        val rows = mutableMapOf<String, Map<String, String?>>()
        db.query(
            "SELECT songId, albumId, artistId, coverArt, groupId, filePath, profileId FROM downloads",
        ).use { c ->
            while (c.moveToNext()) {
                val songId = c.getString(0)
                rows[songId] = mapOf(
                    "albumId" to if (c.isNull(1)) null else c.getString(1),
                    "artistId" to if (c.isNull(2)) null else c.getString(2),
                    "coverArt" to if (c.isNull(3)) null else c.getString(3),
                    "groupId" to if (c.isNull(4)) null else c.getString(4),
                    "filePath" to if (c.isNull(5)) null else c.getString(5),
                    "profileId" to c.getString(6),
                )
            }
        }
        assertEquals(setOf("p1:song-1", "p1:song-2", "local:9", "p2:song-9"), rows.keys)
        assertFalse(rows.containsKey("song-1"))

        val s1 = rows.getValue("p1:song-1")
        assertEquals("p1:al-1", s1["albumId"])
        assertEquals("p1:ar-1", s1["artistId"])
        assertEquals("p1:cov-1", s1["coverArt"])
        assertEquals("album:p1:al-1", s1["groupId"])
        assertEquals("/downloads/legacy/song-1.flac", s1["filePath"])
        assertEquals("p1", s1["profileId"])

        val s2 = rows.getValue("p1:song-2")
        assertEquals("p1:al-2", s2["albumId"])
        assertEquals("p1:ar-2", s2["artistId"])
        assertEquals("content://media/art/2", s2["coverArt"])
        assertEquals("playlist:p1:pl-1", s2["groupId"])
        assertEquals("p1", s2["profileId"])

        val local = rows.getValue("local:9")
        assertEquals("local_album:3", local["albumId"])
        assertEquals("local_artist:4", local["artistId"])
        assertEquals("file:///storage/art.jpg", local["coverArt"])
        assertEquals("favorites", local["groupId"])
        assertEquals("p1", local["profileId"])

        val p2 = rows.getValue("p2:song-9")
        assertEquals("p2:al-9", p2["albumId"])
        assertEquals("p2:ar-9", p2["artistId"])
        assertEquals("p2:cov-9", p2["coverArt"])
        assertEquals("album:p2:al-9", p2["groupId"])
        assertEquals("p1", p2["profileId"])
    }

    private fun assertDownloadGroups(db: SupportSQLiteDatabase) {
        val groups = mutableMapOf<String, String>()
        db.query("SELECT groupId, profileId FROM download_groups").use { c ->
            while (c.moveToNext()) {
                groups[c.getString(0)] = c.getString(1)
            }
        }
        assertEquals(
            mapOf(
                "album:p1:al-1" to "p1",
                "playlist:p1:pl-1" to "p1",
                "favorites" to "p1",
                "playlist:p2:pl-2" to "p1",
            ),
            groups,
        )
    }

    private fun assertPendingActions(db: SupportSQLiteDatabase) {
        val actions = mutableListOf<Pair<String, String>>()
        db.query("SELECT targetId, profileId FROM pending_actions ORDER BY timeMs ASC").use { c ->
            while (c.moveToNext()) {
                actions += c.getString(0) to c.getString(1)
            }
        }
        assertEquals(listOf("p1:song-1" to "p1", "p2:other" to "p1"), actions)
    }

    private fun assertCachedTracks(db: SupportSQLiteDatabase) {
        val tracks = mutableMapOf<String, Pair<String, String>>()
        db.query("SELECT songId, cacheKey, profileId FROM cached_tracks").use { c ->
            while (c.moveToNext()) {
                tracks[c.getString(0)] = c.getString(1) to c.getString(2)
            }
        }
        assertEquals(
            mapOf(
                "p1:song-1" to ("https://srv/rest/stream.view?id=song-1" to "p1"),
                "local:5" to ("content://5" to "p1"),
            ),
            tracks,
        )
    }

    private fun assertLyricsCache(db: SupportSQLiteDatabase) {
        val lyrics = mutableMapOf<String, String>()
        db.query("SELECT songId, profileId FROM lyrics_cache").use { c ->
            while (c.moveToNext()) {
                lyrics[c.getString(0)] = c.getString(1)
            }
        }
        assertEquals(mapOf("p1:song-1" to "p1", "local:5" to "p1"), lyrics)
    }

    private fun assertCachedCovers(db: SupportSQLiteDatabase) {
        val covers = mutableSetOf<String>()
        db.query("SELECT coverArtId FROM cached_covers").use { c ->
            while (c.moveToNext()) {
                covers += c.getString(0)
            }
        }
        assertEquals(
            setOf("p1:cov-1", "content://media/art/2", "file:///storage/art.jpg", "p2:cov-9"),
            covers,
        )
    }

    private fun assertMetadataCacheCleared(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM metadata_cache").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    private fun assertProfileIndicesPresent(db: SupportSQLiteDatabase) {
        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            while (c.moveToNext()) {
                indices += c.getString(0)
            }
        }
        val expected = listOf(
            "index_downloads_profileId",
            "index_download_groups_profileId",
            "index_pending_actions_profileId",
            "index_cached_tracks_profileId",
            "index_lyrics_cache_profileId",
        )
        for (idx in expected) {
            assertTrue("Missing expected index: $idx (found=$indices)", idx in indices)
        }
    }

    companion object {
        private const val TEST_DB = "migration_6_to_7_test.db"
    }
}
