package com.feishin.mobile.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class ProfileStore private constructor(
    private val prefsProvider: () -> SharedPreferences,
    private val legacyCredentials: CredentialsStore,
    private val settings: SettingsStore,
    private val legacyPrefsOverride: SharedPreferences?,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        legacyCredentials: CredentialsStore,
        settings: SettingsStore,
    ) : this(
        prefsProvider = { createEncryptedPrefs(context.applicationContext) },
        legacyCredentials = legacyCredentials,
        settings = settings,
        legacyPrefsOverride = null,
    )

    internal constructor(
        prefs: SharedPreferences,
        legacyCredentials: CredentialsStore,
        settings: SettingsStore,
        legacyPrefs: SharedPreferences? = null,
    ) : this(
        prefsProvider = { prefs },
        legacyCredentials = legacyCredentials,
        settings = settings,
        legacyPrefsOverride = legacyPrefs,
    )

    private val prefs: SharedPreferences by lazy { prefsProvider() }
    private val preloadMutex = Mutex()
    private val writeLock = Any()
    @Volatile private var preloaded = false

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val _profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    private val _activeProfiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val activeProfiles: StateFlow<List<ServerProfile>> = _activeProfiles.asStateFlow()

    /** Idempotent disk read + deterministic one-shot legacy migration on IO. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        preloadMutex.withLock {
            preloadLocked(skipIfPreloaded = false)
        }
    }

    /** Fast-path no-op once preloaded or written; otherwise runs [preload] under [preloadMutex]. */
    suspend fun preloadIfNeeded() {
        if (preloaded) return
        withContext(Dispatchers.IO) {
            preloadMutex.withLock {
                if (!preloaded) {
                    preloadLocked(skipIfPreloaded = true)
                }
            }
        }
    }

    private suspend fun preloadLocked(skipIfPreloaded: Boolean) {
        val stored = readStoredProfiles()
        val legacyMigrated = prefs.getBoolean(KEY_LEGACY_MIGRATED, false)
        if (!legacyMigrated && stored.isEmpty()) {
            migrateLegacyIfNeeded(skipIfPreloaded)
        } else {
            synchronized(writeLock) {
                if (!skipIfPreloaded || !preloaded) {
                    setMemoryState(sortProfiles(stored))
                    preloaded = true
                }
            }
        }
    }

    fun saveAll(list: List<ServerProfile>): Boolean = synchronized(writeLock) {
        val sorted = sortProfiles(list)
        val encoded = json.encodeToString(sorted)
        val committed = prefs.edit()
            .putString(KEY_PROFILES_JSON, encoded)
            .commit()
        if (committed) {
            setMemoryState(sorted)
            preloaded = true
        }
        committed
    }

    fun upsert(profile: ServerProfile): Boolean = synchronized(writeLock) {
        ensureMemoryLoadedForWrite()
        val current = _profiles.value.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        saveAll(current)
    }

    fun setEnabled(profileId: String, enabled: Boolean): Boolean = synchronized(writeLock) {
        ensureMemoryLoadedForWrite()
        val updated = _profiles.value.map {
            if (it.id == profileId) it.copy(enabled = enabled) else it
        }
        saveAll(updated)
    }

    fun reorder(orderedIds: List<String>): Boolean = synchronized(writeLock) {
        ensureMemoryLoadedForWrite()
        val byId = _profiles.value.associateBy { it.id }
        val reordered = mutableListOf<ServerProfile>()
        orderedIds.forEachIndexed { index, id ->
            byId[id]?.let { reordered.add(it.copy(priority = index)) }
        }
        val remaining = _profiles.value.filter { it.id !in orderedIds }
        remaining.forEachIndexed { offset, profile ->
            reordered.add(profile.copy(priority = orderedIds.size + offset))
        }
        saveAll(reordered)
    }

    fun remove(profileId: String): Boolean = synchronized(writeLock) {
        ensureMemoryLoadedForWrite()
        saveAll(_profiles.value.filterNot { it.id == profileId })
    }

    fun clear() = synchronized(writeLock) {
        prefs.edit()
            .remove(KEY_PROFILES_JSON)
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .commit()
        setMemoryState(emptyList())
        preloaded = true
    }

    private fun ensureMemoryLoadedForWrite() {
        if (preloaded) return
        val stored = readStoredProfiles()
        if (stored.isNotEmpty()) {
            setMemoryState(sortProfiles(stored))
        }
    }

    fun byId(profileId: String): ServerProfile? = _profiles.value.firstOrNull { it.id == profileId }

    fun findByHost(scheme: String, host: String, port: Int): ServerProfile? {
        if (host.isBlank()) return null
        val targetPort = effectivePort(scheme, port)
        return _profiles.value.firstOrNull { profile ->
            val parsed = profile.url.toHttpUrlOrNull() ?: return@firstOrNull false
            parsed.scheme.equals(scheme, ignoreCase = true) &&
                parsed.host.equals(host, ignoreCase = true) &&
                parsed.port == targetPort
        }
    }

    fun nextProfileId(): String =
        if (_profiles.value.isEmpty()) "p1"
        else "p_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    fun nextPriority(): Int = (_profiles.value.maxOfOrNull { it.priority } ?: -1) + 1

    /**
     * Generates and persists a stable UUIDv4 Jellyfin device ID once, surviving
     * profile updates and logouts so Jellyfin server sessions remain consistent.
     */
    fun getOrCreateJellyfinDeviceId(): String = synchronized(writeLock) {
        val existing = prefs.getString(KEY_JELLYFIN_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return@synchronized existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_JELLYFIN_DEVICE_ID, generated).commit()
        generated
    }

    private suspend fun migrateLegacyIfNeeded(skipIfPreloaded: Boolean) {
        val legacy = readLegacyCredentials()
        if (legacy == null) {
            synchronized(writeLock) {
                if (!skipIfPreloaded || !preloaded) {
                    prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).commit()
                    setMemoryState(emptyList())
                    preloaded = true
                }
            }
            return
        }

        val currentSettings = runCatching { settings.flow.first() }
            .getOrElse { SettingsStore.Settings() }
        val normalizedUrl = normalizeBaseUrl(legacy.serverUrl)
        val migrated = ServerProfile(
            id = "p1",
            name = hostLabel(normalizedUrl),
            type = BackendType.NAVIDROME,
            url = normalizedUrl,
            userId = legacy.username,
            credentials = ProfileCredentials.Subsonic(
                username = legacy.username,
                password = legacy.password,
            ),
            allowSelfSigned = currentSettings.allowSelfSigned,
            allowCleartext = currentSettings.allowCleartext,
            enabled = true,
            priority = 0,
        )
        val list = listOf(migrated)
        val encoded = json.encodeToString(list)
        val committed = synchronized(writeLock) {
            if (skipIfPreloaded && preloaded) {
                false
            } else {
                prefs.edit()
                    .putString(KEY_PROFILES_JSON, encoded)
                    .putBoolean(KEY_LEGACY_MIGRATED, true)
                    .commit()
                    .also { ok ->
                        if (ok) {
                            setMemoryState(list)
                            preloaded = true
                        }
                    }
            }
        }
        if (committed) {
            clearLegacyCredentials()
        }
    }

    private fun readLegacyCredentials(): Credentials? {
        val override = legacyPrefsOverride
        if (override != null) {
            val url = override.getString("serverUrl", null)
            val user = override.getString("username", null)
            val pass = override.getString("password", null)
            return if (url.isNullOrBlank() || user.isNullOrBlank() || pass == null) {
                null
            } else {
                Credentials(url, user, pass)
            }
        }
        legacyCredentials.preload()
        return legacyCredentials.state.value
    }

    private fun clearLegacyCredentials() {
        val override = legacyPrefsOverride
        if (override != null) {
            override.edit().clear().commit()
        } else {
            legacyCredentials.clear()
        }
    }

    private fun readStoredProfiles(): List<ServerProfile> {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun setMemoryState(sorted: List<ServerProfile>) {
        _profiles.value = sorted
        _activeProfiles.value = sorted.filter { it.enabled }
    }

    private fun sortProfiles(list: List<ServerProfile>): List<ServerProfile> =
        list.sortedWith(compareBy<ServerProfile> { it.priority }.thenBy { it.id })

    companion object {
        internal const val PREFS_NAME = "feishin_profiles"
        internal const val KEY_PROFILES_JSON = "profiles_json_v1"
        internal const val KEY_LEGACY_MIGRATED = "legacy_migrated_v1"
        internal const val KEY_JELLYFIN_DEVICE_ID = "jellyfin_device_id"

        private fun createEncryptedPrefs(appContext: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        internal fun normalizeBaseUrl(raw: String): String =
            runCatching {
                SubsonicAuth.normalizeServerUrl(raw)
                    .removeSuffix("/rest/")
                    .removeSuffix("/rest")
                    .trimEnd('/')
            }.getOrElse { raw.trim().trimEnd('/') }

        internal fun hostLabel(serverUrl: String): String =
            serverUrl.toHttpUrlOrNull()?.host?.ifBlank { null }
                ?: serverUrl.removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { serverUrl }

        private fun effectivePort(scheme: String, port: Int): Int = when {
            port > 0 -> port
            scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }
    }
}
