package com.feishin.mobile.data.backend

import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.DynamicTrustManager
import com.feishin.mobile.data.remote.HostRewriteInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import retrofit2.Retrofit

/**
 * Builds per-profile and ephemeral probe API clients sharing the singleton
 * [OkHttpClient] connection pool while isolating host, auth, and TLS rules.
 */
@Singleton
class BackendClientFactory @Inject constructor(
    private val json: Json,
    private val baseClient: OkHttpClient,
    @Suppress("UNUSED_PARAMETER")
    private val trustManager: DynamicTrustManager,
) {
    private data class CachedSubsonicApi(
        val url: String,
        val allowSelfSigned: Boolean,
        val credentials: ProfileCredentials,
        val api: SubsonicApi,
    )

    private val subsonicApis = ConcurrentHashMap<String, CachedSubsonicApi>()

    fun createEphemeralSubsonicApi(probeProfile: ServerProfile): SubsonicApi =
        buildSubsonicRetrofit(probeProfile).create(SubsonicApi::class.java)

    fun subsonicApi(profile: ServerProfile): SubsonicApi {
        val baseUrl = profile.url.trimEnd('/')
        val existing = subsonicApis[profile.id]
        if (existing != null &&
            existing.url == baseUrl &&
            existing.allowSelfSigned == profile.allowSelfSigned &&
            existing.credentials == profile.credentials
        ) {
            return existing.api
        }
        val created = buildSubsonicRetrofit(profile).create(SubsonicApi::class.java)
        subsonicApis[profile.id] = CachedSubsonicApi(
            url = baseUrl,
            allowSelfSigned = profile.allowSelfSigned,
            credentials = profile.credentials,
            api = created,
        )
        return created
    }

    private fun buildSubsonicRetrofit(profile: ServerProfile): Retrofit {
        val baseUrl = "${profile.url.trimEnd('/')}/rest/"
        val client = buildSubsonicClient(profile)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun buildSubsonicClient(profile: ServerProfile): OkHttpClient {
        val builder = baseClient.newBuilder()
        // Strip legacy single-server interceptors until P12.0-C removes them globally.
        builder.interceptors().removeAll { it is HostRewriteInterceptor || it is AuthInterceptor }

        if (profile.allowSelfSigned) {
            val permissive = permissiveTrustManager()
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(permissive), SecureRandom())
            }
            builder.sslSocketFactory(ssl.socketFactory, permissive)
            builder.hostnameVerifier { _, _ -> true }
        } else {
            val systemTm = defaultTrustManager()
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(systemTm), SecureRandom())
            }
            builder.sslSocketFactory(ssl.socketFactory, systemTm)
            builder.hostnameVerifier(OkHostnameVerifier)
        }

        val profileAuthInterceptor = Interceptor { chain ->
            val original = chain.request()
            val creds = profile.credentials as? ProfileCredentials.Subsonic
            val urlBuilder = original.url.newBuilder()
            if (creds != null) {
                val salt = SubsonicAuth.newSalt()
                val token = SubsonicAuth.token(creds.password, salt)
                urlBuilder
                    .addQueryParameter("u", creds.username)
                    .addQueryParameter("t", token)
                    .addQueryParameter("s", salt)
                    .addQueryParameter("v", SubsonicAuth.API_VERSION)
                    .addQueryParameter("c", SubsonicAuth.CLIENT_NAME)
                    .addQueryParameter("f", "json")
            }
            val request = original.newBuilder()
                .header(HEADER_PROFILE_ID, profile.id)
                .url(urlBuilder.build())
                .build()
            chain.proceed(request)
        }
        builder.interceptors().add(0, profileAuthInterceptor)
        return builder.build()
    }

    private fun defaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun permissiveTrustManager(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val HEADER_PROFILE_ID = "X-Vusic-Profile-Id"
    }
}
