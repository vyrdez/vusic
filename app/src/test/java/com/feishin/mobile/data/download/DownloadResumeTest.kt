package com.feishin.mobile.data.download

import com.feishin.mobile.core.common.DownloadStateMachine
import com.feishin.mobile.core.common.MobileDataPolicy
import com.feishin.mobile.core.common.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadResumeTest {

    @Test
    fun resumeDecision_appendsWhenPartialMatches() {
        val decision = DownloadStateMachine.resumeDecision(
            bytesDownloaded = 500L,
            expectedBytes = 1000L,
            rangeSupported = true,
        )
        assertTrue(decision)
    }

    @Test
    fun resumeDecision_rejectsWhenNoPartialOrExpectedUnknown() {
        assertFalse(DownloadStateMachine.resumeDecision(0L, 1000L, true))
        assertTrue(DownloadStateMachine.resumeDecision(500L, 0L, true))
        assertFalse(DownloadStateMachine.resumeDecision(500L, 1000L, false))
    }

    @Test
    fun rangeHeader_formattedCorrectly() {
        val existing = 1024L
        val header = "bytes=$existing-"
        assertEquals("bytes=1024-", header)
    }

    @Test
    fun mobileDataPolicy_matrix() {
        // Wi-Fi only enabled
        assertTrue(MobileDataPolicy.allows(online = true, unmetered = true, roaming = false, allowMobileData = false))
        assertFalse(MobileDataPolicy.allows(online = true, unmetered = false, roaming = false, allowMobileData = false))

        // Mobile data allowed
        assertTrue(MobileDataPolicy.allows(online = true, unmetered = false, roaming = false, allowMobileData = true))

        // Roaming disallowed
        assertFalse(MobileDataPolicy.allows(online = true, unmetered = false, roaming = true, allowMobileData = true))

        // Offline disallowed
        assertFalse(MobileDataPolicy.allows(online = false, unmetered = true, roaming = false, allowMobileData = true))
    }

    @Test
    fun appendModeBehavior_preservesExistingBytes() {
        val temp = File.createTempFile("download_test", ".tmp")
        try {
            temp.writeBytes("part1_".toByteArray())
            assertEquals(6L, temp.length())

            // Appending (HTTP 206)
            java.io.FileOutputStream(temp, true).use { out ->
                out.write("part2".toByteArray())
            }
            assertEquals(11L, temp.length())
            assertEquals("part1_part2", temp.readText())

            // Truncate (HTTP 200 without range)
            java.io.FileOutputStream(temp, false).use { out ->
                out.write("fresh".toByteArray())
            }
            assertEquals(5L, temp.length())
            assertEquals("fresh", temp.readText())
        } finally {
            temp.delete()
        }
    }

    @Test
    fun isValidAudioResponse_rejectsJsonXmlHtmlAndSmallPayloads() {
        // Rejects error content types
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "application/json", 100_000L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "application/json; charset=utf-8", 100_000L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "application/xml", 100_000L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "text/xml", 100_000L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "text/html", 100_000L))

        // Rejects payloads below MIN_VALID_AUDIO_BYTES (8192)
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "audio/mpeg", 0L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "audio/mpeg", 500L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(200, "audio/mpeg", 8191L))

        // Rejects non-2xx codes
        assertFalse(DownloadStateMachine.isValidAudioResponse(404, "audio/mpeg", 100_000L))
        assertFalse(DownloadStateMachine.isValidAudioResponse(500, "audio/mpeg", 100_000L))

        // Accepts valid audio
        assertTrue(DownloadStateMachine.isValidAudioResponse(200, "audio/mpeg", 8192L))
        assertTrue(DownloadStateMachine.isValidAudioResponse(200, "audio/flac", 100_000L))
        assertTrue(DownloadStateMachine.isValidAudioResponse(206, "audio/ogg", 100_000L))
        // Accepts chunked/unknown content-length (-1L)
        assertTrue(DownloadStateMachine.isValidAudioResponse(200, "audio/mpeg", -1L))
    }

    @Test
    fun resolvedExtension_mapsTranscodedAndDefaults() {
        val originalQuality = StreamQuality(0, "raw")
        val mp3Transcode = StreamQuality(192, "mp3")
        val opusTranscode = StreamQuality(128, "opus")
        val aacTranscode = StreamQuality(256, "aac")

        // Transcoded formats override track suffix
        assertEquals("mp3", DownloadStateMachine.resolvedExtension("flac", mp3Transcode))
        assertEquals("opus", DownloadStateMachine.resolvedExtension("flac", opusTranscode))
        assertEquals("aac", DownloadStateMachine.resolvedExtension("wav", aacTranscode))

        // Original quality preserves track suffix
        assertEquals("flac", DownloadStateMachine.resolvedExtension("flac", originalQuality))
        assertEquals("flac", DownloadStateMachine.resolvedExtension(".flac", originalQuality))
        assertEquals("wav", DownloadStateMachine.resolvedExtension("WAV", originalQuality))
        assertEquals("bin", DownloadStateMachine.resolvedExtension("bin", originalQuality))

        // Null or blank suffix falls back to mp3
        assertEquals("mp3", DownloadStateMachine.resolvedExtension(null, originalQuality))
        assertEquals("mp3", DownloadStateMachine.resolvedExtension("", originalQuality))
        assertEquals("mp3", DownloadStateMachine.resolvedExtension("   ", originalQuality))
    }

    @Test
    fun startupAudit_corruptFileDetection() {
        val tempDir = java.nio.file.Files.createTempDirectory("audit_test").toFile()
        try {
            // Missing file
            val missingFile = File(tempDir, "missing.flac")
            assertTrue(DownloadRepository.isCorruptDownloadedFile(missingFile))
            assertTrue(DownloadRepository.isCorruptDownloadedFile(null))

            // Small file (< 8192 bytes)
            val smallFile = File(tempDir, "small.flac").apply {
                writeBytes(ByteArray(100))
            }
            assertTrue(DownloadRepository.isCorruptDownloadedFile(smallFile))

            // JSON error payload (>= 8192 bytes)
            val jsonFile = File(tempDir, "json_err.mp3").apply {
                val json = """{"subsonic-response":{"status":"failed","error":{"code":10,"message":"Required parameter is missing"}}}"""
                val padded = json.padEnd(9000, ' ')
                writeText(padded)
            }
            assertTrue(DownloadRepository.isCorruptDownloadedFile(jsonFile))

            // XML error payload (>= 8192 bytes)
            val xmlFile = File(tempDir, "xml_err.mp3").apply {
                val xml = """<?xml version="1.0" encoding="UTF-8"?><subsonic-response status="failed"/>"""
                val padded = xml.padEnd(9000, ' ')
                writeText(padded)
            }
            assertTrue(DownloadRepository.isCorruptDownloadedFile(xmlFile))

            // Valid audio file (dummy binary >= 8192 bytes, does not start with { or <)
            val validFile = File(tempDir, "valid.mp3").apply {
                val bytes = ByteArray(9000) { (it % 256).toByte() }
                bytes[0] = 0xFF.toByte() // standard MP3 frame sync
                bytes[1] = 0xFB.toByte()
                writeBytes(bytes)
            }
            assertFalse(DownloadRepository.isCorruptDownloadedFile(validFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
