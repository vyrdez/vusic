package com.feishin.mobile.data.auth

import com.feishin.mobile.core.common.CrashGuard
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.JellyfinUrl
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.LyricsCacheDao
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.remote.jellyfin.JellyfinAuthRequest
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
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

data class ConnectionResult(
    val profile: ServerProfile,
    val latencyMs: Long,
    val apiInfo: String = "",
)

object JellyfinAuthError {
    fun userMessage(
        cause: Throwable? = null,
        statusCode: Int? = (cause as? HttpException)?.code(),
        requestPath: String? = (cause as? HttpException)
            ?.response()
            ?.raw()
            ?.request
            ?.url
            ?.encodedPath,
    ): String {
        if (statusCode == 401) {
            return "Wrong username or password."
        }
        if (statusCode == 404 &&
            requestPath?.contains("/Users/AuthenticateByName", ignoreCase = true) == true
        ) {
            return "This doesn't look like a Jellyfin server."
        }
        if (statusCode != null) {
            return when (statusCode) {
                403 -> "Access denied for this Jellyfin account."
                404 -> "Jellyfin endpoint not found (HTTP 404)."
                else -> "Server error (HTTP $statusCode)."
            }
        }
        if (cause is SerializationException || cause is IllegalArgumentException) {
            return "This doesn't look like a Jellyfin server."
        }
        return SubsonicAuth.userMessage(code = null, serverText = null, cause = cause)
    }
}

private class ProfileCleanupDeps(
    val cachedTrackStore: dagger.Lazy<CachedTrackStore>,
    val metadataCache: dagger.Lazy<MetadataCache>,
    val cachedCoverDao: dagger.Lazy<CachedCoverDao>,
    val pendingActionDao: dagger.Lazy<PendingActionDao>,
    val lyricsCacheDao: dagger.Lazy<LyricsCacheDao>,
    val downloadRepository: dagger.Lazy<DownloadRepository>,
)

/**
 * Owns multi-backend profile authentication state. Profiles live encrypted in
 * [ProfileStore]; in-flight login/probe attempts live in [TransientProbeRegistry]
 * until verified via `ping` or Jellyfin `AuthenticateByName`.
 */
@Singleton
class AuthRepository private constructor(
    private val profileStore: ProfileStore,
    private val legacyCredentials: CredentialsStore,
    private val backendFactory: BackendClientFactory,
    private val probeRegistry: TransientProbeRegistry,
    private val settings: SettingsStore,
    private val profileAuthStateStore: ProfileAuthStateStore,
    private val cleanup: ProfileCleanupDeps?,
) {
    @Inject
    constructor(
        profileStore: ProfileStore,
        legacyCredentials: CredentialsStore,
        backendFactory: BackendClientFactory,
        probeRegistry: TransientProbeRegistry,
        settings: SettingsStore,
        profileAuthStateStore: ProfileAuthStateStore,
        cachedTrackStore: dagger.Lazy<CachedTrackStore>,
        metadataCache: dagger.Lazy<MetadataCache>,
        cachedCoverDao: dagger.Lazy<CachedCoverDao>,
        pendingActionDao: dagger.Lazy<PendingActionDao>,
        lyricsCacheDao: dagger.Lazy<LyricsCacheDao>,
        downloadRepository: dagger.Lazy<DownloadRepository>,
    ) : this(
        profileStore = profileStore,
        legacyCredentials = legacyCredentials,
        backendFactory = backendFactory,
        probeRegistry = probeRegistry,
        settings = settings,
        profileAuthStateStore = profileAuthStateStore,
        cleanup = ProfileCleanupDeps(
            cachedTrackStore = cachedTrackStore,
            metadataCache = metadataCache,
            cachedCoverDao = cachedCoverDao,
            pendingActionDao = pendingActionDao,
            lyricsCacheDao = lyricsCacheDao,
            downloadRepository = downloadRepository,
        ),
    )

    internal constructor(
        profileStore: ProfileStore,
        legacyCredentials: CredentialsStore,
        backendFactory: BackendClientFactory,
        probeRegistry: TransientProbeRegistry,
        settings: SettingsStore,
        profileAuthStateStore: ProfileAuthStateStore = ProfileAuthStateStore(),
    ) : this(
        profileStore = profileStore,
        legacyCredentials = legacyCredentials,
        backendFactory = backendFactory,
        probeRegistry = probeRegistry,
        settings = settings,
        profileAuthStateStore = profileAuthStateStore,
        cleanup = null,
    )
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
    ): Result<ServerProfile> = when (
        val res = connectSubsonicWithResult(
            type = type,
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
        )
    ) {
        is Result.Ok -> Result.Ok(res.value.profile)
        is Result.Err -> res
    }

    suspend fun connectSubsonicWithResult(
        type: BackendType,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> = withContext(Dispatchers.IO) {
        val probed = runSubsonicProbe(
            type = type,
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
            existingProfileId = existingProfileId,
        )
        when (probed) {
            is Result.Err -> probed
            is Result.Ok -> {
                val candidate = probed.value.profile
                val creds = candidate.credentials as ProfileCredentials.Subsonic
                val matchedExisting = findExistingProfile(
                    type = candidate.type,
                    normalizedUrl = candidate.url,
                    username = creds.username,
                    existingProfileId = existingProfileId,
                )
                val saved = persistConnectedProfile(candidate, matchedExisting)
                Result.Ok(probed.value.copy(profile = saved))
            }
        }
    }

    suspend fun probeSubsonic(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean = false,
        allowCleartext: Boolean = true,
    ): Result<ServerProfile> = when (
        val res = probeSubsonicWithResult(
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
        )
    ) {
        is Result.Ok -> Result.Ok(res.value.profile)
        is Result.Err -> res
    }

    suspend fun probeSubsonicWithResult(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean = false,
        allowCleartext: Boolean = true,
        type: BackendType = BackendType.NAVIDROME,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> = withContext(Dispatchers.IO) {
        runSubsonicProbe(
            type = type,
            name = "",
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
            existingProfileId = existingProfileId,
        )
    }

    suspend fun connectJellyfin(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> = withContext(Dispatchers.IO) {
        val probed = runJellyfinProbe(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
            existingProfileId = existingProfileId,
        )
        when (probed) {
            is Result.Err -> probed
            is Result.Ok -> {
                val candidate = probed.value.profile
                val creds = candidate.credentials as ProfileCredentials.Jellyfin
                val matchedExisting = findExistingProfile(
                    type = BackendType.JELLYFIN,
                    normalizedUrl = candidate.url,
                    username = creds.username,
                    existingProfileId = existingProfileId,
                )
                val saved = persistConnectedProfile(candidate, matchedExisting)
                Result.Ok(probed.value.copy(profile = saved))
            }
        }
    }

    suspend fun probeJellyfin(
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean = false,
        allowCleartext: Boolean = true,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> = withContext(Dispatchers.IO) {
        runJellyfinProbe(
            name = "",
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowSelfSigned = allowSelfSigned,
            allowCleartext = allowCleartext,
            existingProfileId = existingProfileId,
        )
    }

    suspend fun updateProfile(profile: ServerProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = try {
            if (profile.type == BackendType.JELLYFIN) {
                JellyfinUrl.normalize(profile.url)
            } else {
                normalizeBaseUrl(profile.url)
            }
        } catch (e: IllegalArgumentException) {
            return@withContext Result.Err("Invalid server URL.")
        }
        profileStore.upsert(profile.copy(url = baseUrl))
        backendFactory.invalidate(profile.id)
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
        keepDownloads: Boolean,
    ) = withContext(Dispatchers.IO) {
        profileStore.remove(profileId)
        backendFactory.invalidate(profileId)
        profileAuthStateStore.clear(profileId)
        cleanup?.let { c ->
            runCatching { c.cachedTrackStore.get().clearForProfile(profileId) }
            runCatching { c.metadataCache.get().deleteByPrefix("$profileId:") }
            runCatching { c.cachedCoverDao.get().deleteByPrefix("$profileId:") }
            runCatching { c.pendingActionDao.get().deleteByProfile(profileId) }
            runCatching { c.lyricsCacheDao.get().deleteByProfile(profileId) }
            runCatching { c.downloadRepository.get().removeProfileData(profileId, keepDownloads) }
        }
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
        backendFactory.clearAll()
        profileAuthStateStore.clearAll()
        runCatching { legacyCredentials.clear() }
        syncLoginState()
    }

    /** Compatibility shim for SettingsViewModel until P12.0-I. */
    suspend fun updateServerUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = profileStore.profiles.value.firstOrNull()
            ?: return@withContext Result.Err("Not logged in")
        updateProfile(current.copy(url = url))
    }

    private fun findExistingProfile(
        type: BackendType,
        normalizedUrl: String,
        username: String,
        existingProfileId: String?,
    ): ServerProfile? {
        if (!existingProfileId.isNullOrBlank()) {
            return profileStore.byId(existingProfileId)?.takeIf { it.type == type }
        }
        return profileStore.profiles.value.firstOrNull { profile ->
            if (profile.type != type) return@firstOrNull false
            val existingUrl = runCatching {
                if (type == BackendType.JELLYFIN) JellyfinUrl.normalize(profile.url)
                else normalizeBaseUrl(profile.url)
            }.getOrNull()
            if (existingUrl != normalizedUrl) return@firstOrNull false
            val existingUsername = when (val creds = profile.credentials) {
                is ProfileCredentials.Subsonic -> creds.username
                is ProfileCredentials.Jellyfin -> creds.username
                is ProfileCredentials.GoogleDrive -> creds.accountEmail
            }
            existingUsername.equals(username, ignoreCase = true)
        }
    }

    private suspend fun persistConnectedProfile(
        candidate: ServerProfile,
        matchedExisting: ServerProfile?,
    ): ServerProfile {
        val saved = if (matchedExisting != null) {
            candidate.copy(
                id = matchedExisting.id,
                priority = matchedExisting.priority,
                enabled = true,
            )
        } else {
            candidate.copy(
                id = profileStore.nextProfileId(),
                priority = profileStore.nextPriority(),
                enabled = true,
            )
        }
        profileStore.upsert(saved)
        backendFactory.invalidate(saved.id)
        profileAuthStateStore.clear(saved.id)
        clearDeviceFilesStandaloneIfNeeded()
        syncLoginState()
        return saved
    }

    private suspend fun clearDeviceFilesStandaloneIfNeeded() {
        val isStandalone = settings.latestSettings?.deviceFilesStandalone
            ?: runCatching { settings.flow.first().deviceFilesStandalone }.getOrDefault(false)
        if (isStandalone) {
            runCatching { settings.setDeviceFilesStandalone(false) }
        }
    }

    private suspend fun runSubsonicProbe(
        type: BackendType,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> {
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

        val existingCreds = existingProfileId
            ?.let { profileStore.byId(it)?.credentials as? ProfileCredentials.Subsonic }
        val resolvedPassword = if (password.isBlank() && existingCreds != null) {
            existingCreds.password
        } else {
            password
        }

        val displayName = name.trim().ifBlank { ProfileStore.hostLabel(baseUrl) }
        val candidate = ServerProfile(
            id = "__probe__",
            name = displayName,
            type = type,
            url = baseUrl,
            userId = user,
            credentials = ProfileCredentials.Subsonic(username = user, password = resolvedPassword),
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
            val startNs = System.nanoTime()
            val api = backendFactory.createEphemeralSubsonicApi(probeProfile)
            val ping = api.ping().response
            if (ping.status != "ok") {
                Result.Err(SubsonicAuth.userMessage(ping.error?.code, ping.error?.message))
            } else {
                val extensions = fetchExtensionsTolerant(api)
                val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
                val label = if (type == BackendType.OPENSUBSONIC) "OpenSubsonic" else "Navidrome"
                val apiInfo = if (ping.version.isNotBlank()) {
                    "$label · API ${ping.version}"
                } else {
                    label
                }
                Result.Ok(
                    ConnectionResult(
                        profile = probeProfile.copy(openSubsonicExtensions = extensions),
                        latencyMs = latencyMs,
                        apiInfo = apiInfo,
                    ),
                )
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

    private suspend fun runJellyfinProbe(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean,
        allowCleartext: Boolean,
        existingProfileId: String? = null,
    ): Result<ConnectionResult> {
        val baseUrl = try {
            JellyfinUrl.normalize(serverUrl)
        } catch (e: IllegalArgumentException) {
            return Result.Err("Invalid server URL.")
        }
        val user = username.trim()
        if (user.isEmpty()) return Result.Err("Username is empty.")
        if (!allowCleartext && baseUrl.startsWith("http://", ignoreCase = true)) {
            return Result.Err("Cleartext HTTP connections are disabled for this server.")
        }

        val existing = existingProfileId?.let { profileStore.byId(it) }
            ?.takeIf { it.type == BackendType.JELLYFIN }
        val existingCreds = existing?.credentials as? ProfileCredentials.Jellyfin
        val existingUrl = existing?.let { runCatching { JellyfinUrl.normalize(it.url) }.getOrNull() }
        val canReuseStoredToken = existing != null &&
            existingCreds != null &&
            password.isBlank() &&
            existingUrl == baseUrl &&
            existingCreds.username == user

        val deviceId = existingCreds?.deviceId?.takeIf { it.isNotBlank() }
            ?: profileStore.getOrCreateJellyfinDeviceId()
        val displayName = name.trim().ifBlank { ProfileStore.hostLabel(baseUrl) }
        val candidate = ServerProfile(
            id = "__probe__",
            name = displayName,
            type = BackendType.JELLYFIN,
            url = baseUrl,
            userId = if (canReuseStoredToken) existing!!.userId else "",
            credentials = if (canReuseStoredToken) {
                existingCreds!!.copy(deviceId = deviceId)
            } else {
                ProfileCredentials.Jellyfin(
                    username = user,
                    accessToken = "",
                    serverId = "",
                    deviceId = deviceId,
                )
            },
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
            val startNs = System.nanoTime()
            val api = backendFactory.createEphemeralJellyfinApi(probeProfile)
            val resolvedProfile = if (canReuseStoredToken) {
                if (existing!!.userId.isNotBlank()) {
                    api.getItems(userId = existing.userId, limit = 1)
                }
                probeProfile
            } else {
                val auth = api.authenticateByName(
                    JellyfinAuthRequest(username = user, pw = password),
                )
                if (auth.accessToken.isBlank()) {
                    return Result.Err("This doesn't look like a Jellyfin server.")
                }
                val resolvedUsername = auth.user.name.ifBlank { user }
                val resolvedUserId = auth.user.id.ifBlank { resolvedUsername }
                probeProfile.copy(
                    userId = resolvedUserId,
                    credentials = ProfileCredentials.Jellyfin(
                        username = resolvedUsername,
                        accessToken = auth.accessToken,
                        serverId = auth.serverId,
                        deviceId = deviceId,
                    ),
                )
            }
            val apiInfo = fetchJellyfinInfoTolerant(api)
            val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            Result.Ok(
                ConnectionResult(
                    profile = resolvedProfile,
                    latencyMs = latencyMs,
                    apiInfo = apiInfo,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Err(JellyfinAuthError.userMessage(e), e)
        } finally {
            probeRegistry.remove(probeId)
            verifying.value = false
            syncLoginState()
        }
    }

    private suspend fun fetchJellyfinInfoTolerant(api: JellyfinApi): String {
        val info = try {
            api.getPublicSystemInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return "Jellyfin"
        }
        val product = info.productName.ifBlank { "Jellyfin" }
        return "$product ${info.version}".trim()
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
