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

data class LoginUi(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    val settings: SettingsStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    /** Test the server and connect on success. Persists nothing on failure. */
    fun testAndConnect(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = LoginUi(busy = true)
            _ui.value = when (val r = auth.connect(serverUrl, username, password)) {
                is Result.Ok -> LoginUi()
                is Result.Err -> LoginUi(message = r.message)
            }
        }
    }

    /** Connection test only; stored credentials are left untouched. */
    fun testOnly(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = LoginUi(busy = true)
            _ui.value = when (val r = auth.probe(serverUrl, username, password)) {
                is Result.Ok -> LoginUi(message = "Connection OK")
                is Result.Err -> LoginUi(message = "Failed: ${r.message}")
            }
        }
    }

    fun setAllowSelfSigned(allow: Boolean) {
        viewModelScope.launch { settings.setAllowSelfSigned(allow) }
    }
}
