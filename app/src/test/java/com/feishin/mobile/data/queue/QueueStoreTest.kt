package com.feishin.mobile.data.queue

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun track(id: String, title: String) = Track(
    id = id, title = title, album = "Al", albumId = "al",
    artist = "Ar", artistId = "ar", coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): QueueStore {
        val backing = PreferenceDataStoreFactory.create(
            produceFile = { temp.newFile("queue_test.preferences_pb") },
        )
        return QueueStore(backing)
    }

    @Test
    fun emptyStore_loadsNull() = runTest {
        assertNull(store().load())
        assertTrue(store().loadTracks().isEmpty())
    }

    @Test
    fun saveAndLoad_roundTripsQueueIndexAndPosition() = runTest {
        val s = store()
        val tracks = listOf(track("1", "One"), track("2", "Two"), track("3", "Three"))
        s.save(tracks, currentIndex = 1, positionMs = 42_000)
        val loaded = s.load()
        assertEquals(3, loaded?.tracks?.size)
        assertEquals("Two", loaded?.tracks?.get(1)?.toDomain()?.title)
        assertEquals(1, loaded?.currentIndex)
        assertEquals(42_000L, loaded?.positionMs)
        // Domain mapping keeps the details.
        val first = loaded!!.tracks.first().toDomain()
        assertEquals("1", first.id)
        assertEquals("Al", first.album)
        assertEquals(true, first.starred)
    }

    @Test
    fun save_clampsOutOfRangeIndex() = runTest {
        val s = store()
        s.save(listOf(track("1", "One")), currentIndex = 99, positionMs = -5)
        val loaded = s.load()
        assertEquals(0, loaded?.currentIndex)
        assertEquals(0L, loaded?.positionMs)
    }

    @Test
    fun saveEmpty_clearsSnapshot() = runTest {
        val s = store()
        s.save(listOf(track("1", "One")), 0, 0)
        assertEquals(1, s.loadTracks().size)
        s.save(emptyList(), 0, 0)
        assertNull(s.load())
    }

    @Test
    fun clear_emptiesSnapshot() = runTest {
        val s = store()
        s.save(listOf(track("1", "One")), 0, 1_000)
        s.clear()
        assertNull(s.load())
    }

    // ---- 8.3-C: the state the restore contract needs ----------------------

    @Test
    fun saveAndLoad_roundTripsRepeatShuffleSourceAndFlags() = runTest {
        val s = store()
        val tracks = listOf(track("1", "One"), track("2", "Two"))
        s.save(
            tracks,
            currentIndex = 1,
            positionMs = 12_345,
            sourceName = "Playing from test",
            radioActive = true,
            radioSeedId = "2",
            queueIds = listOf(7L, 9L),
            shuffleActive = true,
            shuffleOrder = listOf(9L, 7L),
            queueSeq = 10L,
            repeatMode = 2,
            downloadedIds = setOf("1"),
            cachedIds = setOf("2"),
        )
        val loaded = s.load()!!
        assertEquals(1, loaded.currentIndex)
        assertEquals(12_345L, loaded.positionMs)
        assertEquals("Playing from test", loaded.sourceName)
        assertTrue(loaded.radioActive)
        assertEquals("2", loaded.radioSeedId)
        assertEquals(listOf(7L, 9L), loaded.tracks.map { it.queueId })
        assertTrue(loaded.shuffleActive)
        assertEquals(listOf(9L, 7L), loaded.shuffleOrder)
        assertEquals(10L, loaded.queueSeq)
        assertEquals(2, loaded.repeatMode)
        assertEquals(true, loaded.tracks[0].downloaded)
        assertEquals(false, loaded.tracks[0].cached)
        assertEquals(true, loaded.tracks[1].cached)
        assertEquals(false, loaded.tracks[1].downloaded)
    }

    @Test
    fun positionOnlyWrite_winsOverTheSnapshotPosition() = runTest {
        val s = store()
        s.save(listOf(track("1", "One")), 0, 1_000)
        s.savePosition(9_000)
        assertEquals(9_000L, s.load()?.positionMs)
        // The queue itself is untouched by a position write.
        assertEquals(1, s.loadTracks().size)
    }

    @Test
    fun positionWriteWithoutSnapshot_doesNothing() = runTest {
        val s = store()
        s.savePosition(5_000)
        assertNull(s.load())
    }

    @Test
    fun clear_alsoDropsTheSidePositionKey() = runTest {
        val s = store()
        s.save(listOf(track("1", "One")), 0, 1_000)
        s.savePosition(8_000)
        s.clear()
        assertNull(s.load())
        // A later snapshot must not inherit the dead position.
        s.save(listOf(track("2", "Two")), 0, 0)
        assertEquals(0L, s.load()?.positionMs)
    }

    @Test
    fun saveReplacesTheSidePosition_evenAfterAnOlderPositionWrite() = runTest {
        val s = store()
        s.save(listOf(track("1", "One"), track("2", "Two")), 0, 1_000)
        s.savePosition(1_500)
        // Track change: the snapshot and its position are written together.
        s.save(listOf(track("1", "One"), track("2", "Two")), 1, 0, queueIds = listOf(1L, 2L))
        val loaded = s.load()!!
        assertEquals(1, loaded.currentIndex)
        assertEquals(0L, loaded.positionMs)
    }

    @Test
    fun saveAndLoad_preservesLocalUriAndModifiedSecForDeviceFiles() = runTest {
        val s = store()
        val serverTrack = track("srv_1", "Server Song")
        val localTrack = track("loc_2", "Local Song").copy(
            localUri = "content://media/external/audio/media/42",
            localModifiedSec = 1700000000L,
        )
        s.save(listOf(serverTrack, localTrack), currentIndex = 1, positionMs = 5000L)

        val loaded = s.load()!!
        assertEquals(2, loaded.tracks.size)

        val loadedServer = loaded.tracks[0].toDomain()
        assertNull(loadedServer.localUri)
        assertEquals(0L, loadedServer.localModifiedSec)

        val loadedLocal = loaded.tracks[1].toDomain()
        assertEquals("content://media/external/audio/media/42", loadedLocal.localUri)
        assertEquals(1700000000L, loadedLocal.localModifiedSec)
    }
}
