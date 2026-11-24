package com.feishin.mobile.data.remote

import android.annotation.SuppressLint
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.internal.tls.OkHostnameVerifier

/**
 * Per-host/port TLS trust manager replacing the global `DynamicTrustManager`.
 *
 * Self-signed certificate and hostname verification bypass is granted ONLY when
 * an active transient probe in [TransientProbeRegistry] or a configured profile in
 * [ProfileStore] matches the exact `(hostname, port)` and has `allowSelfSigned == true`
 * (never for `GOOGLE_DRIVE` and never via a global fallback boolean).
 */
@SuppressLint("CustomX509TrustManager")
@Singleton
class ProfileTrustManager internal constructor(
    private val profileStore: ProfileStore,
    private val probeRegistry: TransientProbeRegistry,
    private val systemTrustManagerOverride: X509TrustManager? = null,
) : X509ExtendedTrustManager() {

    @Inject
    constructor(
        profileStore: ProfileStore,
        probeRegistry: TransientProbeRegistry,
        @Suppress("UNUSED_PARAMETER")
        settingsStore: SettingsStore,
    ) : this(
        profileStore = profileStore,
        probeRegistry = probeRegistry,
        systemTrustManagerOverride = null,
    )

    private val defaultManager: X509TrustManager by lazy {
        systemTrustManagerOverride ?: loadSystemTrustManager()
    }

    fun isSelfSignedAllowed(hostname: String?, port: Int?): Boolean {
        if (hostname.isNullOrBlank()) return false
        val targetHost = hostname.trim()
        val targetPort = port?.takeIf { it > 0 }

        val probe = probeRegistry.findByHost(targetHost, targetPort ?: -1)
        if (probe != null && probe.allowSelfSigned && probe.type != BackendType.GOOGLE_DRIVE) {
            return true
        }

        return profileStore.profiles.value.any { profile ->
            if (!profile.allowSelfSigned || profile.type == BackendType.GOOGLE_DRIVE) {
                return@any false
            }
            val parsed = profile.url.toHttpUrlOrNull() ?: return@any false
            parsed.host.equals(targetHost, ignoreCase = true) &&
                (targetPort == null || parsed.port == targetPort)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultManager.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        val tm = defaultManager
        if (tm is X509ExtendedTrustManager) {
            tm.checkClientTrusted(chain, authType, socket)
        } else {
            tm.checkClientTrusted(chain, authType)
        }
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        val tm = defaultManager
        if (tm is X509ExtendedTrustManager) {
            tm.checkClientTrusted(chain, authType, engine)
        } else {
            tm.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Fail-closed when no peer host/port context is available.
        defaultManager.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        val sslSocket = socket as? SSLSocket
        val host = sslSocket?.handshakeSession?.peerHost?.takeIf { it.isNotBlank() }
            ?: socket?.inetAddress?.hostName?.takeIf { it.isNotBlank() }
        val port = sslSocket?.handshakeSession?.peerPort?.takeIf { it > 0 }
            ?: socket?.port?.takeIf { it > 0 }
        if (isSelfSignedAllowed(host, port)) return

        val tm = defaultManager
        if (tm is X509ExtendedTrustManager) {
            tm.checkServerTrusted(chain, authType, socket)
        } else {
            tm.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        val session = engine?.handshakeSession
        val host = session?.peerHost?.takeIf { it.isNotBlank() }
            ?: engine?.peerHost?.takeIf { it.isNotBlank() }
        val port = session?.peerPort?.takeIf { it > 0 }
            ?: engine?.peerPort?.takeIf { it > 0 }
        if (isSelfSignedAllowed(host, port)) return

        val tm = defaultManager
        if (tm is X509ExtendedTrustManager) {
            tm.checkServerTrusted(chain, authType, engine)
        } else {
            tm.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultManager.acceptedIssuers

    fun sslContext(): SSLContext {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(this), SecureRandom())
        return ctx
    }

    fun hostnameVerifier(): HostnameVerifier = HostnameVerifier { hostname, session ->
        val port = session?.peerPort?.takeIf { it > 0 }
        if (isSelfSignedAllowed(hostname, port)) {
            true
        } else {
            OkHostnameVerifier.verify(hostname, session)
        }
    }

    private fun loadSystemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
