package com.feishin.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "feishin_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Speed-dial pin: "album:{id}" or "playlist:{id}". */
fun speedDialKey(type: String, id: String) = "$type:$id"

// Recent searches are joined with newlines; the search field is single-line so
// queries never contain one.
private const val RECENT_SEP = "\n"

@Singleton
class SettingsStore internal constructor(
    private val store: DataStore<Preferences>,
) {
    /** Hilt path: app-private DataStore file. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

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
        val pins = stringSetPreferencesKey("speed_dial_pins")
        val recentSearches = stringPreferencesKey("recent_searches")
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
        val pins: Set<String> = emptySet(),
        val recentSearches: List<String> = emptyList(),
    )

    val flow: Flow<Settings> = store.data.map { p ->
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
            pins = p[K.pins].orEmpty(),
            recentSearches = p[K.recentSearches]?.split(RECENT_SEP)?.filter { it.isNotBlank() } ?: emptyList(),
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

    suspend fun setPinned(key: String, pinned: Boolean) = edit {
        it[K.pins] = if (pinned) it[K.pins].orEmpty() + key else it[K.pins].orEmpty() - key
    }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim().replace(RECENT_SEP, " ")
        if (q.isBlank()) return
        edit {
            val current = it[K.recentSearches]?.split(RECENT_SEP)?.filter { s -> s.isNotBlank() } ?: emptyList()
            it[K.recentSearches] = ((listOf(q) + current.filter { s -> s != q }).take(20)).joinToString(RECENT_SEP)
        }
    }

    suspend fun clearRecentSearches() = edit { it.remove(K.recentSearches) }

    suspend fun pinsNow(): Set<String> = flow.first().pins

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }
}
