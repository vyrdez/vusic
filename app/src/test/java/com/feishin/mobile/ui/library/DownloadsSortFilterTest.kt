package com.feishin.mobile.ui.library

import com.feishin.mobile.core.common.LibrarySorts
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.data.db.DownloadEntity
import com.feishin.mobile.data.db.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsSortFilterTest {

    private fun download(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        durationSec: Int = 180,
        enqueuedAt: Long = 1000L,
        completedAt: Long? = 2000L,
    ) = DownloadEntity(
        songId = id,
        title = title,
        artist = artist,
        album = album,
        albumId = "al_$id",
        artistId = "ar_$id",
        coverArt = "cover_$id",
        durationSec = durationSec,
        trackNo = 1,
        year = 2024,
        genre = "Pop",
        suffix = "flac",
        status = DownloadStatus.COMPLETED.name,
        enqueuedAt = enqueuedAt,
        completedAt = completedAt,
    )

    @Test
    fun librarySorts_downloadsOptionsAndDefault() {
        val options = LibrarySorts.options("DOWNLOADS")
        assertEquals(
            listOf("title", "artist", "date_added", "duration"),
            options.map { it.id },
        )
        assertEquals(
            listOf("Title", "Artist", "Date added", "Duration"),
            options.map { it.label },
        )

        assertEquals(SortChoice("title", true), LibrarySorts.default("DOWNLOADS"))
    }

    @Test
    fun librarySorts_migratesNameToTitle() {
        assertEquals(
            SortChoice("title", true),
            LibrarySorts.migrate("DOWNLOADS", SortChoice("name", true)),
        )
        assertEquals(
            SortChoice("title", false),
            LibrarySorts.migrate("DOWNLOADS", SortChoice("name", false)),
        )
        assertEquals(
            SortChoice("title", true),
            LibrarySorts.migrate(SortChoice("name", true)),
        )
        assertEquals(
            SortChoice("title", true),
            SortChoice("name", true).migrate(),
        )

        // Non-name or other tabs unaffected
        assertEquals(
            SortChoice("artist", true),
            LibrarySorts.migrate("DOWNLOADS", SortChoice("artist", true)),
        )
        assertEquals(
            SortChoice("name", true),
            LibrarySorts.migrate("ALBUMS", SortChoice("name", true)),
        )

        // Labels resolve Title instead of Name
        assertEquals("Title ↑", LibrarySorts.label("DOWNLOADS", SortChoice("name", true)))
        assertEquals("Title ↓", LibrarySorts.label("DOWNLOADS", SortChoice("name", false)))
    }

    @Test
    fun filterDownloads_matchesTitleArtistAlbumCaseInsensitive() {
        val list = listOf(
            download("1", title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera"),
            download("2", title = "Starboy", artist = "The Weeknd", album = "Starboy"),
            download("3", title = "Midnight City", artist = "M83", album = "Hurry Up, We're Dreaming"),
        )

        // Title match
        assertEquals(listOf("1"), filterDownloads(list, "bohemian").map { it.songId })
        assertEquals(listOf("1"), filterDownloads(list, "RHAPSODY").map { it.songId })

        // Artist match
        assertEquals(listOf("2"), filterDownloads(list, "weeknd").map { it.songId })
        assertEquals(listOf("3"), filterDownloads(list, "M83").map { it.songId })

        // Album match
        assertEquals(listOf("1"), filterDownloads(list, "opera").map { it.songId })
        assertEquals(listOf("3"), filterDownloads(list, "hurry up").map { it.songId })

        // Blank or whitespace query returns full list
        assertEquals(3, filterDownloads(list, "").size)
        assertEquals(3, filterDownloads(list, "   ").size)

        // Non-matching query returns empty
        assertTrue(filterDownloads(list, "nonexistent").isEmpty())
    }

    @Test
    fun sortDownloads_byTitleAscendingAndDescending() {
        val list = listOf(
            download("1", title = "Zebra"),
            download("2", title = "Apple"),
            download("3", title = "Mango"),
        )

        val asc = sortDownloads(list, SortChoice("title", ascending = true))
        assertEquals(listOf("Apple", "Mango", "Zebra"), asc.map { it.title })

        val desc = sortDownloads(list, SortChoice("title", ascending = false))
        assertEquals(listOf("Zebra", "Mango", "Apple"), desc.map { it.title })
    }

    @Test
    fun sortDownloads_byArtistAscendingAndDescending() {
        val list = listOf(
            download("1", title = "Song A", artist = "The Beatles"),
            download("2", title = "Song B", artist = "ABBA"),
            download("3", title = "Song C", artist = "Coldplay"),
        )

        val asc = sortDownloads(list, SortChoice("artist", ascending = true))
        assertEquals(listOf("ABBA", "Coldplay", "The Beatles"), asc.map { it.artist })

        val desc = sortDownloads(list, SortChoice("artist", ascending = false))
        assertEquals(listOf("The Beatles", "Coldplay", "ABBA"), desc.map { it.artist })
    }

    @Test
    fun sortDownloads_byDateAddedAscendingAndDescending() {
        val list = listOf(
            download("1", title = "Oldest", completedAt = 100L),
            download("2", title = "Newest", completedAt = 300L),
            download("3", title = "Middle", completedAt = 200L),
        )

        val asc = sortDownloads(list, SortChoice("date_added", ascending = true))
        assertEquals(listOf("Oldest", "Middle", "Newest"), asc.map { it.title })

        val desc = sortDownloads(list, SortChoice("date_added", ascending = false))
        assertEquals(listOf("Newest", "Middle", "Oldest"), desc.map { it.title })
    }

    @Test
    fun sortDownloads_byDurationAscendingAndDescending() {
        val list = listOf(
            download("1", title = "Medium", durationSec = 180),
            download("2", title = "Short", durationSec = 60),
            download("3", title = "Long", durationSec = 360),
        )

        val asc = sortDownloads(list, SortChoice("duration", ascending = true))
        assertEquals(listOf("Short", "Medium", "Long"), asc.map { it.title })

        val desc = sortDownloads(list, SortChoice("duration", ascending = false))
        assertEquals(listOf("Long", "Medium", "Short"), desc.map { it.title })
    }

    @Test
    fun sortDownloads_migratesNameToTitle() {
        val list = listOf(
            download("1", title = "Beta"),
            download("2", title = "Alpha"),
        )

        val sorted = sortDownloads(list, SortChoice("name", ascending = true))
        assertEquals(listOf("Alpha", "Beta"), sorted.map { it.title })
    }

    @Test
    fun toDomainTrack_mapsAllFields() {
        val entity = download(
            id = "s123",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationSec = 240,
        )

        val track = entity.toDomainTrack()
        assertEquals("s123", track.id)
        assertEquals("Test Song", track.title)
        assertEquals("Test Artist", track.artist)
        assertEquals("Test Album", track.album)
        assertEquals(240, track.durationSec)
        assertEquals("cover_s123", track.coverArt)
    }
}
