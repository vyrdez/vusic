package com.feishin.mobile.data.remote.drive

import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Single-flight OkHttp [Authenticator] for Google Drive `401 Unauthorized` responses.
 *
 * Only handles `https://www.googleapis.com:443`, resolves the originating profile via
 * [ProfileIdTag] (or unambiguous single enabled Drive profile), enforces a max-retry cap,
 * and marks [ProfileAuthStateStore] as expired when silent refresh requires consent or fails.
 */
@Singleton
class DriveTokenAuthenticator internal constructor(
    private val profileStore: ProfileStore,
    private val profileAuthStateStore: ProfileAuthStateStore,
    private val tokenRefresher: DriveTokenRefresher,
    private val isDriveOrigin: (HttpUrl) -> Boolean = ::isGoogleDriveApiOrigin,
) : Authenticator {

    @Inject
    constructor(
        profileStore: ProfileStore,
        profileAuthStateStore: ProfileAuthStateStore,
        driveAuthCoordinator: DriveAuthCoordinator,
    ) : this(
        profileStore = profileStore,
        profileAuthStateStore = profileAuthStateStore,
        tokenRefresher = driveAuthCoordinator,
        isDriveOrigin = ::isGoogleDriveApiOrigin,
    )

    private val profileMutexes = ConcurrentHashMap<String, Mutex>()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null
        val request = response.request
        if (!isDriveOrigin(request.url)) return null

        val profileId = resolveProfileId(request) ?: return null
        if (profileId.startsWith(TransientProbeRegistry.PROBE_PREFIX)) return null

        if (responseCount(response) >= MAX_AUTH_ATTEMPTS) {
            profileAuthStateStore.markExpired(profileId)
            return null
        }

        val failedToken = extractBearerToken(
            request.header(ProfileAuthInterceptor.HEADER_AUTHORIZATION),
        )
        val mutex = profileMutexes.computeIfAbsent(profileId) { Mutex() }

        return runBlocking {
            mutex.withLock {
                val profile = profileStore.byId(profileId) ?: return@withLock null
                if (!profile.enabled || profile.type != BackendType.GOOGLE_DRIVE) {
                    return@withLock null
                }
                val creds = profile.credentials as? ProfileCredentials.GoogleDrive
                    ?: return@withLock null

                val currentToken = creds.accessToken.trim()
                if (currentToken.isNotBlank() && currentToken != failedToken) {
                    profileAuthStateStore.clear(profileId)
                    return@withLock request.newBuilder()
                        .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer $currentToken")
                        .tag(ProfileIdTag::class.java, ProfileIdTag(profileId))
                        .build()
                }

                val tokenToClear = failedToken.ifBlank { currentToken }
                when (val outcome = tokenRefresher.refreshTokenBlocking(creds.accountEmail, tokenToClear)) {
                    DriveRefreshOutcome.ResolutionRequired,
                    is DriveRefreshOutcome.Failed,
                    -> {
                        profileAuthStateStore.markExpired(profileId)
                        null
                    }
                    is DriveRefreshOutcome.Refreshed -> {
                        val newToken = outcome.accessToken.trim()
                        if (newToken.isBlank() || newToken == failedToken) {
                            profileAuthStateStore.markExpired(profileId)
                            return@withLock null
                        }
                        val latestProfile = profileStore.byId(profileId) ?: profile
                        val latestCreds = (latestProfile.credentials as? ProfileCredentials.GoogleDrive) ?: creds
                        profileStore.upsert(
                            latestProfile.copy(
                                credentials = latestCreds.copy(
                                    accessToken = newToken,
                                    expiresAtEpochMs = outcome.expiresAtEpochMs,
                                ),
                            ),
                        )
                        profileAuthStateStore.clear(profileId)
                        request.newBuilder()
                            .header(ProfileAuthInterceptor.HEADER_AUTHORIZATION, "Bearer $newToken")
                            .tag(ProfileIdTag::class.java, ProfileIdTag(profileId))
                            .build()
                    }
                }
            }
        }
    }

    private fun resolveProfileId(request: Request): String? {
        request.tag(ProfileIdTag::class.java)?.profileId?.takeIf { it.isNotBlank() }?.let { return it }
        return profileStore.profiles.value
            .filter { it.enabled && it.type == BackendType.GOOGLE_DRIVE }
            .singleOrNull()
            ?.id
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun extractBearerToken(headerValue: String?): String {
        if (headerValue.isNullOrBlank()) return ""
        val trimmed = headerValue.trim()
        return if (trimmed.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            trimmed.substring(BEARER_PREFIX.length).trim()
        } else {
            ""
        }
    }

    companion object {
        private const val MAX_AUTH_ATTEMPTS = 2
        private const val BEARER_PREFIX = "Bearer "

        internal fun isGoogleDriveApiOrigin(url: HttpUrl): Boolean =
            url.isHttps &&
                url.host.equals(ProfileAuthInterceptor.GOOGLE_API_HOST, ignoreCase = true) &&
                url.port == 443
    }
}
