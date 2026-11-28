package com.feishin.mobile.data.repo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.DriveAlbumRow
import com.feishin.mobile.data.db.DriveArtistRow
import com.feishin.mobile.data.db.DriveFileModTime
import com.feishin.mobile.data.db.DriveGenreRow
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.db.LyricsCacheDao
import com.feishin.mobile.data.db.LyricsCacheEntity
import com.feishin.mobile.data.lyrics.DriveEmbeddedLyricsProvider
import com.feishin.mobile.data.lyrics.FileTagLyricsProvider
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveLyricsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun driveProfile(id: String = "p3"): ServerProfile = ServerProfile(
        id = id,
        name = "Google Drive",
        type = BackendType.GOOGLE_DRIVE,
        url = "",
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = "user@gmail.com",
            accessToken = "drive-token",
        ),
    )

    private fun jellyfinProfile(id: String = "p2"): ServerProfile = ServerProfile(
        id = id,
        name = "Jellyfin",
        type = BackendType.JELLYFIN,
        url = "https://jf.example.com",
        credentials = ProfileCredentials.Jellyfin(
            username = "alice",
            accessToken = "jf-token",
            serverId = "srv-1",
            deviceId = "dev-1",
        ),
    )

    private fun navidromeProfile(id: String = "p1"): ServerProfile = ServerProfile(
        id = id,
        name = "Navidrome",
        type = BackendType.NAVIDROME,
        url = "https://nd.example.com",
        credentials = ProfileCredentials.Subsonic(username = "bob", password = "pw"),
    )

    private fun track(id: String, title: String = "Song", isLocal: Boolean = false): Track = Track(
        id = id,
        title = title,
        album = "Album",
        albumId = null,
        artist = "Artist",
        artistId = null,
        coverArt = null,
        durationSec = 180,
        trackNo = null,
        year = null,
        genre = null,
        suffix = null,
        starred = false,
        localUri = if (isLocal) "content://media/external/audio/media/99" else null,
    )

    private fun newStores(
        scope: CoroutineScope,
        profiles: List<ServerProfile>,
    ): Pair<ProfileStore, SettingsStore> {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val store = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
        )
        store.saveAll(profiles)
        return store to settings
    }

    @Test
    fun driveEmbeddedLyricsProvider_decodesJsonOfflineAndHandlesAbsentOrInvalid() = runTest {
        val embedded = Lyrics(
            lines = listOf(LyricLine(1000L, "Embedded Drive line")),
            kind = LyricsKind.SYNCED,
            source = LyricsSource.FILE_TAGS,
        )
        val dao = FakeDriveTrackDao(
            mapOf(
                "p3:file-1" to json.encodeToString(Lyrics.serializer(), embedded),
                "raw-file-2" to json.encodeToString(Lyrics.serializer(), embedded.copy(lines = listOf(LyricLine(2000L, "Raw fallback")))),
                "p3:corrupt" to "{not valid json",
            ),
        )
        val provider = DriveEmbeddedLyricsProvider(dao, json)

        val exact = provider.lyricsForSong("p3:file-1", "Song", "Artist", null, 180_000L) as Result.Ok
        assertEquals(LyricsKind.SYNCED, exact.value.kind)
        assertEquals("Embedded Drive line", exact.value.lines.single().text)

        val fallback = provider.lyricsForSong("p3:raw-file-2", "Song", "Artist", null, 180_000L) as Result.Ok
        assertEquals(LyricsKind.SYNCED, fallback.value.kind)
        assertEquals("Raw fallback", fallback.value.lines.single().text)

        val corrupt = provider.lyricsForSong("p3:corrupt", "Song", "Artist", null, 180_000L) as Result.Ok
        assertEquals(LyricsKind.NONE, corrupt.value.kind)
        assertNull(provider.readEmbeddedLyrics("p3:corrupt"))

        val missing = provider.lyricsForSong("p3:missing", "Song", "Artist", null, 180_000L) as Result.Ok
        assertEquals(LyricsKind.NONE, missing.value.kind)
        assertNull(provider.readEmbeddedLyrics("p3:missing"))
    }

    @Test
    fun driveLyricsPriority_prefersLocalDataAndFallsThroughBetterLyricsLrclibKugou() = runTest {
        val (profileStore, settingsStore) = newStores(backgroundScope, listOf(driveProfile("p3")))
        settingsStore.setLyricsProviderOrder(listOf("LOCAL_DATA", "BETTER_LYRICS", "LRCLIB", "KUGOU"))
        settingsStore.setLyricsProviderEnabled(setOf("LOCAL_DATA", "BETTER_LYRICS", "LRCLIB", "KUGOU"))
        settingsStore.setLyricsFetchOnline(true)
        settingsStore.setOfflineMode(false)

        val embedded = Lyrics(
            lines = listOf(LyricLine(1500L, "Drive embedded wins")),
            kind = LyricsKind.SYNCED,
            source = LyricsSource.FILE_TAGS,
        )
        val dao = FakeDriveTrackDao(
            mapOf("p3:drive-hit" to json.encodeToString(Lyrics.serializer(), embedded)),
        )
        val callOrder = mutableListOf<String>()
        val cache = InMemoryLyricsCacheDao()

        val repo = LyricsRepository(
            cache = cache,
            lrclib = RecordingProvider("LRCLIB", callOrder, Lyrics(listOf(LyricLine(3000L, "LRCLIB hit")), kind = LyricsKind.SYNCED, source = LyricsSource.LRCLIB)),
            betterLyrics = RecordingProvider("BETTER_LYRICS", callOrder, Lyrics(kind = LyricsKind.NONE)),
            paxsenix = RecordingProvider("PAXSENIX", callOrder, Lyrics(kind = LyricsKind.NONE)),
            kugou = RecordingProvider("KUGOU", callOrder, Lyrics(listOf(LyricLine(4000L, "KuGou hit")), kind = LyricsKind.SYNCED, source = LyricsSource.KUGOU)),
            navidrome = RecordingProvider("NAVIDROME", callOrder, Lyrics(kind = LyricsKind.NONE)),
            jellyfin = RecordingProvider("JELLYFIN", callOrder, Lyrics(kind = LyricsKind.NONE)),
            driveLyricsProvider = DriveEmbeddedLyricsProvider(dao, json),
            fileTags = FileTagLyricsProvider(stubContext()),
            profileStore = profileStore,
            settingsStore = settingsStore,
            json = json,
            context = stubContext(),
        )

        val hit = repo.getLyrics(track("p3:drive-hit")) as Result.Ok
        assertEquals("Drive embedded wins", hit.value.lines.single().text)
        assertEquals(LyricsSource.FILE_TAGS, hit.value.source)
        assertTrue("Online providers must not be called when LOCAL_DATA hits", callOrder.isEmpty())

        val miss = repo.getLyrics(track("p3:drive-miss")) as Result.Ok
        assertEquals("LRCLIB hit", miss.value.lines.single().text)
        assertEquals(LyricsSource.LRCLIB, miss.value.source)
        assertEquals(listOf("BETTER_LYRICS", "LRCLIB"), callOrder)
    }

    @Test
    fun lyricsCacheEntity_persistsProfileIdForDriveJellyfinSubsonicAndCustom() = runTest {
        val profiles = listOf(navidromeProfile("p1"), jellyfinProfile("p2"), driveProfile("p3"))
        val (profileStore, settingsStore) = newStores(backgroundScope, profiles)
        settingsStore.setLyricsProviderOrder(listOf("LOCAL_DATA"))
        settingsStore.setLyricsProviderEnabled(setOf("LOCAL_DATA"))
        settingsStore.setLyricsFetchOnline(false)
        settingsStore.setOfflineMode(true)

        val driveLyrics = Lyrics(listOf(LyricLine(1000L, "Drive")), kind = LyricsKind.SYNCED, source = LyricsSource.FILE_TAGS)
        val jfLyrics = Lyrics(listOf(LyricLine(1000L, "Jellyfin")), kind = LyricsKind.SYNCED, source = LyricsSource.SERVER_METADATA)
        val ndLyrics = Lyrics(listOf(LyricLine(1000L, "Subsonic")), kind = LyricsKind.SYNCED, source = LyricsSource.SERVER_METADATA)

        val dao = FakeDriveTrackDao(mapOf("p3:d1" to json.encodeToString(Lyrics.serializer(), driveLyrics)))
        val cache = InMemoryLyricsCacheDao()

        val repo = LyricsRepository(
            cache = cache,
            lrclib = RecordingProvider("LRCLIB", mutableListOf(), Lyrics(kind = LyricsKind.NONE)),
            betterLyrics = RecordingProvider("BETTER_LYRICS", mutableListOf(), Lyrics(kind = LyricsKind.NONE)),
            paxsenix = RecordingProvider("PAXSENIX", mutableListOf(), Lyrics(kind = LyricsKind.NONE)),
            kugou = RecordingProvider("KUGOU", mutableListOf(), Lyrics(kind = LyricsKind.NONE)),
            navidrome = RecordingProvider("NAVIDROME", mutableListOf(), ndLyrics),
            jellyfin = RecordingProvider("JELLYFIN", mutableListOf(), jfLyrics),
            driveLyricsProvider = DriveEmbeddedLyricsProvider(dao, json),
            fileTags = FileTagLyricsProvider(stubContext()),
            profileStore = profileStore,
            settingsStore = settingsStore,
            json = json,
            context = stubContext(),
        )

        repo.getLyrics(track("p3:d1"))
        repo.getLyrics(track("p2:j1"))
        repo.getLyrics(track("p1:s1"))
        repo.getLyrics(track("p3:d-missing"))
        repo.saveCustomLyrics(track("p3:d-custom"), driveLyrics)
        val localTrack = track("local:99", isLocal = true)
        repo.saveCustomLyrics(localTrack, driveLyrics)

        assertEquals("p3", cache.getEntry("p3:d1")?.profileId)
        assertEquals("p2", cache.getEntry("p2:j1")?.profileId)
        assertEquals("p1", cache.getEntry("p1:s1")?.profileId)
        assertEquals("p3", cache.getEntry("p3:d-missing")?.profileId)
        assertEquals("p3", cache.getEntry("p3:d-custom")?.profileId)
        assertEquals("p1", cache.getEntry(repo.cacheKeyForTrack(localTrack))?.profileId)
    }

    private class RecordingProvider(
        private val name: String,
        private val calls: MutableList<String>,
        private val response: Lyrics,
    ) : LyricsProvider {
        override suspend fun lyricsForSong(
            songId: String,
            title: String,
            artist: String,
            album: String?,
            durationMs: Long,
            localUri: String?,
            localModifiedSec: Long,
        ): Result<Lyrics> {
            calls.add(name)
            return Result.Ok(response)
        }
    }

    private class FakeDriveTrackDao(
        private val lyricsBySongId: Map<String, String?>,
    ) : DriveTrackDao {
        override suspend fun embeddedLyrics(songId: String): String? = lyricsBySongId[songId]
        override suspend fun allIdsAndModTimes(profileId: String): List<DriveFileModTime> = emptyList()
        override suspend fun upsertAll(tracks: List<DriveTrackEntity>) = Unit
        override suspend fun deleteByIds(songIds: List<String>) = Unit
        override suspend fun clearProfile(profileId: String) = Unit
        override suspend fun countForProfile(profileId: String): Int = 0
        override suspend fun tracksForProfile(profileId: String): List<DriveTrackEntity> = emptyList()
        override suspend fun trackById(songId: String): DriveTrackEntity? = null
        override suspend fun albumsForProfile(profileId: String): List<DriveAlbumRow> = emptyList()
        override suspend fun albumsRecentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun albumsFrequentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun artistsForProfile(profileId: String): List<DriveArtistRow> = emptyList()
        override suspend fun genresForProfile(profileId: String): List<DriveGenreRow> = emptyList()
        override suspend fun tracksInAlbum(albumKey: String): List<DriveTrackEntity> = emptyList()
        override suspend fun tracksByArtist(artistKey: String): List<DriveTrackEntity> = emptyList()
        override suspend fun tracksByParentFolder(profileId: String, parentFolderId: String): List<DriveTrackEntity> = emptyList()
        override suspend fun allSongsForProfile(profileId: String, limit: Int, offset: Int): List<DriveTrackEntity> = emptyList()
        override suspend fun randomTracks(profileId: String, limit: Int, genre: String?): List<DriveTrackEntity> = emptyList()
        override suspend fun searchTracks(profileId: String, query: String, limit: Int): List<DriveTrackEntity> = emptyList()
        override suspend fun searchAlbums(profileId: String, query: String, limit: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun searchArtists(profileId: String, query: String, limit: Int): List<DriveArtistRow> = emptyList()
    }

    private class InMemoryLyricsCacheDao : LyricsCacheDao {
        private val entries = mutableMapOf<String, LyricsCacheEntity>()

        override suspend fun getEntry(id: String): LyricsCacheEntity? = entries[id]
        override suspend fun get(id: String): String? = entries[id]?.json
        override suspend fun put(entry: LyricsCacheEntity) {
            entries[entry.songId] = entry
        }
        override suspend fun remove(id: String) {
            entries.remove(id)
        }
        override suspend fun deleteByProfile(profileId: String) {
            entries.entries.removeAll { it.value.profileId == profileId }
        }
        override suspend fun clear() {
            entries.clear()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                pending.forEach { (k, v) -> if (v == null) target.remove(k) else target[k] = v }
            }
        }
    }
}
