@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.feishin.mobile.data.download.DownloadNotifications
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class FeishinApp : Application(), ImageLoaderFactory, Configuration.Provider {

    /** Entry point so Application can touch Hilt singletons for warmup. */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WarmupEntry {
        fun okHttp(): okhttp3.OkHttpClient
        fun cache(): androidx.media3.datasource.cache.SimpleCache
    }

    // Lazy: ImageLoader construction does a blocking DataStore read for the
    // disk-cache size, so it must NOT run in Application.onCreate. Coil calls
    // newImageLoader() on first image load instead — after the first frame.
    @Inject lateinit var imageLoaderProvider: javax.inject.Provider<ImageLoader>
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Lets Hilt inject into WorkManager workers. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = imageLoaderProvider.get()

    override fun onCreate() {
        super.onCreate()
        // Debug-only: catch disk/network on the main thread early. Release is
        // unaffected (BuildConfig.DEBUG is a compile-time false there).
        if (com.feishin.mobile.BuildConfig.DEBUG) {
            runCatching {
                android.os.StrictMode.setThreadPolicy(
                    android.os.StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .detectDiskWrites()
                        .detectNetwork()
                        .penaltyLog()
                        .build(),
                )
            }
        }
        // Absorb non-fatal background failures so one failed call cannot take
        // the whole app down (main-thread crashes still surface).
        runCatching { com.feishin.mobile.core.common.CrashGuard.install() }
        // Media3 posts its playback notification on this channel; pre-create it
        // with our branding so it reads "Vusic" instead of a generic name.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val mgr = getSystemService(NotificationManager::class.java) ?: return@runCatching
                if (mgr.getNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID) == null) {
                    mgr.createNotificationChannel(
                        NotificationChannel(
                            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                            "Vusic",
                            NotificationManager.IMPORTANCE_LOW,
                        ),
                    )
                }
            }
            // Download progress channel.
            runCatching { DownloadNotifications.ensureChannel(this) }
        }
        // Warm the network stack off the main thread: DynamicTrustManager init
        // reads the system CA store and sslContext() runs TLS init, both disk
        // reads that would otherwise happen on first injection during the first
        // composition (StrictMode-clean startup). Hilt caches singletons, so
        // later main-thread gets are memory-only.
        val warmupScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        warmupScope.launch {
            runCatching {
                val entry = EntryPointAccessors.fromApplication(this@FeishinApp, WarmupEntry::class.java)
                // Cache first: PlayerManager (needed at first composition)
                // demands it before any network call needs OkHttp.
                entry.cache()
                entry.okHttp()
            }
        }
    }
}
