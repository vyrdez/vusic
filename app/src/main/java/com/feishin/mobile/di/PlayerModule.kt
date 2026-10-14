@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
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
    fun provideCache(
        @ApplicationContext ctx: Context,
        settings: com.feishin.mobile.data.settings.SettingsStore,
    ): SimpleCache {
        val dir = File(ctx.cacheDir, "stream_cache").apply { mkdirs() }
        // Read synchronously at graph build: a changed size takes effect on the
        // next launch (the evictor capacity is fixed at construction).
        val mb: Int = runCatching {
            kotlinx.coroutines.runBlocking {
                val s: com.feishin.mobile.data.settings.SettingsStore.Settings =
                    settings.flow.first()
                s.cacheSizeMb
            }
        }.getOrDefault(512).coerceIn(256, 5120)
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(mb.toLong() * 1024 * 1024),
            StandaloneDatabaseProvider(ctx),
        )
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
