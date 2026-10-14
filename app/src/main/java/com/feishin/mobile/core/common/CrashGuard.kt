package com.feishin.mobile.core.common

import android.os.Looper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Crash-safe layer for *non-fatal* failures.
 *
 * Background work (playback bookkeeping, prefetch, widgets, offline sync) runs
 * in `SupervisorJob` scopes. Without a handler, an uncaught exception in such a
 * child propagates to the thread's default handler and takes the whole app
 * down — for something as routine as one failed HTTP call. This handler
 * absorbs those, logs them (debug builds only) and keeps the app usable.
 *
 * Deliberately NOT applied to the main/UI thread: a real UI exception should
 * still surface during development instead of being silently swallowed.
 */
object CrashGuard {

    /** Install on every app-level scope: `CoroutineScope(SupervisorJob() + CrashGuard.handler)` */
    val handler = CoroutineExceptionHandler { _, throwable ->
        VLog.w(TAG, "non-fatal coroutine failure: ${throwable.javaClass.simpleName}: ${throwable.message}")
    }

    /**
     * Absorbs uncaught exceptions on background threads (installed once from
     * Application). Main-thread crashes keep the platform default so genuine UI
     * bugs remain visible.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (thread == Looper.getMainLooper().thread) {
                previous?.uncaughtException(thread, throwable)
            } else {
                VLog.w(
                    TAG,
                    "uncaught on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable,
                )
            }
        }
    }

    private const val TAG = "CrashGuard"
}
