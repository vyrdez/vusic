package com.feishin.mobile.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.feishin.mobile.core.common.NetworkClass
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live connectivity state. Screens use [isOnline] to show the offline banner and
 * to skip doomed network calls quietly; the downloader uses [networkClass] to
 * honour "download over Wi-Fi only".
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(computeOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkClass = MutableStateFlow(computeClass())
    val networkClass: StateFlow<NetworkClass> = _networkClass.asStateFlow()

    /**
     * True when the active connection is NOT metered.
     *
     * This is the real `NET_CAPABILITY_NOT_METERED` capability, NOT "the
     * transport is not cellular": a Wi-Fi hotspot that Android marks as metered
     * is metered, and must be treated as mobile data.
     */
    private val _unmetered = MutableStateFlow(computeUnmetered())
    val unmetered: StateFlow<Boolean> = _unmetered.asStateFlow()

    /**
     * True when the active connection is roaming. Android reports this cheaply
     * (`NET_CAPABILITY_NOT_ROAMING`), and the app never spends mobile data
     * while roaming.
     */
    private val _roaming = MutableStateFlow(computeRoaming())
    val roaming: StateFlow<Boolean> = _roaming.asStateFlow()

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update()
                override fun onLost(network: Network) = update()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
            })
        }
    }

    private fun update() {
        _isOnline.value = computeOnline()
        _networkClass.value = computeClass()
        _unmetered.value = computeUnmetered()
        _roaming.value = computeRoaming()
    }

    private fun computeOnline(): Boolean {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun computeClass(): NetworkClass {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return NetworkClass.OTHER
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkClass.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkClass.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkClass.OTHER
            else -> NetworkClass.OTHER
        }
    }

    /**
     * The real metered check. A Wi-Fi hotspot Android marks as metered is
     * metered; an Ethernet link can be metered too.
     */
    private fun computeUnmetered(): Boolean {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun computeRoaming(): Boolean {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    }

    /** True when the current connection is unmetered (Wi-Fi / Ethernet). */
    fun isUnmetered(): Boolean = _unmetered.value
}
