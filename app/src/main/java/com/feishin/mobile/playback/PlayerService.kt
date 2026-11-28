@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil.ImageLoader
import coil.request.ImageRequest
import com.feishin.mobile.MainActivity
import com.feishin.mobile.R
import com.feishin.mobile.core.common.AutoBrowse
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.PlaybackSource
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.StableCacheKey
import com.feishin.mobile.core.network.CoverCacheKey
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.toTrack
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.queue.QueueStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the real ExoPlayer and publishes it through a [MediaLibrarySession],
 * which gives us the media notification, lock-screen controls, Bluetooth /
 * headset buttons — and Android Auto browse + voice — for free. ExoPlayer
 * handles audio focus and noisy transitions.
 *
 * Phone behavior is unchanged: the app drives this same player through its
 * MediaController, so the queue Auto shows is always the phone's queue. Items
 * that already carry a stream URI (every phone-initiated request) pass through
 * [MediaLibrarySession.Callback.onAddMediaItems] untouched; only Auto browse /
 * voice placeholders are resolved here, and the resolution mirrors the queue
 * back into [PlayerManager] without touching the controller.
 */
@AndroidEntryPoint
class PlayerService : MediaLibraryService() {

    @Inject lateinit var repo: MusicDataRepository
    @Inject lateinit var profileStore: ProfileStore
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var downloads: DownloadRepository
    @Inject lateinit var favorites: FavoritesStore
    @Inject lateinit var cachedTracks: CachedTrackStore
    @Inject lateinit var network: NetworkMonitor
    @Inject lateinit var playerManager: PlayerManager
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var queueStore: com.feishin.mobile.data.queue.QueueStore
    @Inject lateinit var offlineSync: dagger.Lazy<OfflineSyncStore>
    @Inject lateinit var localHistoryStore: com.feishin.mobile.data.history.LocalHistoryStore
    @Inject @Named("streaming") lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var cache: androidx.media3.datasource.cache.SimpleCache

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.feishin.mobile.core.common.CrashGuard.handler)
    /** Application-scoped IO scope that survives [onDestroy] so final Jellyfin stop reports finish or fall back to offline queue. */
    private val stopReportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.feishin.mobile.core.common.CrashGuard.handler)
    private val scrobblePolicy = com.feishin.mobile.core.network.ScrobblePolicy()
    private val jellyfinSession = JellyfinSessionCoordinator()
    private var ticker: Job? = null

    /** songId -> exact Track for already-browsed items (adopt mirror + resolve). */
    private val resolvedSongs = java.util.concurrent.ConcurrentHashMap<String, Track>()

    /** Voice query -> resolved playable tracks for onGetSearchResult paging. */
    private val searchStash = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()

    /** Voice queries that asked for shuffle ("shuffle play X"). */
    private val shuffleQueries = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /** songId -> retried flag, guarding against infinite network retry loops. */
    private val retriedMediaIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    /** Main-thread-populated lookup tables so `ResolvingDataSource` resolves `"p1"` profile IDs safely. */
    private val streamProfileByKey = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val streamProfileByUri = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var activePlaybackMediaId: String? = null

    private fun indexPlayerMediaItems(p: Player) {
        activePlaybackMediaId = p.currentMediaItem?.mediaId
        if (streamProfileByKey.size > 512) streamProfileByKey.clear()
        if (streamProfileByUri.size > 512) streamProfileByUri.clear()
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val pid = GlobalId.profileIdOrNull(item.mediaId) ?: continue
            val local = item.localConfiguration ?: continue
            local.customCacheKey?.takeIf { it.isNotBlank() }?.let { streamProfileByKey[it] = pid }
            streamProfileByUri[local.uri.toString()] = pid
        }
    }

    override fun onCreate() {
        super.onCreate()
        VLog.d(TAG, "service onCreate")
        scope.launch { runCatching { profileStore.preloadIfNeeded() } }
        // Brand the playback notification with the Vusic note. Media3 would
        // otherwise post its own generic small icon; the shape must match the
        // app icon, and it must be a flat silhouette for the status bar.
        runCatching {
            setMediaNotificationProvider(
                androidx.media3.session.DefaultMediaNotificationProvider(this).apply {
                    setSmallIcon(R.drawable.ic_stat_vusic)
                },
            )
        }
        // Scheme-routing DataSource: local files (file://, content://, android.resource://)
        // bypass CacheDataSource and SimpleCache entirely via local DefaultDataSource.
        // Network streams (http://, https://) route to CacheDataSource with UpstreamLogger.
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
        val httpFactory = ResolvingDataSource.Factory(okHttpFactory) { spec ->
            val mediaId = activePlaybackMediaId
            val profileId = StableCacheKey.extractProfileId(spec.key)
                ?: spec.key?.let { streamProfileByKey[it] }
                ?: streamProfileByUri[spec.uri.toString()]
                ?: mediaId?.let { GlobalId.profileIdOrNull(it) }
            if (profileId != null && !spec.httpRequestHeaders.containsKey(ProfileAuthInterceptor.HEADER_PROFILE_ID)) {
                spec.buildUpon()
                    .setHttpRequestHeaders(
                        spec.httpRequestHeaders + (ProfileAuthInterceptor.HEADER_PROFILE_ID to profileId),
                    )
                    .build()
            } else {
                spec
            }
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(UpstreamLogger(httpFactory))
            // Stable keys (song + quality, no per-request salt): prefetched
            // bytes and replays hit the same spans instead of re-downloading.
            .setCacheKeyFactory { spec ->
                spec.key ?: StableCacheKey.keyFor(spec.uri.toString())
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(
                object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        VLog.d(TAG, "cache hit bytes=$cachedBytesRead cacheSize=$cacheSizeBytes")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        VLog.d(TAG, "cache ignored reason=$reason")
                    }
                },
            )

        val localFactory = DefaultDataSource.Factory(this)
        val routingFactory = RoutingDataSourceFactory(
            localFactory = localFactory,
            streamFactory = cacheFactory,
        )

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(routingFactory))
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
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                indexPlayerMediaItems(exo)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                indexPlayerMediaItems(exo)
                val id = mediaItem?.mediaId
                val jellyfin = id != null && isJellyfinTrack(id)
                val uri = mediaItem?.localConfiguration?.uri?.toString().orEmpty()
                val isDirect = !uri.contains("/universal", ignoreCase = true)
                val (stoppedPrev, startedNext) = jellyfinSession.transitionTo(
                    newTrackId = id,
                    isJellyfin = jellyfin,
                    isDirect = isDirect,
                    durationMs = player?.duration ?: 0L,
                )
                if (stoppedPrev != null) {
                    stopReportScope.launch {
                        repo.reportPlaybackStopped(
                            id = stoppedPrev.trackId,
                            positionMs = stoppedPrev.lastPositionMs,
                            playSessionId = stoppedPrev.playSessionId,
                        )
                    }
                }
                if (id == null) return
                // Prune other retried IDs when moving to a different track
                val old = retriedMediaIds.toSet()
                for (oldId in old) {
                    if (oldId != id) retriedMediaIds.remove(oldId)
                }
                if (startedNext != null) {
                    scrobblePolicy.reset()
                    scope.launch {
                        repo.reportPlaybackStart(
                            id = startedNext.trackId,
                            positionMs = 0L,
                            playSessionId = startedNext.playSessionId,
                            isDirect = startedNext.isDirect,
                        )
                    }
                } else if (!isLocal(mediaItem)) {
                    // Reset submission rule for non-Jellyfin tracks (Subsonic + Google Drive);
                    // only send "now playing" scrobble when backend supports scrobbling.
                    scrobblePolicy.reset()
                    if (repo.capabilitiesFor(id).supportsScrobbling) {
                        scope.launch { repo.scrobble(id, submission = false) }
                    }
                }
                startTicker()
                pushWidget()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val item = player?.currentMediaItem
                    val id = item?.mediaId
                    val ended = jellyfinSession.onEnded(id, player?.duration ?: 0L)
                    if (ended != null) {
                        val (session, stopPositionMs) = ended
                        stopReportScope.launch {
                            reportStoppedOrQueueOffline(
                                trackId = session.trackId,
                                positionMs = stopPositionMs,
                                playSessionId = session.playSessionId,
                            )
                        }
                    } else if (id != null && !isJellyfinTrack(id) && !isLocal(item) && scrobblePolicy.onTrackEnded(id, player?.duration ?: 0L)) {
                        val track = resolvedTrackFor(item, id)
                        scope.launch {
                            submitPlayOrRecordLocal(
                                id = id,
                                trackProvider = { track },
                                repo = repo,
                                localHistoryStore = localHistoryStore,
                            )
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val p = player
                val id = p?.currentMediaItem?.mediaId
                if (isPlaying && id != null) {
                    retriedMediaIds.remove(id)
                }
                if (p != null && id != null) {
                    val session = jellyfinSession.onPauseOrSeek(id, p.currentPosition, p.duration)
                    if (session != null) {
                        scope.launch {
                            repo.reportPlaybackProgress(
                                id = session.trackId,
                                positionMs = session.lastPositionMs,
                                isPaused = !isPlaying,
                                playSessionId = session.playSessionId,
                                isDirect = session.isDirect,
                            )
                        }
                    }
                }
                pushWidget()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                val p = player ?: return
                val id = p.currentMediaItem?.mediaId ?: return
                val session = jellyfinSession.onPauseOrSeek(id, newPosition.positionMs, p.duration) ?: return
                val paused = !p.isPlaying
                scope.launch {
                    repo.reportPlaybackProgress(
                        id = session.trackId,
                        positionMs = session.lastPositionMs,
                        isPaused = paused,
                        playSessionId = session.playSessionId,
                        isDirect = session.isDirect,
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopTicker()
                val p = player ?: return
                val item = p.currentMediaItem ?: return
                val idx = p.currentMediaItemIndex
                val title = item.mediaMetadata.title?.toString()

                scope.launch {
                    val offline = isOffline()
                    val livePlayer = player ?: return@launch

                    if (item.localConfiguration?.uri?.scheme == "file" && !isLocal(item)) {
                        val localFile = downloads.localFile(item.mediaId)
                        if (PlaybackRecovery.shouldDeleteDownloadOnError(offline, localFile != null)) {
                            downloads.remove(item.mediaId)
                        }
                        if (offline) {
                            livePlayer.pause()
                            playerManager.notifyOfflineUnavailable(title)
                        } else {
                            val track = TrackMediaItems.toTrack(item)
                            val qId = TrackMediaItems.queueIdOf(item) ?: -1L
                            val streamItem = track?.let { resolveTrack(it, qId, forceNetwork = true) }
                            if (streamItem != null && livePlayer.currentMediaItemIndex == idx) {
                                livePlayer.replaceMediaItem(idx, streamItem)
                                livePlayer.prepare()
                                livePlayer.play()
                            } else {
                                livePlayer.pause()
                                playerManager.notifyPlaybackFailed(title)
                            }
                        }
                    } else if (!isLocal(item)) {
                        // Case B: Stream URI failed
                        if (offline) {
                            livePlayer.pause()
                            playerManager.notifyOfflineUnavailable(title)
                        } else {
                            val uriStr = item.localConfiguration?.uri?.toString().orEmpty()
                            val key = StableCacheKey.keyFor(uriStr, GlobalId.profileIdOrNull(item.mediaId))
                            if (PlaybackRecovery.shouldEvictStreamCacheOnError(isOffline = false)) {
                                runCatching { cache.removeResource(key) }
                                cachedTracks.remove(item.mediaId)
                            }
                            val pos = livePlayer.currentPosition
                            val alreadyRetried = retriedMediaIds.contains(item.mediaId)
                            if (PlaybackRecovery.shouldRetryFromNetwork(error.errorCode, alreadyRetried, isOffline = false)) {
                                retriedMediaIds.add(item.mediaId)
                                val track = TrackMediaItems.toTrack(item)
                                val qId = TrackMediaItems.queueIdOf(item) ?: -1L
                                val streamItem = track?.let { resolveTrack(it, qId, forceNetwork = true) }
                                if (streamItem != null && livePlayer.currentMediaItemIndex == idx) {
                                    livePlayer.replaceMediaItem(idx, streamItem)
                                    if (pos > 0) livePlayer.seekTo(idx, pos)
                                    livePlayer.prepare()
                                    livePlayer.play()
                                } else if (livePlayer.hasNextMediaItem()) {
                                    livePlayer.seekToNextMediaItem()
                                    livePlayer.prepare()
                                    livePlayer.play()
                                } else {
                                    livePlayer.pause()
                                    playerManager.notifyPlaybackFailed(title)
                                }
                            } else if (livePlayer.hasNextMediaItem()) {
                                livePlayer.seekToNextMediaItem()
                                livePlayer.prepare()
                                livePlayer.play()
                            } else {
                                livePlayer.pause()
                                playerManager.notifyPlaybackFailed(title)
                            }
                        }
                    } else if (isLocal(item)) {
                        if (livePlayer.hasNextMediaItem()) {
                            livePlayer.seekToNextMediaItem()
                            livePlayer.prepare()
                            livePlayer.play()
                        } else {
                            livePlayer.pause()
                            playerManager.notifyPlaybackFailed(title)
                        }
                    }
                }
            }
        })

        player = exo
        playerManager.attachLocalPlayer(exo)
        val sessionPlayer = BoundedQueuePlayer(exo, maxWindow = 100)
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, AutoCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            // Auto cannot load per-request authenticated artwork reliably, so the
            // session serves bitmaps through the shared (self-signed-aware) Coil
            // loader instead.
            .setBitmapLoader(CoilBitmapLoader())
            .build()
        startTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        VLog.d(TAG, "onGetSession pkg=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        // Last chance to keep the queue without blocking the main thread.
        playerManager.snapshotPositionFromPlayer()
        val drained = jellyfinSession.drainOnDestroy(
            currentTrackId = player?.currentMediaItem?.mediaId,
            playerPositionMs = player?.currentPosition ?: 0L,
        )
        stopReportScope.launch {
            runCatching { playerManager.persist() }
        }
        if (drained != null) {
            val (session, finalPosMs) = drained
            stopReportScope.launch {
                reportStoppedOrQueueOffline(
                    trackId = session.trackId,
                    positionMs = finalPosMs,
                    playSessionId = session.playSessionId,
                )
            }
        }
        stopTicker()
        mediaSession?.release()
        mediaSession = null
        player?.let { playerManager.detachLocalPlayer(it) }
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app out of recents tears the session down. Persist off
        // the main thread so a large queue never triggers an ANR.
        playerManager.snapshotPositionFromPlayer()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { playerManager.persist() }
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Pushes the home widget from the session player (covers the app-dead
     * path). Event-driven: track transitions and play/pause flips only, never
     * the 1s ticker. Skips no-op repeats so background transitions don't spam
     * RemoteViews updates.
     */
    private var lastWidgetKey: Triple<String?, Boolean, Int>? = null

    private fun pushWidget() {
        val p = player ?: return
        // ExoPlayer is app-thread confined: snapshot here (this runs on Main
        // from the listener), then fan out to IO for prefs and art.
        val item = p.currentMediaItem
        val count = p.mediaItemCount
        val playing = p.isPlaying
        val track = item?.let { TrackMediaItems.toTrack(it) }
        val artUri = item?.mediaMetadata?.artworkUri?.toString()
        val key = Triple(track?.id, playing, count)
        if (key == lastWidgetKey) return
        lastWidgetKey = key
        scope.launch(Dispatchers.IO) {
            runCatching { profileStore.preloadIfNeeded() }
            val saved = com.feishin.mobile.widget.WidgetStateStore(this@PlayerService).load()
            val state = if (track == null || count == 0) {
                com.feishin.mobile.widget.WidgetState.empty()
            } else {
                val starred = if (saved.trackId == track.id) saved.starred else false
                com.feishin.mobile.widget.WidgetState(
                    hasQueue = true,
                    title = track.title,
                    artist = track.artist,
                    coverArtId = track.coverArt,
                    trackId = track.id,
                    isPlaying = playing,
                    starred = starred,
                )
            }
            runCatching {
                com.feishin.mobile.widget.WidgetUpdater.update(
                    this@PlayerService, state, imageLoader,
                ) { id ->
                    artUri ?: coverUrl(id, 256)?.toString()
                }
            }
        }
    }

    /** Drives the 15s Jellyfin session progress ticks and the 50%-or-4-minutes Subsonic scrobble rule. */
    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                val p = player ?: break
                val item = p.currentMediaItem
                val id = item?.mediaId
                if (id != null && p.isPlaying) {
                    if (jellyfinSession.active?.trackId == id) {
                        val dueSession = jellyfinSession.onTick(
                            trackId = id,
                            positionMs = p.currentPosition,
                            durationMs = p.duration,
                        )
                        if (dueSession != null) {
                            repo.reportPlaybackProgress(
                                id = dueSession.trackId,
                                positionMs = dueSession.lastPositionMs,
                                isPaused = false,
                                playSessionId = dueSession.playSessionId,
                                isDirect = dueSession.isDirect,
                            )
                        }
                    } else if (!isJellyfinTrack(id) && !isLocal(item)) {
                        val submit = scrobblePolicy.onProgress(
                            id = id,
                            positionMs = p.currentPosition,
                            durationMs = p.duration,
                            isPlaying = true,
                        )
                        if (submit) {
                            val track = resolvedTrackFor(item, id)
                            scope.launch {
                                submitPlayOrRecordLocal(
                                    id = id,
                                    trackProvider = { track },
                                    repo = repo,
                                    localHistoryStore = localHistoryStore,
                                )
                            }
                        }
                    }
                }
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun isJellyfinTrack(id: String): Boolean {
        if (repo.isSessionScrobbleBackend(id)) return true
        val profileId = GlobalId.profileIdOrNull(id) ?: return false
        return profileStore.byId(profileId)?.type == BackendType.JELLYFIN
    }

    private suspend fun reportStoppedOrQueueOffline(
        trackId: String,
        positionMs: Long,
        playSessionId: String,
    ) {
        val offline = isOffline()
        val res = if (offline) null else withTimeoutOrNull(STOP_REPORT_TIMEOUT_MS) {
            repo.reportPlaybackStopped(trackId, positionMs, playSessionId)
        }
        if (res !is Result.Ok) {
            offlineSync.get().queueScrobble(trackId, submission = true, timeMs = System.currentTimeMillis())
        }
    }

    // ---- Auto helpers (used by the callback) -------------------------------

    private fun rememberTracks(tracks: List<Track>) {
        for (t in tracks) {
            if (resolvedSongs.size > 1000) resolvedSongs.clear()
            resolvedSongs[t.id] = t
        }
    }

    private fun resolvedTrackFor(item: MediaItem, id: String): Track? =
        resolvedSongs[id]
            ?: playerManager.current.value?.takeIf { it.id == id }
            ?: TrackMediaItems.toTrack(item)?.copy(id = id)

    private fun isLocal(mediaItem: MediaItem?): Boolean {
        // Keyed on the id we mint for MediaStore tracks. Downloaded server songs
        // also play from a file:// URI, so a scheme check would wrongly treat
        // them as device files (and silently stop scrobbling them).
        return mediaItem?.mediaId?.startsWith("local:") == true
    }

    private fun coverUrl(coverArtId: String?, size: Int = 512): Uri? {
        if (coverArtId.isNullOrBlank()) return null
        if (coverArtId.startsWith("content://") || coverArtId.startsWith("file://")) {
            return Uri.parse(coverArtId)
        }
        return repo.coverArtUrl(coverArtId, size)?.let { Uri.parse(it) }
    }

    private fun folder(id: String, title: String, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .apply { if (art != null) setArtworkUri(art) }
                    .build(),
            )
            .build()

    /** Browse placeholder for a track: no URI yet (resolved on play). */
    private fun trackPlaceholder(track: Track, parentId: String, index: Int): MediaItem {
        rememberTracks(listOf(track))
        return MediaItem.Builder()
            .setMediaId("auto_${track.id}_$index")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setDurationMs(if (track.durationSec > 0) track.durationSec * 1000L else 0L)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .apply { coverUrl(track.coverArt, 512)?.let { setArtworkUri(it) } }
                    .setExtras(
                        androidx.core.os.bundleOf(
                            EXTRA_SONG_ID to track.id,
                            EXTRA_PARENT_ID to parentId,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    private fun songIdOf(item: MediaItem): String? =
        item.mediaMetadata.extras?.getString(EXTRA_SONG_ID)

    /** Offline when the toggle says so or the network is gone. */
    private suspend fun isOffline(): Boolean {
        val s = runCatching { settings.settingsNow() }.getOrNull()
        val auto = s?.autoOffline == true && !network.isOnline.value
        return s?.offlineMode == true || auto || !network.isOnline.value
    }

    /**
     * Playable MediaItem for [track]: finished downloads play from the local
     * file (offline-safe), otherwise the Wi-Fi-quality stream URL. Null when
     * offline with nothing downloaded (the item is then dropped, never a dead
     * entry that errors in the car).
     */
    private suspend fun resolveTrack(
        track: Track,
        queueIdHint: Long = -1L,
        forceNetwork: Boolean = false,
    ): MediaItem? {
        runCatching { profileStore.preloadIfNeeded() }
        val deviceUri = track.localUri
        val local = if (deviceUri == null && !forceNetwork) downloads.localFile(track.id) else null
        val uriStr = deviceUri ?: if (local != null) {
            PlaybackSource.localUri(local.absolutePath)
        } else {
            if (isOffline()) return null
            val s = runCatching { settings.settingsNow() }.getOrNull()
            // The car is unmetered: stream at the Wi-Fi quality setting.
            repo.streamUrl(track, s?.wifiBitrate ?: 0, s?.wifiFormat ?: "raw")
        }
        return TrackMediaItems.toMediaItem(
            track = track,
            streamUrl = uriStr,
            coverArtUrl = coverUrl(track.coverArt, 512)?.toString(),
            queueId = queueIdHint.takeIf { it >= 0 },
        )
    }

    private suspend fun collectionTracks(kind: String, id: String): List<Track> {
        return when (kind) {
            "pl" -> (repo.playlist(id) as? Result.Ok)?.value?.tracks.orEmpty()
            "al" -> (repo.album(id) as? Result.Ok)?.value?.tracks.orEmpty()
            "ar" -> {
                val detail = (repo.artist(id) as? Result.Ok)?.value ?: return emptyList()
                // Artist page order: first album's tracks (newest first).
                val first = detail.albums.sortedByDescending { it.year ?: 0 }.firstOrNull()
                    ?: return emptyList()
                (repo.album(first.id) as? Result.Ok)?.value?.tracks.orEmpty()
            }
            "mix" -> when (id) {
                "random" -> (repo.randomSongs(50) as? Result.Ok)?.value.orEmpty()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private suspend fun voiceTracksFor(query: String): List<Track> {
        val q = AutoBrowse.normalizeVoiceQuery(query)
        if (q.isBlank()) return emptyList()
        val res = (repo.search(q, 20, 10, 50) as? Result.Ok)?.value
            ?: return emptyList()
        val picked = AutoBrowse.voiceTracks(res, q)
        if (picked.isNotEmpty()) return picked
        // "play <album>": the album's tracks.
        val album = res.albums.firstOrNull()
        if (album != null) {
            val tracks = (repo.album(album.id) as? Result.Ok)?.value?.tracks.orEmpty()
            if (tracks.isNotEmpty()) return tracks.take(50)
        }
        // "play <artist>": their top songs.
        val artist = res.artists.firstOrNull()
        if (artist != null) {
            val top = (repo.topSongs(artist.name, 50) as? Result.Ok)?.value.orEmpty()
            if (top.isNotEmpty()) return top
        }
        return emptyList()
    }

    private inner class AutoCallback : MediaLibrarySession.Callback {

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Play tapped with an empty player (widget/media button after the
            // app process died): restore the persisted queue paused, exactly
            // like a cold start, instead of failing the command.
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val snapshot = runCatching { queueStore.load() }.getOrNull()
                if (snapshot == null || snapshot.tracks.isEmpty()) {
                    future.setException(IllegalStateException("Nothing to resume"))
                    return@launch
                }
                val built = snapshot.tracks.mapNotNull { p ->
                    val track = p.toDomain()
                    resolveTrack(track, p.queueId.takeIf { it >= 0 } ?: -1L)
                }
                if (built.isEmpty()) {
                    future.setException(IllegalStateException("Nothing to resume"))
                    return@launch
                }
                val index = snapshot.currentIndex.coerceIn(0, built.size - 1)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        built, index, snapshot.positionMs,
                    ),
                )
            }
            return future
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            VLog.d(TAG, "onConnect pkg=${controller.packageName}")
            // Car controllers get Favorite + Start-radio actions; the phone's
            // own controller (same package) is left alone.
            if (controller.packageName != packageName) {
                session.setCustomLayout(
                    controller,
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName("Favorite")
                            .setSessionCommand(SessionCommand(COMMAND_FAVORITE, Bundle.EMPTY))
                            .setIconResId(R.drawable.ic_auto_favorite)
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("Start radio")
                            .setSessionCommand(SessionCommand(COMMAND_RADIO, Bundle.EMPTY))
                            .setIconResId(R.drawable.ic_auto_radio)
                            .build(),
                    ),
                )
            }
            // Custom commands must be routable. The base defaults are session
            // commands only — browsers need the LIBRARY set too, otherwise
            // every browse call fails PERMISSION_DENIED before the callback.
            val commands = SessionCommands.Builder()
                .addSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.commands)
                .add(SessionCommand(COMMAND_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_RADIO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                commands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            VLog.d(TAG, "onGetLibraryRoot")
            val root = MediaItem.Builder()
                .setMediaId(AutoBrowse.ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Vusic")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                val items = runCatching { childrenOf(parentId) }.getOrDefault(emptyList())
                val sliced = AutoBrowse.page(items, page, pageSize)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(sliced), params))
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            scope.launch {
                val raw = query.trim()
                if (raw.startsWith("shuffle ", ignoreCase = true)) shuffleQueries += raw
                val tracks = runCatching { voiceTracksFor(raw) }.getOrDefault(emptyList())
                searchStash[raw] = tracks
                rememberTracks(tracks)
                future.set(LibraryResult.ofVoid())
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                val tracks = searchStash[query]
                    ?: runCatching { voiceTracksFor(query) }.getOrDefault(emptyList())
                if (!searchStash.containsKey(query)) {
                    searchStash[query] = tracks
                    rememberTracks(tracks)
                }
                // The raw query rides in the parent id so the play request can
                // pick up the shuffle flag ("shuffle play X").
                val parent = "search::$query"
                val items = AutoBrowse.page(tracks, page, pageSize)
                    .mapIndexed { i, t -> trackPlaceholder(t, parent, page * pageSize + i) }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // Phone-initiated requests already carry stream URIs: untouched.
            if (mediaItems.all { it.localConfiguration?.uri?.toString().isNullOrEmpty().not() }) {
                return Futures.immediateFuture(mediaItems)
            }
            val future = SettableFuture.create<List<MediaItem>>()
            scope.launch {
                val out = runCatching { resolvePlayRequest(mediaItems) }.getOrDefault(mediaItems)
                future.set(out)
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            browser: MediaSession.ControllerInfo,
            command: SessionCommand,
            extras: Bundle,
        ): ListenableFuture<SessionResult> {
            val future = SettableFuture.create<SessionResult>()
            scope.launch {
                val code = runCatching {
                    when (command.customAction) {
                        COMMAND_FAVORITE -> {
                            val current = playerManager.current.value ?: return@runCatching SessionResult.RESULT_ERROR_UNKNOWN
                            favorites.setTrackStarred(current.id, !current.starred)
                            SessionResult.RESULT_SUCCESS
                        }
                        COMMAND_RADIO -> {
                            val seed = playerManager.current.value
                                ?: (repo.randomSongs(1) as? Result.Ok)?.value?.firstOrNull()
                                ?: return@runCatching SessionResult.RESULT_ERROR_UNKNOWN
                            playerManager.startRadio(seed)
                            SessionResult.RESULT_SUCCESS
                        }
                        else -> SessionResult.RESULT_ERROR_UNKNOWN
                    }
                }.getOrDefault(SessionResult.RESULT_ERROR_UNKNOWN)
                future.set(SessionResult(code))
            }
            return future
        }
    }

    private fun rememberTracksFromItemsUnused() = Unit

    private suspend fun childrenOf(parentId: String): List<MediaItem> {
        runCatching { profileStore.preloadIfNeeded() }
        if (parentId == AutoBrowse.ROOT_ID) {
            return AutoBrowse.rootTabs().map { folder(it.id, it.title) }
        }
        // Home nodes render their own children.
        when (parentId) {
            AutoBrowse.TAB_HOME -> return AutoBrowse.homeChildren().map { n ->
                folder(n.id, n.title).takeIf { n.browsable }
                    ?: actionItem(n.id, n.title)
            }
            AutoBrowse.TAB_LIBRARY -> return AutoBrowse.libraryChildren().map { folder(it.id, it.title) }
            AutoBrowse.TAB_FAVORITES -> return AutoBrowse.favoritesChildren().map { folder(it.id, it.title) }
            AutoBrowse.TAB_DOWNLOADS -> return AutoBrowse.downloadsChildren().map { folder(it.id, it.title) }
            AutoBrowse.LIB_PLAYLISTS -> {
                val pinned = listOf(
                    folder(AutoBrowse.collectionNode("pl", FAVORITE_SONGS_PLAYLIST_ID), "Favorite songs"),
                    folder(AutoBrowse.collectionNode("pl", CACHED_SONGS_PLAYLIST_ID), "Cached"),
                )
                val lists = (repo.playlists() as? Result.Ok)?.value.orEmpty()
                return pinned + lists.map { p ->
                    folder(AutoBrowse.collectionNode("pl", p.id), "${p.name} (${p.songCount})", coverUrl(p.coverArt, 256))
                }
            }
            AutoBrowse.LIB_ALBUMS -> {
                val albums = (repo.albumList("alphabeticalByName", 100, 0) as? Result.Ok)?.value.orEmpty()
                return albums.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.LIB_ARTISTS -> {
                val artists = (repo.artists() as? Result.Ok)?.value.orEmpty()
                return artists.map { a ->
                    folder(AutoBrowse.collectionNode("ar", a.id), "${a.name} (${a.albumCount})", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.HOME_RECENT -> {
                val recent = playerManager.recentSnapshot().takeLast(50).reversed()
                return recent.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.HOME_NEWEST -> {
                val albums = (repo.albumList("newest", 20, 0) as? Result.Ok)?.value.orEmpty()
                return albums.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.HOME_RANDOM -> {
                val tracks = (repo.randomSongs(50) as? Result.Ok)?.value.orEmpty()
                return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.HOME_RADIO -> return listOf(actionItem(ACTION_RADIO_NODE, "Play radio mix"))
            AutoBrowse.FAV_SONGS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.tracks.orEmpty()
                return favs.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.FAV_ALBUMS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.albums.orEmpty()
                return favs.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.FAV_ARTISTS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.artists.orEmpty()
                return favs.map { a ->
                    folder(AutoBrowse.collectionNode("ar", a.id), a.name, coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.DL_TRACKS -> {
                val done = downloads.completedTracks()
                return done.mapIndexed { i, e ->
                    trackPlaceholder(e.toDomainTrack(), parentId, i)
                }
            }
        }
        // Collection tracks (playlist/album/artist/mix).
        val ref = AutoBrowse.collectionRefOf(parentId)
        if (ref != null) {
            val (kind, id) = ref
            if (kind == "pl" && (id == FAVORITE_SONGS_PLAYLIST_ID || id == CACHED_SONGS_PLAYLIST_ID)) {
                val tracks = if (id == FAVORITE_SONGS_PLAYLIST_ID) {
                    (repo.starred() as? Result.Ok)?.value?.tracks.orEmpty()
                } else {
                    // Cached collection: fully cached songs from Room.
                    cachedTracksSnapshot()
                }
                return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            val tracks = collectionTracks(kind, id)
            return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
        }
        return emptyList()
    }

    private suspend fun cachedTracksSnapshot(): List<Track> =
        runCatching { cachedTracks.all().map { it.toTrack() } }.getOrDefault(emptyList())

    /** Non-browsable playable action (e.g. "Play radio mix"). */
    private fun actionItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(androidx.core.os.bundleOf(EXTRA_ACTION to id))
                    .build(),
            )
            .build()

    /**
     * Resolves an Auto play request. Normal placeholders become playable items
     * (same builder as phone playback) and the queue is mirrored into
     * [PlayerManager]; the "start radio" action boots the radio instead and the
     * car's stale set is answered with the live session playlist so nothing the
     * radio just queued is clobbered.
     */
    private suspend fun resolvePlayRequest(mediaItems: List<MediaItem>): List<MediaItem> {
        val action = mediaItems.firstOrNull { it.mediaId == ACTION_RADIO_NODE }
        if (action != null) {
            val seed = playerManager.current.value
                ?: (repo.randomSongs(1) as? Result.Ok)?.value?.firstOrNull()
            if (seed != null) {
                // Boot radio through the phone path (passthrough-safe), then
                // answer the car's set with the same seed item: both writers
                // converge on [seed] and the background batches append on top.
                playerManager.startRadio(seed)
                val queueId = playerManager.queue.value.firstOrNull()?.queueId ?: 0L
                val seedItem = resolveTrack(seed, queueId)
                if (seedItem != null) {
                    return listOf(seedItem)
                }
            }
            return mediaItems
        }
        val tracks = mediaItems.mapNotNull { item ->
            val id = songIdOf(item) ?: return@mapNotNull null
            resolvedSongs[id]
                ?: TrackMediaItems.toTrack(item)?.copy(id = id)
        }
        if (tracks.isEmpty()) return mediaItems
        val parent = mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString(EXTRA_PARENT_ID)
        val shuffle = parent?.removePrefix("search::")?.let { shuffleQueries.remove(it) } == true
        // Drop offline-unresolvable entries, never play dead items.
        val playableTracks = if (isOffline()) {
            tracks.filter { it.localUri != null || downloads.localFile(it.id) != null }
        } else {
            tracks
        }
        if (playableTracks.isEmpty()) return mediaItems
        val queueItems = playerManager.adoptExternalQueue(playableTracks, "Android Auto")
        val resolved = queueItems.mapNotNull { item -> resolveTrack(item.song, item.queueId) }
        if (resolved.isEmpty()) return mediaItems
        if (shuffle) playerManager.setShuffle(true)
        return resolved
    }

    /** Coil-backed artwork for the session (notification + Auto now-playing). */
    private inner class CoilBitmapLoader : androidx.media3.common.util.BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean =
            mimeType.startsWith("image/")

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                val bmp = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = ImageRequest.Builder(this@PlayerService)
                            .data(data)
                            .size(256, 256)
                            .build()
                        (imageLoader.execute(req).drawable?.toBitmap())
                            ?: BitmapFactory.decodeByteArray(data, 0, data.size)
                    }
                }.getOrNull() ?: placeholderBitmap()
                future.set(bmp)
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
            loadBitmapInternal(uri, coverKey = null)

        override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
            val data = metadata.artworkData
            if (data != null) return decodeBitmap(data)
            val uri = metadata.artworkUri ?: return null
            val coverKey = metadata.extras?.getString(TrackMediaItems.EXTRA_COVER_ART)
                ?.takeIf { it.isNotBlank() }
            return loadBitmapInternal(uri, coverKey)
        }

        private fun loadBitmapInternal(uri: Uri, coverKey: String?): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                val bmp = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = ImageRequest.Builder(this@PlayerService)
                            .data(uri.toString())
                            .size(256, 256)
                            .apply {
                                if (!coverKey.isNullOrBlank()) {
                                    tag(CoverCacheKey::class.java, CoverCacheKey(coverKey))
                                    GlobalId.profileIdOrNull(coverKey)?.let { pid ->
                                        tag(ProfileIdTag::class.java, ProfileIdTag(pid))
                                        addHeader(ProfileAuthInterceptor.HEADER_PROFILE_ID, pid)
                                    }
                                }
                            }
                            .build()
                        imageLoader.execute(req).drawable?.toBitmap()
                    }
                }.getOrNull() ?: placeholderBitmap()
                future.set(bmp)
            }
            return future
        }

        private fun placeholderBitmap(): Bitmap {
            val d = getDrawable(R.drawable.art_placeholder)
            return d?.toBitmap(256, 256) ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        }
    }

    companion object {
        const val TAG = "PlayerCache"
        const val COMMAND_FAVORITE = "vusic_favorite"
        const val COMMAND_RADIO = "vusic_radio"
        const val ACTION_RADIO_NODE = "auto_action_radio"
        const val EXTRA_SONG_ID = "auto_song_id"
        const val EXTRA_PARENT_ID = "auto_parent_id"
        const val EXTRA_ACTION = "auto_action"
        private const val STOP_REPORT_TIMEOUT_MS = 2_500L

        internal suspend fun submitPlayOrRecordLocal(
            id: String,
            trackProvider: () -> Track?,
            repo: com.feishin.mobile.domain.repo.MusicDataRepository,
            localHistoryStore: com.feishin.mobile.data.history.LocalHistoryStore,
        ) {
            if (repo.capabilitiesFor(id).supportsScrobbling) {
                repo.scrobble(id, submission = true)
            } else {
                val track = trackProvider() ?: return
                localHistoryStore.recordPlay(track)
            }
        }
    }
}

/**
 * Pure session state machine for Jellyfin `/Sessions/Playing` -> `/Progress` -> `/Stopped` reporting.
 */
internal class JellyfinSessionCoordinator(
    private val clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val sessionIdFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val progressIntervalMs: Long = 15_000L,
) {
    data class ActiveSession(
        val trackId: String,
        val playSessionId: String,
        val isDirect: Boolean,
        val lastPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val lastProgressSentRealtimeMs: Long = 0L,
    )

    var active: ActiveSession? = null
        private set

    fun transitionTo(
        newTrackId: String?,
        isJellyfin: Boolean,
        isDirect: Boolean,
        durationMs: Long,
    ): Pair<ActiveSession?, ActiveSession?> {
        val stopped = active
        active = null
        if (newTrackId == null || !isJellyfin) {
            return stopped to null
        }
        val started = ActiveSession(
            trackId = newTrackId,
            playSessionId = sessionIdFactory(),
            isDirect = isDirect,
            lastPositionMs = 0L,
            durationMs = durationMs.coerceAtLeast(0L),
            lastProgressSentRealtimeMs = clockMs(),
        )
        active = started
        return stopped to started
    }

    fun onTick(trackId: String, positionMs: Long, durationMs: Long): ActiveSession? {
        val cur = active ?: return null
        if (cur.trackId != trackId) return null
        val now = clockMs()
        val pos = positionMs.coerceAtLeast(0L)
        val dur = if (durationMs > 0L) durationMs else cur.durationMs
        val due = now - cur.lastProgressSentRealtimeMs >= progressIntervalMs
        val updated = cur.copy(
            lastPositionMs = pos,
            durationMs = dur,
            lastProgressSentRealtimeMs = if (due) now else cur.lastProgressSentRealtimeMs,
        )
        active = updated
        return if (due) updated else null
    }

    fun onPauseOrSeek(trackId: String, positionMs: Long, durationMs: Long): ActiveSession? {
        val cur = active ?: return null
        if (cur.trackId != trackId) return null
        val updated = cur.copy(
            lastPositionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs > 0L) durationMs else cur.durationMs,
            lastProgressSentRealtimeMs = clockMs(),
        )
        active = updated
        return updated
    }

    fun onEnded(trackId: String?, playerDurationMs: Long): Pair<ActiveSession, Long>? {
        val cur = active ?: return null
        if (trackId != null && cur.trackId != trackId) return null
        active = null
        val stopPositionMs = maxOf(cur.durationMs, playerDurationMs.coerceAtLeast(0L), cur.lastPositionMs)
        return cur to stopPositionMs
    }

    fun drainOnDestroy(currentTrackId: String?, playerPositionMs: Long): Pair<ActiveSession, Long>? {
        val cur = active ?: return null
        active = null
        val pos = if (currentTrackId == cur.trackId && playerPositionMs > 0L) {
            maxOf(cur.lastPositionMs, playerPositionMs)
        } else {
            cur.lastPositionMs
        }
        return cur to pos
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
                val key = dataSpec.key ?: StableCacheKey.keyFor(dataSpec.uri.toString())
                VLog.d(PlayerService.TAG, "upstream open key=$key uri=${dataSpec.uri.host}${dataSpec.uri.path}")
                return inner.open(dataSpec)
            }
        }
    }
}

private class RoutingDataSourceFactory(
    private val localFactory: DataSource.Factory,
    private val streamFactory: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return RoutingDataSource(
            localDataSource = localFactory.createDataSource(),
            streamDataSource = streamFactory.createDataSource(),
        )
    }
}

private class RoutingDataSource(
    private val localDataSource: DataSource,
    private val streamDataSource: DataSource,
) : DataSource {
    private var activeDelegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        localDataSource.addTransferListener(transferListener)
        streamDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = if (PlaybackSource.shouldBypassStreamCache(dataSpec.uri.scheme)) {
            localDataSource
        } else {
            streamDataSource
        }
        activeDelegate = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return checkNotNull(activeDelegate) { "RoutingDataSource is not open" }.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return activeDelegate?.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeDelegate?.responseHeaders ?: emptyMap()
    }

    override fun close() {
        try {
            activeDelegate?.close()
        } finally {
            activeDelegate = null
        }
    }
}
