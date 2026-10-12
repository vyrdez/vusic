package com.feishin.mobile.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "feishin_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val wifiBitrate = intPreferencesKey("wifi_bitrate")
        val mobileBitrate = intPreferencesKey("mobile_bitrate")
        val wifiFormat = stringPreferencesKey("wifi_format")
        val mobileFormat = stringPreferencesKey("mobile_format")
        val allowSelfSigned = booleanPreferencesKey("allow_self_signed")
        val cacheSizeMb = intPreferencesKey("cache_size_mb")
        val crossfade = booleanPreferencesKey("crossfade")
        val replayGain = booleanPreferencesKey("replay_gain")
        val gapless = booleanPreferencesKey("gapless")
    }

    data class Settings(
        val theme: ThemeMode = ThemeMode.DARK,
        val wifiBitrate: Int = 0,
        val mobileBitrate: Int = 128,
        val wifiFormat: String = "raw",
        val mobileFormat: String = "mp3",
        val allowSelfSigned: Boolean = false,
        val cacheSizeMb: Int = 512,
        val crossfade: Boolean = false,
        val replayGain: Boolean = true,
        val gapless: Boolean = true,
    )

    val flow: Flow<Settings> = context.settingsDataStore.data.map { p ->
        Settings(
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            wifiBitrate = p[K.wifiBitrate] ?: 0,
            mobileBitrate = p[K.mobileBitrate] ?: 128,
            wifiFormat = p[K.wifiFormat] ?: "raw",
            mobileFormat = p[K.mobileFormat] ?: "mp3",
            allowSelfSigned = p[K.allowSelfSigned] ?: false,
            cacheSizeMb = p[K.cacheSizeMb] ?: 512,
            crossfade = p[K.crossfade] ?: false,
            replayGain = p[K.replayGain] ?: true,
            gapless = p[K.gapless] ?: true,
        )
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setWifi(bitrate: Int, format: String) = edit { it[K.wifiBitrate] = bitrate; it[K.wifiFormat] = format }
    suspend fun setMobile(bitrate: Int, format: String) = edit { it[K.mobileBitrate] = bitrate; it[K.mobileFormat] = format }
    suspend fun setAllowSelfSigned(v: Boolean) = edit { it[K.allowSelfSigned] = v }
    suspend fun setCacheSizeMb(v: Int) = edit { it[K.cacheSizeMb] = v }
    suspend fun setCrossfade(v: Boolean) = edit { it[K.crossfade] = v }
    suspend fun setReplayGain(v: Boolean) = edit { it[K.replayGain] = v }
    suspend fun setGapless(v: Boolean) = edit { it[K.gapless] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
