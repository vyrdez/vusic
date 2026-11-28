package com.feishin.mobile.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class BackendType {
    NAVIDROME,
    OPENSUBSONIC,
    JELLYFIN,
    GOOGLE_DRIVE,
}

@Serializable
data class OpenSubsonicExtensions(
    val songLyrics: Boolean = false,
    val transcodeOffset: Boolean = false,
    val formPost: Boolean = false,
    val apiKeyAuthentication: Boolean = false,
    val rawNames: List<String> = emptyList(),
)

@Serializable
sealed interface ProfileCredentials {
    @Serializable
    data class Subsonic(
        val username: String,
        val password: String,
        val apiKey: String? = null,
    ) : ProfileCredentials

    @Serializable
    data class Jellyfin(
        val username: String,
        val accessToken: String,
        val serverId: String,
        val deviceId: String,
    ) : ProfileCredentials

    @Serializable
    data class GoogleDrive(
        val accountEmail: String,
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAtEpochMs: Long = 0L,
    ) : ProfileCredentials
}

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val type: BackendType,
    val url: String,
    val userId: String = "",
    val credentials: ProfileCredentials,
    val allowSelfSigned: Boolean = false,
    val allowCleartext: Boolean = true,
    val driveRootFolderId: String? = null,
    val driveRootFolderName: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val openSubsonicExtensions: OpenSubsonicExtensions = OpenSubsonicExtensions(),
)

@Immutable
data class BackendCapabilities(
    val supportsServerPlaylists: Boolean,
    val supportsScrobbling: Boolean,
    val supportsServerLyrics: Boolean,
    val supportsTranscoding: Boolean,
    val supportsRadioMix: Boolean,
    val supportsLibraryScan: Boolean,
    val supportsServerFavorites: Boolean = true,
) {
    companion object {
        val DEVICE_LOCAL = BackendCapabilities(
            supportsServerPlaylists = false,
            supportsScrobbling = false,
            supportsServerLyrics = false,
            supportsTranscoding = false,
            supportsRadioMix = false,
            supportsLibraryScan = false,
            supportsServerFavorites = false,
        )

        private val FULL_REMOTE = BackendCapabilities(
            supportsServerPlaylists = true,
            supportsScrobbling = true,
            supportsServerLyrics = true,
            supportsTranscoding = true,
            supportsRadioMix = true,
            supportsLibraryScan = true,
            supportsServerFavorites = true,
        )

        private val GOOGLE_DRIVE_CAPS = BackendCapabilities(
            supportsServerPlaylists = false,
            supportsScrobbling = false,
            supportsServerLyrics = false,
            supportsTranscoding = false,
            supportsRadioMix = false,
            supportsLibraryScan = true,
            supportsServerFavorites = false,
        )

        fun forType(type: BackendType): BackendCapabilities = when (type) {
            BackendType.NAVIDROME,
            BackendType.OPENSUBSONIC,
            BackendType.JELLYFIN -> FULL_REMOTE
            BackendType.GOOGLE_DRIVE -> GOOGLE_DRIVE_CAPS
        }
    }
}
