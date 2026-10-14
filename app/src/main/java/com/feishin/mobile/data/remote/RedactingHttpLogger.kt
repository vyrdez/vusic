package com.feishin.mobile.data.remote

import android.util.Log
import com.feishin.mobile.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Request/response logger for API traffic.
 *
 * Two rules, enforced in one place:
 *  1. Nothing is logged in release builds (R8 minify the call sites too, since
 *     [ENABLED] is a compile-time constant).
 *  2. Credentials never reach the log even in debug: the Subsonic query params
 *     `u` (username), `t` (token) and `s` (salt), plus any `p`/`password`, are
 *     replaced with `***`. Only method, redacted path/query and status remain.
 */
class RedactingHttpLogger : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!ENABLED) return chain.proceed(chain.request())
        val request = chain.request()
        val started = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.d(TAG, "${request.method} ${redact(request.url.toString())} -> ${e.javaClass.simpleName}")
            throw e
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.d(
            TAG,
            "${request.method} ${redact(request.url.toString())} -> ${response.code} (${ms}ms)",
        )
        return response
    }

    companion object {
        private const val TAG = "VusicHttp"

        /** Compile-time: false in release, so the strings vanish from the APK. */
        val ENABLED: Boolean = BuildConfig.DEBUG

        private val SECRET_PARAMS = setOf("u", "t", "s", "p", "password", "token")

        /**
         * Replaces credential query values with `***`, keeping the parameter
         * names so the log still shows which auth scheme was used.
         */
        fun redact(url: String): String {
            val q = url.indexOf('?')
            if (q < 0) return url
            val base = url.substring(0, q)
            val query = url.substring(q + 1)
            val redacted = query.split('&').joinToString("&") { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) pair
                else {
                    val name = pair.substring(0, eq)
                    if (name.lowercase() in SECRET_PARAMS) "$name=***" else pair
                }
            }
            return "$base?$redacted"
        }
    }
}
