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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "feishin_settings")

// AMOLED was removed in M6.7e: dark is the default dark theme now. A stored"
// AMOLED value migrates to DARK (see migrateStoredTheme in ui.theme).
enum class ThemeMode { SYSTEM, DARK, LIGHT }

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

    /**
     * Now Playing tab (Song vs Lyrics), session-only. A process restart drops
     * it, so a cold launch always starts on Song (P10.1 D2). MainActivity also
     * clears it after five minutes in the background.
     */
    private val lyricsModeSession = kotlinx.coroutines.flow.MutableStateFlow(false)
    val lyricsModeState: kotlinx.coroutines.flow.StateFlow<Boolean> = lyricsModeSession
    val nowPlayingLyricsMode: Boolean get() = lyricsModeSession.value

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
        val refreshRateMode = stringPreferencesKey("refresh_rate_mode")
        val defaultLayout = stringPreferencesKey("default_layout")
        // M5.5 additions
        val showOfflineChip = booleanPreferencesKey("show_offline_chip")
        val autoOffline = booleanPreferencesKey("auto_offline")
        val radioBatchSize = intPreferencesKey("radio_batch_size")
        val cacheUpcoming = booleanPreferencesKey("cache_upcoming")
        val tracksToCache = intPreferencesKey("tracks_to_cache")
        val cachePlayedSongs = booleanPreferencesKey("cache_played_songs")
        val cacheWifiOnly = booleanPreferencesKey("cache_wifi_only")
        val maxDownloadMb = intPreferencesKey("max_download_mb")
        // Lyrics settings (P9-A)
        val lyricsFetchOnline = booleanPreferencesKey("lyrics_fetch_online")
        val lyricsSourcePriority = stringPreferencesKey("lyrics_source_priority")
        val lyricsAlignment = stringPreferencesKey("lyrics_alignment")
        val lyricsTextSize = stringPreferencesKey("lyrics_text_size")
        val lyricsAutoScroll = booleanPreferencesKey("lyrics_auto_scroll")
        val lyricsTapToSeek = booleanPreferencesKey("lyrics_tap_to_seek")
        val lyricsWordHighlight = booleanPreferencesKey("lyrics_word_highlight")
        val lyricsKeepScreenOn = booleanPreferencesKey("lyrics_keep_screen_on")
        val lyricsSyncOffsetMs = intPreferencesKey("lyrics_sync_offset_ms")
        val lyricsShowUnsynced = booleanPreferencesKey("lyrics_show_unsynced")
        val lyricsProviderOrder = stringPreferencesKey("lyrics_provider_order")
        val lyricsProviderOrderMigrated92 = booleanPreferencesKey("lyrics_provider_order_migrated_92")
        val lyricsProviderEnabled = stringPreferencesKey("lyrics_provider_enabled")
        val lyricsPreferWordByWord = booleanPreferencesKey("lyrics_prefer_word_by_word")
        val lyricsRespectAgentPositioning = booleanPreferencesKey("lyrics_respect_agent_positioning")
        val lyricsShowIntervalIndicator = booleanPreferencesKey("lyrics_show_interval_indicator")
        val lyricsRomanize = booleanPreferencesKey("lyrics_romanize")
        val lyricsRomanizeAutomatically = booleanPreferencesKey("lyrics_romanize_automatically")
        val lyricsRomanizedTrackIds = stringPreferencesKey("lyrics_romanized_track_ids")
        val lyricsDeRomanizedTrackIds = stringPreferencesKey("lyrics_deromanized_track_ids")
        val lyricsLineGapPercent = intPreferencesKey("lyrics_line_gap_percent")
        // now_playing_lyrics_mode is intentionally no longer persisted: the tab
        // must start on Song on cold launch (P10.1 D2). See lyricsModeSession.
        // Device files settings (P9-B)
        val showDeviceFiles = booleanPreferencesKey("show_device_files")
        val deviceFilesMinDurationSec = intPreferencesKey("device_files_min_duration_sec")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val maxConcurrentDownloads = intPreferencesKey("max_concurrent_downloads")
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
        /** High-refresh-rate request: AUTO (default), MAX, STANDARD (60 Hz). */
        val refreshRateMode: com.feishin.mobile.core.ui.RefreshRateMode =
            com.feishin.mobile.core.ui.RefreshRateMode.AUTO,
        val defaultLayout: LayoutMode = LayoutMode.GRID,
        val showOfflineChip: Boolean = true,
        val autoOffline: Boolean = false,
        /** Songs per generated radio/mix batch. */
        val radioBatchSize: Int = 20,
        val cacheUpcoming: Boolean = true,
        /** Upcoming tracks to keep fully buffered (1..5). */
        val tracksToCache: Int = 2,
        val cachePlayedSongs: Boolean = false,
        val cacheWifiOnly: Boolean = true,
        /** Download storage cap in MB. 0 = unlimited. */
        val maxDownloadMb: Int = 0,
        // Lyrics settings (P9-A)
        val lyricsFetchOnline: Boolean = true,
        val lyricsSourcePriority: String = "ONLINE_FIRST",
        val lyricsAlignment: String = "CENTER",
        val lyricsTextSize: String = "100%",
        val lyricsAutoScroll: Boolean = true,
        val lyricsTapToSeek: Boolean = true,
        val lyricsWordHighlight: Boolean = false,
        val lyricsKeepScreenOn: Boolean = false,
        val lyricsSyncOffsetMs: Int = 0,
        val lyricsShowUnsynced: Boolean = true,
        val lyricsProviderOrder: List<String> = DEFAULT_LYRICS_PROVIDER_ORDER,
        val lyricsProviderEnabled: Set<String> = DEFAULT_LYRICS_PROVIDER_ENABLED,
        val lyricsPreferWordByWord: Boolean = false,
        val lyricsRespectAgentPositioning: Boolean = false,
        val lyricsShowIntervalIndicator: Boolean = true,
        val lyricsRomanize: Boolean = false,
        val lyricsLineGapPercent: Int = 100,
        val nowPlayingLyricsMode: Boolean = false,
        // Device files settings (P9-B)
        val showDeviceFiles: Boolean = false,
        val deviceFilesMinDurationSec: Int = 30,
        val libraryViewMode: String = "SERVER",
        val maxConcurrentDownloads: Int = 2,
        /** Per-tab sort choices, keyed by tab name. */
        val librarySorts: Map<String, com.feishin.mobile.core.common.SortChoice> = emptyMap(),
        /** Per-tab grid/list layout, keyed by tab name. */
        val libraryLayouts: Map<String, LayoutMode> = emptyMap(),
        /** Per-playlist track order, keyed by playlist id. Never sent to the server. */
        val playlistSorts: Map<String, com.feishin.mobile.core.common.PlaylistSort> = emptyMap(),
    ) {
        /** Effective ReplayGain mode: the master switch overrides the picker. */
        val effectiveReplayGain: com.feishin.mobile.core.common.ReplayGainMode
            get() = if (!replayGain) com.feishin.mobile.core.common.ReplayGainMode.OFF else replayGainMode
    }

    val flow: Flow<Settings> = combine(store.data, lyricsModeSession) { p, lyricsMode ->
        Settings(
            theme = com.feishin.mobile.ui.theme.migrateStoredTheme(p[K.theme]),
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
            refreshRateMode = runCatching {
                com.feishin.mobile.core.ui.RefreshRateMode.valueOf(
                    p[K.refreshRateMode]
                        ?: com.feishin.mobile.core.ui.RefreshRateMode.AUTO.name,
                )
            }.getOrDefault(com.feishin.mobile.core.ui.RefreshRateMode.AUTO),
            defaultLayout = runCatching { LayoutMode.valueOf(p[K.defaultLayout] ?: "GRID") }
                .getOrDefault(LayoutMode.GRID),
            showOfflineChip = p[K.showOfflineChip] ?: true,
            autoOffline = p[K.autoOffline] ?: false,
            radioBatchSize = (p[K.radioBatchSize] ?: 20).coerceIn(10, 50),
            cacheUpcoming = p[K.cacheUpcoming] ?: true,
            tracksToCache = (p[K.tracksToCache] ?: 2).coerceIn(1, 5),
            cachePlayedSongs = p[K.cachePlayedSongs] ?: false,
            cacheWifiOnly = p[K.cacheWifiOnly] ?: true,
            maxDownloadMb = (p[K.maxDownloadMb] ?: 0).coerceAtLeast(0),
            lyricsFetchOnline = p[K.lyricsFetchOnline] ?: true,
            lyricsSourcePriority = p[K.lyricsSourcePriority] ?: "ONLINE_FIRST",
            lyricsAlignment = p[K.lyricsAlignment] ?: "CENTER",
            lyricsTextSize = parseLyricsTextSizePercent(p[K.lyricsTextSize] ?: "100%").let { "${it}%" },
            lyricsAutoScroll = p[K.lyricsAutoScroll] ?: true,
            lyricsTapToSeek = p[K.lyricsTapToSeek] ?: true,
            lyricsWordHighlight = false,
            lyricsKeepScreenOn = p[K.lyricsKeepScreenOn] ?: false,
            lyricsSyncOffsetMs = (p[K.lyricsSyncOffsetMs] ?: 0).coerceIn(-2000, 2000),
            lyricsShowUnsynced = p[K.lyricsShowUnsynced] ?: true,
            lyricsProviderOrder = if (p[K.lyricsProviderOrderMigrated92] == true) {
                p[K.lyricsProviderOrder]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: DEFAULT_LYRICS_PROVIDER_ORDER
            } else {
                DEFAULT_LYRICS_PROVIDER_ORDER
            },
            lyricsProviderEnabled = p[K.lyricsProviderEnabled]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: DEFAULT_LYRICS_PROVIDER_ENABLED,
            lyricsPreferWordByWord = false,
            lyricsRespectAgentPositioning = p[K.lyricsRespectAgentPositioning] ?: false,
            lyricsShowIntervalIndicator = p[K.lyricsShowIntervalIndicator] ?: true,
            lyricsRomanize = p[K.lyricsRomanize] ?: run {
                val auto = p[K.lyricsRomanizeAutomatically] ?: false
                val trackIds = !p[K.lyricsRomanizedTrackIds].isNullOrBlank()
                auto || trackIds
            },
            lyricsLineGapPercent = parseLyricsLineGapPercent(p[K.lyricsLineGapPercent]),
            nowPlayingLyricsMode = lyricsMode,
            showDeviceFiles = p[K.showDeviceFiles] ?: false,
            deviceFilesMinDurationSec = p[K.deviceFilesMinDurationSec] ?: 30,
            libraryViewMode = p[K.libraryViewMode] ?: "SERVER",
            maxConcurrentDownloads = (p[K.maxConcurrentDownloads] ?: 2).coerceIn(1, 5),
            librarySorts = p.asMap()
                .filterKeys { it.name.startsWith(SORT_PREFIX) }
                .mapNotNull { (k, v) ->
                    val tab = k.name.removePrefix(SORT_PREFIX)
                    (v as? String)?.let { raw ->
                        tab to com.feishin.mobile.core.common.SortChoice.parse(
                            raw, com.feishin.mobile.core.common.LibrarySorts.default(tab).option,
                        )
                    }
                }.toMap(),
            libraryLayouts = p.asMap()
                .filterKeys { it.name.startsWith(LAYOUT_PREFIX) }
                .mapNotNull { (k, v) ->
                    val tab = k.name.removePrefix(LAYOUT_PREFIX)
                    runCatching { LayoutMode.valueOf(v as String) }.getOrNull()?.let { tab to it }
                }.toMap(),
            playlistSorts = p.asMap()
                .filterKeys { it.name.startsWith(PLAYLIST_SORT_PREFIX) }
                .mapNotNull { (k, v) ->
                    val key = k.name.removePrefix(PLAYLIST_SORT_PREFIX)
                    (v as? String)?.let { raw -> key to com.feishin.mobile.core.common.PlaylistSort.parse(raw) }
                }.toMap(),
        )
    }

    companion object {
        const val SORT_PREFIX = "library_sort_"
        const val LAYOUT_PREFIX = "library_layout_"
        const val PLAYLIST_SORT_PREFIX = "playlist_sort_"
        val RADIO_BATCH_OPTIONS = listOf(10, 20, 30, 50)
        val DEFAULT_LYRICS_PROVIDER_ORDER = listOf("BETTER_LYRICS", "LRCLIB", "PAXSENIX", "KUGOU", "LOCAL_DATA")
        val DEFAULT_LYRICS_PROVIDER_ENABLED = setOf("LRCLIB", "BETTER_LYRICS", "KUGOU", "LOCAL_DATA")

        fun parseLyricsTextSizePercent(raw: String): Int {
            val clean = raw.trim().removeSuffix("%")
            val num = clean.toIntOrNull()
            if (num != null) return num.coerceIn(80, 220)
            return when (raw.uppercase()) {
                "SMALL" -> 85
                "LARGE" -> 125
                else -> 100
            }
        }

        fun parseLyricsLineGapPercent(raw: Int?): Int = (raw ?: 100).coerceIn(50, 200)

        fun isTrackRomanized(trackId: String, hasCjk: Boolean, settings: Settings): Boolean {
            return settings.lyricsRomanize && hasCjk
        }
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
    suspend fun setRefreshRateMode(v: com.feishin.mobile.core.ui.RefreshRateMode) =
        edit { it[K.refreshRateMode] = v.name }
    suspend fun setDefaultLayout(v: LayoutMode) = edit { it[K.defaultLayout] = v.name }
    suspend fun setShowOfflineChip(v: Boolean) = edit { it[K.showOfflineChip] = v }
    suspend fun setAutoOffline(v: Boolean) = edit { it[K.autoOffline] = v }
    suspend fun setRadioBatchSize(v: Int) = edit { it[K.radioBatchSize] = v.coerceIn(10, 50) }
    suspend fun setCacheUpcoming(v: Boolean) = edit { it[K.cacheUpcoming] = v }
    suspend fun setTracksToCache(v: Int) = edit { it[K.tracksToCache] = v.coerceIn(1, 5) }
    suspend fun setCachePlayedSongs(v: Boolean) = edit { it[K.cachePlayedSongs] = v }
    suspend fun setCacheWifiOnly(v: Boolean) = edit { it[K.cacheWifiOnly] = v }
    suspend fun setMaxConcurrentDownloads(v: Int) = edit { it[K.maxConcurrentDownloads] = v.coerceIn(1, 5) }
    suspend fun setMaxDownloadMb(v: Int) = edit { it[K.maxDownloadMb] = v.coerceAtLeast(0) }
    // Lyrics setters (P9-A)
    suspend fun setLyricsFetchOnline(v: Boolean) = edit { it[K.lyricsFetchOnline] = v }
    suspend fun setLyricsSourcePriority(v: String) = edit { it[K.lyricsSourcePriority] = v }
    suspend fun setLyricsAlignment(v: String) = edit { it[K.lyricsAlignment] = v }
    suspend fun setLyricsTextSize(v: String) = edit { it[K.lyricsTextSize] = v }
    suspend fun setLyricsAutoScroll(v: Boolean) = edit { it[K.lyricsAutoScroll] = v }
    suspend fun setLyricsTapToSeek(v: Boolean) = edit { it[K.lyricsTapToSeek] = v }
    suspend fun setLyricsWordHighlight(v: Boolean) = Unit
    suspend fun setLyricsKeepScreenOn(v: Boolean) = edit { it[K.lyricsKeepScreenOn] = v }
    suspend fun setLyricsSyncOffsetMs(v: Int) = edit { it[K.lyricsSyncOffsetMs] = v.coerceIn(-2000, 2000) }
    suspend fun setLyricsShowUnsynced(v: Boolean) = edit { it[K.lyricsShowUnsynced] = v }
    suspend fun setLyricsProviderOrder(order: List<String>) = edit {
        it[K.lyricsProviderOrder] = order.joinToString(",")
        it[K.lyricsProviderOrderMigrated92] = true
    }
    suspend fun setLyricsProviderEnabled(enabled: Set<String>) = edit { it[K.lyricsProviderEnabled] = enabled.joinToString(",") }
    suspend fun setLyricsPreferWordByWord(v: Boolean) = Unit
    suspend fun setLyricsRespectAgentPositioning(v: Boolean) = edit { it[K.lyricsRespectAgentPositioning] = v }
    suspend fun setLyricsShowIntervalIndicator(v: Boolean) = edit { it[K.lyricsShowIntervalIndicator] = v }
    /** Session-scoped: survives navigation and rotation, not process death. */
    suspend fun setNowPlayingLyricsMode(v: Boolean) { lyricsModeSession.value = v }
    suspend fun setLyricsLineGapPercent(v: Int) = edit { it[K.lyricsLineGapPercent] = v.coerceIn(50, 200) }
    suspend fun resetLyricsLineGap() = setLyricsLineGapPercent(100)
    suspend fun setLyricsRomanize(v: Boolean) = edit { it[K.lyricsRomanize] = v }
    suspend fun setLyricsRomanizeAutomatically(v: Boolean) = setLyricsRomanize(v)
    suspend fun resetLyricsProviders() = edit {
        it[K.lyricsProviderOrder] = DEFAULT_LYRICS_PROVIDER_ORDER.joinToString(",")
        it[K.lyricsProviderEnabled] = DEFAULT_LYRICS_PROVIDER_ENABLED.joinToString(",")
        it[K.lyricsProviderOrderMigrated92] = true
    }
    // Device files setters (P9-B)
    suspend fun setShowDeviceFiles(v: Boolean) = edit { it[K.showDeviceFiles] = v }
    suspend fun setDeviceFilesMinDurationSec(v: Int) = edit { it[K.deviceFilesMinDurationSec] = v }
    suspend fun setLibraryViewMode(v: String) = edit { it[K.libraryViewMode] = v }

    /** Per-tab sort choice, e.g. tab = "ALBUMS". */
    suspend fun setLibrarySort(tab: String, choice: com.feishin.mobile.core.common.SortChoice) =
        edit { it[stringPreferencesKey(SORT_PREFIX + tab)] = choice.serialize() }

    /** Per-tab grid/list layout, e.g. tab = "ALBUMS". */
    suspend fun setLibraryLayout(tab: String, mode: LayoutMode) =
        edit { it[stringPreferencesKey(LAYOUT_PREFIX + tab)] = mode.name }

    /** Per-playlist track order, e.g. playlist = "pl-123". View-only. */
    suspend fun setPlaylistSort(
        playlist: String,
        sort: com.feishin.mobile.core.common.PlaylistSort,
    ) = edit { it[stringPreferencesKey(PLAYLIST_SORT_PREFIX + playlist)] = sort.serialize() }

    fun playlistSortFlow(playlist: String): Flow<com.feishin.mobile.core.common.PlaylistSort> =
        flow.map { s -> s.playlistSorts[playlist] ?: com.feishin.mobile.core.common.PlaylistOrdering.DEFAULT }

    fun librarySortFlow(tab: String): Flow<com.feishin.mobile.core.common.SortChoice> =
        flow.map { s ->
            s.librarySorts[tab] ?: com.feishin.mobile.core.common.LibrarySorts.default(tab)
        }

    fun libraryLayoutFlow(tab: String): Flow<LayoutMode> =
        flow.map { s -> s.libraryLayouts[tab] ?: s.defaultLayout }

    /**
     * Resets the Sound & Playback section to defaults (used by the confirm
     * dialog). Connectivity/appearance/library/download prefs are untouched.
     */
    suspend fun resetPlaybackDefaults() = edit {
        it.remove(K.wifiBitrate); it.remove(K.wifiFormat)
        it.remove(K.mobileBitrate); it.remove(K.mobileFormat)
        it.remove(K.downloadBitrate); it.remove(K.downloadFormat); it.remove(K.downloadOriginal)
        it.remove(K.replayGain); it.remove(K.replayGainMode)
        it.remove(K.replayGainPreampDb); it.remove(K.replayGainFallbackDb)
        it.remove(K.gapless); it.remove(K.crossfade); it.remove(K.crossfadeSeconds)
        it.remove(K.cacheUpcoming); it.remove(K.tracksToCache); it.remove(K.cachePlayedSongs)
        it.remove(K.cacheWifiOnly)
        it.remove(K.pauseOnHeadphone); it.remove(K.resumeOnFocusGain)
    }

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

    suspend fun removeRecentSearch(query: String) {
        edit {
            val current = it[K.recentSearches]?.split(RECENT_SEP)?.filter { s -> s.isNotBlank() } ?: emptyList()
            it[K.recentSearches] = current.filter { s -> s != query }.joinToString(RECENT_SEP)
        }
    }

    suspend fun pinsNow(): Set<String> = flow.first().pins

    suspend fun settingsNow(): Settings = flow.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }
}
