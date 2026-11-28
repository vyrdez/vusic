package com.feishin.mobile.data.backend

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import coil.decode.DataSource
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.Size
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.BackendUrlResolver
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import com.feishin.mobile.data.db.DriveAlbumRow
import com.feishin.mobile.data.db.DriveArtistRow
import com.feishin.mobile.data.db.DriveFileModTime
import com.feishin.mobile.data.db.DriveGenreRow
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.db.LocalFavoriteDao
import com.feishin.mobile.data.db.LocalFavoriteEntity
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveCoverFetcher
import com.feishin.mobile.data.remote.drive.DriveCoverTranscoder
import com.feishin.mobile.data.remote.drive.DriveRangeReader
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.di.OfflineCoverInterceptor
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class GoogleDriveBackendTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var coversDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val stubLifecycle = object : Lifecycle() {
        override val currentState: State = State.RESUMED
        override fun addObserver(observer: LifecycleObserver) = Unit
        override fun removeObserver(observer: LifecycleObserver) = Unit
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        coversDir = tmp.newFolder("download_covers")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun driveProfile(id: String = "gd1"): ServerProfile = ServerProfile(
        id = id,
        name = "My Drive",
        type = BackendType.GOOGLE_DRIVE,
        url = server.url("/").toString().trimEnd('/'),
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = "user@gmail.com",
            accessToken = "drive-token",
        ),
    )

    private fun createBackend(
        scope: CoroutineScope,
        profile: ServerProfile = driveProfile(),
        trackDao: FakeDriveTrackDao = FakeDriveTrackDao(),
        favoriteDao: FakeLocalFavoriteDao = FakeLocalFavoriteDao(),
        coverDao: FakeCachedCoverDao = FakeCachedCoverDao(),
    ): GoogleDriveBackend {
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
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriveApi::class.java)

        val rangeReader = DriveRangeReader(
            client = client,
            coversDirProvider = { coversDir },
            transcoder = DriveCoverTranscoder { bytes, _ -> bytes },
            fileUrlBuilder = { fileId -> server.url("/drive/v3/files/$fileId?alt=media").toString() },
        )
        val indexer = DriveLibraryIndexer(
            driveApiProvider = { api },
            profileStore = profileStore,
            driveRangeReader = rangeReader,
            driveTrackDao = trackDao,
            cachedCoverDao = coverDao,
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
            localFavoriteDao = favoriteDao,
            indexer = indexer,
            json = json,
        )
    }

    @Test
    fun `streamUrl downloadUrl and coverArtUrl resolve drive-cover and alt media URLs consistently`() = runTest {
        val profile = driveProfile("gd1")
        val backend = createBackend(backgroundScope, profile)

        val track = Track(
            id = "gd1:file-99",
            title = "Midnight",
            album = "After Hours",
            albumId = "gd1:al:1",
            artist = "Artist",
            artistId = "gd1:ar:1",
            coverArt = "gd1:cov:file-99",
            durationSec = 200,
            trackNo = 1,
            year = 2024,
            genre = "Synthwave",
            suffix = "mp3",
            starred = false,
            sourceProfileId = "gd1",
            sourceLabel = "My Drive",
        )

        assertEquals("https://www.googleapis.com/drive/v3/files/file-99?alt=media", backend.streamUrl(track))
        assertEquals("https://www.googleapis.com/drive/v3/files/file-99?alt=media", backend.downloadUrl(track))
        assertEquals("drive-cover://gd1/file-99", backend.coverArtUrl("gd1:cov:file-99"))
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/folder-art-7?alt=media",
            backend.coverArtUrl("gd1:folder-art-7"),
        )
        assertNull(backend.coverArtUrl(null))

        // Verify parity with BackendUrlResolver
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val profileStore = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
        )
        profileStore.saveAll(listOf(profile))
        val resolver = BackendUrlResolver(profileStore)
        assertEquals(resolver.streamUrl(track), backend.streamUrl(track))
        assertEquals(resolver.downloadUrl(track), backend.downloadUrl(track))
        assertEquals(resolver.coverArtUrl("gd1:cov:file-99"), backend.coverArtUrl("gd1:cov:file-99"))
        assertEquals(resolver.coverArtUrl("gd1:folder-art-7"), backend.coverArtUrl("gd1:folder-art-7"))
    }

    @Test
    fun `DriveCoverFetcher serves disk cache hit and extracts bounded art via Range on cache miss`() = runTest {
        val coverDao = FakeCachedCoverDao()
        val cachedFile = File(coversDir, "cached_hit.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22))
        }
        coverDao.upsert(CachedCoverEntity("gd1:cov:hit-1", cachedFile.absolutePath, cachedFile.length()))

        val rangeRequests = AtomicInteger(0)
        val jpegArt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xAA.toByte(), 0xBB.toByte())
        val mp3WithArt = buildId3v2WithApic(jpegArt)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                rangeRequests.incrementAndGet()
                val match = Regex("""bytes=(\d+)-(\d+)""").matchEntire(request.getHeader("Range").orEmpty())
                    ?: return MockResponse().setResponseCode(400)
                val start = match.groupValues[1].toInt()
                val end = minOf(match.groupValues[2].toInt(), mp3WithArt.size - 1)
                val slice = mp3WithArt.copyOfRange(start, end + 1)
                return MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/${mp3WithArt.size}")
                    .setBody(Buffer().write(slice))
            }
        }

        val rangeReader = DriveRangeReader(
            client = OkHttpClient.Builder().build(),
            coversDirProvider = { coversDir },
            transcoder = DriveCoverTranscoder { bytes, _ -> bytes },
            fileUrlBuilder = { fileId -> server.url("/drive/v3/files/$fileId?alt=media").toString() },
        )

        // 1. Cache hit must NOT touch network
        val hitFetcher = DriveCoverFetcher("gd1", "hit-1", coverDao, rangeReader)
        val hitResult = hitFetcher.fetch() as SourceResult
        assertEquals(DataSource.DISK, hitResult.dataSource)
        assertEquals("image/jpeg", hitResult.mimeType)
        assertEquals(0, rangeRequests.get())

        // 2. Cache miss extracts via Range, saves file, and persists CachedCoverEntity
        val missFetcher = DriveCoverFetcher("gd1", "miss-2", coverDao, rangeReader)
        val missResult = missFetcher.fetch() as SourceResult
        assertEquals(DataSource.NETWORK, missResult.dataSource)
        assertTrue(rangeRequests.get() >= 1)
        val savedPath = coverDao.path("gd1:cov:miss-2")
        assertNotNull(savedPath)
        assertTrue(File(savedPath!!).exists())

        // 3. Uri parser handles drive-cover://{profileId}/{driveFileId}
        assertEquals(
            "gd1" to "miss-2",
            DriveCoverFetcher.parseDriveCoverUri("drive-cover", "gd1", "miss-2", null),
        )
        assertEquals(
            "gd1" to "miss-2",
            DriveCoverFetcher.parseDriveCoverUri(null, null, null, "drive-cover://gd1/miss-2"),
        )
    }

    @Test
    fun `OfflineCoverInterceptor early-returns immediately on drive-cover URI`() = runTest {
        val coverDao = FakeCachedCoverDao()
        // Even if an entry exists under gd1:file-1, OfflineCoverInterceptor must NOT rewrite drive-cover://
        val bogusFile = File(coversDir, "bogus.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coverDao.upsert(CachedCoverEntity("gd1:file-1", bogusFile.absolutePath, 3L))
        coverDao.upsert(CachedCoverEntity("gd1:cov:file-1", bogusFile.absolutePath, 3L))

        val interceptor = OfflineCoverInterceptor(coverDao)
        val originalReq = ImageRequest.Builder(stubContext())
            .data("drive-cover://gd1/file-1")
            .lifecycle(stubLifecycle)
            .build()

        var forwardedReq: ImageRequest? = null
        val chain = object : Interceptor.Chain {
            override val request: ImageRequest = originalReq
            override val size: Size = Size.ORIGINAL
            override fun withRequest(request: ImageRequest): Interceptor.Chain = this
            override fun withSize(size: Size): Interceptor.Chain = this
            override suspend fun proceed(request: ImageRequest): ImageResult {
                forwardedReq = request
                return ErrorResult(drawable = null, request = request, throwable = RuntimeException("ok"))
            }
        }

        interceptor.intercept(chain)
        assertSame(originalReq, forwardedReq)
        assertEquals("drive-cover://gd1/file-1", forwardedReq?.data)
        assertNull(interceptor.resolveKey(originalReq, "drive-cover://gd1/file-1"))
    }

    @Test
    fun `all 6 albumList modes work offline including recent and frequent playCount`() = runTest {
        val trackDao = FakeDriveTrackDao()
        trackDao.upsertAll(
            listOf(
                DriveTrackEntity(
                    songId = "gd1:s1",
                    profileId = "gd1",
                    driveFileId = "s1",
                    fileName = "01.mp3",
                    title = "Alpha Song",
                    artist = "Zed Artist",
                    album = "Beta Album",
                    albumKey = "gd1:al:beta",
                    artistKey = "gd1:ar:zed",
                    year = 2020,
                    genre = "Rock",
                    durationSec = 180,
                    bitRate = 320_000, // bps normalized to 320 kbps
                ),
                DriveTrackEntity(
                    songId = "gd1:s2",
                    profileId = "gd1",
                    driveFileId = "s2",
                    fileName = "02.mp3",
                    title = "Omega Song",
                    artist = "Alpha Artist",
                    album = "Alpha Album",
                    albumKey = "gd1:al:alpha",
                    artistKey = "gd1:ar:alpha",
                    year = 2024,
                    genre = "Jazz",
                    durationSec = 210,
                    bitRate = 320,
                ),
            ),
        )
        trackDao.recentAlbumPlays["gd1:al:beta"] = 4L to 2_000L
        trackDao.recentAlbumPlays["gd1:al:alpha"] = 9L to 1_000L

        val backend = createBackend(backgroundScope, trackDao = trackDao)

        val recent = (backend.albumList("recent", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:beta", "gd1:al:alpha"), recent.map { it.id })
        assertEquals(4L, recent[0].playCount)

        val frequent = (backend.albumList("frequent", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha", "gd1:al:beta"), frequent.map { it.id })
        assertEquals(9L, frequent[0].playCount)

        val newest = (backend.albumList("newest", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha", "gd1:al:beta"), newest.map { it.id })

        val byName = (backend.albumList("alphabeticalByName", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha", "gd1:al:beta"), byName.map { it.id })

        val byArtist = (backend.albumList("alphabeticalByArtist", size = 10, offset = 0) as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha", "gd1:al:beta"), byArtist.map { it.id })

        val random = (backend.albumList("random", size = 10, offset = 0) as Result.Ok).value
        assertEquals(setOf("gd1:al:alpha", "gd1:al:beta"), random.map { it.id }.toSet())

        val byGenre = (backend.albumList("byGenre", size = 10, offset = 0, genre = "jazz") as Result.Ok).value
        assertEquals(listOf("gd1:al:alpha"), byGenre.map { it.id })

        val song = (backend.getSong("gd1:s1") as Result.Ok).value
        assertEquals(320, song.bitRate)
    }

    @Test
    fun `BackendCapabilities supportsServerFavorites is false for Drive and Local while LocalFavoriteDao round-trips`() = runTest {
        assertFalse(BackendCapabilities.forType(BackendType.GOOGLE_DRIVE).supportsServerFavorites)
        assertFalse(BackendCapabilities.DEVICE_LOCAL.supportsServerFavorites)
        assertTrue(BackendCapabilities.forType(BackendType.NAVIDROME).supportsServerFavorites)
        assertTrue(BackendCapabilities.forType(BackendType.OPENSUBSONIC).supportsServerFavorites)
        assertTrue(BackendCapabilities.forType(BackendType.JELLYFIN).supportsServerFavorites)

        val trackDao = FakeDriveTrackDao()
        trackDao.upsertAll(
            listOf(
                DriveTrackEntity(
                    songId = "gd1:s1",
                    profileId = "gd1",
                    driveFileId = "s1",
                    fileName = "01.mp3",
                    title = "Star Track",
                    artist = "Star Artist",
                    album = "Star Album",
                    albumKey = "gd1:al:star",
                    artistKey = "gd1:ar:star",
                    coverArtId = "gd1:cov:s1",
                    durationSec = 240,
                ),
            ),
        )
        val favoriteDao = FakeLocalFavoriteDao()
        val backend = createBackend(backgroundScope, trackDao = trackDao, favoriteDao = favoriteDao)

        assertTrue(backend.starTrack("gd1:s1", true) is Result.Ok)
        assertTrue(backend.starAlbum("gd1:al:star", true) is Result.Ok)
        assertTrue(backend.starArtist("gd1:ar:star", true) is Result.Ok)

        val starred = (backend.starred() as Result.Ok).value
        assertEquals(listOf("gd1:s1"), starred.tracks.map { it.id })
        assertTrue(starred.tracks.single().starred)
        assertEquals(listOf("gd1:al:star"), starred.albums.map { it.id })
        assertTrue(starred.albums.single().starred)
        assertEquals(listOf("gd1:ar:star"), starred.artists.map { it.id })
        assertTrue(starred.artists.single().starred)

        assertTrue((backend.album("gd1:al:star") as Result.Ok).value.starred)
        assertTrue((backend.artist("gd1:ar:star") as Result.Ok).value.starred)

        assertTrue(backend.unstarTrack("gd1:s1") is Result.Ok)
        assertTrue(backend.unstarAlbum("gd1:al:star") is Result.Ok)
        assertTrue(backend.unstarArtist("gd1:ar:star") is Result.Ok)

        val afterUnstar = (backend.starred() as Result.Ok).value
        assertTrue(afterUnstar.tracks.isEmpty())
        assertTrue(afterUnstar.albums.isEmpty())
        assertTrue(afterUnstar.artists.isEmpty())
    }

    private fun buildId3v2WithApic(jpeg: ByteArray): ByteArray {
        val header = byteArrayOf(0) + "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(0, 3, 0)
        val apicPayload = header + jpeg
        val apicFrame = "APIC".toByteArray(StandardCharsets.ISO_8859_1) +
            synchsafe32(apicPayload.size) + byteArrayOf(0, 0) + apicPayload
        val tit2Payload = byteArrayOf(3) + "Cover".toByteArray(StandardCharsets.UTF_8)
        val tit2Frame = "TIT2".toByteArray(StandardCharsets.ISO_8859_1) +
            synchsafe32(tit2Payload.size) + byteArrayOf(0, 0) + tit2Payload
        val body = ByteArrayOutputStream().apply {
            write(tit2Frame)
            write(apicFrame)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
            write(4)
            write(0)
            write(0)
            write(synchsafe32(body.size))
            write(body)
            write(ByteArray(32))
        }.toByteArray()
    }

    private fun synchsafe32(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private class FakeDriveTrackDao : DriveTrackDao {
        private val rows = LinkedHashMap<String, DriveTrackEntity>()
        val recentAlbumPlays = LinkedHashMap<String, Pair<Long, Long>>() // albumKey -> (playCount, lastPlayedAtMs)

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

        override suspend fun albumsRecentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> =
            albumsForProfile(profileId)
                .mapNotNull { row ->
                    val stats = recentAlbumPlays[row.albumKey] ?: return@mapNotNull null
                    row.copy(playCount = stats.first) to stats.second
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .drop(offset)
                .take(limit)

        override suspend fun albumsFrequentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> =
            albumsForProfile(profileId)
                .mapNotNull { row ->
                    val stats = recentAlbumPlays[row.albumKey] ?: return@mapNotNull null
                    row.copy(playCount = stats.first) to stats.second
                }
                .sortedWith(compareByDescending<Pair<DriveAlbumRow, Long>> { it.first.playCount }.thenByDescending { it.second })
                .map { it.first }
                .drop(offset)
                .take(limit)

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
            rows.values.filter { it.profileId == profileId }.take(limit)

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
