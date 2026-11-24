package com.feishin.mobile.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.UiState
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.cache.CacheBytes
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.db.CachedTrackDao
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
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
import com.feishin.mobile.domain.repo.FederatedHealthState
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ProfileHealth
import com.feishin.mobile.domain.repo.ScanStatus
import com.feishin.mobile.ui.library.LibraryViewModel
import com.feishin.mobile.ui.login.LoginViewModel
import com.feishin.mobile.ui.settings.SettingsViewModel
import com.feishin.mobile.ui.settings.TestState
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MultiProfileUiViewModelTest {

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
        val file = File(tmp.root, "settings_ui_${settingsSeq++}_${UUID.randomUUID()}.preferences_pb")
        return SettingsStore(PreferenceDataStoreFactory.create { file })
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun newAuthHarness(
        settings: SettingsStore,
        initialProfiles: List<ServerProfile> = emptyList(),
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
        val baseClient = OkHttpClient.Builder()
            .sslSocketFactory(trustManager.sslContext().socketFactory, trustManager)
            .hostnameVerifier(trustManager.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .build()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val backendFactory = BackendClientFactory(json, baseClient)
        val auth = AuthRepository(profileStore, legacyCreds, backendFactory, probeRegistry, settings)
        runBlocking { auth.preload() }
        return AuthHarness(auth, profileStore)
    }

    private data class AuthHarness(
        val auth: AuthRepository,
        val store: ProfileStore,
    )

    private fun sampleProfile(
        id: String,
        name: String,
        type: BackendType = BackendType.NAVIDROME,
        url: String = "https://$id.example.com",
        priority: Int = 0,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = name,
        type = type,
        url = url,
        userId = "user_$id",
        credentials = ProfileCredentials.Subsonic(username = "user_$id", password = "pw"),
        allowSelfSigned = false,
        allowCleartext = true,
        enabled = enabled,
        priority = priority,
    )

    private fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(20)
        }
        assertTrue("Condition timed out", predicate())
    }

    // ---------- LoginViewModel ----------

    @Test
    fun loginViewModel_useDeviceFilesOnly_setsStandaloneAndDeviceFilter() = runBlocking {
        val settings = newSettings()
        val harness = newAuthHarness(settings)
        val vm = track(LoginViewModel(harness.auth, settings))

        vm.useDeviceFilesOnly()

        awaitCondition {
            val s = runBlocking { settings.flow.first() }
            s.showDeviceFiles && s.deviceFilesStandalone && s.librarySourceFilter == "DEVICE"
        }
        val finalSettings = settings.flow.first()
        assertTrue(finalSettings.showDeviceFiles)
        assertTrue(finalSettings.deviceFilesStandalone)
        assertEquals("DEVICE", finalSettings.librarySourceFilter)
        assertEquals("DEVICE", finalSettings.libraryViewMode)
    }

    @Test
    fun loginViewModel_addModeConnect_preservesExistingProfilesAndInvokesOnSuccess() {
        val settings = newSettings()
        val existing = sampleProfile("p1", "Primary Navidrome", priority = 0)
        val harness = newAuthHarness(settings, listOf(existing))
        val vm = track(LoginViewModel(harness.auth, settings))

        var addedCount = 0
        LocalSubsonicServer { req ->
            when {
                req.path.endsWith("/ping.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                req.path.endsWith("/getOpenSubsonicExtensions.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonicExtensions":[{"name":"songLyrics","versions":[1]}]}}"""
                else -> 404 to """{"subsonic-response":{"status":"failed"}}"""
            }
        }.use { server ->
            vm.testAndConnect(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "secret",
                backendType = BackendType.OPENSUBSONIC,
                serverName = "Gonic Studio",
                allowSelfSigned = false,
                allowCleartext = true,
                addMode = true,
                onSuccess = { addedCount++ },
            )
            awaitCondition { addedCount == 1 && harness.auth.profiles.value.size == 2 }
        }

        val profiles = harness.auth.profiles.value
        assertEquals(2, profiles.size)
        assertEquals("p1", profiles[0].id)
        val added = profiles[1]
        assertEquals("Gonic Studio", added.name)
        assertEquals(BackendType.OPENSUBSONIC, added.type)
        assertTrue(added.openSubsonicExtensions.songLyrics)
    }

    // ---------- SettingsViewModel ----------

    @Test
    fun settingsViewModel_reorderMoveAndToggleProfile_updatesStoreAndPriorities() {
        val settings = newSettings()
        val p1 = sampleProfile("p1", "First", BackendType.NAVIDROME, priority = 0)
        val p2 = sampleProfile("p2", "Second", BackendType.OPENSUBSONIC, priority = 1)
        val harness = newAuthHarness(settings, listOf(p1, p2))
        val repo = FakeMultiProfileRepo()
        val vm = track(SettingsViewModel(settings, repo, harness.auth))

        vm.moveProfile("p2", -1)
        awaitCondition { vm.profiles.value.map { it.id } == listOf("p2", "p1") }
        assertEquals(0, vm.profiles.value[0].priority)
        assertEquals(1, vm.profiles.value[1].priority)

        vm.reorderProfiles(listOf("p1", "p2"))
        awaitCondition { vm.profiles.value.map { it.id } == listOf("p1", "p2") }

        vm.setProfileEnabled("p2", false)
        awaitCondition { vm.profiles.value.first { it.id == "p2" }.enabled.not() }
        assertFalse(vm.profiles.value.first { it.id == "p2" }.enabled)
    }

    @Test
    fun settingsViewModel_removeProfile_respectsLastProfileAndStandaloneFlag() = runBlocking {
        val settings = newSettings()
        val p1 = sampleProfile("p1", "One", priority = 0)
        val p2 = sampleProfile("p2", "Two", priority = 1)
        val harness = newAuthHarness(settings, listOf(p1, p2))
        val repo = FakeMultiProfileRepo()
        val vm = track(SettingsViewModel(settings, repo, harness.auth))

        settings.setLibrarySourceFilter("PROFILE:p1")
        var allRemovedCount = 0

        vm.removeProfile("p1", keepDownloads = true) { allRemovedCount++ }
        awaitCondition {
            vm.profiles.value.map { it.id } == listOf("p2") &&
                runBlocking { settings.flow.first().librarySourceFilter == "ALL" }
        }
        assertEquals(0, allRemovedCount)
        assertEquals("ALL", settings.flow.first().librarySourceFilter)

        // Removing last profile when standalone=true should NOT trigger onAllRemoved
        settings.setDeviceFilesStandalone(true)
        vm.removeProfile("p2", keepDownloads = false) { allRemovedCount++ }
        awaitCondition { vm.profiles.value.isEmpty() }
        assertEquals(0, allRemovedCount)

        // With standalone=false and last profile removed, onAllRemoved MUST fire
        val p3 = sampleProfile("p3", "Three", priority = 0)
        harness.store.saveAll(listOf(p3))
        settings.setDeviceFilesStandalone(false)
        awaitCondition { vm.profiles.value.size == 1 }

        vm.removeProfile("p3", keepDownloads = true) { allRemovedCount++ }
        awaitCondition { vm.profiles.value.isEmpty() && allRemovedCount == 1 }
        assertEquals(1, allRemovedCount)
    }

    @Test
    fun settingsViewModel_testProfileConnection_updatesTestStateAndExtensions() {
        val settings = newSettings()
        LocalSubsonicServer { req ->
            when {
                req.path.endsWith("/ping.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                req.path.endsWith("/getOpenSubsonicExtensions.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonicExtensions":[{"name":"formPost","versions":[1]}]}}"""
                else -> 404 to """{"subsonic-response":{"status":"failed"}}"""
            }
        }.use { server ->
            val p1 = sampleProfile("p1", "Gonic", BackendType.OPENSUBSONIC, url = server.baseUrl, priority = 0)
            val harness = newAuthHarness(settings, listOf(p1))
            val repo = FakeMultiProfileRepo()
            val vm = track(SettingsViewModel(settings, repo, harness.auth))

            vm.testProfileConnection(p1)
            awaitCondition { vm.profileTestStates.value["p1"] is TestState.Ok }
            assertEquals(TestState.Ok, vm.profileTestStates.value["p1"])
            awaitCondition { vm.profiles.value.single().openSubsonicExtensions.formPost }
            assertTrue(vm.profiles.value.single().openSubsonicExtensions.formPost)
        }
    }

    // ---------- LibraryViewModel ----------

    @Test
    fun libraryViewModel_sourceFilterSelection_persistsRefreshesAndEmitsLogicalReload() = runBlocking {
        val settings = newSettings()
        val p1 = sampleProfile("p1", "Home Navidrome", BackendType.NAVIDROME, priority = 0)
        val p2 = sampleProfile("p2", "Studio Gonic", BackendType.OPENSUBSONIC, priority = 1)
        val harness = newAuthHarness(settings, listOf(p1, p2))
        val repo = FakeMultiProfileRepo(settings)
        val refresh = LibraryRefresh()
        val emittedKeys = mutableListOf<String>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            refresh.refreshed.collect { emittedKeys.add(it) }
        }

        val vm = track(
            LibraryViewModel(
                repo = repo,
                playlistEvents = PlaylistEvents(),
                settings = settings,
                cachedTracks = CachedTrackStore(FakeCachedTrackDao(), FakeCacheBytes()),
                refresh = refresh,
                savedStateHandle = SavedStateHandle(),
                auth = harness.auth,
            ),
        )

        awaitCondition { vm.albums.value is UiState.Data }
        val allAlbums = (vm.albums.value as UiState.Data).value
        assertEquals(2, allAlbums.size)
        assertEquals(2, vm.profiles.value.size)

        vm.setLibrarySourceFilter("PROFILE:p2")
        awaitCondition {
            vm.librarySourceFilter.value == "PROFILE:p2" &&
                (vm.albums.value as? UiState.Data)?.value?.singleOrNull()?.id == "p2:al2"
        }
        assertEquals("PROFILE:p2", settings.flow.first().librarySourceFilter)
        assertTrue(emittedKeys.contains(LibraryViewModel.KEY_SOURCE_FILTER_RELOAD))

        vm.setLibrarySourceFilter("TYPE:NAVIDROME")
        awaitCondition {
            vm.librarySourceFilter.value == "TYPE:NAVIDROME" &&
                (vm.albums.value as? UiState.Data)?.value?.singleOrNull()?.id == "p1:al1"
        }

        val callsBeforeDevice = repo.albumCalls.get()
        vm.setLibrarySourceFilter("DEVICE")
        awaitCondition { vm.librarySourceFilter.value == "DEVICE" }
        assertEquals("DEVICE", settings.flow.first().librarySourceFilter)
        // Selecting DEVICE switches Library to DeviceFilesScreen without blanking remote reads
        assertEquals(callsBeforeDevice, repo.albumCalls.get())
        assertTrue(vm.healthState.value.profiles["p2"]?.degraded == false)

        collectJob.cancel()
    }

    @Test
    fun libraryViewModel_concurrentResetAndPagination_neverDuplicatesAlbumIds() = runBlocking {
        val settings = newSettings()
        val p1 = sampleProfile("p1", "Home Navidrome", BackendType.NAVIDROME, priority = 0)
        val p2 = sampleProfile("p2", "Studio Gonic", BackendType.OPENSUBSONIC, priority = 1)
        val harness = newAuthHarness(settings, listOf(p1, p2))
        val refresh = LibraryRefresh()

        val p2Album = Album("p2:al2", "OpenSubsonic Album", "Art2", null, null, 1, 60, 0, 2024, "Jazz", false, "p2", "Studio Gonic")
        val p2AlbumB = Album("p2:al3", "OpenSubsonic Second", "Art3", null, null, 1, 60, 0, 2024, "Jazz", false, "p2", "Studio Gonic")

        val call1Started = CompletableDeferred<Unit>()
        val call1Gate = CompletableDeferred<Unit>()
        val call2Started = CompletableDeferred<Unit>()
        val call2Gate = CompletableDeferred<Unit>()
        val stage = AtomicInteger(0)

        val repo = FakeMultiProfileRepo(settings) { offset, filter ->
            when (stage.get()) {
                0 -> {
                    stage.compareAndSet(0, 1)
                    call1Started.complete(Unit)
                    call1Gate.await()
                    null // fall back to default ALL list (p1:al1, p2:al2)
                }
                1 -> {
                    if (filter == "PROFILE:p2" && offset == 0) {
                        stage.compareAndSet(1, 2)
                        call2Started.complete(Unit)
                        call2Gate.await()
                        listOf(p2Album, p2Album)
                    } else {
                        null
                    }
                }
                2 -> {
                    if (offset > 0) listOf(p2Album, p2AlbumB) else listOf(p2Album, p2AlbumB)
                }
                else -> listOf(p2Album, p2AlbumB)
            }
        }

        val vm = track(
            LibraryViewModel(
                repo = repo,
                playlistEvents = PlaylistEvents(),
                settings = settings,
                cachedTracks = CachedTrackStore(FakeCachedTrackDao(), FakeCacheBytes()),
                refresh = refresh,
                savedStateHandle = SavedStateHandle(),
                auth = harness.auth,
            ),
        )

        call1Started.await()
        assertEquals(1, repo.albumCalls.get())

        // Overlapping pagination while initial reset=true load is still in flight must be ignored.
        vm.loadAlbums(reset = false)
        assertEquals(1, repo.albumCalls.get())

        // Switching source filter cancels the in-flight reset and bumps generation.
        vm.setLibrarySourceFilter("PROFILE:p2")
        awaitCondition { call2Started.isCompleted }

        // Even if the stale call resolves first, it must not pollute or duplicate the new list.
        call1Gate.complete(Unit)
        call2Gate.complete(Unit)

        awaitCondition {
            val list = (vm.albums.value as? UiState.Data)?.value ?: return@awaitCondition false
            list.map { it.id } == listOf("p2:al2")
        }

        // Next page containing an overlapping album ID must deduplicate cleanly.
        vm.loadAlbums(reset = false)
        awaitCondition {
            val list = (vm.albums.value as? UiState.Data)?.value ?: return@awaitCondition false
            list.map { it.id } == listOf("p2:al2", "p2:al3")
        }
        val pagedIds = (vm.albums.value as UiState.Data).value.map { it.id }
        assertEquals(pagedIds.distinct(), pagedIds)

        // Scoped cache key refresh triggers a clean reset reload with no duplicates.
        val callsBeforeScopedReload = repo.albumCalls.get()
        refresh.emitRefreshed("p2:albums:alphabeticalByName:0")
        awaitCondition { repo.albumCalls.get() > callsBeforeScopedReload }
        val finalIds = (vm.albums.value as UiState.Data).value.map { it.id }
        assertEquals(listOf("p2:al2", "p2:al3"), finalIds)
    }

    // ---------- Local Test Fakes ----------

    private class FakeMultiProfileRepo(
        private val settings: SettingsStore? = null,
        private val onAlbumList: (suspend (offset: Int, filter: String) -> List<Album>?)? = null,
    ) : MusicDataRepository {
        val albumCalls = AtomicInteger(0)
        private val p1Album = Album("p1:al1", "Navidrome Album", "Art1", null, null, 1, 60, 0, 2024, "Rock", false, "p1", "Home Navidrome")
        private val p2Album = Album("p2:al2", "OpenSubsonic Album", "Art2", null, null, 1, 60, 0, 2024, "Jazz", false, "p2", "Studio Gonic")

        override val healthState = MutableStateFlow(
            FederatedHealthState(
                mapOf(
                    "p1" to ProfileHealth("p1", "Home Navidrome", degraded = false),
                    "p2" to ProfileHealth("p2", "Studio Gonic", degraded = false),
                ),
            ),
        )

        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> {
            albumCalls.incrementAndGet()
            val filter = settings?.latestSettings?.librarySourceFilter ?: "ALL"
            val custom = onAlbumList?.invoke(offset, filter)
            if (custom != null) return Result.Ok(custom)
            val list = when {
                filter == "PROFILE:p2" || filter == "TYPE:OPENSUBSONIC" -> listOf(p2Album)
                filter == "PROFILE:p1" || filter == "TYPE:NAVIDROME" -> listOf(p1Album)
                else -> listOf(p1Album, p2Album)
            }
            return Result.Ok(list)
        }
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) = Result.Ok(SearchResults())
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) = Result.Ok(SearchResults())
        override suspend fun serverInfo() = Result.Ok(ServerInfo("1.16.1", "Test", emptyList()))
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus(false, 0))
        override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("unused")
        override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("unused")
        override suspend fun artistInfo(id: String) = Result.Ok(ArtistInfo())
        override suspend fun randomSongs(size: Int, genre: String?) = Result.Ok(emptyList<Track>())
        override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("unused")
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

    private class FakeCacheBytes : CacheBytes {
        override fun isCached(cacheKey: String, sizeBytes: Long) = false
        override fun removeResource(cacheKey: String) = Unit
    }

    private class FakeCachedTrackDao : CachedTrackDao {
        private val rows = mutableMapOf<String, CachedTrackEntity>()
        override fun observeAll(): Flow<List<CachedTrackEntity>> = MutableStateFlow(emptyList())
        override suspend fun all() = rows.values.toList()
        override suspend fun byProfile(profileId: String) = rows.values.filter { it.profileId == profileId }
        override suspend fun byId(id: String) = rows[id]
        override suspend fun upsert(track: CachedTrackEntity) { rows[track.songId] = track }
        override suspend fun touchPlayed(id: String, now: Long) = Unit
        override suspend fun delete(id: String) { rows.remove(id) }
        override suspend fun deleteByProfile(profileId: String) { rows.entries.removeIf { it.value.profileId == profileId } }
        override suspend fun clear() { rows.clear() }
        override suspend fun count() = rows.size
    }

    private data class RecordedReq(val path: String, val query: String)

    private class LocalSubsonicServer(
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
                    val line = reader.readLine() ?: return@use
                    val target = line.split(" ").getOrElse(1) { "/" }
                    val (status, body) = handler(RecordedReq(target.substringBefore('?'), target.substringAfter('?', "")))
                    val bytes = body.toByteArray(Charsets.UTF_8)
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
}
