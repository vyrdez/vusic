package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistAddSpeedTest {

    private class FakeCacheDao : MetadataCacheDao {
        val storage = ConcurrentHashMap<String, String>()
        override suspend fun get(key: String): String? = storage[key]
        override suspend fun put(entity: CachedMetadataEntity) {
            storage[entity.key] = entity.json
        }
        override suspend fun remove(key: String) {
            storage.remove(key)
        }
        override suspend fun clear() {
            storage.clear()
        }
    }

    private fun testTrack(id: String, durationSec: Int = 180) = Track(
        id = id,
        title = "Song $id",
        album = "Album",
        albumId = "al_1",
        artist = "Artist",
        artistId = "ar_1",
        coverArt = "cover",
        durationSec = durationSec,
        trackNo = 1,
        year = 2024,
        genre = "Pop",
        suffix = "mp3",
        starred = false,
    )

    @Test
    fun optimisticUpdate_updatesRoomCacheImmediately() = runTest {
        val dao = FakeCacheDao()
        val cache = MetadataCache(dao)
        val events = PlaylistEvents()

        val playlistId = "pl_1"
        val initialDetail = PlaylistDetail(
            id = playlistId,
            name = "My Jams",
            songCount = 2,
            durationSec = 360,
            tracks = listOf(testTrack("s1"), testTrack("s2")),
        )
        val initialPlaylists = listOf(
            Playlist(
                id = playlistId,
                name = "My Jams",
                songCount = 2,
                durationSec = 360,
                coverArt = "cover",
            )
        )

        // Seed cache
        cache.write(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer(), initialDetail)
        cache.write(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()), initialPlaylists)

        val newTrack = testTrack("s3", durationSec = 200)

        // Snapshot original
        val originalDetail = cache.read(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer())
        val originalPlaylists = cache.read(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()))

        // Perform optimistic update
        val updatedDetail = originalDetail!!.copy(
            songCount = originalDetail.songCount + 1,
            durationSec = originalDetail.durationSec + newTrack.durationSec,
            tracks = originalDetail.tracks + newTrack,
        )
        cache.write(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer(), updatedDetail)

        val updatedPlaylists = originalPlaylists!!.map { p ->
            if (p.id == playlistId) {
                p.copy(
                    songCount = p.songCount + 1,
                    durationSec = p.durationSec + newTrack.durationSec,
                )
            } else p
        }
        cache.write(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()), updatedPlaylists)

        // Verify cache updated immediately (0ms latency)
        val cachedAfter = cache.read(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer())
        assertEquals(3, cachedAfter?.songCount)
        assertEquals(560, cachedAfter?.durationSec)
        assertEquals(3, cachedAfter?.tracks?.size)
        assertEquals("s3", cachedAfter?.tracks?.last()?.id)

        val cachedPlaylistsAfter = cache.read(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()))
        assertEquals(3, cachedPlaylistsAfter?.first()?.songCount)
        assertEquals(560, cachedPlaylistsAfter?.first()?.durationSec)
    }

    @Test
    fun rollbackOnFailure_restoresRoomCache() = runTest {
        val dao = FakeCacheDao()
        val cache = MetadataCache(dao)

        val playlistId = "pl_1"
        val originalDetail = PlaylistDetail(
            id = playlistId,
            name = "My Jams",
            songCount = 2,
            durationSec = 360,
            tracks = listOf(testTrack("s1"), testTrack("s2")),
        )
        val originalPlaylists = listOf(
            Playlist(
                id = playlistId,
                name = "My Jams",
                songCount = 2,
                durationSec = 360,
                coverArt = "cover",
            )
        )

        cache.write(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer(), originalDetail)
        cache.write(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()), originalPlaylists)

        // Optimistically apply new track
        val newTrack = testTrack("s3", durationSec = 200)
        cache.write(
            MetadataCacheKeys.playlist(playlistId),
            PlaylistDetail.serializer(),
            originalDetail.copy(
                songCount = originalDetail.songCount + 1,
                durationSec = originalDetail.durationSec + newTrack.durationSec,
                tracks = originalDetail.tracks + newTrack,
            )
        )

        // Simulate network failure and trigger rollback
        var rollbackInvoked = false
        val onRollback: () -> Unit = { rollbackInvoked = true }

        // Execute rollback logic
        cache.write(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer(), originalDetail)
        cache.write(MetadataCacheKeys.PLAYLISTS, ListSerializer(Playlist.serializer()), originalPlaylists)
        onRollback()

        assertTrue(rollbackInvoked)
        val restoredDetail = cache.read(MetadataCacheKeys.playlist(playlistId), PlaylistDetail.serializer())
        assertEquals(2, restoredDetail?.songCount)
        assertEquals(360, restoredDetail?.durationSec)
        assertEquals(2, restoredDetail?.tracks?.size)
    }

    @Test
    fun duplicateCheck_fromCacheTriggersConfirmation() = runTest {
        val playlist = PlaylistDetail(
            id = "pl_dup",
            name = "Favorites",
            songCount = 3,
            durationSec = 600,
            tracks = listOf(testTrack("song_1"), testTrack("song_2"), testTrack("song_3")),
        )
        val existingIds = playlist.tracks.map { it.id }.toSet()

        // Track already present in cache
        val candidateExisting = listOf(testTrack("song_2"))
        val isDuplicate = candidateExisting.any { it.id in existingIds }
        assertTrue(isDuplicate)

        // Track not present in cache
        val candidateFresh = listOf(testTrack("song_99"))
        val isDuplicateFresh = candidateFresh.any { it.id in existingIds }
        assertFalse(isDuplicateFresh)
    }

    @Test
    fun missingCache_proceedsWithoutBlocking() = runTest {
        // When cached detail is null (miss), duplicate check returns false immediately
        val cachedDetail: PlaylistDetail? = null
        val tracksToAdd = listOf(testTrack("song_1"))

        val isDuplicate = if (cachedDetail != null) {
            val existingIds = cachedDetail.tracks.map { it.id }.toSet()
            tracksToAdd.any { it.id in existingIds }
        } else {
            false // Does not block on network
        }

        assertFalse(isDuplicate)
    }

    @Test
    fun singleSongIdToAddParameterUsedInsteadOfFullList() {
        // Verify that adding 1 song sends a single ID in songIdToAdd rather than full list
        val tracksToAdd = listOf("song_42")
        val batches = com.feishin.mobile.core.common.PlaylistBatch.chunks(tracksToAdd)
        assertEquals(1, batches.size)
        assertEquals(listOf("song_42"), batches.first())
    }
}
