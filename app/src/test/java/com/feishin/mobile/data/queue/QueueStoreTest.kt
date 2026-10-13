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
}
