package com.feishin.mobile.data.backend

import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.domain.model.ServerProfile
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Builds per-profile and ephemeral probe API clients sharing the singleton
 * [OkHttpClient] connection pool, [com.feishin.mobile.data.remote.ProfileTrustManager],
 * [com.feishin.mobile.data.remote.CleartextGuardInterceptor], and
 * [ProfileAuthInterceptor].
 */
@Singleton
class BackendClientFactory @Inject constructor(
    private val json: Json,
    private val baseClient: OkHttpClient,
) {
    private data class CachedApi<T>(
        val url: String,
        val api: T,
    )

    private val subsonicApis = ConcurrentHashMap<String, CachedApi<SubsonicApi>>()
    private val jellyfinApis = ConcurrentHashMap<String, CachedApi<JellyfinApi>>()

    fun createEphemeralSubsonicApi(probeProfile: ServerProfile): SubsonicApi =
        buildSubsonicRetrofit(probeProfile).create(SubsonicApi::class.java)

    fun subsonicApi(profile: ServerProfile): SubsonicApi {
        val baseUrl = profile.url.trimEnd('/')
        val existing = subsonicApis[profile.id]
        if (existing != null && existing.url == baseUrl) {
            return existing.api
        }
        val created = buildSubsonicRetrofit(profile).create(SubsonicApi::class.java)
        subsonicApis[profile.id] = CachedApi(
            url = baseUrl,
            api = created,
        )
        return created
    }

    fun createEphemeralJellyfinApi(probeProfile: ServerProfile): JellyfinApi =
        buildJellyfinRetrofit(probeProfile).create(JellyfinApi::class.java)

    fun jellyfinApi(profile: ServerProfile): JellyfinApi {
        val normalizedUrl = JellyfinUrl.normalize(profile.url)
        val existing = jellyfinApis[profile.id]
        if (existing != null && existing.url == normalizedUrl) {
            return existing.api
        }
        val created = buildJellyfinRetrofit(profile, normalizedUrl).create(JellyfinApi::class.java)
        jellyfinApis[profile.id] = CachedApi(
            url = normalizedUrl,
            api = created,
        )
        return created
    }

    /** Drops any cached Retrofit client for [profileId] (e.g. on URL change or logout). */
    fun invalidate(profileId: String) {
        subsonicApis.remove(profileId)
        jellyfinApis.remove(profileId)
    }

    /** Drops all cached Retrofit clients. */
    fun clearAll() {
        subsonicApis.clear()
        jellyfinApis.clear()
    }

    private fun buildSubsonicRetrofit(profile: ServerProfile): Retrofit =
        buildRetrofit(
            baseUrl = "${profile.url.trimEnd('/')}/rest/",
            profileId = profile.id,
        )

    private fun buildJellyfinRetrofit(
        profile: ServerProfile,
        normalizedUrl: String = JellyfinUrl.normalize(profile.url),
    ): Retrofit = buildRetrofit(
        baseUrl = "$normalizedUrl/",
        profileId = profile.id,
    )

    private fun buildRetrofit(baseUrl: String, profileId: String): Retrofit {
        val client = buildProfileTaggedClient(profileId)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * Shares [baseClient]'s connection pool, TLS manager, cleartext guard,
     * host-pinned auth interceptor, and redirect guard; prepends both [ProfileIdTag]
     * and the internal `X-Vusic-Profile-Id` header (stripped before wire by [ProfileAuthInterceptor]).
     */
    private fun buildProfileTaggedClient(profileId: String): OkHttpClient {
        val builder = baseClient.newBuilder()
        val tagger = Interceptor { chain ->
            val tagged = chain.request().newBuilder()
                .header(HEADER_PROFILE_ID, profileId)
                .tag(ProfileIdTag::class.java, ProfileIdTag(profileId))
                .build()
            chain.proceed(tagged)
        }
        builder.interceptors().add(0, tagger)
        return builder.build()
    }

    companion object {
        const val HEADER_PROFILE_ID = ProfileAuthInterceptor.HEADER_PROFILE_ID
    }
}
