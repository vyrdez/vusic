@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.feishin.mobile.core.common.QueueCommit
import com.feishin.mobile.core.common.QueueOps
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.common.RadioProgrammer
import com.feishin.mobile.core.common.RadioTrigger
import com.feishin.mobile.core.common.RadioMemory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.ShuffleModel
import com.feishin.mobile.core.common.ShufflePlan
import com.feishin.mobile.core.common.ShuffleState
import com.feishin.mobile.core.common.NetworkClass
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.PlaybackSource
import com.feishin.mobile.core.common.ReplayGain
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.core.common.OfflineRadio
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.core.network.StreamUrlProvider
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.queue.QueueStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class QueueItem(val song: Track, val queueId: Long)

/**
 * App-side view of playback. Talks to [PlayerService] through a [MediaController],
 * so the app process and the service can come and go independently while the
 * session (and its notification) survive.
 *
 * Shuffle is implemented app-side (the controller's shuffle stays off) so the
 * visible queue order always matches the actual playback order. Repeat maps
 * straight onto the controller.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val creds: CredentialsStore,
    private val settings: SettingsStore,
    private val queueStore: QueueStore,
    private val repo: MusicDataRepository,
    private val fetcher: RadioFetcher,
    private val downloads: com.feishin.mobile.data.download.DownloadRepository,
    private val network: com.feishin.mobile.data.network.NetworkMonitor,
    private val prefetch: PrefetchManager,
    private val cachedTracks: com.feishin.mobile.data.cache.CachedTrackStore,
    private val streamCache: androidx.media3.datasource.cache.SimpleCache,
    private val messenger: com.feishin.mobile.ui.components.UiMessenger,
) {
    // MUST stay on Dispatchers.Main: every command here (setMediaItems, seekTo,
    // play, currentPosition, ...) touches the MediaController, which throws
    // "MediaController method is called from a wrong thread" off the main
    // thread. CrashGuard would swallow that and playback would silently never
    // start, so keep the dispatcher explicit and the handler beside it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.feishin.mobile.core.common.CrashGuard.handler)
    internal companion object {
        const val TAG = "PlayerManager"

        /**
         * Reorders [player]'s timeline around the active item at [currentIdx] ([keepId])
         * using prefix/suffix removal and insertion so the active item is never removed,
         * replaced, or re-prepared (0ms audio cutout).
         */
        internal fun applyTimelineReorder(
            player: Player,
            before: List<QueueItem>,
            currentIdx: Int,
            keepId: Long?,
            reordered: List<QueueItem>,
            reorderedMediaItems: List<MediaItem>,
            mediaItemLookup: (Long) -> MediaItem? = { null },
            chunkForBoundedController: Boolean = false,
        ): Int {
            if (before.isEmpty() || reordered.isEmpty()) return 0
            val safeBeforeIdx = currentIdx.coerceIn(0, before.lastIndex)
            val plan = ShufflePlan.sliceAroundKeep(before.size, safeBeforeIdx, reordered, keepId) { it.queueId }
            val targetIndex = plan.targetIndex

            val playerCount = player.mediaItemCount.takeIf { it > 0 } ?: before.size
            val liveCurIdx = if (player.mediaItemCount > 0 && player.currentMediaItemIndex in 0 until player.mediaItemCount) {
                player.currentMediaItemIndex
            } else {
                safeBeforeIdx.coerceIn(0, maxOf(0, playerCount - 1))
            }

            // 1. Remove suffix after playing item
            if (liveCurIdx + 1 < playerCount) {
                player.removeMediaItems(liveCurIdx + 1, playerCount)
            }
            // 2. Remove prefix before playing item (active item is now at index 0)
            if (liveCurIdx > 0) {
                player.removeMediaItems(0, liveCurIdx)
            }
            // 3. Insert new prefix before playing item (active item shifts to targetIndex)
            val headMediaItems = if (reorderedMediaItems.size == reordered.size) {
                reorderedMediaItems.subList(0, targetIndex)
            } else {
                plan.head.mapNotNull { qi -> mediaItemLookup(qi.queueId) }
            }
            if (headMediaItems.isNotEmpty()) {
                if (!chunkForBoundedController || headMediaItems.size <= QueueWindow.DEFAULT_MAX_WINDOW) {
                    player.addMediaItems(0, headMediaItems)
                } else {
                    var offset = 0
                    while (offset < headMediaItems.size) {
                        val end = minOf(headMediaItems.size, offset + QueueWindow.DEFAULT_MAX_WINDOW)
                        player.addMediaItems(offset, headMediaItems.subList(offset, end))
                        offset = end
                    }
                }
            }
            // 4. Insert new suffix after playing item
            val tailMediaItems = if (reorderedMediaItems.size == reordered.size) {
                reorderedMediaItems.subList(targetIndex + 1, reorderedMediaItems.size)
            } else {
                plan.tail.mapNotNull { qi -> mediaItemLookup(qi.queueId) }
            }
            if (tailMediaItems.isNotEmpty()) {
                val insertStart = targetIndex + 1
                if (!chunkForBoundedController || tailMediaItems.size <= QueueWindow.DEFAULT_MAX_WINDOW) {
                    player.addMediaItems(insertStart, tailMediaItems)
                } else {
                    var offset = 0
                    while (offset < tailMediaItems.size) {
                        val end = minOf(tailMediaItems.size, offset + QueueWindow.DEFAULT_MAX_WINDOW)
                        player.addMediaItems(insertStart + offset, tailMediaItems.subList(offset, end))
                        offset = end
                    }
                }
            }
            return targetIndex
        }
    }
    private var queueSeq = 0L
    private var restored = false

    /** Last played song ids (cap [RadioProgrammer.RECENT_HISTORY_CAP]) for radio de-dup. */
    private val recentTracks = ArrayDeque<Track>()
    /**
     * Every song radio has played or queued this session. Unlike [recentTracks]
     * this is scoped to the radio session (not the last 50 plays), so a long
     * session never loops back to songs it already played.
     */
    private val radioPlayedIds = RadioMemory()
    private val radioTrigger = RadioTrigger()
    private var radioFailures = 0

    /** ReplayGain volume, before the crossfade ramp is applied. */
    @Volatile private var baseVolume: Float = 1f
    @Volatile private var crossfadeFactor: Float = 1f

    /**
     * Single source of truth for app-side queue shuffle and original catalog restoration.
     */
    @Volatile private var shuffleState = ShuffleState()
    val shuffleOrder: List<Long>?
        get() = shuffleState.originalIds.takeIf { _shuffle.value && it.isNotEmpty() }
    private val shuffleGeneration = AtomicLong(0L)
    private val mediaItemCache = java.util.concurrent.ConcurrentHashMap<Long, MediaItem>()
    private var shuffleJob: kotlinx.coroutines.Job? = null

    private fun currentShuffleState(): ShuffleState {
        val displayed = _queue.value.map { it.queueId }
        val curId = currentQueueId()
        val enabled = _shuffle.value
        val orig = if (enabled) {
            shuffleState.originalIds.ifEmpty { displayed }
        } else {
            emptyList()
        }
        val anchor = if (enabled) {
            shuffleState.anchorOriginalIndex.coerceIn(0, orig.size)
        } else {
            0
        }
        return ShuffleState(
            displayedIds = displayed,
            originalIds = orig,
            currentQueueId = curId,
            shuffleEnabled = enabled,
            anchorOriginalIndex = anchor,
        )
    }

    @Volatile private var controller: MediaController? = null
    @Volatile private var localPlayer: Player? = null
    @Volatile private var connectJob: kotlinx.coroutines.Job? = null

    private fun activePlayer(): Player? = localPlayer ?: controller

    private val localPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isPlaying) scope.launch { persist() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = localPlayer ?: return
            if (_queue.value.isEmpty() && p.mediaItemCount > 0) {
                syncQueueFromController(p)
                restored = true
            }
            syncCurrentFromPlayer(p, mediaItem, fromTransition = true)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val p = localPlayer ?: return
            if (_queue.value.isEmpty() && !restored) return
            if (_queue.value.isEmpty() && p.mediaItemCount > 0) {
                syncQueueFromController(p)
            }
            syncCurrentFromPlayer(p, p.currentMediaItem)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val p = localPlayer ?: return
            if (_queue.value.isEmpty() && !restored) return
            syncCurrentFromPlayer(p, newPosition.mediaItem ?: p.currentMediaItem)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = localPlayer ?: return
            if (playbackState != Player.STATE_IDLE) _isPlaying.value = p.isPlaying
        }
    }

    fun attachLocalPlayer(exo: Player) {
        if (localPlayer === exo) return
        localPlayer?.removeListener(localPlayerListener)
        localPlayer = exo
        exo.addListener(localPlayerListener)
        if (_queue.value.isNotEmpty() || exo.mediaItemCount > 0) {
            syncCurrentFromPlayer(exo, exo.currentMediaItem)
        }
    }

    fun detachLocalPlayer(exo: Player) {
        if (localPlayer === exo) {
            exo.removeListener(localPlayerListener)
            localPlayer = null
        }
    }

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    fun livePositionMs(): Long = activePlayer()?.currentPosition?.coerceAtLeast(0) ?: _position.value

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sourceName = MutableStateFlow<String?>(null)
    /** Where the current queue is playing from (album, playlist, mix, ...). */
    val sourceName: StateFlow<String?> = _sourceName.asStateFlow()

    private val _radioActive = MutableStateFlow(false)
    /** Radio auto-DJ stays on until the user starts something else or clears. */
    val radioActive: StateFlow<Boolean> = _radioActive.asStateFlow()

    private val _radioSeed = MutableStateFlow<Track?>(null)
    val radioSeed: StateFlow<Track?> = _radioSeed.asStateFlow()

    init {
        connect()
        scope.launch { refreshDownloadedTracks() }
        // The prefetch window follows every queue change automatically: edits,
        // shuffle toggles, radio appends, skip/seek and restores all flow
        // through _queue/_currentIndex. Debounced so rapid edits coalesce.
        scope.launch {
            combine(_queue, _currentIndex) { q, i -> q to i }
                .debounce(800)
                .collect { (q, i) -> prefetch.onQueue(q, i) }
        }
        // Auto-cache played songs: once past 50%, ensure the current item is
        // fully cached (a finished song always passes 50% first).
        var autoCachedId: String? = null
        scope.launch {
            var ticks = 0
            while (true) {
                val p = activePlayer()
                val isPlaying = p?.isPlaying == true
                delay(if (isPlaying) 100 else 500)
                val c = activePlayer() ?: continue
                _position.value = c.currentPosition.coerceAtLeast(0)
                _duration.value = c.duration.takeIf { it > 0 } ?: 0L
                // Crossfade ramp + ReplayGain, recomputed on the same tick.
                if (c.isPlaying) updateVolume(_position.value, _duration.value)
                val cur = _current.value
                val dur = _duration.value
                if (c.isPlaying && cur != null && dur > 0 && _position.value >= dur / 2) {
                    val key = "${cur.id}@${_currentIndex.value}"
                    if (autoCachedId != key) {
                        autoCachedId = key
                        scope.launch {
                            val s = settings.flow.first()
                            if (s.cachePlayedSongs) {
                                val qid = currentQueueId()
                                if (qid != null) prefetch.ensureFull(cur, qid)
                            }
                        }
                    }
                }
                // Persist position cheaply: every ~6s while playing. A single
                // long in its own DataStore key, so a long session never churns
                // the whole queue JSON; it never resurrects a cleared queue.
                if (c.isPlaying && ++ticks % 60 == 0) savePosition()
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    private fun connect() {
        if (connectJob?.isActive == true) return
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        connectJob = scope.launch {
            // The first bind races the service: right after a force-stop or a
            // reinstall the session is still being torn down and buildAsync()
            // fails. Without this the controller stayed null for the rest of the
            // process and every playAll/skip from the UI silently did nothing.
            val controller0 = withTimeoutOrNull(10_000) {
                future.awaitCompat()
            }
            if (controller0 == null) {
                runCatching { future.cancel(true) }
                connectJob = null
                // Back off and try again; give up after ~30s of failures.
                var attempt = 0
                while (controller == null && attempt < 6) {
                    delay(500L * (1L shl attempt))
                    connectJob = null
                    connect()
                    delay(1_000)
                    attempt++
                }
                return@launch
            }
            connectJob = null
            val c = controller0
            onControllerReady(c)
        }
    }

    @androidx.media3.common.util.UnstableApi
    private fun onControllerReady(c: MediaController) {
        run {
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (localPlayer != null) return
                    _isPlaying.value = isPlaying
                    if (!isPlaying) scope.launch { persist() }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (localPlayer != null) return
                    // Playback can start in the service without going through
                    // playAll(): a widget / media-button press on an empty player
                    // goes through MediaSession.Callback.onPlaybackResumption,
                    // which rebuilds the persisted queue in the service. Adopt it
                    // here, or the mini player and queue sheet stay empty while
                    // audio plays.
                    if (_queue.value.isEmpty() && c.mediaItemCount > 0) {
                        syncQueueFromController(c)
                        restored = true
                    }
                    syncCurrentFromPlayer(c, mediaItem, fromTransition = true)
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (localPlayer != null) return
                    if (_queue.value.isEmpty() && !restored) return
                    if (_queue.value.isEmpty() && c.mediaItemCount > 0) {
                        syncQueueFromController(c)
                    }
                    syncCurrentFromPlayer(c, c.currentMediaItem)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (localPlayer != null) return
                    if (_queue.value.isEmpty() && !restored) return
                    syncCurrentFromPlayer(c, newPosition.mediaItem ?: c.currentMediaItem)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    if (localPlayer != null) return
                    _repeatMode.value = repeatMode
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (localPlayer != null) return
                    if (playbackState != Player.STATE_IDLE) _isPlaying.value = c.isPlaying
                }

                override fun onPlayerError(error: PlaybackException) {
                    VLog.w(TAG, "onPlayerError: ${error.message}", error)
                    if (c.playbackState == Player.STATE_IDLE && !c.hasNextMediaItem()) {
                        notifyPlaybackFailed(_current.value?.title)
                    }
                }
            })
            val p = activePlayer() ?: c
            if (p.mediaItemCount > 0) {
                // The service survived (or another client queued). The session
                // publishes a bounded window, so the controller's item list can
                // be shorter than the real queue: prefer our own snapshot and
                // only adopt the controller's items when there is none.
                scope.launch {
                    val hadSnapshot = restoreFromSnapshot(p, seedPlayer = false)
                    if (!hadSnapshot) syncQueueFromController(p)
                    syncCurrentFromPlayer(p, p.currentMediaItem)
                    _isPlaying.value = p.isPlaying
                    _repeatMode.value = p.repeatMode
                    restored = true
                    persist()
                }
            } else {
                scope.launch { restoreFromSnapshot(p) }
            }
        }
    }

    /**
     * Resolves `_current` and `_currentIndex` from the player's current [MediaItem]:
     * 1. `EXTRA_QUEUE_ID` via [TrackMediaItems.queueIdOf]
     * 2. `mediaId` (closest to `_currentIndex` for duplicate songs)
     * 3. Never indexes full `_queue` with window-relative `c.currentMediaItemIndex`
     *    when `_queue.value.size > c.mediaItemCount`.
     */
    private fun syncCurrentFromPlayer(
        c: Player,
        mediaItem: MediaItem? = c.currentMediaItem,
        fromTransition: Boolean = false,
    ) {
        val p = localPlayer ?: c
        val item = mediaItem ?: p.currentMediaItem
        val q = _queue.value
        if (q.isEmpty() && item == null) return
        val prevIdx = _currentIndex.value
        val prevTrackId = _current.value?.id
        val prevQueueId = q.getOrNull(prevIdx)?.queueId

        val qid = item?.let { TrackMediaItems.queueIdOf(it) }
            ?: p.currentMediaItem?.let { TrackMediaItems.queueIdOf(it) }
        val mediaId = item?.mediaId?.takeIf { it.isNotBlank() }
            ?: p.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
        val fallbackTrack = item?.let { TrackMediaItems.toTrack(it) }
            ?: p.currentMediaItem?.let { TrackMediaItems.toTrack(it) }

        val resolved = QueueWindow.resolveCurrent(
            queue = q,
            currentIndex = prevIdx,
            queueId = qid,
            mediaId = mediaId,
            controllerMediaItemIndex = p.currentMediaItemIndex,
            controllerMediaItemCount = p.mediaItemCount,
            fallbackTrack = fallbackTrack,
        )
        _currentIndex.value = resolved.index
        _current.value = resolved.track
        _position.value = p.currentPosition.coerceAtLeast(0L)
        p.duration.takeIf { it > 0L }?.let { _duration.value = it }

        if (com.feishin.mobile.BuildConfig.DEBUG) {
            VLog.d(
                "NowPlayingProbe",
                "UI_CURRENT id=${resolved.track?.id} title=${resolved.track?.title} idx=${resolved.index} qSize=${q.size}",
            )
        }

        val newQueueId = q.getOrNull(resolved.index)?.queueId
        if (newQueueId != null) {
            shuffleState = ShuffleModel.select(currentShuffleState(), newQueueId)
        }
        val changed = fromTransition ||
            resolved.index != prevIdx ||
            resolved.track?.id != prevTrackId ||
            newQueueId != prevQueueId
        if (changed) {
            resolved.track?.let { recordRecent(it) }
            scope.launch { applyReplayGain(resolved.track) }
            scope.launch { cachedTracks.touchPlayed(resolved.track?.id.orEmpty()) }
            maybeAppendRadio()
            persistAsync()
        }
    }

    /**
     * The controller for the current command, waiting briefly for the first
     * bind. A tap during the first seconds after a cold start used to be dropped
     * on the floor (`controller ?: return@launch`).
     */
    private suspend fun awaitController(): Player? {
        activePlayer()?.let { return it }
        repeat(24) {
            delay(125)
            activePlayer()?.let { return it }
        }
        return activePlayer()
    }

    /** Guava ListenableFuture -> coroutine, without pulling in coroutines-guava. */
    private suspend fun <T> ListenableFuture<T>.awaitCompat(): T? =
        suspendCancellableCoroutine { cont ->
            addListener({
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
            cont.invokeOnCancellation { runCatching { cancel(true) } }
        }

    /** Rebuilds the domain queue mirror from the controller's media items. */
    private fun syncQueueFromController(c: Player) {
        val p = localPlayer ?: c
        val existing = _queue.value.associateBy { it.song.id }
        val rebuilt = ArrayList<QueueItem>(p.mediaItemCount)
        var maxId = queueSeq - 1
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val id = item.mediaId
            val known = existing[id]?.song
            val fromItem = TrackMediaItems.toTrack(item)
            val merged = when {
                known != null && fromItem != null -> known.copy(
                    title = fromItem.title,
                    artist = fromItem.artist,
                    album = fromItem.album,
                    durationSec = fromItem.durationSec,
                )
                known != null -> known
                fromItem != null -> fromItem
                else -> null
            }
            if (merged != null) {
                // Keep the original per-entry key when the service still has it;
                // otherwise mint a fresh one.
                val qid = TrackMediaItems.queueIdOf(item) ?: queueSeq++
                maxId = maxOf(maxId, qid)
                rebuilt += QueueItem(merged, qid)
            }
        }
        _queue.value = rebuilt
        // Never reuse a key that the surviving queue already owns.
        queueSeq = maxOf(queueSeq, maxId + 1)
        val liveIds = rebuilt.map { it.queueId }
        shuffleState = if (_shuffle.value) {
            shuffleState.copy(
                displayedIds = liveIds,
                originalIds = shuffleState.originalIds.ifEmpty { liveIds },
                currentQueueId = currentQueueId(),
            )
        } else {
            ShuffleState(
                displayedIds = liveIds,
                originalIds = emptyList(),
                currentQueueId = currentQueueId(),
                shuffleEnabled = false,
            )
        }
    }

    /**
     * Restores the persisted queue after a process kill. Rebuilds fresh (single-use)
     * stream URLs, seeks to the saved position, and stays paused — never auto-plays.
     * The per-entry queue keys and the shuffle snapshot are restored verbatim so a
     * shuffle toggle after restart behaves exactly as before the kill.
     */
    private suspend fun restoreFromSnapshot(c: Player, seedPlayer: Boolean = true): Boolean {
        // Give the credentials up to 10s to arrive (first launch, slow disk);
        // if they never do, keep the snapshot for the next launch instead of
        // silently dropping it forever.
        val credsReady = withTimeoutOrNull(10_000) { creds.state.first { it != null } } != null
        if (restored) {
            return true
        }
        // Credentials not ready within 10s: don't permanently bail, leave the
        // snapshot for the next launch (restored stays false so a reconnect
        // retries).
        if (!credsReady) {
            return false
        }
        val snapshot = runCatching { queueStore.load() }.getOrNull()
        if (snapshot == null) return false
        val persisted = snapshot.tracks
        val tracks = persisted.map { it.toDomain() }
        var maxId = maxOf(queueSeq - 1, snapshot.queueSeq - 1)
        val entries = ArrayList<QueueItem>(persisted.size)
        val built = ArrayList<Pair<QueueItem, MediaItem>>(persisted.size)
        for (p in persisted) {
            val track = p.toDomain()
            val qid = p.queueId.takeIf { it >= 0 } ?: queueSeq++
            maxId = maxOf(maxId, qid)
            val qi = QueueItem(track, qid)
            val mi = toMediaItem(track, qid) ?: continue
            entries += qi
            built += qi to mi; mediaItemCache[qid] = mi
        }
        if (built.isEmpty()) return false
        queueSeq = maxOf(queueSeq, maxId + 1)
        // Publish the mirror + current item before touching the player, so the
        // queue sheet / mini player show something immediately.
        _queue.value = entries
        _currentIndex.value = snapshot.currentIndex.coerceIn(0, entries.size - 1)
        _current.value = entries.getOrNull(_currentIndex.value)?.song
        _sourceName.value = snapshot.sourceName
        _repeatMode.value = snapshot.repeatMode
        _radioActive.value = snapshot.radioActive
        _radioSeed.value = snapshot.radioSeedId?.let { id -> tracks.find { it.id == id } }
        _shuffle.value = snapshot.shuffleActive
        val liveIds = entries.map { it.queueId }
        val liveSet = liveIds.toHashSet()
        val restoredOrig = if (snapshot.shuffleActive) {
            snapshot.shuffleOrder.filter { it in liveSet }.ifEmpty { liveIds }
        } else {
            emptyList()
        }
        val curQid = entries.getOrNull(_currentIndex.value)?.queueId
        val anchorIdx = if (snapshot.shuffleActive && curQid != null) {
            restoredOrig.indexOf(curQid).takeIf { it >= 0 } ?: 0
        } else 0
        shuffleState = ShuffleState(
            displayedIds = liveIds,
            originalIds = restoredOrig,
            currentQueueId = curQid,
            shuffleEnabled = snapshot.shuffleActive,
            anchorOriginalIndex = anchorIdx,
        )
        val p = localPlayer ?: c
        _duration.value = p.duration.takeIf { it > 0 } ?: 0L
        if (seedPlayer) {
            _position.value = snapshot.positionMs
            val items = built.map { it.second }
            seedQueue(p, items, _currentIndex.value, snapshot.positionMs)
            VLog.d(TAG, "restore: ${entries.size} items, index=${_currentIndex.value} pos=${snapshot.positionMs}")
        } else {
            // The service still owns a live queue: only fill the mirror, never
            // replace what is playing.
            _position.value = p.currentPosition.coerceAtLeast(0L)
        }
        restored = true
        return true
    }

    /**
     * Seeds the session queue without putting the whole list in one Binder
     * transaction. [MediaItem]s carry titles, artwork URIs and extras, so a
     * multi-thousand-song queue has to be appended in chunks or
     * `setMediaItems` throws [android.os.TransactionTooLargeException].
     */
    private fun seedQueue(c: Player, items: List<MediaItem>, index: Int, positionMs: Long) {
        setMediaItemsChunked(c, items, index, positionMs)
        c.pause()
    }

    /**
     * Sets [items] on [c] in chunks of at most [QueueWindow.DEFAULT_MAX_WINDOW]
     * anchored at [startIndex] so no Binder IPC exceeds 100 items and the active
     * item at [startIndex] starts at [positionMs] without restarting.
     */
    private fun setMediaItemsChunked(
        c: Player,
        items: List<MediaItem>,
        startIndex: Int,
        positionMs: Long,
    ) {
        if (items.isEmpty()) return
        val start = startIndex.coerceIn(0, items.lastIndex)
        val local = localPlayer
        if (local != null) {
            local.setMediaItems(items, start, positionMs)
            return
        }
        if (items.size <= QueueWindow.DEFAULT_MAX_WINDOW) {
            c.setMediaItems(items, start, positionMs)
            return
        }
        val plan = QueueWindow.chunkedSetPlan(items.size, start)
        c.setMediaItems(
            items.subList(plan.initialRange.first, plan.initialRange.last + 1),
            0,
            positionMs,
        )
        for (r in plan.suffixRanges) {
            c.addMediaItems(items.subList(r.first, r.last + 1))
        }
        for (r in plan.prefixRangesReversed) {
            c.addMediaItems(0, items.subList(r.first, r.last + 1))
        }
    }

    /**
     * Seeks [c] to [fullIndex] in `_queue` by translating full-queue coordinates
     * into bounded-window steps via [QueueWindow.seekSteps] (never double-adding
     * `windowStart`).
     */
    private fun seekToQueueIndex(
        c: Player,
        fullIndex: Int,
        positionMs: Long = 0L,
        fromFullIndex: Int = _currentIndex.value,
    ) {
        val q = _queue.value
        if (q.isEmpty()) return
        val target = fullIndex.coerceIn(0, q.lastIndex)
        val local = localPlayer
        if (local != null) {
            local.seekTo(target, positionMs)
            return
        }
        val liveFrom = c.currentMediaItem?.let { item ->
            TrackMediaItems.queueIdOf(item)?.let { qid ->
                q.indexOfFirst { it.queueId == qid }.takeIf { it >= 0 }
            }
        } ?: fromFullIndex
        val steps = QueueWindow.seekSteps(
            targetFullIndex = target,
            fullCount = q.size,
            currentFullIndex = liveFrom,
        )
        if (steps.isEmpty()) return
        for (i in 0 until steps.lastIndex) {
            c.seekTo(steps[i], 0L)
        }
        c.seekTo(steps.last(), positionMs)
    }

    internal suspend fun persist() {
        val q = _queue.value
        // A cold start shows an EMPTY mirror until the restore has run: never
        // wipe a persisted snapshot because the mirror hasn't been filled yet.
        if (q.isEmpty() && !restored) return
        if (q.isEmpty()) {
            runCatching { queueStore.clear() }
            return
        }
        val liveIds = q.map { it.queueId }
        val origIds = if (_shuffle.value) {
            shuffleState.originalIds.ifEmpty { liveIds }
        } else {
            emptyList()
        }
        runCatching {
            queueStore.save(
                q.map { it.song },
                _currentIndex.value.coerceIn(0, q.size - 1),
                _position.value,
                _sourceName.value,
                _radioActive.value,
                _radioSeed.value?.id,
                queueIds = liveIds,
                shuffleActive = _shuffle.value,
                shuffleOrder = origIds,
                queueSeq = queueSeq,
                repeatMode = _repeatMode.value,
            )
        }
    }

    /** Captures live player position on the main thread before service teardown. */
    fun snapshotPositionFromPlayer() {
        runCatching {
            activePlayer()?.currentPosition?.coerceAtLeast(0L)?.let { _position.value = it }
        }
    }

    private var persistJob: Job? = null
    private fun persistAsync(debounceMs: Long = 500L) {
        persistJob?.cancel()
        persistJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            persist()
        }
    }

    /** Cheap position-only write (every few seconds while playing). */
    private suspend fun savePosition() = queueStore.savePosition(_position.value)

    /**
     * Synchronous save used from the service lifecycle (onTaskRemoved / onDestroy),
     * which are not coroutine contexts. Best effort, bounded by [timeoutMs].
     */
    fun saveBlocking(timeoutMs: Long = 1_500L) {
        runBlocking { withTimeoutOrNull(timeoutMs) { persist() } }
    }

    private suspend fun streamParams(): Pair<Int, String> {
        val s = settings.flow.first()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val unmetered = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        return if (unmetered) s.wifiBitrate to s.wifiFormat else s.mobileBitrate to s.mobileFormat
    }

    /** Fresh, signed stream URL (stream auth params are single-use). */
    private suspend fun streamUrlFor(track: Track): String? {
        val c = creds.state.value ?: return null
        // A fully cached copy was stored at a specific quality. Offline, or when
        // the network would pick a DIFFERENT quality than the cached copy, using
        // the cached quality is what makes the CacheDataSource hit instead of
        // silently trying the (unreachable) network.
        val cachedQuality = if (isOffline()) cachedTracks.cachedQuality(track.id) else null
        val (bitrate, format) = cachedQuality ?: streamParams()
        return StreamUrlProvider.build(c.serverUrl, c.username, c.password, track.id, bitrate, format)
    }

    private suspend fun toMediaItem(
        track: Track,
        queueId: Long? = null,
        completedPaths: Map<String, String>? = null,
    ): MediaItem? {
        val deviceUri = track.localUri
        // Offline files win over the network so downloads play with no connection.
        val localPath = if (deviceUri == null) {
            completedPaths?.get(track.id) ?: downloads.localFile(track.id)?.absolutePath
        } else null
        val localUri = deviceUri ?: localPath?.let { PlaybackSource.localUri(it) }
        val streamUrl = if (localUri == null) streamUrlFor(track) else null
        if (localUri == null && streamUrl == null) return null
        return TrackMediaItems.toMediaItem(
            track = track,
            streamUrl = localUri ?: streamUrl!!,
            coverArtUrl = coverArtUrl(track.coverArt),
            queueId = queueId,
        )
    }

    /** Applies the ReplayGain setting to the live player (per track). */
    private suspend fun applyReplayGain(track: Track?) {
        val c = activePlayer() ?: return
        if (track == null) return
        val s = settings.flow.first()
        val mode = s.effectiveReplayGain
        baseVolume = if (mode == ReplayGainMode.OFF) {
            1f
        } else {
            val gain = ReplayGain.pickGain(
                mode = mode,
                trackGain = track.replayGainTrack,
                albumGain = track.replayGainAlbum,
                fallbackDb = s.replayGainFallbackDb.toDouble(),
            )
            ReplayGain.scale(gain, s.replayGainPreampDb.toDouble(), track.replayGainPeak)
        }
        c.volume = baseVolume * crossfadeFactor
    }

    /**
     * Crossfade as a volume ramp on the single player.
     *
     * A true two-player crossfade would need a second ExoPlayer and would put
     * gapless playback, scrobble timing and queue edits at risk, so this fades
     * the tail of the outgoing track and the head of the incoming one instead.
     * It never touches the media source, so gapless/scrobble/shuffle/radio all
     * keep working (documented in PLAN.md as best-effort).
     *
     * Recomputes player volume from ReplayGain and the crossfade ramp.
     */
    private suspend fun updateVolume(positionMs: Long, durationMs: Long) {
        val c = activePlayer() ?: return
        val s = settings.flow.first()
        var factor = 1f
        if (s.crossfade && durationMs > 0) {
            val window = s.crossfadeSeconds * 1000L
            val remaining = durationMs - positionMs
            val fadeOut = if (remaining < window) (remaining.toDouble() / window).coerceIn(0.0, 1.0) else 1.0
            val fadeIn = if (positionMs < window) (positionMs.toDouble() / window).coerceIn(0.0, 1.0) else 1.0
            factor = minOf(fadeOut, fadeIn).toFloat()
        }
        crossfadeFactor = factor
        c.volume = baseVolume * factor
    }

    fun coverArtUrl(coverArtId: String?, size: Int = 600): String? {
        if (coverArtId.isNullOrBlank()) return null
        if (coverArtId.startsWith("content://") || coverArtId.startsWith("file://")) {
            return coverArtId
        }
        val c = creds.state.value ?: return null
        return CoverArtUrlProvider.build(
            c.serverUrl, c.username, c.password, coverArtId, size,
        )
    }

    /** Plays [tracks] starting at [startIndex]; the caller's list is the queue. */
    fun playAll(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null) {
        cancelRadio()
        shuffleGeneration.incrementAndGet()
        shuffleJob?.cancel()
        scope.launch {
            val c = awaitController() ?: return@launch
            if (tracks.isEmpty()) return@launch
            val catalogEntries = tracks.map { QueueItem(it, queueSeq++) }
            val completedPaths = withContext(Dispatchers.IO) {
                runCatching { downloads.completedPathMap() }.getOrDefault(emptyMap())
            }
            val (queue, mediaItems, nextShuffleState) = withContext(Dispatchers.Default) {
                val playableCatalog = ArrayList<Pair<QueueItem, MediaItem>>(catalogEntries.size)
                val byId = HashMap<Long, Pair<QueueItem, MediaItem>>(catalogEntries.size * 2)
                for (qi in catalogEntries) {
                    val mi = toMediaItem(qi.song, qi.queueId, completedPaths) ?: continue
                    mediaItemCache[qi.queueId] = mi
                    val pair = qi to mi
                    playableCatalog.add(pair)
                    byId[qi.queueId] = pair
                }
                val catalogIds = playableCatalog.map { it.first.queueId }
                val requestedStartId = if (startIndex in catalogEntries.indices && startIndex > 0) {
                    catalogEntries[startIndex].queueId.takeIf { it in byId }
                } else null
                val computedState = if (shuffle) {
                    ShuffleModel.fromPlaylistShuffle(
                        allQueueIds = catalogIds,
                        startQueueId = requestedStartId,
                    )
                } else {
                    val clampedIdx = startIndex.coerceIn(0, maxOf(0, playableCatalog.lastIndex))
                    ShuffleState(
                        displayedIds = catalogIds,
                        originalIds = emptyList(),
                        currentQueueId = playableCatalog.getOrNull(clampedIdx)?.first?.queueId,
                        shuffleEnabled = false,
                        anchorOriginalIndex = 0,
                    )
                }
                val orderedPairs = if (shuffle) {
                    computedState.displayedIds.mapNotNull { byId[it] }
                } else {
                    playableCatalog
                }
                Triple(orderedPairs.map { it.first }, orderedPairs.map { it.second }, computedState)
            }
            if (queue.isEmpty()) return@launch
            val p = localPlayer ?: c
            p.shuffleModeEnabled = false
            _queue.value = queue
            val start = if (shuffle) {
                0
            } else {
                startIndex.coerceIn(0, queue.size - 1)
            }
            setMediaItemsChunked(p, mediaItems, start, 0L)
            p.prepare()
            p.play()
            _currentIndex.value = start
            _current.value = queue.getOrNull(start)?.song
            _shuffle.value = shuffle
            shuffleState = nextShuffleState.copy(
                displayedIds = queue.map { it.queueId },
                currentQueueId = queue.getOrNull(start)?.queueId,
                shuffleEnabled = shuffle,
            )
            _current.value?.let { recordRecent(it) }
            applyReplayGain(_current.value)
            if (source != null) _sourceName.value = source
            persistAsync()
        }
    }

    /**
     * Starts radio auto-DJ: the seed plays instantly as item 1 with source
     * "Radio • <title>", and 20 more songs are fetched in the background.
     * Any previous radio session is cancelled first (fast taps can't stack).
     *
     * The initial fill holds the trigger, so the seed's own track transition
     * can't start a second concurrent fetch for the same generation.
     */
    fun startRadio(seed: Track, onLibraryEmpty: () -> Unit = {}) {
        if (activePlayer() == null) return
        playAll(listOf(seed), source = "Radio • ${seed.title}")
        val gen = radioTrigger.newSession()
        _radioActive.value = true
        _radioSeed.value = seed
        radioFailures = 0
        radioPlayedIds.clear()
        rememberRadioPlayed(seed.id)
        recordRecent(seed)
        scope.launch {
            // Offline: build the batch from downloaded songs only. Never an error.
            if (isOffline()) {
                val batch = localRadioBatch(seed, exclusions())
                if (gen != radioTrigger.currentGeneration()) return@launch
                if (batch.isNotEmpty()) {
                    radioFailures = 0
                    appendRadioBatch(batch, gen)
                    persist()
                }
                return@launch
            }
            if (!radioTrigger.tryBegin(gen)) return@launch
            try {
                var attempts = 0
                var filled = false
                var transientFailure = false
                val batchSize = settings.flow.first().radioBatchSize
                while (gen == radioTrigger.currentGeneration() && !filled && attempts < 4) {
                    attempts++
                    when (val result = fetcher.fetchBatch(seed, exclusions(), batchSize)) {
                        is Result.Ok -> {
                            val batch = result.value
                            if (gen != radioTrigger.currentGeneration()) break
                            if (batch.isNotEmpty()) {
                                radioFailures = 0
                                appendRadioBatch(batch, gen)
                                persist()
                                filled = true
                            } else {
                                break // Library truly yielded nothing.
                            }
                        }
                        is Result.Err -> {
                            // Transient (network/rate limit): back off quietly
                            // and retry; radio stays on and later transitions
                            // re-arm even if these attempts run out.
                            transientFailure = true
                            radioFailures++
                            delay(minOf(15_000L * radioFailures, 120_000L))
                        }
                    }
                }
                if (!filled && !transientFailure) onLibraryEmpty()
            } finally {
                radioTrigger.end(gen)
            }
        }
    }

    /** Stops radio mode (new user playback or cleared queue). Stale batches die. */
    private fun cancelRadio() {
        radioTrigger.cancel()
        _radioActive.value = false
        _radioSeed.value = null
        radioPlayedIds.clear()
    }

    private fun rememberRadioPlayed(id: String) {
        radioPlayedIds.remember(id)
    }

    private fun recordRecent(track: Track) {
        recentTracks.removeAll { it.id == track.id }
        recentTracks.addLast(track)
        while (recentTracks.size > RadioProgrammer.RECENT_HISTORY_CAP) recentTracks.removeFirst()
        if (_radioActive.value) rememberRadioPlayed(track.id)
    }

    private fun exclusions(): Set<String> =
        _queue.value.map { it.song.id }.toSet() + recentTracks.map { it.id }.toSet() + radioPlayedIds.snapshot()

    /** True when reads must not touch the network. */
    suspend fun isOffline(): Boolean {
        val s = runCatching { settings.settingsNow() }.getOrDefault(null)
        // Auto offline follows the live network state when the user enables it.
        val auto = s?.autoOffline == true && !network.isOnline.value
        return s?.offlineMode == true || auto || !network.isOnline.value
    }

    /** Snapshot for the prefetch window; safe to call from any state change. */
    private fun refreshPrefetchWindow() {
        prefetch.onQueue(_queue.value.toList(), _currentIndex.value)
    }

    /** Offline radio: same genre first, then any downloaded song. */
    private fun localRadioBatch(seed: Track, excludeIds: Set<String>): List<Track> {
        val downloaded = _downloadedTracks
        return OfflineRadio.pick(downloaded, seed.genre, excludeIds, RadioProgrammer.BATCH_SIZE)
    }

    /** Snapshot of downloaded tracks, refreshed alongside the queue. */
    @Volatile private var _downloadedTracks: List<Track> = emptyList()

    suspend fun refreshDownloadedTracks() {
        _downloadedTracks = downloads.completedTracks().map { it.toDomainTrack() }
    }

    /**
     * Endless radio: when playback is within two songs of the queue end, fetch
     * and append another 20 seeded from recent plays. Appending touches only
     * the tail, so playback, the current item and gapless are never disturbed.
     * Failures back off quietly and retry in the same job (bounded), so radio
     * can't die at the end of the queue; the next transition re-arms anyway.
     */
    private fun maybeAppendRadio() {
        if (!_radioActive.value) return
        if (!RadioProgrammer.shouldAppend(_currentIndex.value, _queue.value.size)) return
        val gen = radioTrigger.currentGeneration()
        if (!radioTrigger.tryBegin(gen)) return
        scope.launch {
            try {
                // Offline: keep the queue alive from downloaded songs only. No
                // retry/backoff loop is needed because this never hits the network.
                if (isOffline()) {
                    val seed = _current.value ?: _radioSeed.value
                    if (seed != null) {
                        val batch = localRadioBatch(seed, exclusions())
                        if (gen == radioTrigger.currentGeneration() && batch.isNotEmpty()) {
                            appendRadioBatch(batch, gen)
                            persist()
                        }
                    }
                    return@launch
                }
                var attempts = 0
                var appended = false
                val batchSize = settings.flow.first().radioBatchSize
                while (gen == radioTrigger.currentGeneration() && _radioActive.value &&
                    RadioProgrammer.shouldAppend(_currentIndex.value, _queue.value.size) && attempts < 6 && !appended
                ) {
                    attempts++
                    val seed = recentTracks.lastOrNull { it.id != _current.value?.id }
                        ?: _radioSeed.value ?: _current.value
                    if (seed == null) break
                    when (val result = fetcher.fetchBatch(seed, exclusions(), batchSize)) {
                        is Result.Ok -> {
                            if (gen != radioTrigger.currentGeneration()) break
                            if (result.value.isNotEmpty()) {
                                radioFailures = 0
                                appendRadioBatch(result.value, gen)
                                persist()
                                appended = true
                            } else {
                                break // Nothing to add; stop silently.
                            }
                        }
                        is Result.Err -> {
                            radioFailures++
                            delay(minOf(15_000L * radioFailures, 120_000L))
                        }
                    }
                }
            } catch (_: Exception) {
                radioFailures++
            } finally {
                radioTrigger.end(gen)
            }
        }
    }

    /** Tail-only append: never touches the current item or playback state. */
    private suspend fun appendRadioBatch(batch: List<Track>, gen: Int) {
        val c = activePlayer() ?: return
        if (gen != radioTrigger.currentGeneration() || !_radioActive.value) return
        if (batch.isEmpty() || _queue.value.isEmpty()) return
        val built = batch.mapNotNull { t ->
            val qi = QueueItem(t, queueSeq++)
            toMediaItem(t, qi.queueId)?.let { qi to it }
        }
        if (built.isEmpty() || gen != radioTrigger.currentGeneration()) return
        built.forEach { (qi, mi) -> mediaItemCache[qi.queueId] = mi }
        val baseState = currentShuffleState()
        (localPlayer ?: c).addMediaItems(built.map { it.second })
        val entries = built.map { it.first }
        _queue.value = QueueOps.appendAll(_queue.value, entries)
        shuffleState = ShuffleModel.addToQueue(baseState, entries.map { it.queueId })
        entries.forEach { rememberRadioPlayed(it.song.id) }
    }

    /** The queue key of the currently playing entry, if any. */
    private fun currentQueueId(): Long? = _queue.value.getOrNull(_currentIndex.value)?.queueId

    fun playNext(track: Track) {
        scope.launch {
            val c = awaitController() ?: return@launch
            val qi = QueueItem(track, queueSeq++)
            val item = toMediaItem(track, qi.queueId)?.also { mediaItemCache[qi.queueId] = it } ?: return@launch
            val baseState = currentShuffleState()
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            _queue.value = QueueOps.insertNext(_queue.value, qi, _currentIndex.value)
            (localPlayer ?: c).addMediaItem(at, item)
            shuffleState = ShuffleModel.playNext(baseState, listOf(qi.queueId))
            persistAsync()
        }
    }

    /** Inserts a whole list directly after the current item, atomically. */
    fun playNextAll(tracks: List<Track>) {
        scope.launch {
            val c = awaitController() ?: return@launch
            if (tracks.isEmpty()) return@launch
            val completedPaths = withContext(Dispatchers.IO) {
                runCatching { downloads.completedPathMap() }.getOrDefault(emptyMap())
            }
            val (entries, mediaItems) = withContext(Dispatchers.Default) {
                val qis = tracks.map { QueueItem(it, queueSeq++) }
                val built = qis.mapNotNull { qi ->
                    toMediaItem(qi.song, qi.queueId, completedPaths)?.let { qi to it }
                }
                built.forEach { (qi, mi) -> mediaItemCache[qi.queueId] = mi }
                built.map { it.first } to built.map { it.second }
            }
            if (mediaItems.isEmpty()) return@launch
            val baseState = currentShuffleState()
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            (localPlayer ?: c).addMediaItems(at, mediaItems)
            _queue.value = _queue.value.toMutableList().apply { addAll(at, entries) }
            shuffleState = ShuffleModel.playNext(baseState, entries.map { it.queueId })
            persistAsync()
        }
    }

    fun addToEnd(track: Track) {
        scope.launch {
            val c = awaitController() ?: return@launch
            val qi = QueueItem(track, queueSeq++)
            val item = toMediaItem(track, qi.queueId)?.also { mediaItemCache[qi.queueId] = it } ?: return@launch
            val baseState = currentShuffleState()
            (localPlayer ?: c).addMediaItem(item)
            _queue.value = QueueOps.append(_queue.value, qi)
            shuffleState = ShuffleModel.addToQueue(baseState, listOf(qi.queueId))
            persistAsync()
        }
    }

    fun addAllToEnd(tracks: List<Track>) {
        scope.launch {
            val c = awaitController() ?: return@launch
            if (tracks.isEmpty()) return@launch
            val completedPaths = withContext(Dispatchers.IO) {
                runCatching { downloads.completedPathMap() }.getOrDefault(emptyMap())
            }
            val (entries, mediaItems) = withContext(Dispatchers.Default) {
                val qis = tracks.map { QueueItem(it, queueSeq++) }
                val built = qis.mapNotNull { qi ->
                    toMediaItem(qi.song, qi.queueId, completedPaths)?.let { qi to it }
                }
                built.forEach { (qi, mi) -> mediaItemCache[qi.queueId] = mi }
                built.map { it.first } to built.map { it.second }
            }
            if (mediaItems.isEmpty()) return@launch
            val baseState = currentShuffleState()
            (localPlayer ?: c).addMediaItems(mediaItems)
            _queue.value = QueueOps.appendAll(_queue.value, entries)
            shuffleState = ShuffleModel.addToQueue(baseState, entries.map { it.queueId })
            persistAsync()
        }
    }

    fun removeAt(index: Int) {
        val c = activePlayer() ?: return
        val before = _queue.value
        val removedId = before.getOrNull(index)?.queueId ?: return
        val baseState = currentShuffleState()
        val (next, adjusted) = QueueOps.removeAt(before, index, _currentIndex.value)
        if (next === before) return
        _queue.value = next
        _currentIndex.value = adjusted
        _current.value = next.getOrNull(adjusted)?.song
        shuffleState = ShuffleModel.remove(baseState, removedId)
        mediaItemCache.remove(removedId)
        (localPlayer ?: c).removeMediaItem(index)
        persistAsync()
    }

    /**
     * Removes one entry by its queue key.
     *
     * The UI must never send a position: rows move (reorder, an earlier
     * removal, shuffle) between the frame the gesture started on and the
     * moment the dismiss settles, and a position captured earlier pointed at
     * a different song by then. A key that is already gone is a no-op, so a
     * settle that fires twice cannot take a second entry with it.
     */
    fun removeById(queueId: Long) {
        val index = _queue.value.indexOfFirst { it.queueId == queueId }
        if (index < 0) return
        removeAt(index)
    }

    fun removeByQueueId(queueId: Long) = removeById(queueId)

    fun move(from: Int, to: Int) {
        val c = activePlayer() ?: return
        val baseState = currentShuffleState()
        val (next, adjusted) = QueueOps.move(_queue.value, from, to, _currentIndex.value)
        if (next === _queue.value) return
        _queue.value = next
        _currentIndex.value = adjusted
        _current.value = next.getOrNull(adjusted)?.song
        (localPlayer ?: c).moveMediaItem(from, to)
        // Manual drag-reorder updates ONLY displayedIds and preserves originalIds
        // so turning shuffle OFF still restores the original catalog order.
        shuffleState = ShuffleModel.reorder(baseState, next.map { it.queueId })
        persistAsync()
    }

    /**
     * One-shot reorder commit for drag-and-drop. The queue UI reorders a LOCAL
     * copy during the drag (never touching the player per frame) and calls this
     * exactly once on drop with the final order of queue ids.
     *
     * Stale targets (entries added/removed mid-drag) are ignored rather than
     * applied, so a radio append racing a drop cannot corrupt the queue.
     */
    fun commitOrder(targetIds: List<Long>) {
        val before = _queue.value
        val beforeIds = before.map { it.queueId }
        val target = QueueCommit.targetIds(
            beforeIds, targetIds,
        ) ?: return
        val baseState = currentShuffleState()
        val c = activePlayer()
        if (c == null) {
            val keepId = currentQueueId()
            val reordered = ShufflePlan.applyOrder(target, before) { it.queueId }
            if (reordered.size != before.size) return
            _queue.value = reordered
            val idx = reordered.indexOfFirst { it.queueId == keepId }
            _currentIndex.value = if (idx >= 0) idx
            else _currentIndex.value.coerceIn(0, maxOf(0, reordered.size - 1))
            _current.value = reordered.getOrNull(_currentIndex.value)?.song
            shuffleState = ShuffleModel.reorder(baseState, reordered.map { it.queueId })
            return
        }
        val single = QueueCommit.singleMove(beforeIds, target)
        if (single != null) {
            move(single.first, single.second)
            return
        }
        val p = localPlayer ?: c
        val moves = ShufflePlan.moveSeq(beforeIds, target)
        var currentQueue = before
        var currentIdx = _currentIndex.value
        for ((from, to) in moves) {
            p.moveMediaItem(from, to)
            val (next, adjusted) = QueueOps.move(currentQueue, from, to, currentIdx)
            currentQueue = next
            currentIdx = adjusted
        }
        _queue.value = currentQueue
        _currentIndex.value = currentIdx
        _current.value = currentQueue.getOrNull(currentIdx)?.song
        shuffleState = ShuffleModel.reorder(baseState, currentQueue.map { it.queueId })
        persistAsync()
    }

    /** In-memory snapshot of a cleared queue, for the Undo snackbar. */
    data class ClearedQueue(
        val items: List<QueueItem>,
        val index: Int,
        val positionMs: Long,
        val sourceName: String?,
        val shuffleActive: Boolean,
        val shuffleOrder: List<Long>,
    )

    /**
     * Clears playback like [clear] but returns a snapshot that [restoreCleared]
     * can bring back. The persisted queue is cleared too, so killing the app
     * inside the Undo window stays cleared.
     */
    fun clearWithUndo(): ClearedQueue {
        val liveIds = _queue.value.map { it.queueId }
        val snap = ClearedQueue(
            items = _queue.value.toList(),
            index = _currentIndex.value.coerceIn(0, maxOf(0, _queue.value.size - 1)),
            positionMs = _position.value,
            sourceName = _sourceName.value,
            shuffleActive = _shuffle.value,
            shuffleOrder = if (_shuffle.value) shuffleState.originalIds.ifEmpty { liveIds } else emptyList(),
        )
        clear()
        return snap
    }

    /**
     * Restores a [clearWithUndo] snapshot: same queue, same item and position,
     * paused. Radio is not resumed (a clear ends the radio session by design).
     */
    fun restoreCleared(snap: ClearedQueue) {
        val c = activePlayer() ?: return
        if (snap.items.isEmpty()) return
        scope.launch {
            val built = snap.items.mapNotNull { qi ->
                toMediaItem(qi.song, qi.queueId)?.let { qi to it }
            }
            if (built.isEmpty()) return@launch
            // Restored keys stay reserved so later mints never collide.
            queueSeq = maxOf(queueSeq, (snap.items.maxOfOrNull { it.queueId } ?: -1) + 1)
            val items = built.map { it.second }
            val index = snap.index.coerceIn(0, items.size - 1)
            // setMediaItems + prepare mirrors a fresh queue; pause keeps the
            // "restored paused" contract (never auto-plays from Undo).
            val p = localPlayer ?: c
            setMediaItemsChunked(p, items, index, snap.positionMs)
            p.prepare()
            p.pause()
            val restoredQueue = built.map { it.first }
            _queue.value = restoredQueue
            _currentIndex.value = index
            _current.value = restoredQueue.getOrNull(index)?.song
            _sourceName.value = snap.sourceName
            _shuffle.value = snap.shuffleActive
            val liveIds = restoredQueue.map { it.queueId }
            val liveSet = liveIds.toHashSet()
            val restoredOrig = if (snap.shuffleActive) {
                snap.shuffleOrder.filter { it in liveSet }.ifEmpty { liveIds }
            } else emptyList()
            val curQid = restoredQueue.getOrNull(index)?.queueId
            val anchorIdx = if (snap.shuffleActive && curQid != null) {
                restoredOrig.indexOf(curQid).takeIf { it >= 0 } ?: 0
            } else 0
            shuffleState = ShuffleState(
                displayedIds = liveIds,
                originalIds = restoredOrig,
                currentQueueId = curQid,
                shuffleEnabled = snap.shuffleActive,
                anchorOriginalIndex = anchorIdx,
            )
            _position.value = snap.positionMs
            _isPlaying.value = false
            persistAsync()
        }
    }

    fun clear() {
        shuffleGeneration.incrementAndGet()
        shuffleJob?.cancel()
        activePlayer()?.clearMediaItems()
        activePlayer()?.stop()
        cancelRadio()
        _queue.value = emptyList()
        _currentIndex.value = 0
        _current.value = null
        _sourceName.value = null
        _shuffle.value = false
        shuffleState = ShuffleState()
        mediaItemCache.clear()
        _isPlaying.value = false
        _position.value = 0
        _duration.value = 0
        persistAsync()
        scope.launch { settings.setNowPlayingLyricsMode(false) }
    }

    fun notifyPlaybackFailed(songTitle: String? = null) {
        val title = songTitle ?: _current.value?.title
        val message = if (!title.isNullOrBlank()) "Playback failed: $title" else "Playback failed"
        messenger.show(message)
    }

    fun notifyOfflineUnavailable(songTitle: String? = null) {
        val title = songTitle?.takeIf { it.isNotBlank() } ?: "Track"
        messenger.show("Offline: $title is not downloaded")
    }

    private suspend fun isTrackPlayableOffline(track: Track): Boolean {
        if (track.localUri != null) return true
        if (downloads.localFile(track.id) != null) return true
        return isTrackFullyCached(track.id)
    }

    private suspend fun isTrackFullyCached(songId: String): Boolean {
        val row = cachedTracks.get(songId) ?: return false
        if (row.sizeBytes <= 0) return false
        return runCatching {
            streamCache.isCached(row.cacheKey, 0, row.sizeBytes)
        }.getOrDefault(false)
    }

    fun playAt(index: Int) {
        val c = activePlayer() ?: return
        if (index in _queue.value.indices) {
            val entry = _queue.value[index]
            val target = entry.song
            val fromIndex = _currentIndex.value
            scope.launch {
                if (isOffline() && !isTrackPlayableOffline(target)) {
                    notifyOfflineUnavailable(target.title)
                    return@launch
                }
                seekToQueueIndex(c, index, 0L, fromIndex)
                if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
                c.play()
                _currentIndex.value = index
                _current.value = target
                shuffleState = ShuffleModel.select(currentShuffleState(), entry.queueId)
                _current.value?.let { recordRecent(it) }
                scope.launch { applyReplayGain(_current.value) }
                persistAsync()
            }
        }
    }

    fun playOrToggle(queueId: Long) {
        val c = activePlayer() ?: return
        val currentItem = _queue.value.getOrNull(_currentIndex.value)
        if (currentItem != null && currentItem.queueId == queueId) {
            if (c.isPlaying || c.playWhenReady) {
                c.pause()
            } else {
                scope.launch {
                    if (isOffline() && !isTrackPlayableOffline(currentItem.song)) {
                        notifyOfflineUnavailable(currentItem.song.title)
                        return@launch
                    }
                    if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
                    c.play()
                }
            }
            return
        }
        val index = _queue.value.indexOfFirst { it.queueId == queueId }
        if (index >= 0) {
            val target = _queue.value[index].song
            val fromIndex = _currentIndex.value
            scope.launch {
                if (isOffline() && !isTrackPlayableOffline(target)) {
                    notifyOfflineUnavailable(target.title)
                    return@launch
                }
                seekToQueueIndex(c, index, 0L, fromIndex)
                if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
                c.play()
                _currentIndex.value = index
                _current.value = target
                shuffleState = ShuffleModel.select(currentShuffleState(), queueId)
                _current.value?.let { recordRecent(it) }
                scope.launch { applyReplayGain(_current.value) }
                persistAsync()
            }
        }
    }

    fun togglePlayPause() {
        val c = activePlayer() ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
            c.play()
        }
    }

    fun pause() {
        activePlayer()?.pause()
    }

    /** Gapless is an ExoPlayer property; applied live so the toggle takes effect. */
    fun applyGapless(enabled: Boolean) {
        // ExoPlayer 1.4 has no runtime gapless toggle; the setting is honoured by
        // the service when the player is built. Recorded here for the UI state.
        gaplessEnabled = enabled
    }

    @Volatile private var gaplessEnabled: Boolean = true

    fun resume() {
        val c = activePlayer() ?: return
        if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
        c.play()
    }

    fun skipToNext() {
        val c = activePlayer() ?: return
        scope.launch {
            if (isOffline()) {
                val q = _queue.value
                val cur = _currentIndex.value
                val nextPlayable = (cur + 1 until q.size).firstOrNull { isTrackPlayableOffline(q[it].song) }
                if (nextPlayable != null) {
                    seekToQueueIndex(c, nextPlayable, 0L, cur)
                    if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
                    c.play()
                    _currentIndex.value = nextPlayable
                    _current.value = q.getOrNull(nextPlayable)?.song
                } else {
                    val nextSong = q.getOrNull(cur + 1)?.song
                    notifyOfflineUnavailable(nextSong?.title)
                }
            } else {
                c.seekToNextMediaItem()
            }
        }
    }

    fun skipToPrevious() {
        val c = activePlayer() ?: return
        scope.launch {
            if (isOffline()) {
                val q = _queue.value
                val cur = _currentIndex.value
                val prevPlayable = (cur - 1 downTo 0).firstOrNull { isTrackPlayableOffline(q[it].song) }
                if (prevPlayable != null) {
                    seekToQueueIndex(c, prevPlayable, 0L, cur)
                    if (PlaybackRecovery.shouldPrepareBeforePlay(c.playbackState)) c.prepare()
                    c.play()
                    _currentIndex.value = prevPlayable
                    _current.value = q.getOrNull(prevPlayable)?.song
                } else {
                    val prevSong = q.getOrNull(cur - 1)?.song
                    notifyOfflineUnavailable(prevSong?.title)
                }
            } else {
                c.seekToPreviousMediaItem()
            }
        }
    }

    fun seekTo(ms: Long) {
        val c = activePlayer() ?: return
        runCatching {
            val dur = c.duration.takeIf { it > 0L } ?: _duration.value.takeIf { it > 0L } ?: 0L
            val targetMs = if (dur > 0L) ms.coerceIn(0L, maxOf(0L, dur)) else ms.coerceAtLeast(0L)
            c.seekTo(targetMs)
            _position.value = targetMs
        }.onFailure { e ->
            VLog.w(TAG, "seekTo failed for ms=$ms", e)
        }
    }

    /**
     * App-side shuffle backed by [ShuffleModel]. The player's own shuffleModeEnabled
     * stays off so the visible queue always matches the actual playback order.
     *
     * - Shuffle ON: places the currently playing item at index `0` (`#1`) and permutes
     *   all other items (including items that were before `currentIndex`), saving
     *   `originalIds` in [shuffleState].
     * - Shuffle OFF: restores `originalIds` with explicit fallbacks for deleted/orphan/missing
     *   items, placing the currently playing item at its original catalog slot `k`.
     * - Uses `O(N)` `HashMap` pre-indexing on `Dispatchers.Default`, latest-wins
     *   [shuffleGeneration] coalescing, and `withContext(NonCancellable + Dispatchers.Main.immediate)`
     *   atomic timeline mutation around the active item so playback never restarts.
     */
    fun setShuffle(enabled: Boolean, random: Random = Random.Default) {
        val gen = shuffleGeneration.incrementAndGet()
        shuffleJob?.cancel()
        val c = activePlayer()
        val live = _queue.value
        if (live.isEmpty()) {
            c?.shuffleModeEnabled = false
            _shuffle.value = enabled
            shuffleState = ShuffleState(shuffleEnabled = enabled)
            persistAsync()
            return
        }
        if (c == null) {
            val base = currentShuffleState()
            val nextState = if (enabled) ShuffleModel.shuffle(base, random) else ShuffleModel.unshuffle(base)
            val byId = live.associateByTo(HashMap(live.size * 2)) { it.queueId }
            val reordered = nextState.displayedIds.mapNotNull { byId[it] }
            val targetIdx = reordered.indexOfFirst { it.queueId == nextState.currentQueueId }.coerceAtLeast(0)
            _queue.value = reordered
            _currentIndex.value = targetIdx
            _current.value = reordered.getOrNull(targetIdx)?.song
            _shuffle.value = enabled
            shuffleState = nextState
            return
        }
        c.shuffleModeEnabled = false
        val liveIds = live.map { it.queueId }
        val curId = currentQueueId()
        if (enabled && shuffleState.originalIds.isEmpty()) {
            val anchor = _currentIndex.value.coerceIn(0, maxOf(0, live.lastIndex))
            shuffleState = ShuffleState(
                displayedIds = liveIds,
                originalIds = liveIds,
                currentQueueId = curId,
                shuffleEnabled = true,
                anchorOriginalIndex = anchor,
            )
        }
        _shuffle.value = enabled
        val planInput = ShuffleState(
            displayedIds = liveIds,
            originalIds = shuffleState.originalIds.ifEmpty { liveIds },
            currentQueueId = curId,
            shuffleEnabled = shuffleState.shuffleEnabled || shuffleState.originalIds.isNotEmpty(),
            anchorOriginalIndex = shuffleState.anchorOriginalIndex.coerceIn(0, liveIds.size),
        )
        shuffleJob = scope.launch {
            val planned = withContext(Dispatchers.Default) {
                if (gen != shuffleGeneration.get()) return@withContext null
                val byId = live.associateByTo(HashMap(live.size * 2)) { it.queueId }
                val nextState = if (enabled) {
                    ShuffleModel.shuffle(planInput, random)
                } else {
                    ShuffleModel.unshuffle(planInput)
                }
                if (gen != shuffleGeneration.get()) return@withContext null
                val reorderedItems = ArrayList<QueueItem>(nextState.displayedIds.size)
                val reorderedMediaItems = ArrayList<MediaItem>(nextState.displayedIds.size)
                for (qid in nextState.displayedIds) {
                    val qi = byId[qid] ?: continue
                    reorderedItems.add(qi)
                    val mi = mediaItemCache[qid]
                        ?: toMediaItem(qi.song, qi.queueId)?.also { mediaItemCache[qid] = it }
                    if (mi != null) reorderedMediaItems.add(mi)
                }
                Triple(nextState, reorderedItems, reorderedMediaItems)
            } ?: return@launch

            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (gen != shuffleGeneration.get()) return@withContext
                val (nextState, reorderedItems, reorderedMediaItems) = planned
                val p = localPlayer ?: c
                applyOrder(
                    c = p,
                    targetIds = nextState.displayedIds,
                    reorderedItems = reorderedItems,
                    reorderedMediaItems = reorderedMediaItems,
                    keepId = nextState.currentQueueId,
                )
                val committedIds = _queue.value.map { it.queueId }
                shuffleState = ShuffleState(
                    displayedIds = committedIds,
                    originalIds = if (enabled) nextState.originalIds else emptyList(),
                    currentQueueId = currentQueueId(),
                    shuffleEnabled = enabled,
                    anchorOriginalIndex = if (enabled) nextState.anchorOriginalIndex else 0,
                )
                _shuffle.value = enabled
                persistAsync()
            }
        }
    }

    /**
     * Reorders the live queue to [targetIds] mutating slices around the active item
     * using [removeMediaItems] and [addMediaItems], so the playing item is never
     * removed or re-prepared and playback continues without cutout.
     */
    private fun applyOrder(
        c: Player,
        targetIds: List<Long>,
        reorderedItems: List<QueueItem>? = null,
        reorderedMediaItems: List<MediaItem> = targetIds.mapNotNull { mediaItemCache[it] },
        keepId: Long? = currentQueueId(),
    ) {
        val p = localPlayer ?: c
        val before = _queue.value
        if (before.isEmpty()) return
        val reordered = if (reorderedItems != null && reorderedItems.size == before.size) {
            reorderedItems
        } else {
            ShufflePlan.applyOrder(targetIds, before) { it.queueId }
        }
        require(reordered.size == before.size) {
            "queue reorder must not drop entries (${before.size} -> ${reordered.size})"
        }
        val currentIdx = _currentIndex.value.coerceIn(0, before.lastIndex)
        val targetIndex = applyTimelineReorder(
            player = p,
            before = before,
            currentIdx = currentIdx,
            keepId = keepId,
            reordered = reordered,
            reorderedMediaItems = reorderedMediaItems,
            mediaItemLookup = { qid -> mediaItemCache[qid] },
            chunkForBoundedController = (localPlayer == null),
        )
        _queue.value = reordered
        _currentIndex.value = targetIndex
        _current.value = reordered.getOrNull(targetIndex)?.song
    }

    /**
     * Adopts a queue the MediaSession (Android Auto / voice) set directly on
     * the session player. The session already owns the items and the start
     * position — this only mirrors them into the app state (queue, current,
     * source, persistence) without touching the controller, so phone and car
     * never fight and existing phone behavior is unchanged.
     *
     * The start index comes along a beat later through the normal media-item
     * transition listener, which re-points at the same entry by queue key
     * (duplicates safe).
     */
    fun adoptExternalQueue(tracks: List<Track>, source: String?) {
        if (tracks.isEmpty()) return
        cancelRadio()
        shuffleGeneration.incrementAndGet()
        shuffleJob?.cancel()
        val entries = tracks.map { QueueItem(it, queueSeq++) }
        _queue.value = entries
        _currentIndex.value = 0
        _current.value = entries.firstOrNull()?.song
        _current.value?.let { recordRecent(it) }
        _shuffle.value = false
        shuffleState = ShuffleState(
            displayedIds = entries.map { it.queueId },
            originalIds = emptyList(),
            currentQueueId = entries.firstOrNull()?.queueId,
            shuffleEnabled = false,
        )
        if (source != null) _sourceName.value = source
        prefetch.onQueue(entries.toList(), 0)
        persistAsync()
    }

    /** Session-era recent tracks for Auto Home, newest last. */
    fun recentSnapshot(): List<Track> = recentTracks.toList()

    fun toggleShuffle(): Boolean {
        setShuffle(!_shuffle.value)
        return _shuffle.value
    }

    fun cycleRepeat(): Int {
        val c = activePlayer() ?: return Player.REPEAT_MODE_OFF
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = c.repeatMode
        return c.repeatMode
    }

    /** 0f..1f, for the mini player progress bar. */
    fun progress(): Float {
        val d = _duration.value
        return if (d <= 0) 0f else (_position.value.toFloat() / d).coerceIn(0f, 1f)
    }
}
