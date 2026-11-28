package com.feishin.mobile.data.playlist

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.LocalPlaylistDao
import com.feishin.mobile.data.db.LocalPlaylistEntity
import com.feishin.mobile.data.db.LocalPlaylistTrackEntity
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalPlaylistRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `mixed Navidrome, Jellyfin, Drive, and Device local tracks in one playlist with 4-cover collage and source map`() =
        runTest {
            val dao = InMemoryLocalPlaylistDao()
            val profileStore = newProfileStore(
                backgroundScope,
                listOf(
                    navidromeProfile("nd1"),
                    jellyfinProfile("jf1"),
                    driveProfile("gd1"),
                ),
            )
            val repo = LocalPlaylistRepository(dao, profileStore)

            val navTrack = track(id = "nd1:s1", title = "Nav Song", coverArt = "nd1:al1", durationSec = 180)
            val jfTrack = track(id = "jf1:j1", title = "JF Song", coverArt = "jf1:item1", durationSec = 200)
            val driveTrack = track(id = "gd1:d1", title = "Drive Song", coverArt = "drive-cover://gd1/d1", durationSec = 220)
            val deviceTrack = track(
                id = "local:42",
                title = "Device Song",
                coverArt = "content://media/external/audio/media/42",
                durationSec = 100,
                localUri = "content://media/external/audio/media/42",
            )
            val dupCoverTrack = track(id = "nd1:s2", title = "Dup Cover", coverArt = "nd1:al1", durationSec = 150)
            val fifthCoverTrack = track(id = "jf1:j2", title = "Fifth Cover", coverArt = "jf1:item2", durationSec = 90)

            val created = repo.createPlaylist(
                name = "Cross Server Mix",
                tracks = listOf(navTrack, jfTrack, driveTrack, deviceTrack, dupCoverTrack, fifthCoverTrack),
            )

            assertTrue(created.id.startsWith("local_pl:"))
            assertEquals("Cross Server Mix", created.name)
            assertEquals(6, created.songCount)
            assertEquals(940, created.durationSec)

            // Extracts first 4 distinct coverArt entries across remote, drive-cover://, and local: URIs
            val expectedCovers = listOf(
                "nd1:al1",
                "jf1:item1",
                "drive-cover://gd1/d1",
                "content://media/external/audio/media/42",
            )
            assertEquals(PlaylistCover.Collage(expectedCovers), repo.resolvePlaylistCover(created))
            assertEquals(PlaylistCover.Collage(expectedCovers), repo.playlistCover(created.id))
            assertEquals(mapOf(created.id to PlaylistCover.Collage(expectedCovers)), repo.allPlaylistCovers())

            // Source profile IDs exclude local: device tracks and deduplicate remote profile IDs
            assertEquals(setOf("nd1", "jf1", "gd1"), repo.playlistSourceProfileIds(created.id))
            assertEquals(mapOf(created.id to setOf("nd1", "jf1", "gd1")), repo.allPlaylistSourceMap())
        }

    @Test
    fun `add, duplicate-safe remove by entryId or position, rename, search, and delete`() = runTest {
        val dao = InMemoryLocalPlaylistDao()
        val profileStore = newProfileStore(backgroundScope, listOf(navidromeProfile("nd1")))
        val repo = LocalPlaylistRepository(dao, profileStore)

        val t1 = track(id = "nd1:1", title = "One", coverArt = "nd1:c1", durationSec = 100)
        val t2 = track(id = "nd1:2", title = "Two", coverArt = null, durationSec = 200)
        val t1Dup = track(id = "nd1:1", title = "One", coverArt = "nd1:c1", durationSec = 100)

        val created = repo.createPlaylist("Roadtrip", listOf(t1))
        repo.addTracks(created.id, listOf(t2, t1Dup))

        val afterAdd = repo.playlist(created.id)!!
        assertEquals(3, afterAdd.first.songCount)
        assertEquals(listOf("nd1:1", "nd1:2", "nd1:1"), afterAdd.second.map { it.id })

        // Remove only the duplicate at position 2 via entryId
        val entries = repo.playlistEntries(created.id)
        assertEquals(listOf(0, 1, 2), entries.map { it.position })
        repo.removeTracks(created.id, entryIds = listOf(entries[2].entryId))

        val afterDupRemove = repo.playlist(created.id)!!
        assertEquals(listOf("nd1:1", "nd1:2"), afterDupRemove.second.map { it.id })

        // Remove position 0 and verify remaining track re-indexes to position 0
        repo.removeTracks(created.id, positions = listOf(0))
        val afterPosRemove = repo.playlistEntries(created.id)
        assertEquals(1, afterPosRemove.size)
        assertEquals("nd1:2", afterPosRemove[0].track.id)
        assertEquals(0, afterPosRemove[0].position)

        // Rename & search
        repo.renamePlaylist(created.id, "Weekend Drive")
        val searchHits = repo.search("weekend")
        assertEquals(1, searchHits.size)
        assertEquals("Weekend Drive", searchHits[0].name)

        // Delete
        repo.deletePlaylist(created.id)
        assertNull(repo.playlist(created.id))
        assertTrue(repo.allPlaylists().isEmpty())
    }

    @Test
    fun `removed or disabled profile tracks stay preserved without crashing and report availability accurately`() =
        runTest {
            val dao = InMemoryLocalPlaylistDao()
            val profileStore = newProfileStore(
                backgroundScope,
                listOf(
                    navidromeProfile("nd1", enabled = true),
                    jellyfinProfile("jf1", enabled = true),
                ),
            )
            val repo = LocalPlaylistRepository(dao, profileStore)

            val navTrack = track(id = "nd1:s1", title = "Active Nav")
            val jfTrack = track(id = "jf1:j1", title = "Removed JF")
            val deviceTrack = track(
                id = "local:9",
                title = "Device Track",
                localUri = "content://media/external/audio/media/9",
            )
            val created = repo.createPlaylist("Resilient Mix", listOf(navTrack, jfTrack, deviceTrack))

            // Disable nd1 and remove jf1 completely
            profileStore.setEnabled("nd1", enabled = false)
            profileStore.remove("jf1")

            val loaded = repo.playlist(created.id)!!
            assertEquals(3, loaded.second.size)

            val entries = repo.playlistEntries(created.id)
            assertEquals(3, entries.size)
            assertFalse(entries[0].isSourceAvailable) // disabled nd1
            assertFalse(entries[1].isSourceAvailable) // removed jf1
            assertTrue(entries[2].isSourceAvailable) // local: device track ignores ProfileStore
        }

    @Test
    fun `cross-server download helper partitions remote downloadable tracks by profileId`() = runTest {
        val dao = InMemoryLocalPlaylistDao()
        val profileStore = newProfileStore(
            backgroundScope,
            listOf(
                navidromeProfile("nd1", enabled = true),
                jellyfinProfile("jf1", enabled = true),
                driveProfile("gd1", enabled = true),
                navidromeProfile("nd_off", enabled = false),
            ),
        )
        val repo = LocalPlaylistRepository(dao, profileStore)

        val navTrack = track(id = "nd1:s1", title = "Nav")
        val jfTrack = track(id = "jf1:j1", title = "JF")
        val driveTrack = track(id = "gd1:d1", title = "Drive")
        val disabledTrack = track(id = "nd_off:s2", title = "Disabled")
        val deviceTrack = track(
            id = "local:10",
            title = "Device",
            localUri = "content://media/external/audio/media/10",
        )
        val downloadedRemote = track(
            id = "nd1:s3",
            title = "Already Local File",
            localUri = "file:///cache/s3.mp3",
        )

        val allTracks = listOf(navTrack, jfTrack, driveTrack, disabledTrack, deviceTrack, downloadedRemote)

        val partitioned = repo.partitionDownloadableByProfile(allTracks)
        assertEquals(setOf("nd1", "jf1", "gd1"), partitioned.keys)
        assertEquals(listOf(navTrack), partitioned["nd1"])
        assertEquals(listOf(jfTrack), partitioned["jf1"])
        assertEquals(listOf(driveTrack), partitioned["gd1"])

        val fullPartition = repo.partitionTracksForDownload(allTracks)
        assertEquals(listOf(deviceTrack, downloadedRemote), fullPartition.localTracks)
        assertEquals(listOf(disabledTrack), fullPartition.unavailableTracks)

        // Verify playlist-level lookup works identically
        val pl = repo.createPlaylist("Downloadable Mix", allTracks)
        assertNotNull(repo.playlist(pl.id))
        assertEquals(partitioned, repo.downloadableTracksByProfile(pl.id))
    }

    @Test
    fun `PlaylistCover select decodes local_collage markers into 2x2 collage`() = runTest {
        val dao = InMemoryLocalPlaylistDao()
        val profileStore = newProfileStore(backgroundScope, listOf(navidromeProfile("nd1")))
        val repo = LocalPlaylistRepository(dao, profileStore)

        val tracks = listOf(
            track("nd1:1", "One", coverArt = "nd1:al1"),
            track("gd1:2", "Two", coverArt = "drive-cover://gd1/f2"),
            track("local:3", "Three", coverArt = "content://media/external/audio/media/3", localUri = "content://media/external/audio/media/3"),
            track("jf1:4", "Four", coverArt = "jf1:item4"),
        )
        val marker = repo.encodeCoverArtMarker(repo.extractCoverArtIds(tracks))
        val selected = PlaylistCover.select(marker, emptyList())
        assertEquals(
            PlaylistCover.Collage(
                listOf("nd1:al1", "drive-cover://gd1/f2", "content://media/external/audio/media/3", "jf1:item4"),
            ),
            selected,
        )
    }

    private fun track(
        id: String,
        title: String,
        coverArt: String? = null,
        durationSec: Int = 120,
        localUri: String? = null,
    ) = Track(
        id = id,
        title = title,
        album = "Album",
        albumId = null,
        artist = "Artist",
        artistId = null,
        coverArt = coverArt,
        durationSec = durationSec,
        trackNo = 1,
        year = 2024,
        genre = "Pop",
        suffix = "mp3",
        starred = false,
        localUri = localUri,
    )

    private fun navidromeProfile(id: String, enabled: Boolean = true) = ServerProfile(
        id = id,
        name = "Nav $id",
        type = BackendType.NAVIDROME,
        url = "https://nav.example.com",
        credentials = ProfileCredentials.Subsonic(username = "alice", password = "pw"),
        enabled = enabled,
        priority = 0,
    )

    private fun jellyfinProfile(id: String, enabled: Boolean = true) = ServerProfile(
        id = id,
        name = "JF $id",
        type = BackendType.JELLYFIN,
        url = "https://jf.example.com",
        credentials = ProfileCredentials.Jellyfin(
            username = "carol",
            accessToken = "tok",
            serverId = "srv-$id",
            deviceId = "dev-1",
        ),
        enabled = enabled,
        priority = 1,
    )

    private fun driveProfile(id: String, enabled: Boolean = true) = ServerProfile(
        id = id,
        name = "Drive $id",
        type = BackendType.GOOGLE_DRIVE,
        url = "",
        credentials = ProfileCredentials.GoogleDrive(accountEmail = "user@gmail.com", accessToken = "tok"),
        enabled = enabled,
        priority = 2,
    )

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun newProfileStore(scope: CoroutineScope, profiles: List<ServerProfile>): ProfileStore {
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
        return store
    }

    private class InMemoryLocalPlaylistDao : LocalPlaylistDao {
        private val playlists = linkedMapOf<String, LocalPlaylistEntity>()
        private val tracks = mutableListOf<LocalPlaylistTrackEntity>()
        private var nextEntryId = 1L
        private val playlistsFlow = MutableStateFlow<List<LocalPlaylistEntity>>(emptyList())
        private val tracksFlow = MutableStateFlow<List<LocalPlaylistTrackEntity>>(emptyList())

        private fun emitFlows() {
            playlistsFlow.value = playlists.values.sortedWith(compareByDescending<LocalPlaylistEntity> { it.updatedAtMs }.thenBy { it.name.lowercase() })
            tracksFlow.value = tracks.toList()
        }

        override fun observePlaylists(): Flow<List<LocalPlaylistEntity>> = playlistsFlow
        override suspend fun allPlaylists(): List<LocalPlaylistEntity> =
            playlists.values.sortedWith(compareByDescending<LocalPlaylistEntity> { it.updatedAtMs }.thenBy { it.name.lowercase() })
        override suspend fun playlistById(playlistId: String): LocalPlaylistEntity? = playlists[playlistId]
        override suspend fun searchPlaylists(query: String): List<LocalPlaylistEntity> =
            allPlaylists().filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun upsertPlaylist(playlist: LocalPlaylistEntity) {
            playlists[playlist.playlistId] = playlist
            emitFlows()
        }
        override suspend fun renamePlaylist(playlistId: String, name: String, updatedAtMs: Long) {
            playlists[playlistId]?.let {
                playlists[playlistId] = it.copy(name = name, updatedAtMs = updatedAtMs)
                emitFlows()
            }
        }
        override suspend fun touchPlaylist(playlistId: String, updatedAtMs: Long) {
            playlists[playlistId]?.let {
                playlists[playlistId] = it.copy(updatedAtMs = updatedAtMs)
                emitFlows()
            }
        }
        override suspend fun deletePlaylist(playlistId: String) {
            playlists.remove(playlistId)
            tracks.removeAll { it.playlistId == playlistId }
            emitFlows()
        }
        override suspend fun tracksForPlaylist(playlistId: String): List<LocalPlaylistTrackEntity> =
            tracks.filter { it.playlistId == playlistId }.sortedWith(compareBy({ it.position }, { it.entryId }))
        override fun observeTracksForPlaylist(playlistId: String): Flow<List<LocalPlaylistTrackEntity>> =
            tracksFlow.map { list -> list.filter { it.playlistId == playlistId }.sortedWith(compareBy({ it.position }, { it.entryId })) }
        override suspend fun allTracks(): List<LocalPlaylistTrackEntity> =
            tracks.sortedWith(compareBy({ it.playlistId }, { it.position }, { it.entryId }))
        override suspend fun maxPosition(playlistId: String): Int =
            tracks.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: -1
        override suspend fun trackCount(playlistId: String): Int =
            tracks.count { it.playlistId == playlistId }
        override suspend fun insertTracks(tracks: List<LocalPlaylistTrackEntity>): List<Long> {
            val insertedIds = mutableListOf<Long>()
            for (item in tracks) {
                val id = if (item.entryId > 0L) item.entryId else nextEntryId++
                this.tracks.add(item.copy(entryId = id))
                insertedIds.add(id)
            }
            emitFlows()
            return insertedIds
        }
        override suspend fun deleteEntries(entryIds: List<Long>) {
            val idSet = entryIds.toSet()
            tracks.removeAll { it.entryId in idSet }
            emitFlows()
        }
        override suspend fun deleteByPositions(playlistId: String, positions: List<Int>) {
            val posSet = positions.toSet()
            tracks.removeAll { it.playlistId == playlistId && it.position in posSet }
            emitFlows()
        }
        override suspend fun clearPlaylistTracks(playlistId: String) {
            tracks.removeAll { it.playlistId == playlistId }
            emitFlows()
        }
        override suspend fun updatePosition(entryId: Long, position: Int) {
            val idx = tracks.indexOfFirst { it.entryId == entryId }
            if (idx >= 0) {
                tracks[idx] = tracks[idx].copy(position = position)
                emitFlows()
            }
        }
        override suspend fun playlistIdsContainingProfile(profileId: String): List<String> =
            tracks.filter { it.trackId.startsWith("$profileId:") }.map { it.playlistId }.distinct()
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?): String? =
            synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(data) { (data[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int): Int =
            synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long): Long =
            synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float): Float =
            synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?): Boolean = synchronized(data) { data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) updates[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) updates[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) updates[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) updates[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) updates[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                synchronized(data) {
                    if (clearAll) data.clear()
                    removals.forEach { data.remove(it) }
                    updates.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
