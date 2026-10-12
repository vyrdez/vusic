package com.feishin.mobile.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.DynamicTrustManager
import com.feishin.mobile.data.remote.HostRewriteInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier { hostname, session ->
                if (tm.allowSelfSigned) true
                else okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
            }
            .addInterceptor(rewrite)
            .addInterceptor(auth)
            .addInterceptor(logging)
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

    @Provides @Singleton
    fun provideImageLoader(@ApplicationContext ctx: Context): ImageLoader =
        ImageLoader.Builder(ctx)
            .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(ctx.cacheDir.resolve("coil")).maxSizeBytes(256L * 1024 * 1024).build() }
            .build()
}
