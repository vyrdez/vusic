package com.feishin.mobile.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.core.common.DownloadStateMachine
import com.feishin.mobile.data.db.DownloadDao
import com.feishin.mobile.data.db.DownloadEntity
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.di.DownloadClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Transfers queued songs to app-private storage.
 *
 * Runs as unique WorkManager work with a foreground notification, so it keeps
 * going with the app closed. Up to [MAX_CONCURRENT] transfers run at once; partial
 * files resume with an HTTP Range request; a low-storage or Wi-Fi-only situation
 * pauses instead of failing.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val covers: com.feishin.mobile.data.db.CachedCoverDao,
    private val settings: SettingsStore,
    private val repo: DownloadRepository,
    @DownloadClient private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    private val notifyLock = Mutex()
    @Volatile private var lastNotified = 0

    override suspend fun doWork(): Result {
        DownloadNotifications.ensureChannel(applicationContext)
        val root = repo.downloadsDir()
        var didWork = false
        var retryable = false

        try {
            while (!isStopped) {
                val batch = dao.nextQueued(MAX_CONCURRENT)
                if (batch.isEmpty()) break
                didWork = true
                // Two at a time, so a slow server still makes parallel progress.
                val outcomes = supervisorScope {
                    batch.map { row -> async(Dispatchers.IO) { transfer(row, root) } }.awaitAll()
                }
                if (outcomes.any { it == Outcome.RETRY }) retryable = true
            }
        } finally {
            if (!isStopped) DownloadNotifications.clear(applicationContext)
        }

        if (isStopped) {
            // Stopped by the user: leave rows PAUSED so they can resume later.
            return Result.success()
        }
        return if (retryable) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        DownloadNotifications.ensureChannel(applicationContext)
        val pending = dao.pending().size
        val n = DownloadNotifications.build(
            applicationContext,
            "Downloading",
            "$pending song(s) queued",
            progress = 0,
            indeterminate = true,
        )
        return foregroundInfo(n)
    }

    private fun foregroundInfo(n: android.app.Notification): ForegroundInfo =
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                DownloadNotifications.NOTIFICATION_ID,
                n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotifications.NOTIFICATION_ID, n)
        }

    private enum class Outcome { DONE, PAUSED, FAILED, RETRY }

    private suspend fun transfer(row: DownloadEntity, root: File): Outcome {
        if (!DownloadStateMachine.canTransition(row.statusEnum, DownloadStatus.DOWNLOADING)) {
            return Outcome.DONE
        }
        dao.setStatus(row.songId, DownloadStatus.DOWNLOADING.name)

        val ext = row.suffix?.takeIf { it.isNotBlank() } ?: "bin"
        val target = File(root, "${row.songId}.$ext")
        val partial = File(repo.partialDir(), "${row.songId}.part")

        // Already finished on disk: just record it.
        if (target.exists() && target.length() > 0) {
            dao.markCompleted(row.songId, target.absolutePath, target.length())
            downloadCover(row)
            return Outcome.DONE
        }

        val quality = repo.downloadQuality()
        val url = repo.signedUrlFor(row, quality)
            ?: return Outcome.FAILED.also {
                dao.setStatus(row.songId, DownloadStatus.FAILED.name, "Not signed in")
            }

        val existing = if (partial.exists()) partial.length() else 0L
        val useRange = DownloadStateMachine.resumeDecision(
            bytesDownloaded = existing,
            expectedBytes = row.expectedBytes,
            rangeSupported = true,
        )
        val startAt = if (useRange) existing else 0L
        if (!useRange && partial.exists()) partial.delete()

        val builder = Request.Builder().url(url)
        if (startAt > 0) builder.header("Range", "bytes=$startAt-")
        val request = builder.build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    dao.setStatus(row.songId, DownloadStatus.FAILED.name, "HTTP ${resp.code}")
                    return if (resp.code >= 500) Outcome.RETRY else Outcome.FAILED
                }
                val appending = resp.code == 206 && startAt > 0
                val total = resp.body?.contentLength()?.let { if (it > 0) it + (if (appending) startAt else 0) else 0L } ?: 0L
                if (total > 0 && !CacheSizePolicy.fits(total, repo.freeBytes())) {
                    dao.setStatus(row.songId, DownloadStatus.PAUSED.name, "Not enough storage")
                    return Outcome.PAUSED
                }
                // Hard floor: below 1 GB free, nothing new is written (downloads
                // are never counted against the cache, but they must not fill the
                // device either).
                if (total > 0 && repo.freeBytes() < com.feishin.mobile.core.common.StorageLimits.LOW_SPACE_BYTES) {
                    dao.setStatus(row.songId, DownloadStatus.PAUSED.name, "Low storage")
                    return Outcome.PAUSED
                }
                if (total > 0 && repo.overDownloadCap(total - startAt)) {
                    dao.setStatus(row.songId, DownloadStatus.PAUSED.name, "Storage limit reached")
                    return Outcome.PAUSED
                }
                val expected = if (total > 0) total else row.expectedBytes
                var written = if (appending) startAt else 0L
                partial.parentFile?.mkdirs()
                if (!appending && partial.exists()) partial.delete()
                resp.body?.byteStream()?.use { input ->
                    partial.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped) throw IOException("stopped")
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            written += read
                            publishProgress(row.songId, row.title, written, expected, partial)
                        }
                    }
                }
                if (expected > 0 && written < expected) {
                    // Server cut the stream short: retry with Range later.
                    dao.setProgress(row.songId, written, DownloadStateMachine.progress(written, expected), partial.absolutePath, DownloadStatus.QUEUED.name)
                    return Outcome.RETRY
                }
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                if (target.length() <= 0) {
                    dao.setStatus(row.songId, DownloadStatus.FAILED.name, "Empty file")
                    return Outcome.FAILED
                }
                dao.markCompleted(row.songId, target.absolutePath, target.length())
                downloadCover(row)
                Outcome.DONE
            }
        } catch (e: IOException) {
            if (isStopped) {
                dao.setStatus(row.songId, DownloadStatus.PAUSED.name)
                Outcome.PAUSED
            } else {
                android.util.Log.w("DownloadWorker", "transfer failed for ${row.songId} url=$url", e)
                dao.setStatus(row.songId, DownloadStatus.FAILED.name, "Network error: ${e.message ?: e.javaClass.simpleName}")
                Outcome.RETRY
            }
        } catch (e: Exception) {
            dao.setStatus(row.songId, DownloadStatus.FAILED.name, e.message ?: "Download failed")
            Outcome.FAILED
        }
    }

    /** Artwork must live on disk too, so Now Playing and notifications work offline. */
    private suspend fun downloadCover(row: DownloadEntity) {
        val coverId = row.coverArt ?: return
        val existing = covers.path(coverId)
        if (existing != null && File(existing).exists()) return
        val url = repo.signedCoverUrl(coverId) ?: return
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val f = File(repo.coversDir(), "$coverId.jpg")
                resp.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } }
                if (f.length() > 0) repo.storeCover(coverId, f)
            }
        }
    }

    private suspend fun publishProgress(songId: String, title: String, bytes: Long, expected: Long, partial: File) {
        val progress = DownloadStateMachine.progress(bytes, expected)
        dao.setProgress(
            songId, bytes, progress, partial.absolutePath,
            DownloadStatus.DOWNLOADING.name,
        )
        val pct = (progress * 100).toInt()
        if (pct == lastNotified) return
        notifyLock.withLock {
            if (pct == lastNotified) return@withLock
            lastNotified = pct
            val n = DownloadNotifications.build(
                applicationContext,
                "Downloading",
                title,
                progress = pct,
                indeterminate = false,
            )
            runCatching { setForegroundAsync(foregroundInfo(n)) }
        }
    }

    companion object {
        const val MAX_CONCURRENT = 2
    }
}
