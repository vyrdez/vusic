package com.feishin.mobile.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ProfileAuthStatus {
    OK,
    SESSION_EXPIRED,
}

/**
 * Leaf singleton holding per-profile auth status (e.g. Jellyfin 401 -> [ProfileAuthStatus.SESSION_EXPIRED]).
 * Injected into both [com.feishin.mobile.data.remote.ProfileAuthInterceptor] and
 * [AuthRepository] / UI ViewModels without introducing a Hilt DI cycle.
 */
@Singleton
class ProfileAuthStateStore @Inject constructor() {
    private val _states = MutableStateFlow<Map<String, ProfileAuthStatus>>(emptyMap())
    val states: StateFlow<Map<String, ProfileAuthStatus>> = _states.asStateFlow()

    fun statusFor(profileId: String): ProfileAuthStatus =
        _states.value[profileId] ?: ProfileAuthStatus.OK

    fun markExpired(profileId: String) {
        if (profileId.isBlank()) return
        _states.update { current ->
            if (current[profileId] == ProfileAuthStatus.SESSION_EXPIRED) {
                current
            } else {
                current + (profileId to ProfileAuthStatus.SESSION_EXPIRED)
            }
        }
    }

    fun clear(profileId: String) {
        if (profileId.isBlank()) return
        _states.update { current ->
            if (profileId !in current) current else current - profileId
        }
    }

    fun clearAll() {
        _states.value = emptyMap()
    }
}
