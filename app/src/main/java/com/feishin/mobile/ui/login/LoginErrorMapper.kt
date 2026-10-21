package com.feishin.mobile.ui.login

import com.feishin.mobile.core.common.Result
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * One failed login attempt, phrased for the login form.
 *
 * [message] is the specific, user-facing line shown in the inline error card.
 * [details] is the raw technical text, only offered for errors we could not
 * classify, behind a "Details" action. It never contains the password: the
 * password is not part of any error string produced by [com.feishin.mobile.core.network.SubsonicAuth]
 * or OkHttp.
 */
internal data class LoginFailure(
    val message: String,
    val details: String? = null,
)

/**
 * Pure mapping from a connect/probe failure to a readable explanation.
 *
 * Kept out of the ViewModel so it is unit-testable without Android or a
 * DataStore, and so both "Test & Connect" and "Test connection only" share
 * exactly one set of messages.
 */
internal object LoginErrorMapper {
    const val BAD_URL =
        "That address doesn't look right. It should look like https://host:4533/music"
    const val EMPTY_USERNAME = "Please enter a username."
    const val WRONG_CREDENTIALS = "Wrong username or password."
    const val UNREACHABLE =
        "Couldn't reach the server. Check the address and your connection."
    const val CERTIFICATE =
        "The server's certificate isn't trusted. If this is your own server, " +
            "turn on 'Allow self-signed certificate'."
    const val NOT_NAVIDROME =
        "That address doesn't look like a Navidrome server. Check the server URL."
    const val TIMEOUT = "The server took too long to answer. Try again."

    /** Marker produced by [com.feishin.mobile.core.network.SubsonicAuth.parseEnvelope]. */
    private const val UNEXPECTED_RESPONSE = "unexpected response"

    fun map(err: Result.Err): LoginFailure {
        val cause = err.cause
        val raw = err.message

        // Transport-level failures first: their message text is not meaningful.
        when (cause) {
            is UnknownHostException -> return LoginFailure(UNREACHABLE)
            is SocketTimeoutException -> return LoginFailure(UNREACHABLE)
            is SSLException -> return LoginFailure(CERTIFICATE)
            is IOException -> return LoginFailure(UNREACHABLE)
        }

        val text = raw.trim()
        return when {
            text.equals("Invalid server URL.", ignoreCase = true) -> LoginFailure(BAD_URL)
            text.equals("Username is empty.", ignoreCase = true) -> LoginFailure(EMPTY_USERNAME)
            text.contains(UNEXPECTED_RESPONSE, ignoreCase = true) ||
                text.contains("Subsonic/Navidrome", ignoreCase = true) ||
                text.contains("MalformedJson", ignoreCase = true) ||
                text.contains("JsonDecodingException", ignoreCase = true) ->
                LoginFailure(NOT_NAVIDROME)

            text.contains("certificate", ignoreCase = true) ||
                text.contains("TLS", ignoreCase = true) ||
                text.contains("SSL", ignoreCase = true) -> LoginFailure(CERTIFICATE)

            isWrongCredentials(text) -> LoginFailure(WRONG_CREDENTIALS)
            text.contains("timed out", ignoreCase = true) -> LoginFailure(UNREACHABLE)
            text.contains("Network error", ignoreCase = true) -> LoginFailure(UNREACHABLE)
            text.contains("Server not found", ignoreCase = true) -> LoginFailure(UNREACHABLE)
            text.contains("Connection timed out", ignoreCase = true) -> LoginFailure(UNREACHABLE)

            text.isBlank() -> LoginFailure(UNREACHABLE)
            else -> LoginFailure("Couldn't connect to the server.", details = redact(text))
        }
    }

    /** Password-shaped query params, in case a server echoes the request back. */
    private val PASSWORD_PARAM = Regex("(?i)\\b(password|passwd|pass|p)=([^&\\s]+)")

    /** Defence in depth: the details line must never carry the password. */
    internal fun redact(text: String): String =
        text.replace(PASSWORD_PARAM) { "${it.groupValues[1]}=***" }

    /**
     * Subsonic answers code 40 (and Navidrome a 401 body) for a bad password.
     * [com.feishin.mobile.core.network.SubsonicAuth.userMessage] normalises both
     * to "Wrong username or password.", so match that plus the raw codes.
     */
    private fun isWrongCredentials(text: String): Boolean =
        text.startsWith("Wrong password", ignoreCase = true) ||
            text.contains("Wrong username or password", ignoreCase = true) ||
            UNAUTHORIZED.containsMatchIn(text) ||
            SUB_CODE_40.containsMatchIn(text) ||
            text.contains("Unauthorized", ignoreCase = true) ||
            text.contains("Incorrect username", ignoreCase = true)

    private val UNAUTHORIZED = Regex("\\b401\\b")
    private val SUB_CODE_40 = Regex("(?i)\\bcode\\s*40\\b")
}
