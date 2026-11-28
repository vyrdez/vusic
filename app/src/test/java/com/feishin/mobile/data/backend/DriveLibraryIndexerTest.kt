package com.feishin.mobile.data.backend

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.ProfileIdTag
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
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveCoverTranscoder
import com.feishin.mobile.data.remote.drive.DriveRangeReader
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.ScanStatus
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class DriveLibraryIndexerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var coversDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
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

    private fun driveProfile(
        id: String = "gd1",
        rootFolderId: String? = "root-music",
    ): ServerProfile = ServerProfile(
        id = id,
        name = "Google Drive",
        type = BackendType.GOOGLE_DRIVE,
        url = server.url("/").toString().trimEnd('/'),
        driveRootFolderId = rootFolderId,
        driveRootFolderName = if (rootFolderId != null) "Music" else null,
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = "user@gmail.com",
            accessToken = "drive-token",
        ),
    )

    private data class Harness(
        val indexer: DriveLibraryIndexer,
        val trackDao: InMemoryDriveTrackDao,
        val coverDao: InMemoryCachedCoverDao,
        val cancellationRegistry: DriveScanCancellationRegistry,
        val recordedTags: MutableList<ProfileIdTag?>,
        val recordedSleeps: MutableList<Long>,
        val settingsStore: SettingsStore,
    )

    private fun createHarness(
        scope: CoroutineScope,
        profile: ServerProfile,
        metered: () -> Boolean = { false },
        nowMs: () -> Long = { 1_700_000_000_000L },
    ): Harness {
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

        val recordedTags = CopyOnWriteArrayList<ProfileIdTag?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val req = chain.request()
                    recordedTags += req.tag(ProfileIdTag::class.java)
                    chain.proceed(req)
                },
            )
            .build()

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

        val trackDao = InMemoryDriveTrackDao()
        val coverDao = InMemoryCachedCoverDao()
        val cancellationRegistry = DriveScanCancellationRegistry()
        val recordedSleeps = CopyOnWriteArrayList<Long>()

        val indexer = DriveLibraryIndexer(
            driveApiProvider = { api },
            profileStore = profileStore,
            driveRangeReader = rangeReader,
            driveTrackDao = trackDao,
            cachedCoverDao = coverDao,
            settingsStore = settings,
            cancellationRegistry = cancellationRegistry,
            json = json,
            isNetworkMetered = metered,
            clockMs = nowMs,
            sleeper = { ms -> recordedSleeps += ms },
            jitterProvider = { 1.0 },
            externalScope = scope,
        )
        return Harness(
            indexer = indexer,
            trackDao = trackDao,
            coverDao = coverDao,
            cancellationRegistry = cancellationRegistry,
            recordedTags = recordedTags,
            recordedSleeps = recordedSleeps,
            settingsStore = settings,
        )
    }

    @Test
    fun `scoped MIME-free BFS discovers octet-stream audio and folder cover while skipping shortcuts`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-music")
        val h = createHarness(backgroundScope, profile)

        val queries = CopyOnWriteArrayList<String>()
        val rangeCalls = CopyOnWriteArrayList<String>()
        val mp3Bytes = buildId3v2Mp3(title = "First Light", artist = "Signal", album = "Dawn")
        val flacBytes = ByteArray(128) { 0 } // Triggers fallback inference from parentFolderPath + filename

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    val fileId = url.pathSegments.last()
                    rangeCalls += fileId
                    val bytes = when (fileId) {
                        "f-mp3" -> mp3Bytes
                        "f-flac" -> flacBytes
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return rangeResponse(request.getHeader("Range"), bytes)
                }

                val q = url.queryParameter("q").orEmpty()
                queries += q
                val body = when {
                    "'root-music' in parents" in q -> """
                        {
                          "files": [
                            {"id": "dir-artist", "name": "The Midnight", "mimeType": "application/vnd.google-apps.folder", "parents": ["root-music"]},
                            {"id": "shortcut-1", "name": "LinkedFolder", "mimeType": "application/vnd.google-apps.shortcut", "parents": ["root-music"]},
                            {"id": "readme-1", "name": "notes.txt", "mimeType": "text/plain", "parents": ["root-music"]}
                          ]
                        }
                    """.trimIndent()
                    "'dir-artist' in parents" in q -> """
                        {
                          "files": [
                            {"id": "dir-album", "name": "Nocturnal", "mimeType": "application/vnd.google-apps.folder", "parents": ["dir-artist"]}
                          ]
                        }
                    """.trimIndent()
                    "'dir-album' in parents" in q -> """
                        {
                          "files": [
                            {"id": "cov-1", "name": "cover.jpg", "mimeType": "image/jpeg", "parents": ["dir-album"]},
                            {"id": "f-flac", "name": "01 - Shadows.flac", "mimeType": "application/octet-stream", "size": "${flacBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["dir-album"]},
                            {"id": "f-mp3", "name": "02 - Sunset.mp3", "mimeType": "application/octet-stream", "size": "${mp3Bytes.size}", "modifiedTime": "2026-11-28T10:01:00Z", "parents": ["dir-album"]},
                            {"id": "f-shortcut", "name": "03 - Alias.mp3", "mimeType": "application/vnd.google-apps.shortcut", "parents": ["dir-album"]},
                            {"id": "f-trashed", "name": "04 - Gone.mp3", "mimeType": "audio/mpeg", "trashed": true, "parents": ["dir-album"]}
                          ]
                        }
                    """.trimIndent()
                    else -> """{"files": []}"""
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }

        val result = h.indexer.scanNow("gd1") as Result.Ok
        assertFalse(result.value.scanning)
        assertFalse(result.value.quotaLimited)
        assertEquals(2, result.value.count)
        assertEquals(3, result.value.folderCount)

        // MIME filter must NOT appear in scoped queries
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.none { "mimeType" in it })
        assertTrue(h.recordedTags.all { it == ProfileIdTag("gd1") })
        assertEquals(setOf("f-flac", "f-mp3"), rangeCalls.toSet())

        val flac = h.trackDao.trackById("gd1:f-flac")
        assertNotNull(flac)
        assertEquals("The Midnight/Nocturnal", flac!!.parentFolderPath)
        assertEquals("The Midnight", flac.artist)
        assertEquals("Nocturnal", flac.album)
        assertEquals("Shadows", flac.title)
        assertEquals(1, flac.trackNo)
        assertEquals("flac", flac.suffix)
        assertEquals("gd1:cov-1", flac.coverArtId)
        assertTrue(flac.tagsParsed)

        val mp3 = h.trackDao.trackById("gd1:f-mp3")
        assertNotNull(mp3)
        assertEquals("First Light", mp3!!.title)
        assertEquals("Signal", mp3.artist)
        assertEquals("Dawn", mp3.album)
        assertEquals("gd1:cov-1", mp3.coverArtId)
        assertNull(h.trackDao.trackById("gd1:f-shortcut"))
        assertNull(h.trackDao.trackById("gd1:f-trashed"))
    }

    @Test
    fun `pagination nextPageToken and two-stage progress emissions`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-1")
        val h = createHarness(backgroundScope, profile)
        val observed = mutableListOf<ScanStatus>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            h.indexer.scanStatusFlow("gd1").toList(observed)
        }

        val trackBytes = buildId3v2Mp3(title = "Track", artist = "Artist", album = "Album")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    return rangeResponse(request.getHeader("Range"), trackBytes)
                }
                val q = url.queryParameter("q").orEmpty()
                val pageToken = url.queryParameter("pageToken")
                val body = when {
                    "'root-1' in parents" in q && pageToken == null -> """
                        {
                          "nextPageToken": "page-2",
                          "files": [
                            {"id": "d-artist", "name": "Daft Punk", "mimeType": "application/vnd.google-apps.folder", "parents": ["root-1"]},
                            {"id": "f-1", "name": "01 - One.mp3", "mimeType": "audio/mpeg", "size": "${trackBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                          ]
                        }
                    """.trimIndent()
                    "'root-1' in parents" in q && pageToken == "page-2" -> """
                        {
                          "files": [
                            {"id": "f-2", "name": "02 - Two.mp3", "mimeType": "audio/mpeg", "size": "${trackBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                          ]
                        }
                    """.trimIndent()
                    "'d-artist' in parents" in q -> """
                        {
                          "files": [
                            {"id": "d-album", "name": "Discovery", "mimeType": "application/vnd.google-apps.folder", "parents": ["d-artist"]}
                          ]
                        }
                    """.trimIndent()
                    "'d-album' in parents" in q -> """
                        {
                          "files": [
                            {"id": "f-3", "name": "03 - Harder.mp3", "mimeType": "audio/mpeg", "size": "${trackBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["d-album"]}
                          ]
                        }
                    """.trimIndent()
                    else -> """{"files": []}"""
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }

        val res = h.indexer.scanNow("gd1") as Result.Ok
        assertEquals(3, res.value.count)
        assertEquals(3, res.value.totalCount)
        assertEquals("Daft Punk/Discovery", h.trackDao.trackById("gd1:f-3")?.parentFolderPath)

        // Two-stage progress verification: Stage 1 has totalCount == null, Stage 2 has totalCount == 3
        val stage1Discovery = observed.filter { it.scanning && it.totalCount == null }
        val stage2Indexing = observed.filter { it.scanning && it.totalCount != null }
        assertTrue("Expected Stage 1 emissions with totalCount == null: $observed", stage1Discovery.isNotEmpty())
        assertTrue("Expected discovery count to reach 3: $stage1Discovery", stage1Discovery.any { it.count == 3 })
        assertTrue("Expected Stage 2 emissions with totalCount == 3: $observed", stage2Indexing.any { it.totalCount == 3 })
        assertTrue("Expected final non-scanning state: $observed", observed.last().let { !it.scanning && it.count == 3 })
        collectJob.cancel()
    }

    @Test
    fun `http 429 retry-after succeeds on retry and exponential backoff surfaces quotaLimited without deleting existing rows`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-1")
        val h = createHarness(backgroundScope, profile)
        // Pre-seed an existing row to verify partial/quota-aborted scan does NOT delete it
        h.trackDao.upsertAll(
            listOf(
                DriveTrackEntity(
                    songId = "gd1:existing-1",
                    profileId = "gd1",
                    driveFileId = "existing-1",
                    fileName = "existing.mp3",
                    title = "Existing",
                    artist = "Artist",
                    album = "Album",
                    albumKey = "gd1:al:1",
                    artistKey = "gd1:ar:1",
                    modifiedTimeMs = 1000L,
                    tagsParsed = true,
                ),
            ),
        )

        val trackBytes = buildId3v2Mp3(title = "Recovered", artist = "Artist", album = "Album")
        val listAttempts = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    return rangeResponse(request.getHeader("Range"), trackBytes)
                }
                val attempt = listAttempts.incrementAndGet()
                return if (attempt == 1) {
                    MockResponse().setResponseCode(429).addHeader("Retry-After", "3")
                } else {
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "files": [
                            {"id": "existing-1", "name": "existing.mp3", "mimeType": "audio/mpeg", "modifiedTime": "1970-01-01T00:00:01Z", "parents": ["root-1"]},
                            {"id": "new-1", "name": "01 - New.mp3", "mimeType": "audio/mpeg", "size": "${trackBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                          ]
                        }
                        """.trimIndent(),
                    )
                }
            }
        }

        val recovered = h.indexer.scanNow("gd1") as Result.Ok
        assertFalse(recovered.value.quotaLimited)
        assertEquals(listOf(3_000L), h.recordedSleeps.toList())
        assertNotNull(h.trackDao.trackById("gd1:new-1"))

        // Now force 6 consecutive 429s (initial + 5 retries) without Retry-After -> exponential backoff + quotaLimited
        h.recordedSleeps.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(429)
            }
        }

        val exhausted = h.indexer.scanNow("gd1") as Result.Ok
        assertTrue(exhausted.value.quotaLimited)
        assertFalse(exhausted.value.scanning)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), h.recordedSleeps.toList())
        // Existing indexed rows must NOT be deleted when traversal is aborted by quota limit
        assertNotNull(h.trackDao.trackById("gd1:existing-1"))
        assertNotNull(h.trackDao.trackById("gd1:new-1"))
    }

    @Test
    fun `incremental sync skips unchanged modifiedTime and prunes deleted files and covers only after complete traversal`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-1")
        val h = createHarness(backgroundScope, profile)

        val mp3WithArt = buildId3v2Mp3(
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            apicJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02),
        )
        val mp3Two = buildId3v2Mp3(title = "Song B", artist = "Artist A", album = "Album A")

        val rangeFetchIds = CopyOnWriteArrayList<String>()
        var secondScan = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    val id = url.pathSegments.last()
                    rangeFetchIds += id
                    val bytes = if (id == "f-a") mp3WithArt else mp3Two
                    return rangeResponse(request.getHeader("Range"), bytes)
                }
                val body = if (!secondScan) {
                    """
                    {
                      "files": [
                        {"id": "f-a", "name": "a.mp3", "mimeType": "audio/mpeg", "size": "${mp3WithArt.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]},
                        {"id": "f-b", "name": "b.mp3", "mimeType": "audio/mpeg", "size": "${mp3Two.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                      ]
                    }
                    """.trimIndent()
                } else {
                    // f-a was deleted/trashed; f-b is unchanged
                    """
                    {
                      "files": [
                        {"id": "f-b", "name": "b.mp3", "mimeType": "audio/mpeg", "size": "${mp3Two.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                      ]
                    }
                    """.trimIndent()
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }

        val first = h.indexer.scanNow("gd1") as Result.Ok
        assertEquals(2, first.value.count)
        assertEquals(setOf("f-a", "f-b"), rangeFetchIds.toSet())
        assertEquals("gd1:cov:f-a", h.trackDao.trackById("gd1:f-a")?.coverArtId)
        assertNotNull(h.coverDao.path("gd1:cov:f-a"))

        // Second scan: f-b unchanged -> NO Range fetch; f-a removed -> pruned from drive_tracks & cached_covers
        rangeFetchIds.clear()
        secondScan = true
        val second = h.indexer.scanNow("gd1") as Result.Ok
        assertEquals(1, second.value.count)
        assertTrue("Expected unchanged f-b to skip Range fetch, got $rangeFetchIds", rangeFetchIds.isEmpty())
        assertNull(h.trackDao.trackById("gd1:f-a"))
        assertNull(h.coverDao.path("gd1:cov:f-a"))
        assertNotNull(h.trackDao.trackById("gd1:f-b"))
    }

    @Test
    fun `metered network guard defers Range tag fetches and next unmetered scan parses them`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-1")
        var metered = true
        val h = createHarness(backgroundScope, profile, metered = { metered })
        val mp3Bytes = buildId3v2Mp3(title = "Tagged Title", artist = "Tagged Artist", album = "Tagged Album")
        val rangeCalls = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    rangeCalls.incrementAndGet()
                    return rangeResponse(request.getHeader("Range"), mp3Bytes)
                }
                return MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "files": [
                        {"id": "f-1", "name": "01 - Fallback Title.mp3", "mimeType": "audio/mpeg", "size": "${mp3Bytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["root-1"]}
                      ]
                    }
                    """.trimIndent(),
                )
            }
        }

        // Metered + default downloadWifiOnly = true -> skips Range fetch, stores tagsParsed = false
        h.indexer.scanNow("gd1")
        assertEquals(0, rangeCalls.get())
        val meteredRow = h.trackDao.trackById("gd1:f-1")
        assertNotNull(meteredRow)
        assertFalse(meteredRow!!.tagsParsed)
        assertEquals("Fallback Title", meteredRow.title)

        // Now unmetered -> same modifiedTime, but tagsParsed == false so Range fetch executes!
        metered = false
        h.indexer.scanNow("gd1")
        assertEquals(1, rangeCalls.get())
        val unmeteredRow = h.trackDao.trackById("gd1:f-1")
        assertTrue(unmeteredRow!!.tagsParsed)
        assertEquals("Tagged Title", unmeteredRow.title)
        assertEquals("Tagged Artist", unmeteredRow.artist)
    }

    @Test
    fun `cancellation registry cancels active background scan`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = "root-1")
        val h = createHarness(backgroundScope, profile)
        val requestEntered = CompletableDeferred<Unit>()
        val releaseServer = CompletableDeferred<Unit>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestEntered.complete(Unit)
                kotlinx.coroutines.runBlocking { releaseServer.await() }
                return MockResponse().setResponseCode(200).setBody("""{"files": []}""")
            }
        }

        val started = h.indexer.startScan("gd1")
        assertTrue(started is Result.Ok)
        requestEntered.await()
        assertTrue((h.indexer.scanStatus("gd1") as Result.Ok).value.scanning)

        h.cancellationRegistry.cancel("gd1")
        assertFalse((h.indexer.scanStatus("gd1") as Result.Ok).value.scanning)
        releaseServer.complete(Unit)
    }

    @Test
    fun `scan-all mode resolves parentFolderPath and retries 403 userRateLimitExceeded`() = runTest {
        val profile = driveProfile(id = "gd1", rootFolderId = null)
        val h = createHarness(backgroundScope, profile)
        val trackBytes = buildId3v2Mp3(title = "AllDrive", artist = "Artist", album = "Album")
        val attempts = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.queryParameter("alt") == "media") {
                    return rangeResponse(request.getHeader("Range"), trackBytes)
                }
                val q = url.queryParameter("q").orEmpty()
                if (attempts.incrementAndGet() == 1) {
                    return MockResponse()
                        .setResponseCode(403)
                        .setBody("""{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}""")
                }
                val body = when {
                    "mimeType contains 'audio/'" in q -> """
                        {
                          "files": [
                            {"id": "f-all-1", "name": "track.mp3", "mimeType": "audio/mpeg", "size": "${trackBytes.size}", "modifiedTime": "2026-11-28T10:00:00Z", "parents": ["folder-alb"]}
                          ]
                        }
                    """.trimIndent()
                    "mimeType = 'application/vnd.google-apps.folder'" in q -> """
                        {
                          "files": [
                            {"id": "folder-art", "name": "Boards of Canada", "mimeType": "application/vnd.google-apps.folder", "parents": []},
                            {"id": "folder-alb", "name": "Geogaddi", "mimeType": "application/vnd.google-apps.folder", "parents": ["folder-art"]}
                          ]
                        }
                    """.trimIndent()
                    else -> """{"files": []}"""
                }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }

        val res = h.indexer.scanNow("gd1") as Result.Ok
        assertFalse(res.value.quotaLimited)
        assertEquals(1, res.value.count)
        assertEquals(listOf(1_000L), h.recordedSleeps.toList())
        assertEquals("Boards of Canada/Geogaddi", h.trackDao.trackById("gd1:f-all-1")?.parentFolderPath)
    }

    private fun rangeResponse(rangeHeader: String?, data: ByteArray): MockResponse {
        val match = Regex("""bytes=(\d+)-(\d+)""").matchEntire(rangeHeader.orEmpty())
            ?: return MockResponse().setResponseCode(400)
        val start = match.groupValues[1].toLong()
        val reqEnd = match.groupValues[2].toLong()
        if (start < 0 || start >= data.size) return MockResponse().setResponseCode(416)
        val end = minOf(reqEnd, data.size.toLong() - 1L)
        val slice = data.copyOfRange(start.toInt(), end.toInt() + 1)
        return MockResponse()
            .setResponseCode(206)
            .addHeader("Content-Range", "bytes $start-$end/${data.size}")
            .setBody(Buffer().write(slice))
    }

    private fun buildId3v2Mp3(
        title: String,
        artist: String,
        album: String,
        apicJpeg: ByteArray? = null,
    ): ByteArray {
        val frames = mutableListOf(
            id3TextFrame("TIT2", title),
            id3TextFrame("TPE1", artist),
            id3TextFrame("TALB", album),
        )
        if (apicJpeg != null) {
            frames += id3ApicFrame(apicJpeg)
        }
        val payload = ByteArrayOutputStream().apply {
            frames.forEach { write(it) }
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
        out.write(4) // v2.4
        out.write(0)
        out.write(0)
        out.write(synchsafe32(payload.size))
        out.write(payload)
        // Pad enough audio bytes so file size > 0
        out.write(ByteArray(64))
        return out.toByteArray()
    }

    private fun id3TextFrame(id: String, value: String): ByteArray {
        val textBytes = value.toByteArray(StandardCharsets.UTF_8)
        val framePayload = byteArrayOf(3) + textBytes
        return id.toByteArray(StandardCharsets.ISO_8859_1) + synchsafe32(framePayload.size) + byteArrayOf(0, 0) + framePayload
    }

    private fun id3ApicFrame(jpeg: ByteArray): ByteArray {
        val header = byteArrayOf(0) + "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(0, 3, 0)
        val payload = header + jpeg
        return "APIC".toByteArray(StandardCharsets.ISO_8859_1) + synchsafe32(payload.size) + byteArrayOf(0, 0) + payload
    }

    private fun synchsafe32(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private class InMemoryDriveTrackDao : DriveTrackDao {
        private val rows = LinkedHashMap<String, DriveTrackEntity>()

        override suspend fun allIdsAndModTimes(profileId: String): List<DriveFileModTime> =
            synchronized(rows) {
                rows.values.filter { it.profileId == profileId }.map {
                    DriveFileModTime(it.songId, it.driveFileId, it.modifiedTimeMs, it.tagsParsed)
                }
            }

        override suspend fun upsertAll(tracks: List<DriveTrackEntity>) {
            synchronized(rows) {
                tracks.forEach { rows[it.songId] = it }
            }
        }

        override suspend fun deleteByIds(songIds: List<String>) {
            synchronized(rows) {
                songIds.forEach { rows.remove(it) }
            }
        }

        override suspend fun clearProfile(profileId: String) {
            synchronized(rows) {
                rows.entries.removeAll { it.value.profileId == profileId }
            }
        }

        override suspend fun countForProfile(profileId: String): Int =
            synchronized(rows) { rows.values.count { it.profileId == profileId } }

        override suspend fun tracksForProfile(profileId: String): List<DriveTrackEntity> =
            synchronized(rows) { rows.values.filter { it.profileId == profileId } }

        override suspend fun trackById(songId: String): DriveTrackEntity? =
            synchronized(rows) { rows[songId] }

        override suspend fun embeddedLyrics(songId: String): String? =
            synchronized(rows) { rows[songId]?.embeddedLyricsJson }

        override suspend fun albumsForProfile(profileId: String): List<DriveAlbumRow> = emptyList()
        override suspend fun albumsRecentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun albumsFrequentFromHistory(profileId: String, limit: Int, offset: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun artistsForProfile(profileId: String): List<DriveArtistRow> = emptyList()
        override suspend fun genresForProfile(profileId: String): List<DriveGenreRow> = emptyList()
        override suspend fun tracksInAlbum(albumKey: String): List<DriveTrackEntity> = emptyList()
        override suspend fun tracksByArtist(artistKey: String): List<DriveTrackEntity> = emptyList()
        override suspend fun tracksByParentFolder(profileId: String, parentFolderId: String): List<DriveTrackEntity> = emptyList()
        override suspend fun allSongsForProfile(profileId: String, limit: Int, offset: Int): List<DriveTrackEntity> = emptyList()
        override suspend fun randomTracks(profileId: String, limit: Int, genre: String?): List<DriveTrackEntity> = emptyList()
        override suspend fun searchTracks(profileId: String, query: String, limit: Int): List<DriveTrackEntity> = emptyList()
        override suspend fun searchAlbums(profileId: String, query: String, limit: Int): List<DriveAlbumRow> = emptyList()
        override suspend fun searchArtists(profileId: String, query: String, limit: Int): List<DriveArtistRow> = emptyList()
    }

    private class InMemoryCachedCoverDao : CachedCoverDao {
        private val entries = LinkedHashMap<String, CachedCoverEntity>()

        override suspend fun path(id: String): String? = synchronized(entries) { entries[id]?.filePath }
        override suspend fun upsert(cover: CachedCoverEntity) {
            synchronized(entries) { entries[cover.coverArtId] = cover }
        }
        override suspend fun allSizes(): List<Long> = synchronized(entries) { entries.values.map { it.sizeBytes } }
        override suspend fun delete(id: String) {
            synchronized(entries) { entries.remove(id) }
        }
        override suspend fun deleteByPrefix(prefix: String) {
            synchronized(entries) { entries.entries.removeAll { it.key.startsWith(prefix) } }
        }
        override suspend fun clear() {
            synchronized(entries) { entries.clear() }
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
