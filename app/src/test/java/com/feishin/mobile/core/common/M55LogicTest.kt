package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun album(id: String, name: String, artist: String = "Art", year: Int? = null, plays: Long = 0) =
    Album(id, name, artist, null, null, 1, 60, plays, year, null, false)

private fun artist(id: String, name: String, albums: Int = 1) =
    Artist(id, name, null, albums, false)

private fun track(id: String, title: String, artist: String = "Ar", duration: Int = 180, year: Int? = null) =
    Track(id, title, "Al", "al", artist, "ar", null, duration, 1, year, "Music", "mp3", false)

private fun playlist(id: String, name: String, songs: Int = 1) =
    Playlist(id, name, songs, songs * 180, null)

class LibrarySortTest {

    @Test
    fun sortChoice_roundTrips() {
        assertEquals(SortChoice("name", true), SortChoice.parse("name|asc", "title"))
        assertEquals(SortChoice("year", false), SortChoice.parse("year|desc", "name"))
        assertEquals(SortChoice("title", true), SortChoice.parse("", "title"))
        assertEquals(SortChoice("title", true), SortChoice.parse(null, "title"))
    }

    @Test
    fun options_haveReadableLabels() {
        val albums = LibrarySorts.options("ALBUMS").associate { it.id to it.label }
        assertEquals("Name", albums["name"])
        assertEquals("Artist", albums["artist"])
        assertEquals("Year", albums["year"])
        assertEquals("Most played", albums["plays"])
        // No raw enum names leak into the UI.
        LibrarySorts.options("ALBUMS").forEach {
            assertFalse(it.label.contains("_"))
            assertFalse(it.label.all { c -> c.isUpperCase() || c == ' ' })
        }
        assertEquals(setOf("name", "albums"), LibrarySorts.options("ARTISTS").map { it.id }.toSet())
        assertEquals(
            setOf("title", "artist", "duration", "year"),
            LibrarySorts.options("SONGS").map { it.id }.toSet(),
        )
        assertEquals(setOf("name", "songs"), LibrarySorts.options("PLAYLISTS").map { it.id }.toSet())
    }

    @Test
    fun defaults_areSensible() {
        assertEquals(SortChoice("name", true), LibrarySorts.default("ALBUMS"))
        assertEquals(SortChoice("title", true), LibrarySorts.default("SONGS"))
    }

    @Test
    fun label_showsDirection() {
        assertEquals("Name ↑", LibrarySorts.label("ALBUMS", SortChoice("name", true)))
        assertEquals("Name ↓", LibrarySorts.label("ALBUMS", SortChoice("name", false)))
    }
}

class LibraryOrderingTest {

    @Test
    fun albums_nameBothDirections() {
        val list = listOf(album("1", "b"), album("2", "a"))
        assertEquals(listOf("a", "b"), LibraryOrdering.sortAlbums(list, SortChoice("name", true)).map { it.name })
        assertEquals(listOf("b", "a"), LibraryOrdering.sortAlbums(list, SortChoice("name", false)).map { it.name })
    }

    @Test
    fun albums_yearAndPlays() {
        val list = listOf(album("1", "x", year = 2000, plays = 5), album("2", "y", year = 2020, plays = 1))
        assertEquals(listOf("y", "x"), LibraryOrdering.sortAlbums(list, SortChoice("year", false)).map { it.name })
        assertEquals(listOf("x", "y"), LibraryOrdering.sortAlbums(list, SortChoice("plays", false)).map { it.name })
    }

    @Test
    fun artists_mostAlbums() {
        val list = listOf(artist("1", "a", 1), artist("2", "b", 9))
        assertEquals(
            listOf("b", "a"),
            LibraryOrdering.sortArtists(list, SortChoice("albums", false)).map { it.name },
        )
    }

    @Test
    fun songs_titleArtistDuration() {
        val list = listOf(track("1", "b", "Zed", 300), track("2", "a", "Amy", 100))
        assertEquals(listOf("a", "b"), LibraryOrdering.sortSongs(list, SortChoice("title", true)).map { it.title })
        assertEquals(
            listOf("a", "b"),
            LibraryOrdering.sortSongs(list, SortChoice("artist", true)).map { it.title },
        )
        assertEquals(
            listOf("a", "b"),
            LibraryOrdering.sortSongs(list, SortChoice("duration", true)).map { it.title },
        )
    }

    @Test
    fun playlists_mostSongs() {
        val list = listOf(playlist("1", "b", 2), playlist("2", "a", 9))
        assertEquals(
            listOf("a", "b"),
            LibraryOrdering.sortPlaylists(list, SortChoice("songs", false)).map { it.name },
        )
    }

    @Test
    fun filter_matchesNamesAndArtists() {
        val albums = listOf(album("1", "Frozen", "Roku"), album("2", "Other", "Someone"))
        assertEquals(listOf("Frozen"), LibraryOrdering.filterAlbums(albums, "fro").map { it.name })
        assertEquals(listOf("Frozen"), LibraryOrdering.filterAlbums(albums, "ROKU").map { it.name })
        assertEquals(2, LibraryOrdering.filterAlbums(albums, "").size)
        assertEquals(2, LibraryOrdering.filterAlbums(albums, "   ").size)
        assertTrue(LibraryOrdering.filterAlbums(albums, "zzz").isEmpty())
    }

    @Test
    fun filter_songsMatchTitleArtistAlbum() {
        val songs = listOf(track("1", "Hello", "A"), track("2", "Bye", "B"))
        assertEquals(1, LibraryOrdering.filterSongs(songs, "hello").size)
        assertEquals(1, LibraryOrdering.filterSongs(songs, "b").size)
    }

    @Test
    fun filter_playlistsMatchName() {
        val lists = listOf(playlist("1", "Gym"), playlist("2", "Chill"))
        assertEquals(listOf("Gym"), LibraryOrdering.filterPlaylists(lists, "gym").map { it.name })
    }
}

class SearchGroupingTest {

    private fun results() = SearchResults(
        artists = listOf(artist("ar1", "Aimer")),
        albums = listOf(
            com.feishin.mobile.domain.model.Album("al1", "After Dark", "A", null, null, 5, 1, 0, 2020, null, false),
        ),
        tracks = (1..8).map { track("t$it", "Song $it") },
        playlists = listOf(playlist("p1", "Mix")),
    )

    @Test
    fun topResult_prefersArtistOverAlbum() {
        val top = SearchGrouping.topResult(results())
        assertTrue(top is SearchGrouping.TopHit.Artist)
        assertEquals("ar1", (top as SearchGrouping.TopHit.Artist).id)
    }

    @Test
    fun topResult_fallsBackToAlbum() {
        val top = SearchGrouping.topResult(results().copy(artists = emptyList()))
        assertTrue(top is SearchGrouping.TopHit.Album)
    }

    @Test
    fun topResult_nullWhenNeither() {
        assertNull(SearchGrouping.topResult(results().copy(artists = emptyList(), albums = emptyList())))
    }

    @Test
    fun songs_fivePreviewThenRest() {
        val res = results()
        assertEquals(5, SearchGrouping.previewSongs(res).size)
        assertEquals(3, SearchGrouping.remainingSongs(res).size)
        assertEquals(
            res.tracks.map { it.id },
            (SearchGrouping.previewSongs(res) + SearchGrouping.remainingSongs(res)).map { it.id },
        )
    }

    @Test
    fun isEmpty_coversAllTypes() {
        assertTrue(SearchGrouping.isEmpty(SearchResults()))
        assertFalse(SearchGrouping.isEmpty(results().copy(tracks = emptyList(), artists = emptyList(), albums = emptyList())))
    }
}

class StableCacheKeyTest {

    @Test
    fun stripsVolatileAuthParams() {
        val a = "https://host/rest/stream.view?id=abc&u=u&t=tok1&s=salt1&v=1.16.1&c=c&f=json"
        val b = "https://host/rest/stream.view?id=abc&u=u&t=tok2&s=salt2&v=1.16.1&c=c&f=json"
        assertEquals(StableCacheKey.keyFor(a), StableCacheKey.keyFor(b))
    }

    @Test
    fun keepsQualityParams() {
        val base = "https://host/rest/stream.view?id=abc&u=u&t=t&s=s&v=v&c=c&f=json"
        val hq = "$base&maxBitRate=320&format=mp3"
        val raw = base
        assertTrue(StableCacheKey.keyFor(hq) != StableCacheKey.keyFor(raw))
        assertEquals(StableCacheKey.keyFor("$base&format=mp3&maxBitRate=320"), StableCacheKey.keyFor(hq))
    }

    @Test
    fun distinguishesSongs() {
        val a = "https://host/rest/stream.view?id=aaa&u=u&t=t&s=s&v=v&c=c&f=json"
        val b = "https://host/rest/stream.view?id=bbb&u=u&t=t&s=s&v=v&c=c&f=json"
        assertTrue(StableCacheKey.keyFor(a) != StableCacheKey.keyFor(b))
    }

    @Test
    fun nonUrl_returnsAsIs() {
        assertEquals("file:///x/y.flac", StableCacheKey.keyFor("file:///x/y.flac"))
        assertEquals("not a url at all", StableCacheKey.keyFor("not a url at all"))
    }
}

class PrefetchWindowTest {

    @Test
    fun window_nextNAfterCurrent() {
        assertEquals(listOf(3L, 4L), PrefetchWindow.window(listOf(1L, 2L, 3L, 4L, 5L), 1, 2))
        assertEquals(listOf(5L), PrefetchWindow.window(listOf(1L, 2L, 3L, 4L, 5L), 3, 3))
        assertEquals(emptyList<Long>(), PrefetchWindow.window(listOf(1L, 2L), 1, 2))
    }

    @Test
    fun window_dedupsAndBounds() {
        assertEquals(listOf(2L, 3L), PrefetchWindow.window(listOf(1L, 2L, 2L, 3L), 0, 5))
        assertEquals(emptyList<Long>(), PrefetchWindow.window(listOf(1L), 0, 0))
        assertEquals(emptyList<Long>(), PrefetchWindow.window(emptyList(), 0, 2))
    }

    @Test
    fun stale_detectsLeftWindow() {
        assertEquals(setOf(9L), PrefetchWindow.stale(setOf(3L, 4L, 9L), listOf(3L, 4L)))
        assertTrue(PrefetchWindow.stale(setOf(1L), listOf(1L)).isEmpty())
    }

    @Test
    fun allowedOnNetwork_respectsWifiOnly() {
        assertTrue(PrefetchWindow.allowedOnNetwork(NetworkClass.WIFI, true))
        assertFalse(PrefetchWindow.allowedOnNetwork(NetworkClass.MOBILE, true))
        assertTrue(PrefetchWindow.allowedOnNetwork(NetworkClass.MOBILE, false))
    }
}

class CachedMembershipTest {

    data class Row(val key: String)

    @Test
    fun keepsFullyCachedDropsRest() {
        val rows = listOf(Row("a"), Row("b"), Row("c"))
        val (keep, remove) = CachedMembership.partition(rows, { it.key }) { it != "b" }
        assertEquals(listOf("a", "c"), keep.map { it.key })
        assertEquals(listOf("b"), remove.map { it.key })
    }

    @Test
    fun checkFailure_countsAsEvicted() {
        val (keep, remove) = CachedMembership.partition(listOf(Row("a")), { it.key }) { throw RuntimeException("x") }
        assertTrue(keep.isEmpty())
        assertEquals(listOf("a"), remove.map { it.key })
    }

    @Test
    fun emptyIn_allKept() {
        val rows = listOf(Row("a"))
        val (keep, remove) = CachedMembership.partition(rows, { it.key }) { true }
        assertEquals(1, keep.size)
        assertTrue(remove.isEmpty())
    }
}
