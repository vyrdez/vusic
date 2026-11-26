package com.feishin.mobile.ui.settings

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
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.backend.BackendClientFactory
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
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ProfileHealth
import com.feishin.mobile.domain.repo.ScanStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {

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
        val file = File(tmp.root, "settings_vm_${settingsSeq++}_${UUID.randomUUID()}.preferences_pb")
        return SettingsStore(PreferenceDataStoreFactory.create { file })
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun newAuthHarness(
        settings: SettingsStore,
        authStateStore: ProfileAuthStateStore,
        initialProfiles: List<ServerProfile> = emptyList(),
    ): Pair<AuthRepository, ProfileStore> {
        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)
        if (initialProfiles.isNotEmpty()) {
            profileStore.saveAll(initialProfiles)
        }
        val probeRegistry = TransientProbeRegistry()
        val cleartextGuard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)
        val profileAuth = ProfileAuthInterceptor(profileStore, probeRegistry, authStateStore)
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
        val auth = AuthRepository(profileStore, legacyCreds, backendFactory, probeRegistry, settings, authStateStore)
        runBlocking { auth.preload() }
        return auth to profileStore
    }

    private fun sampleProfile(
        id: String,
        name: String,
        type: BackendType = BackendType.NAVIDROME,
        priority: Int = 0,
    ) = ServerProfile(
        id = id,
        name = name,
        type = type,
        url = "https://$id.example.com",
        userId = "user_$id",
        credentials = ProfileCredentials.Subsonic(username = "user_$id", password = "pw"),
        priority = priority,
    )

    private fun awaitCondition(timeoutMs: Long = 3_000L, predicate: suspend () -> Boolean) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return@runBlocking
            Thread.sleep(20)
        }
        assertTrue("Condition timed out", predicate())
    }

    @Test
    fun toggleExpanded_and_collapseAllProfiles_manageHoistedExpansionSet() {
        val settings = newSettings()
        val authStateStore = ProfileAuthStateStore()
        val (auth, _) = newAuthHarness(settings, authStateStore)
        val vm = track(SettingsViewModel(settings, FakeRepo(), auth, profileAuthStateStore = authStateStore))

        assertTrue(vm.expandedProfileIds.value.isEmpty())

        vm.toggleExpanded("p1")
        assertEquals(setOf("p1"), vm.expandedProfileIds.value)

        // Multi-expand supported
        vm.toggleExpanded("p2")
        assertEquals(setOf("p1", "p2"), vm.expandedProfileIds.value)

        // Second tap collapses p1 only
        vm.toggleExpanded("p1")
        assertEquals(setOf("p2"), vm.expandedProfileIds.value)

        vm.collapseAllProfiles()
        assertTrue(vm.expandedProfileIds.value.isEmpty())
    }

    @Test
    fun sessionExpired_statusToneMapping_and_authStatesExposure() {
        val settings = newSettings()
        val authStateStore = ProfileAuthStateStore()
        val (auth, _) = newAuthHarness(settings, authStateStore)
        val vm = track(SettingsViewModel(settings, FakeRepo(), auth, profileAuthStateStore = authStateStore))

        assertTrue(vm.authStates.value.isEmpty())

        authStateStore.markExpired("jf_1")
        assertEquals(ProfileAuthStatus.SESSION_EXPIRED, vm.authStates.value["jf_1"])

        val expiredStatus = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = ProfileHealth("jf_1", "Jellyfin", degraded = false),
            authStatus = vm.authStates.value["jf_1"] ?: ProfileAuthStatus.OK,
        )
        assertEquals(ProfileStatusTone.ERROR, expiredStatus.tone)
        assertEquals("Session expired", expiredStatus.label)

        // SessionExpired takes precedence over offline / degraded when enabled
        val expiredWhileOffline = resolveProfileStatus(
            enabled = true,
            online = false,
            testState = TestState.Idle,
            health = ProfileHealth("jf_1", "Jellyfin", degraded = true, lastError = "401"),
            authStatus = ProfileAuthStatus.SESSION_EXPIRED,
        )
        assertEquals(ProfileStatusTone.ERROR, expiredWhileOffline.tone)
        assertEquals("Session expired", expiredWhileOffline.label)

        // Disabled overrides to Disabled
        val disabledStatus = resolveProfileStatus(
            enabled = false,
            online = true,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.SESSION_EXPIRED,
        )
        assertEquals(ProfileStatusTone.OFFLINE_OR_DISABLED, disabledStatus.tone)
        assertEquals("Disabled", disabledStatus.label)

        // Clearing returns to Healthy
        authStateStore.clear("jf_1")
        val clearedStatus = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = ProfileHealth("jf_1", "Jellyfin", degraded = false),
            authStatus = vm.authStates.value["jf_1"] ?: ProfileAuthStatus.OK,
        )
        assertEquals(ProfileStatusTone.HEALTHY, clearedStatus.tone)
        assertEquals("Healthy", clearedStatus.label)
    }

    @Test
    fun removeProfile_lastProfileRule_respectsDeviceFilesStandalone() = runBlocking {
        val settings = newSettings()
        val authStateStore = ProfileAuthStateStore()
        val p1 = sampleProfile("p1", "Primary", priority = 0)
        val p2 = sampleProfile("p2", "Secondary", priority = 1)
        val (auth, profileStore) = newAuthHarness(settings, authStateStore, listOf(p1, p2))
        val vm = track(SettingsViewModel(settings, FakeRepo(), auth, profileAuthStateStore = authStateStore))

        vm.toggleExpanded("p1")
        assertTrue("p1" in vm.expandedProfileIds.value)

        var logoutCalls = 0
        // Remove first of two: stays in app, clears expanded state for p1
        vm.removeProfile("p1", keepDownloads = true) { logoutCalls++ }
        awaitCondition { vm.profiles.value.map { it.id } == listOf("p2") }
        assertFalse("p1" in vm.expandedProfileIds.value)
        assertEquals(0, logoutCalls)

        // Standalone = true: removing last profile does NOT invoke onLogout
        settings.setDeviceFilesStandalone(true)
        awaitCondition { settings.flow.first().deviceFilesStandalone }
        vm.removeProfile("p2", keepDownloads = true) { logoutCalls++ }
        awaitCondition { vm.profiles.value.isEmpty() }
        assertEquals(0, logoutCalls)

        // Standalone = false: removing last profile MUST invoke onLogout
        val p3 = sampleProfile("p3", "Third", priority = 0)
        profileStore.saveAll(listOf(p3))
        settings.setDeviceFilesStandalone(false)
        awaitCondition { vm.profiles.value.size == 1 && !settings.flow.first().deviceFilesStandalone }

        vm.removeProfile("p3", keepDownloads = false) { logoutCalls++ }
        awaitCondition { vm.profiles.value.isEmpty() && logoutCalls == 1 }
        assertEquals(1, logoutCalls)
    }

    private class FakeRepo : MusicDataRepository {
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
