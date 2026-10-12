package com.feishin.mobile.data.auth

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.remote.SubsonicApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns login state. Credentials themselves live encrypted in [CredentialsStore];
 * this repository exposes them as a [StateFlow] plus connect/probe/logout
 * operations that verify against the server with a `ping`.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val store: CredentialsStore,
    private val api: SubsonicApi,
) {
    /** Null = logged out, non-null = logged in. Read synchronously at startup: no launch flicker. */
    val loginState: StateFlow<Credentials?> = store.state

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Backing prefs were read synchronously in the store constructor, so this
        // flips true before the first frame: no splash flash in practice.
        scope.launch { _ready.value = true }
    }

    /**
     * Tests the server and, on success, persists credentials (Test & Connect).
     * Never commits credentials when the test fails.
     */
    suspend fun connect(serverUrl: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val normalized = try {
                SubsonicAuth.normalizeServerUrl(serverUrl)
            } catch (e: IllegalArgumentException) {
                return@withContext Result.Err("Invalid server URL.")
            }
            val user = username.trim()
            if (user.isEmpty()) return@withContext Result.Err("Username is empty.")
            val host = normalized.removeSuffix("/rest/")
            val previous = store.state.value
            // Save first so the auth interceptor can sign the ping, then verify.
            store.save(host, user, password)
            try {
                val r = api.ping().response
                if (r.status == "ok") {
                    Result.Ok(Unit)
                } else {
                    if (previous == null) store.clear()
                    else store.save(previous.serverUrl, previous.username, previous.password)
                    Result.Err(SubsonicAuth.userMessage(r.error?.code, r.error?.message))
                }
            } catch (e: Exception) {
                if (previous == null) store.clear()
                else store.save(previous.serverUrl, previous.username, previous.password)
                Result.Err(SubsonicAuth.userMessage(null, null, e), e)
            }
        }

    /** Connection test only: leaves previously stored credentials untouched. */
    suspend fun probe(serverUrl: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val normalized = try {
                SubsonicAuth.normalizeServerUrl(serverUrl)
            } catch (e: IllegalArgumentException) {
                return@withContext Result.Err("Invalid server URL.")
            }
            val previous = store.state.value
            store.save(normalized.removeSuffix("/rest/"), username.trim(), password)
            try {
                val r = api.ping().response
                if (r.status == "ok") Result.Ok(Unit)
                else Result.Err(SubsonicAuth.userMessage(r.error?.code, r.error?.message))
            } catch (e: Exception) {
                Result.Err(SubsonicAuth.userMessage(null, null, e), e)
            } finally {
                if (previous == null) store.clear()
                else store.save(previous.serverUrl, previous.username, previous.password)
            }
        }

    /** Wipes credentials immediately; navigation reacts via [loginState]. */
    fun logout() = store.clear()
}
