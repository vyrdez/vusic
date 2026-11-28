package com.feishin.mobile.di

import android.content.Context
import androidx.work.WorkManager
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
import com.feishin.mobile.data.remote.drive.DriveTokenAuthenticator
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
     * Transfer client: enforces per-host TLS, cleartext guard, host-pinned auth
     * (preserving pre-signed Subsonic download URLs), and redirect guard.
     */
    @Provides @Singleton @DownloadClient
    fun provideDownloadClient(
        cleartextGuard: CleartextGuardInterceptor,
        profileAuth: ProfileAuthInterceptor,
        driveTokenAuthenticator: DriveTokenAuthenticator,
        tm: ProfileTrustManager,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .sslSocketFactory(tm.sslContext().socketFactory, tm)
            .hostnameVerifier(tm.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .authenticator(driveTokenAuthenticator)
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // a whole song/album may take a while
            .retryOnConnectionFailure(true)
            .build()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
