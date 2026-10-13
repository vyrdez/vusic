package com.feishin.mobile.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.feishin.mobile.data.db.PendingActionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Replays scrobbles and stars that were queued while offline. Scheduled with a
 * connected constraint, so it only runs once the network is actually back.
 */
@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: OfflineSyncStore,
    private val dao: PendingActionDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (dao.all().isEmpty()) return Result.success()
        val replayed = runCatching { store.flush() }.getOrDefault(0)
        val remaining = runCatching { dao.all().size }.getOrDefault(0)
        return when {
            remaining == 0 -> Result.success()
            replayed > 0 -> Result.retry()
            // Nothing moved at all: likely server-side; retry later with backoff.
            else -> Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .addTag(TAG)
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }

        const val UNIQUE_NAME = "vusic_offline_sync"
        const val TAG = "offline_sync"
    }
}
