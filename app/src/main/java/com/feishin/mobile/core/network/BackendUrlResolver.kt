package com.feishin.mobile.core.network

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.StableCacheKey
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure Subsonic/OpenSubsonic URL builder (JVM-safe via OkHttp [HttpUrl], no `android.net.Uri`).
 */
internal object SubsonicUrlBuilder {
    const val DEFAULT_FORMAT = "raw"

    private fun normalizeBaseUrl(serverUrl: String): String =
        SubsonicAuth.normalizeServerUrl(serverUrl)
            .removeSuffix("/rest/")
            .removeSuffix("/rest")
            .trimEnd('/')

    fun streamUrl(
        serverUrl: String,
        username: String,
        password: String,
        rawTrackId: String,
        maxBitrate: Int = 0,
        format: String = DEFAULT_FORMAT,
    ): String {
        val query = linkedMapOf("id" to rawTrackId)
        if (maxBitrate > 0) query["maxBitRate"] = maxBitrate.toString()
        if (format.isNotBlank() && format != DEFAULT_FORMAT) query["format"] = format
        query.putAll(SubsonicAuth.authQuery(username, password))
        return SubsonicAuth.buildUrl(normalizeBaseUrl(serverUrl), "rest/stream.view", query)
    }

    fun coverArtUrl(
        serverUrl: String,
        username: String,
        password: String,
        rawCoverArtId: String,
        size: Int = 512,
    ): String? {
        if (rawCoverArtId.isBlank()) return null
        return CoverArtUrlProvider.build(
            serverUrl = normalizeBaseUrl(serverUrl),
            username = username,
            password = password,
            coverArtId = rawCoverArtId,
            size = size,
        )
    }

    fun downloadUrl(
        serverUrl: String,
        username: String,
        password: String,
        rawTrackId: String,
    ): String {
        val query = linkedMapOf("id" to rawTrackId)
        query.putAll(SubsonicAuth.authQuery(username, password))
        return SubsonicAuth.buildUrl(normalizeBaseUrl(serverUrl), "rest/download.view", query)
    }
}

/**
 * Pure Jellyfin URL builder (JVM-safe via OkHttp [HttpUrl], no `android.net.Uri`).
 */
internal object JellyfinUrlBuilder {
    private const val FALLBACK_TRANSCODE_CODEC = "mp3"

    private fun parseBaseUrl(serverUrl: String): HttpUrl? =
        runCatching { JellyfinUrl.normalize(serverUrl).toHttpUrlOrNull() }.getOrNull()

    fun streamUrl(
        serverUrl: String,
        userId: String,
        deviceId: String,
        rawTrackId: String,
        maxBitrate: Int = 0,
        format: String = SubsonicUrlBuilder.DEFAULT_FORMAT,
    ): String? {
        val base = parseBaseUrl(serverUrl) ?: return null
        val normalizedFormat = format.trim().lowercase(Locale.ROOT)
        val isDirect = maxBitrate <= 0 && (normalizedFormat.isEmpty() || normalizedFormat == SubsonicUrlBuilder.DEFAULT_FORMAT)
        val builder = base.newBuilder()
            .addPathSegment("Audio")
            .addPathSegment(rawTrackId)
        if (isDirect) {
            return builder
                .addPathSegment("stream")
                .addQueryParameter("static", "true")
                .build()
                .toString()
        }
        val codec = normalizedFormat.takeIf {
            it.isNotEmpty() && it != SubsonicUrlBuilder.DEFAULT_FORMAT
        } ?: FALLBACK_TRANSCODE_CODEC
        builder.addPathSegment("universal")
        if (userId.isNotBlank()) builder.addQueryParameter("UserId", userId)
        if (deviceId.isNotBlank()) builder.addQueryParameter("DeviceId", deviceId)
        if (maxBitrate > 0) builder.addQueryParameter("MaxStreamingBitrate", (maxBitrate * 1000L).toString())
        return builder
            .addQueryParameter("AudioCodec", codec)
            .addQueryParameter("Container", codec)
            .addQueryParameter("TranscodingProtocol", "http")
            .addQueryParameter("TranscodingContainer", codec)
            .addQueryParameter("EnableRedirection", "false")
            .addQueryParameter("EnableRemoteMedia", "false")
            .build()
            .toString()
    }

    fun coverArtUrl(
        serverUrl: String,
        rawCoverArtId: String,
        size: Int = 512,
    ): String? {
        if (rawCoverArtId.isBlank()) return null
        val base = parseBaseUrl(serverUrl) ?: return null
        return base.newBuilder()
            .addPathSegment("Items")
            .addPathSegment(rawCoverArtId)
            .addPathSegment("Images")
            .addPathSegment("Primary")
            .addQueryParameter("maxWidth", size.toString())
            .addQueryParameter("quality", "90")
            .build()
            .toString()
    }

    fun downloadUrl(
        serverUrl: String,
        rawTrackId: String,
    ): String? {
        val base = parseBaseUrl(serverUrl) ?: return null
        return base.newBuilder()
            .addPathSegment("Audio")
            .addPathSegment(rawTrackId)
            .addPathSegment("stream")
            .addQueryParameter("static", "true")
            .build()
            .toString()
    }
}

/**
 * Single multi-backend URL resolver for playback, prefetch, downloads, widgets,
 * and MediaSession artwork — eliminating direct `CredentialsStore` bypasses.
 */
@Singleton
class BackendUrlResolver @Inject constructor(
    private val profileStore: ProfileStore,
) {
    /**
     * Ensures [ProfileStore] has finished initial preload and waits up to
     * [timeoutMs] for at least one profile when none are loaded yet.
     */
    suspend fun awaitProfilesReady(timeoutMs: Long = 10_000L): Boolean {
        runCatching { profileStore.preload() }
        if (profileStore.profiles.value.isNotEmpty()) return true
        return withTimeoutOrNull(timeoutMs) {
            profileStore.profiles.first { it.isNotEmpty() }
        } != null
    }

    fun streamUrl(track: Track, maxBitrate: Int = 0, format: String = "raw"): String? {
        if (track.isLocal) return track.localUri
        val profile = resolveProfile(track.id) ?: return null
        val rawId = GlobalId.rawId(track.id)
        return when (profile.type) {
            BackendType.NAVIDROME,
            BackendType.OPENSUBSONIC -> {
                val creds = profile.credentials as? ProfileCredentials.Subsonic ?: return null
                runCatching {
                    SubsonicUrlBuilder.streamUrl(
                        serverUrl = profile.url,
                        username = creds.username,
                        password = creds.password,
                        rawTrackId = rawId,
                        maxBitrate = maxBitrate,
                        format = format,
                    )
                }.getOrNull()
            }
            BackendType.JELLYFIN -> buildJellyfinStreamUrl(profile, rawId, maxBitrate, format)
            BackendType.GOOGLE_DRIVE -> buildGoogleDriveFileUrl(rawId)
        }
    }

    fun coverArtUrl(coverArtId: String?, size: Int = 512): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (coverArtId.startsWith("content://") ||
            coverArtId.startsWith("file://") ||
            coverArtId.startsWith("http://") ||
            coverArtId.startsWith("https://")
        ) {
            return coverArtId
        }
        val profile = resolveProfile(coverArtId) ?: return null
        val rawId = GlobalId.rawId(coverArtId)
        if (rawId.isBlank()) return null
        return when (profile.type) {
            BackendType.NAVIDROME,
            BackendType.OPENSUBSONIC -> {
                val creds = profile.credentials as? ProfileCredentials.Subsonic ?: return null
                runCatching {
                    SubsonicUrlBuilder.coverArtUrl(
                        serverUrl = profile.url,
                        username = creds.username,
                        password = creds.password,
                        rawCoverArtId = rawId,
                        size = size,
                    )
                }.getOrNull()
            }
            BackendType.JELLYFIN -> JellyfinUrlBuilder.coverArtUrl(
                serverUrl = profile.url,
                rawCoverArtId = rawId,
                size = size,
            )
            BackendType.GOOGLE_DRIVE -> {
                if (rawId.startsWith("cov:")) {
                    "drive-cover://${profile.id}/${rawId.removePrefix("cov:")}"
                } else {
                    buildGoogleDriveFileUrl(rawId)
                }
            }
        }
    }

    fun downloadUrl(track: Track): String? {
        if (track.isLocal) return track.localUri
        val profile = resolveProfile(track.id) ?: return null
        val rawId = GlobalId.rawId(track.id)
        return when (profile.type) {
            BackendType.NAVIDROME,
            BackendType.OPENSUBSONIC -> {
                val creds = profile.credentials as? ProfileCredentials.Subsonic ?: return null
                runCatching {
                    SubsonicUrlBuilder.downloadUrl(
                        serverUrl = profile.url,
                        username = creds.username,
                        password = creds.password,
                        rawTrackId = rawId,
                    )
                }.getOrNull()
            }
            BackendType.JELLYFIN -> JellyfinUrlBuilder.downloadUrl(
                serverUrl = profile.url,
                rawTrackId = rawId,
            )
            BackendType.GOOGLE_DRIVE -> buildGoogleDriveFileUrl(rawId)
        }
    }

    fun customCacheKey(streamUrl: String, mediaId: String): String =
        StableCacheKey.keyFor(streamUrl, GlobalId.profileIdOrNull(mediaId))

    private fun resolveProfile(globalId: String): ServerProfile? {
        if (GlobalId.isDeviceLocal(globalId) || GlobalId.isReserved(globalId)) return null
        val explicitProfileId = GlobalId.profileIdOrNull(globalId)
        if (explicitProfileId != null) {
            return profileStore.byId(explicitProfileId)
        }
        return profileStore.byId(DEFAULT_LEGACY_PROFILE_ID)
            ?: profileStore.activeProfiles.value.firstOrNull()
    }

    private fun parseBaseUrl(rawUrl: String): HttpUrl? =
        runCatching { SubsonicAuth.normalizeServerUrl(rawUrl).toHttpUrlOrNull() }.getOrNull()

    private fun buildJellyfinStreamUrl(
        profile: ServerProfile,
        rawId: String,
        maxBitrate: Int,
        format: String,
    ): String? {
        val creds = profile.credentials as? ProfileCredentials.Jellyfin
        return JellyfinUrlBuilder.streamUrl(
            serverUrl = profile.url,
            userId = profile.userId,
            deviceId = creds?.deviceId.orEmpty(),
            rawTrackId = rawId,
            maxBitrate = maxBitrate,
            format = format,
        )
    }

    private fun buildGoogleDriveFileUrl(rawId: String): String =
        HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("drive/v3/files")
            .addPathSegment(rawId)
            .addQueryParameter("alt", "media")
            .build()
            .toString()

    private companion object {
        const val DEFAULT_LEGACY_PROFILE_ID = "p1"
    }
}
