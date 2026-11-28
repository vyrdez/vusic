package com.feishin.mobile.data.playlist

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.DriveAlbumRow
import com.feishin.mobile.data.db.DriveArtistRow
import com.feishin.mobile.data.db.DriveFileModTime
import com.feishin.mobile.data.db.DriveGenreRow
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.db.LocalPlaylistDao
import com.feishin.mobile.data.db.LocalPlaylistEntity
import com.feishin.mobile.data.db.LocalPlaylistTrackEntity
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class M3uParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `parses UTF-8 BOM, CRLF, backslashes, EXTINF artist-title, and file URL prefixes`() {
        val m3u = buildString {
            append('\uFEFF')
            append("#EXTM3U\r\n")
            append("\r\n")
            append("#EXT-X-VERSION:3\r\n")
            append("#EXTINF:215,The Midnight - Night Drive\r\n")
            append("#EXTVLCOPT:network-caching=1000\r\n")
            append("Music\\The Midnight\\Endless Summer\\03 - Night Drive.mp3\r\n")
            append("#EXTINF:-1,Just Title Only\r\n")
            append("file:///C:/Users/Alice/Music/Artist/Track%2002%20%2B%20Bonus.flac\r\n")
            append("#EXTINF:180.6,Artist - Song - Remix\r\n")
            append("https://media.example.com/library/Artist/Song%20-%20Remix.ogg?token=abc#frag\r\n")
            append("file://localhost/Local/Folder/Track%20%231.mp3\r\n")
            append("solo.wav\r\n")
        }

        val entries = M3uParser.parse(ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8)))

        assertEquals(5, entries.size)

        assertEquals("Music/The Midnight/Endless Summer/03 - Night Drive.mp3", entries[0].normalizedPath)
        assertEquals("Music/The Midnight/Endless Summer", entries[0].parentFolderPath)
        assertEquals("03 - Night Drive.mp3", entries[0].fileName)
        assertEquals(215, entries[0].durationSec)
        assertEquals("The Midnight", entries[0].artist)
        assertEquals("Night Drive", entries[0].title)

        assertEquals("Users/Alice/Music/Artist/Track 02 + Bonus.flac", entries[1].normalizedPath)
        assertEquals("Users/Alice/Music/Artist", entries[1].parentFolderPath)
        assertEquals("Track 02 + Bonus.flac", entries[1].fileName)
        assertNull(entries[1].durationSec)
        assertNull(entries[1].artist)
        assertEquals("Just Title Only", entries[1].title)

        assertEquals("library/Artist/Song - Remix.ogg", entries[2].normalizedPath)
        assertEquals("library/Artist", entries[2].parentFolderPath)
        assertEquals("Song - Remix.ogg", entries[2].fileName)
        assertEquals(181, entries[2].durationSec)
        assertEquals("Artist", entries[2].artist)
        assertEquals("Song - Remix", entries[2].title)

        assertEquals("Local/Folder/Track #1.mp3", entries[3].normalizedPath)
        assertEquals("Track #1.mp3", entries[3].fileName)
        assertNull(entries[3].title)

        assertEquals("solo.wav", entries[4].normalizedPath)
        assertEquals("", entries[4].parentFolderPath)
        assertEquals("solo.wav", entries[4].fileName)
    }

    @Test
    fun `enforces 5 MB byte cap on input stream`() {
        val exactLimit = ByteArray(MAX_M3U_BYTES.toInt()) { ' '.code.toByte() }
        assertEquals(emptyList<M3uEntry>(), M3uParser.parse(ByteArrayInputStream(exactLimit)))

        val overLimit = ByteArray(MAX_M3U_BYTES.toInt() + 1) { 'a'.code.toByte() }
        try {
            M3uParser.parse(ByteArrayInputStream(overLimit))
            fail("Expected M3uFileTooLargeException when stream exceeds 5 MB")
        } catch (expected: M3uFileTooLargeException) {
            assertEquals(MAX_M3U_BYTES, expected.maxBytes)
        }
    }

    @Test
    fun `importer matches deterministically across 3 tiers and creates distinct editable playlists on re-import`() =
        runTest {
            val driveDao = InMemoryDriveTrackDao(
                listOf(
                    driveTrack(
                        profileId = "gd1",
                        fileId = "f-rock",
                        fileName = "01 - Intro.mp3",
                        parentFolderPath = "Rock/AlbumA",
                        title = "Intro",
                        artist = "Rock Band",
                    ),
                    driveTrack(
                        profileId = "gd1",
                        fileId = "f-jazz",
                        fileName = "01 - Intro.mp3",
                        parentFolderPath = "Jazz/AlbumB",
                        title = "Intro",
                        artist = "Jazz Trio",
                    ),
                    driveTrack(
                        profileId = "gd1",
                        fileId = "f-exact-file",
                        fileName = "Midnight City.FLAC",
                        parentFolderPath = "Synth/HurryUp",
                        title = "Midnight City",
                        artist = "M83",
                    ),
                    driveTrack(
                        profileId = "gd1",
                        fileId = "f-ext-strip",
                        fileName = "Starlight.flac",
                        parentFolderPath = "Muse/BlackHoles",
                        title = "Starlight",
                        artist = "Muse",
                    ),
                ),
            )
            val remoteCandidate = Track(
                id = "nd1:song-9",
                title = "Horizon",
                album = "Dawn",
                albumId = "nd1:al-9",
                artist = "Tycho",
                artistId = "nd1:ar-9",
                coverArt = "nd1:al-9",
                durationSec = 240,
                trackNo = 1,
                year = 2016,
                genre = "Electronic",
                suffix = "mp3",
                starred = false,
                path = "",
                sourceProfileId = "nd1",
                sourceLabel = "Navidrome",
            )
            val playlistDao = InMemoryLocalPlaylistDao()
            val profileStore = newProfileStore(
                backgroundScope,
                listOf(
                    driveProfile("gd1", priority = 0),
                    navidromeProfile("nd1", priority = 1),
                ),
            )
            val repo = LocalPlaylistRepository(playlistDao, profileStore)
            val importer = M3uImporter(driveDao, repo, profileStore)

            val m3u = """
                #EXTM3U
                # Tier 1: normalized parentFolderPath/fileName picks Jazz over Rock despite identical fileName
                D:\Exports\Jazz\AlbumB\01 - Intro.mp3
                # Tier 2: exact case-insensitive fileName when folder differs
                OtherFolder\midnight city.flac
                # Tier 3a: normalized #EXTINF artist + title against remote candidate without path
                #EXTINF:240,  tycho  -  HORIZON
                missing/folder/track09.aac
                # Tier 3b: strip-extension fallback (wav -> flac)
                Anywhere/Starlight.wav
                # Unmatched line
                Unknown/DoesNotExist.mp3
            """.trimIndent()

            val firstResult = importer.import(
                inputStream = ByteArrayInputStream(m3u.toByteArray()),
                playlistName = "Imported Mix",
                sourceM3uName = "mix.m3u8",
                candidateTracks = listOf(remoteCandidate),
            )

            assertEquals(4, firstResult.importedCount)
            assertEquals(1, firstResult.unmatchedCount)
            assertTrue(firstResult.playlistId.startsWith("local_pl:"))
            assertEquals(listOf("Unknown/DoesNotExist.mp3"), firstResult.unmatchedLines)

            val loaded = repo.playlist(firstResult.playlistId)!!
            assertEquals(
                listOf("gd1:f-jazz", "gd1:f-exact-file", "nd1:song-9", "gd1:f-ext-strip"),
                loaded.second.map { it.id },
            )
            assertEquals("mix.m3u8", playlistDao.playlistById(firstResult.playlistId)?.sourceM3uName)

            // User edits first playlist: rename and remove track at index 1
            repo.renamePlaylist(firstResult.playlistId, "Edited Mix")
            repo.removeTracks(firstResult.playlistId, positions = listOf(1))

            // Re-importing creates a distinct editable local_pl:* playlist without overwriting edits
            val secondResult = importer.import(
                inputStream = ByteArrayInputStream(m3u.toByteArray()),
                playlistName = "Imported Mix",
                sourceM3uName = "mix.m3u8",
                candidateTracks = listOf(remoteCandidate),
            )

            assertNotEquals(firstResult.playlistId, secondResult.playlistId)
            val editedFirst = repo.playlist(firstResult.playlistId)!!
            assertEquals("Edited Mix", editedFirst.first.name)
            assertEquals(3, editedFirst.second.size)

            val freshSecond = repo.playlist(secondResult.playlistId)!!
            assertEquals("Imported Mix", freshSecond.first.name)
            assertEquals(4, freshSecond.second.size)
        }

    private fun driveTrack(
        profileId: String,
        fileId: String,
        fileName: String,
        parentFolderPath: String,
        title: String,
        artist: String,
    ) = DriveTrackEntity(
        songId = "$profileId:$fileId",
        profileId = profileId,
        driveFileId = fileId,
        fileName = fileName,
        parentFolderId = "fld",
        parentFolderPath = parentFolderPath,
        title = title,
        artist = artist,
        album = "Album",
        albumKey = "$profileId:al:1",
        artistKey = "$profileId:ar:1",
        durationSec = 200,
    )

    private fun driveProfile(id: String, priority: Int) = ServerProfile(
        id = id,
        name = "Drive $id",
        type = BackendType.GOOGLE_DRIVE,
        url = "",
        credentials = ProfileCredentials.GoogleDrive(accountEmail = "user@gmail.com", accessToken = "tok"),
        enabled = true,
        priority = priority,
    )

    private fun navidromeProfile(id: String, priority: Int) = ServerProfile(
        id = id,
        name = "Navidrome $id",
        type = BackendType.NAVIDROME,
        url = "https://nav.example.com",
        credentials = ProfileCredentials.Subsonic(username = "alice", password = "pw"),
        enabled = true,
        priority = priority,
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

    private class InMemoryDriveTrackDao(
        private val items: List<DriveTrackEntity>,
    ) : DriveTrackDao {
        override suspend fun allIdsAndModTimes(profileId: String): List<DriveFileModTime> = emptyList()
        override suspend fun upsertAll(tracks: List<DriveTrackEntity>) = Unit
        override suspend fun deleteByIds(songIds: List<String>) = Unit
        override suspend fun clearProfile(profileId: String) = Unit
        override suspend fun countForProfile(profileId: String): Int = items.count { it.profileId == profileId }
        override suspend fun tracksForProfile(profileId: String): List<DriveTrackEntity> =
            items.filter { it.profileId == profileId }
        override suspend fun trackById(songId: String): DriveTrackEntity? = items.firstOrNull { it.songId == songId }
        override suspend fun embeddedLyrics(songId: String): String? = null
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
