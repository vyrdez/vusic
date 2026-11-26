package com.feishin.mobile.data.remote

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
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
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileAuthInterceptorTest {

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

    private fun newHardenedClient(
        profileStore: ProfileStore,
        probeRegistry: TransientProbeRegistry,
        settings: SettingsStore,
    ): OkHttpClient {
        val cleartextGuard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)
        val profileAuth = ProfileAuthInterceptor(profileStore, probeRegistry)
        return OkHttpClient.Builder()
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .build()
    }

    @Test
    fun subsonicHostPinnedAuthStripsInternalHeaderAndPreventsCrossHostLeak() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()

        val serverARequests = CopyOnWriteArrayList<RecordedRequest>()
        val serverBRequests = CopyOnWriteArrayList<RecordedRequest>()

        LocalTestServer { req ->
            serverARequests.add(req)
            ServerResponse(200, body = "ok-a")
        }.use { serverA ->
            LocalTestServer { req ->
                serverBRequests.add(req)
                ServerResponse(200, body = "ok-b")
            }.use { serverB ->
                val p1 = ServerProfile(
                    id = "p1",
                    name = "Server A",
                    type = BackendType.NAVIDROME,
                    url = serverA.baseUrl,
                    userId = "alice",
                    credentials = ProfileCredentials.Subsonic("alice", "s3cr3t"),
                    allowCleartext = true,
                    enabled = true,
                )
                profileStore.upsert(p1)

                val client = newHardenedClient(profileStore, probeRegistry, settings)

                // 1. Request to pinned Server A with X-Vusic-Profile-Id -> signed & internal header stripped.
                client.newCall(
                    Request.Builder()
                        .url("${serverA.baseUrl}/rest/ping.view")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p1")
                        .build(),
                ).execute().use { resp -> assertEquals(200, resp.code) }

                val reqA = serverARequests.single()
                assertNull(
                    "Internal X-Vusic-Profile-Id header must never hit wire",
                    reqA.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()],
                )
                val qA = parseQuery(reqA.query)
                assertEquals("alice", qA["u"])
                assertNotNull(qA["s"])
                assertEquals(SubsonicAuth.token("s3cr3t", qA["s"]!!), qA["t"])
                assertEquals(SubsonicAuth.API_VERSION, qA["v"])
                assertEquals(SubsonicAuth.CLIENT_NAME, qA["c"])
                assertEquals("json", qA["f"])

                // 2. Request to unrelated Server B even WITH X-Vusic-Profile-Id: p1 -> zero credentials & header stripped.
                client.newCall(
                    Request.Builder()
                        .url("${serverB.baseUrl}/api/lyrics?track=hello")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p1")
                        .build(),
                ).execute().use { resp -> assertEquals(200, resp.code) }

                val reqB = serverBRequests.single()
                assertNull(reqB.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])
                assertNull(reqB.headers["authorization"])
                assertNull(reqB.headers["x-emby-token"])
                val qB = parseQuery(reqB.query)
                assertNull("u must not leak to unrelated host", qB["u"])
                assertNull("t must not leak to unrelated host", qB["t"])
                assertNull("s must not leak to unrelated host", qB["s"])
                assertEquals("hello", qB["track"])
            }
        }
    }

    @Test
    fun presignedSubsonicUrlIsNotDoubleSignedAndDisabledProfileSkipsInjection() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()
        val requests = CopyOnWriteArrayList<RecordedRequest>()

        LocalTestServer { req ->
            requests.add(req)
            ServerResponse(200, body = "ok")
        }.use { server ->
            val p1 = ServerProfile(
                id = "p1",
                name = "Server A",
                type = BackendType.OPENSUBSONIC,
                url = server.baseUrl,
                userId = "alice",
                credentials = ProfileCredentials.Subsonic("alice", "pw"),
                allowCleartext = true,
                enabled = true,
            )
            profileStore.upsert(p1)
            val client = newHardenedClient(profileStore, probeRegistry, settings)

            // Pre-signed cover art URL should preserve original u/t/s verbatim (no double signing).
            client.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/rest/getCoverArt.view?id=al-1&u=alice&t=fixedtok&s=fixedsalt&v=1.16.1&c=FeishinMobile&f=json")
                    .build(),
            ).execute().use { assertEquals(200, it.code) }

            val presigned = parseQuery(requests[0].query)
            assertEquals("fixedtok", presigned["t"])
            assertEquals("fixedsalt", presigned["s"])
            assertEquals(1, requests[0].query.split('&').count { it.startsWith("u=") })

            // Disabled profile must NOT inject credentials.
            profileStore.setEnabled("p1", false)
            client.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/rest/ping.view")
                    .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p1")
                    .build(),
            ).execute().use { assertEquals(200, it.code) }

            val disabledQuery = parseQuery(requests[1].query)
            assertNull(disabledQuery["u"])
            assertNull(disabledQuery["t"])
            assertNull(disabledQuery["s"])
        }
    }

    @Test
    fun http302CrossPortRedirectStripsSubsonicAndJellyfinCredentials() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()

        val serverARequests = CopyOnWriteArrayList<RecordedRequest>()
        val serverBRequests = CopyOnWriteArrayList<RecordedRequest>()

        LocalTestServer { req ->
            serverBRequests.add(req)
            ServerResponse(200, body = "redirect-target")
        }.use { serverB ->
            LocalTestServer { req ->
                serverARequests.add(req)
                val target = when {
                    req.path.startsWith("/rest/stream.view") ->
                        "${serverB.baseUrl}/steal?id=42&u=alice&t=leakedtok&s=leakedsalt"
                    else ->
                        "${serverB.baseUrl}/cdn/audio.flac"
                }
                ServerResponse(
                    code = 302,
                    headers = mapOf("Location" to target),
                    body = "",
                )
            }.use { serverA ->
                val client = newHardenedClient(profileStore, probeRegistry, settings)

                // Case 1: Subsonic redirect carrying u/t/s in Location URL -> serverB sees ZERO u/t/s.
                profileStore.upsert(
                    ServerProfile(
                        id = "p1",
                        name = "Subsonic A",
                        type = BackendType.NAVIDROME,
                        url = serverA.baseUrl,
                        userId = "alice",
                        credentials = ProfileCredentials.Subsonic("alice", "pw"),
                        allowCleartext = true,
                    ),
                )

                client.newCall(
                    Request.Builder()
                        .url("${serverA.baseUrl}/rest/stream.view?id=42")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p1")
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                    assertEquals("redirect-target", resp.body?.string())
                }

                val subA = parseQuery(serverARequests[0].query)
                assertEquals("alice", subA["u"])
                assertNotNull(subA["t"])
                assertNotNull(subA["s"])

                val subB = serverBRequests[0]
                val subBQuery = parseQuery(subB.query)
                assertEquals("42", subBQuery["id"])
                assertNull("u must be stripped on cross-port redirect", subBQuery["u"])
                assertNull("t must be stripped on cross-port redirect", subBQuery["t"])
                assertNull("s must be stripped on cross-port redirect", subBQuery["s"])
                assertNull(subB.headers["authorization"])
                assertNull(subB.headers["x-emby-token"])
                assertNull(subB.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])

                // Case 2: Jellyfin redirect -> serverA gets X-Emby-Token & MediaBrowser Authorization,
                // serverB gets ZERO auth headers!
                profileStore.saveAll(
                    listOf(
                        ServerProfile(
                            id = "p2",
                            name = "Jellyfin A",
                            type = BackendType.JELLYFIN,
                            url = serverA.baseUrl,
                            userId = "jf-user",
                            credentials = ProfileCredentials.Jellyfin(
                                username = "bob",
                                accessToken = "jf-secret-token",
                                serverId = "srv-1",
                                deviceId = "dev-123",
                            ),
                            allowCleartext = true,
                        ),
                    ),
                )

                client.newCall(
                    Request.Builder()
                        .url("${serverA.baseUrl}/Items/99/Download")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p2")
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                }

                val jfA = serverARequests[1]
                assertEquals("jf-secret-token", jfA.headers["x-emby-token"])
                assertEquals(
                    """MediaBrowser Client="Vusic", Device="Android", DeviceId="dev-123", Version="${com.feishin.mobile.BuildConfig.VERSION_NAME}", Token="jf-secret-token"""",
                    jfA.headers["authorization"],
                )
                assertNull(jfA.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])

                val jfB = serverBRequests[1]
                assertNull("X-Emby-Token must be stripped on cross-port redirect", jfB.headers["x-emby-token"])
                assertNull("Authorization must be stripped on cross-port redirect", jfB.headers["authorization"])
                assertNull(jfB.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID.lowercase()])
            }
        }
    }

    @Test
    fun googleDriveBearerTokenOnlySentToHttpsGoogleApis() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()
        profileStore.upsert(
            ServerProfile(
                id = "gd1",
                name = "My Drive",
                type = BackendType.GOOGLE_DRIVE,
                url = "https://www.googleapis.com",
                userId = "user@example.com",
                credentials = ProfileCredentials.GoogleDrive(
                    accountEmail = "user@example.com",
                    accessToken = "ya29.secret-drive-token",
                ),
                allowCleartext = false,
            ),
        )
        val interceptor = ProfileAuthInterceptor(profileStore, probeRegistry)

        // 1. https://www.googleapis.com -> Bearer injected, X-Vusic-Profile-Id stripped.
        val validChain = FakeChain(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "gd1")
                .build(),
        )
        interceptor.intercept(validChain)
        val sentValid = validChain.proceededRequest!!
        assertEquals("Bearer ya29.secret-drive-token", sentValid.header("Authorization"))
        assertNull(sentValid.header(ProfileAuthInterceptor.HEADER_PROFILE_ID))

        // 2. http://www.googleapis.com -> NO Bearer injected.
        val cleartextChain = FakeChain(
            Request.Builder()
                .url("http://www.googleapis.com/drive/v3/files")
                .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "gd1")
                .build(),
        )
        interceptor.intercept(cleartextChain)
        assertNull(cleartextChain.proceededRequest!!.header("Authorization"))

        // 3. https://evil.example.com and https://lrclib.net -> NO Bearer injected.
        val evilChain = FakeChain(
            Request.Builder()
                .url("https://evil.example.com/drive/v3/files")
                .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "gd1")
                .build(),
        )
        interceptor.intercept(evilChain)
        assertNull(evilChain.proceededRequest!!.header("Authorization"))
        assertNull(evilChain.proceededRequest!!.header(ProfileAuthInterceptor.HEADER_PROFILE_ID))

        val lyricsChain = FakeChain(
            Request.Builder()
                .url("https://lrclib.net/api/get?track_name=Test")
                .build(),
        )
        interceptor.intercept(lyricsChain)
        assertNull(lyricsChain.proceededRequest!!.header("Authorization"))
        assertNull(lyricsChain.proceededRequest!!.url.queryParameter("u"))
    }

    @Test
    fun cleartextGuardEnforcesPerProfileGlobalAndGoogleDrivePolicies() = runTest {
        val settings = newSettingsStore()
        settings.setAllowCleartext(false)
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()
        val guard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)

        profileStore.saveAll(
            listOf(
                ServerProfile(
                    id = "p_http_no",
                    name = "No Cleartext",
                    type = BackendType.NAVIDROME,
                    url = "http://lan-no.local:4533",
                    credentials = ProfileCredentials.Subsonic("u", "p"),
                    allowCleartext = false,
                ),
                ServerProfile(
                    id = "p_http_yes",
                    name = "Yes Cleartext",
                    type = BackendType.NAVIDROME,
                    url = "http://lan-yes.local:4533",
                    credentials = ProfileCredentials.Subsonic("u", "p"),
                    allowCleartext = true,
                ),
                ServerProfile(
                    id = "gd_bad",
                    name = "Drive Misconfigured",
                    type = BackendType.GOOGLE_DRIVE,
                    url = "https://www.googleapis.com",
                    credentials = ProfileCredentials.GoogleDrive("a@b.com", "tok"),
                    allowCleartext = true, // must still be rejected for Drive!
                ),
            ),
        )

        // Profile with allowCleartext = false throws.
        assertThrowsCleartext {
            guard.intercept(
                FakeChain(
                    Request.Builder()
                        .url("http://lan-no.local:4533/rest/ping.view")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p_http_no")
                        .build(),
                ),
            )
        }

        // Profile with allowCleartext = true succeeds.
        val okChain = FakeChain(
            Request.Builder()
                .url("http://lan-yes.local:4533/rest/ping.view")
                .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "p_http_yes")
                .build(),
        )
        assertEquals(200, guard.intercept(okChain).code)

        // Google Drive ALWAYS rejects http:// even if allowCleartext = true.
        assertThrowsCleartext {
            guard.intercept(
                FakeChain(
                    Request.Builder()
                        .url("http://other-host.local/file")
                        .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "gd_bad")
                        .build(),
                ),
            )
        }

        // Unmatched host follows SettingsStore.allowCleartext (currently false).
        assertThrowsCleartext {
            guard.intercept(FakeChain(Request.Builder().url("http://unknown.local/test").build()))
        }

        // Flip global setting to true -> unmatched host now passes.
        settings.setAllowCleartext(true)
        assertEquals(
            200,
            guard.intercept(FakeChain(Request.Builder().url("http://unknown.local/test").build())).code,
        )

        // Transient probe with allowCleartext = false throws even when global setting is true.
        val probeId = probeRegistry.register(
            ServerProfile(
                id = "",
                name = "Probe",
                type = BackendType.OPENSUBSONIC,
                url = "http://probe.local:4533",
                credentials = ProfileCredentials.Subsonic("u", "p"),
                allowCleartext = false,
            ),
        )
        try {
            assertThrowsCleartext {
                guard.intercept(
                    FakeChain(
                        Request.Builder()
                            .url("http://probe.local:4533/rest/ping.view")
                            .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, probeId)
                            .build(),
                    ),
                )
            }
        } finally {
            probeRegistry.remove(probeId)
        }
    }

    @Test
    fun profileTrustManagerIsolatesSelfSignedPerHostAndPort() = runTest {
        val settings = newSettingsStore()
        settings.setAllowSelfSigned(true) // global flag must NEVER weaken other hosts!
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()

        profileStore.saveAll(
            listOf(
                ServerProfile(
                    id = "p_lan",
                    name = "LAN Self-Signed",
                    type = BackendType.NAVIDROME,
                    url = "https://lan.local:4533",
                    credentials = ProfileCredentials.Subsonic("u", "p"),
                    allowSelfSigned = true,
                ),
                ServerProfile(
                    id = "p_strict",
                    name = "Strict HTTPS",
                    type = BackendType.JELLYFIN,
                    url = "https://strict.example.com",
                    credentials = ProfileCredentials.Jellyfin("u", "t", "s", "d"),
                    allowSelfSigned = false,
                ),
                ServerProfile(
                    id = "gd_strict",
                    name = "Drive",
                    type = BackendType.GOOGLE_DRIVE,
                    url = "https://www.googleapis.com",
                    credentials = ProfileCredentials.GoogleDrive("a@b.com", "tok"),
                    allowSelfSigned = true, // must still never bypass TLS for Drive!
                ),
            ),
        )

        val rejectingSystemTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("system-client-reject")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("system-server-reject")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val tm = ProfileTrustManager(profileStore, probeRegistry, rejectingSystemTm)

        // Exact host+port allowed (case-insensitive).
        assertTrue(tm.isSelfSignedAllowed("lan.local", 4533))
        assertTrue(tm.isSelfSignedAllowed("LAN.LOCAL", 4533))
        tm.checkServerTrusted(emptyArray(), "RSA", FakeSSLEngine("lan.local", 4533))
        assertTrue(tm.hostnameVerifier().verify("lan.local", fakeSslSession("lan.local", 4533)))

        // Same host on a DIFFERENT port must stay strict!
        assertFalse(tm.isSelfSignedAllowed("lan.local", 8443))
        assertThrowsCertException {
            tm.checkServerTrusted(emptyArray(), "RSA", FakeSSLEngine("lan.local", 8443))
        }

        // Strict profile, Google Drive, and third-party lyrics hosts MUST stay strict!
        for ((host, port) in listOf(
            "strict.example.com" to 443,
            "www.googleapis.com" to 443,
            "lrclib.net" to 443,
            "api.betterlyrics.org" to 443,
        )) {
            assertFalse("$host:$port must not allow self-signed", tm.isSelfSignedAllowed(host, port))
            assertThrowsCertException {
                tm.checkServerTrusted(emptyArray(), "RSA", FakeSSLEngine(host, port))
            }
            assertFalse(tm.hostnameVerifier().verify(host, fakeSslSession(host, port)))
        }

        // Context-less checkServerTrusted(chain, authType) is fail-closed.
        assertThrowsCertException {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }

        // Transient probe enables self-signed ONLY while registered.
        val probeId = probeRegistry.register(
            ServerProfile(
                id = "",
                name = "Probe Self-Signed",
                type = BackendType.OPENSUBSONIC,
                url = "https://new-lan.local:8096",
                credentials = ProfileCredentials.Subsonic("u", "p"),
                allowSelfSigned = true,
            ),
        )
        assertTrue(tm.isSelfSignedAllowed("new-lan.local", 8096))
        tm.checkServerTrusted(emptyArray(), "RSA", FakeSSLEngine("new-lan.local", 8096))
        probeRegistry.remove(probeId)
        assertFalse(tm.isSelfSignedAllowed("new-lan.local", 8096))
        assertThrowsCertException {
            tm.checkServerTrusted(emptyArray(), "RSA", FakeSSLEngine("new-lan.local", 8096))
        }
    }

    @Test
    fun redactingHttpLoggerMasksHeadersAndOAuthTokens() {
        assertEquals(
            "<redacted>",
            RedactingHttpLogger.redactHeader("Authorization", "Bearer ya29.secret"),
        )
        assertEquals(
            "<redacted>",
            RedactingHttpLogger.redactHeader("X-Emby-Token", "jf-secret-123"),
        )
        assertEquals(
            "application/json",
            RedactingHttpLogger.redactHeader("Content-Type", "application/json"),
        )

        val headers = Headers.Builder()
            .add("Authorization", "MediaBrowser Token=secret")
            .add("X-Emby-Token", "emby-secret")
            .add("Accept", "application/json")
            .build()
        val redactedHeaders = RedactingHttpLogger.redactHeaders(headers)
        assertEquals("<redacted>", redactedHeaders["Authorization"])
        assertEquals("<redacted>", redactedHeaders["X-Emby-Token"])
        assertEquals("application/json", redactedHeaders["Accept"])

        val oauthUrl = "https://oauth2.googleapis.com/token?access_token=abc&refresh_token=def&token=ghi&scope=drive"
        val redactedUrl = RedactingHttpLogger.redact(oauthUrl)
        assertFalse(redactedUrl.contains("abc"))
        assertFalse(redactedUrl.contains("def"))
        assertFalse(redactedUrl.contains("ghi"))
        assertTrue(redactedUrl.contains("access_token=***"))
        assertTrue(redactedUrl.contains("refresh_token=***"))
        assertTrue(redactedUrl.contains("token=***"))
        assertTrue(redactedUrl.contains("scope=drive"))

        val form = RedactingHttpLogger.redactForm("u=alice&p=hunter2&refresh_token=r1&client_id=vusic")
        assertEquals("u=***&p=***&refresh_token=***&client_id=vusic", form)
    }

    @Test
    fun jellyfinProfileIdTagIsolatesSameHostAnd401MarksSessionExpired() = runTest {
        val settings = newSettingsStore()
        val profileStore = newProfileStore(settings)
        val probeRegistry = TransientProbeRegistry()
        val authStateStore = com.feishin.mobile.data.auth.ProfileAuthStateStore()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        var nextCode = 200

        LocalTestServer { req ->
            requests.add(req)
            ServerResponse(nextCode, body = "{}")
        }.use { server ->
            val p1 = ServerProfile(
                id = "jf1",
                name = "Jellyfin 1",
                type = BackendType.JELLYFIN,
                url = server.baseUrl,
                userId = "u1",
                credentials = ProfileCredentials.Jellyfin("alice", "tok-1", "srv", "dev-1"),
                allowCleartext = true,
            )
            val p2 = ServerProfile(
                id = "jf2",
                name = "Jellyfin 2",
                type = BackendType.JELLYFIN,
                url = server.baseUrl,
                userId = "u2",
                credentials = ProfileCredentials.Jellyfin("bob", "tok-2", "srv", "dev-2"),
                allowCleartext = true,
            )
            profileStore.saveAll(listOf(p1, p2))

            val interceptor = ProfileAuthInterceptor(profileStore, probeRegistry, authStateStore)
            val client = OkHttpClient.Builder()
                .addInterceptor(CleartextGuardInterceptor(profileStore, probeRegistry, settings))
                .addInterceptor(interceptor)
                .addNetworkInterceptor(interceptor.redirectGuard())
                .build()

            // 1. ProfileIdTag wins over HEADER_PROFILE_ID and isolates same-host profiles.
            client.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/Users/u2/Items")
                    .tag(
                        com.feishin.mobile.core.network.ProfileIdTag::class.java,
                        com.feishin.mobile.core.network.ProfileIdTag("jf2"),
                    )
                    .header(ProfileAuthInterceptor.HEADER_PROFILE_ID, "jf1")
                    .build(),
            ).execute().use { assertEquals(200, it.code) }

            assertEquals("tok-2", requests[0].headers["x-emby-token"])
            assertEquals(
                """MediaBrowser Client="Vusic", Device="Android", DeviceId="dev-2", Version="${com.feishin.mobile.BuildConfig.VERSION_NAME}", Token="tok-2"""",
                requests[0].headers["authorization"],
            )

            // 2. Users/AuthenticateByName omits Token and X-Emby-Token, and 401 does NOT mark SESSION_EXPIRED.
            nextCode = 401
            client.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/users/authenticatebyname")
                    .tag(
                        com.feishin.mobile.core.network.ProfileIdTag::class.java,
                        com.feishin.mobile.core.network.ProfileIdTag("jf1"),
                    )
                    .build(),
            ).execute().use { assertEquals(401, it.code) }

            assertNull(requests[1].headers["x-emby-token"])
            assertEquals(
                """MediaBrowser Client="Vusic", Device="Android", DeviceId="dev-1", Version="${com.feishin.mobile.BuildConfig.VERSION_NAME}"""",
                requests[1].headers["authorization"],
            )
            assertEquals(
                com.feishin.mobile.data.auth.ProfileAuthStatus.OK,
                authStateStore.statusFor("jf1"),
            )

            // 3. Authenticated Jellyfin 401 marks SESSION_EXPIRED non-blockingly for that profile only.
            client.newCall(
                Request.Builder()
                    .url("${server.baseUrl}/Users/u1/Items")
                    .tag(
                        com.feishin.mobile.core.network.ProfileIdTag::class.java,
                        com.feishin.mobile.core.network.ProfileIdTag("jf1"),
                    )
                    .build(),
            ).execute().use { assertEquals(401, it.code) }

            assertEquals(
                com.feishin.mobile.data.auth.ProfileAuthStatus.SESSION_EXPIRED,
                authStateStore.statusFor("jf1"),
            )
            assertEquals(
                com.feishin.mobile.data.auth.ProfileAuthStatus.OK,
                authStateStore.statusFor("jf2"),
            )
        }
    }

    private fun assertThrowsCleartext(block: () -> Unit) {
        try {
            block()
            fail("Expected CleartextNotPermittedException")
        } catch (_: CleartextNotPermittedException) {
            // expected
        }
    }

    private fun assertThrowsCertException(block: () -> Unit) {
        try {
            block()
            fail("Expected CertificateException")
        } catch (_: CertificateException) {
            // expected
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
        }.toMap()
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
        override fun call(): Call = OkHttpClient().newCall(initialRequest)
        override fun connectTimeoutMillis(): Int = 5_000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 5_000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 5_000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private data class RecordedRequest(
        val path: String,
        val query: String,
        val headers: Map<String, String>,
    )

    private data class ServerResponse(
        val code: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: String = "",
    )

    private class LocalTestServer(
        private val handler: (RecordedRequest) -> ServerResponse,
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
                client.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine() ?: return@use
                    val target = requestLine.split(' ').getOrNull(1) ?: "/"
                    val qIdx = target.indexOf('?')
                    val path = if (qIdx >= 0) target.substring(0, qIdx) else target
                    val query = if (qIdx >= 0) target.substring(qIdx + 1) else ""
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) {
                            headers[line.substring(0, colon).trim().lowercase()] =
                                line.substring(colon + 1).trim()
                        }
                    }
                    val resp = handler(RecordedRequest(path, query, headers))
                    val bodyBytes = resp.body.toByteArray(Charsets.UTF_8)
                    val reason = when (resp.code) {
                        200 -> "OK"
                        302 -> "Found"
                        else -> "Status"
                    }
                    val out = s.getOutputStream()
                    val head = buildString {
                        append("HTTP/1.1 ${resp.code} $reason\r\n")
                        resp.headers.forEach { (k, v) -> append("$k: $v\r\n") }
                        append("Content-Length: ${bodyBytes.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    out.write(head.toByteArray(Charsets.US_ASCII))
                    out.write(bodyBytes)
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
            worker.join(1_000)
        }
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
