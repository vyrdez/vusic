package com.feishin.mobile.data.remote.drive

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveTokenAuthenticatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private fun newSettingsStore(): SettingsStore =
        SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun newProfileStore(settings: SettingsStore): ProfileStore =
        ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
            legacyPrefs = InMemorySharedPreferences(),
        )

    private fun driveProfile(
        id: String,
        email: String,
        accessToken: String,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = "Drive ($email)",
        type = BackendType.GOOGLE_DRIVE,
        url = "https://www.googleapis.com",
        userId = email,
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = email,
            accessToken = accessToken,
        ),
        allowSelfSigned = false,
        allowCleartext = false,
        enabled = enabled,
    )

    @Test
    fun concurrent401PerformsSingleFlightClearTokenAndRetriesWithFreshToken() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val authStateStore = ProfileAuthStateStore()
        profileStore.saveAll(listOf(driveProfile("gd1", "alice@example.com", "stale-tok-1")))

        val refreshCalls = AtomicInteger(0)
        val clearedTokens = CopyOnWriteArrayList<Pair<String, String>>()
        val refresher = DriveTokenRefresher { email, failedToken ->
            refreshCalls.incrementAndGet()
            clearedTokens.add(email to failedToken)
            Thread.sleep(60)
            DriveRefreshOutcome.Refreshed("fresh-tok-2", 9_999_999L)
        }

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return if (request.getHeader("Authorization") == "Bearer fresh-tok-2") {
                        MockResponse().setResponseCode(200).setBody("{\"user\":{}}")
                    } else {
                        MockResponse().setResponseCode(401)
                    }
                }
            }
            server.start()

            val authenticator = DriveTokenAuthenticator(
                profileStore = profileStore,
                profileAuthStateStore = authStateStore,
                tokenRefresher = refresher,
                isDriveOrigin = { true },
            )
            val client = OkHttpClient.Builder()
                .authenticator(authenticator)
                .build()

            val codes = (1..3).map {
                async(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url(server.url("/drive/v3/about"))
                        .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer stale-tok-1")
                        .tag(ProfileIdTag::class.java, ProfileIdTag("gd1"))
                        .build()
                    client.newCall(req).execute().use { it.code }
                }
            }.awaitAll()

            assertEquals(listOf(200, 200, 200), codes)
            assertEquals(1, refreshCalls.get())
            assertEquals(listOf("alice@example.com" to "stale-tok-1"), clearedTokens)
            val updated = profileStore.byId("gd1")?.credentials as ProfileCredentials.GoogleDrive
            assertEquals("fresh-tok-2", updated.accessToken)
            assertEquals(9_999_999L, updated.expiresAtEpochMs)
            assertEquals(ProfileAuthStatus.OK, authStateStore.statusFor("gd1"))
        }
    }

    @Test
    fun resolutionRequiredMarksSessionExpiredAndStopsRetrying() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val authStateStore = ProfileAuthStateStore()
        profileStore.saveAll(listOf(driveProfile("gd1", "alice@example.com", "stale-tok-1")))

        val refreshCalls = AtomicInteger(0)
        val refresher = DriveTokenRefresher { _, _ ->
            refreshCalls.incrementAndGet()
            DriveRefreshOutcome.ResolutionRequired
        }

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()

            val authenticator = DriveTokenAuthenticator(
                profileStore = profileStore,
                profileAuthStateStore = authStateStore,
                tokenRefresher = refresher,
                isDriveOrigin = { true },
            )
            val client = OkHttpClient.Builder()
                .authenticator(authenticator)
                .build()

            val code = client.newCall(
                Request.Builder()
                    .url(server.url("/drive/v3/about"))
                    .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer stale-tok-1")
                    .tag(ProfileIdTag::class.java, ProfileIdTag("gd1"))
                    .build(),
            ).execute().use { it.code }

            assertEquals(401, code)
            assertEquals(1, refreshCalls.get())
            assertEquals(1, server.requestCount)
            assertEquals(ProfileAuthStatus.SESSION_EXPIRED, authStateStore.statusFor("gd1"))
        }
    }

    @Test
    fun unchangedTokenAndMaxRetryCapMarkSessionExpired() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val authStateStore = ProfileAuthStateStore()
        profileStore.saveAll(listOf(driveProfile("gd1", "alice@example.com", "tok-1")))

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()

            // 1. Refresh returns identical token -> marks SESSION_EXPIRED without retrying.
            val sameTokenAuth = DriveTokenAuthenticator(
                profileStore = profileStore,
                profileAuthStateStore = authStateStore,
                tokenRefresher = { _, _ -> DriveRefreshOutcome.Refreshed("tok-1", 100L) },
                isDriveOrigin = { true },
            )
            val client1 = OkHttpClient.Builder().authenticator(sameTokenAuth).build()
            val code1 = client1.newCall(
                Request.Builder()
                    .url(server.url("/drive/v3/about"))
                    .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer tok-1")
                    .tag(ProfileIdTag::class.java, ProfileIdTag("gd1"))
                    .build(),
            ).execute().use { it.code }
            assertEquals(401, code1)
            assertEquals(1, server.requestCount)
            assertEquals(ProfileAuthStatus.SESSION_EXPIRED, authStateStore.statusFor("gd1"))
        }

        // 2. Retry with new token still returns 401 -> max-retry >= 2 stops and marks SESSION_EXPIRED.
        authStateStore.clear("gd1")
        profileStore.saveAll(listOf(driveProfile("gd1", "alice@example.com", "tok-1")))
        val refreshCount = AtomicInteger(0)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()

            val authenticator = DriveTokenAuthenticator(
                profileStore = profileStore,
                profileAuthStateStore = authStateStore,
                tokenRefresher = { _, _ ->
                    refreshCount.incrementAndGet()
                    DriveRefreshOutcome.Refreshed("tok-2", 200L)
                },
                isDriveOrigin = { true },
            )
            val client2 = OkHttpClient.Builder().authenticator(authenticator).build()
            val code2 = client2.newCall(
                Request.Builder()
                    .url(server.url("/drive/v3/about"))
                    .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer tok-1")
                    .tag(ProfileIdTag::class.java, ProfileIdTag("gd1"))
                    .build(),
            ).execute().use { it.code }

            assertEquals(401, code2)
            assertEquals(2, server.requestCount)
            assertEquals(1, refreshCount.get())
            assertEquals(ProfileAuthStatus.SESSION_EXPIRED, authStateStore.statusFor("gd1"))
        }
    }

    @Test
    fun multiDriveProfileIdTagRoutes401ToExactProfileAndRejectsAmbiguousUntagged() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val authStateStore = ProfileAuthStateStore()
        profileStore.saveAll(
            listOf(
                driveProfile("gd1", "first@example.com", "tok-a"),
                driveProfile("gd2", "second@example.com", "tok-b"),
            ),
        )

        val refreshedEmails = CopyOnWriteArrayList<Pair<String, String>>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return if (request.getHeader("Authorization") == "Bearer tok-b-fresh") {
                        MockResponse().setResponseCode(200)
                    } else {
                        MockResponse().setResponseCode(401)
                    }
                }
            }
            server.start()

            val authenticator = DriveTokenAuthenticator(
                profileStore = profileStore,
                profileAuthStateStore = authStateStore,
                tokenRefresher = { email, failed ->
                    refreshedEmails.add(email to failed)
                    DriveRefreshOutcome.Refreshed("tok-b-fresh", 555L)
                },
                isDriveOrigin = { true },
            )
            val client = OkHttpClient.Builder().authenticator(authenticator).build()

            // Tagged with gd2 -> only gd2 refreshes.
            val codeTagged = client.newCall(
                Request.Builder()
                    .url(server.url("/drive/v3/files"))
                    .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer tok-b")
                    .tag(ProfileIdTag::class.java, ProfileIdTag("gd2"))
                    .build(),
            ).execute().use { it.code }
            assertEquals(200, codeTagged)
            assertEquals(listOf("second@example.com" to "tok-b"), refreshedEmails)
            assertEquals(
                "tok-a",
                (profileStore.byId("gd1")?.credentials as ProfileCredentials.GoogleDrive).accessToken,
            )
            assertEquals(
                "tok-b-fresh",
                (profileStore.byId("gd2")?.credentials as ProfileCredentials.GoogleDrive).accessToken,
            )

            // Untagged with 2 enabled Drive profiles -> must not guess.
            val codeUntagged = client.newCall(
                Request.Builder()
                    .url(server.url("/drive/v3/files"))
                    .build(),
            ).execute().use { it.code }
            assertEquals(401, codeUntagged)
            assertEquals(1, refreshedEmails.size)
        }
    }

    @Test
    fun zeroBearerLeakageOffGoogleapisAndBlankTokenSendsNoAuthorizationHeader() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()
        val authStateStore = ProfileAuthStateStore()
        profileStore.saveAll(
            listOf(
                driveProfile("gd_valid", "valid@example.com", "secret-drive-tok"),
                driveProfile("gd_blank", "blank@example.com", "   "),
            ),
        )

        val refreshCalls = AtomicInteger(0)
        val defaultAuthenticator = DriveTokenAuthenticator(
            profileStore = profileStore,
            profileAuthStateStore = authStateStore,
            tokenRefresher = { _, _ ->
                refreshCalls.incrementAndGet()
                DriveRefreshOutcome.Refreshed("should-not-run", 1L)
            },
        )
        val profileAuth = ProfileAuthInterceptor(profileStore, probeRegistry, authStateStore)
        val cleartextGuard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)

        // 1. Non-googleapis.com MockWebServer: neither ProfileAuthInterceptor nor default DriveTokenAuthenticator leaks/refreshes.
        settings.setAllowCleartext(true)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()

            // Register a non-Drive hostless call (ProfileIdTag points to gd_valid, but target is 127.0.0.1).
            val client = OkHttpClient.Builder()
                .addInterceptor(profileAuth)
                .authenticator(defaultAuthenticator)
                .addNetworkInterceptor(profileAuth.redirectGuard())
                .build()

            val respCode = client.newCall(
                Request.Builder()
                    .url(server.url("/steal-token"))
                    .tag(ProfileIdTag::class.java, ProfileIdTag("gd_valid"))
                    .build(),
            ).execute().use { it.code }

            assertEquals(401, respCode)
            val recorded = server.takeRequest()
            assertNull("Bearer token must never leak off googleapis.com", recorded.getHeader("Authorization"))
            assertEquals(0, refreshCalls.get())
        }

        // 2. CleartextGuardInterceptor blocks http://www.googleapis.com unconditionally.
        val guardChain = FakeChain(
            Request.Builder()
                .url("http://www.googleapis.com/drive/v3/about")
                .tag(ProfileIdTag::class.java, ProfileIdTag("gd_valid"))
                .build(),
        )
        val cleartextBlocked = runCatching { cleartextGuard.intercept(guardChain) }.exceptionOrNull()
        assertTrue(cleartextBlocked != null)

        // 3. Blank accessToken on https://www.googleapis.com skips Authorization header & preserves ProfileIdTag.
        val blankChain = FakeChain(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about")
                .tag(ProfileIdTag::class.java, ProfileIdTag("gd_blank"))
                .build(),
        )
        profileAuth.intercept(blankChain)
        val blankOutgoing = blankChain.proceededRequest!!
        assertNull(
            "Blank Drive token must not emit empty Bearer header",
            blankOutgoing.header(ProfileAuthInterceptor.HEADER_AUTHORIZATION),
        )
        assertEquals("gd_blank", blankOutgoing.tag(ProfileIdTag::class.java)?.profileId)

        // 4. Valid accessToken on https://www.googleapis.com emits Bearer token & preserves ProfileIdTag.
        val validChain = FakeChain(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about")
                .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "gd_valid")
                .build(),
        )
        profileAuth.intercept(validChain)
        val validOutgoing = validChain.proceededRequest!!
        assertEquals(
            "Bearer secret-drive-tok",
            validOutgoing.header(ProfileAuthInterceptor.HEADER_AUTHORIZATION),
        )
        assertEquals("gd_valid", validOutgoing.tag(ProfileIdTag::class.java)?.profileId)
        assertNull(validOutgoing.header(ProfileAuthInterceptor.HEADER_PROFILE_ID))
    }

    private class FakeChain(
        private val initialRequest: Request,
    ) : Interceptor.Chain {
        var proceededRequest: Request? = null

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("ok".toResponseBody(null))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): okhttp3.Call = OkHttpClient().newCall(initialRequest)
        override fun connectTimeoutMillis(): Int = 5_000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 5_000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 5_000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
