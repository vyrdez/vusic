package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFilesFilterTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        durationSec: Int = 180,
        localModifiedSec: Long = 1000L,
    ) = Track(
        id = id,
        title = title,
        album = album,
        albumId = "al_$id",
        artist = artist,
        artistId = "ar_$id",
        coverArt = "c",
        durationSec = durationSec,
        trackNo = 1,
        year = 2024,
        genre = "Rock",
        suffix = "mp3",
        starred = false,
        localUri = "content://media/external/audio/media/$id",
        localModifiedSec = localModifiedSec,
    )

    @Test
    fun shouldInclude_filtersByDurationThreshold() {
        assertTrue(DeviceFilesFilter.shouldInclude(durationMs = 30_000, minDurationSec = 30))
        assertFalse(DeviceFilesFilter.shouldInclude(durationMs = 29_999, minDurationSec = 30))
        assertTrue(DeviceFilesFilter.shouldInclude(durationMs = 500, minDurationSec = 0))
    }

    @Test
    fun shouldInclude_excludesRingtonesAlarmsAndNotifications() {
        assertFalse(DeviceFilesFilter.shouldInclude(durationMs = 60_000, minDurationSec = 0, isRingTone = true))
        assertFalse(DeviceFilesFilter.shouldInclude(durationMs = 60_000, minDurationSec = 0, isAlarm = true))
        assertFalse(DeviceFilesFilter.shouldInclude(durationMs = 60_000, minDurationSec = 0, isNotification = true))
        assertFalse(DeviceFilesFilter.shouldInclude(durationMs = 60_000, minDurationSec = 0, isMusic = false))
        assertTrue(DeviceFilesFilter.shouldInclude(durationMs = 60_000, minDurationSec = 0, isMusic = true))
    }

    @Test
    fun filterAndSort_filtersByQueryAndSortsCorrectly() {
        val tracks = listOf(
            track("1", "Song Beta", artist = "Bravo", localModifiedSec = 100),
            track("2", "Song Alpha", artist = "Alpha", localModifiedSec = 300),
            track("3", "Instrumental Gamma", artist = "Delta", localModifiedSec = 200),
        )

        // Query test
        val filtered = DeviceFilesFilter.filterAndSort(
            tracks,
            query = "alpha",
            sort = SortChoice("title", ascending = true),
        )
        assertEquals(1, filtered.size)
        assertEquals("Song Alpha", filtered[0].title)

        // Sort by title ascending
        val sortedTitle = DeviceFilesFilter.filterAndSort(
            tracks,
            query = "",
            sort = SortChoice("title", ascending = true),
        )
        assertEquals(listOf("Instrumental Gamma", "Song Alpha", "Song Beta"), sortedTitle.map { it.title })

        // Sort by recent descending
        val sortedRecent = DeviceFilesFilter.filterAndSort(
            tracks,
            query = "",
            sort = SortChoice("recent", ascending = true),
        )
        assertEquals(listOf("2", "3", "1"), sortedRecent.map { it.id })
    }

    @Test
    fun durationLabel_formatsCorrectly() {
        assertEquals("Off", DeviceFilesFilter.durationLabel(0))
        assertEquals("15 s", DeviceFilesFilter.durationLabel(15))
        assertEquals("30 s", DeviceFilesFilter.durationLabel(30))
        assertEquals("60 s", DeviceFilesFilter.durationLabel(60))
    }
}
