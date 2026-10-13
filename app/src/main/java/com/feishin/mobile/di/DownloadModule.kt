package com.feishin.mobile.di

import android.content.Context
import androidx.work.WorkManager
import com.feishin.mobile.data.remote.DynamicTrustManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Marks the client used for file transfers (longer read timeouts, no auth rewrite). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    /**
     * Transfer client: the download/stream URLs are already signed per request, so
     * no auth interceptors are needed, but the shared trust manager keeps the
     * self-signed setting working without a restart.
     */
    @Provides @Singleton @DownloadClient
    fun provideDownloadClient(tm: DynamicTrustManager): OkHttpClient =
        OkHttpClient.Builder()
            .sslSocketFactory(tm.sslContext().socketFactory, tm)
            .hostnameVerifier { hostname, session ->
                if (tm.allowSelfSigned) true
                else okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // a whole song/album may take a while
            .retryOnConnectionFailure(true)
            .build()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
