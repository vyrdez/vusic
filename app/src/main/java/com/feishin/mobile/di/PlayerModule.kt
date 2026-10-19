@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.data.cache.LiveLruCacheEvictor
import com.feishin.mobile.data.remote.DynamicTrustManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @androidx.media3.common.util.UnstableApi
    @Provides @Singleton
    fun provideCacheEvictor(
        settings: com.feishin.mobile.data.settings.SettingsStore,
        downloads: com.feishin.mobile.data.download.DownloadRepository,
    ): LiveLruCacheEvictor = LiveLruCacheEvictor(
        maxBytesProvider = {
            // Read live so a smaller limit trims the cache immediately.
            runCatching {
                runBlocking {
                    val s: com.feishin.mobile.data.settings.SettingsStore.Settings = settings.flow.first()
                    CacheSizePolicy.bytes(s.cacheSizeMb)
                }
            }.getOrDefault(CacheSizePolicy.bytes(CacheSizePolicy.DEFAULT_STREAM_MB))
        },
        // Real free space, so the cache stops growing before the phone is full.
        freeBytesProvider = { downloads.freeBytes() },
    )

    @androidx.media3.common.util.UnstableApi
    @Provides @Singleton
    fun provideCache(
        @ApplicationContext ctx: Context,
        evictor: LiveLruCacheEvictor,
    ): SimpleCache {
        val dir = File(ctx.cacheDir, "stream_cache").apply { mkdirs() }
        return SimpleCache(dir, evictor, StandaloneDatabaseProvider(ctx))
    }

    /**
     * Streaming client used by the player. Stream URLs are already signed per
     * request, so no auth interceptors are needed; the dynamic trust manager is
     * still shared so the self-signed setting applies without a restart.
     */
    @Provides @Singleton @Named("streaming")
    fun provideStreamingClient(tm: DynamicTrustManager): OkHttpClient =
        OkHttpClient.Builder()
            .sslSocketFactory(tm.sslContext().socketFactory, tm)
            .hostnameVerifier { hostname, session ->
                if (tm.allowSelfSigned) true
                else okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Stream auth params are single-use; never let OkHttp replay a request.
            .retryOnConnectionFailure(false)
            .build()
}
