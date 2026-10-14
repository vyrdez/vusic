package com.feishin.mobile.core.common

import android.util.Log
import com.feishin.mobile.BuildConfig

/**
 * Debug-only logging. Release builds log nothing at all: `ENABLED` is a
 * compile-time constant and R8 strips these branches, so the message strings
 * never even make it into the release APK.
 *
 * Use for diagnostics (cache hits, prefetch, perf timings). Never pass
 * credentials, tokens, salts or full signed URLs — see
 * [com.feishin.mobile.data.remote.RedactingHttpLogger.redact] for URLs.
 */
object VLog {
    val ENABLED: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (ENABLED) Log.d(tag, message)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        if (ENABLED) Log.w(tag, message, t)
    }
}
