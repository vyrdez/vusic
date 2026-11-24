package com.feishin.mobile.core.common

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feishin.mobile.core.network.CoverCacheKey
import com.feishin.mobile.data.cache.CacheBytes
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.db.CachedTrackDao
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.ui.home.DiskHomeCache
import com.feishin.mobile.ui.home.HomeUi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class P12CacheAndDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---------- StableCacheKey ----------

    @Test
    fun stableCacheKey_p1AndNullPreserveV6Format() {
        val url = "https://music.example.com:443/rest/stream.view?id=s1&maxBitRate=320&format=mp3&u=a&t=b&s=c"
        val expected = "https://music.example.com/rest/stream.view?format=mp3&id=s1&maxBitRate=320"
        assertEquals(expected, StableCacheKey.keyFor(url))
        assertEquals(expected, StableCacheKey.keyFor(url, null))
        assertEquals(expected, StableCacheKey.keyFor(url, "p1"))
    }

    @Test
    fun stableCacheKey_nonP1IncludesProfileAndNonDefaultPort() {
        val customPortUrl = "http://nas.local:4533/rest/stream.view?id=s1&maxBitRate=192&format=opus&t=abc"
        assertEquals(
            "p2:http://nas.local:4533/rest/stream.view?format=opus&id=s1&maxBitRate=192",
            StableCacheKey.keyFor(customPortUrl, "p2"),
        )

        val defaultHttpsUrl = "https://nas.local:443/rest/stream.view?id=s1&t=abc"
        assertEquals(
            "p2:https://nas.local/rest/stream.view?id=s1",
            StableCacheKey.keyFor(defaultHttpsUrl, "p2"),
        )

        val defaultHttpUrl = "http://nas.local:80/rest/stream.view?id=s1&t=abc"
        assertEquals(
            "p2:http://nas.local/rest/stream.view?id=s1",
            StableCacheKey.keyFor(defaultHttpUrl, "p2"),
        )
    }

    @Test
    fun stableCacheKey_matchesParamsCaseInsensitivelyAndPreservesCasing() {
        val jellyfinUrl =
            "https://jf.example.com:8920/Audio/item1/stream?Static=true&MaxStreamingBitRate=320000&AudioCodec=aac&Container=mp4&MediaSourceId=ms1&api_key=secret"
        assertEquals(
            "jf1:https://jf.example.com:8920/Audio/item1/stream?AudioCodec=aac&Container=mp4&MaxStreamingBitRate=320000&MediaSourceId=ms1&Static=true",
            StableCacheKey.keyFor(jellyfinUrl, "jf1"),
        )
    }

    // ---------- CachedTrackStore ----------

    @Test
    fun cachedTrackStore_cachedQualityHandlesSubsonicJellyfinAndRaw() = runTest {
        val dao = FakeCachedTrackDao()
        val bytes = FakeCacheBytes(alwaysCached = true)
        val store = CachedTrackStore(dao, bytes)

        store.recordCached(
            sampleTrack("p1:s1"),
            "https://h/rest/stream.view?id=s1&maxbitrate=320&format=opus",
            sizeBytes = 1024L,
        )
        store.recordCached(
            sampleTrack("jf1:s2"),
            "jf1:https://jf:8096/Audio/s2/stream?MaxStreamingBitRate=192000&Container=mp3",
            sizeBytes = 2048L,
        )
        store.recordCached(
            sampleTrack("jf1:s3"),
            "jf1:https://jf:8096/Audio/s3/stream?AudioBitRate=256000&AudioCodec=aac",
            sizeBytes = 2048L,
        )
        store.recordCached(
            sampleTrack("gd1:s4"),
            "gd1:https://www.googleapis.com/drive/v3/files/s4",
            sizeBytes = 4096L,
        )

        assertEquals(320 to "opus", store.cachedQuality("p1:s1"))
        assertEquals(192 to "mp3", store.cachedQuality("jf1:s2"))
        assertEquals(256 to "aac", store.cachedQuality("jf1:s3"))
        assertEquals(0 to "raw", store.cachedQuality("gd1:s4"))
        assertEquals("p1", dao.byId("p1:s1")?.profileId)
        assertEquals("jf1", dao.byId("jf1:s2")?.profileId)
    }

    @Test
    fun cachedTrackStore_clearForProfileEvictsOnlyTargetProfile() = runTest {
        val dao = FakeCachedTrackDao()
        val bytes = FakeCacheBytes(alwaysCached = true)
        val store = CachedTrackStore(dao, bytes)

        store.recordCached(sampleTrack("p1:a"), "key_p1_a", 100L)
        store.recordCached(sampleTrack("p2:b"), "key_p2_b", 200L)
        store.recordCached(sampleTrack("p2:c"), "key_p2_c", 300L)

        store.clearForProfile("p2")

        assertEquals(setOf("key_p2_b", "key_p2_c"), bytes.removedKeys)
        assertNotNull(dao.byId("p1:a"))
        assertNull(dao.byId("p2:b"))
        assertNull(dao.byId("p2:c"))
    }

    // ---------- CachePolicy, LibraryRefresh & MetadataCacheKeys ----------

    @Test
    fun cachePolicy_forceLogicalInvalidatesEachProfileOnce() {
        var now = 1_000L
        val policy = CachePolicy(clock = { now })
        val refresh = LibraryRefresh()

        policy.forceLogical(MetadataCacheKeys.ARTISTS)
        assertTrue(policy.consumeForced("p1:artists", MetadataCacheKeys.ARTISTS))
        assertFalse(policy.consumeForced("p1:artists", MetadataCacheKeys.ARTISTS))
        assertTrue(policy.consumeForced("p2:artists", MetadataCacheKeys.ARTISTS))
        assertFalse(policy.consumeForced("p2:artists", MetadataCacheKeys.ARTISTS))

        // Direct scoped force stays isolated to that profile.
        policy.force("p1:genres")
        assertTrue(policy.consumeForced("p1:genres", MetadataCacheKeys.GENRES))
        assertFalse(policy.consumeForced("p2:genres", MetadataCacheKeys.GENRES))

        // LibraryRefresh.invalidate triggers both exact and logical consumers.
        refresh.invalidate(MetadataCacheKeys.STARRED)
        assertTrue(refresh.policy.consumeForced("p1:starred", MetadataCacheKeys.STARRED))
        assertTrue(refresh.policy.consumeForced("p2:starred", MetadataCacheKeys.STARRED))
        assertFalse(refresh.policy.consumeForced("p1:starred", MetadataCacheKeys.STARRED))

        // forceAll covers all profiles once without double-triggering pending logical force.
        policy.forceLogical(MetadataCacheKeys.PLAYLISTS)
        policy.forceAll(durationMs = 4_000L)
        assertTrue(policy.consumeForced("p1:playlists", MetadataCacheKeys.PLAYLISTS))
        assertFalse(policy.consumeForced("p1:playlists", MetadataCacheKeys.PLAYLISTS))
        now += 100L
        assertTrue(policy.consumeForced("p2:playlists", MetadataCacheKeys.PLAYLISTS))
    }

    @Test
    fun metadataCacheKeys_scopedPrefixesLogicalKeys() {
        assertEquals("p2:artists", MetadataCacheKeys.scoped("p2", MetadataCacheKeys.ARTISTS))
        assertEquals("p1:album:p1:al-1", MetadataCacheKeys.scoped("p1", MetadataCacheKeys.album("p1:al-1")))
        assertEquals("cover-key", CoverCacheKey("cover-key").key)
    }

    // ---------- DiskHomeCache ----------

    @Test
    fun diskHomeCache_partitionsSnapshotsByNormalizedSourceFilter() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val homeDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "home_${UUID.randomUUID()}.preferences_pb") },
        )
        val cache = DiskHomeCache(homeDataStore, settingsStore)

        val allUi = HomeUi(
            selectedGenre = "All",
            recent = listOf(sampleAlbum("p1:al1", "All Album")),
        )
        val p2Ui = HomeUi(
            selectedGenre = "All",
            recent = listOf(sampleAlbum("p2:al2", "P2 Album")),
        )

        settingsStore.setLibrarySourceFilter("ALL")
        cache.saveDisk("All", allUi)

        settingsStore.setLibrarySourceFilter("PROFILE:p2")
        assertNull(cache.get("All"))
        assertNull(cache.loadDisk("All"))
        cache.saveDisk("All", p2Ui)

        // DEVICE normalizes to ALL and shares home_snapshot_v2_ALL.
        settingsStore.setLibrarySourceFilter("DEVICE")
        assertEquals("All Album", cache.loadDisk("All")?.recent?.single()?.name)

        val prefs = homeDataStore.data.first()
        assertTrue(prefs.contains(stringPreferencesKey("home_snapshot_v2_ALL")))
        assertTrue(prefs.contains(stringPreferencesKey("home_snapshot_v2_PROFILE:p2")))
        assertFalse(prefs.contains(stringPreferencesKey("home_snapshot_v2_DEVICE")))
    }

    // ---------- DownloadRepository ----------

    @Test
    fun downloadRepository_sanitizeIdAndProfileDownloadsDir() {
        assertEquals("p2_song_1_2_x", DownloadRepository.sanitizeId("p2:song/1:2?x"))
        assertEquals("p1", DownloadRepository.sanitizeId("p1"))

        val p2Dir = DownloadRepository.downloadsDir(tmp.root, "p2")
        assertTrue(p2Dir.exists() && p2Dir.isDirectory)
        assertEquals(File(tmp.root, "downloads/p2"), p2Dir)

        val specialDir = DownloadRepository.downloadsDir(tmp.root, "gd:acct/1")
        assertTrue(specialDir.exists() && specialDir.isDirectory)
        assertEquals(File(tmp.root, "downloads/gd_acct_1"), specialDir)
    }

    // ---------- Helpers ----------

    private fun sampleTrack(id: String) = Track(
        id = id,
        title = "Title",
        album = "Album",
        albumId = "al1",
        artist = "Artist",
        artistId = "ar1",
        coverArt = null,
        durationSec = 180,
        trackNo = 1,
        year = 2024,
        genre = "Rock",
        suffix = "flac",
        starred = false,
    )

    private fun sampleAlbum(id: String, name: String) = Album(
        id = id,
        name = name,
        artist = "Artist",
        artistId = "ar1",
        coverArt = null,
        songCount = 1,
        durationSec = 180,
        playCount = 0L,
        year = 2024,
        genre = "Rock",
        starred = false,
    )

    private class FakeCacheBytes(private val alwaysCached: Boolean) : CacheBytes {
        val removedKeys = mutableSetOf<String>()
        override fun isCached(cacheKey: String, sizeBytes: Long): Boolean = alwaysCached
        override fun removeResource(cacheKey: String) {
            removedKeys += cacheKey
        }
    }

    private class FakeCachedTrackDao : CachedTrackDao {
        private val rows = linkedMapOf<String, CachedTrackEntity>()
        private val flow = MutableStateFlow<List<CachedTrackEntity>>(emptyList())

        private fun sync() {
            flow.value = rows.values.toList()
        }

        override fun observeAll(): Flow<List<CachedTrackEntity>> = flow
        override suspend fun all(): List<CachedTrackEntity> = rows.values.toList()
        override suspend fun byProfile(profileId: String): List<CachedTrackEntity> =
            rows.values.filter { it.profileId == profileId }
        override suspend fun byId(id: String): CachedTrackEntity? = rows[id]
        override suspend fun upsert(track: CachedTrackEntity) {
            rows[track.songId] = track
            sync()
        }
        override suspend fun touchPlayed(id: String, now: Long) {
            rows[id]?.let { rows[id] = it.copy(lastPlayedAt = now) }
            sync()
        }
        override suspend fun delete(id: String) {
            rows.remove(id)
            sync()
        }
        override suspend fun deleteByProfile(profileId: String) {
            rows.entries.removeAll { it.value.profileId == profileId }
            sync()
        }
        override suspend fun clear() {
            rows.clear()
            sync()
        }
        override suspend fun count(): Int = rows.size
    }
}
