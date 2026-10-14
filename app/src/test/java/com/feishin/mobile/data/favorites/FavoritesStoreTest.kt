package com.feishin.mobile.data.favorites

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun track(id: String) = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = "Ar", artistId = "ar", coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = "Music",
    suffix = "flac", starred = true,
)

private class FavFakeRepo : MusicDataRepository {
    var starredTracks: List<Track> = emptyList()
    var starShouldFail = false
    val starCalls = ArrayList<Pair<String, Boolean>>()
    override suspend fun ping() = Result.Ok(Unit)
    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<com.feishin.mobile.domain.model.Album>())
    override suspend fun artists() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Artist>())
    override suspend fun genres() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Genre>())
    override suspend fun playlists() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Playlist>())
    override suspend fun search(query: String) = Result.Ok(SearchResults())
    override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) =
        Result.Ok(SearchResults())
    override suspend fun serverInfo() = Result.Ok(com.feishin.mobile.domain.model.ServerInfo())
    override suspend fun startScan() = Result.Ok(Unit)
    override suspend fun scanStatus() = Result.Ok(com.feishin.mobile.domain.model.ScanStatus())
    override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("x")
    override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("x")
    override suspend fun artistInfo(id: String) = Result.Ok(ArtistInfo())
    override suspend fun randomSongs(size: Int, genre: String?) = Result.Ok(emptyList<Track>())
    override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = Result.Ok(emptyList<Track>())
    override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("x")
    override suspend fun starred(): Result<StarredCollection> = Result.Ok(StarredCollection(tracks = starredTracks))
    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> {
        starCalls += id to starred
        return if (starShouldFail) Result.Err("nope") else Result.Ok(Unit)
    }
    override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
    override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
    override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Ok(Unit)
    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
    override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
    override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
    override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean) = Result.Ok(Unit)
    override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
    override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Ok(SyncedLyrics.plain(""))
    override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
    override fun coverArtUrl(coverArtId: String?, size: Int) = null
    override fun downloadUrl(track: Track) = ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStoreTest {

    @Test
    fun star_syncsCollectionAndEmits() = runTest {
        val repo = FavFakeRepo()
        val events = PlaylistEvents()
        val store = FavoritesStore(repo, events)
        val collected = mutableListOf<PlaylistEvent>()
        val job = launch { events.events.collect { collected += it } }
        runCurrent()
        repo.starredTracks = listOf(track("a"), track("b"))
        assertEquals(Result.Ok(Unit), store.setTrackStarred("a", true))
        runCurrent()
        job.cancel()
        assertEquals(listOf("a"), repo.starCalls.map { it.first })
        assertEquals(listOf("a", "b"), store.tracks.value.map { it.id })
        assertEquals(listOf(PlaylistEvent.FavoritesChanged), collected)
    }

    @Test
    fun unstar_removesFromCollection() = runTest {
        val repo = FavFakeRepo()
        repo.starredTracks = listOf(track("a"), track("b"))
        val store = FavoritesStore(repo, PlaylistEvents())
        store.refresh()
        assertEquals(2, store.tracks.value.size)
        repo.starredTracks = listOf(track("b"))
        store.setTrackStarred("a", false)
        assertEquals(listOf("b"), store.tracks.value.map { it.id })
    }

    @Test
    fun starFailure_noSyncNoEvent() = runTest {
        val repo = FavFakeRepo()
        repo.starShouldFail = true
        val events = PlaylistEvents()
        val store = FavoritesStore(repo, events)
        val collected = mutableListOf<PlaylistEvent>()
        val job = launch { events.events.collect { collected += it } }
        runCurrent()
        val r = store.setTrackStarred("a", true)
        runCurrent()
        job.cancel()
        assertTrue(r is Result.Err)
        assertTrue(store.tracks.value.isEmpty())
        assertTrue(collected.isEmpty())
    }
}
