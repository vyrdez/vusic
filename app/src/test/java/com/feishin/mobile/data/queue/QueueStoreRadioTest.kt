package com.feishin.mobile.data.queue

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun track(id: String) = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = "Ar", artistId = "ar", coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueStoreRadioTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var n = 0

    private fun store(): QueueStore {
        val backing = PreferenceDataStoreFactory.create(
            produceFile = { temp.newFile("radio_${n++}.preferences_pb") },
        )
        return QueueStore(backing)
    }

    @Test
    fun radioFlagAndSeed_roundTrip() = runTest {
        val s = store()
        s.save(listOf(track("a"), track("b")), 0, 1000L, "Radio • A", radioActive = true, radioSeedId = "a")
        val loaded = s.load()!!
        assertTrue(loaded.radioActive)
        assertEquals("a", loaded.radioSeedId)
        assertEquals("Radio • A", loaded.sourceName)
    }

    @Test
    fun nonRadio_defaultFalse() = runTest {
        val s = store()
        s.save(listOf(track("a")), 0, 0L, "Album")
        val loaded = s.load()!!
        assertFalse(loaded.radioActive)
        assertEquals(null, loaded.radioSeedId)
    }

    @Test
    fun clear_wipesRadioState() = runTest {
        val s = store()
        s.save(listOf(track("a")), 0, 0L, "Radio • A", radioActive = true, radioSeedId = "a")
        s.clear()
        assertEquals(null, s.load())
    }
}
