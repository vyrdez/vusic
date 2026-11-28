package com.feishin.mobile.data.auth

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Leaf singleton allowing [AuthRepository] to cancel active per-profile library/Drive scan jobs
 * on profile removal without introducing a Hilt dependency cycle back to `ScanCoordinator` or indexers.
 */
@Singleton
class DriveScanCancellationRegistry @Inject constructor() {
    private val listeners = ConcurrentHashMap<String, (String) -> Unit>()

    fun register(key: String, onCancelProfile: (String) -> Unit) {
        listeners[key] = onCancelProfile
    }

    fun unregister(key: String) {
        listeners.remove(key)
    }

    fun cancel(profileId: String) {
        listeners.values.forEach { callback ->
            runCatching { callback(profileId) }
        }
    }
}
