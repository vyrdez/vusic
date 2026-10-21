package com.feishin.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Login form state.
 *
 * [message] drives the inline card; [isError] picks the error colours, so a
 * successful "Test connection only" is a plain confirmation, not a red error.
 * Nothing here holds the typed URL/username/password: those live in the
 * composable's own state, so a failed attempt can never reset them.
 */
data class LoginUi(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val details: String? = null,
    // Monotonic counter that changes on every failure, so the screen's
    // LaunchedEffect re-fires (and re-shows the snackbar) even when two
    // consecutive attempts produce the identical error message.
    val failureNonce: Int = 0,
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
                // Navigation is driven by AuthRepository.loginState, not from here.
                is Result.Ok -> LoginUi()
                is Result.Err -> result.toFailureUi(_ui.value.failureNonce + 1)
                null -> LoginUi(
                    message = LoginErrorMapper.TIMEOUT,
                    isError = true,
                    failureNonce = _ui.value.failureNonce + 1,
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
                is Result.Err -> result.toFailureUi(_ui.value.failureNonce + 1)
                null -> LoginUi(
                    message = LoginErrorMapper.TIMEOUT,
                    isError = true,
                    failureNonce = _ui.value.failureNonce + 1,
                )
            }
        }
    }

    fun setAllowSelfSigned(allow: Boolean) {
        viewModelScope.launch { settings.setAllowSelfSigned(allow) }
    }

    /** Clear the error when the user edits a field. */
    fun clearError() {
        if (_ui.value.message == null && _ui.value.details == null) return
        _ui.value = _ui.value.copy(message = null, details = null)
    }

    private fun Result.Err.toFailureUi(nonce: Int): LoginUi {
        val failure = LoginErrorMapper.map(this)
        return LoginUi(
            message = failure.message,
            isError = true,
            details = failure.details,
            failureNonce = nonce,
        )
    }
}
