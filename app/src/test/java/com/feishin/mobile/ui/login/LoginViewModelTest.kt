package com.feishin.mobile.ui.login

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.cache.LibraryRefresh
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
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
import com.feishin.mobile.data.remote.drive.DriveAuthResult
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
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
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.settings.ScanCoordinator
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val createdVms = mutableListOf<ViewModel>()
    private var settingsSeq = 0

    private fun <T : ViewModel> track(vm: T): T {
        createdVms += vm
        return vm
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        createdVms.forEach { it.viewModelScope.cancel() }
        createdVms.clear()
        Thread.sleep(50)
        Dispatchers.resetMain()
    }

    private fun newSettings(): SettingsStore {
        val file = File(tmp.root, "login_vm_${settingsSeq++}_${UUID.randomUUID()}.preferences_pb")
        return SettingsStore(PreferenceDataStoreFactory.create { file })
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private data class AuthHarness(
        val auth: AuthRepository,
        val profileStore: ProfileStore,
        val backendFactory: BackendClientFactory,
        val probeRegistry: TransientProbeRegistry,
    )

    private fun newAuthHarness(
        settings: SettingsStore,
        initialProfiles: List<ServerProfile> = emptyList(),
        driveInterceptor: Interceptor? = null,
    ): AuthHarness {
        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)
        if (initialProfiles.isNotEmpty()) {
            profileStore.saveAll(initialProfiles)
        }
        val probeRegistry = TransientProbeRegistry()
        val cleartextGuard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)
        val profileAuth = ProfileAuthInterceptor(profileStore, probeRegistry)
        val trustManager = ProfileTrustManager(profileStore, probeRegistry, settings)
        val clientBuilder = OkHttpClient.Builder()
            .sslSocketFactory(trustManager.sslContext().socketFactory, trustManager)
            .hostnameVerifier(trustManager.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .addNetworkInterceptor(profileAuth.redirectGuard())
        if (driveInterceptor != null) {
            clientBuilder.addInterceptor(driveInterceptor)
        }
        val baseClient = clientBuilder.build()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val backendFactory = BackendClientFactory(json, baseClient)
        val auth = AuthRepository(
            profileStore = profileStore,
            legacyCredentials = legacyCreds,
            backendFactory = backendFactory,
            probeRegistry = probeRegistry,
            settings = settings,
            profileAuthStateStore = ProfileAuthStateStore(),
        )
        runBlocking { auth.preload() }
        return AuthHarness(auth, profileStore, backendFactory, probeRegistry)
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(15)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private suspend fun awaitSuspendCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(15)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun readinessHelpers_enforcePasswordForSubsonic_optionalForJellyfinAndEditMode() {
        assertFalse(LoginViewModel.canTest("https://h", "alice", "", BackendType.NAVIDROME, editMode = false))
        assertFalse(LoginViewModel.canConnect("https://h", "alice", "", BackendType.OPENSUBSONIC, editMode = false))
        assertTrue(LoginViewModel.canTest("https://h", "alice", "pw", BackendType.NAVIDROME, editMode = false))
        assertTrue(LoginViewModel.canConnect("https://h", "alice", "pw", BackendType.OPENSUBSONIC, editMode = false))

        // Jellyfin allows blank password (passwordless accounts)
        assertTrue(LoginViewModel.canTest("https://jf", "alice", "", BackendType.JELLYFIN, editMode = false))
        assertTrue(LoginViewModel.canConnect("https://jf", "alice", "", BackendType.JELLYFIN, editMode = false))

        // Edit mode allows blank password on all backends
        assertTrue(LoginViewModel.canTest("https://h", "alice", "", BackendType.NAVIDROME, editMode = true))
        assertTrue(LoginViewModel.canConnect("https://h", "alice", "", BackendType.NAVIDROME, editMode = true))
    }

    @Test
    fun clientValidation_invalidUrlAndCleartextBlocked_failWithoutNetworkCall() {
        val settings = newSettings()
        val harness = newAuthHarness(settings)
        val vm = track(LoginViewModel(harness.auth, settings))

        val requestCount = AtomicInteger(0)
        LocalHttpServer {
            requestCount.incrementAndGet()
            200 to "{}"
        }.use { server ->
            vm.testOnly(
                serverUrl = "not a valid url",
                username = "alice",
                password = "pw",
                allowCleartext = false,
                backendType = BackendType.NAVIDROME,
            )
            awaitCondition { vm.ui.value.banner is ConnectionBannerState.Error }
            assertEquals(
                LoginErrorMapper.INVALID_URL_FORMAT,
                (vm.ui.value.banner as ConnectionBannerState.Error).message,
            )

            vm.testOnly(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "pw",
                allowCleartext = false,
                backendType = BackendType.JELLYFIN,
            )
            awaitCondition {
                (vm.ui.value.banner as? ConnectionBannerState.Error)?.message ==
                    LoginErrorMapper.CLEARTEXT_BLOCKED
            }
        }
        assertEquals(0, requestCount.get())
    }

    @Test
    fun testOnly_jellyfinSuccess_transitionsBannerToSuccessWithApiInfo() {
        val settings = newSettings()
        val harness = newAuthHarness(settings)
        val vm = track(LoginViewModel(harness.auth, settings))

        LocalHttpServer { req ->
            when {
                req.path.endsWith("/Users/AuthenticateByName") ->
                    200 to """{"AccessToken":"tok-1","ServerId":"srv-1","User":{"Id":"u-1","Name":"alice"}}"""
                req.path.endsWith("/System/Info/Public") ->
                    200 to """{"ProductName":"Jellyfin Server","Version":"10.9.11"}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            vm.testOnly(
                serverUrl = "${server.baseUrl}/web/",
                username = "alice",
                password = "",
                allowSelfSigned = false,
                allowCleartext = true,
                backendType = BackendType.JELLYFIN,
            )
            awaitCondition { vm.ui.value.banner is ConnectionBannerState.Success }
        }

        val banner = vm.ui.value.banner as ConnectionBannerState.Success
        assertTrue(banner.message.startsWith("Connection OK · "))
        assertTrue(banner.message.contains("Jellyfin Server 10.9.11"))
        assertTrue(harness.auth.profiles.value.isEmpty())

        vm.dismissBanner()
        assertEquals(ConnectionBannerState.Idle, vm.ui.value.banner)
    }

    @Test
    fun testAndConnect_jellyfinSuccessAndErrors_persistOnlyOnSuccessAndMap401And404() = runBlocking {
        val settings = newSettings()
        val harness = newAuthHarness(settings)
        val vm = track(LoginViewModel(harness.auth, settings))
        val events = CopyOnWriteArrayList<LoginEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { events += it } }

        LocalHttpServer { req ->
            when {
                req.path.endsWith("/Users/AuthenticateByName") && req.body.contains("\"Pw\":\"bad\"") ->
                    401 to "{}"
                req.path.endsWith("/Users/AuthenticateByName") && req.body.contains("\"Username\":\"notjf\"") ->
                    404 to "{}"
                req.path.endsWith("/Users/AuthenticateByName") ->
                    200 to """{"AccessToken":"jf-tok","ServerId":"srv-9","User":{"Id":"u-9","Name":"alice"}}"""
                req.path.endsWith("/System/Info/Public") ->
                    200 to """{"ProductName":"Jellyfin","Version":"10.10.0"}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            // 401 -> Wrong credentials
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "bad",
                backendType = BackendType.JELLYFIN,
                allowCleartext = true,
            )
            awaitSuspendCondition { vm.ui.value.banner is ConnectionBannerState.Error }
            assertEquals(
                LoginErrorMapper.WRONG_CREDENTIALS,
                (vm.ui.value.banner as ConnectionBannerState.Error).message,
            )
            assertTrue(harness.auth.profiles.value.isEmpty())

            // 404 on AuthenticateByName -> Not Jellyfin
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "notjf",
                password = "pw",
                backendType = BackendType.JELLYFIN,
                allowCleartext = true,
            )
            awaitSuspendCondition {
                (vm.ui.value.banner as? ConnectionBannerState.Error)?.message ==
                    LoginErrorMapper.NOT_JELLYFIN
            }

            // Success
            var okCalls = 0
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "",
                backendType = BackendType.JELLYFIN,
                serverName = "Living Room JF",
                allowCleartext = true,
                addMode = true,
                onSuccess = { okCalls++ },
            )
            awaitSuspendCondition {
                okCalls == 1 &&
                    harness.auth.profiles.value.size == 1 &&
                    events.any { it is LoginEvent.Snackbar && it.message.startsWith("Connected to \"Living Room JF\"") }
            }
        }

        val saved = harness.auth.profiles.value.single()
        assertEquals(BackendType.JELLYFIN, saved.type)
        assertEquals("Living Room JF", saved.name)
        assertTrue(events.any { it is LoginEvent.Snackbar && it.message.startsWith("Connected to \"Living Room JF\"") })
        job.cancel()
    }

    @Test
    fun editMode_blankPasswordKeepsStoredCredentials_andUrlOrUserChangeSurfaces401InBanner() = runBlocking {
        val settings = newSettings()
        LocalHttpServer { req ->
            when {
                req.path.endsWith("/Users/u-1/Items") ->
                    200 to """{"Items":[],"TotalRecordCount":0}"""
                req.path.endsWith("/Users/AuthenticateByName") ->
                    401 to "{}"
                req.path.endsWith("/System/Info/Public") ->
                    200 to """{"ProductName":"Jellyfin","Version":"10.9.11"}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            val existingJf = ServerProfile(
                id = "p2",
                name = "Old JF",
                type = BackendType.JELLYFIN,
                url = server.baseUrl,
                userId = "u-1",
                credentials = ProfileCredentials.Jellyfin(
                    username = "alice",
                    accessToken = "stored-secret-token",
                    serverId = "srv-1",
                    deviceId = "dev-1",
                ),
                allowSelfSigned = false,
                allowCleartext = true,
                enabled = true,
                priority = 0,
            )
            val harness = newAuthHarness(settings, listOf(existingJf))
            val vm = track(LoginViewModel(harness.auth, settings))
            val events = CopyOnWriteArrayList<LoginEvent>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { events += it } }

            // Blank password with same URL+username reuses stored token and updates profile in place
            var savedCount = 0
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "",
                backendType = BackendType.JELLYFIN,
                serverName = "Renamed JF",
                allowSelfSigned = true,
                allowCleartext = true,
                addMode = true,
                existingProfileId = "p2",
                onSuccess = { savedCount++ },
            )
            awaitSuspendCondition {
                savedCount == 1 && events.any { it == LoginEvent.Snackbar("Updated \"Renamed JF\"") }
            }

            val updated = harness.auth.profiles.value.single()
            assertEquals("p2", updated.id)
            assertEquals("Renamed JF", updated.name)
            assertTrue(updated.allowSelfSigned)
            assertEquals("stored-secret-token", (updated.credentials as ProfileCredentials.Jellyfin).accessToken)
            assertTrue(events.any { it == LoginEvent.Snackbar("Updated \"Renamed JF\"") })

            // Changing Jellyfin username with blank password triggers AuthenticateByName and surfaces 401
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "bob",
                password = "",
                backendType = BackendType.JELLYFIN,
                serverName = "Renamed JF",
                allowSelfSigned = true,
                allowCleartext = true,
                addMode = true,
                existingProfileId = "p2",
            )
            awaitSuspendCondition { vm.ui.value.banner is ConnectionBannerState.Error }
            assertEquals(
                LoginErrorMapper.WRONG_CREDENTIALS,
                (vm.ui.value.banner as ConnectionBannerState.Error).message,
            )
            job.cancel()
        }
    }

    @Test
    fun editMode_subsonicBlankPasswordKeepsStoredPassword() {
        val settings = newSettings()
        val seenQueries = CopyOnWriteArrayList<String>()
        LocalHttpServer { req ->
            seenQueries += req.query
            when {
                req.path.endsWith("/rest/ping.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                req.path.endsWith("/rest/getOpenSubsonicExtensions.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            val existingSub = ServerProfile(
                id = "p1",
                name = "Old Sub",
                type = BackendType.NAVIDROME,
                url = server.baseUrl,
                userId = "alice",
                credentials = ProfileCredentials.Subsonic("alice", "kept-secret"),
                allowSelfSigned = false,
                allowCleartext = true,
                enabled = true,
                priority = 0,
            )
            val harness = newAuthHarness(settings, listOf(existingSub))
            val vm = track(LoginViewModel(harness.auth, settings))

            var done = false
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "",
                backendType = BackendType.NAVIDROME,
                serverName = "New Sub",
                allowSelfSigned = false,
                allowCleartext = true,
                addMode = true,
                existingProfileId = "p1",
                onSuccess = { done = true },
            )
            awaitCondition { done }

            val updated = harness.auth.profiles.value.single()
            assertEquals("New Sub", updated.name)
            assertEquals("kept-secret", (updated.credentials as ProfileCredentials.Subsonic).password)
        }
    }

    @Test
    fun snackbarHelpers_emitExpectedEvents() = runBlocking {
        val settings = newSettings()
        val harness = newAuthHarness(settings)
        val vm = track(LoginViewModel(harness.auth, settings))
        val events = CopyOnWriteArrayList<LoginEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { events += it } }

        vm.notifyDriveIncomplete(hasAuth = false)
        vm.notifyDriveIncomplete(hasAuth = true)
        vm.notifyIncomplete(BackendType.NAVIDROME, editMode = false)
        vm.notifyIncomplete(BackendType.JELLYFIN, editMode = false)
        vm.useDeviceFilesOnly()

        awaitSuspendCondition { events.size >= 5 }
        assertEquals(
            listOf(
                LoginEvent.Snackbar("Sign in with Google first"),
                LoginEvent.Snackbar("Choose a Music folder or turn on \"Scan all audio files\""),
                LoginEvent.Snackbar("Fill in Server URL, Username, and Password first"),
                LoginEvent.Snackbar("Fill in Server URL and Username first"),
                LoginEvent.Snackbar("Offline mode · Using local device files only"),
            ),
            events.take(5),
        )
        job.cancel()
    }

    @Test
    fun driveReadinessHelpers_enforceAuthAndFolderOrScanAll() {
        assertFalse(LoginViewModel.canTestDrive("", "tok"))
        assertFalse(LoginViewModel.canTestDrive("alice@gmail.com", ""))
        assertTrue(LoginViewModel.canTestDrive("alice@gmail.com", "tok"))
        assertTrue(LoginViewModel.canTestDrive("alice@gmail.com", "", editMode = true))

        assertFalse(
            LoginViewModel.canConnectDrive(
                accountEmail = "alice@gmail.com",
                accessToken = "tok",
                driveRootFolderId = null,
                scanAllAudio = false,
            ),
        )
        assertTrue(
            LoginViewModel.canConnectDrive(
                accountEmail = "alice@gmail.com",
                accessToken = "tok",
                driveRootFolderId = "folder-1",
                scanAllAudio = false,
            ),
        )
        assertTrue(
            LoginViewModel.canConnectDrive(
                accountEmail = "alice@gmail.com",
                accessToken = "tok",
                driveRootFolderId = null,
                scanAllAudio = true,
            ),
        )
    }

    @Test
    fun driveAuthOutcomesAndTestOnly_matchExactBannerAndSnackbarCopy() = runBlocking {
        val settings = newSettings()
        val aboutInterceptor = Interceptor { chain ->
            val req = chain.request()
            if (req.url.host == "www.googleapis.com" && req.url.encodedPath == "/drive/v3/about") {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"user":{"emailAddress":"alice@gmail.com","displayName":"Alice"}}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            } else {
                chain.proceed(req)
            }
        }
        val harness = newAuthHarness(settings, driveInterceptor = aboutInterceptor)
        val vm = track(
            LoginViewModel.forTest(
                auth = harness.auth,
                settings = settings,
                backendFactory = harness.backendFactory,
                probeRegistry = harness.probeRegistry,
            ),
        )

        vm.handleDriveAuthResult(DriveAuthResult.Cancelled)
        assertEquals(
            ConnectionBannerState.Info("Google sign-in cancelled"),
            vm.ui.value.banner,
        )
        vm.clearError()
        assertEquals(ConnectionBannerState.Idle, vm.ui.value.banner)

        vm.handleDriveAuthResult(DriveAuthResult.Unavailable("missing client id"))
        assertEquals(
            LoginViewModel.DRIVE_UNAVAILABLE_ERROR,
            (vm.ui.value.banner as ConnectionBannerState.Error).message,
        )

        vm.handleDriveAuthResult(DriveAuthResult.Error("Access denied"), fromResolution = true)
        assertEquals(
            LoginViewModel.DRIVE_CONSENT_DENIED_ERROR,
            (vm.ui.value.banner as ConnectionBannerState.Error).message,
        )

        vm.handleDriveAuthResult(
            DriveAuthResult.Authorized(
                email = "alice@gmail.com",
                accessToken = "tok-drive-1",
                expiresAtEpochMs = 1_900_000_000_000L,
            ),
        )
        assertEquals("alice@gmail.com", vm.driveState.value.accountEmail)
        assertEquals("tok-drive-1", vm.driveState.value.accessToken)
        assertEquals(ConnectionBannerState.Idle, vm.ui.value.banner)

        vm.testDriveOnly()
        awaitSuspendCondition { vm.ui.value.banner is ConnectionBannerState.Success }
        assertEquals(
            "Connection OK · Google Drive (alice@gmail.com)",
            (vm.ui.value.banner as ConnectionBannerState.Success).message,
        )
    }

    @Test
    fun driveEditMode_folderChangeClearsDriveTablesAndCovers_andStartsReindex() = runBlocking {
        val settings = newSettings()
        val aboutInterceptor = Interceptor { chain ->
            val req = chain.request()
            if (req.url.host == "www.googleapis.com" && req.url.encodedPath == "/drive/v3/about") {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"user":{"emailAddress":"alice@gmail.com","displayName":"Alice"}}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            } else {
                chain.proceed(req)
            }
        }
        val existingDrive = ServerProfile(
            id = "p-drive",
            name = "My Drive",
            type = BackendType.GOOGLE_DRIVE,
            url = BackendClientFactory.GOOGLE_DRIVE_BASE_URL,
            userId = "alice@gmail.com",
            credentials = ProfileCredentials.GoogleDrive(
                accountEmail = "alice@gmail.com",
                accessToken = "stored-drive-tok",
                expiresAtEpochMs = 1_900_000_000_000L,
            ),
            allowSelfSigned = false,
            allowCleartext = false,
            driveRootFolderId = "folder-old",
            driveRootFolderName = "Old Music",
        )
        val harness = newAuthHarness(
            settings = settings,
            initialProfiles = listOf(existingDrive),
            driveInterceptor = aboutInterceptor,
        )
        val clearedDriveProfiles = CopyOnWriteArrayList<String>()
        val clearedFavProfiles = CopyOnWriteArrayList<String>()
        val clearedHistoryProfiles = CopyOnWriteArrayList<String>()
        val clearedCoverPrefixes = CopyOnWriteArrayList<String>()
        val cancelledScans = CopyOnWriteArrayList<String>()
        val startedScans = CopyOnWriteArrayList<String>()

        val cancellationRegistry = DriveScanCancellationRegistry().apply {
            register("test") { cancelledScans += it }
        }
        val scanRepo = object : StubMusicRepo() {
            override suspend fun startScan(profileId: String): Result<Unit> {
                startedScans += profileId
                return Result.Ok(Unit)
            }
        }
        val scanCoordinator = ScanCoordinator(
            repo = scanRepo,
            libraryRefresh = LibraryRefresh(),
            messenger = UiMessenger(),
        )
        val vm = track(
            LoginViewModel.forTest(
                auth = harness.auth,
                settings = settings,
                backendFactory = harness.backendFactory,
                probeRegistry = harness.probeRegistry,
                scanCoordinator = scanCoordinator,
                driveTrackDao = FakeDriveTrackDao(clearedDriveProfiles),
                localFavoriteDao = FakeLocalFavoriteDao(clearedFavProfiles),
                localPlayHistoryDao = FakeLocalPlayHistoryDao(clearedHistoryProfiles),
                cachedCoverDao = FakeCachedCoverDao(clearedCoverPrefixes),
                driveScanCancellationRegistry = cancellationRegistry,
                libraryRefresh = LibraryRefresh(),
            ),
        )
        val events = CopyOnWriteArrayList<LoginEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { events += it } }

        vm.initDriveEditState(existingDrive)
        assertFalse(vm.needsDriveReindexOnSave(existingDrive))

        vm.setDriveFolderSelection("folder-new", "Lossless")
        assertTrue(vm.needsDriveReindexOnSave(existingDrive))

        var savedOk = false
        vm.connectGoogleDrive(
            serverName = "Updated Drive",
            addMode = true,
            existingProfileId = "p-drive",
            onSuccess = { savedOk = true },
        )
        awaitSuspendCondition { savedOk && startedScans.contains("p-drive") }

        val updated = harness.auth.profiles.value.single()
        assertEquals("Updated Drive", updated.name)
        assertEquals("folder-new", updated.driveRootFolderId)
        assertEquals("Lossless", updated.driveRootFolderName)
        assertEquals(listOf("p-drive"), cancelledScans)
        assertEquals(listOf("p-drive"), clearedDriveProfiles)
        assertEquals(listOf("p-drive"), clearedFavProfiles)
        assertEquals(listOf("p-drive"), clearedHistoryProfiles)
        assertTrue(clearedCoverPrefixes.contains("p-drive:cov:"))
        assertTrue(events.any { it == LoginEvent.Snackbar("Connected to Google Drive · indexing started") })
        job.cancel()
        scanCoordinator.cancelPolling()
    }

    @Test
    fun reloginSameServer_updatesExistingProfileInPlace_preservingIdAndPriority() = runBlocking {
        val settings = newSettings()
        LocalHttpServer { req ->
            when {
                req.path.endsWith("/Users/AuthenticateByName") ->
                    200 to """{"AccessToken":"refreshed-tok","ServerId":"srv-1","User":{"Id":"u-1","Name":"Alice"}}"""
                req.path.endsWith("/System/Info/Public") ->
                    200 to """{"ProductName":"Jellyfin","Version":"10.9.11"}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            val existing = ServerProfile(
                id = "p-keep",
                name = "Existing JF",
                type = BackendType.JELLYFIN,
                url = server.baseUrl,
                userId = "u-1",
                credentials = ProfileCredentials.Jellyfin(
                    username = "alice",
                    accessToken = "old-tok",
                    serverId = "srv-1",
                    deviceId = "dev-1",
                ),
                allowSelfSigned = false,
                allowCleartext = true,
                enabled = false,
                priority = 2,
            )
            val harness = newAuthHarness(settings, listOf(existing))
            val vm = track(LoginViewModel(harness.auth, settings))

            var done = false
            vm.testAndConnect(
                serverUrl = "${server.baseUrl}/web/",
                username = "Alice",
                password = "new-pw",
                backendType = BackendType.JELLYFIN,
                serverName = "Updated JF",
                allowSelfSigned = true,
                allowCleartext = true,
                addMode = false,
                existingProfileId = null,
                onSuccess = { done = true },
            )
            awaitSuspendCondition { done }

            val profiles = harness.auth.profiles.value
            assertEquals(1, profiles.size)
            val saved = profiles.single()
            assertEquals("p-keep", saved.id)
            assertEquals(2, saved.priority)
            assertEquals("Updated JF", saved.name)
            assertTrue(saved.enabled)
            assertTrue(saved.allowSelfSigned)
            assertEquals("refreshed-tok", (saved.credentials as ProfileCredentials.Jellyfin).accessToken)
        }
    }

    private data class RecordedReq(val path: String, val query: String, val body: String)

    private class LocalHttpServer(
        private val handler: (RecordedReq) -> Pair<Int, String>,
    ) : Closeable {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val baseUrl: String = "http://127.0.0.1:${socket.localPort}"
        @Volatile private var closed = false
        private val worker = Thread {
            while (!closed) {
                val client = try { socket.accept() } catch (_: SocketException) { break }
                client.use { conn ->
                    val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use
                    var contentLength = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                    val bodyChars = if (contentLength > 0) {
                        CharArray(contentLength).also { buf ->
                            var read = 0
                            while (read < contentLength) {
                                val n = reader.read(buf, read, contentLength - read)
                                if (n <= 0) break
                                read += n
                            }
                        }.concatToString()
                    } else {
                        ""
                    }
                    val target = requestLine.split(" ").getOrElse(1) { "/" }
                    val (status, respBody) = handler(
                        RecordedReq(
                            path = target.substringBefore('?'),
                            query = target.substringAfter('?', ""),
                            body = bodyChars,
                        ),
                    )
                    val bytes = respBody.toByteArray(Charsets.UTF_8)
                    val out = conn.getOutputStream()
                    val head = "HTTP/1.1 $status OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.US_ASCII))
                    out.write(bytes)
                    out.flush()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            closed = true
            runCatching { socket.close() }
            runCatching { worker.join(1_000) }
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?) = synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = synchronized(data) { (data[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int) = synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long) = synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float) = synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean) = synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?) = synchronized(data) { data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) updates[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) updates[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) updates[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) updates[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) updates[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                synchronized(data) {
                    if (clearAll) data.clear()
                    removals.forEach { data.remove(it) }
                    updates.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                }
                return true
            }
            override fun apply() { commit() }
        }
    }

    private open class StubMusicRepo : MusicDataRepository {
        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<Album>())
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) = Result.Ok(SearchResults())
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) = Result.Ok(SearchResults())
        override suspend fun serverInfo() = Result.Ok(ServerInfo())
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus(false, 0))
        override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("stub")
        override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("stub")
        override suspend fun artistInfo(id: String) = Result.Ok(ArtistInfo())
        override suspend fun randomSongs(size: Int, genre: String?) = Result.Ok(emptyList<Track>())
        override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("stub")
        override suspend fun starred() = Result.Ok(StarredCollection())
        override suspend fun starTrack(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Ok("pl1")
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean) = Result.Ok(Unit)
        override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
        override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Ok(SyncedLyrics.plain(""))
        override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
        override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
        override fun downloadUrl(track: Track) = ""
    }

    private class FakeDriveTrackDao(private val cleared: MutableList<String>) : DriveTrackDao {
        override suspend fun allIdsAndModTimes(profileId: String) = emptyList<DriveFileModTime>()
        override suspend fun upsertAll(tracks: List<DriveTrackEntity>) = Unit
        override suspend fun deleteByIds(songIds: List<String>) = Unit
        override suspend fun clearProfile(profileId: String) { cleared += profileId }
        override suspend fun countForProfile(profileId: String) = 0
        override suspend fun tracksForProfile(profileId: String) = emptyList<DriveTrackEntity>()
        override suspend fun trackById(songId: String): DriveTrackEntity? = null
        override suspend fun embeddedLyrics(songId: String): String? = null
        override suspend fun albumsForProfile(profileId: String) = emptyList<DriveAlbumRow>()
        override suspend fun albumsRecentFromHistory(profileId: String, limit: Int, offset: Int) = emptyList<DriveAlbumRow>()
        override suspend fun albumsFrequentFromHistory(profileId: String, limit: Int, offset: Int) = emptyList<DriveAlbumRow>()
        override suspend fun artistsForProfile(profileId: String) = emptyList<DriveArtistRow>()
        override suspend fun genresForProfile(profileId: String) = emptyList<DriveGenreRow>()
        override suspend fun tracksInAlbum(albumKey: String) = emptyList<DriveTrackEntity>()
        override suspend fun tracksByArtist(artistKey: String) = emptyList<DriveTrackEntity>()
        override suspend fun tracksByParentFolder(profileId: String, parentFolderId: String) = emptyList<DriveTrackEntity>()
        override suspend fun allSongsForProfile(profileId: String, limit: Int, offset: Int) = emptyList<DriveTrackEntity>()
        override suspend fun randomTracks(profileId: String, limit: Int, genre: String?) = emptyList<DriveTrackEntity>()
        override suspend fun searchTracks(profileId: String, query: String, limit: Int) = emptyList<DriveTrackEntity>()
        override suspend fun searchAlbums(profileId: String, query: String, limit: Int) = emptyList<DriveAlbumRow>()
        override suspend fun searchArtists(profileId: String, query: String, limit: Int) = emptyList<DriveArtistRow>()
    }

    private class FakeLocalFavoriteDao(private val cleared: MutableList<String>) : LocalFavoriteDao {
        override suspend fun all() = emptyList<LocalFavoriteEntity>()
        override suspend fun byKind(kind: String) = emptyList<LocalFavoriteEntity>()
        override suspend fun byProfile(profileId: String) = emptyList<LocalFavoriteEntity>()
        override suspend fun byProfileAndKind(profileId: String, kind: String) = emptyList<LocalFavoriteEntity>()
        override suspend fun idsByKind(kind: String) = emptyList<String>()
        override suspend fun isStarred(targetId: String) = false
        override suspend fun upsert(favorite: LocalFavoriteEntity) = Unit
        override suspend fun delete(targetId: String) = Unit
        override suspend fun clearProfile(profileId: String) { cleared += profileId }
        override suspend fun clear() = Unit
    }

    private class FakeLocalPlayHistoryDao(private val cleared: MutableList<String>) : LocalPlayHistoryDao {
        override suspend fun insert(entry: LocalPlayHistoryEntity) = 0L
        override suspend fun recentForProfile(profileId: String, limit: Int) = emptyList<LocalPlayHistoryEntity>()
        override suspend fun recent(limit: Int) = emptyList<LocalPlayHistoryEntity>()
        override suspend fun recentAlbumsForProfile(profileId: String, limit: Int, offset: Int) = emptyList<LocalAlbumPlayStats>()
        override suspend fun frequentAlbumsForProfile(profileId: String, limit: Int, offset: Int) = emptyList<LocalAlbumPlayStats>()
        override suspend fun clearProfile(profileId: String) { cleared += profileId }
        override suspend fun clear() = Unit
    }

    private class FakeCachedCoverDao(private val prefixes: MutableList<String>) : CachedCoverDao {
        override suspend fun path(id: String): String? = null
        override suspend fun upsert(cover: CachedCoverEntity) = Unit
        override suspend fun allSizes() = emptyList<Long>()
        override suspend fun delete(id: String) = Unit
        override suspend fun deleteByPrefix(prefix: String) { prefixes += prefix }
        override suspend fun clear() = Unit
    }
}
