@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.session.DefaultMediaNotificationProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FeishinApp : Application(), ImageLoaderFactory {
    @Inject lateinit var imageLoader: ImageLoader
    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
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
        }
    }
}
