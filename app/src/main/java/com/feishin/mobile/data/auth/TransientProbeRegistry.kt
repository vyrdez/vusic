package com.feishin.mobile.data.auth

import com.feishin.mobile.domain.model.ServerProfile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Holds in-flight pre-persistence login/probe [ServerProfile] candidates keyed by
 * `"__probe__:<uuid>"` so auth and TLS interceptors can resolve them before
 * anything is persisted to [ProfileStore].
 */
@Singleton
class TransientProbeRegistry @Inject constructor() {
    private val profiles = ConcurrentHashMap<String, ServerProfile>()

    fun register(profile: ServerProfile): String {
        val id = "$PROBE_PREFIX${UUID.randomUUID()}"
        profiles[id] = profile.copy(id = id)
        return id
    }

    fun get(id: String): ServerProfile? = profiles[id]

    fun findByHost(host: String, port: Int): ServerProfile? {
        if (host.isBlank()) return null
        return profiles.values.firstOrNull { profile ->
            val parsed = profile.url.toHttpUrlOrNull() ?: return@firstOrNull false
            parsed.host.equals(host, ignoreCase = true) && (port <= 0 || parsed.port == port)
        }
    }

    fun remove(id: String) {
        profiles.remove(id)
    }

    companion object {
        const val PROBE_PREFIX = "__probe__:"
    }
}
