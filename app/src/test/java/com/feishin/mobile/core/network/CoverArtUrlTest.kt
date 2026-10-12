package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtUrlTest {
    @Test
    fun nullOrBlankId_returnsNull() {
        assertNull(CoverArtUrlProvider.build("https://h", "u", "p", null, 300))
        assertNull(CoverArtUrlProvider.build("https://h", "u", "p", "  ", 300))
    }

    @Test
    fun stableAcrossCalls_cacheCanHit() {
        val a = CoverArtUrlProvider.build("https://host:4533", "demo", "secret", "al-123", 300)
        val b = CoverArtUrlProvider.build("https://host:4533", "demo", "secret", "al-123", 300)
        assertEquals(a, b)
    }

    @Test
    fun differsPerCoverAndSize() {
        val base = Triple("https://host:4533", "demo", "secret")
        val (h, u, p) = base
        val a = CoverArtUrlProvider.build(h, u, p, "al-1", 300)
        val b = CoverArtUrlProvider.build(h, u, p, "al-2", 300)
        val c = CoverArtUrlProvider.build(h, u, p, "al-1", 600)
        assertTrue(a != b && a != c)
    }

    @Test
    fun keepsPathPrefix_andHasAuthParams() {
        val url = CoverArtUrlProvider.build("https://example.com/navidrome", "demo", "secret", "al-9", 300)!!
        assertTrue(url.startsWith("https://example.com/navidrome/rest/getCoverArt.view?"))
        assertTrue(url.contains("id=al-9"))
        assertTrue(url.contains("size=300"))
        assertTrue(url.contains("u=demo"))
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("c=FeishinMobile"))
        assertTrue(url.contains("f=json"))
        // Token present but the plain password never appears.
        assertTrue(!url.contains("secret"))
    }

    @Test
    fun stableSalt_isDeterministicAndSized() {
        val a = SubsonicAuth.stableSalt("cover:al-1")
        assertEquals(a, SubsonicAuth.stableSalt("cover:al-1"))
        assertEquals(32, a.length)
        assertEquals(SubsonicAuth.md5Hex("feishin:cover:al-1"), a)
    }
}
