package com.feishin.mobile.data.download

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.db.PendingActionEntity
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncStoreTest {

    private class FakePendingActionDao : PendingActionDao {
        private var nextId = 1L
        private val rows = mutableListOf<PendingActionEntity>()
        private val countFlow = MutableStateFlow(0)

        override suspend fun all(): List<PendingActionEntity> =
            rows.sortedWith(compareBy<PendingActionEntity> { it.timeMs }.thenBy { it.id })

        override suspend fun pendingForProfile(profileId: String): List<PendingActionEntity> =
            rows.filter { it.profileId == profileId }
                .sortedWith(compareBy<PendingActionEntity> { it.timeMs }.thenBy { it.id })

        override suspend fun distinctProfileIds(): List<String> =
            rows.map { it.profileId }.distinct().sorted()

        override suspend fun insert(action: PendingActionEntity): Long {
            val id = if (action.id > 0) action.id else nextId++
            rows += action.copy(id = id)
            countFlow.value = rows.size
            return id
        }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
            countFlow.value = rows.size
        }

        override suspend fun deleteByProfile(profileId: String) {
            rows.removeAll { it.profileId == profileId }
            countFlow.value = rows.size
        }

        override fun observeCount(): Flow<Int> = countFlow

        override suspend fun clear() {
            rows.clear()
            countFlow.value = 0
        }
    }

    private class StubMusicRepo(
        var failingProfiles: Set<String> = emptySet(),
    ) : MusicDataRepository {
        val replayedCalls = mutableListOf<String>()

        private fun handle(op: String, id: String): Result<Unit> {
            val profile = GlobalId.profileIdOrNull(id) ?: OfflineSyncStore.DEFAULT_PROFILE_ID
            replayedCalls += "$op:$id"
            return if (profile in failingProfiles) {
                Result.Err("Server $profile offline")
            } else {
                Result.Ok(Unit)
            }
        }

        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> =
            handle("scrobble", id)

        override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> =
            handle(if (starred) "star" else "unstar", id)

        override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> =
            handle(if (starred) "starAlbum" else "unstarAlbum", id)

        override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> =
            handle(if (starred) "starArtist" else "unstarArtist", id)

        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<Album>())
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) = Result.Ok(SearchResults(emptyList(), emptyList(), emptyList()))
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) =
            Result.Ok(SearchResults(emptyList(), emptyList(), emptyList()))
        override suspend fun serverInfo() = Result.Ok(ServerInfo())
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus())
        override suspend fun album(id: String) = Result.Err("unused")
        override suspend fun artist(id: String) = Result.Err("unused")
        override suspend fun artistInfo(id: String) = Result.Err("unused")
        override suspend fun randomSongs(size: Int, genre: String?) = Result.Ok(emptyList<Track>())
        override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun playlist(id: String) = Result.Err("unused")
        override suspend fun starred() = Result.Ok(StarredCollection())
        override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Err("unused")
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
        override suspend fun lyricsForTrack(id: String, artist: String, title: String) =
            Result.Ok(SyncedLyrics.EMPTY)
        override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
        override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
        override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
        override fun downloadUrl(track: Track) = ""
    }

    @Test
    fun flush_isolatesFailurePerProfile_drainingOnlineServerWhileOfflineServerKeepsOrder() = runTest {
        val dao = FakePendingActionDao()
        val repo = StubMusicRepo(failingProfiles = setOf("p1"))
        val store = OfflineSyncStore(dao, repo)

        // Queue interleaved actions for p1 (offline) and p2 (online)
        store.queueScrobble("p1:song-1", submission = true, timeMs = 100L)
        store.queueStar("p2:song-a", starred = true)
        store.queueStar("p1:song-2", starred = true)
        store.queueScrobble("p2:song-b", submission = true, timeMs = 200L)
        store.queueStarAlbum("p2:alb-1", starred = true)

        val firstFlush = store.flush()

        // p2 drained all 3 actions; p1 failed on its first action and stopped without attempting p1:song-2
        assertEquals(3, firstFlush)
        assertTrue(dao.pendingForProfile("p2").isEmpty())
        val p1Remaining = dao.pendingForProfile("p1")
        assertEquals(listOf("p1:song-1", "p1:song-2"), p1Remaining.map { it.targetId })
        assertEquals(
            listOf("scrobble:p1:song-1", "scrobble:p2:song-b", "star:p2:song-a", "starAlbum:p2:alb-1"),
            repo.replayedCalls,
        )

        // Once p1 comes back online, its queued actions drain cleanly in order
        repo.replayedCalls.clear()
        repo.failingProfiles = emptySet()

        val secondFlush = store.flush()
        assertEquals(2, secondFlush)
        assertTrue(dao.all().isEmpty())
        assertEquals(listOf("scrobble:p1:song-1", "star:p1:song-2"), repo.replayedCalls)
    }

    @Test
    fun queueActions_extractsProfileId_defaultsBareToP1_andSkipsDeviceLocal() = runTest {
        val dao = FakePendingActionDao()
        val repo = StubMusicRepo()
        val store = OfflineSyncStore(dao, repo)

        store.queueScrobble("p2:s1", submission = true, timeMs = 10L)
        store.queueStar("legacy-bare-song", starred = true)
        store.queueStarAlbum("p3:al1", starred = false)
        store.queueStarArtist("p2:ar1", starred = true)

        // Device-local IDs must not be queued as server writes
        store.queueScrobble("local:42", submission = true, timeMs = 20L)
        store.queueStar("local:42", starred = true)
        store.queueStarAlbum("local_album:7", starred = true)
        store.queueStarArtist("local_artist:9", starred = true)

        val rows = dao.all()
        assertEquals(4, rows.size)
        assertEquals(listOf("p1", "p2", "p3"), dao.distinctProfileIds())
        assertEquals("p1", rows.first { it.targetId == "legacy-bare-song" }.profileId)
        assertEquals("p2", rows.first { it.targetId == "p2:s1" }.profileId)
        assertEquals("p2", rows.first { it.targetId == "p2:ar1" }.profileId)
        assertEquals("p3", rows.first { it.targetId == "p3:al1" }.profileId)
    }
}
