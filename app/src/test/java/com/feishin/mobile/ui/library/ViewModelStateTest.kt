package com.feishin.mobile.ui.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
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

    @Test
    fun library_tabSortAndGrid_transitions() {
        val vm = LibraryViewModel(
            FakeMusicRepo(albums = Result.Ok(listOf(album("1", "b"), album("2", "a")))),
            PlaylistEvents(),
            SavedStateHandle(),
        )
        assertEquals(LibraryTab.ALBUMS, vm.tab.value)
        vm.selectTab(LibraryTab.ARTISTS)
        assertEquals(LibraryTab.ARTISTS, vm.tab.value)
        assertEquals(true, vm.grid.value)
        vm.toggleGrid()
        assertEquals(false, vm.grid.value)
        vm.setSort(SortOrder.NAME_DESC)
        assertEquals(SortOrder.NAME_DESC, vm.sort.value)
    }

    @Test
    fun library_initialTab_fromSavedState() {
        val vm = LibraryViewModel(FakeMusicRepo(), PlaylistEvents(), SavedStateHandle(mapOf("tab" to "PLAYLISTS")))
        assertEquals(LibraryTab.PLAYLISTS, vm.tab.value)
    }

    @Test
    fun library_albumSort_ordersNames() {
        val vm = LibraryViewModel(
            FakeMusicRepo(albums = Result.Ok(listOf(album("1", "b"), album("2", "a")))),
            PlaylistEvents(),
            SavedStateHandle(),
        )
        val state = vm.albums.value
        assertTrue(state is UiState.Data)
        val names = (state as UiState.Data).value.map { it.name }
        assertEquals(listOf("a", "b"), names)
        vm.setSort(SortOrder.NAME_DESC)
        val desc = (vm.albums.value as UiState.Data).value.map { it.name }
        assertEquals(listOf("b", "a"), desc)
    }

    @Test
    fun library_albumError_mapsToError() {
        val vm = LibraryViewModel(FakeMusicRepo(albums = Result.Err("boom")), PlaylistEvents(), SavedStateHandle())
        assertTrue(vm.albums.value is UiState.Error)
        assertEquals("boom", (vm.albums.value as UiState.Error).message)
    }
}
