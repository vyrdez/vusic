package com.feishin.mobile.core.network

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.model.SubsonicEnvelope
import com.feishin.mobile.data.model.SubsonicResponse
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure Subsonic/OpenSubsonic auth + URL helpers.
 *
 * Everything here is JVM-testable (no Android dependency): token auth
 * (u, t = md5(password + salt), s, v, c, f=json), server URLs with path
 * prefixes, and ping/envelope parsing with user-facing error messages.
 */
object SubsonicAuth {
    const val API_VERSION = "1.16.1"
    const val CLIENT_NAME = "FeishinMobile"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Fresh random salt for every request. Hex without dashes. */
    fun newSalt(): String = UUID.randomUUID().toString().replace("-", "")

    /**
     * Stable salt derived from [key] (e.g. a cover-art id). Lets cacheable URLs
     * stay identical across requests while remaining unique per key.
     */
    fun stableSalt(key: String): String = md5Hex("feishin:$key")

    /** Token for a request: md5 Hex of (password + salt). The plain password is never sent. */
    fun token(password: String, salt: String): String = md5Hex(password + salt)

    /** Full auth query for one request (fresh salt each call). */
    fun authQuery(username: String, password: String): Map<String, String> {
        val salt = newSalt()
        return mapOf(
            "u" to username,
            "t" to token(password, salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT_NAME,
            "f" to "json",
        )
    }

    /**
     * Normalizes a user-typed server URL: adds https:// when no scheme is
     * present and strips trailing slashes. Keeps any path prefix.
     */
    fun normalizeServerUrl(raw: String): String {
        var s = raw.trim()
        require(s.isNotEmpty()) { "Server URL is empty" }
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s.trimEnd('/')
    }

    /**
     * Builds an absolute URL for a Subsonic endpoint (e.g. "rest/ping.view"),
     * preserving a server path prefix (e.g. https://host/navidrome) and
     * URL-encoding every query parameter via HttpUrl.Builder.
     */
    fun buildUrl(serverUrl: String, endpoint: String, query: Map<String, String>): String {
        val base = normalizeServerUrl(serverUrl).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL: $serverUrl")
        val builder = base.newBuilder()
        endpoint.trim('/').split('/').forEach { segment ->
            if (segment.isNotEmpty()) builder.addPathSegment(segment)
        }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    /** Parses a raw `f=json` response body into its envelope, mapping failures. */
    fun parseEnvelope(body: String): Result<SubsonicResponse> {
        return try {
            Result.Ok(json.decodeFromString(SubsonicEnvelope.serializer(), body).response)
        } catch (e: SerializationException) {
            Result.Err("Server returned an unexpected response (is this a Subsonic/Navidrome server?)", e)
        } catch (e: IllegalArgumentException) {
            Result.Err("Server returned an unexpected response (is this a Subsonic/Navidrome server?)", e)
        }
    }

    /**
     * Maps a Subsonic `error` (or transport failure) to a clear user-facing message.
     * Covers wrong password, wrong URL, non-Subsonic hosts, timeouts, no network.
     */
    fun userMessage(code: Int?, serverText: String?, cause: Throwable? = null): String {
        if (code == null) {
            val msg = cause?.message.orEmpty()
            return when {
                cause is java.net.UnknownHostException -> "Server not found. Check the URL and your connection."
                cause is java.net.SocketTimeoutException -> "Connection timed out. Check the URL and try again."
                cause is javax.net.ssl.SSLHandshakeException ->
                    "TLS certificate not trusted. If the server uses a self-signed certificate, enable it on the login screen."
                cause is java.io.IOException -> "Network error (${msg.ifBlank { "no connection" }}). Check the URL and your connection."
                msg.isNotBlank() -> msg
                !serverText.isNullOrBlank() -> serverText
                else -> "Connection failed."
            }
        }
        return when (code) {
            40 -> "Wrong username or password."
            41 -> "Server requires a different auth method (token auth not supported)."
            10 -> "Server rejected the request (missing parameter). Try updating Navidrome."
            0 -> serverText?.ifBlank { null } ?: "Server error."
            else -> serverText?.ifBlank { null } ?: "Server error (code $code)."
        }
    }

    fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
