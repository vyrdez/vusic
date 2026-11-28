package com.feishin.mobile.ui.library

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun googleDriveProfile(
        id: String,
        name: String,
        priority: Int,
        enabled: Boolean = true,
        email: String = "drive.user@gmail.com",
    ) = ServerProfile(
        id = id,
        name = name,
        type = BackendType.GOOGLE_DRIVE,
        url = "",
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = email,
            accessToken = "drive-tok-$id",
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

    @Test
    fun `google drive appears in View my with Google Drive label and follows priority rank`() {
        val driveDisabled = googleDriveProfile(id = "gd-off", name = "Drive Disabled", priority = 0, enabled = false)
        val navidrome = navidromeProfile(id = "nd-1", name = "Main Navidrome", priority = 20)
        val drivePrimary = googleDriveProfile(
            id = "gd-1",
            name = "My Drive Music",
            priority = 10,
            email = "music@gmail.com",
        )
        val jellyfin = jellyfinProfile(id = "jf-1", name = "Home Jellyfin", priority = 30)

        val model = buildViewMyFilterModel(
            profiles = listOf(driveDisabled, navidrome, drivePrimary, jellyfin),
            showDeviceFiles = true,
        )

        assertEquals("3 active servers", model.allSubtitle)
        assertEquals(
            listOf(BackendType.GOOGLE_DRIVE, BackendType.NAVIDROME, BackendType.JELLYFIN),
            model.typeFilters.map { it.type },
        )
        assertEquals(
            listOf("Google Drive", "Navidrome", "Jellyfin"),
            model.typeFilters.map { it.label },
        )
        assertEquals(
            listOf("My Drive Music", "Main Navidrome", "Home Jellyfin"),
            model.profileFilters.map { it.name },
        )
        assertEquals("Google Drive · music@gmail.com", model.profileFilters.first { it.id == "gd-1" }.subtitle)
    }

    @Test
    fun `local_pl playlists are unprefixed reserved ids visible under All sources and excluded under member backend filters`() {
        val driveProfile = googleDriveProfile(id = "gd-1", name = "Drive", priority = 10)
        val navProfile = navidromeProfile(id = "nd-1", name = "Navidrome", priority = 20)
        val profiles = listOf(driveProfile, navProfile)

        val playlists = listOf(
            Playlist(id = "gd-1:pl-1", name = "Drive Mix", songCount = 5, durationSec = 0, coverArt = null),
            Playlist(id = "nd-1:pl-2", name = "Nav Mix", songCount = 8, durationSec = 0, coverArt = null),
            Playlist(id = "local_pl:uuid-1", name = "Cross-Server Favorites", songCount = 12, durationSec = 0, coverArt = null),
        )

        fun visibleForSource(sourceFilter: String): List<String> {
            val filter = sourceFilter.trim()
            return when {
                filter.isBlank() || filter.equals("ALL", ignoreCase = true) -> playlists.map { it.id }
                filter.startsWith("TYPE:", ignoreCase = true) -> {
                    val targetType = BackendType.valueOf(filter.substringAfter(":").trim())
                    val matchingProfileIds = profiles.filter { it.type == targetType }.map { it.id }.toSet()
                    playlists.filter { GlobalId.profileIdOrNull(it.id) in matchingProfileIds }.map { it.id }
                }
                filter.startsWith("PROFILE:", ignoreCase = true) -> {
                    val pid = filter.substringAfter(":").trim()
                    playlists.filter { GlobalId.profileIdOrNull(it.id) == pid }.map { it.id }
                }
                else -> emptyList()
            }
        }

        assertTrue(GlobalId.isReserved("local_pl:uuid-1"))
        assertNull(GlobalId.profileIdOrNull("local_pl:uuid-1"))

        assertEquals(listOf("gd-1:pl-1", "nd-1:pl-2", "local_pl:uuid-1"), visibleForSource("ALL"))
        assertEquals(listOf("gd-1:pl-1"), visibleForSource("TYPE:GOOGLE_DRIVE"))
        assertEquals(listOf("nd-1:pl-2"), visibleForSource("TYPE:NAVIDROME"))
        assertEquals(listOf("gd-1:pl-1"), visibleForSource("PROFILE:gd-1"))
    }
}
