package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlProviderTest {
    @Test
    fun withoutPathPrefix_containsAllAuthParams() {
        val url = StreamUrlProvider.build("https://host:4533", "demo", "secret", "abc123")
        assertTrue(url.startsWith("https://host:4533/rest/stream.view?"))
        assertTrue(url.contains("id=abc123"))
        assertTrue(url.contains("u=demo"))
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("c=FeishinMobile"))
        assertTrue(url.contains("f=json"))
        assertTrue(url.contains("s="))
        assertTrue(url.contains("t="))
        // Plain password is never part of the URL.
        assertFalse(url.contains("secret"))
    }

    @Test
    fun withPathPrefix_isPreserved() {
        val url = StreamUrlProvider.build("https://example.com/navidrome", "u1", "pw", "id1")
        assertTrue(url.startsWith("https://example.com/navidrome/rest/stream.view?"))
    }

    @Test
    fun tokenMatchesMd5OfPasswordPlusSalt() {
        val url = StreamUrlProvider.build("https://host", "u1", "pw", "id1")
        val params = url.substringAfter('?').split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }
        val expected = SubsonicAuth.md5Hex("pw" + params.getValue("s"))
        assertEquals(expected, params.getValue("t"))
    }

    @Test
    fun freshSaltPerCall() {
        val a = StreamUrlProvider.build("https://host", "u", "p", "id")
        val b = StreamUrlProvider.build("https://host", "u", "p", "id")
        assertFalse(a == b)
    }

    @Test
    fun defaultIsOriginalQuality_noBitrateNoFormat() {
        val url = StreamUrlProvider.build("https://host", "u", "p", "id")
        assertFalse(url.contains("maxBitRate"))
        assertFalse(url.contains("format="))
    }

    @Test
    fun bitrateAndFormatIncluded_whenSet() {
        val url = StreamUrlProvider.build("https://host", "u", "p", "id", maxBitrate = 192, format = "opus")
        assertTrue(url.contains("maxBitRate=192"))
        assertTrue(url.contains("format=opus"))
    }

    @Test
    fun rawFormatIsOmitted() {
        val url = StreamUrlProvider.build("https://host", "u", "p", "id", 0, "raw")
        assertFalse(url.contains("format="))
    }

    @Test
    fun idsAreUrlEncoded() {
        val url = StreamUrlProvider.build("https://host", "u", "p", "id with space&x")
        assertFalse(url.contains(" "))
        assertTrue(url.contains("id%20with%20space%26x"))
    }
}