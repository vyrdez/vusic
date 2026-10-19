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

    /**
     * Re-evaluates the queued work against the CURRENT setting.
     *
     * Called when the "download over mobile data" switch is flipped. A job
     * enqueued under the old constraint keeps that constraint until it is
     * replaced, so without this a download that was waiting for Wi-Fi would
     * keep waiting after the user allowed mobile data - and a download running
     * on mobile data would keep running after the user forbade it.
     *
     * The work is REPLACED, not restarted from scratch: the progress lives in
     * the database, and the worker resumes a partial file with an HTTP Range
     * request, so nothing is lost. A queued row simply waits for the new
     * constraint, and a running transfer is interrupted and resumes later.
     */
    suspend fun reEvaluate() {
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
            val wm = WorkManager.getInstance(context)
            // Cancel first: a running job holds the old constraint, and
            // replacing it while it runs would leave two workers on the queue.
            wm.cancelUniqueWork(UNIQUE_NAME)
            wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
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
