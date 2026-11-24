package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicBackend
import com.feishin.mobile.domain.repo.ScanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FederatedMusicRepositoryTest {

    private fun profile(
        id: String,
        name: String = "Server $id",
        type: BackendType = BackendType.NAVIDROME,
        priority: Int = 0,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = name,
        type = type,
        url = "https://$id.example.com",
        userId = "user",
        credentials = ProfileCredentials.Subsonic("user", "pass"),
        enabled = enabled,
        priority = priority,
    )

    private fun sampleTrack(
        profileId: String,
        rawId: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        durationSec: Int = 200,
        suffix: String? = "mp3",
        bitRate: Int? = 320,
    ) = Track(
        id = "$profileId:$rawId",
        title = title,
        album = album,
        albumId = "$profileId:al1",
        artist = artist,
        artistId = "$profileId:ar1",
        coverArt = "$profileId:cv1",
        durationSec = durationSec,
        trackNo = 1,
        year = 2020,
        genre = "Rock",
        suffix = suffix,
        starred = false,
        bitRate = bitRate,
        sourceProfileId = profileId,
        sourceLabel = "Server $profileId",
    )

    private fun sampleAlbum(
        profileId: String,
        rawId: String,
        name: String,
        artist: String = "Artist",
        year: Int? = 2020,
        playCount: Long = 0,
    ) = Album(
        id = "$profileId:$rawId",
        name = name,
        artist = artist,
        artistId = "$profileId:ar1",
        coverArt = "$profileId:cv-$rawId",
        songCount = 10,
        durationSec = 2000,
        playCount = playCount,
        year = year,
        genre = "Rock",
        starred = false,
        sourceProfileId = profileId,
        sourceLabel = "Server $profileId",
    )

    private class FakeBackend(
        override val profile: ServerProfile,
        var delayMs: Long = 0L,
        var failWith: String? = null,
        var albums: List<Album> = emptyList(),
        var cachedAlbums: List<Album>? = null,
        var artistsList: List<Artist> = emptyList(),
        var cachedArtists: List<Artist>? = null,
        var genresList: List<Genre> = emptyList(),
        var playlistsList: List<Playlist> = emptyList(),
        var topSongsList: List<Track> = emptyList(),
        var starredCollection: StarredCollection = StarredCollection(),
    ) : MusicBackend {
        override val capabilities: BackendCapabilities = BackendCapabilities.forType(profile.type)
        val calls = mutableListOf<String>()

        private suspend fun <T> runOp(op: String, block: () -> T): Result<T> {
            calls += op
            if (delayMs > 0) delay(delayMs)
            val err = failWith
            return if (err != null) Result.Err(err) else Result.Ok(block())
        }

        override suspend fun ping(): Result<Unit> = runOp("ping") { }
        override suspend fun serverInfo(): Result<ServerInfo> = runOp("serverInfo") { ServerInfo(product = profile.name) }
        override suspend fun startScan(): Result<Unit> = runOp("startScan") { }
        override suspend fun scanStatus(): Result<ScanStatus> = runOp("scanStatus") { ScanStatus() }
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> =
            runOp("albumList:$type") { albums }
        override suspend fun artists(): Result<List<Artist>> = runOp("artists") { artistsList }
        override suspend fun genres(): Result<List<Genre>> = runOp("genres") { genresList }
        override suspend fun playlists(): Result<List<Playlist>> = runOp("playlists") { playlistsList }
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults> =
            runOp("search:$query") { SearchResults(albums = albums, tracks = topSongsList) }
        override suspend fun album(id: String): Result<AlbumDetail> = runOp("album:$id") {
            AlbumDetail(id, "Album $id", "Artist", null, null, 1, 200, 2020, "Rock", false, emptyList())
        }
        override suspend fun artist(id: String): Result<ArtistDetail> = runOp("artist:$id") {
            ArtistDetail(id, "Artist $id", null, 1, false, albums)
        }
        override suspend fun artistInfo(id: String): Result<ArtistInfo> = runOp("artistInfo:$id") { ArtistInfo() }
        override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> =
            runOp("randomSongs") { topSongsList }
        override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> =
            runOp("topSongs:$artist") { topSongsList }
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> =
            runOp("similarSongs:${songId ?: artistId}") { topSongsList }
        override suspend fun playlist(id: String): Result<PlaylistDetail> = runOp("playlist:$id") {
            PlaylistDetail(id, "Playlist $id", 0, 0, emptyList())
        }
        override suspend fun starred(): Result<StarredCollection> = runOp("starred") { starredCollection }
        override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = runOp("starTrack:$id:$starred") { }
        override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = runOp("starAlbum:$id:$starred") { }
        override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = runOp("starArtist:$id:$starred") { }
        override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> =
            runOp("createPlaylist:$name:${trackIds.joinToString(",")}") { "${profile.id}:pl-new" }
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
            runOp("addToPlaylist:$playlistId:${trackIds.joinToString(",")}") { }
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> =
            runOp("removeFromPlaylist:$playlistId") { }
        override suspend fun deletePlaylist(id: String): Result<Unit> = runOp("deletePlaylist:$id") { }
        override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = runOp("scrobble:$id") { }
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> =
            runOp("scrobbleAt:$id:$timeMs") { }
        override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> =
            runOp("lyricsForTrack:$id") { SyncedLyrics.EMPTY }
        override suspend fun lyrics(artist: String, title: String): Result<String> = runOp("lyrics") { "" }
        override fun streamUrl(track: Track, maxBitrate: Int, format: String): String =
            "${profile.url}/stream?id=${track.id}"
        override fun coverArtUrl(coverArtId: String?, size: Int): String? =
            coverArtId?.let { "${profile.url}/cover?id=$it&size=$size" }
        override fun downloadUrl(track: Track): String = "${profile.url}/download?id=${track.id}"

        override suspend fun readCachedAlbumList(type: String, offset: Int, genre: String?): List<Album>? = cachedAlbums
        override suspend fun readCachedArtists(): List<Artist>? = cachedArtists
    }

    @Test
    fun timeoutIsolation_marksSlowBackendDegradedAndServesFastPlusStaleCache() = runTest {
        val p1 = profile("p1", priority = 0)
        val p2 = profile("p2", priority = 1)
        val fast = FakeBackend(
            profile = p1,
            delayMs = 50L,
            artistsList = listOf(Artist("p1:ar1", "Alpha", null, 2, false)),
        )
        val slow = FakeBackend(
            profile = p2,
            delayMs = 10_000L, // Exceeds 4s federatedTimeoutMs
            artistsList = listOf(Artist("p2:ar-live", "Live Slow", null, 1, false)),
            cachedArtists = listOf(Artist("p2:ar-cached", "Beta Cached", null, 3, false)),
        )
        val refresh = LibraryRefresh()
        val staleEvents = mutableListOf<String>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            refresh.stale.collect { staleEvents += it }
        }

        val repo = FederatedMusicRepository(
            profilesFlow = MutableStateFlow(listOf(p1, p2)),
            backendFactory = { p, _ -> if (p.id == "p1") fast else slow },
            settingsProvider = { SettingsStore.Settings(federatedTimeoutMs = 4_000L) },
            refresh = refresh,
            scope = backgroundScope,
        )

        val res = repo.artists()
        advanceUntilIdle()
        collectJob.cancel()

        assertTrue(res is Result.Ok)
        val names = (res as Result.Ok).value.map { it.name }
        assertEquals(listOf("Alpha", "Beta Cached"), names)

        val health = repo.healthState.value
        assertFalse(health.profiles["p1"]!!.degraded)
        assertTrue(health.profiles["p2"]!!.degraded)
        assertTrue(health.hasDegraded)
        assertEquals(listOf(MetadataCacheKeys.ARTISTS), staleEvents)
    }

    @Test
    fun partialFailure_returnsOkWhenOneBackendSucceedsOrHasCache_andErrOnlyWhenAllFailEmpty() = runTest {
        val p1 = profile("p1", priority = 0)
        val p2 = profile("p2", priority = 1)
        val failingWithCache = FakeBackend(
            profile = p1,
            failWith = "Connection refused",
            cachedAlbums = listOf(sampleAlbum("p1", "al-cached", "Cached Album")),
        )
        val failingEmpty = FakeBackend(
            profile = p2,
            failWith = "DNS error",
            cachedAlbums = null,
        )
        val refresh = LibraryRefresh()
        val repo = FederatedMusicRepository(
            profilesFlow = MutableStateFlow(listOf(p1, p2)),
            backendFactory = { p, _ -> if (p.id == "p1") failingWithCache else failingEmpty },
            settingsProvider = { SettingsStore.Settings() },
            refresh = refresh,
            scope = backgroundScope,
        )

        // All backends fail network, but p1 has non-empty stale cache -> Ok!
        val withCache = repo.albumList("recent", size = 10)
        assertTrue(withCache is Result.Ok)
        assertEquals(listOf("p1:al-cached"), (withCache as Result.Ok).value.map { it.id })

        // Now clear p1's cache -> ALL backends fail with zero cached items -> Err!
        failingWithCache.cachedAlbums = emptyList()
        val allFailedEmpty = repo.albumList("recent", size = 10)
        assertTrue(allFailedEmpty is Result.Err)
    }

    @Test
    fun topSongs_prioritizesPreferredProfileAndDeduplicatesAcrossBackends() = runTest {
        val p1 = profile("p1", priority = 0)
        val p2 = profile("p2", priority = 1)
        val b1 = FakeBackend(
            profile = p1,
            topSongsList = listOf(
                sampleTrack("p1", "s1", "Shared Hit", bitRate = 320),
                sampleTrack("p1", "s2", "Only On P1", bitRate = 320),
            ),
        )
        val b2 = FakeBackend(
            profile = p2,
            topSongsList = listOf(
                sampleTrack("p2", "s9", "Shared Hit", bitRate = 312), // Within 16 kbps -> preferred p2 wins!
                sampleTrack("p2", "s8", "Only On P2", bitRate = 320),
            ),
        )
        val repo = FederatedMusicRepository(
            profilesFlow = MutableStateFlow(listOf(p1, p2)),
            backendFactory = { p, _ -> if (p.id == "p1") b1 else b2 },
            settingsProvider = { SettingsStore.Settings() },
            refresh = LibraryRefresh(),
            scope = backgroundScope,
        )

        val res = repo.topSongs("Radiohead", count = 10, preferredProfileId = "p2")
        assertTrue(res is Result.Ok)
        val ids = (res as Result.Ok).value.map { it.id }
        assertEquals(listOf("p2:s9", "p2:s8", "p1:s2"), ids)
    }

    @Test
    fun deviceSourceFilter_treatedAsAllSoHomeAndSearchNeverBlankOut_whileTypeAndProfileFiltersNarrow() = runTest {
        val p1 = profile("p1", type = BackendType.NAVIDROME, priority = 0)
        val p2 = profile("p2", type = BackendType.OPENSUBSONIC, priority = 1)
        val b1 = FakeBackend(p1, albums = listOf(sampleAlbum("p1", "a1", "Nav Album")))
        val b2 = FakeBackend(p2, albums = listOf(sampleAlbum("p2", "a2", "OpenSub Album")))
        var activeFilter = "DEVICE"

        val repo = FederatedMusicRepository(
            profilesFlow = MutableStateFlow(listOf(p1, p2)),
            backendFactory = { p, _ -> if (p.id == "p1") b1 else b2 },
            settingsProvider = { SettingsStore.Settings(librarySourceFilter = activeFilter) },
            refresh = LibraryRefresh(),
            scope = backgroundScope,
        )

        val deviceResult = repo.albumList("recent") as Result.Ok
        assertEquals(setOf("p1:a1", "p2:a2"), deviceResult.value.map { it.id }.toSet())

        activeFilter = "TYPE:OPENSUBSONIC"
        val typeResult = repo.albumList("recent") as Result.Ok
        assertEquals(listOf("p2:a2"), typeResult.value.map { it.id })

        activeFilter = "PROFILE:p1"
        val profileResult = repo.albumList("recent") as Result.Ok
        assertEquals(listOf("p1:a1"), profileResult.value.map { it.id })
    }

    @Test
    fun globalIdRouting_virtualFavorites_genresMerge_andLogicalLibraryRefreshInvalidation() = runTest {
        val p1 = profile("p1", priority = 0)
        val p2 = profile("p2", priority = 1)
        val b1 = FakeBackend(
            profile = p1,
            genresList = listOf(Genre("Rock", songCount = 10, albumCount = 2)),
            starredCollection = StarredCollection(tracks = listOf(sampleTrack("p1", "t1", "Fav One"))),
        )
        val b2 = FakeBackend(
            profile = p2,
            genresList = listOf(Genre("rock", songCount = 5, albumCount = 1), Genre("Jazz", songCount = 7, albumCount = 2)),
            starredCollection = StarredCollection(tracks = listOf(sampleTrack("p2", "t2", "Fav Two"))),
        )
        val profiles = MutableStateFlow(listOf(p1, p2))
        val refresh = LibraryRefresh()
        val repo = FederatedMusicRepository(
            profilesFlow = profiles,
            backendFactory = { p, _ -> if (p.id == "p1") b1 else b2 },
            settingsProvider = { SettingsStore.Settings() },
            refresh = refresh,
            scope = backgroundScope,
        )

        // Single-item GlobalId dispatch goes strictly to the owning backend
        assertTrue(repo.album("p2:al-99") is Result.Ok)
        assertEquals(listOf("album:p2:al-99"), b2.calls)
        assertTrue(b1.calls.isEmpty())

        assertEquals("https://p2.example.com/stream?id=p2:s7", repo.streamUrl(sampleTrack("p2", "s7", "S")))
        assertEquals("https://p1.example.com/cover?id=p1:cv1&size=256", repo.coverArtUrl("p1:cv1", 256))
        assertEquals("https://p2.example.com/download?id=p2:s7", repo.downloadUrl(sampleTrack("p2", "s7", "S")))

        // Genres merge case-insensitively summing songCount & albumCount
        val genres = (repo.genres() as Result.Ok).value
        assertEquals(
            listOf(Genre("Jazz", songCount = 7, albumCount = 2), Genre("Rock", songCount = 15, albumCount = 3)),
            genres,
        )

        // Virtual __favorites__ playlist aggregates starred across profiles
        val favPl = (repo.playlist(FAVORITE_SONGS_PLAYLIST_ID) as Result.Ok).value
        assertEquals(FAVORITE_SONGS_PLAYLIST_ID, favPl.id)
        assertEquals(listOf("p1:t1", "p2:t2"), favPl.tracks.map { it.id })

        // Logical invalidation via mutations wakes BOTH profiles independently
        assertTrue(repo.starTrack("p2:t2", true) is Result.Ok)
        assertTrue(
            refresh.policy.consumeForced(
                MetadataCacheKeys.scoped("p1", MetadataCacheKeys.STARRED),
                MetadataCacheKeys.STARRED,
            ),
        )
        assertTrue(
            refresh.policy.consumeForced(
                MetadataCacheKeys.scoped("p2", MetadataCacheKeys.STARRED),
                MetadataCacheKeys.STARRED,
            ),
        )
        assertFalse(
            refresh.policy.consumeForced(
                MetadataCacheKeys.scoped("p2", MetadataCacheKeys.STARRED),
                MetadataCacheKeys.STARRED,
            ),
        )

        // Profile removal updates BackendRegistry and prunes healthState immediately
        assertNotNull(repo.get("p2"))
        profiles.value = listOf(p1)
        assertNull(repo.get("p2"))
        assertEquals(listOf("p1"), repo.all().map { it.profile.id })
    }
}
