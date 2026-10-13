package com.feishin.mobile.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.feishin.mobile.MainActivity
import com.feishin.mobile.R

/** Foreground notification for the download service, with a Cancel action. */
object DownloadNotifications {

    const val CHANNEL_ID = "vusic_downloads"
    const val NOTIFICATION_ID = 0x0D0D

    /** Broadcast the worker listens for to stop the current transfer. */
    const val ACTION_CANCEL = "com.feishin.mobile.action.CANCEL_DOWNLOADS"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Offline download progress"
                    setShowBadge(false)
                },
            )
        }
    }

    fun build(
        ctx: Context,
        title: String,
        detail: String,
        progress: Int,
        indeterminate: Boolean,
    ): Notification {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getBroadcast(
            ctx, 1, Intent(ACTION_CANCEL).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
    }

    fun post(ctx: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    fun clear(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID) }
    }
}
