package com.feishin.mobile.data.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException

class JellyfinAuthTest {

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

    private data class TestHarness(
        val settings: SettingsStore,
        val profilePrefs: InMemorySharedPreferences,
        val profileStore: ProfileStore,
        val probeRegistry: TransientProbeRegistry,
        val authStateStore: ProfileAuthStateStore,
        val baseClient: OkHttpClient,
        val backendFactory: BackendClientFactory,
        val auth: AuthRepository,
    )

    private fun newHarness(scope: CoroutineScope): TestHarness {
        val settings = newSettingsStore(scope)
        val profilePrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, InMemorySharedPreferences())
        val probeRegistry = TransientProbeRegistry()
        val authStateStore = ProfileAuthStateStore()
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
        val backendFactory = BackendClientFactory(testJson(), baseClient)
        val auth = AuthRepository(
            profileStore = profileStore,
            legacyCredentials = legacyCreds,
            backendFactory = backendFactory,
            probeRegistry = probeRegistry,
            settings = settings,
            profileAuthStateStore = authStateStore,
        )
        return TestHarness(
            settings = settings,
            profilePrefs = profilePrefs,
            profileStore = profileStore,
            probeRegistry = probeRegistry,
            authStateStore = authStateStore,
            baseClient = baseClient,
            backendFactory = backendFactory,
            auth = auth,
        )
    }

    @Test
    fun jellyfinUrlNormalizeStripsTrailingSlashAndWebSuffix() {
        assertEquals("https://jf.example.com:8096", JellyfinUrl.normalize("jf.example.com:8096/web/"))
        assertEquals("https://jf.example.com:8096", JellyfinUrl.normalize("https://jf.example.com:8096/web"))
        assertEquals("http://192.168.1.50:8096/media", JellyfinUrl.normalize("http://192.168.1.50:8096/media/web/"))
        assertEquals("https://web.example.com", JellyfinUrl.normalize("https://web.example.com/"))

        for (bad in listOf("", "   ", "ftp://jf.example.com", "not a url")) {
            try {
                JellyfinUrl.normalize(bad)
                fail("Expected IllegalArgumentException for '$bad'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun persistentDeviceIdIsUuidV4AndSurvivesProfileUpdatesAndClear() = runTest {
        val settings = newSettingsStore(backgroundScope)
        val prefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(stubContext())
        val store1 = ProfileStore(prefs, legacyCreds, settings, InMemorySharedPreferences())

        val firstId = store1.getOrCreateJellyfinDeviceId()
        val parsed = UUID.fromString(firstId)
        assertEquals(4, parsed.version())
        assertEquals(firstId, store1.getOrCreateJellyfinDeviceId())

        store1.upsert(
            ServerProfile(
                id = "p1",
                name = "JF",
                type = BackendType.JELLYFIN,
                url = "https://jf.example.com",
                userId = "u1",
                credentials = ProfileCredentials.Jellyfin("alice", "tok", "srv", firstId),
            ),
        )
        store1.clear()

        val store2 = ProfileStore(prefs, legacyCreds, settings, InMemorySharedPreferences())
        assertEquals(firstId, store2.getOrCreateJellyfinDeviceId())
    }

    @Test
    fun connectJellyfinSendsAuthenticateByNameHeaderWithoutTokenAndPersistsProfile() = runTest {
        val h = newHarness(backgroundScope)
        h.auth.preload()
        h.settings.setDeviceFilesStandalone(true)
        assertTrue(h.settings.flow.first().deviceFilesStandalone)

        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests.add(req)
            when {
                req.path.equals("/Users/AuthenticateByName", ignoreCase = true) -> {
                    200 to """{"AccessToken":"tok-alice-1","ServerId":"srv-99","User":{"Id":"uid-1","Name":"alice","Policy":{"EnableContentDownloading":true}}}"""
                }
                req.path.equals("/System/Info/Public", ignoreCase = true) -> {
                    200 to """{"Version":"10.9.11","ProductName":"Jellyfin","Id":"srv-99","ServerName":"Home JF"}"""
                }
                else -> 404 to "{}"
            }
        }.use { server ->
            val result = h.auth.connectJellyfin(
                name = "Home Jellyfin",
                serverUrl = "${server.baseUrl}/web/",
                username = "alice",
                password = "secret",
                allowSelfSigned = false,
                allowCleartext = true,
            )

            assertTrue(result is Result.Ok)
            val ok = (result as Result.Ok).value
            assertEquals("Jellyfin 10.9.11", ok.apiInfo)
            val saved = ok.profile
            assertEquals("p1", saved.id)
            assertEquals(server.baseUrl, saved.url)
            assertEquals("uid-1", saved.userId)
            val creds = saved.credentials as ProfileCredentials.Jellyfin
            assertEquals("alice", creds.username)
            assertEquals("tok-alice-1", creds.accessToken)
            assertEquals("srv-99", creds.serverId)
            assertEquals(h.profileStore.getOrCreateJellyfinDeviceId(), creds.deviceId)

            val authReq = requests.first { it.path == "/Users/AuthenticateByName" }
            assertNull(authReq.headers["x-emby-token"])
            assertNull(authReq.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])
            val expectedAuthHeader =
                """MediaBrowser Client="Vusic", Device="Android", DeviceId="${creds.deviceId}", Version="${BuildConfig.VERSION_NAME}""""
            assertEquals(expectedAuthHeader, authReq.headers["authorization"])

            // Connecting clears deviceFilesStandalone.
            assertFalse(h.settings.flow.first().deviceFilesStandalone)

            // BackendClientFactory caches JellyfinApi per (id, baseUrl) and invalidates cleanly.
            val api1 = h.backendFactory.jellyfinApi(saved)
            val api2 = h.backendFactory.jellyfinApi(saved)
            assertSame(api1, api2)
            h.backendFactory.invalidate(saved.id)
            val api3 = h.backendFactory.jellyfinApi(saved)
            assertTrue(api1 !== api3)
        }
    }

    @Test
    fun prePersistenceProbeGrantsSelfSignedTrustOnlyDuringProbeAndLeavesStoreEmpty() = runTest {
        val h = newHarness(backgroundScope)
        h.auth.preload()

        val rejectingSystemTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("reject-client")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("reject-server")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val trustManager = ProfileTrustManager(h.profileStore, h.probeRegistry, rejectingSystemTm)

        var trustedDuringProbe = false
        var activeProbeId: String? = null

        LocalHttpServer { req ->
            val probe = h.probeRegistry.findByHost("127.0.0.1", req.serverPort)
            activeProbeId = probe?.id
            if (req.path == "/Users/AuthenticateByName") {
                trustedDuringProbe = trustManager.isSelfSignedAllowed("127.0.0.1", req.serverPort)
                trustManager.checkServerTrusted(
                    emptyArray(),
                    "RSA",
                    FakeSSLEngine("127.0.0.1", req.serverPort),
                )
                200 to """{"AccessToken":"probe-tok","ServerId":"srv-1","User":{"Id":"u1","Name":"alice"}}"""
            } else {
                200 to """{"Version":"10.10.0","ProductName":"Jellyfin","Id":"srv-1"}"""
            }
        }.use { server ->
            val probeRes = h.auth.probeJellyfin(
                serverUrl = server.baseUrl,
                username = "alice",
                password = "pw",
                allowSelfSigned = true,
                allowCleartext = true,
            )

            assertTrue(probeRes is Result.Ok)
            assertTrue("Trust manager must allow self-signed for active probe", trustedDuringProbe)
            assertNotNull(activeProbeId)
            assertNull(h.probeRegistry.get(activeProbeId!!))
            assertTrue("probeJellyfin must not persist profiles", h.profileStore.profiles.value.isEmpty())
            assertFalse(
                "Trust must be revoked immediately after probe finishes",
                trustManager.isSelfSignedAllowed("127.0.0.1", server.port),
            )
            try {
                trustManager.checkServerTrusted(
                    emptyArray(),
                    "RSA",
                    FakeSSLEngine("127.0.0.1", server.port),
                )
                fail("Expected CertificateException after probe cleanup")
            } catch (_: CertificateException) {
                // expected
            }
        }
    }

    @Test
    fun sameHostMultiProfileIsolatedByProfileIdTagAndHeaderPriority() = runTest {
        val h = newHarness(backgroundScope)
        h.auth.preload()

        val observedRequests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            observedRequests.add(req)
            when {
                req.path == "/Users/AuthenticateByName" && req.body.contains("alice") ->
                    200 to """{"AccessToken":"tok-alice","ServerId":"srv","User":{"Id":"uid-a","Name":"alice"}}"""
                req.path == "/Users/AuthenticateByName" && req.body.contains("bob") ->
                    200 to """{"AccessToken":"tok-bob","ServerId":"srv","User":{"Id":"uid-b","Name":"bob"}}"""
                req.path == "/System/Info/Public" ->
                    200 to """{"Version":"10.9.11","ProductName":"Jellyfin","Id":"srv"}"""
                else ->
                    200 to """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""
            }
        }.use { server ->
            val alice = (h.auth.connectJellyfin("Alice JF", server.baseUrl, "alice", "pw1", false, true) as Result.Ok).value.profile
            val bob = (h.auth.connectJellyfin("Bob JF", server.baseUrl, "bob", "pw2", false, true) as Result.Ok).value.profile
            assertNotEquals(alice.id, bob.id)

            observedRequests.clear()

            h.backendFactory.jellyfinApi(alice).getItems(userId = alice.userId, limit = 5)
            h.backendFactory.jellyfinApi(bob).getItems(userId = bob.userId, limit = 5)

            assertEquals(2, observedRequests.size)
            assertEquals("tok-alice", observedRequests[0].headers["x-emby-token"])
            assertEquals(
                """MediaBrowser Client="Vusic", Device="Android", DeviceId="${(alice.credentials as ProfileCredentials.Jellyfin).deviceId}", Version="${BuildConfig.VERSION_NAME}", Token="tok-alice"""",
                observedRequests[0].headers["authorization"],
            )
            assertEquals("tok-bob", observedRequests[1].headers["x-emby-token"])
            assertEquals(
                """MediaBrowser Client="Vusic", Device="Android", DeviceId="${(bob.credentials as ProfileCredentials.Jellyfin).deviceId}", Version="${BuildConfig.VERSION_NAME}", Token="tok-bob"""",
                observedRequests[1].headers["authorization"],
            )

            // ProfileIdTag takes precedence even if HEADER_PROFILE_ID points elsewhere.
            observedRequests.clear()
            h.baseClient.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/Users/${bob.userId}/Items?Limit=1")
                    .tag(ProfileIdTag::class.java, ProfileIdTag(bob.id))
                    .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, alice.id)
                    .build(),
            ).execute().use { assertEquals(200, it.code) }

            assertEquals("tok-bob", observedRequests.single().headers["x-emby-token"])
            assertNull(observedRequests.single().headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])
        }
    }

    @Test
    fun crossHostRedirectStripsJellyfinCredentialsAnd401MarksAndReAuthClearsSessionExpired() = runTest {
        val h = newHarness(backgroundScope)
        h.auth.preload()

        val targetRequests = CopyOnWriteArrayList<RecordedRequest>()
        var return401 = false
        var authCounter = 0

        LocalHttpServer { req ->
            targetRequests.add(req)
            200 to """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""
        }.use { evilServer ->
            LocalHttpServer { req ->
                when {
                    req.path == "/Users/AuthenticateByName" -> {
                        authCounter += 1
                        val tok = "tok-$authCounter"
                        200 to """{"AccessToken":"$tok","ServerId":"srv","User":{"Id":"uid-a","Name":"alice"}}"""
                    }
                    req.path == "/System/Info/Public" ->
                        200 to """{"Version":"10.9.11","ProductName":"Jellyfin","Id":"srv"}"""
                    req.path.startsWith("/RedirectTest") ->
                        302 to "" // handled below via custom headers
                    return401 ->
                        401 to "Unauthorized"
                    else ->
                        200 to """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""
                }
            }.use { jfServer ->
                // 1. Connect initially.
                val initial = (h.auth.connectJellyfin("JF", jfServer.baseUrl, "alice", "pw", false, true) as Result.Ok).value.profile
                assertEquals("p1", initial.id)
                assertEquals(0, initial.priority)
                assertEquals(ProfileAuthStatus.OK, h.authStateStore.statusFor("p1"))

                // 2. Cross-host redirect strips X-Emby-Token & Authorization.
                LocalHttpServer(
                    handlerWithHeaders = {
                        Triple(
                            302,
                            mapOf("Location" to "${evilServer.baseUrl}/steal"),
                            "",
                        )
                    },
                ).use { redirectServer ->
                    val redirProfile = initial.copy(id = "p_redir", url = redirectServer.baseUrl, priority = 1)
                    h.profileStore.upsert(redirProfile)
                    h.backendFactory.jellyfinApi(redirProfile).getItems(userId = "uid-a", limit = 1)
                    val leaked = targetRequests.single()
                    assertNull(leaked.headers["x-emby-token"])
                    assertNull(leaked.headers["authorization"])
                    assertNull(leaked.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])
                    h.profileStore.remove("p_redir")
                }

                // 3. HTTP 401 on non-AuthenticateByName Jellyfin API call marks SESSION_EXPIRED.
                return401 = true
                try {
                    h.backendFactory.jellyfinApi(initial).getItems(userId = initial.userId, limit = 1)
                    fail("Expected HttpException 401")
                } catch (e: HttpException) {
                    assertEquals(401, e.code())
                }
                assertEquals(ProfileAuthStatus.SESSION_EXPIRED, h.authStateStore.statusFor("p1"))

                // 4. Re-auth updates profile in-place (preserving id & priority) and clears SESSION_EXPIRED.
                return401 = false
                val reauth = (h.auth.connectJellyfin("JF", jfServer.baseUrl, "alice", "new-pw", false, true) as Result.Ok).value.profile
                assertEquals("p1", reauth.id)
                assertEquals(0, reauth.priority)
                assertEquals(1, h.profileStore.profiles.value.size)
                assertEquals("tok-2", (reauth.credentials as ProfileCredentials.Jellyfin).accessToken)
                assertEquals(ProfileAuthStatus.OK, h.authStateStore.statusFor("p1"))
            }
        }
    }

    @Test
    fun editModeBlankPasswordReusesStoredTokenAndMaps401And404Errors() = runTest {
        val h = newHarness(backgroundScope)
        h.auth.preload()

        val observedPaths = CopyOnWriteArrayList<String>()
        var failAuth404 = false
        var failAuth401 = false

        LocalHttpServer { req ->
            observedPaths.add(req.path)
            when {
                req.path == "/Users/AuthenticateByName" -> when {
                    failAuth404 -> 404 to "Not found"
                    failAuth401 -> 401 to "Unauthorized"
                    else -> 200 to """{"AccessToken":"tok-orig","ServerId":"srv","User":{"Id":"uid-1","Name":"alice"}}"""
                }
                req.path == "/System/Info/Public" ->
                    200 to """{"Version":"10.9.11","ProductName":"Jellyfin","Id":"srv"}"""
                req.path == "/Users/uid-1/Items" ->
                    200 to """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""
                else -> 404 to "{}"
            }
        }.use { server ->
            val saved = (h.auth.connectJellyfin("Old Name", server.baseUrl, "alice", "pw", false, true) as Result.Ok).value.profile
            observedPaths.clear()

            // Edit mode with blank password and unchanged URL/username reuses stored token.
            val edited = h.auth.connectJellyfin(
                name = "Renamed JF",
                serverUrl = server.baseUrl,
                username = "alice",
                password = "",
                allowSelfSigned = false,
                allowCleartext = true,
                existingProfileId = saved.id,
            )
            assertTrue(edited is Result.Ok)
            val updated = (edited as Result.Ok).value.profile
            assertEquals(saved.id, updated.id)
            assertEquals("Renamed JF", updated.name)
            assertEquals("tok-orig", (updated.credentials as ProfileCredentials.Jellyfin).accessToken)
            assertFalse(
                "AuthenticateByName must not be called when reusing stored token",
                observedPaths.contains("/Users/AuthenticateByName"),
            )
            assertTrue(observedPaths.contains("/Users/uid-1/Items"))

            // Changing username with blank password re-runs AuthenticateByName -> 401 maps clearly.
            failAuth401 = true
            val err401 = h.auth.probeJellyfin(
                serverUrl = server.baseUrl,
                username = "bob",
                password = "",
                allowSelfSigned = false,
                allowCleartext = true,
                existingProfileId = saved.id,
            )
            assertTrue(err401 is Result.Err)
            assertEquals("Wrong username or password.", (err401 as Result.Err).message)
            // Failed AuthenticateByName probe must NOT mark existing profile SESSION_EXPIRED.
            assertEquals(ProfileAuthStatus.OK, h.authStateStore.statusFor(saved.id))

            // 404 on /Users/AuthenticateByName -> clear non-Jellyfin message.
            failAuth401 = false
            failAuth404 = true
            val err404 = h.auth.probeJellyfin(
                serverUrl = server.baseUrl,
                username = "bob",
                password = "pw",
                allowSelfSigned = false,
                allowCleartext = true,
            )
            assertTrue(err404 is Result.Err)
            assertEquals("This doesn't look like a Jellyfin server.", (err404 as Result.Err).message)
        }
    }

    private data class RecordedRequest(
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: String,
        val serverPort: Int,
    )

    private class LocalHttpServer(
        private val handlerWithHeaders: ((RecordedRequest) -> Triple<Int, Map<String, String>, String>)? = null,
        private val handler: ((RecordedRequest) -> Pair<Int, String>)? = null,
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
                } catch (_: Throwable) {
                    break
                }
                client.use { conn ->
                    val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine() ?: return@use
                    val target = requestLine.split(' ').getOrElse(1) { "/" }
                    val path = target.substringBefore('?')
                    val query = target.substringAfter('?', "")
                    val headers = linkedMapOf<String, String>()
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            val name = line.substring(0, sep).trim().lowercase()
                            val value = line.substring(sep + 1).trim()
                            headers[name] = value
                            if (name == "content-length") {
                                contentLength = value.toIntOrNull() ?: 0
                            }
                        }
                    }
                    val body = if (contentLength > 0) {
                        val chars = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(chars, read, contentLength - read)
                            if (n < 0) break
                            read += n
                        }
                        String(chars, 0, read)
                    } else {
                        ""
                    }

                    val recorded = RecordedRequest(path, query, headers, body, port)
                    val (status, respHeaders, respBody) = when {
                        handlerWithHeaders != null -> handlerWithHeaders.invoke(recorded)
                        handler != null -> {
                            val (c, b) = handler.invoke(recorded)
                            Triple(c, emptyMap(), b)
                        }
                        else -> Triple(200, emptyMap(), "{}")
                    }
                    val bytes = respBody.toByteArray(Charsets.UTF_8)
                    val reason = when (status) {
                        200 -> "OK"
                        302 -> "Found"
                        401 -> "Unauthorized"
                        404 -> "Not Found"
                        else -> "Status"
                    }
                    val out = conn.getOutputStream()
                    val head = buildString {
                        append("HTTP/1.1 $status $reason\r\n")
                        append("Content-Type: application/json\r\n")
                        respHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
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

    private fun fakeSslSession(host: String, port: Int): SSLSession =
        Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPeerHost" -> host
                "getPeerPort" -> port
                "getPeerCertificates" -> throw SSLPeerUnverifiedException("fake-unverified")
                "isValid" -> true
                "getId" -> ByteArray(0)
                "getValueNames" -> emptyArray<String>()
                "getCreationTime", "getLastAccessedTime" -> 0L
                "getPacketBufferSize", "getApplicationBufferSize" -> 0
                else -> null
            }
        } as SSLSession

    private inner class FakeSSLEngine(
        host: String,
        port: Int,
    ) : SSLEngine(host, port) {
        private val fakeSession = fakeSslSession(host, port)
        override fun getHandshakeSession(): SSLSession = fakeSession
        override fun getSession(): SSLSession = fakeSession
        override fun wrap(srcs: Array<out ByteBuffer>?, offset: Int, length: Int, dst: ByteBuffer?): SSLEngineResult =
            throw UnsupportedOperationException()
        override fun unwrap(src: ByteBuffer?, dsts: Array<out ByteBuffer>?, offset: Int, length: Int): SSLEngineResult =
            throw UnsupportedOperationException()
        override fun getDelegatedTask(): Runnable? = null
        override fun closeInbound() = Unit
        override fun isInboundDone(): Boolean = true
        override fun closeOutbound() = Unit
        override fun isOutboundDone(): Boolean = true
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
        override fun getEnabledCipherSuites(): Array<String> = emptyArray()
        override fun setEnabledCipherSuites(suites: Array<out String>?) = Unit
        override fun getSupportedProtocols(): Array<String> = emptyArray()
        override fun getEnabledProtocols(): Array<String> = emptyArray()
        override fun setEnabledProtocols(protocols: Array<out String>?) = Unit
        override fun beginHandshake() = Unit
        override fun getHandshakeStatus(): SSLEngineResult.HandshakeStatus =
            SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        override fun setUseClientMode(mode: Boolean) = Unit
        override fun getUseClientMode(): Boolean = true
        override fun setNeedClientAuth(need: Boolean) = Unit
        override fun getNeedClientAuth(): Boolean = false
        override fun setWantClientAuth(want: Boolean) = Unit
        override fun getWantClientAuth(): Boolean = false
        override fun setEnableSessionCreation(flag: Boolean) = Unit
        override fun getEnableSessionCreation(): Boolean = true
    }

    private class InMemorySharedPreferences : SharedPreferences {
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
