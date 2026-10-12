package com.feishin.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.data.repo.MusicRepository
import com.feishin.mobile.data.store.CredentialsStore
import com.feishin.mobile.util.Result
import com.feishin.mobile.util.normalizeServerUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUi(val loading: Boolean = false, val error: String? = null, val testing: Boolean = false)

@HiltViewModel
class AuthViewModel @Inject constructor(
    val creds: CredentialsStore,
    private val repo: MusicRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    fun login(serverUrl: String, username: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _ui.value = LoginUi(loading = true)
            val normalized = try {
                normalizeServerUrl(serverUrl)
            } catch (e: Exception) {
                _ui.value = LoginUi(error = "Invalid server URL")
                return@launch
            }
            // Save first so the interceptor can sign the test request, then ping.
            creds.save(normalized.removeSuffix("/rest/"), username.trim(), password)
            when (val r = repo.ping()) {
                is Result.Ok -> {
                    _ui.value = LoginUi()
                    onDone()
                }
                is Result.Err -> {
                    creds.clear()
                    _ui.value = LoginUi(error = r.message)
                }
            }
        }
    }

    fun test(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = LoginUi(testing = true)
            val normalized = try {
                normalizeServerUrl(serverUrl)
            } catch (_: Exception) {
                _ui.value = LoginUi(error = "Invalid server URL")
                return@launch
            }
            val prev = creds.state.value
            creds.save(normalized.removeSuffix("/rest/"), username.trim(), password)
            when (val r = repo.ping()) {
                is Result.Ok -> _ui.value = LoginUi(error = "Connection OK")
                is Result.Err -> {
                    if (prev == null) creds.clear() else creds.save(prev.serverUrl, prev.username, prev.password)
                    _ui.value = LoginUi(error = "Failed: ${r.message}")
                }
            }
        }
    }

    fun logout() = creds.clear()
}
