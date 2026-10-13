package com.feishin.mobile.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.feishin.mobile.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Enqueues the download worker as unique work so transfers survive the app being
 * closed and never run twice. The Wi-Fi-only setting becomes a WorkManager
 * constraint, so the job simply waits rather than failing on mobile data.
 */
@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    /** Starts (or continues) the download queue. */
    suspend fun enqueue() {
        val wifiOnly = runCatching { settings.flow.first().downloadWifiOnly }.getOrDefault(true)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    /** Stops the running transfer; the row is left PAUSED for a later resume. */
    fun cancelCurrent() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
    }

    companion object {
        const val UNIQUE_NAME = "vusic_downloads"
        const val TAG = "downloads"
    }
}
