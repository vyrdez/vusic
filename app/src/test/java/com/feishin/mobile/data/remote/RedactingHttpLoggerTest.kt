package com.feishin.mobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Part D: credentials must never reach a log line. */
class RedactingHttpLoggerTest {

    @Test
    fun redact_hidesUserTokenAndSalt() {
        val url = "https://host/rest/search3.view?query=x&u=demo&t=abc123&s=deadbeef&v=1.16.1&c=FeishinMobile&f=json"
        val out = RedactingHttpLogger.redact(url)
        assertFalse("username leaked", out.contains("demo"))
        assertFalse("token leaked", out.contains("abc123"))
        assertFalse("salt leaked", out.contains("deadbeef"))
        assertEquals(true, out.contains("u=***"))
        assertEquals(true, out.contains("t=***"))
        assertEquals(true, out.contains("s=***"))
        // Non-secret params and the base stay readable for debugging.
        assertEquals(true, out.contains("query=x"))
        assertEquals(true, out.contains("v=1.16.1"))
        assertEquals(true, out.startsWith("https://host/rest/search3.view?"))
    }

    @Test
    fun redact_hidesPasswordParam() {
        val out = RedactingHttpLogger.redact("https://host/rest/stream.view?id=1&u=me&p=hunter2")
        assertFalse(out.contains("hunter2"))
        assertEquals(true, out.contains("p=***"))
    }

    @Test
    fun redact_leavesUrlsWithoutQueryUntouched() {
        assertEquals("https://host/rest/ping.view", RedactingHttpLogger.redact("https://host/rest/ping.view"))
    }

    @Test
    fun redact_keepsParamsWithoutValues() {
        val out = RedactingHttpLogger.redact("https://host/x?flag&u=me")
        assertEquals(true, out.contains("flag"))
        assertEquals(true, out.contains("u=***"))
    }

    @Test
    fun disabledInReleaseOnly() {
        // Robolectric-less unit test runs as debug; release is enforced by R8
        // stripping the build-flag branch (asserted in PLAN.md).
        assertEquals(true, RedactingHttpLogger.ENABLED == com.feishin.mobile.BuildConfig.DEBUG)
    }
}
