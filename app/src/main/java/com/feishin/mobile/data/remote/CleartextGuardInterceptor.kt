package com.feishin.mobile.data.remote

import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ServerProfile
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class CleartextNotPermittedException(message: String) : IOException(message)

/**
 * First application interceptor on all 4 OkHttpClients. Enforces per-profile
 * `allowCleartext` (and global `SettingsStore.allowCleartext` fallback for
 * unconfigured URLs). Unconditionally blocks cleartext `http://` for Google Drive.
 */
@Singleton
class CleartextGuardInterceptor @Inject constructor(
    private val profileStore: ProfileStore,
    private val probeRegistry: TransientProbeRegistry,
    private val settings: SettingsStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isHttps) return chain.proceed(request)

        val url = request.url
        if (url.host.equals(ProfileAuthInterceptor.GOOGLE_API_HOST, ignoreCase = true)) {
            throw CleartextNotPermittedException(
                "Cleartext HTTP is never permitted for Google Drive (${url.host})",
            )
        }

        val profile = resolveProfile(request, url)
        if (profile != null) {
            if (profile.type == BackendType.GOOGLE_DRIVE) {
                throw CleartextNotPermittedException(
                    "Cleartext HTTP is never permitted for Google Drive (${url.host})",
                )
            }
            if (!profile.allowCleartext) {
                throw CleartextNotPermittedException(
                    "Cleartext HTTP is disabled for ${profile.name.ifBlank { url.host }}",
                )
            }
            return chain.proceed(request)
        }

        val allowGlobal = runCatching {
            runBlocking { settings.flow.first().allowCleartext }
        }.getOrElse { settings.latestSettings?.allowCleartext ?: false }
        if (!allowGlobal) {
            throw CleartextNotPermittedException(
                "Cleartext HTTP is disabled (${url.host})",
            )
        }
        return chain.proceed(request)
    }

    private fun resolveProfile(request: Request, url: HttpUrl): ServerProfile? {
        val profileId = request.header(ProfileAuthInterceptor.HEADER_PROFILE_ID)
        if (!profileId.isNullOrBlank()) {
            return if (profileId.startsWith(TransientProbeRegistry.PROBE_PREFIX)) {
                probeRegistry.get(profileId)
            } else {
                profileStore.byId(profileId)
            }
        }
        return probeRegistry.findByHost(url.host, url.port)
            ?: profileStore.findByHost(url.scheme, url.host, url.port)
    }
}
