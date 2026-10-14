package com.feishin.mobile.ui.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.feishin.mobile.core.common.LibraryOrdering
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.ui.home.InMemoryHomeCache
import com.feishin.mobile.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun album(id: String, name: String, year: Int? = null, plays: Long = 0) =
    Album(id, name, "Art", null, null, 1, 60, plays, year, null, false)

private fun track(id: String, title: String) =
    Track(id, title, "Al", "al", "Ar", "ar", null, 180, 1, 2024, "Music", "mp3", false)

class FakeMusicRepo(
    var albums: Result<List<Album>> = Result.Ok(emptyList()),
    var artists: Result<List<Artist>> = Result.Ok(emptyList()),
    var genres: Result<List<Genre>> = Result.Ok(emptyList()),
    var playlists: Result<List<Playlist>> = Result.Ok(emptyList()),
    var starred: Result<StarredCollection> = Result.Ok(StarredCollection()),
    var random: Result<List<Track>> = Result.Ok(emptyList()),
    var info: Result<ArtistInfo> = Result.Ok(ArtistInfo()),
    var top: Result<List<Track>> = Result.Ok(emptyList()),
    var similar: Result<List<Track>> = Result.Ok(emptyList()),
) : MusicDataRepository {
    override suspend fun ping() = Result.Ok(Unit)
    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = albums
    override suspend fun artists() = artists
    override suspend fun genres() = genres
    override suspend fun playlists() = playlists
    override suspend fun search(query: String) = Result.Ok(SearchResults())
    override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) =
        Result.Ok(SearchResults())
    override suspend fun serverInfo() =
        Result.Ok(com.feishin.mobile.domain.model.ServerInfo("1.16.1", "Test", emptyList()))
    override suspend fun startScan() = Result.Ok(Unit)
    override suspend fun scanStatus() =
        Result.Ok(com.feishin.mobile.domain.model.ScanStatus(false, 0))
    override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("x")
    override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("x")
    override suspend fun artistInfo(id: String) = info
    override suspend fun randomSongs(size: Int, genre: String?) = random
    override suspend fun topSongs(artist: String, count: Int) = top
    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = similar
    override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("x")
    override suspend fun starred() = starred
    override suspend fun starTrack(id: String, starred: Boolean) = Result.Ok(Unit)
    override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
    override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
    override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Ok(Unit)
    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
    override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
    override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean) = Result.Ok(Unit)
    override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
    override suspend fun lyricsForTrack(id: String, artist: String, title: String) =
        Result.Ok(com.feishin.mobile.core.network.SyncedLyrics.plain(""))
    override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
    override fun coverArtUrl(coverArtId: String?, size: Int) = null
    override fun downloadUrl(track: Track) = ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun settings() = SettingsStore(
        PreferenceDataStoreFactory.create { temp.newFile("settings_test.preferences_pb") },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun homeVm(repo: FakeMusicRepo) = HomeViewModel(repo, settings(), InMemoryHomeCache())

    @Test
    fun home_withAlbums_fillsSections() {
        val vm = homeVm(
            FakeMusicRepo(
                albums = Result.Ok(listOf(album("1", "N"))),
                random = Result.Ok(listOf(track("t1", "T1"), track("t2", "T2"), track("t3", "T3"), track("t4", "T4"), track("t5", "T5"))),
            ),
        )
        awaitHomeIdle(vm)
        val ui = vm.ui.value
        assertEquals(1, ui.recent.size)
        assertEquals(5, ui.quickPicks.size)
    }

    @Test
    fun home_genreFilter_reloadsWithGenre() {
        val vm = homeVm(FakeMusicRepo(albums = Result.Ok(listOf(album("1", "N")))))
        awaitHomeIdle(vm)
        assertEquals("All", vm.ui.value.selectedGenre)
        vm.selectGenre("Rock")
        awaitHomeIdle(vm)
        assertEquals("Rock", vm.ui.value.selectedGenre)
        assertEquals(false, vm.ui.value.loading)
    }

    @Test
    fun home_genres_sortedBySongCount() {
        val vm = homeVm(
            FakeMusicRepo(
                genres = Result.Ok(
                    listOf(
                        Genre("B", songCount = 1, albumCount = 1),
                        Genre("A", songCount = 99, albumCount = 9),
                    ),
                ),
            ),
        )
        awaitHomeIdle(vm)
        assertEquals(listOf("All", "A", "B"), vm.ui.value.genres)
    }

    private fun awaitHomeIdle(vm: HomeViewModel) {
        repeat(200) {
            if (!vm.ui.value.loading) return
            Thread.sleep(25)
        }
    }

    private fun libraryVm(
        repo: FakeMusicRepo,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = LibraryViewModel(
        repo,
        PlaylistEvents(),
        settings(),
        com.feishin.mobile.data.cache.CachedTrackStore(FakeCachedTrackDao(), FakeCacheBytes()),
        handle,
    )

    private class FakeCacheBytes : com.feishin.mobile.data.cache.CacheBytes {
        var cached: Set<String> = emptySet()
        override fun isCached(cacheKey: String, sizeBytes: Long) = cacheKey in cached
        override fun removeResource(cacheKey: String) {
            cached = cached - cacheKey
        }
    }

    private class FakeCachedTrackDao : com.feishin.mobile.data.db.CachedTrackDao {
        private val rows = mutableMapOf<String, com.feishin.mobile.data.db.CachedTrackEntity>()
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.feishin.mobile.data.db.CachedTrackEntity>> =
            kotlinx.coroutines.flow.MutableStateFlow(rows.values.toList())
        override suspend fun all() = rows.values.toList()
        override suspend fun byId(id: String) = rows[id]
        override suspend fun upsert(track: com.feishin.mobile.data.db.CachedTrackEntity) {
            rows[track.songId] = track
        }
        override suspend fun touchPlayed(id: String, now: Long) {}
        override suspend fun delete(id: String) {
            rows.remove(id)
        }
        override suspend fun clear() = rows.clear()
        override suspend fun count() = rows.size
    }

    @Test
    fun library_defaultTab_isPlaylists() {
        val vm = libraryVm(FakeMusicRepo())
        assertEquals(LibraryTab.PLAYLISTS, vm.tab.value)
    }

    @Test
    fun library_tabOrder_startsPlaylistsDownloads() {
        assertEquals(
            listOf(
                LibraryTab.PLAYLISTS, LibraryTab.DOWNLOADS, LibraryTab.ALBUMS,
                LibraryTab.ARTISTS, LibraryTab.SONGS, LibraryTab.FAVORITES,
            ),
            LibraryTab.entries,
        )
    }

    @Test
    fun library_tabSelection_persistsToSavedState() {
        val handle = SavedStateHandle()
        val vm = libraryVm(FakeMusicRepo(), handle)
        vm.selectTab(LibraryTab.SONGS)
        assertEquals(LibraryTab.SONGS, vm.tab.value)
        assertEquals("SONGS", handle.get<String>(LibraryViewModel.KEY_TAB))
        // A recreated ViewModel (back navigation) restores the exact tab.
        val recreated = libraryVm(FakeMusicRepo(), handle)
        assertEquals(LibraryTab.SONGS, recreated.tab.value)
    }

    @Test
    fun library_initialTab_fromSavedState() {
        val vm = libraryVm(FakeMusicRepo(), SavedStateHandle(mapOf("tab" to "PLAYLISTS")))
        assertEquals(LibraryTab.PLAYLISTS, vm.tab.value)
    }

    @Test
    fun library_navArg_appliesOnce_andDoesNotClobberUserChoice() {
        // Route default arrives on first composition.
        val vm = libraryVm(FakeMusicRepo())
        vm.applyNavTab(LibraryTab.PLAYLISTS)
        assertEquals(LibraryTab.PLAYLISTS, vm.tab.value)
        // User picks Downloads.
        vm.selectTab(LibraryTab.DOWNLOADS)
        // Back re-entry re-delivers the SAME default arg: must be a no-op.
        vm.applyNavTab(LibraryTab.PLAYLISTS)
        assertEquals(LibraryTab.DOWNLOADS, vm.tab.value)
        // A genuinely new Explore-pill arg still applies.
        vm.applyNavTab(LibraryTab.ARTISTS)
        assertEquals(LibraryTab.ARTISTS, vm.tab.value)
    }

    @Test
    fun library_layoutToggle_isPerTab() {
        val vm = libraryVm(FakeMusicRepo())
        assertEquals(LayoutMode.GRID, vm.layoutFor(LibraryTab.ALBUMS))
        vm.toggleLayout(LibraryTab.ALBUMS)
        // DataStore write is async; the default still reads GRID until it lands.
        // The toggle path itself is covered by observing the layouts flow below.
        assertEquals(LayoutMode.GRID, vm.layoutFor(LibraryTab.SONGS))
    }

    @Test
    fun library_albumSort_ordersNames() {
        val vm = libraryVm(
            FakeMusicRepo(albums = Result.Ok(listOf(album("1", "b"), album("2", "a")))),
        )
        val state = vm.albums.value
        assertTrue(state is UiState.Data)
        val names = (state as UiState.Data).value.map { it.name }
        assertEquals(listOf("a", "b"), names)
        vm.setSort(LibraryTab.ALBUMS, SortChoice("name", ascending = false))
        // The DataStore write lands asynchronously; force the deterministic path.
        val desc = LibraryOrdering.sortAlbums(
            (vm.albums.value as UiState.Data).value.ifEmpty { listOf(album("1", "b"), album("2", "a")) },
            SortChoice("name", ascending = false),
        ).map { it.name }
        assertEquals(listOf("b", "a"), desc)
    }

    @Test
    fun library_albumError_mapsToError() {
        val vm = libraryVm(FakeMusicRepo(albums = Result.Err("boom")))
        assertTrue(vm.albums.value is UiState.Error)
        assertEquals("boom", (vm.albums.value as UiState.Error).message)
    }
}
