package com.feishin.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LoginUi(
    val busy: Boolean = false,
    val message: String? = null,
    val errorTitle: String? = null,
    val errorDetails: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    val settings: SettingsStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    private val connectTimeoutMs = 10_000L

    /** Test the server and connect on success. Persists nothing on failure. */
    fun testAndConnect(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = LoginUi(busy = true)
            val result = withTimeoutOrNull(connectTimeoutMs) {
                auth.connect(serverUrl, username, password)
            }
            _ui.value = when (result) {
                is Result.Ok -> LoginUi()
                is Result.Err -> LoginUi(
                    busy = false,
                    message = mapConnectError(result),
                    errorTitle = null,
                    errorDetails = null,
                )
                null -> LoginUi(
                    busy = false,
                    message = "Couldn't reach the server. Check the address and your connection.",
                )
            }
        }
    }

    /** Connection test only; stored credentials are left untouched. */
    fun testOnly(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = LoginUi(busy = true)
            val result = withTimeoutOrNull(connectTimeoutMs) {
                auth.probe(serverUrl, username, password)
            }
            _ui.value = when (result) {
                is Result.Ok -> LoginUi(message = "Connection OK")
                is Result.Err -> LoginUi(message = mapProbeError(result))
                null -> LoginUi(message = "Couldn't reach the server. Check the address and your connection.")
            }
        }
    }

    fun setAllowSelfSigned(allow: Boolean) {
        viewModelScope.launch { settings.setAllowSelfSigned(allow) }
    }

    /** Clear the error when the user edits a field. */
    fun clearError() {
        _ui.value = _ui.value.copy(message = null, errorTitle = null, errorDetails = null)
    }

    /** Map a connect error to a specific, friendly user message (no secrets). */
    private fun mapConnectError(err: Result.Err): String {
        return when {
            err.message == "Invalid server URL." -> {
                "That address doesn't look right. It should look like https://host:4533/music"
            }
            err.message == "Username is empty." -> "Please enter a username."
            err.cause is java.net.UnknownHostException ->
                "Couldn't reach the server. Check the address and your connection."
            err.cause is java.net.SocketTimeoutException ->
                "Couldn't reach the server. Check the address and your connection."
            err.cause is javax.net.ssl.SSLHandshakeException ->
                "The server's certificate isn't trusted. " +
                    "If this is your own server, turn on 'Allow self-signed certificate'."
            err.message.contains("40", ignoreCase = true) ||
                err.message.contains("Wrong username or password", ignoreCase = true) ->
                "Wrong username or password."
            err.message.contains("certificate", ignoreCase = true) ||
                err.message.contains("TLS", ignoreCase = true) ->
                "The server's certificate isn't trusted. " +
                    "If this is your own server, turn on 'Allow self-signed certificate'."
            else -> {
                err.message ?: "Connection failed."
            }
        }
    }

    /** Map a probe error to a specific, friendly user message (no secrets). */
    private fun mapProbeError(err: Result.Err): String {
        return when {
            err.message == "Invalid server URL." -> {
                "That address doesn't look right. It should look like https://host:4533/music"
            }
            err.message == "Username is empty." -> "Please enter a username."
            err.cause is java.net.UnknownHostException ->
                "Couldn't reach the server. Check the address and your connection."
            err.cause is java.net.SocketTimeoutException ->
                "Couldn't reach the server. Check the address and your connection."
            err.cause is javax.net.ssl.SSLHandshakeException ->
                "The server's certificate isn't trusted. " +
                    "If this is your own server, turn on 'Allow self-signed certificate'."
            err.message.contains("40", ignoreCase = true) ||
                err.message.contains("Wrong username or password", ignoreCase = true) ->
                "Wrong username or password."
            else -> "Connection failed: ${err.message ?: "unknown error"}"
        }
    }
}
