package com.feishin.mobile.core.network

import com.feishin.mobile.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {

    @Test
    fun token_knownSaltAndPassword_matchesMd5() {
        // md5("secretabc123") computed independently (Python/PowerShell reference).
        assertEquals("f22ae4f5eacb35c993e910606a413410", SubsonicAuth.token("secret", "abc123"))
    }

    @Test
    fun token_differsPerSalt() {
        assertNotEquals(
            SubsonicAuth.token("secret", "salt1"),
            SubsonicAuth.token("secret", "salt2"),
        )
    }

    @Test
    fun newSalt_isFresh32HexChars() {
        val a = SubsonicAuth.newSalt()
        val b = SubsonicAuth.newSalt()
        assertEquals(32, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, b)
    }

    @Test
    fun normalize_addsSchemeAndTrimsSlash() {
        assertEquals("https://host:4533/music", SubsonicAuth.normalizeServerUrl("host:4533/music"))
        assertEquals("http://h/x", SubsonicAuth.normalizeServerUrl("http://h/x/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalize_rejectsBlank() {
        SubsonicAuth.normalizeServerUrl("   ")
    }

    @Test
    fun buildUrl_withoutPrefix() {
        val url = SubsonicAuth.buildUrl(
            "https://host:4533",
            "rest/ping.view",
            mapOf("u" to "demo", "t" to "tok", "s" to "salt", "v" to "1.16.1", "c" to "FeishinMobile", "f" to "json"),
        )
        assertEquals(
            "https://host:4533/rest/ping.view?u=demo&t=tok&s=salt&v=1.16.1&c=FeishinMobile&f=json",
            url,
        )
    }

    @Test
    fun buildUrl_withPathPrefix() {
        val url = SubsonicAuth.buildUrl(
            "https://example.com/navidrome",
            "rest/ping.view",
            mapOf("u" to "demo", "t" to "tok", "s" to "salt", "v" to "1.16.1", "c" to "FeishinMobile", "f" to "json"),
        )
        assertEquals(
            "https://example.com/navidrome/rest/ping.view?u=demo&t=tok&s=salt&v=1.16.1&c=FeishinMobile&f=json",
            url,
        )
    }

    @Test
    fun buildUrl_encodesParameters() {
        val url = SubsonicAuth.buildUrl(
            "https://host",
            "rest/search3.view",
            mapOf("query" to "a b&c=?"),
        )
        assertTrue(url.startsWith("https://host/rest/search3.view?query="))
        assertTrue(url.contains("a%20b%26c%3D%3F"))
        assertTrue(!url.contains(" "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildUrl_rejectsGarbage() {
        SubsonicAuth.buildUrl("::::", "rest/ping.view", emptyMap())
    }

    @Test
    fun parseEnvelope_ok() {
        val r = SubsonicAuth.parseEnvelope("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""")
        assertTrue(r is Result.Ok)
        assertEquals("ok", (r as Result.Ok).value.status)
        assertEquals("1.16.1", r.value.version)
    }

    @Test
    fun parseEnvelope_error_wrongPassword() {
        val r = SubsonicAuth.parseEnvelope(
            """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}""",
        )
        assertTrue(r is Result.Ok)
        val body = (r as Result.Ok).value
        assertEquals("failed", body.status)
        assertEquals(40, body.error?.code)
        assertEquals("Wrong username or password.", SubsonicAuth.userMessage(body.error?.code, body.error?.message))
    }

    @Test
    fun parseEnvelope_malformed_isErr() {
        val r = SubsonicAuth.parseEnvelope("this is not json {{{")
        assertTrue(r is Result.Err)
    }

    @Test
    fun parseEnvelope_htmlLoginPage_isErr() {
        val r = SubsonicAuth.parseEnvelope("<html><body>nginx</body></html>")
        assertTrue(r is Result.Err)
    }

    @Test
    fun userMessage_transportFailures() {
        assertTrue(
            SubsonicAuth.userMessage(null, null, java.net.UnknownHostException("x"))
                .contains("not found", ignoreCase = true),
        )
        assertTrue(
            SubsonicAuth.userMessage(null, null, java.net.SocketTimeoutException())
                .contains("timed out", ignoreCase = true),
        )
    }
}
