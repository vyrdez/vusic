@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.DynamicTrustManager
import com.feishin.mobile.data.store.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @androidx.media3.common.util.UnstableApi
    @Provides @Singleton
    fun provideCache(@ApplicationContext ctx: Context): SimpleCache {
        val dir = File(ctx.cacheDir, "stream_cache").apply { mkdirs() }
        // Size is reconfigured at runtime; default 512MB here.
        return SimpleCache(dir, LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024), StandaloneDatabaseProvider(ctx))
    }

    @androidx.media3.common.util.UnstableApi
    @Provides @Singleton
    fun provideExoPlayer(
        @ApplicationContext ctx: Context,
        cache: SimpleCache,
        auth: AuthInterceptor,
        tm: DynamicTrustManager,
    ): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val cacheSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ctx)
                    .setDataSourceFactory(cacheSource),
            )
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
