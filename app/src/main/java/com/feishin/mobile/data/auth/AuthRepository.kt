package com.feishin.mobile.data.auth

import com.feishin.mobile.core.common.CrashGuard
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.OpenSubsonicExtensions
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns multi-backend profile authentication state. Profiles live encrypted in
 * [ProfileStore]; in-flight login/probe attempts live in [TransientProbeRegistry]
 * until verified via `ping`.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val profileStore: ProfileStore,
    private val legacyCredentials: CredentialsStore,
    private val backendFactory: BackendClientFactory,
    private val probeRegistry: TransientProbeRegistry,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)

    private val verifying = MutableStateFlow(false)

    val profiles: StateFlow<List<ServerProfile>> = profileStore.profiles
    val activeProfiles: StateFlow<List<ServerProfile>> = profileStore.activeProfiles

    private val _loginState = MutableStateFlow(profileStore.profiles.value)
    val loginState: StateFlow<List<ServerProfile>> = _loginState.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            combine(profileStore.profiles, verifying) { list, isVerifying ->
                if (isVerifying && list.isEmpty()) emptyList() else list
            }.collect { _loginState.value = it }
        }
        scope.launch { preload() }
    }

    suspend fun preload() = withContext(Dispatchers.IO) {
        profileStore.preload()
        syncLoginState()
        _ready.value = true
    }

    suspend fun connectSubsonic(
        type: BackendType,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
    ): Result<ServerProfile> = withContext(Dispatchers.IO) {
        val probed = runSubsonicProbe(
            type = type,
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
        )
        when (probed) {
            is Result.Err -> probed
            is Result.Ok -> {
                val saved = probed.value.copy(
                    id = profileStore.nextProfileId(),
                    priority = profileStore.nextPriority(),
                )
                profileStore.upsert(saved)
                syncLoginState()
                Result.Ok(saved)
            }
        }
    }

    suspend fun probeSubsonic(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean = false,
        allowCleartext: Boolean = true,
    ): Result<ServerProfile> = withContext(Dispatchers.IO) {
        runSubsonicProbe(
            type = BackendType.NAVIDROME,
            name = "",
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
        )
    }

    suspend fun updateProfile(profile: ServerProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = try {
            normalizeBaseUrl(profile.url)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.Err("Invalid server URL.")
        }
        profileStore.upsert(profile.copy(url = baseUrl))
        syncLoginState()
        Result.Ok(Unit)
    }

    suspend fun setProfileEnabled(profileId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        profileStore.setEnabled(profileId, enabled)
        syncLoginState()
    }

    suspend fun reorderProfiles(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        profileStore.reorder(orderedIds)
        syncLoginState()
    }

    suspend fun removeProfile(
        profileId: String,
        @Suppress("UNUSED_PARAMETER") keepDownloads: Boolean,
    ) = withContext(Dispatchers.IO) {
        profileStore.remove(profileId)
        if (profileStore.profiles.value.isEmpty()) {
            runCatching { legacyCredentials.clear() }
        }
        syncLoginState()
    }

    /** Compatibility shim for LoginViewModel until P12.0-I. */
    suspend fun connect(serverUrl: String, username: String, password: String): Result<Unit> {
        val current = runCatching { settings.flow.first() }.getOrNull()
        return when (
            val r = connectSubsonic(
                type = BackendType.NAVIDROME,
                name = "",
                serverUrl = serverUrl,
                username = username,
                password = password,
                allowSelfSigned = current?.allowSelfSigned ?: false,
                allowCleartext = current?.allowCleartext ?: true,
            )
        ) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    /** Compatibility shim for LoginViewModel until P12.0-I. */
    suspend fun probe(serverUrl: String, username: String, password: String): Result<Unit> {
        val current = runCatching { settings.flow.first() }.getOrNull()
        return when (
            val r = probeSubsonic(
                serverUrl = serverUrl,
                username = username,
                password = password,
                allowSelfSigned = current?.allowSelfSigned ?: false,
                allowCleartext = current?.allowCleartext ?: true,
            )
        ) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    /** Wipes all profiles and legacy credentials; navigation reacts via [loginState]. */
    fun logout() {
        profileStore.clear()
        runCatching { legacyCredentials.clear() }
        syncLoginState()
    }

    /** Compatibility shim for SettingsViewModel until P12.0-I. */
    suspend fun updateServerUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = profileStore.profiles.value.firstOrNull()
            ?: return@withContext Result.Err("Not logged in")
        updateProfile(current.copy(url = url))
    }

    private suspend fun runSubsonicProbe(
        type: BackendType,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
    ): Result<ServerProfile> {
        val baseUrl = try {
            normalizeBaseUrl(serverUrl)
        } catch (e: IllegalArgumentException) {
            return Result.Err("Invalid server URL.")
        }
        val user = username.trim()
        if (user.isEmpty()) return Result.Err("Username is empty.")
        if (!allowCleartext && baseUrl.startsWith("http://", ignoreCase = true)) {
            return Result.Err("Cleartext HTTP connections are disabled for this server.")
        }

        val displayName = name.trim().ifBlank { ProfileStore.hostLabel(baseUrl) }
        val candidate = ServerProfile(
            id = "__probe__",
            name = displayName,
            type = type,
            url = baseUrl,
            userId = user,
            credentials = ProfileCredentials.Subsonic(username = user, password = password),
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
            enabled = true,
            priority = profileStore.nextPriority(),
        )
        val probeId = probeRegistry.register(candidate)
        val probeProfile = probeRegistry.get(probeId) ?: candidate.copy(id = probeId)
        verifying.value = true
        syncLoginState()
        return try {
            val api = backendFactory.createEphemeralSubsonicApi(probeProfile)
            val ping = api.ping().response
            if (ping.status != "ok") {
                Result.Err(SubsonicAuth.userMessage(ping.error?.code, ping.error?.message))
            } else {
                val extensions = fetchExtensionsTolerant(api)
                Result.Ok(probeProfile.copy(openSubsonicExtensions = extensions))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Err(SubsonicAuth.userMessage(null, null, e), e)
        } finally {
            probeRegistry.remove(probeId)
            verifying.value = false
            syncLoginState()
        }
    }

    private suspend fun fetchExtensionsTolerant(api: SubsonicApi): OpenSubsonicExtensions {
        val names = try {
            val resp = api.getOpenSubsonicExtensions().response
            if (resp.status == "ok") {
                resp.openSubsonicExtensions.orEmpty()
                    .map { it.name.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return OpenSubsonicExtensions(
            songLyrics = names.any { it.equals("songLyrics", ignoreCase = true) },
            transcodeOffset = names.any { it.equals("transcodeOffset", ignoreCase = true) },
            formPost = names.any { it.equals("formPost", ignoreCase = true) },
            apiKeyAuthentication = names.any { it.equals("apiKeyAuthentication", ignoreCase = true) },
            rawNames = names,
        )
    }

    private fun normalizeBaseUrl(raw: String): String =
        SubsonicAuth.normalizeServerUrl(raw)
            .removeSuffix("/rest/")
            .removeSuffix("/rest")
            .trimEnd('/')

    private fun syncLoginState() {
        val list = profileStore.profiles.value
        _loginState.value = if (verifying.value && list.isEmpty()) emptyList() else list
    }
}
