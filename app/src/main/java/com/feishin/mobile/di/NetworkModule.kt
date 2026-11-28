package com.feishin.mobile.di

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.network.CoverCacheKey
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
import com.feishin.mobile.data.remote.RedactingHttpLogger
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.remote.drive.DriveCoverFetcher
import com.feishin.mobile.data.remote.drive.DriveRangeReader
import com.feishin.mobile.data.remote.drive.DriveTokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideOkHttp(
        cleartextGuard: CleartextGuardInterceptor,
        profileAuth: ProfileAuthInterceptor,
        driveTokenAuthenticator: DriveTokenAuthenticator,
        tm: ProfileTrustManager,
    ): OkHttpClient {
        val ssl = tm.sslContext()
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier(tm.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .authenticator(driveTokenAuthenticator)
            // Library reads must fail visibly and fast on a bad network: the
            // spinner turns into an error with Retry instead of hanging.
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            // One attempt per call: a dead server must never multiply into a
            // retry storm behind every tab and every revalidation.
            .retryOnConnectionFailure(false)
            // Debug-only, credential-redacting (release: no logging at all).
            .addInterceptor(RedactingHttpLogger())
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit {
        // Legacy fallback Retrofit instance retained until P12.0-G replaces MusicRepository;
        // per-profile APIs are created by BackendClientFactory.
        return Retrofit.Builder()
            .baseUrl("https://localhost/rest/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): SubsonicApi = retrofit.create(SubsonicApi::class.java)

    /**
     * Dedicated client for Coil: shares per-host TLS, cleartext guard, host-pinned
     * auth (no double-signing on pre-signed Subsonic cover URLs), and redirect guard.
     */
    @Provides @Singleton @javax.inject.Named("coil")
    fun provideCoilOkHttp(
        cleartextGuard: CleartextGuardInterceptor,
        profileAuth: ProfileAuthInterceptor,
        driveTokenAuthenticator: DriveTokenAuthenticator,
        tm: ProfileTrustManager,
    ): OkHttpClient {
        val ssl = tm.sslContext()
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier(tm.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .authenticator(driveTokenAuthenticator)
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .build()
    }

    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext ctx: Context,
        @javax.inject.Named("coil") coilClient: OkHttpClient,
        settings: com.feishin.mobile.data.settings.SettingsStore,
        cachedCoverDao: com.feishin.mobile.data.db.CachedCoverDao,
        profileStore: ProfileStore,
        driveRangeReader: DriveRangeReader,
    ): ImageLoader {
        // Same deal as the stream cache: the size is fixed at construction, so
        // a changed value takes effect on the next launch.
        val mb: Int = runCatching {
            runBlocking {
                val s: com.feishin.mobile.data.settings.SettingsStore.Settings =
                    settings.flow.first()
                s.imageCacheMb
            }
        }.getOrDefault(250).coerceIn(100, 1024)
        return ImageLoader.Builder(ctx)
            .okHttpClient(coilClient)
            .components {
                add(OfflineCoverInterceptor(cachedCoverDao, profileStore))
                add(DriveCoverFetcher.Factory(cachedCoverDao, driveRangeReader))
                add(com.feishin.mobile.core.ui.LocalAudioCoverFetcher.Factory(ctx))
            }
            .crossfade(true)
            .placeholder(com.feishin.mobile.R.drawable.art_placeholder)
            .error(com.feishin.mobile.R.drawable.art_error)
            .fallback(com.feishin.mobile.R.drawable.art_placeholder)
            .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(ctx.cacheDir.resolve("coil")).maxSizeBytes(mb.toLong() * 1024 * 1024).build() }
            .build()
    }
}

class OfflineCoverInterceptor(
    private val cachedCoverDao: com.feishin.mobile.data.db.CachedCoverDao,
    private val profileStore: ProfileStore? = null,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): coil.request.ImageResult {
        val request = chain.request
        val url = request.data?.toString() ?: ""
        if (url.startsWith("drive-cover://", ignoreCase = true)) {
            return chain.proceed(request)
        }
        val key = resolveKey(request, url)
        if (!key.isNullOrBlank()) {
            val path = cachedCoverDao.path(key)
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    return chain.proceed(request.newBuilder().data(file).build())
                }
            }
        }
        val taggedProfileId = request.tags.tag(ProfileIdTag::class.java)?.profileId?.takeIf { it.isNotBlank() }
            ?: key?.let { GlobalId.profileIdOrNull(it) }
        val nextRequest = if (
            taggedProfileId != null &&
            request.headers[ProfileAuthInterceptor.HEADER_PROFILE_ID].isNullOrBlank()
        ) {
            request.newBuilder()
                .setHeader(ProfileAuthInterceptor.HEADER_PROFILE_ID, taggedProfileId)
                .build()
        } else {
            request
        }
        return chain.proceed(nextRequest)
    }

    internal fun resolveKey(request: coil.request.ImageRequest, url: String): String? {
        request.tags.tag(CoverCacheKey::class.java)?.key?.takeIf { it.isNotBlank() }?.let { return it }
        val taggedProfileId = request.tags.tag(ProfileIdTag::class.java)?.profileId?.takeIf { it.isNotBlank() }
        val parsedUrl = runCatching { url.toHttpUrlOrNull() }.getOrNull()
        if (url.contains("getCoverArt")) {
            val rawId = parsedUrl?.queryParameter("id")
                ?: runCatching { Uri.parse(url).getQueryParameter("id") }.getOrNull()
            if (!rawId.isNullOrBlank()) {
                val profileId = taggedProfileId ?: parsedUrl?.let {
                    profileStore?.findByHost(it.scheme, it.host, it.port)?.id
                }
                return if (profileId != null) GlobalId.format(profileId, rawId) else rawId
            }
        }
        if (parsedUrl != null) {
            JELLYFIN_COVER_REGEX.find(parsedUrl.encodedPath)?.groupValues?.getOrNull(1)?.let { itemId ->
                val profileId = taggedProfileId
                    ?: profileStore?.findByHost(parsedUrl.scheme, parsedUrl.host, parsedUrl.port)?.id
                return if (profileId != null) GlobalId.format(profileId, itemId) else itemId
            }
        }
        return null
    }

    private companion object {
        val JELLYFIN_COVER_REGEX = Regex("/Items/([^/]+)/Images/Primary")
    }
}
