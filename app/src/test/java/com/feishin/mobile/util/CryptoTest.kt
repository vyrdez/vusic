package com.feishin.mobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    @Test
    fun md5KnownVector() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5Hex("hello"))
    }

    @Test
    fun normalizeAddsSchemeAndRest() {
        assertEquals("https://host:4533/music/rest/", normalizeServerUrl("host:4533/music"))
        assertEquals("http://h/x/rest/", normalizeServerUrl("http://h/x/"))
    }

    @Test
    fun normalizeRejectsBlank() {
        var threw = false
        try {
            normalizeServerUrl("   ")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
