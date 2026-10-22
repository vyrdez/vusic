package com.feishin.mobile.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsCacheKey
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.db.LyricsCacheDao
import com.feishin.mobile.data.db.LyricsCacheEntity
import com.feishin.mobile.data.lyrics.BetterLyricsProvider
import com.feishin.mobile.data.lyrics.FileTagLyricsProvider
import com.feishin.mobile.data.lyrics.KuGouLyricsProvider
import com.feishin.mobile.data.lyrics.LrclibLyricsProvider
import com.feishin.mobile.data.lyrics.PaxsenixLyricsProvider
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.LyricsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

@Singleton
class LyricsRepository @Inject constructor(
    private val cache: LyricsCacheDao,
    private val lrclib: LrclibLyricsProvider,
    private val betterLyrics: BetterLyricsProvider,
    private val paxsenix: PaxsenixLyricsProvider,
    private val kugou: KuGouLyricsProvider,
    private val navidrome: NavidromeLyricsProvider,
    private val fileTags: FileTagLyricsProvider,
    private val settingsStore: SettingsStore,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    /** 7-day TTL for negative cache entries ("nothing found"). */
    private val negativeTtlMs = 7 * 24 * 60 * 60 * 1000L

    /** Total lookup budget per song across all providers before giving up. */
    private val totalLookupBudgetMs = 15_000L

    /** Global mutex to ensure requests across all providers are strictly sequential. */
    private val globalLookupMutex = Mutex()

    /** Guard against multiple simultaneous lookups for the same song. */
    private val lookupLocks = ConcurrentHashMap<String, Mutex>()

    /** In-memory tracking of transient network failures within this app session. */
    private val sessionFailedKeys = ConcurrentHashMap.newKeySet<String>()

    fun cacheKeyForTrack(track: Track): String = LyricsCacheKey.of(track)

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
                                return@withContext Result.Ok(cachedLyrics)
                            }
                            // Expired negative entry, fall through to re-query
                        } else {
                            // Preserve original provider source stored in cache
                            val needsRomanize = cachedLyrics.lines.any { it.romanizedText == null && com.feishin.mobile.data.lyrics.Romanizer.hasRomanizableScript(it.text) }
                            val resolved = if (needsRomanize) com.feishin.mobile.data.lyrics.Romanizer.romanizeLyrics(cachedLyrics) else cachedLyrics
                            return@withContext Result.Ok(resolved)
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

            val durationMs = track.durationSec.toLong() * 1000L

            // Run sequential chain inside global mutex with total 15s budget
            var finalLyrics: Lyrics? = null
            var transientError: Result.Err? = null

            globalLookupMutex.withLock {
                withTimeoutOrNull(totalLookupBudgetMs) {
                    val enabledOrder = settings.lyricsProviderOrder.filter { it in settings.lyricsProviderEnabled }
                    var bestSynced: Lyrics? = null
                    var plainCandidate: Lyrics? = null

                    for (providerId in enabledOrder) {
                        val isOnlineProvider = providerId != "LOCAL_DATA"
                        if (isOnlineProvider && !isOnlineAllowed) {
                            continue
                        }

                        val provider: LyricsProvider = when (providerId) {
                            "LRCLIB" -> lrclib
                            "BETTER_LYRICS" -> betterLyrics
                            "PAXSENIX" -> paxsenix
                            "KUGOU" -> kugou
                            "LOCAL_DATA" -> if (track.isLocal) fileTags else navidrome
                            else -> continue
                        }

                        val result = provider.lyricsForSong(
                            songId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = durationMs,
                            localUri = track.localUri,
                            localModifiedSec = track.localModifiedSec,
                        )

                        when (result) {
                            is Result.Ok -> {
                                val lyrics = result.value
                                if (lyrics.isSynced) {
                                    val hasWords = lyrics.lines.any { it.words.isNotEmpty() }
                                    if (settings.lyricsPreferWordByWord) {
                                        if (bestSynced == null) {
                                            bestSynced = lyrics
                                            if (hasWords) break // Found word-timed lyrics, best possible
                                        } else if (!bestSynced.lines.any { it.words.isNotEmpty() } && hasWords) {
                                            bestSynced = lyrics
                                            break // Upgraded line-synced to word-synced!
                                        }
                                    } else {
                                        // First line-synced wins
                                        bestSynced = lyrics
                                        break
                                    }
                                } else if (lyrics.isPlain && plainCandidate == null) {
                                    plainCandidate = lyrics
                                }
                            }
                            is Result.Err -> {
                                if (transientError == null) transientError = result
                            }
                        }
                    }

                    finalLyrics = bestSynced ?: plainCandidate
                }
            }

            // Persist & Return
            if (finalLyrics != null && !finalLyrics!!.isNone) {
                sessionFailedKeys.remove(key)
                val processed = com.feishin.mobile.data.lyrics.Romanizer.romanizeLyrics(finalLyrics!!)
                runCatching {
                    val encoded = json.encodeToString(Lyrics.serializer(), processed)
                    cache.put(LyricsCacheEntity(key, encoded, System.currentTimeMillis()))
                }
                return@withContext Result.Ok(processed)
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
        getLyrics(track, bypassCache = false)
    }

    suspend fun saveCustomLyrics(track: Track, lyrics: Lyrics) = withContext(Dispatchers.IO) {
        val key = cacheKeyForTrack(track)
        sessionFailedKeys.remove(key)
        val processed = com.feishin.mobile.data.lyrics.Romanizer.romanizeLyrics(lyrics)
        val encoded = json.encodeToString(Lyrics.serializer(), processed)
        cache.put(LyricsCacheEntity(key, encoded, System.currentTimeMillis()))
    }

    suspend fun removeCached(track: Track) = withContext(Dispatchers.IO) {
        val key = cacheKeyForTrack(track)
        cache.remove(key)
        sessionFailedKeys.remove(key)
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
