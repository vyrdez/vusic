package com.feishin.mobile.playback

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun track(id: String, artist: String = "Ar", artistId: String? = "ar", genre: String? = "Music") = Track(
    id = id, title = "T$id", album = "Al", albumId = "al",
    artist = artist, artistId = artistId, coverArt = "c",
    durationSec = 200, trackNo = 1, year = 2024, genre = genre,
    suffix = "flac", starred = false,
)

/** Fake that logs call order and serves genre/plain randoms separately. */
private class RadioFakeRepo(
    var similar: List<Track> = emptyList(),
    var genreRandom: List<Track> = emptyList(),
    var plainRandom: List<Track> = emptyList(),
    var failSimilar: Boolean = false,
    var failAll: Boolean = false,
) : MusicDataRepository {
    val calls = ArrayList<String>()
    override suspend fun ping() = Result.Ok(Unit)
    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<com.feishin.mobile.domain.model.Album>())
    override suspend fun artists() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Artist>())
    override suspend fun genres() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Genre>())
    override suspend fun playlists() = Result.Ok(emptyList<com.feishin.mobile.domain.model.Playlist>())
    override suspend fun search(query: String) = Result.Ok(SearchResults())
    override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("x")
    override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("x")
    override suspend fun artistInfo(id: String) = Result.Ok(ArtistInfo())
    override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> {
        calls += if (genre != null) "genre:$genre" else "random"
        if (failAll) return Result.Err("boom")
        return Result.Ok(if (genre != null) genreRandom else plainRandom)
    }
    override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
    override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> {
        calls += "similar"
        if (failAll || failSimilar) return Result.Err("boom")
        return Result.Ok(similar)
    }
    override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("x")
    override suspend fun starred() = Result.Ok(StarredCollection())
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
    override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Ok(SyncedLyrics.plain(""))
    override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
    override fun coverArtUrl(coverArtId: String?, size: Int) = null
    override fun downloadUrl(track: Track) = ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class RadioFetcherTest {
    private val seed = track("seed", artist = "Seed Artist", artistId = "seed-ar", genre = "Rock")

    @Test
    fun similarFillsBatch_noFurtherCalls() = runTest {
        val repo = RadioFakeRepo(
            similar = (1..30).map { track("s$it", artist = "A$it", artistId = "a$it") },
            genreRandom = (1..30).map { track("g$it") },
            plainRandom = (1..30).map { track("r$it") },
        )
        val out = (RadioFetcher(repo).fetchBatch(seed, setOf("seed")) as Result.Ok).value
        assertEquals(20, out.size)
        assertEquals(listOf("similar"), repo.calls)
        assertTrue(out.none { it.id == "seed" })
    }

    @Test
    fun fallbackOrder_similarThenGenreThenRandom() = runTest {
        val repo = RadioFakeRepo(
            similar = listOf(track("s1")),
            genreRandom = listOf(track("g1")),
            plainRandom = listOf(track("r1")),
        )
        val out = (RadioFetcher(repo).fetchBatch(seed, setOf("seed"), want = 3) as Result.Ok).value
        assertEquals(listOf("similar", "genre:Rock", "random"), repo.calls)
        assertEquals(3, out.size)
    }

    @Test
    fun emptySimilar_fallsThroughSilently() = runTest {
        val repo = RadioFakeRepo(
            similar = emptyList(),
            genreRandom = (1..25).map { track("g$it") },
        )
        val out = (RadioFetcher(repo).fetchBatch(seed, emptySet()) as Result.Ok).value
        assertEquals(20, out.size)
        assertEquals(listOf("similar", "genre:Rock"), repo.calls)
    }

    @Test
    fun emptyLibrary_returnsEmpty() = runTest {
        val repo = RadioFakeRepo()
        val out = (RadioFetcher(repo).fetchBatch(seed, emptySet()) as Result.Ok).value
        assertTrue(out.isEmpty())
        assertEquals(listOf("similar", "genre:Rock", "random"), repo.calls)
    }

    @Test
    fun allSourcesFail_returnsErr() = runTest {
        val repo = RadioFakeRepo()
        repo.failAll = true
        val r = RadioFetcher(repo).fetchBatch(seed, emptySet())
        assertTrue(r is Result.Err)
    }

    @Test
    fun partialFailure_stillFillsFromWorkingSources() = runTest {
        val repo = RadioFakeRepo(
            genreRandom = (1..25).map { track("g$it") },
        )
        repo.failSimilar = true
        val out = (RadioFetcher(repo).fetchBatch(seed, emptySet()) as Result.Ok).value
        assertEquals(20, out.size)
        assertEquals(listOf("similar", "genre:Rock"), repo.calls)
    }

    @Test
    fun dedupsAgainstQueueAndHistory() = runTest {
        val repo = RadioFakeRepo(
            similar = listOf(track("s1"), track("old")),
            plainRandom = listOf(track("s1"), track("new")),
        )
        val out = (
            RadioFetcher(repo).fetchBatch(
                seed.copy(genre = null), setOf("old"), want = 20,
            ) as Result.Ok
            ).value
        val ids = out.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("old" !in ids)
        assertEquals(1, ids.count { it == "s1" })
        assertTrue("new" in ids)
    }
}
