package com.feishin.mobile.data.remote

import com.feishin.mobile.data.settings.SettingsStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Trust manager that honors the "allow self-signed" setting live, without restart.
 * When disabled, delegates to the platform default trust manager.
 */
@Singleton
class DynamicTrustManager @Inject constructor(settingsStore: SettingsStore) : X509TrustManager {
    @Volatile var allowSelfSigned: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultManager: X509TrustManager

    init {
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm(),
        )
        tmf.init(null as java.security.KeyStore?)
        defaultManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        scope.launch {
            settingsStore.flow.collectLatest { allowSelfSigned = it.allowSelfSigned }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!allowSelfSigned) defaultManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!allowSelfSigned) defaultManager.checkServerTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultManager.acceptedIssuers

    fun sslContext(): SSLContext {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(this), SecureRandom())
        return ctx
    }
}
