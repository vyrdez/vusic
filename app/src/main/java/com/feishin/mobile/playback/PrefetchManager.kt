@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.feishin.mobile.core.common.PrefetchWindow
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.StableCacheKey
import com.feishin.mobile.core.network.StreamUrlProvider
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Buffers upcoming queue items fully into the shared Media3 [SimpleCache] so
 * song transitions have no loading gap.
 *
 * Every fetch uses [StableCacheKey] — the same function the player's
 * CacheDataSource keys with — so prefetched bytes are exactly what playback
 * consumes. Fetches run on IO at minimum priority, never touch the current
 * item, and are cancelled the moment the queue window moves past them.
 */
@Singleton
class PrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: SimpleCache,
    @Named("streaming") private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val creds: CredentialsStore,
    private val downloads: DownloadRepository,
    private val cachedTracks: CachedTrackStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.feishin.mobile.core.common.CrashGuard.handler)

    /** queueId -> in-flight fetch. Guarded by confinement to [scope]. */
    private val active = ConcurrentHashMap<Long, Job>()

    /**
     * Recomputes the prefetch window from the live queue. Called on track
     * transitions, queue edits, shuffle changes, radio appends and skip/seek —
     * i.e. everywhere the "next N" can move.
     */
    fun onQueue(queue: List<QueueItem>, currentIndex: Int) {
        scope.launch {
            val s = settings.flow.first()
            if (!s.cacheUpcoming) {
                cancelAll()
                return@launch
            }
            if (isOffline()) {
                cancelAll()
                return@launch
            }
            if (!PrefetchWindow.allowedOnNetwork(network.networkClass.value, s.cacheWifiOnly)) {
                cancelAll()
                return@launch
            }
            // Safety floor: caching pauses automatically below 1 GB free.
            if (!com.feishin.mobile.core.common.StorageLimits.cachingAllowed(downloads.freeBytes())) {
                cancelAll()
                return@launch
            }
            val window = PrefetchWindow.window(
                queueIds = queue.map { it.queueId },
                currentIndex = currentIndex,
                n = s.tracksToCache,
            )
            // Cancel fetches that left the window.
            PrefetchWindow.stale(active.keys, window).forEach { active.remove(it)?.cancel() }
            val byId = queue.associateBy { it.queueId }
            val quality = QualityPolicy.forNetwork(
                network = network.networkClass.value,
                wifiBitrate = s.wifiBitrate, wifiFormat = s.wifiFormat,
                mobileBitrate = s.mobileBitrate, mobileFormat = s.mobileFormat,
            )
            val credsNow = creds.state.value ?: return@launch
            for (qid in window) {
                if (active.containsKey(qid)) continue
                val item = byId[qid] ?: continue
                if (downloads.localFile(item.song.id) != null) continue
                if (alreadyCached(item.song, quality.bitrateKbps, quality.format)) continue
                active[qid] = launch {
                    fetch(
                        track = item.song, queueId = qid,
                        serverUrl = credsNow.serverUrl, username = credsNow.username, password = credsNow.password,
                        bitrate = quality.bitrateKbps, format = quality.format,
                    )
                }
            }
        }
    }

    /**
     * Ensures one specific song is fully cached (auto-cache-played path).
     * No-ops when it is already cached, stored locally, or in flight.
     */
    fun ensureFull(track: Track, queueId: Long) {
        scope.launch {
            val s = settings.flow.first()
            if (isOffline()) return@launch
            if (!PrefetchWindow.allowedOnNetwork(network.networkClass.value, s.cacheWifiOnly)) return@launch
            if (active.containsKey(queueId)) return@launch
            val credsNow = creds.state.value ?: return@launch
            if (downloads.localFile(track.id) != null) return@launch
            val quality = QualityPolicy.forNetwork(
                network = network.networkClass.value,
                wifiBitrate = s.wifiBitrate, wifiFormat = s.wifiFormat,
                mobileBitrate = s.mobileBitrate, mobileFormat = s.mobileFormat,
            )
            if (alreadyCached(track, quality.bitrateKbps, quality.format)) return@launch
            active[queueId] = launch {
                fetch(
                    track = track, queueId = queueId,
                    serverUrl = credsNow.serverUrl, username = credsNow.username, password = credsNow.password,
                    bitrate = quality.bitrateKbps, format = quality.format,
                )
            }
        }
    }

    fun cancelAll() {
        active.values.forEach { it.cancel() }
        active.clear()
    }

    fun activeCount(): Int = active.size

    private suspend fun isOffline(): Boolean =
        runCatching { settings.settingsNow().offlineMode }.getOrDefault(false) || !network.isOnline.value

    /**
     * True when the Room registry has this song and its bytes are still fully
     * in the cache at the requested quality.
     */
    private suspend fun alreadyCached(track: Track, bitrate: Int, format: String): Boolean {
        val row = cachedTracks.get(track.id) ?: return false
        if (row.sizeBytes <= 0) return false
        return runCatching { cache.isCached(row.cacheKey, 0, row.sizeBytes) }.getOrDefault(false)
    }

    /**
     * Fully fetches one song into the shared cache, then records it for the
     * Cached collection. Minimum-priority IO: playback always wins.
     */
    private suspend fun fetch(
        track: Track,
        queueId: Long,
        serverUrl: String,
        username: String,
        password: String,
        bitrate: Int,
        format: String,
    ) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
        try {
            val url = StreamUrlProvider.build(serverUrl, username, password, track.id, bitrate, format)
            val key = StableCacheKey.keyFor(url)
            val dataSpec = DataSpec(Uri.parse(url), 0L, C.LENGTH_UNSET.toLong())
            val upstream = OkHttpDataSource.Factory(http).createDataSource()
            val cds = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory { upstream }
                .setCacheKeyFactory { spec -> StableCacheKey.keyFor(spec.uri.toString()) }
                .createDataSource()
            val writer = CacheWriter(cds, dataSpec, ByteArray(DEFAULT_BUFFER), null)
            // Returns only once EOF is reached: the song is whole.
            writer.cache()
            val bytes = runCatching { cache.getCachedBytes(key, 0L, Long.MAX_VALUE) }.getOrDefault(0L)
            cachedTracks.recordCached(track, key, bytes)
            VLog.d(TAG, "prefetch complete song=${track.id} key=$key bytes=$bytes")
        } catch (e: Exception) {
            // Quiet by design: prefetch must never surface errors or retries.
            VLog.d(TAG, "prefetch skipped song=${track.id}: ${e.message}")
        } finally {
            active.remove(queueId)
        }
    }

    companion object {
        const val TAG = "Prefetch"
        const val DEFAULT_BUFFER = 128 * 1024
    }
}
