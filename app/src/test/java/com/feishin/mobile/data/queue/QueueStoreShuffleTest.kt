package com.feishin.mobile.data.queue

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feishin.mobile.domain.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun t(id: String) = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = "Ar", artistId = "ar", coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QueueStoreShuffleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var n = 0

    private fun store(): QueueStore {
        val backing = PreferenceDataStoreFactory.create(
            produceFile = { temp.newFile("shuffle_${n++}.preferences_pb") },
        )
        return QueueStore(backing)
    }

    @Test
    fun shuffleState_roundTrips() = runTest {
        val s = store()
        s.save(
            tracks = listOf(t("a"), t("b"), t("c")),
            currentIndex = 1,
            positionMs = 5_000,
            sourceName = "Album",
            queueIds = listOf(11L, 12L, 13L),
            shuffleActive = true,
            shuffleOrder = listOf(13L, 11L, 12L),
            queueSeq = 14L,
        )
        val loaded = s.load()!!
        assertTrue(loaded.shuffleActive)
        assertEquals(listOf(13L, 11L, 12L), loaded.shuffleOrder)
        assertEquals(listOf(11L, 12L, 13L), loaded.tracks.map { it.queueId })
        assertEquals(14L, loaded.queueSeq)
    }

    @Test
    fun shuffleOff_snapshotIsEmpty() = runTest {
        val s = store()
        s.save(listOf(t("a")), 0, 0L, queueIds = listOf(1L))
        val loaded = s.load()!!
        assertFalse(loaded.shuffleActive)
        assertTrue(loaded.shuffleOrder.isEmpty())
    }

    @Test
    fun legacySnapshot_withoutQueueIds_stillLoads() = runTest {
        // Written by an older build: no queueId / shuffle fields at all.
        val file = temp.newFile("legacy_${n++}.preferences_pb")
        val backing = PreferenceDataStoreFactory.create(produceFile = { file })
        val legacy = """{"tracks":[{"id":"a","title":"A"},{"id":"b","title":"B"}],"currentIndex":1,"positionMs":100}"""
        backing.edit { it[stringPreferencesKey("queue_snapshot")] = legacy }

        val loaded = QueueStore(backing).load()!!
        assertEquals(2, loaded.tracks.size)
        assertEquals(-1L, loaded.tracks[0].queueId)
        assertFalse(loaded.shuffleActive)
        assertTrue(loaded.shuffleOrder.isEmpty())
    }

    @Test
    fun clear_wipesShuffleStateToo() = runTest {
        val s = store()
        s.save(
            listOf(t("a")), 0, 0L,
            queueIds = listOf(1L), shuffleActive = true, shuffleOrder = listOf(1L),
        )
        s.clear()
        assertNull(s.load())
    }
}
