@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.feishin.mobile.MainActivity
import com.feishin.mobile.core.network.ScrobblePolicy
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the real ExoPlayer and publishes it through a [MediaSession], which gives
 * us the media notification, lock-screen controls and Bluetooth/headset buttons
 * for free. ExoPlayer handles audio focus and noisy transitions.
 */
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    @Inject lateinit var repo: MusicDataRepository
    @Inject @Named("streaming") lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var cache: androidx.media3.datasource.cache.SimpleCache

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val scrobblePolicy = ScrobblePolicy()
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Scheme-aware upstream: downloaded tracks are opened from file:// by
        // DefaultDataSource (FileDataSource), while network streams still go
        // through the shared OkHttp client (self-signed + path prefix).
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(UpstreamLogger(upstream))
            // Stable keys (song + quality, no per-request salt): prefetched
            // bytes and replays hit the same spans instead of re-downloading.
            .setCacheKeyFactory { spec ->
                com.feishin.mobile.core.common.StableCacheKey.keyFor(spec.uri.toString())
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(
                object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        android.util.Log.d(TAG, "cache hit bytes=$cachedBytesRead cacheSize=$cacheSizeBytes")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        android.util.Log.d(TAG, "cache ignored reason=$reason")
                    }
                },
            )

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                // "Now playing" scrobble + reset the submission rule for the new track.
                scope.launch { repo.scrobble(id, submission = false) }
                startTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val id = player?.currentMediaItem?.mediaId
                    if (id != null && scrobblePolicy.onTrackEnded(id, player?.duration ?: 0L)) {
                        scope.launch { repo.scrobble(id, submission = true) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopTicker()
            }
        })

        player = exo
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        startTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        stopTicker()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** Drives the 50%-or-4-minutes scrobble submission rule. */
    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                val p = player ?: break
                val id = p.currentMediaItem?.mediaId
                if (id != null && p.isPlaying) {
                    val submit = scrobblePolicy.onProgress(
                        id = id,
                        positionMs = p.currentPosition,
                        durationMs = p.duration,
                        isPlaying = true,
                    )
                    if (submit) repo.scrobble(id, submission = true)
                }
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        const val TAG = "PlayerCache"
    }
}

/**
 * Upstream decorator that logs every real network open with its stable key.
 * A second play of a fully cached song must produce cache-hit logs and no
 * "upstream open" line at all — that is the zero-network-request proof.
 */
private class UpstreamLogger(
    private val delegate: androidx.media3.datasource.DataSource.Factory,
) : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource {
        val inner = delegate.createDataSource()
        return object : androidx.media3.datasource.DataSource by inner {
            override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                val key = com.feishin.mobile.core.common.StableCacheKey.keyFor(dataSpec.uri.toString())
                android.util.Log.d(PlayerService.TAG, "upstream open key=$key uri=${dataSpec.uri.host}${dataSpec.uri.path}")
                return inner.open(dataSpec)
            }
        }
    }
}