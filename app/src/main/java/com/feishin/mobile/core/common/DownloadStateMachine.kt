package com.feishin.mobile.core.common

import com.feishin.mobile.data.db.DownloadStatus

/**
 * Pure download transfer rules: allowed status transitions and the resume
 * decision, so behaviour is identical after a process kill and is unit-testable.
 */
object DownloadStateMachine {

    private val allowed: Map<DownloadStatus, Set<DownloadStatus>> = mapOf(
        DownloadStatus.QUEUED to setOf(
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.FAILED,
        ),
        DownloadStatus.DOWNLOADING to setOf(
            DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.PAUSED, DownloadStatus.QUEUED,
        ),
        DownloadStatus.PAUSED to setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.FAILED),
        DownloadStatus.FAILED to setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING),
        // A finished download may be re-queued when the file goes missing.
        DownloadStatus.COMPLETED to setOf(DownloadStatus.QUEUED),
    )

    fun canTransition(from: DownloadStatus, to: DownloadStatus): Boolean =
        from == to || allowed[from]?.contains(to) == true

    /** A transfer is only runnable from these states. */
    fun isRunnable(status: DownloadStatus): Boolean =
        status == DownloadStatus.QUEUED || status == DownloadStatus.PAUSED

    /**
     * What to do with a partial file when (re)starting: resume with a Range
     * request only when bytes exist, the server allows ranges, and the partial
     * file is smaller than the expected size.
     */
    fun resumeDecision(
        bytesDownloaded: Long,
        expectedBytes: Long,
        rangeSupported: Boolean,
    ): Boolean =
        bytesDownloaded > 0 &&
            rangeSupported &&
            (expectedBytes <= 0 || bytesDownloaded < expectedBytes)

    /** Status after a transfer is interrupted by the user (pause). */
    fun onPause(): DownloadStatus = DownloadStatus.PAUSED

    /** Status after a user cancel: the row is removed, so it maps to QUEUED-free. */
    fun onCancel(): DownloadStatus = DownloadStatus.PAUSED

    /** Progress fraction, clamped and safe for a zero/unknown size. */
    fun progress(bytes: Long, expected: Long): Float =
        if (expected <= 0) 0f else (bytes.toFloat() / expected).coerceIn(0f, 1f)
}
