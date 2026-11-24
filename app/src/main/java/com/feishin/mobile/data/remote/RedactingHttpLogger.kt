package com.feishin.mobile.data.remote

import android.util.Log
import com.feishin.mobile.BuildConfig
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Request/response logger for API traffic.
 *
 * Two rules, enforced in one place:
 *  1. Nothing is logged in release builds (R8 minifies the call sites too, since
 *     [ENABLED] is a compile-time constant).
 *  2. Credentials never reach the log even in debug: query/form params (`u`, `t`,
 *     `s`, `p`, `password`, `token`, `access_token`, `refresh_token`) are replaced
 *     with `***`, and auth headers (`Authorization`, `X-Emby-Token`, etc.) are
 *     masked as `<redacted>`. Only method, redacted path/query and status remain.
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
        const val REDACTED_HEADER_VALUE = "<redacted>"

        /** Compile-time: false in release, so the strings vanish from the APK. */
        val ENABLED: Boolean = BuildConfig.DEBUG

        private val SECRET_PARAMS = setOf(
            "u",
            "t",
            "s",
            "p",
            "password",
            "token",
            "access_token",
            "refresh_token",
            "apikey",
            "api_key",
        )

        private val SECRET_HEADERS = setOf(
            "authorization",
            "x-emby-token",
            "x-mediabrowser-token",
            "cookie",
            "set-cookie",
        )

        /**
         * Replaces credential query values with `***`, keeping the parameter
         * names so the log still shows which auth scheme was used.
         */
        fun redact(url: String): String {
            val q = url.indexOf('?')
            if (q < 0) return url
            val base = url.substring(0, q)
            val query = url.substring(q + 1)
            return "$base?${redactForm(query)}"
        }

        /** Redacts secret key=value pairs in a URL query or `application/x-www-form-urlencoded` body. */
        fun redactForm(encoded: String): String {
            if (encoded.isEmpty()) return encoded
            return encoded.split('&').joinToString("&") { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    pair
                } else {
                    val name = pair.substring(0, eq)
                    if (name.lowercase() in SECRET_PARAMS) "$name=***" else pair
                }
            }
        }

        /** Masks sensitive header values (`Authorization`, `X-Emby-Token`, etc.) as `<redacted>`. */
        fun redactHeader(name: String, value: String): String =
            if (name.lowercase() in SECRET_HEADERS) REDACTED_HEADER_VALUE else value

        /** Formats a header line with sensitive values masked. */
        fun redactHeaderLine(name: String, value: String): String =
            "$name: ${redactHeader(name, value)}"

        /** Returns a redacted copy of [headers] suitable for debug logging. */
        fun redactHeaders(headers: Headers): Map<String, String> = buildMap {
            for (i in 0 until headers.size) {
                val name = headers.name(i)
                put(name, redactHeader(name, headers.value(i)))
            }
        }
    }
}
