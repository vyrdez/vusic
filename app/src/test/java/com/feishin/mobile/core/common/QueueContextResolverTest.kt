package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [QueueContextResolver] across playlist ordering,
 * library ordering, device files filtering, duplicate IDs, and fallback cases.
 */
class QueueContextResolverTest {

    private fun track(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        durationSec: Int = 180,
        year: Int? = 2024,
        localUri: String? = null,
    ): Track = Track(
        id = id,
        title = title,
        album = album,
        albumId = null,
        artist = artist,
        artistId = null,
        coverArt = null,
        durationSec = durationSec,
        trackNo = null,
        year = year,
        genre = null,
        suffix = "flac",
        starred = false,
        localUri = localUri,
    )

    @Test
    fun playlist_1079SongsFilteredToSingleRinka_resolvesFullSortedListWithExactStartIndex() {
        val targetSourceIndex = 642
        val playlist = List(1079) { index ->
            if (index == targetSourceIndex) {
                track(id = "t-$index", title = "Rinka", artist = "Aimer", durationSec = 215)
            } else {
                track(
                    id = "t-$index",
                    title = "Track ${index.toString().padStart(4, '0')}",
                    artist = "Artist ${index % 17}",
                    durationSec = 120 + (index % 240),
                )
            }
        }

        val sort = PlaylistSort(PlaylistSortKey.TITLE, descending = false)
        val fullSortedSourceIndices = PlaylistOrdering.visibleIndices(
            size = playlist.size,
            sort = sort,
            query = "",
            titleAt = { playlist[it].title },
            artistAt = { playlist[it].artist },
            albumAt = { playlist[it].album },
            durationAt = { playlist[it].durationSec },
        )
        val filteredSourceIndices = PlaylistOrdering.visibleIndices(
            size = playlist.size,
            sort = sort,
            query = "rinka",
            titleAt = { playlist[it].title },
            artistAt = { playlist[it].artist },
            albumAt = { playlist[it].album },
            durationAt = { playlist[it].durationSec },
        )

        assertEquals(1, filteredSourceIndices.size)
        val clickedSourceIndex = filteredSourceIndices.single()
        assertEquals(targetSourceIndex, clickedSourceIndex)

        val fullSortedTracks = fullSortedSourceIndices.map { playlist[it] }
        val clickedTrack = playlist[clickedSourceIndex]

        val resolved = QueueContextResolver.resolvePlayContext(
            fullSortedTracks = fullSortedTracks,
            clickedTrack = clickedTrack,
            fullSortedSourceIndices = fullSortedSourceIndices,
            clickedSourceIndex = clickedSourceIndex,
        )

        val expectedStartIndex = fullSortedSourceIndices.indexOf(targetSourceIndex)
        assertEquals(1079, resolved.tracks.size)
        assertEquals(fullSortedTracks, resolved.tracks)
        assertEquals(expectedStartIndex, resolved.startIndex)
        assertEquals("t-$targetSourceIndex", resolved.tracks[resolved.startIndex].id)
        assertEquals("Rinka", resolved.tracks[resolved.startIndex].title)
    }

    @Test
    fun playlist_filteredAndSortedTitleDescAndDurationAsc_preservesSortOrderAndStartIndex() {
        val playlist = listOf(
            track("1", title = "Delta", artist = "Alpha", durationSec = 240),
            track("2", title = "Alpha", artist = "Beta", durationSec = 90),
            track("3", title = "Charlie", artist = "Alpha", durationSec = 150),
            track("4", title = "Bravo", artist = "Gamma", durationSec = 300),
            track("5", title = "Echo", artist = "Alpha", durationSec = 110),
        )

        val sorts = listOf(
            PlaylistSort(PlaylistSortKey.TITLE, descending = true),
            PlaylistSort(PlaylistSortKey.DURATION, descending = false),
        )

        for (sort in sorts) {
            val fullSortedIndices = PlaylistOrdering.visibleIndices(
                size = playlist.size,
                sort = sort,
                query = "",
                titleAt = { playlist[it].title },
                artistAt = { playlist[it].artist },
                albumAt = { playlist[it].album },
                durationAt = { playlist[it].durationSec },
            )
            val filteredIndices = PlaylistOrdering.visibleIndices(
                size = playlist.size,
                sort = sort,
                query = "Alpha",
                titleAt = { playlist[it].title },
                artistAt = { playlist[it].artist },
                albumAt = { playlist[it].album },
                durationAt = { playlist[it].durationSec },
            )

            assertTrue(filteredIndices.isNotEmpty())
            val fullSortedTracks = fullSortedIndices.map { playlist[it] }
            val clickedSourceIndex = filteredIndices.last()
            val clickedTrack = playlist[clickedSourceIndex]

            val resolved = QueueContextResolver.resolvePlayContext(
                fullSortedTracks = fullSortedTracks,
                clickedTrack = clickedTrack,
                fullSortedSourceIndices = fullSortedIndices,
                clickedSourceIndex = clickedSourceIndex,
            )

            assertEquals(fullSortedTracks, resolved.tracks)
            assertEquals(fullSortedIndices.indexOf(clickedSourceIndex), resolved.startIndex)
            assertEquals(clickedTrack.id, resolved.tracks[resolved.startIndex].id)
        }
    }

    @Test
    fun playlist_duplicateTrackIds_resolvesExactTappedDuplicateViaSourceIndex() {
        val dupTrack = track(id = "dup-1", title = "Repeat Song", artist = "Artist", durationSec = 200)
        val playlist = listOf(
            track("a", title = "First", durationSec = 100),
            dupTrack, // sourceIndex = 1
            track("b", title = "Middle", durationSec = 150),
            dupTrack, // sourceIndex = 3
            track("c", title = "Last", durationSec = 250),
            dupTrack, // sourceIndex = 5
        )

        for (sort in listOf(
            PlaylistOrdering.DEFAULT,
            PlaylistSort(PlaylistSortKey.TITLE, descending = true),
            PlaylistSort(PlaylistSortKey.DURATION, descending = false),
        )) {
            val fullSortedIndices = PlaylistOrdering.visibleIndices(
                size = playlist.size,
                sort = sort,
                query = "",
                titleAt = { playlist[it].title },
                artistAt = { playlist[it].artist },
                albumAt = { playlist[it].album },
                durationAt = { playlist[it].durationSec },
            )
            val fullSortedTracks = fullSortedIndices.map { playlist[it] }

            // Tap the third duplicate entry (sourceIndex = 5), not the first (sourceIndex = 1).
            val tappedSourceIndex = 5
            val resolved = QueueContextResolver.resolvePlayContext(
                fullSortedTracks = fullSortedTracks,
                clickedTrack = playlist[tappedSourceIndex],
                fullSortedSourceIndices = fullSortedIndices,
                clickedSourceIndex = tappedSourceIndex,
            )

            val expectedIndex = fullSortedIndices.indexOf(tappedSourceIndex)
            assertEquals(fullSortedTracks, resolved.tracks)
            assertEquals(expectedIndex, resolved.startIndex)
            assertEquals("dup-1", resolved.tracks[resolved.startIndex].id)
        }
    }

    @Test
    fun fallbacks_missingIdAndEmptyList_neverCrash() {
        val tracks = listOf(
            track("1", title = "One"),
            track("2", title = "Two"),
        )
        val missing = track("missing-999", title = "Ghost")

        val missingResolved = QueueContextResolver.resolvePlayContext(
            fullSortedTracks = tracks,
            clickedTrack = missing,
        )
        assertEquals(listOf(missing) + tracks, missingResolved.tracks)
        assertEquals(0, missingResolved.startIndex)

        val emptyResolved = QueueContextResolver.resolvePlayContext(
            fullSortedTracks = emptyList(),
            clickedTrack = missing,
        )
        assertEquals(listOf(missing), emptyResolved.tracks)
        assertEquals(0, emptyResolved.startIndex)
    }

    @Test
    fun libraryOrdering_filterAndSortSongs_resolvesFullSortedListAtClickedTrack() {
        val library = listOf(
            track("s1", title = "Zeta", artist = "Rinka", durationSec = 260),
            track("s2", title = "Alpha", artist = "Other", durationSec = 140),
            track("s3", title = "Beta", artist = "Rinka", durationSec = 190),
            track("s4", title = "Gamma", artist = "Other", durationSec = 310),
        )

        for (choice in listOf(
            SortChoice("title", ascending = false),
            SortChoice("duration", ascending = true),
        )) {
            val fullSorted = LibraryOrdering.sortSongs(library, choice)
            val filtered = LibraryOrdering.filterSongs(fullSorted, "rinka")
            assertEquals(2, filtered.size)

            val clicked = filtered.last()
            val resolved = QueueContextResolver.resolvePlayContext(
                fullSortedTracks = fullSorted,
                clickedTrack = clicked,
            )

            assertEquals(fullSorted, resolved.tracks)
            assertEquals(fullSorted.indexOfFirst { it.id == clicked.id }, resolved.startIndex)
            assertEquals(clicked.id, resolved.tracks[resolved.startIndex].id)
        }
    }

    @Test
    fun deviceFilesFilter_filterAndSort_resolvesFullSortedListAtClickedTrack() {
        val deviceTracks = listOf(
            track("d1", title = "Zeta", artist = "Local A", durationSec = 240, localUri = "content://media/1"),
            track("d2", title = "Alpha", artist = "Local B", durationSec = 95, localUri = "content://media/2"),
            track("d3", title = "Rinka Demo", artist = "Local A", durationSec = 180, localUri = "content://media/3"),
            track("d4", title = "Gamma", artist = "Local C", durationSec = 320, localUri = "content://media/4"),
        )

        for (choice in listOf(
            SortChoice("title", ascending = false),
            SortChoice("duration", ascending = true),
        )) {
            val fullSorted = DeviceFilesFilter.filterAndSort(deviceTracks, query = "", sort = choice)
            val filtered = DeviceFilesFilter.filterAndSort(deviceTracks, query = "rinka", sort = choice)
            assertEquals(1, filtered.size)

            val clicked = filtered.single()
            val resolved = QueueContextResolver.resolvePlayContext(
                fullSortedTracks = fullSorted,
                clickedTrack = clicked,
            )

            assertEquals(fullSorted, resolved.tracks)
            assertEquals(fullSorted.indexOfFirst { it.id == clicked.id }, resolved.startIndex)
            assertEquals(clicked.id, resolved.tracks[resolved.startIndex].id)
        }
    }
}
