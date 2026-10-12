package com.feishin.mobile.ui.library

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
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
import org.junit.Test

private fun album(id: String, name: String, year: Int? = null, plays: Long = 0) =
    Album(id, name, "Art", null, null, 1, 60, plays, year, null, false)

class FakeMusicRepo(
    var albums: Result<List<Album>> = Result.Ok(emptyList()),
    var artists: Result<List<Artist>> = Result.Ok(emptyList()),
    var genres: Result<List<Genre>> = Result.Ok(emptyList()),
    var playlists: Result<List<Playlist>> = Result.Ok(emptyList()),
    var starred: Result<StarredCollection> = Result.Ok(StarredCollection()),
) : MusicDataRepository {
    override suspend fun ping() = Result.Ok(Unit)
    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = albums
    override suspend fun artists() = artists
    override suspend fun genres() = genres
    override suspend fun playlists() = playlists
    override suspend fun search(query: String) = Result.Ok(SearchResults())
    override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("x")
    override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("x")
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
    override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
    override fun coverArtUrl(coverArtId: String?, size: Int) = null
    override fun downloadUrl(track: Track) = ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun home_emptyServer_mapsToEmpty() {
        val vm = HomeViewModel(FakeMusicRepo())
        assertTrue(vm.ui.value is UiState.Empty)
    }

    @Test
    fun home_withAlbums_mapsToData() {
        val vm = HomeViewModel(FakeMusicRepo(albums = Result.Ok(listOf(album("1", "N")))))
        val state = vm.ui.value
        assertTrue(state is UiState.Data)
        assertEquals(1, (state as UiState.Data).value.recent.size)
    }

    @Test
    fun library_tabSortAndGrid_transitions() {
        val vm = LibraryViewModel(
            FakeMusicRepo(albums = Result.Ok(listOf(album("1", "b"), album("2", "a")))),
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
    fun library_albumSort_ordersNames() {
        val vm = LibraryViewModel(
            FakeMusicRepo(albums = Result.Ok(listOf(album("1", "b"), album("2", "a")))),
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
        val vm = LibraryViewModel(FakeMusicRepo(albums = Result.Err("boom")))
        assertTrue(vm.albums.value is UiState.Error)
        assertEquals("boom", (vm.albums.value as UiState.Error).message)
    }
}
