package com.feishin.mobile.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Credentials(val serverUrl: String, val username: String, val password: String)

@Singleton
class CredentialsStore @Inject constructor(@ApplicationContext context: Context) {
    // Lazy: EncryptedSharedPreferences creation does a Tink keyset disk read,
    // which must not happen in a constructor on the main thread (StrictMode).
    // preload() runs once on IO before the UI gates on ready; later touches
    // are memory-only. Application context: never leaks an Activity.
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "feishin_auth",
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow<Credentials?>(null)
    val state: StateFlow<Credentials?> = _state.asStateFlow()

    val isLoggedIn: Boolean get() = _state.value != null

    /** Disk read — call once from a background thread (see AuthRepository). */
    fun preload() {
        _state.value = read()
    }

    private fun read(): Credentials? {
        val url = prefs.getString("serverUrl", null)
        val user = prefs.getString("username", null)
        val pass = prefs.getString("password", null)
        return if (url.isNullOrBlank() || user.isNullOrBlank() || pass == null) null else Credentials(url, user, pass)
    }

    fun save(serverUrl: String, username: String, password: String) {
        prefs.edit()
            .putString("serverUrl", serverUrl)
            .putString("username", username)
            .putString("password", password)
            .apply()
        _state.value = Credentials(serverUrl, username, password)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = null
    }
}
