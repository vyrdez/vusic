package com.feishin.mobile.data.backend

import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
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
    private data class CachedSubsonicApi(
        val url: String,
        val api: SubsonicApi,
    )

    private val subsonicApis = ConcurrentHashMap<String, CachedSubsonicApi>()

    fun createEphemeralSubsonicApi(probeProfile: ServerProfile): SubsonicApi =
        buildSubsonicRetrofit(probeProfile).create(SubsonicApi::class.java)

    fun subsonicApi(profile: ServerProfile): SubsonicApi {
        val baseUrl = profile.url.trimEnd('/')
        val existing = subsonicApis[profile.id]
        if (existing != null && existing.url == baseUrl) {
            return existing.api
        }
        val created = buildSubsonicRetrofit(profile).create(SubsonicApi::class.java)
        subsonicApis[profile.id] = CachedSubsonicApi(
            url = baseUrl,
            api = created,
        )
        return created
    }

    /** Drops any cached Retrofit client for [profileId] (e.g. on URL change or logout). */
    fun invalidate(profileId: String) {
        subsonicApis.remove(profileId)
    }

    /** Drops all cached Retrofit clients. */
    fun clearAll() {
        subsonicApis.clear()
    }

    private fun buildSubsonicRetrofit(profile: ServerProfile): Retrofit {
        val baseUrl = "${profile.url.trimEnd('/')}/rest/"
        val client = buildProfileTaggedClient(profile.id)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * Shares [baseClient]'s connection pool, TLS manager, cleartext guard,
     * host-pinned auth interceptor, and redirect guard; prepends only the
     * internal `X-Vusic-Profile-Id` tag (stripped before wire by [ProfileAuthInterceptor]).
     */
    private fun buildProfileTaggedClient(profileId: String): OkHttpClient {
        val builder = baseClient.newBuilder()
        val tagger = Interceptor { chain ->
            val tagged = chain.request().newBuilder()
                .header(HEADER_PROFILE_ID, profileId)
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
