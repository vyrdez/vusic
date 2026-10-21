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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.feishin.mobile.core.common.CrashGuard.handler)

    private val verifying = MutableStateFlow(false)

    /**
     * Null = logged out, non-null = logged in. Populated by preload() before
     * `ready` flips, so the gated UI never sees the initial null: no launch
     * flicker.
     *
     * A connect/probe saves the candidate credentials before it can sign the
     * ping, so [store] briefly holds them even when the attempt will fail.
     * While that verification is in flight [loginState] reports "logged out",
     * otherwise the gated UI would flash the main screen and come back — which
     * recreates the login form and throws away everything the user typed.
     */
    val loginState: StateFlow<Credentials?> =
        combine(store.state, verifying) { creds, isVerifying ->
            if (isVerifying) null else creds
        }.stateIn(scope, SharingStarted.Eagerly, store.state.value)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        // Credentials disk read happens here on IO (StrictMode-clean), and
        // AppNav holds a blank gate on `ready` until it completes.
        scope.launch {
            store.preload()
            _ready.value = true
        }
    }

    /**
     * Tests the server and, on success, persists credentials (Test & Connect).
     * Never commits credentials when the test fails.
     *
     * Overlapping calls are safe: cleanup only restores when the store still
     * holds what THIS call saved, so a slow earlier attempt cannot wipe a
     * newer successful login (e.g. double-tap with a stalled first ping).
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
            // Mark verification first: the credentials are about to be saved
            // so the interceptor can sign the ping, and the UI must not treat
            // that as a login until the ping says so.
            verifying.value = true
            try {
                // Save first so the auth interceptor can sign the ping, then verify.
                store.save(host, user, password)
                val mine = store.state.value
                try {
                    val r = api.ping().response
                    if (r.status == "ok") {
                        Result.Ok(Unit)
                    } else {
                        restoreIfMine(previous, mine)
                        Result.Err(SubsonicAuth.userMessage(r.error?.code, r.error?.message))
                    }
                } catch (e: Exception) {
                    restoreIfMine(previous, mine)
                    Result.Err(SubsonicAuth.userMessage(null, null, e), e)
                }
            } finally {
                verifying.value = false
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
            verifying.value = true
            try {
                store.save(normalized.removeSuffix("/rest/"), username.trim(), password)
                val mine = store.state.value
                try {
                    val r = api.ping().response
                    if (r.status == "ok") Result.Ok(Unit)
                    else Result.Err(SubsonicAuth.userMessage(r.error?.code, r.error?.message))
                } catch (e: Exception) {
                    Result.Err(SubsonicAuth.userMessage(null, null, e), e)
                } finally {
                    restoreIfMine(previous, mine)
                }
            } finally {
                verifying.value = false
            }
        }

    /**
     * Undoes this call's save, but only when nobody saved after us: an
     * overlapping newer login/probe owns the store now and must be left alone.
     */
    private fun restoreIfMine(previous: Credentials?, mine: Credentials?) {
        if (store.state.value != mine) return
        if (previous == null) store.clear()
        else store.save(previous.serverUrl, previous.username, previous.password)
    }

    /** Wipes credentials immediately; navigation reacts via [loginState]. */
    fun logout() = store.clear()

    /**
     * Updates just the server URL (Account settings). The password is kept, so
     * the user can point the app at a different host without re-entering it;
     * a failing URL simply makes later calls error, and "Test connection" tells
     * them immediately.
     */
    suspend fun updateServerUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = store.state.value ?: return@withContext Result.Err("Not logged in")
        val normalized = try {
            SubsonicAuth.normalizeServerUrl(url)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.Err("Invalid server URL.")
        }
        store.save(normalized.removeSuffix("/rest/"), current.username, current.password)
        Result.Ok(Unit)
    }
}
