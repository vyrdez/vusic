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

enum class ThemeMode { SYSTEM, DARK, AMOLED, LIGHT }

/** Grid or list layout for album/artist collections. */
enum class LayoutMode { GRID, LIST }

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
        val allowCleartext = booleanPreferencesKey("allow_cleartext")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
        val cacheSizeMb = intPreferencesKey("cache_size_mb")
        val imageCacheMb = intPreferencesKey("image_cache_mb")
        val crossfade = booleanPreferencesKey("crossfade")
        val crossfadeSeconds = intPreferencesKey("crossfade_seconds")
        val replayGain = booleanPreferencesKey("replay_gain")
        val replayGainMode = stringPreferencesKey("replay_gain_mode")
        val replayGainPreampDb = intPreferencesKey("replay_gain_preamp")
        val replayGainFallbackDb = intPreferencesKey("replay_gain_fallback")
        val gapless = booleanPreferencesKey("gapless")
        val pauseOnHeadphone = booleanPreferencesKey("pause_on_headphone")
        val resumeOnFocusGain = booleanPreferencesKey("resume_on_focus_gain")
        val pins = stringSetPreferencesKey("speed_dial_pins")
        val recentSearches = stringPreferencesKey("recent_searches")
        // M5 additions
        val offlineMode = booleanPreferencesKey("offline_mode")
        val downloadWifiOnly = booleanPreferencesKey("download_wifi_only")
        val downloadOriginal = booleanPreferencesKey("download_original")
        val downloadBitrate = intPreferencesKey("download_bitrate")
        val downloadFormat = stringPreferencesKey("download_format")
        val dynamicNpColor = booleanPreferencesKey("dynamic_np_color")
        val defaultLayout = stringPreferencesKey("default_layout")
    }

    data class Settings(
        val theme: ThemeMode = ThemeMode.DARK,
        val wifiBitrate: Int = 0,
        val mobileBitrate: Int = 128,
        val wifiFormat: String = "raw",
        val mobileFormat: String = "mp3",
        val allowSelfSigned: Boolean = false,
        val allowCleartext: Boolean = true,
        val timeoutSeconds: Int = 20,
        val cacheSizeMb: Int = 512,
        val imageCacheMb: Int = 250,
        val crossfade: Boolean = false,
        val crossfadeSeconds: Int = 6,
        val replayGain: Boolean = true,
        val replayGainMode: com.feishin.mobile.core.common.ReplayGainMode =
            com.feishin.mobile.core.common.ReplayGainMode.TRACK,
        val replayGainPreampDb: Int = 0,
        val replayGainFallbackDb: Int = 0,
        val gapless: Boolean = true,
        val pauseOnHeadphone: Boolean = true,
        val resumeOnFocusGain: Boolean = true,
        val pins: Set<String> = emptySet(),
        val recentSearches: List<String> = emptyList(),
        val offlineMode: Boolean = false,
        val downloadWifiOnly: Boolean = true,
        val downloadOriginal: Boolean = true,
        val downloadBitrate: Int = 320,
        val downloadFormat: String = "raw",
        val dynamicNpColor: Boolean = true,
        val defaultLayout: LayoutMode = LayoutMode.GRID,
    ) {
        /** Effective ReplayGain mode: the master switch overrides the picker. */
        val effectiveReplayGain: com.feishin.mobile.core.common.ReplayGainMode
            get() = if (!replayGain) com.feishin.mobile.core.common.ReplayGainMode.OFF else replayGainMode
    }

    val flow: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            wifiBitrate = p[K.wifiBitrate] ?: 0,
            mobileBitrate = p[K.mobileBitrate] ?: 128,
            wifiFormat = p[K.wifiFormat] ?: "raw",
            mobileFormat = p[K.mobileFormat] ?: "mp3",
            allowSelfSigned = p[K.allowSelfSigned] ?: false,
            allowCleartext = p[K.allowCleartext] ?: true,
            timeoutSeconds = p[K.timeoutSeconds] ?: 20,
            cacheSizeMb = com.feishin.mobile.core.common.CacheSizePolicy.normalizeStream(p[K.cacheSizeMb] ?: 512),
            imageCacheMb = com.feishin.mobile.core.common.CacheSizePolicy.normalizeImage(p[K.imageCacheMb] ?: 250),
            crossfade = p[K.crossfade] ?: false,
            crossfadeSeconds = p[K.crossfadeSeconds] ?: 6,
            replayGain = p[K.replayGain] ?: true,
            replayGainMode = runCatching {
                com.feishin.mobile.core.common.ReplayGainMode.valueOf(p[K.replayGainMode] ?: "TRACK")
            }.getOrDefault(com.feishin.mobile.core.common.ReplayGainMode.TRACK),
            replayGainPreampDb = p[K.replayGainPreampDb] ?: 0,
            replayGainFallbackDb = p[K.replayGainFallbackDb] ?: 0,
            gapless = p[K.gapless] ?: true,
            pauseOnHeadphone = p[K.pauseOnHeadphone] ?: true,
            resumeOnFocusGain = p[K.resumeOnFocusGain] ?: true,
            pins = p[K.pins].orEmpty(),
            recentSearches = p[K.recentSearches]?.split(RECENT_SEP)?.filter { it.isNotBlank() } ?: emptyList(),
            offlineMode = p[K.offlineMode] ?: false,
            downloadWifiOnly = p[K.downloadWifiOnly] ?: true,
            downloadOriginal = p[K.downloadOriginal] ?: true,
            downloadBitrate = p[K.downloadBitrate] ?: 320,
            downloadFormat = p[K.downloadFormat] ?: "raw",
            dynamicNpColor = p[K.dynamicNpColor] ?: true,
            defaultLayout = runCatching { LayoutMode.valueOf(p[K.defaultLayout] ?: "GRID") }
                .getOrDefault(LayoutMode.GRID),
        )
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setWifi(bitrate: Int, format: String) = edit { it[K.wifiBitrate] = bitrate; it[K.wifiFormat] = format }
    suspend fun setMobile(bitrate: Int, format: String) = edit { it[K.mobileBitrate] = bitrate; it[K.mobileFormat] = format }
    suspend fun setAllowSelfSigned(v: Boolean) = edit { it[K.allowSelfSigned] = v }
    suspend fun setAllowCleartext(v: Boolean) = edit { it[K.allowCleartext] = v }
    suspend fun setTimeout(v: Int) = edit { it[K.timeoutSeconds] = v.coerceIn(5, 120) }
    suspend fun setCacheSizeMb(v: Int) = edit { it[K.cacheSizeMb] = v }
    suspend fun setImageCacheMb(v: Int) = edit { it[K.imageCacheMb] = v }
    suspend fun setCrossfade(v: Boolean) = edit { it[K.crossfade] = v }
    suspend fun setCrossfadeSeconds(v: Int) = edit { it[K.crossfadeSeconds] = v.coerceIn(1, 12) }
    suspend fun setReplayGain(v: Boolean) = edit { it[K.replayGain] = v }
    suspend fun setReplayGainMode(v: com.feishin.mobile.core.common.ReplayGainMode) = edit { it[K.replayGainMode] = v.name }
    suspend fun setReplayGainPreamp(v: Int) = edit { it[K.replayGainPreampDb] = v.coerceIn(-15, 15) }
    suspend fun setReplayGainFallback(v: Int) = edit { it[K.replayGainFallbackDb] = v.coerceIn(-15, 15) }
    suspend fun setGapless(v: Boolean) = edit { it[K.gapless] = v }
    suspend fun setPauseOnHeadphone(v: Boolean) = edit { it[K.pauseOnHeadphone] = v }
    suspend fun setResumeOnFocusGain(v: Boolean) = edit { it[K.resumeOnFocusGain] = v }
    suspend fun setOfflineMode(v: Boolean) = edit { it[K.offlineMode] = v }
    suspend fun setDownloadWifiOnly(v: Boolean) = edit { it[K.downloadWifiOnly] = v }
    suspend fun setDownloadOriginal(v: Boolean) = edit { it[K.downloadOriginal] = v }
    suspend fun setDownloadBitrate(v: Int) = edit { it[K.downloadBitrate] = v }
    suspend fun setDownloadFormat(v: String) = edit { it[K.downloadFormat] = v }
    suspend fun setDynamicNpColor(v: Boolean) = edit { it[K.dynamicNpColor] = v }
    suspend fun setDefaultLayout(v: LayoutMode) = edit { it[K.defaultLayout] = v.name }

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

    suspend fun settingsNow(): Settings = flow.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }
}
