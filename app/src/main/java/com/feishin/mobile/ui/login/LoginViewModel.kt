package com.feishin.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.ConnectionResult
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LoginAction {
    NONE,
    CONNECT,
    TEST_ONLY,
}

sealed interface LoginEvent {
    data class Snackbar(val message: String) : LoginEvent
}

/**
 * Login form state.
 *
 * [banner] drives [ConnectionBanner]; [message]/[isError]/[details] mirror the
 * outcome for backwards compatibility. Nothing here holds typed URL, username,
 * or password: those live in the composable's own state.
 */
data class LoginUi(
    val busy: Boolean = false,
    val activeAction: LoginAction = LoginAction.NONE,
    val message: String? = null,
    val isError: Boolean = false,
    val details: String? = null,
    // Monotonic counter that changes on every failure, so observers can react
    // even when two consecutive attempts produce the identical error message.
    val failureNonce: Int = 0,
    val banner: ConnectionBannerState = ConnectionBannerState.Idle,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    val settings: SettingsStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private var activeJob: Job? = null
    private val connectTimeoutMs = 10_000L

    fun profileForEdit(profileId: String): ServerProfile? =
        auth.profiles.value.firstOrNull { it.id == profileId }

    /** Test the server and connect on success. Persists nothing on failure. */
    fun testAndConnect(
        serverUrl: String,
        username: String,
        password: String,
        backendType: BackendType = BackendType.NAVIDROME,
        serverName: String = "",
        allowSelfSigned: Boolean? = null,
        allowCleartext: Boolean? = null,
        addMode: Boolean = false,
        existingProfileId: String? = null,
        onSuccess: () -> Unit = {},
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val current = settings.latestSettings ?: runCatching { settings.flow.first() }.getOrNull()
            val existingProfile = existingProfileId?.let(::profileForEdit)
            val resolvedSelfSigned = allowSelfSigned ?: existingProfile?.allowSelfSigned ?: false
            val resolvedCleartext = allowCleartext ?: existingProfile?.allowCleartext ?: false

            val normalizedUrl = validateClientInput(
                rawUrl = serverUrl,
                username = username,
                backendType = backendType,
                allowCleartext = resolvedCleartext,
            ) ?: return@launch

            _ui.value = LoginUi(
                busy = true,
                activeAction = LoginAction.CONNECT,
                failureNonce = _ui.value.failureNonce,
                banner = ConnectionBannerState.Testing("Authenticating and verifying server…"),
            )

            val result = withTimeoutOrNull(connectTimeoutMs) {
                if (backendType == BackendType.JELLYFIN) {
                    auth.connectJellyfin(
                        name = serverName,
                        serverUrl = normalizedUrl,
                        username = username,
                        password = password,
                        allowSelfSigned = resolvedSelfSigned,
                        allowCleartext = resolvedCleartext,
                        existingProfileId = existingProfileId,
                    )
                } else {
                    auth.connectSubsonicWithResult(
                        type = backendType,
                        name = serverName,
                        serverUrl = normalizedUrl,
                        username = username,
                        password = password,
                        allowSelfSigned = resolvedSelfSigned,
                        allowCleartext = resolvedCleartext,
                        existingProfileId = existingProfileId,
                    )
                }
            }

            when (result) {
                is Result.Ok -> {
                    val conn = result.value
                    if (!addMode && existingProfileId == null) {
                        if (current?.deviceFilesStandalone == true) {
                            settings.setDeviceFilesStandalone(false)
                        }
                        if (current?.librarySourceFilter == "DEVICE") {
                            settings.setLibrarySourceFilter("ALL")
                            settings.setLibraryViewMode("SERVER")
                        }
                    }
                    val snackbar = if (existingProfileId != null) {
                        "Updated \"${conn.profile.name}\""
                    } else {
                        "Connected to \"${conn.profile.name}\" (${conn.latencyMs} ms)"
                    }
                    _events.trySend(LoginEvent.Snackbar(snackbar))
                    _ui.value = LoginUi(failureNonce = _ui.value.failureNonce)
                    onSuccess()
                }
                is Result.Err -> _ui.value = result.toFailureUi(
                    nonce = _ui.value.failureNonce + 1,
                    backendType = backendType,
                )
                null -> _ui.value = timeoutFailureUi(_ui.value.failureNonce + 1)
            }
        }
    }

    /** Connection test only; stored credentials are left untouched. */
    fun testOnly(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean? = null,
        allowCleartext: Boolean? = null,
        backendType: BackendType = BackendType.NAVIDROME,
        existingProfileId: String? = null,
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val existingProfile = existingProfileId?.let(::profileForEdit)
            val resolvedSelfSigned = allowSelfSigned ?: existingProfile?.allowSelfSigned ?: false
            val resolvedCleartext = allowCleartext ?: existingProfile?.allowCleartext ?: false

            val normalizedUrl = validateClientInput(
                rawUrl = serverUrl,
                username = username,
                backendType = backendType,
                allowCleartext = resolvedCleartext,
            ) ?: return@launch

            val testingText = if (backendType == BackendType.JELLYFIN) {
                "Contacting Jellyfin…"
            } else {
                "Pinging server endpoint…"
            }
            _ui.value = LoginUi(
                busy = true,
                activeAction = LoginAction.TEST_ONLY,
                failureNonce = _ui.value.failureNonce,
                banner = ConnectionBannerState.Testing(testingText),
            )

            val result = withTimeoutOrNull(connectTimeoutMs) {
                if (backendType == BackendType.JELLYFIN) {
                    auth.probeJellyfin(
                        serverUrl = normalizedUrl,
                        username = username,
                        password = password,
                        allowSelfSigned = resolvedSelfSigned,
                        allowCleartext = resolvedCleartext,
                        existingProfileId = existingProfileId,
                    )
                } else {
                    auth.probeSubsonicWithResult(
                        serverUrl = normalizedUrl,
                        username = username,
                        password = password,
                        allowSelfSigned = resolvedSelfSigned,
                        allowCleartext = resolvedCleartext,
                        type = backendType,
                        existingProfileId = existingProfileId,
                    )
                }
            }

            _ui.value = when (result) {
                is Result.Ok -> {
                    val okMessage = formatSuccessBanner(result.value)
                    LoginUi(
                        message = okMessage,
                        failureNonce = _ui.value.failureNonce,
                        banner = ConnectionBannerState.Success(okMessage),
                    )
                }
                is Result.Err -> result.toFailureUi(
                    nonce = _ui.value.failureNonce + 1,
                    backendType = backendType,
                )
                null -> timeoutFailureUi(_ui.value.failureNonce + 1)
            }
        }
    }

    /** Enter offline/device-files standalone mode without signing in to a server. */
    fun useDeviceFilesOnly() {
        viewModelScope.launch {
            settings.setLibraryViewMode("DEVICE")
            settings.setLibrarySourceFilter("DEVICE")
            settings.setShowDeviceFiles(true)
            settings.setDeviceFilesStandalone(true)
            _events.trySend(LoginEvent.Snackbar("Offline mode · Using local device files only"))
        }
    }

    fun notifyComingSoon() {
        _events.trySend(LoginEvent.Snackbar("Google Drive backend support is coming soon"))
    }

    fun notifyIncomplete(backendType: BackendType, editMode: Boolean) {
        val message = if (backendType == BackendType.JELLYFIN || editMode) {
            "Fill in Server URL and Username first"
        } else {
            "Fill in Server URL, Username, and Password first"
        }
        _events.trySend(LoginEvent.Snackbar(message))
    }

    fun setAllowSelfSigned(allow: Boolean) {
        viewModelScope.launch { settings.setAllowSelfSigned(allow) }
    }

    /** Clear transient banner state when the user edits a field or switches backend. */
    fun clearError() {
        if (_ui.value.busy) return
        if (_ui.value.message == null &&
            _ui.value.details == null &&
            _ui.value.banner is ConnectionBannerState.Idle
        ) {
            return
        }
        _ui.value = _ui.value.copy(
            message = null,
            isError = false,
            details = null,
            banner = ConnectionBannerState.Idle,
        )
    }

    /** Dismiss the banner explicitly (also cancels an in-flight probe/connect if still testing). */
    fun dismissBanner() {
        activeJob?.cancel()
        activeJob = null
        _ui.value = LoginUi(failureNonce = _ui.value.failureNonce)
    }

    /** Reset transient status when opening the form in Add/Edit mode. */
    fun resetUi() {
        activeJob?.cancel()
        activeJob = null
        _ui.value = LoginUi()
    }

    private fun validateClientInput(
        rawUrl: String,
        username: String,
        backendType: BackendType,
        allowCleartext: Boolean,
    ): String? {
        val normalized = normalizeAndValidateUrl(rawUrl, backendType)
        if (normalized == null) {
            _ui.value = errorUi(
                message = LoginErrorMapper.INVALID_URL_FORMAT,
                nonce = _ui.value.failureNonce + 1,
            )
            return null
        }
        if (!allowCleartext && normalized.startsWith("http://", ignoreCase = true)) {
            _ui.value = errorUi(
                message = LoginErrorMapper.CLEARTEXT_BLOCKED,
                nonce = _ui.value.failureNonce + 1,
            )
            return null
        }
        if (username.trim().isEmpty()) {
            _ui.value = errorUi(
                message = LoginErrorMapper.EMPTY_USERNAME,
                nonce = _ui.value.failureNonce + 1,
            )
            return null
        }
        return normalized
    }

    private fun errorUi(
        message: String,
        details: String? = null,
        nonce: Int,
    ) = LoginUi(
        message = message,
        isError = true,
        details = details,
        failureNonce = nonce,
        banner = ConnectionBannerState.Error(message = message, details = details),
    )

    private fun timeoutFailureUi(nonce: Int): LoginUi = errorUi(
        message = LoginErrorMapper.TIMEOUT,
        nonce = nonce,
    )

    private fun Result.Err.toFailureUi(nonce: Int, backendType: BackendType): LoginUi {
        val failure = LoginErrorMapper.map(this, backendType)
        return errorUi(
            message = failure.message,
            details = failure.details,
            nonce = nonce,
        )
    }

    companion object {
        fun canTest(
            url: String,
            username: String,
            password: String,
            backendType: BackendType,
            editMode: Boolean,
        ): Boolean = url.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || backendType == BackendType.JELLYFIN || editMode)

        fun canConnect(
            url: String,
            username: String,
            password: String,
            backendType: BackendType,
            editMode: Boolean,
        ): Boolean = canTest(
            url = url,
            username = username,
            password = password,
            backendType = backendType,
            editMode = editMode,
        )

        internal fun formatSuccessBanner(result: ConnectionResult): String =
            if (result.apiInfo.isNotBlank()) {
                "Connection OK · ${result.latencyMs} ms (${result.apiInfo})"
            } else {
                "Connection OK · ${result.latencyMs} ms"
            }

        /**
         * Validates URL syntax client-side before any network call is attempted.
         * Returns `null` on malformed input so callers raise `Invalid server URL format.`
         */
        internal fun normalizeAndValidateUrl(
            rawUrl: String,
            backendType: BackendType,
        ): String? {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return null
            val schemeSep = trimmed.indexOf("://")
            val withScheme = if (schemeSep >= 0) {
                when {
                    trimmed.startsWith("http://", ignoreCase = true) ->
                        "http://" + trimmed.substring(7)
                    trimmed.startsWith("https://", ignoreCase = true) ->
                        "https://" + trimmed.substring(8)
                    else -> return null
                }
            } else {
                "https://$trimmed"
            }
            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            if (parsed.host.isBlank()) return null
            return if (backendType == BackendType.JELLYFIN) {
                runCatching { JellyfinUrl.normalize(withScheme) }.getOrNull()
            } else {
                withScheme.trimEnd('/')
            }
        }
    }
}
