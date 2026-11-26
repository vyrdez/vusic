package com.feishin.mobile.data.remote

import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Host-pinned authentication interceptor shared by all 4 OkHttpClients
 * (API, Coil, ExoPlayer streaming, and Downloads).
 *
 * Security invariants:
 * 1. The internal `X-Vusic-Profile-Id` routing header is ALWAYS stripped before wire.
 * 2. Credentials are injected ONLY when the request's normalized `(scheme, host, port)`
 *    matches the resolved profile's URL (or `https://www.googleapis.com:443` for Drive).
 * 3. Disabled profiles never inject credentials (only active transient probes bypass `enabled`).
 * 4. [redirectGuard] runs as a network interceptor to strip `Authorization`, `X-Emby-Token`,
 *    and Subsonic `u/t/s` query params whenever an HTTP 3xx redirect hops to a different
 *    `(scheme, host, port)` or an unpinned target.
 */
@Singleton
class ProfileAuthInterceptor @Inject constructor(
    private val profileStore: ProfileStore,
    private val probeRegistry: TransientProbeRegistry,
    private val profileAuthStateStore: ProfileAuthStateStore,
) : Interceptor {

    internal constructor(
        profileStore: ProfileStore,
        probeRegistry: TransientProbeRegistry,
    ) : this(profileStore, probeRegistry, ProfileAuthStateStore())

    private data class AuthTargetHost(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val profileId = original.tag(ProfileIdTag::class.java)?.profileId?.takeIf { it.isNotBlank() }
            ?: original.header(HEADER_PROFILE_ID)?.takeIf { it.isNotBlank() }
        // Always strip the internal routing header before the request hits the wire.
        val sanitizedBuilder = original.newBuilder().removeHeader(HEADER_PROFILE_ID)

        val (profile, isProbe) = resolveProfile(profileId, original.url)
        if (profile == null || (!isProbe && !profile.enabled)) {
            return chain.proceed(sanitizedBuilder.build())
        }

        val updated = applyCredentials(original, sanitizedBuilder, profile)
        val response = chain.proceed(updated)
        if (response.code == 401 &&
            !isProbe &&
            profile.type == BackendType.JELLYFIN &&
            !isJellyfinAuthenticateByName(original.url)
        ) {
            val profileUrl = profile.url.toHttpUrlOrNull()
            if (profileUrl != null &&
                matchesOrigin(original.url, profileUrl) &&
                matchesOrigin(response.request.url, profileUrl)
            ) {
                profileAuthStateStore.markExpired(profile.id)
            }
        }
        return response
    }

    /**
     * Network interceptor that strips credentials on cross-host/port/scheme 3xx redirects
     * and prevents credential leakage to unregistered hosts.
     */
    fun redirectGuard(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        val target = request.tag(AuthTargetHost::class.java)

        val needsStrip = when {
            target != null -> !matchesTarget(url, target)
            hasAnyCredential(request) -> !isRegisteredAuthHost(url)
            else -> false
        }

        if (!needsStrip && request.header(HEADER_PROFILE_ID) == null) {
            return@Interceptor chain.proceed(request)
        }

        val builder = request.newBuilder().removeHeader(HEADER_PROFILE_ID)
        if (needsStrip) {
            builder
                .removeHeader(HEADER_AUTHORIZATION)
                .removeHeader(HEADER_EMBY_TOKEN)
            if (url.queryParameter("u") != null ||
                url.queryParameter("t") != null ||
                url.queryParameter("s") != null
            ) {
                val cleanUrl = url.newBuilder()
                    .removeAllQueryParameters("u")
                    .removeAllQueryParameters("t")
                    .removeAllQueryParameters("s")
                    .build()
                builder.url(cleanUrl)
            }
        }
        chain.proceed(builder.build())
    }

    private fun resolveProfile(profileId: String?, url: HttpUrl): Pair<ServerProfile?, Boolean> {
        if (!profileId.isNullOrBlank()) {
            return if (profileId.startsWith(TransientProbeRegistry.PROBE_PREFIX)) {
                probeRegistry.get(profileId) to true
            } else {
                profileStore.byId(profileId) to false
            }
        }
        return profileStore.findByHost(url.scheme, url.host, url.port) to false
    }

    private fun applyCredentials(
        original: Request,
        builder: Request.Builder,
        profile: ServerProfile,
    ): Request {
        val url = original.url
        return when (profile.type) {
            BackendType.NAVIDROME,
            BackendType.OPENSUBSONIC -> {
                val profileUrl = profile.url.toHttpUrlOrNull() ?: return builder.build()
                if (!matchesOrigin(url, profileUrl)) return builder.build()
                val creds = profile.credentials as? ProfileCredentials.Subsonic ?: return builder.build()
                val alreadySigned = url.queryParameter("u") != null &&
                    url.queryParameter("t") != null &&
                    url.queryParameter("s") != null
                if (!alreadySigned) {
                    val salt = SubsonicAuth.newSalt()
                    val token = SubsonicAuth.token(creds.password, salt)
                    val urlBuilder = url.newBuilder()
                        .setQueryParameter("u", creds.username)
                        .setQueryParameter("t", token)
                        .setQueryParameter("s", salt)
                    if (url.queryParameter("v") == null) {
                        urlBuilder.addQueryParameter("v", SubsonicAuth.API_VERSION)
                    }
                    if (url.queryParameter("c") == null) {
                        urlBuilder.addQueryParameter("c", SubsonicAuth.CLIENT_NAME)
                    }
                    if (url.queryParameter("f") == null) {
                        urlBuilder.addQueryParameter("f", "json")
                    }
                    builder.url(urlBuilder.build())
                }
                builder.tag(
                    AuthTargetHost::class.java,
                    AuthTargetHost(
                        scheme = profileUrl.scheme.lowercase(),
                        host = profileUrl.host.lowercase(),
                        port = profileUrl.port,
                    ),
                )
                builder.build()
            }

            BackendType.JELLYFIN -> {
                val profileUrl = profile.url.toHttpUrlOrNull() ?: return builder.build()
                if (!matchesOrigin(url, profileUrl)) return builder.build()
                val creds = profile.credentials as? ProfileCredentials.Jellyfin ?: return builder.build()
                val isAuthenticate = isJellyfinAuthenticateByName(url)
                if (isAuthenticate) {
                    builder.removeHeader(HEADER_EMBY_TOKEN)
                } else if (creds.accessToken.isNotBlank()) {
                    builder.header(HEADER_EMBY_TOKEN, creds.accessToken)
                }
                val tokenForHeader = if (isAuthenticate) null else creds.accessToken.takeIf { it.isNotBlank() }
                builder
                    .header(
                        HEADER_AUTHORIZATION,
                        jellyfinAuthHeader(deviceId = creds.deviceId, token = tokenForHeader),
                    )
                    .tag(
                        AuthTargetHost::class.java,
                        AuthTargetHost(
                            scheme = profileUrl.scheme.lowercase(),
                            host = profileUrl.host.lowercase(),
                            port = profileUrl.port,
                        ),
                    )
                builder.build()
            }

            BackendType.GOOGLE_DRIVE -> {
                if (!isGoogleDriveApiOrigin(url)) return builder.build()
                val creds = profile.credentials as? ProfileCredentials.GoogleDrive ?: return builder.build()
                builder
                    .header(HEADER_AUTHORIZATION, "Bearer ${creds.accessToken}")
                    .tag(
                        AuthTargetHost::class.java,
                        AuthTargetHost(
                            scheme = "https",
                            host = GOOGLE_API_HOST,
                            port = 443,
                        ),
                    )
                builder.build()
            }
        }
    }

    private fun hasAnyCredential(request: Request): Boolean {
        if (request.header(HEADER_AUTHORIZATION) != null) return true
        if (request.header(HEADER_EMBY_TOKEN) != null) return true
        val url = request.url
        return url.queryParameter("u") != null &&
            url.queryParameter("t") != null &&
            url.queryParameter("s") != null
    }

    private fun isRegisteredAuthHost(url: HttpUrl): Boolean {
        if (isGoogleDriveApiOrigin(url)) {
            if (profileStore.profiles.value.any { it.enabled && it.type == BackendType.GOOGLE_DRIVE }) {
                return true
            }
        }
        val stored = profileStore.findByHost(url.scheme, url.host, url.port)
        if (stored != null && stored.enabled) return true
        val probe = probeRegistry.findByHost(url.host, url.port)
        if (probe != null) {
            val probeUrl = probe.url.toHttpUrlOrNull()
            if (probeUrl != null && matchesOrigin(url, probeUrl)) return true
        }
        return false
    }

    private fun matchesOrigin(requestUrl: HttpUrl, profileUrl: HttpUrl): Boolean =
        requestUrl.scheme.equals(profileUrl.scheme, ignoreCase = true) &&
            requestUrl.host.equals(profileUrl.host, ignoreCase = true) &&
            requestUrl.port == profileUrl.port

    private fun matchesTarget(url: HttpUrl, target: AuthTargetHost): Boolean =
        url.scheme.equals(target.scheme, ignoreCase = true) &&
            url.host.equals(target.host, ignoreCase = true) &&
            url.port == target.port

    private fun isJellyfinAuthenticateByName(url: HttpUrl): Boolean =
        url.encodedPath.trimEnd('/').endsWith("/Users/AuthenticateByName", ignoreCase = true)

    private fun jellyfinAuthHeader(deviceId: String, token: String?): String = buildString {
        append("""MediaBrowser Client="Vusic", Device="Android", DeviceId="$deviceId", Version="$CLIENT_VERSION"""")
        if (!token.isNullOrBlank()) {
            append(""", Token="$token"""")
        }
    }

    private fun isGoogleDriveApiOrigin(url: HttpUrl): Boolean =
        url.isHttps &&
            url.host.equals(GOOGLE_API_HOST, ignoreCase = true) &&
            url.port == 443

    companion object {
        const val HEADER_PROFILE_ID = "X-Vusic-Profile-Id"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_EMBY_TOKEN = "X-Emby-Token"
        const val GOOGLE_API_HOST = "www.googleapis.com"
        val CLIENT_VERSION: String get() = BuildConfig.VERSION_NAME
    }
}
