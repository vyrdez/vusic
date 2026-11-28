package com.feishin.mobile.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.ConnectionResult
import com.feishin.mobile.data.auth.DriveAuthError
import com.feishin.mobile.data.auth.DriveScanCancellationRegistry
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.LocalFavoriteDao
import com.feishin.mobile.data.db.LocalPlayHistoryDao
import com.feishin.mobile.data.remote.drive.DriveAuthCoordinator
import com.feishin.mobile.data.remote.drive.DriveAuthResult
import com.feishin.mobile.data.remote.drive.DriveFileDto
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.ui.settings.ScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LoginAction {
    NONE,
    CONNECT,
    TEST_ONLY,
}

sealed interface LoginEvent {
    data class Snackbar(val message: String) : LoginEvent
    data class LaunchDriveConsent(val intentSender: IntentSender, val email: String) : LoginEvent
}

/**
 * Ephemeral in-memory Google Drive sign-in and folder-selection state.
 * Never persisted to SavedStateHandle or rememberSaveable; cleared in [onCleared].
 */
data class DriveFormState(
    val accountEmail: String = "",
    val accessToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val driveRootFolderId: String? = null,
    val driveRootFolderName: String? = null,
    val scanAllAudio: Boolean = false,
)

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

private class LoginDriveDeps(
    val driveAuthCoordinator: DriveAuthCoordinator? = null,
    val backendFactory: BackendClientFactory? = null,
    val probeRegistry: TransientProbeRegistry? = null,
    val scanCoordinator: ScanCoordinator? = null,
    val driveTrackDao: DriveTrackDao? = null,
    val localFavoriteDao: LocalFavoriteDao? = null,
    val localPlayHistoryDao: LocalPlayHistoryDao? = null,
    val cachedCoverDao: CachedCoverDao? = null,
    val driveScanCancellationRegistry: DriveScanCancellationRegistry? = null,
    val libraryRefresh: LibraryRefresh? = null,
)

@HiltViewModel
class LoginViewModel private constructor(
    private val auth: AuthRepository,
    val settings: SettingsStore,
    private val driveDeps: LoginDriveDeps,
) : ViewModel() {

    @Inject
    constructor(
        auth: AuthRepository,
        settings: SettingsStore,
        driveAuthCoordinator: DriveAuthCoordinator,
        backendFactory: BackendClientFactory,
        probeRegistry: TransientProbeRegistry,
        scanCoordinator: ScanCoordinator,
        driveTrackDao: DriveTrackDao,
        localFavoriteDao: LocalFavoriteDao,
        localPlayHistoryDao: LocalPlayHistoryDao,
        cachedCoverDao: CachedCoverDao,
        driveScanCancellationRegistry: DriveScanCancellationRegistry,
        libraryRefresh: LibraryRefresh,
    ) : this(
        auth = auth,
        settings = settings,
        driveDeps = LoginDriveDeps(
            driveAuthCoordinator = driveAuthCoordinator,
            backendFactory = backendFactory,
            probeRegistry = probeRegistry,
            scanCoordinator = scanCoordinator,
            driveTrackDao = driveTrackDao,
            localFavoriteDao = localFavoriteDao,
            localPlayHistoryDao = localPlayHistoryDao,
            cachedCoverDao = cachedCoverDao,
            driveScanCancellationRegistry = driveScanCancellationRegistry,
            libraryRefresh = libraryRefresh,
        ),
    )

    internal constructor(
        auth: AuthRepository,
        settings: SettingsStore,
    ) : this(
        auth = auth,
        settings = settings,
        driveDeps = LoginDriveDeps(),
    )

    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    private val _driveState = MutableStateFlow(DriveFormState())
    val driveState: StateFlow<DriveFormState> = _driveState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private var activeJob: Job? = null
    private val connectTimeoutMs = 10_000L

    override fun onCleared() {
        activeJob?.cancel()
        activeJob = null
        _driveState.value = DriveFormState()
        super.onCleared()
    }

    fun profileForEdit(profileId: String): ServerProfile? =
        auth.profiles.value.firstOrNull { it.id == profileId }

    fun initDriveEditState(profile: ServerProfile) {
        if (profile.type != BackendType.GOOGLE_DRIVE) return
        val creds = profile.credentials as? ProfileCredentials.GoogleDrive
        val rootId = profile.driveRootFolderId?.trim()?.takeIf { it.isNotBlank() }
        val scanAll = rootId == null
        val rootName = profile.driveRootFolderName?.trim()?.takeIf { it.isNotBlank() }
            ?: if (scanAll) ALL_AUDIO_FILES_LABEL else null
        _driveState.value = DriveFormState(
            accountEmail = creds?.accountEmail.orEmpty(),
            accessToken = "",
            expiresAtEpochMs = creds?.expiresAtEpochMs ?: 0L,
            driveRootFolderId = rootId,
            driveRootFolderName = rootName,
            scanAllAudio = scanAll,
        )
    }

    fun signInWithGoogle(activityContext: Context) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _ui.value = LoginUi(
                busy = true,
                activeAction = LoginAction.NONE,
                failureNonce = _ui.value.failureNonce,
                banner = ConnectionBannerState.Testing("Waiting for Google sign-in…"),
            )
            val coordinator = driveDeps.driveAuthCoordinator
            val result = if (coordinator != null) {
                coordinator.signInAndAuthorize(activityContext)
            } else {
                DriveAuthResult.Unavailable("Google Sign-In is unavailable.")
            }
            handleDriveAuthResult(result, fromResolution = false)
        }
    }

    private var legacyConsentPending = false

    fun onDriveResolutionResult(
        resultCode: Int,
        data: Intent?,
        fallbackEmail: String,
    ) {
        val coordinator = driveDeps.driveAuthCoordinator
        if (coordinator == null) {
            handleDriveAuthResult(DriveAuthResult.Unavailable("Google Sign-In is unavailable."), fromResolution = true)
            return
        }
        viewModelScope.launch {
            if (legacyConsentPending) {
                legacyConsentPending = false
                val retried = if (resultCode == Activity.RESULT_OK) {
                    coordinator.authorizeLegacy(fallbackEmail)
                } else {
                    DriveAuthResult.Cancelled
                }
                handleDriveAuthResult(
                    if (retried is DriveAuthResult.ResolutionRequired) DriveAuthResult.Cancelled else retried,
                    fromResolution = true,
                )
                return@launch
            }
            val primary = if (resultCode == Activity.RESULT_OK && data != null) {
                coordinator.resolveAuthorizationIntent(data, fallbackEmail)
            } else {
                DriveAuthResult.Cancelled
            }
            if (primary is DriveAuthResult.Authorized) {
                val resolved = if (primary.email.isBlank()) {
                    val aboutEmail = coordinator.fetchAboutEmail(primary.accessToken)
                    if (aboutEmail != null) primary.copy(email = aboutEmail) else primary
                } else {
                    primary
                }
                if (resolved.email.isNotBlank()) {
                    handleDriveAuthResult(resolved, fromResolution = true)
                    return@launch
                }
            }
            // Older Play Services can fail the Authorization UI (reauth INTERNAL_ERROR); retry
            // with the legacy token API for the account the user just picked.
            val legacy = coordinator.authorizeLegacy(fallbackEmail)
            when (legacy) {
                is DriveAuthResult.Authorized -> handleDriveAuthResult(legacy, fromResolution = true)
                is DriveAuthResult.ResolutionRequired -> {
                    legacyConsentPending = true
                    handleDriveAuthResult(legacy, fromResolution = false)
                }
                else -> handleDriveAuthResult(primary, fromResolution = true)
            }
        }
    }

    internal fun handleDriveAuthResult(
        result: DriveAuthResult,
        fromResolution: Boolean = false,
    ) {
        when (result) {
            is DriveAuthResult.Authorized -> {
                val prev = _driveState.value
                val switchedAccount = prev.accountEmail.isNotBlank() &&
                    !prev.accountEmail.equals(result.email, ignoreCase = true)
                _driveState.value = if (switchedAccount) {
                    DriveFormState(
                        accountEmail = result.email,
                        accessToken = result.accessToken,
                        expiresAtEpochMs = result.expiresAtEpochMs,
                    )
                } else {
                    prev.copy(
                        accountEmail = result.email,
                        accessToken = result.accessToken,
                        expiresAtEpochMs = result.expiresAtEpochMs,
                    )
                }
                _ui.value = LoginUi(failureNonce = _ui.value.failureNonce)
            }

            is DriveAuthResult.ResolutionRequired -> {
                _ui.value = LoginUi(
                    busy = true,
                    activeAction = LoginAction.NONE,
                    failureNonce = _ui.value.failureNonce,
                    banner = ConnectionBannerState.Testing("Waiting for Google sign-in…"),
                )
                _events.trySend(
                    LoginEvent.LaunchDriveConsent(
                        intentSender = result.pendingIntent.intentSender,
                        email = result.email,
                    ),
                )
            }

            DriveAuthResult.Cancelled -> {
                _ui.value = LoginUi(
                    failureNonce = _ui.value.failureNonce,
                    banner = ConnectionBannerState.Info("Google sign-in cancelled"),
                )
            }

            is DriveAuthResult.Unavailable -> {
                _ui.value = errorUi(
                    message = DRIVE_UNAVAILABLE_ERROR,
                    details = result.reason.takeIf { it.isNotBlank() && it != DRIVE_UNAVAILABLE_ERROR },
                    nonce = _ui.value.failureNonce + 1,
                )
            }

            is DriveAuthResult.Error -> {
                val isConsentError = fromResolution || isConsentDeniedMessage(result.message)
                val message = if (isConsentError) {
                    DRIVE_CONSENT_DENIED_ERROR
                } else {
                    result.message.ifBlank { DRIVE_UNAVAILABLE_ERROR }
                }
                _ui.value = errorUi(
                    message = message,
                    details = result.message.takeIf { it.isNotBlank() && it != message },
                    nonce = _ui.value.failureNonce + 1,
                )
            }
        }
    }

    fun setDriveFolderSelection(folderId: String?, folderName: String?) {
        clearError()
        val cleanedId = folderId?.trim()?.takeIf { it.isNotBlank() }
        val cleanedName = folderName?.trim()?.takeIf { it.isNotBlank() }
        _driveState.value = _driveState.value.copy(
            driveRootFolderId = cleanedId,
            driveRootFolderName = if (cleanedId == null) ALL_AUDIO_FILES_LABEL else cleanedName,
            scanAllAudio = cleanedId == null,
        )
    }

    fun setDriveScanAllAudio(enabled: Boolean) {
        clearError()
        val prev = _driveState.value
        _driveState.value = if (enabled) {
            prev.copy(
                scanAllAudio = true,
                driveRootFolderId = null,
                driveRootFolderName = ALL_AUDIO_FILES_LABEL,
            )
        } else {
            prev.copy(
                scanAllAudio = false,
                driveRootFolderName = if (prev.driveRootFolderId == null) null else prev.driveRootFolderName,
            )
        }
    }

    suspend fun listDriveSubfolders(
        parentId: String,
        existingProfileId: String? = null,
    ): Result<List<DriveFileDto>> {
        val state = _driveState.value
        val factory = driveDeps.backendFactory
            ?: return Result.Err("Google Drive client is unavailable.")

        if (state.accessToken.isNotBlank()) {
            val registry = driveDeps.probeRegistry
                ?: return Result.Err("Google Drive probe registry is unavailable.")
            val candidate = ServerProfile(
                id = "__probe__",
                name = state.accountEmail.ifBlank { "Google Drive" },
                type = BackendType.GOOGLE_DRIVE,
                url = BackendClientFactory.GOOGLE_DRIVE_BASE_URL,
                userId = state.accountEmail,
                credentials = ProfileCredentials.GoogleDrive(
                    accountEmail = state.accountEmail,
                    accessToken = state.accessToken,
                    expiresAtEpochMs = state.expiresAtEpochMs,
                ),
                allowSelfSigned = false,
                allowCleartext = false,
            )
            val probeId = registry.register(candidate)
            val probeProfile = registry.get(probeId) ?: candidate.copy(id = probeId)
            return try {
                val api = factory.createEphemeralDriveApi(probeProfile)
                fetchDriveSubfolders(api, parentId)
            } finally {
                registry.remove(probeId)
            }
        }

        val savedProfile = existingProfileId?.let(::profileForEdit)
            ?.takeIf { it.type == BackendType.GOOGLE_DRIVE }
            ?: return Result.Err("Sign in with Google first")
        val api = factory.driveApi(savedProfile)
        return fetchDriveSubfolders(api, parentId)
    }

    fun needsDriveReindexOnSave(existingProfile: ServerProfile?): Boolean {
        if (existingProfile == null || existingProfile.type != BackendType.GOOGLE_DRIVE) return false
        val state = _driveState.value
        val existingEmail = (existingProfile.credentials as? ProfileCredentials.GoogleDrive)
            ?.accountEmail
            .orEmpty()
        val effectiveFolderId = if (state.scanAllAudio) {
            null
        } else {
            state.driveRootFolderId?.trim()?.takeIf { it.isNotBlank() }
        }
        val accountChanged = state.accountEmail.isNotBlank() &&
            !existingEmail.equals(state.accountEmail.trim(), ignoreCase = true)
        val folderChanged = existingProfile.driveRootFolderId != effectiveFolderId
        return accountChanged || folderChanged
    }

    fun testDriveOnly(existingProfileId: String? = null) {
        val state = _driveState.value
        val editMode = !existingProfileId.isNullOrBlank()
        if (!canTestDrive(state.accountEmail, state.accessToken, editMode)) {
            notifyDriveIncomplete(hasAuth = false)
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _ui.value = LoginUi(
                busy = true,
                activeAction = LoginAction.TEST_ONLY,
                failureNonce = _ui.value.failureNonce,
                banner = ConnectionBannerState.Testing("Contacting Google Drive…"),
            )

            val result: Result<String>? = withTimeoutOrNull(connectTimeoutMs) {
                if (state.accessToken.isNotBlank()) {
                    when (
                        val probe = auth.probeGoogleDrive(
                            email = state.accountEmail,
                            accessToken = state.accessToken,
                        )
                    ) {
                        is Result.Ok -> {
                            val resolvedEmail = (probe.value.profile.credentials as? ProfileCredentials.GoogleDrive)
                                ?.accountEmail
                                ?.ifBlank { state.accountEmail }
                                ?: state.accountEmail
                            Result.Ok(resolvedEmail)
                        }
                        is Result.Err -> probe
                    }
                } else {
                    val saved = existingProfileId?.let(::profileForEdit)
                        ?.takeIf { it.type == BackendType.GOOGLE_DRIVE }
                    if (saved == null) {
                        Result.Err("Google Drive profile not found.")
                    } else {
                        verifySavedDriveProfile(saved)
                    }
                }
            }

            _ui.value = when (result) {
                is Result.Ok -> {
                    val okMessage = "Connection OK · Google Drive (${result.value})"
                    LoginUi(
                        message = okMessage,
                        failureNonce = _ui.value.failureNonce,
                        banner = ConnectionBannerState.Success(okMessage),
                    )
                }
                is Result.Err -> errorUi(
                    message = result.message.ifBlank { DRIVE_UNAVAILABLE_ERROR },
                    nonce = _ui.value.failureNonce + 1,
                )
                null -> timeoutFailureUi(_ui.value.failureNonce + 1)
            }
        }
    }

    fun connectGoogleDrive(
        serverName: String = "",
        addMode: Boolean = false,
        existingProfileId: String? = null,
        onSuccess: () -> Unit = {},
    ) {
        val state = _driveState.value
        val editMode = !existingProfileId.isNullOrBlank()
        if (!canTestDrive(state.accountEmail, state.accessToken, editMode)) {
            notifyDriveIncomplete(hasAuth = false)
            return
        }
        if (!canConnectDrive(
                accountEmail = state.accountEmail,
                accessToken = state.accessToken,
                driveRootFolderId = state.driveRootFolderId,
                scanAllAudio = state.scanAllAudio,
                editMode = editMode,
            )
        ) {
            notifyDriveIncomplete(hasAuth = true)
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val current = settings.latestSettings ?: runCatching { settings.flow.first() }.getOrNull()
            val existingProfile = existingProfileId?.let(::profileForEdit)
            val effectiveFolderId = if (state.scanAllAudio) {
                null
            } else {
                state.driveRootFolderId?.trim()?.takeIf { it.isNotBlank() }
            }
            val effectiveFolderName = if (state.scanAllAudio) {
                ALL_AUDIO_FILES_LABEL
            } else {
                state.driveRootFolderName?.trim()?.takeIf { it.isNotBlank() }
            }
            val resolvedName = serverName.trim().ifBlank { state.accountEmail.trim() }
            val needsReindex = needsDriveReindexOnSave(existingProfile)

            _ui.value = LoginUi(
                busy = true,
                activeAction = LoginAction.CONNECT,
                failureNonce = _ui.value.failureNonce,
                banner = ConnectionBannerState.Testing("Verifying Google Drive…"),
            )

            val savedResult: Result<ServerProfile>? = withTimeoutOrNull(connectTimeoutMs) {
                if (state.accessToken.isNotBlank()) {
                    when (
                        val conn = auth.connectGoogleDrive(
                            name = resolvedName,
                            email = state.accountEmail,
                            accessToken = state.accessToken,
                            expiresAtEpochMs = state.expiresAtEpochMs,
                            rootFolderId = effectiveFolderId,
                            rootFolderName = effectiveFolderName,
                            existingProfileId = existingProfileId,
                        )
                    ) {
                        is Result.Ok -> Result.Ok(conn.value.profile)
                        is Result.Err -> conn
                    }
                } else if (existingProfile != null && existingProfile.type == BackendType.GOOGLE_DRIVE) {
                    when (val verified = verifySavedDriveProfile(existingProfile)) {
                        is Result.Err -> verified
                        is Result.Ok -> {
                            val updated = existingProfile.copy(
                                name = resolvedName,
                                driveRootFolderId = effectiveFolderId,
                                driveRootFolderName = effectiveFolderName,
                            )
                            when (val res = auth.updateProfile(updated)) {
                                is Result.Ok -> Result.Ok(updated)
                                is Result.Err -> res
                            }
                        }
                    }
                } else {
                    Result.Err("Sign in with Google first")
                }
            }

            when (savedResult) {
                is Result.Ok -> {
                    val savedProfile = savedResult.value
                    if (!addMode && existingProfileId == null) {
                        if (current?.deviceFilesStandalone == true) {
                            settings.setDeviceFilesStandalone(false)
                        }
                        if (current?.librarySourceFilter == "DEVICE") {
                            settings.setLibrarySourceFilter("ALL")
                            settings.setLibraryViewMode("SERVER")
                        }
                    }

                    val shouldIndex = existingProfileId == null || needsReindex
                    if (needsReindex) {
                        cleanupDriveProfileForReindex(savedProfile.id)
                    }
                    if (shouldIndex) {
                        driveDeps.scanCoordinator?.startScan(savedProfile.id)
                    }

                    val snackbar = if (existingProfileId != null && !needsReindex) {
                        "Updated \"${savedProfile.name}\""
                    } else {
                        "Connected to Google Drive · indexing started"
                    }
                    _events.trySend(LoginEvent.Snackbar(snackbar))
                    _ui.value = LoginUi(failureNonce = _ui.value.failureNonce)
                    onSuccess()
                }
                is Result.Err -> _ui.value = errorUi(
                    message = savedResult.message.ifBlank { DRIVE_UNAVAILABLE_ERROR },
                    nonce = _ui.value.failureNonce + 1,
                )
                null -> _ui.value = timeoutFailureUi(_ui.value.failureNonce + 1)
            }
        }
    }

    private suspend fun verifySavedDriveProfile(profile: ServerProfile): Result<String> =
        withContext(Dispatchers.IO) {
            val factory = driveDeps.backendFactory
            val creds = profile.credentials as? ProfileCredentials.GoogleDrive
                ?: return@withContext Result.Err("Google Drive credentials missing.")
            if (factory != null) {
                try {
                    val about = factory.driveApi(profile).getAbout()
                    val resolved = about.user?.emailAddress?.trim()?.takeIf { it.isNotBlank() }
                        ?: creds.accountEmail
                    return@withContext Result.Ok(resolved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext Result.Err(DriveAuthError.userMessage(e), e)
                }
            }
            when (
                val probe = auth.probeGoogleDrive(
                    email = creds.accountEmail,
                    accessToken = creds.accessToken,
                )
            ) {
                is Result.Ok -> Result.Ok(creds.accountEmail)
                is Result.Err -> probe
            }
        }

    private suspend fun cleanupDriveProfileForReindex(profileId: String) = withContext(Dispatchers.IO) {
        runCatching { driveDeps.driveScanCancellationRegistry?.cancel(profileId) }
        runCatching { driveDeps.driveTrackDao?.clearProfile(profileId) }
        runCatching { driveDeps.localFavoriteDao?.clearProfile(profileId) }
        runCatching { driveDeps.localPlayHistoryDao?.clearProfile(profileId) }
        runCatching { driveDeps.cachedCoverDao?.deleteByPrefix("$profileId:cov:") }
        runCatching { driveDeps.cachedCoverDao?.deleteByPrefix("drive-cover://$profileId/") }
        runCatching { driveDeps.libraryRefresh?.invalidateAll() }
    }

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
        if (backendType == BackendType.GOOGLE_DRIVE) {
            connectGoogleDrive(
                serverName = serverName,
                addMode = addMode,
                existingProfileId = existingProfileId,
                onSuccess = onSuccess,
            )
            return
        }
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
        if (backendType == BackendType.GOOGLE_DRIVE) {
            testDriveOnly(existingProfileId = existingProfileId)
            return
        }
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

    fun notifyDriveIncomplete(hasAuth: Boolean) {
        val message = if (hasAuth) {
            DRIVE_INCOMPLETE_FOLDER_SNACKBAR
        } else {
            DRIVE_SIGN_IN_FIRST_SNACKBAR
        }
        _events.trySend(LoginEvent.Snackbar(message))
    }

    fun notifyIncomplete(backendType: BackendType, editMode: Boolean) {
        if (backendType == BackendType.GOOGLE_DRIVE) {
            val hasAuth = canTestDrive(
                accountEmail = _driveState.value.accountEmail,
                accessToken = _driveState.value.accessToken,
                editMode = editMode,
            )
            notifyDriveIncomplete(hasAuth = hasAuth)
            return
        }
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
        _driveState.value = DriveFormState()
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
        internal fun forTest(
            auth: AuthRepository,
            settings: SettingsStore,
            driveAuthCoordinator: DriveAuthCoordinator? = null,
            backendFactory: BackendClientFactory? = null,
            probeRegistry: TransientProbeRegistry? = null,
            scanCoordinator: ScanCoordinator? = null,
            driveTrackDao: DriveTrackDao? = null,
            localFavoriteDao: LocalFavoriteDao? = null,
            localPlayHistoryDao: LocalPlayHistoryDao? = null,
            cachedCoverDao: CachedCoverDao? = null,
            driveScanCancellationRegistry: DriveScanCancellationRegistry? = null,
            libraryRefresh: LibraryRefresh? = null,
        ): LoginViewModel = LoginViewModel(
            auth = auth,
            settings = settings,
            driveDeps = LoginDriveDeps(
                driveAuthCoordinator = driveAuthCoordinator,
                backendFactory = backendFactory,
                probeRegistry = probeRegistry,
                scanCoordinator = scanCoordinator,
                driveTrackDao = driveTrackDao,
                localFavoriteDao = localFavoriteDao,
                localPlayHistoryDao = localPlayHistoryDao,
                cachedCoverDao = cachedCoverDao,
                driveScanCancellationRegistry = driveScanCancellationRegistry,
                libraryRefresh = libraryRefresh,
            ),
        )

        const val ALL_AUDIO_FILES_LABEL = "All audio files"
        const val DRIVE_UNAVAILABLE_ERROR =
            "Google Sign-In is not configured or Play Services is unavailable."
        const val DRIVE_CONSENT_DENIED_ERROR =
            "Vusic needs read-only access to your Drive to play your music."
        const val DRIVE_SIGN_IN_FIRST_SNACKBAR = "Sign in with Google first"
        const val DRIVE_INCOMPLETE_FOLDER_SNACKBAR =
            "Choose a Music folder or turn on \"Scan all audio files\""

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

        fun canTestDrive(
            accountEmail: String,
            accessToken: String,
            editMode: Boolean = false,
        ): Boolean = accountEmail.isNotBlank() && (accessToken.isNotBlank() || editMode)

        fun canConnectDrive(
            accountEmail: String,
            accessToken: String,
            driveRootFolderId: String?,
            scanAllAudio: Boolean,
            editMode: Boolean = false,
        ): Boolean = canTestDrive(
            accountEmail = accountEmail,
            accessToken = accessToken,
            editMode = editMode,
        ) && (!driveRootFolderId.isNullOrBlank() || scanAllAudio)

        private fun isConsentDeniedMessage(message: String): Boolean {
            val lower = message.lowercase()
            return lower.contains("consent") ||
                lower.contains("denied") ||
                lower.contains("access_denied") ||
                lower.contains("403")
        }

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
