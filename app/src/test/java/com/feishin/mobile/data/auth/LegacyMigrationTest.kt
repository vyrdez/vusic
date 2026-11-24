package com.feishin.mobile.data.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.DynamicTrustManager
import com.feishin.mobile.data.remote.HostRewriteInterceptor
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newSettingsStore(scope: CoroutineScope): SettingsStore =
        SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun testJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun legacyAuthMigratesToP1PreservingTlsFlagsAndIsIdempotent() = runTest {
        val settings = newSettingsStore(backgroundScope)
        settings.setAllowSelfSigned(true)
        settings.setAllowCleartext(false)

        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        legacyPrefs.edit()
            .putString("serverUrl", "https://music.example.com/navidrome/")
            .putString("username", "alice")
            .putString("password", "hunter2")
            .commit()

        val legacyCreds = CredentialsStore(stubContext())
        val store = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)

        store.preload()

        val migrated = store.profiles.value
        assertEquals(1, migrated.size)
        val p1 = migrated.single()
        assertEquals("p1", p1.id)
        assertEquals("music.example.com", p1.name)
        assertEquals(BackendType.NAVIDROME, p1.type)
        assertEquals("https://music.example.com/navidrome", p1.url)
        assertEquals("alice", p1.userId)
        assertEquals(ProfileCredentials.Subsonic("alice", "hunter2"), p1.credentials)
        assertTrue(p1.allowSelfSigned)
        assertFalse(p1.allowCleartext)
        assertTrue(p1.enabled)
        assertEquals(0, p1.priority)

        assertTrue(profilePrefs.getBoolean(ProfileStore.KEY_LEGACY_MIGRATED, false))
        assertNotNull(profilePrefs.getString(ProfileStore.KEY_PROFILES_JSON, null))
        assertNull(legacyPrefs.getString("serverUrl", null))
        assertNull(legacyPrefs.getString("username", null))
        assertNull(legacyPrefs.getString("password", null))

        // Repopulate legacy prefs to prove a 2nd preload is strictly idempotent.
        legacyPrefs.edit()
            .putString("serverUrl", "https://other.example.com")
            .putString("username", "bob")
            .putString("password", "pw")
            .commit()

        store.preload()
        assertEquals(listOf(p1), store.profiles.value)
        assertEquals("https://other.example.com", legacyPrefs.getString("serverUrl", null))
    }

    @Test
    fun legacyAuthIsNotClearedWhenProfileCommitFails() = runTest {
        val settings = newSettingsStore(backgroundScope)
        val profilePrefs = InMemorySharedPreferences(failCommit = true)
        val legacyPrefs = InMemorySharedPreferences()
        legacyPrefs.edit()
            .putString("serverUrl", "https://music.example.com")
            .putString("username", "alice")
            .putString("password", "hunter2")
            .commit()

        val store = ProfileStore(profilePrefs, CredentialsStore(stubContext()), settings, legacyPrefs)
        store.preload()

        assertTrue(store.profiles.value.isEmpty())
        assertEquals("https://music.example.com", legacyPrefs.getString("serverUrl", null))
        assertEquals("alice", legacyPrefs.getString("username", null))
        assertEquals("hunter2", legacyPrefs.getString("password", null))
    }

    @Test
    fun connectSubsonicRegistersTransientProbeDuringPingAndClearsAfterSuccess() = runTest {
        val settings = newSettingsStore(backgroundScope)
        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)
        val probeRegistry = TransientProbeRegistry()
        val baseClient = OkHttpClient.Builder()
            .addInterceptor(HostRewriteInterceptor(legacyCreds))
            .addInterceptor(AuthInterceptor(legacyCreds))
            .build()
        val backendFactory = BackendClientFactory(testJson(), baseClient, DynamicTrustManager(settings))
        val auth = AuthRepository(profileStore, legacyCreds, backendFactory, probeRegistry, settings)
        auth.preload()

        var observedProbeIdDuringPing: String? = null
        var probeFoundByHostDuringPing: ServerProfile? = null
        var storeSizeDuringPing = -1

        LocalSubsonicServer { req ->
            when {
                req.path.endsWith("/ping.view") -> {
                    observedProbeIdDuringPing = req.headers[BackendClientFactory.HEADER_PROFILE_ID.lowercase()]
                    probeFoundByHostDuringPing = probeRegistry.findByHost("127.0.0.1", req.serverPort)
                    storeSizeDuringPing = profileStore.profiles.value.size
                    assertTrue(req.query.contains("u=alice"))
                    assertTrue(req.query.contains("t="))
                    assertTrue(req.query.contains("s="))
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                }
                req.path.endsWith("/getOpenSubsonicExtensions.view") -> {
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonicExtensions":[{"name":"songLyrics","versions":[1]},{"name":"formPost","versions":[1]}]}}"""
                }
                else -> 404 to """{"subsonic-response":{"status":"failed"}}"""
            }
        }.use { server ->
            val result = auth.connectSubsonic(
                type = BackendType.OPENSUBSONIC,
                name = "Local Test",
                serverUrl = server.baseUrl,
                username = "alice",
                password = "secret",
                allowSelfSigned = false,
                allowCleartext = true,
            )

            assertTrue(result is Result.Ok)
            val profile = (result as Result.Ok).value
            assertEquals("p1", profile.id)
            assertEquals("Local Test", profile.name)
            assertEquals(BackendType.OPENSUBSONIC, profile.type)
            assertTrue(profile.openSubsonicExtensions.songLyrics)
            assertTrue(profile.openSubsonicExtensions.formPost)
            assertEquals(listOf("songLyrics", "formPost"), profile.openSubsonicExtensions.rawNames)

            val probeId = observedProbeIdDuringPing
            assertNotNull(probeId)
            assertTrue(probeId!!.startsWith(TransientProbeRegistry.PROBE_PREFIX))
            assertNotNull(probeFoundByHostDuringPing)
            assertEquals(probeId, probeFoundByHostDuringPing?.id)
            assertEquals(0, storeSizeDuringPing)

            // Probe must be cleaned up after completion.
            assertNull(probeRegistry.get(probeId))
            assertNull(probeRegistry.findByHost("127.0.0.1", server.port))
            assertEquals(listOf(profile), auth.profiles.value)
        }
    }

    @Test
    fun failedProbeAndProbeOnlyRemoveTransientProbeWithoutMutatingProfileStore() = runTest {
        val settings = newSettingsStore(backgroundScope)
        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)
        val probeRegistry = TransientProbeRegistry()
        val backendFactory = BackendClientFactory(testJson(), OkHttpClient(), DynamicTrustManager(settings))
        val auth = AuthRepository(profileStore, legacyCreds, backendFactory, probeRegistry, settings)
        auth.preload()

        val observedProbes = CopyOnWriteArrayList<String>()
        var failPing = true

        LocalSubsonicServer { req ->
            val probeId = req.headers[BackendClientFactory.HEADER_PROFILE_ID.lowercase()]
            if (probeId != null) {
                observedProbes.add(probeId)
                assertNotNull(probeRegistry.get(probeId))
            }
            if (req.path.endsWith("/ping.view")) {
                if (failPing) {
                    200 to """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}"""
                } else {
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                }
            } else {
                404 to """{"subsonic-response":{"status":"failed"}}"""
            }
        }.use { server ->
            val failed = auth.connectSubsonic(
                type = BackendType.NAVIDROME,
                name = "",
                serverUrl = server.baseUrl,
                username = "alice",
                password = "wrong",
                allowSelfSigned = false,
                allowCleartext = true,
            )
            assertTrue(failed is Result.Err)
            assertTrue(profileStore.profiles.value.isEmpty())
            assertEquals(1, observedProbes.size)
            assertNull(probeRegistry.get(observedProbes.first()))
            assertNull(probeRegistry.findByHost("127.0.0.1", server.port))

            failPing = false
            val probeOk = auth.probeSubsonic(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "right",
                allowSelfSigned = false,
                allowCleartext = true,
            )
            assertTrue(probeOk is Result.Ok)
            assertTrue(profileStore.profiles.value.isEmpty())
            observedProbes.forEach { id -> assertNull(probeRegistry.get(id)) }
        }
    }

    @Test
    fun settingsStoreMultiBackendFieldsDefaultAndClamp() = runTest {
        val store = newSettingsStore(backgroundScope)
        val initial = store.flow.first()
        assertFalse(initial.deviceFilesStandalone)
        assertEquals("ALL", initial.librarySourceFilter)
        assertEquals(4_000L, initial.federatedTimeoutMs)

        store.setDeviceFilesStandalone(true)
        store.setLibrarySourceFilter("p1")
        store.setFederatedTimeoutMs(500L)
        val clampedLow = store.flow.first()
        assertTrue(clampedLow.deviceFilesStandalone)
        assertEquals("p1", clampedLow.librarySourceFilter)
        assertEquals(2_000L, clampedLow.federatedTimeoutMs)

        store.setFederatedTimeoutMs(99_000L)
        assertEquals(10_000L, store.flow.first().federatedTimeoutMs)
    }

    private data class RecordedRequest(
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val serverPort: Int,
    )

    private class LocalSubsonicServer(
        private val handler: (RecordedRequest) -> Pair<Int, String>,
    ) : Closeable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        val baseUrl: String = "http://127.0.0.1:$port"

        @Volatile
        private var closed = false

        private val worker = Thread {
            while (!closed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                client.use { conn ->
                    val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use
                    val target = requestLine.split(" ").getOrElse(1) { "/" }
                    val path = target.substringBefore('?')
                    val query = target.substringAfter('?', "")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            headers[line.substring(0, sep).trim().lowercase()] =
                                line.substring(sep + 1).trim()
                        }
                    }
                    val (status, body) = handler(RecordedRequest(path, query, headers, port))
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val out = conn.getOutputStream()
                    val head = "HTTP/1.1 $status OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
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

    private class InMemorySharedPreferences(
        var failCommit: Boolean = false,
    ) : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?): String? =
            synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(data) { (data[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int): Int =
            synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long): Long =
            synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float): Float =
            synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?): Boolean = synchronized(data) { data.containsKey(key) }
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
                if (failCommit) return false
                synchronized(data) {
                    if (clearAll) data.clear()
                    removals.forEach { data.remove(it) }
                    updates.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
