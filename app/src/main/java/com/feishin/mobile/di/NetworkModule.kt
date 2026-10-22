package com.feishin.mobile.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.DynamicTrustManager
import com.feishin.mobile.data.remote.HostRewriteInterceptor
import com.feishin.mobile.data.remote.RedactingHttpLogger
import com.feishin.mobile.data.remote.SubsonicApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    fun provideOkHttp(auth: AuthInterceptor, rewrite: HostRewriteInterceptor, tm: DynamicTrustManager): OkHttpClient {
        val ssl = tm.sslContext()
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier { hostname, session ->
                if (tm.allowSelfSigned) true
                else okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
            }
            .addInterceptor(rewrite)
            .addInterceptor(auth)
            // Library reads must fail visibly and fast on a bad network: the
            // spinner turns into an error with Retry instead of hanging.
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            // One attempt per call: a dead server must never multiply into a
            // retry storm behind every tab and every revalidation.
            .retryOnConnectionFailure(false)
            // Debug-only, credential-redacting (release: no logging at all).
            .addInterceptor(RedactingHttpLogger())
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit {
        // Real host comes from HostRewriteInterceptor (reacts to login), so any valid base works.
        return Retrofit.Builder()
            .baseUrl("https://localhost/rest/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): SubsonicApi = retrofit.create(SubsonicApi::class.java)

    /**
     * Dedicated client for Coil: shares the dynamic trust manager (self-signed
     * setting applies, no restart) but skips the host-rewrite/auth interceptors,
     * since cover URLs are already absolute and signed with a stable salt.
     */
    @Provides @Singleton @javax.inject.Named("coil")
    fun provideCoilOkHttp(tm: DynamicTrustManager): OkHttpClient {
        val ssl = tm.sslContext()
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier { hostname, session ->
                if (tm.allowSelfSigned) true
                else okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
            }
            .build()
    }

    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext ctx: Context,
        @javax.inject.Named("coil") coilClient: OkHttpClient,
        settings: com.feishin.mobile.data.settings.SettingsStore,
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
