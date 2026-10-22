package com.feishin.mobile.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.db.LyricsCacheDao
import com.feishin.mobile.data.db.LyricsCacheEntity
import com.feishin.mobile.data.lyrics.FileTagLyricsProvider
import com.feishin.mobile.data.lyrics.LrclibLyricsProvider
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class LyricsRepository @Inject constructor(
    private val cache: LyricsCacheDao,
    private val lrclib: LrclibLyricsProvider,
    private val navidrome: NavidromeLyricsProvider,
    private val fileTags: FileTagLyricsProvider,
    private val settingsStore: SettingsStore,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    /** 7-day TTL for negative cache entries ("nothing found"). */
    private val negativeTtlMs = 7 * 24 * 60 * 60 * 1000L

    /** Guard against multiple simultaneous lookups for the same song. */
    private val lookupLocks = ConcurrentHashMap<String, Mutex>()

    /** In-memory tracking of transient network failures within this app session. */
    private val sessionFailedKeys = ConcurrentHashMap.newKeySet<String>()

    fun cacheKeyForTrack(track: Track): String {
        return if (track.isLocal) {
            "local:${track.localUri}@${track.localModifiedSec}"
        } else {
            track.id
        }
    }

    suspend fun getLyrics(track: Track, bypassCache: Boolean = false): Result<Lyrics> = withContext(Dispatchers.IO) {
        val key = cacheKeyForTrack(track)
        val lock = lookupLocks.computeIfAbsent(key) { Mutex() }

        lock.withLock {
            val settings = settingsStore.flow.first()

            // 1. Check local cache unless explicitly bypassing
            if (!bypassCache) {
                val cachedEntry = runCatching { cache.getEntry(key) }.getOrNull()
                if (cachedEntry != null) {
                    val cachedLyrics = runCatching {
                        json.decodeFromString<Lyrics>(cachedEntry.json)
                    }.getOrNull()

                    if (cachedLyrics != null) {
                        if (cachedLyrics.isNone) {
                            val age = System.currentTimeMillis() - cachedEntry.updatedAt
                            if (age < negativeTtlMs) {
                                return@withContext Result.Ok(cachedLyrics.copy(source = LyricsSource.CACHE))
                            }
                            // Expired negative entry, fall through to re-query
                        } else {
                            return@withContext Result.Ok(cachedLyrics.copy(source = LyricsSource.CACHE))
                        }
                    }
                }
            }

            // Check if transient failure occurred this session and we aren't bypassing
            if (!bypassCache && sessionFailedKeys.contains(key)) {
                return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.CACHE))
            }

            val isOnlineAllowed = settings.lyricsFetchOnline &&
                !settings.offlineMode &&
                isNetworkAvailable()

            val metadataProvider = if (track.isLocal) fileTags else navidrome
            val priority = settings.lyricsSourcePriority // "ONLINE_FIRST" or "SONG_FILE_FIRST"

            var finalLyrics: Lyrics? = null
            var transientError: Result.Err? = null

            val durationMs = track.durationSec.toLong() * 1000L

            if (priority == "SONG_FILE_FIRST" || !isOnlineAllowed) {
                // 1. Try metadata provider first
                val metaResult = metadataProvider.lyricsForSong(
                    songId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = durationMs,
                    localUri = track.localUri,
                    localModifiedSec = track.localModifiedSec,
                )

                when (metaResult) {
                    is Result.Ok -> {
                        if (!metaResult.value.isNone) {
                            finalLyrics = metaResult.value
                        }
                    }
                    is Result.Err -> {
                        transientError = metaResult
                    }
                }

                // 2. If metadata had nothing and online is allowed, try LRCLIB
                if (finalLyrics == null && isOnlineAllowed) {
                    val lrclibResult = lrclib.lyricsForSong(
                        songId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = durationMs,
                        localUri = track.localUri,
                        localModifiedSec = track.localModifiedSec,
                    )
                    when (lrclibResult) {
                        is Result.Ok -> {
                            if (!lrclibResult.value.isNone) {
                                finalLyrics = lrclibResult.value
                            }
                        }
                        is Result.Err -> {
                            if (transientError == null) transientError = lrclibResult
                        }
                    }
                }
            } else {
                // "ONLINE_FIRST":
                // 1. Try LRCLIB
                val lrclibResult = lrclib.lyricsForSong(
                    songId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = durationMs,
                    localUri = track.localUri,
                    localModifiedSec = track.localModifiedSec,
                )

                var lrclibLyrics: Lyrics? = null
                when (lrclibResult) {
                    is Result.Ok -> {
                        if (!lrclibResult.value.isNone) {
                            lrclibLyrics = lrclibResult.value
                        }
                    }
                    is Result.Err -> {
                        transientError = lrclibResult
                    }
                }

                if (lrclibLyrics != null) {
                    // Upgrade rule: if LRCLIB returned PLAIN lyrics, check if metadata lyrics are SYNCED
                    if (lrclibLyrics.isPlain) {
                        val metaResult = metadataProvider.lyricsForSong(
                            songId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = durationMs,
                            localUri = track.localUri,
                            localModifiedSec = track.localModifiedSec,
                        )
                        if (metaResult is Result.Ok && metaResult.value.isSynced) {
                            finalLyrics = metaResult.value // Upgraded to synced metadata lyrics!
                        } else {
                            finalLyrics = lrclibLyrics
                        }
                    } else {
                        finalLyrics = lrclibLyrics
                    }
                } else {
                    // LRCLIB failed or returned NONE -> Fall back to metadata provider
                    val metaResult = metadataProvider.lyricsForSong(
                        songId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = durationMs,
                        localUri = track.localUri,
                        localModifiedSec = track.localModifiedSec,
                    )
                    when (metaResult) {
                        is Result.Ok -> {
                            if (!metaResult.value.isNone) {
                                finalLyrics = metaResult.value
                            }
                        }
                        is Result.Err -> {
                            if (transientError == null) transientError = metaResult
                        }
                    }
                }
            }

            // Persist & Return
            if (finalLyrics != null && !finalLyrics.isNone) {
                sessionFailedKeys.remove(key)
                runCatching {
                    val encoded = json.encodeToString(Lyrics.serializer(), finalLyrics)
                    cache.put(LyricsCacheEntity(key, encoded, System.currentTimeMillis()))
                }
                return@withContext Result.Ok(finalLyrics)
            } else if (transientError != null) {
                // Do NOT cache transient errors negatively in Room; remember in session guard
                sessionFailedKeys.add(key)
                return@withContext transientError
            } else {
                // Definitive nothing found: cache negative result with 7-day TTL
                val emptyLyrics = Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.CACHE)
                runCatching {
                    val encoded = json.encodeToString(Lyrics.serializer(), emptyLyrics)
                    cache.put(LyricsCacheEntity(key, encoded, System.currentTimeMillis()))
                }
                return@withContext Result.Ok(emptyLyrics)
            }
        }
    }

    /** Quietly prefetches lyrics for the upcoming track when on an unmetered connection. */
    suspend fun prefetch(track: Track) = withContext(Dispatchers.IO) {
        if (!isUnmeteredConnection()) return@withContext
        val key = cacheKeyForTrack(track)
        if (sessionFailedKeys.contains(key)) return@withContext
        val existing = runCatching { cache.get(key) }.getOrNull()
        if (existing == null) {
            getLyrics(track, bypassCache = false)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cache.clear()
        sessionFailedKeys.clear()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isUnmeteredConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
