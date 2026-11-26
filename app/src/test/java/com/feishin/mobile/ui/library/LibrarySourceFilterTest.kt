package com.feishin.mobile.ui.library

import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySourceFilterTest {

    private fun navidromeProfile(
        id: String,
        name: String,
        priority: Int,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = name,
        type = BackendType.NAVIDROME,
        url = "https://navi.example.com",
        credentials = ProfileCredentials.Subsonic(username = "alice", password = "pw"),
        enabled = enabled,
        priority = priority,
    )

    private fun openSubsonicProfile(
        id: String,
        name: String,
        priority: Int,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = name,
        type = BackendType.OPENSUBSONIC,
        url = "https://sub.example.com",
        credentials = ProfileCredentials.Subsonic(username = "bob", password = "pw"),
        enabled = enabled,
        priority = priority,
    )

    private fun jellyfinProfile(
        id: String,
        name: String,
        priority: Int,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        name = name,
        type = BackendType.JELLYFIN,
        url = "https://jf.example.com",
        credentials = ProfileCredentials.Jellyfin(
            username = "carol",
            accessToken = "tok",
            serverId = "srv-$id",
            deviceId = "dev-1",
        ),
        enabled = enabled,
        priority = priority,
    )

    @Test
    fun `jellyfin appears in View my only when an enabled jellyfin profile exists`() {
        val disabledJellyfin = jellyfinProfile(id = "jf-1", name = "Home Jellyfin", priority = 0, enabled = false)
        val navidrome = navidromeProfile(id = "nd-1", name = "Main Navidrome", priority = 1, enabled = true)
        val openSubsonic = openSubsonicProfile(id = "os-1", name = "Gonic", priority = 2, enabled = true)

        val withoutEnabledJellyfin = buildViewMyFilterModel(
            profiles = listOf(disabledJellyfin, navidrome, openSubsonic),
            showDeviceFiles = true,
        )

        assertFalse(withoutEnabledJellyfin.typeFilters.any { it.type == BackendType.JELLYFIN })
        assertFalse(withoutEnabledJellyfin.profileFilters.any { it.id == "jf-1" })
        assertEquals(listOf("Navidrome", "OpenSubsonic"), withoutEnabledJellyfin.typeFilters.map { it.label })
        assertTrue(withoutEnabledJellyfin.showDeviceFiles)

        val enabledJellyfin = disabledJellyfin.copy(enabled = true)
        val withEnabledJellyfin = buildViewMyFilterModel(
            profiles = listOf(navidrome, openSubsonic, enabledJellyfin),
            showDeviceFiles = true,
        )

        assertEquals(
            listOf("Jellyfin", "Navidrome", "OpenSubsonic"),
            withEnabledJellyfin.typeFilters.map { it.label },
        )
        assertTrue(withEnabledJellyfin.profileFilters.any { it.id == "jf-1" && it.name == "Home Jellyfin" })
        assertTrue(withEnabledJellyfin.showDeviceFiles)
    }

    @Test
    fun `multiple jellyfin profiles show individual names and strictly follow priority rank`() {
        val jfLowPriority = jellyfinProfile(id = "jf-low", name = "Jellyfin Archive", priority = 30)
        val ndMidPriority = navidromeProfile(id = "nd-mid", name = "Navidrome Studio", priority = 20)
        val jfHighPriority = jellyfinProfile(id = "jf-high", name = "Jellyfin Primary", priority = 10)
        val osLowestPriority = openSubsonicProfile(id = "os-last", name = "OpenSubsonic Backup", priority = 40)

        val model = buildViewMyFilterModel(
            profiles = listOf(jfLowPriority, ndMidPriority, jfHighPriority, osLowestPriority),
            showDeviceFiles = false,
            degradedProfileIds = setOf("jf-low"),
        )

        assertEquals("4 active servers", model.allSubtitle)
        assertEquals(
            listOf(BackendType.JELLYFIN, BackendType.NAVIDROME, BackendType.OPENSUBSONIC),
            model.typeFilters.map { it.type },
        )
        assertEquals(2, model.typeFilters.first { it.type == BackendType.JELLYFIN }.count)
        assertEquals(
            listOf("Jellyfin Primary", "Navidrome Studio", "Jellyfin Archive", "OpenSubsonic Backup"),
            model.profileFilters.map { it.name },
        )
        assertTrue(model.profileFilters.first { it.id == "jf-low" }.subtitle.endsWith("· Degraded"))
        assertFalse(model.showDeviceFiles)
    }
}
