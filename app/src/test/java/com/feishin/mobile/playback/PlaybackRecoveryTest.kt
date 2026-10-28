@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {

    @Test
    fun shouldPrepareBeforePlayOnlyInStateIdle() {
        assertTrue(PlaybackRecovery.shouldPrepareBeforePlay(Player.STATE_IDLE))
        assertFalse(PlaybackRecovery.shouldPrepareBeforePlay(Player.STATE_BUFFERING))
        assertFalse(PlaybackRecovery.shouldPrepareBeforePlay(Player.STATE_READY))
        assertFalse(PlaybackRecovery.shouldPrepareBeforePlay(Player.STATE_ENDED))
    }

    @Test
    fun shouldRetryFromNetworkGuardsAgainstLoops() {
        assertTrue(PlaybackRecovery.shouldRetryFromNetwork(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, alreadyRetried = false))
        assertTrue(PlaybackRecovery.shouldRetryFromNetwork(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, alreadyRetried = false))
        assertFalse(PlaybackRecovery.shouldRetryFromNetwork(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, alreadyRetried = true))
        assertFalse(PlaybackRecovery.shouldRetryFromNetwork(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, alreadyRetried = true))
    }

    @Test
    fun isInvalidCachedPayloadRejectsUnder8KB() {
        assertTrue(PlaybackRecovery.isInvalidCachedPayload(0L))
        assertTrue(PlaybackRecovery.isInvalidCachedPayload(100L))
        assertTrue(PlaybackRecovery.isInvalidCachedPayload(8191L))
        assertFalse(PlaybackRecovery.isInvalidCachedPayload(8192L))
        assertFalse(PlaybackRecovery.isInvalidCachedPayload(100_000L))
    }

    @Test
    fun isInvalidCachedPayloadRejectsJsonAndXml() {
        val jsonPayload = "{\"subsonic-response\":{\"status\":\"failed\",\"error\":{\"code\":70}}}".toByteArray()
        val xmlPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><subsonic-response status=\"failed\"/>".toByteArray()
        val whitespaceJson = "  \r\n  {\"status\": \"failed\"}".toByteArray()

        assertTrue(PlaybackRecovery.isInvalidCachedPayload(10_000L, jsonPayload))
        assertTrue(PlaybackRecovery.isInvalidCachedPayload(10_000L, xmlPayload))
        assertTrue(PlaybackRecovery.isInvalidCachedPayload(10_000L, whitespaceJson))

        val flacHeader = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0, 0, 0, 0)
        val id3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 0)
        assertFalse(PlaybackRecovery.isInvalidCachedPayload(10_000L, flacHeader))
        assertFalse(PlaybackRecovery.isInvalidCachedPayload(10_000L, id3Header))
    }
}
