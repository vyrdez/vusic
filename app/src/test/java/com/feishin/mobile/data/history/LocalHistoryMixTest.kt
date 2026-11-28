package com.feishin.mobile.data.history

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.MixGenerator
import com.feishin.mobile.core.common.OfflineRadio
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.ScrobblePolicy
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.backend.DriveLibraryIndexer
import com.feishin.mobile.data.backend.GoogleDriveBackend
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import com.feishin.mobile.data.db.DriveAlbumRow
import com.feishin.mobile.data.db.DriveArtistRow
import com.feishin.mobile.data.db.DriveFileModTime
import com.feishin.mobile.data.db.DriveGenreRow
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.db.LocalAlbumPlayStats
import com.feishin.mobile.data.db.LocalFavoriteDao
import com.feishin.mobile.data.db.LocalFavoriteEntity
import com.feishin.mobile.data.db.LocalPlayHistoryDao
import com.feishin.mobile.data.db.LocalPlayHistoryEntity
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveCoverTranscoder
import com.feishin.mobile.data.remote.drive.DriveRangeReader
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
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
import com.feishin.mobile.playback.PlayerService
import com.feishin.mobile.playback.RadioFetcher
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class LocalHistoryMixTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun scrobblePolicyThreshold_onDriveTrack_recordsLocalHistoryWithZeroNetworkScrobbles() = runTest {
        val historyDao = FakeLocalPlayHistoryDao()
        val historyStore = LocalHistoryStore(historyDao, json)
        val repo = RecordingRepo()
        val policy = ScrobblePolicy()

        val driveTrack = Track(
            id = "gd1:file42",
            title = "Midnight Drive",
            album = "Neon Nights",
            albumId = "gd1:al:abc123",
            artist = "Synth Pilot",
            artistId = "gd1:ar:xyz789",
            coverArt = "gd1:cov:file42",
            durationSec = 200,
            trackNo = 1,
            year = 2024,
            genre = "Synthwave",
            suffix = "flac",
            starred = false,
            sourceProfileId = "gd1",
            sourceLabel = "My Drive",
        )

        // Advance continuously by 2s ticks up to 50% (100_000 ms of 200_000 ms).
        var firedCount = 0
        for (posMs in 2_000L..98_000L step 2_000L) {
            if (policy.onProgress(driveTrack.id, posMs, 200_000L, isPlaying = true)) {
                firedCount++
            }
        }
        assertEquals(0, firedCount)
        assertTrue(historyDao.entries.isEmpty())

        assertTrue(policy.onProgress(driveTrack.id, 100_000L, 200_000L, isPlaying = true))
        PlayerService.submitPlayOrRecordLocal(
            id = driveTrack.id,
            trackProvider = { driveTrack },
            repo = repo,
            localHistoryStore = historyStore,
        )

        // Second tick past threshold must not re-fire.
        assertFalse(policy.onProgress(driveTrack.id, 102_000L, 200_000L, isPlaying = true))
        assertEquals(0, repo.scrobbleCalls.size)
        assertEquals(1, historyDao.entries.size)
        val row = historyDao.entries.single()
        assertEquals("gd1", row.profileId)
        assertEquals("gd1:file42", row.trackId)
        assertEquals(driveTrack.albumId, row.albumKey)
        assertEquals(driveTrack.artistId, row.artistKey)

        // Long track (10m) also triggers at the 4-minute cap, and Subsonic still uses network scrobble.
        policy.reset()
        val longDrive = driveTrack.copy(id = "gd1:long1", durationSec = 600)
        for (posMs in 2_000L..238_000L step 2_000L) {
            assertFalse(policy.onProgress(longDrive.id, posMs, 600_000L, isPlaying = true))
        }
        assertTrue(policy.onProgress(longDrive.id, 240_000L, 600_000L, isPlaying = true))
        PlayerService.submitPlayOrRecordLocal(
            id = longDrive.id,
            trackProvider = { longDrive },
            repo = repo,
            localHistoryStore = historyStore,
        )
        assertEquals(0, repo.scrobbleCalls.size)
        assertEquals(2, historyDao.entries.size)

        val subsonicTrack = driveTrack.copy(id = "p1:s99", albumId = "p1:al:99", artistId = "p1:ar:99", sourceProfileId = "p1")
        policy.reset()
        for (posMs in 2_000L..100_000L step 2_000L) {
            if (policy.onProgress(subsonicTrack.id, posMs, 200_000L, isPlaying = true)) {
                PlayerService.submitPlayOrRecordLocal(
                    id = subsonicTrack.id,
                    trackProvider = { subsonicTrack },
                    repo = repo,
                    localHistoryStore = historyStore,
                )
            }
        }
        assertEquals(listOf("p1:s99" to true), repo.scrobbleCalls)
        assertEquals(2, historyDao.entries.size)
    }

    @Test
    fun driveBackendRecentAndFrequent_populateAlbumPlayCountFromLocalHistory() = runTest {
        val historyDao = FakeLocalPlayHistoryDao()
        val historyStore = LocalHistoryStore(historyDao, json)
        val trackDao = FakeDriveTrackDao(historyDao)

        val tAlpha = driveEntity("gd1", "s1", "Alpha Track", "Alpha Artist", "Alpha Album", "gd1:al:alpha", "gd1:ar:alpha", "Jazz", 2023)
        val tBeta = driveEntity("gd1", "s2", "Beta Track", "Beta Artist", "Beta Album", "gd1:al:beta", "gd1:ar:beta", "Rock", 2022)
        trackDao.upsertAll(listOf(tAlpha, tBeta))

        val domainAlpha = tAlpha.toTestTrack()
        val domainBeta = tBeta.toTestTrack()

        // Alpha played 3 times earlier; Beta played 1 time most recently.
        historyStore.recordPlay(domainAlpha, playedAtMs = 1_000L)
        historyStore.recordPlay(domainAlpha, playedAtMs = 2_000L)
        historyStore.recordPlay(domainAlpha, playedAtMs = 3_000L)
        historyStore.recordPlay(domainBeta, playedAtMs = 5_000L)

        val backend = createBackend(backgroundScope, trackDao = trackDao)

        val recent = (backend.albumList("recent", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:beta", "gd1:al:alpha"), recent.map { it.id })
        assertEquals(1L, recent[0].playCount)
        assertEquals(3L, recent[1].playCount)

        val frequent = (backend.albumList("frequent", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha", "gd1:al:beta"), frequent.map { it.id })
        assertEquals(3L, frequent[0].playCount)
        assertEquals(1L, frequent[1].playCount)
    }

    @Test
    fun mixGenerator_producesFiveMixesFromDriveTracksAndLocalPlayHistory() = runTest {
        val historyDao = FakeLocalPlayHistoryDao()
        val historyStore = LocalHistoryStore(historyDao, json)
        val trackDao = FakeDriveTrackDao(historyDao)

        val genres = listOf("Rock", "Jazz", "Electronic", "Ambient", "Folk")
        val entities = (1..25).map { idx ->
            val artistIdx = (idx - 1) % 5 + 1
            val albumIdx = (idx - 1) % 5 + 1
            driveEntity(
                profileId = "gd1",
                fileId = "f$idx",
                title = "Drive Song $idx",
                artist = "Drive Artist $artistIdx",
                album = "Drive Album $albumIdx",
                albumKey = "gd1:al:a$albumIdx",
                artistKey = "gd1:ar:r$artistIdx",
                genre = genres[(idx - 1) % genres.size],
                year = 2010 + (idx % 15),
            )
        }
        trackDao.upsertAll(entities)

        // Play Artist 1 / Album 1 multiple times so history drives affinity.
        val topTrack = entities.first().toTestTrack()
        repeat(4) { i ->
            historyStore.recordPlay(topTrack, playedAtMs = 1_000L + i * 1_000L)
        }

        val backend = createBackend(backgroundScope, trackDao = trackDao)
        val randomSongs = (backend.randomSongs(size = 50) as Result.Ok).value
        val recentAlbums = (backend.albumList("recent", size = 20) as Result.Ok).value
        val frequentAlbums = (backend.albumList("frequent", size = 20) as Result.Ok).value

        val mixes = MixGenerator.generate(
            randomSongs = randomSongs,
            frequentAlbums = frequentAlbums,
            recentAlbums = recentAlbums,
            batchSize = 10,
            seed = 42L,
        )

        assertEquals(listOf("My Mix 1", "My Mix 2", "My Mix 3", "My Mix 4", "My Mix 5"), mixes.map { it.name })
        assertTrue(mixes.all { it.tracks.isNotEmpty() })
        assertTrue(mixes[0].tracks.any { it.artistId == topTrack.artistId })
    }

    @Test
    fun offlineRadio_andRadioFetcher_mixDriveTracksAndDownloadedTracksWithoutDuplicates() = runTest {
        val historyDao = FakeLocalPlayHistoryDao()
        val trackDao = FakeDriveTrackDao(historyDao)

        val driveRock1 = driveEntity("gd1", "d1", "Drive Rock 1", "Band A", "Al 1", "gd1:al:1", "gd1:ar:1", "Rock", 2022)
        val driveRock2 = driveEntity("gd1", "d2", "Drive Rock 2", "Band B", "Al 2", "gd1:al:2", "gd1:ar:2", "Rock", 2023)
        val driveJazz = driveEntity("gd1", "d3", "Drive Jazz", "Band C", "Al 3", "gd1:al:3", "gd1:ar:3", "Jazz", 2021)
        val seedEntity = driveEntity("gd1", "seed", "Seed Rock", "Band S", "Al S", "gd1:al:s", "gd1:ar:s", "Rock", 2024)
        trackDao.upsertAll(listOf(seedEntity, driveRock1, driveRock2, driveJazz))

        val downloaded = listOf(
            // Overlapping ID with driveRock1 (downloaded copy should win without duplication)
            driveRock1.toTestTrack().copy(path = "/storage/emulated/0/d1.flac"),
            Track(
                id = "p1:dl_rock",
                title = "Downloaded Rock",
                album = "Sub Al",
                albumId = "p1:al:sub1",
                artist = "Sub Band",
                artistId = "p1:ar:sub1",
                coverArt = null,
                durationSec = 190,
                trackNo = 1,
                year = 2020,
                genre = "Rock",
                suffix = "mp3",
                starred = false,
                path = "/storage/emulated/0/sub_rock.mp3",
            ),
            Track(
                id = "p1:dl_pop",
                title = "Downloaded Pop",
                album = "Sub Pop",
                albumId = "p1:al:sub2",
                artist = "Pop Band",
                artistId = "p1:ar:sub2",
                coverArt = null,
                durationSec = 180,
                trackNo = 1,
                year = 2021,
                genre = "Pop",
                suffix = "mp3",
                starred = false,
                path = "/storage/emulated/0/sub_pop.mp3",
            ),
        )
        val driveTracks = listOf(driveRock1, driveRock2, driveJazz).map { it.toTestTrack() }

        val picked = OfflineRadio.pick(
            downloaded = downloaded,
            driveTracks = driveTracks,
            seedGenre = "Rock",
            excludeIds = setOf("gd1:seed"),
            want = 10,
        )
        assertEquals(listOf("gd1:d1", "p1:dl_rock", "gd1:d2", "p1:dl_pop", "gd1:d3"), picked.map { it.id })
        assertEquals(picked.size, picked.map { it.id }.toSet().size)
        assertEquals("/storage/emulated/0/d1.flac", picked.first { it.id == "gd1:d1" }.path)

        // RadioFetcher routes !supportsRadioMix (Drive) seeds locally and skips network similar/random.
        val repo = RecordingRepo()
        val fetcher = RadioFetcher.forTest(
            repo = repo,
            driveTrackDao = trackDao,
        )
        val localBatch = fetcher.fetchLocalBatch(
            seed = seedEntity.toTestTrack(),
            excludeIds = setOf("gd1:seed"),
            want = 10,
            includeDriveTracks = true,
            downloadedOverride = downloaded,
        )
        assertEquals(listOf("gd1:d1", "p1:dl_rock", "gd1:d2", "p1:dl_pop", "gd1:d3"), localBatch.map { it.id })

        val onlineDriveBatch = (fetcher.fetchBatch(seedEntity.toTestTrack(), setOf("gd1:seed"), want = 10) as Result.Ok).value
        assertEquals(setOf("gd1:d1", "gd1:d2", "gd1:d3"), onlineDriveBatch.map { it.id }.toSet())
        assertEquals(0, repo.similarCalls)
        assertEquals(0, repo.randomCalls)
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun createBackend(
        scope: CoroutineScope,
        trackDao: FakeDriveTrackDao,
    ): GoogleDriveBackend {
        val profile = ServerProfile(
            id = "gd1",
            name = "My Drive",
            type = BackendType.GOOGLE_DRIVE,
            url = "https://www.googleapis.com",
            credentials = ProfileCredentials.GoogleDrive(
                accountEmail = "user@gmail.com",
                accessToken = "drive-token",
            ),
        )
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val profileStore = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
        )
        profileStore.saveAll(listOf(profile))

        val client = OkHttpClient.Builder().build()
        val api = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriveApi::class.java)
        val coversDir = tmp.newFolder("covers_${UUID.randomUUID()}")
        val rangeReader = DriveRangeReader(
            client = client,
            coversDirProvider = { coversDir },
            transcoder = DriveCoverTranscoder { bytes, _ -> bytes },
            fileUrlBuilder = { fileId -> "https://www.googleapis.com/drive/v3/files/$fileId?alt=media" },
        )
        val indexer = DriveLibraryIndexer(
            driveApiProvider = { api },
            profileStore = profileStore,
            driveRangeReader = rangeReader,
            driveTrackDao = trackDao,
            cachedCoverDao = FakeCachedCoverDao(),
            settingsStore = settings,
            cancellationRegistry = DriveScanCancellationRegistry(),
            json = json,
            isNetworkMetered = { false },
            externalScope = scope,
        )
        return GoogleDriveBackend(
            profile = profile,
            api = api,
            driveTrackDao = trackDao,
            localFavoriteDao = FakeLocalFavoriteDao(),
            indexer = indexer,
            json = json,
        )
    }

    private fun driveEntity(
        profileId: String,
        fileId: String,
        title: String,
        artist: String,
        album: String,
        albumKey: String,
        artistKey: String,
        genre: String?,
        year: Int?,
    ): DriveTrackEntity = DriveTrackEntity(
        songId = GlobalId.format(profileId, fileId),
        profileId = profileId,
        driveFileId = fileId,
        fileName = "$fileId.flac",
        title = title,
        artist = artist,
        album = album,
        albumKey = albumKey,
        artistKey = artistKey,
        year = year,
        genre = genre,
        durationSec = 200,
        suffix = "flac",
        coverArtId = "$profileId:cov:$fileId",
    )

    private fun DriveTrackEntity.toTestTrack(): Track = Track(
        id = songId,
        title = title,
        album = album,
        albumId = albumKey,
        artist = artist,
        artistId = artistKey,
        coverArt = coverArtId,
        durationSec = durationSec,
        trackNo = trackNo,
        year = year,
        genre = genre,
        suffix = suffix,
        starred = false,
        sourceProfileId = profileId,
        sourceLabel = "My Drive",
    )

    private class RecordingRepo : MusicDataRepository {
        val scrobbleCalls = ArrayList<Pair<String, Boolean>>()
        var similarCalls = 0
        var randomCalls = 0

        override fun capabilitiesFor(id: String): BackendCapabilities = when {
            GlobalId.profileIdOrNull(id)?.startsWith("gd") == true -> BackendCapabilities.forType(BackendType.GOOGLE_DRIVE)
            GlobalId.isDeviceLocal(id) -> BackendCapabilities.DEVICE_LOCAL
            else -> BackendCapabilities.forType(BackendType.NAVIDROME)
        }

        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<Album>())
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) = Result.Ok(SearchResults())
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) = Result.Ok(SearchResults())
        override suspend fun serverInfo() = Result.Ok(ServerInfo())
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus())
        override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("unused")
        override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("unused")
        override suspend fun artistInfo(id: String) = Result.Ok(ArtistInfo())
        override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> {
            randomCalls++
            return Result.Ok(emptyList())
        }
        override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> {
            similarCalls++
            return Result.Ok(emptyList())
        }
        override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("unused")
        override suspend fun starred() = Result.Ok(StarredCollection())
        override suspend fun starTrack(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Ok("pl1")
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> {
            scrobbleCalls += id to submission
            return Result.Ok(Unit)
        }
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> {
            scrobbleCalls += id to submission
            return Result.Ok(Unit)
        }
        override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
        override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Ok(SyncedLyrics.plain(""))
        override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
        override fun coverArtUrl(coverArtId: String?, size: Int) = null
        override fun downloadUrl(track: Track) = ""
    }

    private class FakeLocalPlayHistoryDao : LocalPlayHistoryDao {
        private val seq = AtomicLong(1L)
        val entries = ArrayList<LocalPlayHistoryEntity>()

        override suspend fun insert(entry: LocalPlayHistoryEntity): Long {
            val id = seq.getAndIncrement()
            entries += entry.copy(id = id)
            return id
        }

        override suspend fun recentForProfile(profileId: String, limit: Int): List<LocalPlayHistoryEntity> =
            entries.filter { it.profileId == profileId }.sortedByDescending { it.playedAtMs }.take(limit)

        override suspend fun recent(limit: Int): List<LocalPlayHistoryEntity> =
            entries.sortedByDescending { it.playedAtMs }.take(limit)

        override suspend fun recentAlbumsForProfile(profileId: String, limit: Int, offset: Int): List<LocalAlbumPlayStats> =
            entries.filter { it.profileId == profileId }
                .groupBy { it.albumKey }
                .map { (albumKey, rows) ->
                    LocalAlbumPlayStats(
                        albumKey = albumKey,
                        profileId = profileId,
                        albumJson = rows.mapNotNull { it.albumJson }.maxOrNull(),
                        playCount = rows.size.toLong(),
                        lastPlayedAtMs = rows.maxOf { it.playedAtMs },
                    )
                }
                .sortedByDescending { it.lastPlayedAtMs }
                .drop(offset)
                .take(limit)

        override suspend fun frequentAlbumsForProfile(profileId: String, limit: Int, offset: Int): List<LocalAlbumPlayStats> =
            recentAlbumsForProfile(profileId, Int.MAX_VALUE, 0)
                .sortedWith(compareByDescending<LocalAlbumPlayStats> { it.playCount }.thenByDescending { it.lastPlayedAtMs })
                .drop(offset)
                .take(limit)

        override suspend fun clearProfile(profileId: String) {
            entries.removeAll { it.profileId == profileId }
        }

        override suspend fun clear() {
            entries.clear()
        }
    }

    private class FakeDriveTrackDao(
        private val historyDao: FakeLocalPlayHistoryDao,
    ) : DriveTrackDao {
        private val rows = LinkedHashMap<String, DriveTrackEntity>()

        override suspend fun allIdsAndModTimes(profileId: String): List<DriveFileModTime> =
            rows.values.filter { it.profileId == profileId }.map {
                DriveFileModTime(it.songId, it.driveFileId, it.modifiedTimeMs, it.tagsParsed)
            }

        override suspend fun upsertAll(tracks: List<DriveTrackEntity>) {
            tracks.forEach { rows[it.songId] = it }
        }

        override suspend fun deleteByIds(songIds: List<String>) {
            songIds.forEach { rows.remove(it) }
        }

        override suspend fun clearProfile(profileId: String) {
            rows.entries.removeAll { it.value.profileId == profileId }
        }

        override suspend fun countForProfile(profileId: String): Int =
            rows.values.count { it.profileId == profileId }

        override suspend fun tracksForProfile(profileId: String): List<DriveTrackEntity> =
            rows.values.filter { it.profileId == profileId }

        override suspend fun trackById(songId: String): DriveTrackEntity? = rows[songId]

        override suspend fun embeddedLyrics(songId: String): String? = rows[songId]?.embeddedLyricsJson

        override suspend fun albumsForProfile(profileId: String): List<DriveAlbumRow> =
            rows.values.filter { it.profileId == profileId }
                .groupBy { it.albumKey }
                .values
                .map { g ->
                    val first = g.first()
                    DriveAlbumRow(
                        albumKey = first.albumKey,
                        profileId = profileId,
                        album = first.album,
                        artist = g.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: first.artist,
                        artistKey = first.artistKey,
                        coverArtId = g.firstNotNullOfOrNull { it.coverArtId },
                        songCount = g.size,
                        durationSec = g.sumOf { it.durationSec },
                        year = g.firstNotNullOfOrNull { it.year },
                        genre = g.firstNotNullOfOrNull { it.genre },
                        playCount = 0L,
                    )
                }
                .sortedBy { it.album.lowercase(Locale.ROOT) }

        override suspend fun albumsRecentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> {
            val stats = historyDao.recentAlbumsForProfile(profileId, Int.MAX_VALUE, 0).associateBy { it.albumKey }
            return albumsForProfile(profileId)
                .mapNotNull { row ->
                    val s = stats[row.albumKey] ?: return@mapNotNull null
                    row.copy(playCount = s.playCount) to s.lastPlayedAtMs
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .drop(offset)
                .take(limit)
        }

        override suspend fun albumsFrequentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> {
            val stats = historyDao.frequentAlbumsForProfile(profileId, Int.MAX_VALUE, 0).associateBy { it.albumKey }
            return albumsForProfile(profileId)
                .mapNotNull { row ->
                    val s = stats[row.albumKey] ?: return@mapNotNull null
                    row.copy(playCount = s.playCount) to s.lastPlayedAtMs
                }
                .sortedWith(compareByDescending<Pair<DriveAlbumRow, Long>> { it.first.playCount }.thenByDescending { it.second })
                .map { it.first }
                .drop(offset)
                .take(limit)
        }

        override suspend fun artistsForProfile(profileId: String): List<DriveArtistRow> =
            rows.values.filter { it.profileId == profileId }
                .groupBy { it.artistKey }
                .values
                .map { g ->
                    val first = g.first()
                    DriveArtistRow(
                        artistKey = first.artistKey,
                        profileId = profileId,
                        name = g.firstOrNull { it.albumArtist.isNotBlank() }?.albumArtist ?: first.artist,
                        coverArtId = g.firstNotNullOfOrNull { it.coverArtId },
                        albumCount = g.mapTo(HashSet()) { it.albumKey }.size,
                    )
                }

        override suspend fun genresForProfile(profileId: String): List<DriveGenreRow> = emptyList()

        override suspend fun tracksInAlbum(albumKey: String): List<DriveTrackEntity> =
            rows.values.filter { it.albumKey == albumKey }

        override suspend fun tracksByArtist(artistKey: String): List<DriveTrackEntity> =
            rows.values.filter { it.artistKey == artistKey }

        override suspend fun tracksByParentFolder(profileId: String, parentFolderId: String): List<DriveTrackEntity> =
            rows.values.filter { it.profileId == profileId && it.parentFolderId == parentFolderId }

        override suspend fun allSongsForProfile(profileId: String, limit: Int, offset: Int): List<DriveTrackEntity> =
            rows.values.filter { it.profileId == profileId }.drop(offset).take(limit)

        override suspend fun randomTracks(profileId: String, limit: Int, genre: String?): List<DriveTrackEntity> =
            rows.values.filter {
                it.profileId == profileId && (genre == null || it.genre.equals(genre, ignoreCase = true))
            }.take(limit)

        override suspend fun searchTracks(profileId: String, query: String, limit: Int): List<DriveTrackEntity> =
            rows.values.filter { it.profileId == profileId && it.title.contains(query, ignoreCase = true) }.take(limit)

        override suspend fun searchAlbums(profileId: String, query: String, limit: Int): List<DriveAlbumRow> =
            albumsForProfile(profileId).filter { it.album.contains(query, ignoreCase = true) }.take(limit)

        override suspend fun searchArtists(profileId: String, query: String, limit: Int): List<DriveArtistRow> =
            artistsForProfile(profileId).filter { it.name.contains(query, ignoreCase = true) }.take(limit)
    }

    private class FakeLocalFavoriteDao : LocalFavoriteDao {
        private val entries = LinkedHashMap<String, LocalFavoriteEntity>()

        override suspend fun all(): List<LocalFavoriteEntity> = entries.values.toList()
        override suspend fun byKind(kind: String): List<LocalFavoriteEntity> = entries.values.filter { it.kind == kind }
        override suspend fun byProfile(profileId: String): List<LocalFavoriteEntity> =
            entries.values.filter { it.profileId == profileId }
        override suspend fun byProfileAndKind(profileId: String, kind: String): List<LocalFavoriteEntity> =
            entries.values.filter { it.profileId == profileId && it.kind == kind }
        override suspend fun idsByKind(kind: String): List<String> =
            entries.values.filter { it.kind == kind }.map { it.targetId }
        override suspend fun isStarred(targetId: String): Boolean = entries.containsKey(targetId)
        override suspend fun upsert(favorite: LocalFavoriteEntity) {
            entries[favorite.targetId] = favorite
        }
        override suspend fun delete(targetId: String) {
            entries.remove(targetId)
        }
        override suspend fun clearProfile(profileId: String) {
            entries.entries.removeAll { it.value.profileId == profileId }
        }
        override suspend fun clear() {
            entries.clear()
        }
    }

    private class FakeCachedCoverDao : CachedCoverDao {
        private val entries = LinkedHashMap<String, CachedCoverEntity>()

        override suspend fun path(id: String): String? = entries[id]?.filePath
        override suspend fun upsert(cover: CachedCoverEntity) {
            entries[cover.coverArtId] = cover
        }
        override suspend fun allSizes(): List<Long> = entries.values.map { it.sizeBytes }
        override suspend fun delete(id: String) {
            entries.remove(id)
        }
        override suspend fun deleteByPrefix(prefix: String) {
            entries.entries.removeAll { it.key.startsWith(prefix) }
        }
        override suspend fun clear() {
            entries.clear()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                pending.forEach { (k, v) -> if (v == null) target.remove(k) else target[k] = v }
            }
        }
    }
}
