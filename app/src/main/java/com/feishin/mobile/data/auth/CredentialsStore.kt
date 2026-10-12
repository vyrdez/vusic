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
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "feishin_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Credentials?> = _state.asStateFlow()

    val isLoggedIn: Boolean get() = _state.value != null

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
