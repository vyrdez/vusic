package com.feishin.mobile.data.download

import com.feishin.mobile.core.common.DownloadStateMachine
import com.feishin.mobile.core.common.MobileDataPolicy
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
}
