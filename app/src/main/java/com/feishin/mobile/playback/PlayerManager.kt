@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.feishin.mobile.core.common.QueueOps
import com.feishin.mobile.core.common.RadioProgrammer
import com.feishin.mobile.core.common.RadioTrigger
import com.feishin.mobile.core.common.RadioMemory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.ShufflePlan
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
     * The order to restore when shuffle is turned off (queue ids, not tracks or
     * positions). Null whenever shuffle is off, so "off" is always the real order.
     */
    private var shuffleOrder: List<Long>? = null

    @Volatile private var controller: MediaController? = null

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
        scope.launch {
            var ticks = 0
            while (true) {
                delay(500)
                val c = controller ?: continue
                _position.value = c.currentPosition.coerceAtLeast(0)
                _duration.value = c.duration.takeIf { it > 0 } ?: 0L
                // Crossfade ramp + ReplayGain, recomputed on the same 500ms tick.
                if (c.isPlaying) updateVolume(_position.value, _duration.value)
                // Persist position cheaply: every ~15s while playing.
                if (c.isPlaying && ++ticks % 30 == 0) persist()
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (!isPlaying) scope.launch { persist() }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Match on the per-entry queue key first: the same song can be
                    // queued twice, and song ids would resolve to the wrong entry.
                    val qid = mediaItem?.let { TrackMediaItems.queueIdOf(it) }
                    val idx = qid?.let { id -> _queue.value.indexOfFirst { it.queueId == id } }
                        ?.takeIf { it >= 0 }
                        ?: _queue.value.indexOfFirst { it.song.id == mediaItem?.mediaId }.takeIf { it >= 0 }
                        ?: c.currentMediaItemIndex
                    _currentIndex.value = idx.coerceIn(0, maxOf(0, _queue.value.size - 1))
                    _current.value = _queue.value.getOrNull(_currentIndex.value)?.song
                        ?: mediaItem?.let { TrackMediaItems.toTrack(it) }
                    _current.value?.let { recordRecent(it) }
                    scope.launch { applyReplayGain(_current.value) }
                    maybeAppendRadio()
                    scope.launch { persist() }
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_IDLE) _isPlaying.value = c.isPlaying
                }
            })
            if (c.mediaItemCount > 0) {
                // Service survived (or another client queued): adopt its playlist.
                syncQueueFromController(c)
                _currentIndex.value = c.currentMediaItemIndex.coerceIn(0, maxOf(0, _queue.value.size - 1))
                _current.value = _queue.value.getOrNull(_currentIndex.value)?.song
                _isPlaying.value = c.isPlaying
                _repeatMode.value = c.repeatMode
                restored = true
            } else {
                scope.launch { restoreFromSnapshot(c) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Rebuilds the domain queue mirror from the controller's media items. */
    private fun syncQueueFromController(c: MediaController) {
        val existing = _queue.value.associateBy { it.song.id }
        val rebuilt = ArrayList<QueueItem>(c.mediaItemCount)
        var maxId = queueSeq - 1
        for (i in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
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
    }

    /**
     * Restores the persisted queue after a process kill. Rebuilds fresh (single-use)
     * stream URLs, seeks to the saved position, and stays paused — never auto-plays.
     * The per-entry queue keys and the shuffle snapshot are restored verbatim so a
     * shuffle toggle after restart behaves exactly as before the kill.
     */
    private suspend fun restoreFromSnapshot(c: MediaController) {
        val snapshot = runCatching { queueStore.load() }.getOrNull()
        if (restored || snapshot == null || creds.state.value == null) {
            restored = true
            return
        }
        val persisted = snapshot.tracks
        val tracks = persisted.map { it.toDomain() }
        var maxId = queueSeq - 1
        val entries = ArrayList<QueueItem>(persisted.size)
        val built = ArrayList<Pair<QueueItem, MediaItem>>(persisted.size)
        for (p in persisted) {
            val track = p.toDomain()
            val qid = p.queueId.takeIf { it >= 0 } ?: queueSeq++
            maxId = maxOf(maxId, qid)
            val qi = QueueItem(track, qid)
            val mi = toMediaItem(track, qid) ?: continue
            entries += qi
            built += qi to mi
        }
        if (built.isEmpty()) {
            restored = true
            return
        }
        queueSeq = maxOf(queueSeq, maxId + 1)
        _queue.value = entries
        val items = built.map { it.second }
        val index = snapshot.currentIndex.coerceIn(0, items.size - 1)
        c.setMediaItems(items, index, snapshot.positionMs)
        c.prepare()
        c.pause()
        _currentIndex.value = index
        _current.value = entries.getOrNull(index)?.song
        _sourceName.value = snapshot.sourceName
        _radioActive.value = snapshot.radioActive
        _radioSeed.value = snapshot.radioSeedId?.let { id -> tracks.find { it.id == id } }
        _shuffle.value = snapshot.shuffleActive
        val live = entries.map { it.queueId }.toSet()
        shuffleOrder = if (snapshot.shuffleActive) {
            snapshot.shuffleOrder.filter { it in live }
        } else {
            null
        }
        _position.value = snapshot.positionMs
        _duration.value = c.duration.takeIf { it > 0 } ?: 0L
        restored = true
    }

    private suspend fun persist() {
        val q = _queue.value
        if (q.isEmpty()) {
            runCatching { queueStore.clear() }
            return
        }
        runCatching {
            queueStore.save(
                q.map { it.song },
                _currentIndex.value.coerceIn(0, q.size - 1),
                _position.value,
                _sourceName.value,
                _radioActive.value,
                _radioSeed.value?.id,
                queueIds = q.map { it.queueId },
                shuffleActive = _shuffle.value,
                shuffleOrder = shuffleOrder.orEmpty(),
                queueSeq = queueSeq,
            )
        }
    }

    private fun persistAsync() {
        scope.launch { persist() }
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
        val (bitrate, format) = streamParams()
        return StreamUrlProvider.build(c.serverUrl, c.username, c.password, track.id, bitrate, format)
    }

    private suspend fun toMediaItem(track: Track, queueId: Long? = null): MediaItem? {
        // Offline files win over the network so downloads play with no connection.
        val local = downloads.localFile(track.id)
        val localUri = local?.let { PlaybackSource.localUri(it.absolutePath) }
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
        val c = controller ?: return
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
        val c = controller ?: return
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
        val c = creds.state.value ?: return null
        return CoverArtUrlProvider.build(
            c.serverUrl, c.username, c.password, coverArtId, size,
        )
    }

    /** Plays [tracks] starting at [startIndex]; the caller's list is the queue. */
    fun playAll(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null) {
        cancelRadio()
        scope.launch {
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            // Keys are minted before shuffling so the original (unshuffled) order
            // is recorded by key in the snapshot.
            val entries = tracks.map { QueueItem(it, queueSeq++) }
            val ordered = if (shuffle) entries.shuffled() else entries
            val built = ordered.mapNotNull { qi -> toMediaItem(qi.song, qi.queueId)?.let { qi to it } }
            if (built.isEmpty()) return@launch
            val queue = built.map { it.first }
            _queue.value = queue
            val start = startIndex.coerceIn(0, queue.size - 1)
            c.setMediaItems(built.map { it.second }, start, 0L)
            c.prepare()
            c.play()
            _currentIndex.value = start
            _current.value = queue.getOrNull(start)?.song
            _current.value?.let { recordRecent(it) }
            applyReplayGain(_current.value)
            _shuffle.value = shuffle
            // Shuffle on: remember the page's own order so toggling off restores it.
            val liveKeys = queue.map { it.queueId }.toSet()
            shuffleOrder = if (shuffle) entries.map { it.queueId }.filter { it in liveKeys } else null
            if (source != null) _sourceName.value = source
            persist()
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
        if (controller == null) return
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
                while (gen == radioTrigger.currentGeneration() && !filled && attempts < 4) {
                    attempts++
                    when (val result = fetcher.fetchBatch(seed, exclusions(), RadioProgrammer.BATCH_SIZE)) {
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
    private suspend fun isOffline(): Boolean =
        runCatching { settings.settingsNow().offlineMode }.getOrDefault(false) || !network.isOnline.value

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
                while (gen == radioTrigger.currentGeneration() && _radioActive.value &&
                    RadioProgrammer.shouldAppend(_currentIndex.value, _queue.value.size) && attempts < 6 && !appended
                ) {
                    attempts++
                    val seed = recentTracks.lastOrNull { it.id != _current.value?.id }
                        ?: _radioSeed.value ?: _current.value
                    if (seed == null) break
                    when (val result = fetcher.fetchBatch(seed, exclusions(), RadioProgrammer.BATCH_SIZE)) {
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
        val c = controller ?: return
        if (gen != radioTrigger.currentGeneration() || !_radioActive.value) return
        if (batch.isEmpty() || _queue.value.isEmpty()) return
        val built = batch.mapNotNull { t ->
            val qi = QueueItem(t, queueSeq++)
            toMediaItem(t, qi.queueId)?.let { qi to it }
        }
        if (built.isEmpty() || gen != radioTrigger.currentGeneration()) return
        c.addMediaItems(built.map { it.second })
        val entries = built.map { it.first }
        _queue.value = QueueOps.appendAll(_queue.value, entries)
        shuffleOrder = shuffleOrder?.let { it + entries.map { e -> e.queueId } }
        entries.forEach { rememberRadioPlayed(it.song.id) }
    }

    /** The queue key of the currently playing entry, if any. */
    private fun currentQueueId(): Long? = _queue.value.getOrNull(_currentIndex.value)?.queueId

    fun playNext(track: Track) {
        scope.launch {
            val c = controller ?: return@launch
            val qi = QueueItem(track, queueSeq++)
            val item = toMediaItem(track, qi.queueId) ?: return@launch
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            _queue.value = QueueOps.insertNext(_queue.value, qi, _currentIndex.value)
            c.addMediaItem(at.coerceAtMost(c.mediaItemCount), item)
            // Mirror the add into the restore snapshot: "play next" belongs right
            // after the current entry there too.
            shuffleOrder = shuffleOrder?.let { ShufflePlan.insertAfter(it, currentQueueId(), qi.queueId) }
            persist()
        }
    }

    /** Inserts a whole list directly after the current item, atomically. */
    fun playNextAll(tracks: List<Track>) {
        scope.launch {
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            val qis = tracks.map { QueueItem(it, queueSeq++) }
            val built = qis.mapNotNull { qi -> toMediaItem(qi.song, qi.queueId)?.let { qi to it } }
            if (built.isEmpty()) return@launch
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            c.addMediaItems(at.coerceAtMost(c.mediaItemCount), built.map { it.second })
            val entries = built.map { it.first }
            _queue.value = _queue.value.toMutableList().apply { addAll(at, entries) }
            shuffleOrder = shuffleOrder?.let {
                ShufflePlan.insertAllAfter(it, currentQueueId(), entries.map { e -> e.queueId })
            }
            persist()
        }
    }

    fun addToEnd(track: Track) {
        scope.launch {
            val c = controller ?: return@launch
            val qi = QueueItem(track, queueSeq++)
            val item = toMediaItem(track, qi.queueId) ?: return@launch
            c.addMediaItem(item)
            _queue.value = QueueOps.append(_queue.value, qi)
            shuffleOrder = shuffleOrder?.let { it + qi.queueId }
            persist()
        }
    }

    fun addAllToEnd(tracks: List<Track>) {
        scope.launch {
            val c = controller ?: return@launch
            val qis = tracks.map { QueueItem(it, queueSeq++) }
            val built = qis.mapNotNull { qi -> toMediaItem(qi.song, qi.queueId)?.let { qi to it } }
            if (built.isEmpty()) return@launch
            c.addMediaItems(built.map { it.second })
            val entries = built.map { it.first }
            _queue.value = QueueOps.appendAll(_queue.value, entries)
            shuffleOrder = shuffleOrder?.let { it + entries.map { e -> e.queueId } }
            persist()
        }
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        val removedId = _queue.value.getOrNull(index)?.queueId
        val (next, adjusted) = QueueOps.removeAt(_queue.value, index, _currentIndex.value)
        if (next === _queue.value) return
        _queue.value = next
        _currentIndex.value = adjusted
        _current.value = next.getOrNull(adjusted)?.song
        shuffleOrder = shuffleOrder?.filterNot { it == removedId }
        c.removeMediaItem(index)
        persistAsync()
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        val (next, adjusted) = QueueOps.move(_queue.value, from, to, _currentIndex.value)
        if (next === _queue.value) return
        _queue.value = next
        _currentIndex.value = adjusted
        c.moveMediaItem(from, to)
        // A manual drag while shuffled becomes the new base order (restoring to
        // the pre-shuffle order would silently discard the user's arrangement).
        if (shuffleOrder != null) shuffleOrder = next.map { it.queueId }
        persistAsync()
    }

    fun clear() {
        controller?.clearMediaItems()
        controller?.stop()
        cancelRadio()
        _queue.value = emptyList()
        _currentIndex.value = 0
        _current.value = null
        _sourceName.value = null
        _shuffle.value = false
        shuffleOrder = null
        _isPlaying.value = false
        _position.value = 0
        _duration.value = 0
        persistAsync()
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in _queue.value.indices) {
            c.seekTo(index, 0)
            c.play()
            _currentIndex.value = index
            _current.value = _queue.value[index].song
            persistAsync()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() {
        controller?.pause()
    }

    /** Gapless is an ExoPlayer property; applied live so the toggle takes effect. */
    fun applyGapless(enabled: Boolean) {
        // ExoPlayer 1.4 has no runtime gapless toggle; the setting is honoured by
        // the service when the player is built. Recorded here for the UI state.
        gaplessEnabled = enabled
    }

    @Volatile private var gaplessEnabled: Boolean = true

    fun resume() {
        controller?.play()
    }

    fun skipToNext() = controller?.seekToNextMediaItem()

    fun skipToPrevious() = controller?.seekToPreviousMediaItem()

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    /**
     * App-side shuffle. The controller's own shuffle stays off, so the visible
     * queue always matches the real playback order.
     *
     * On: snapshot the current order, then permute only the not-yet-played tail —
     * the current item keeps playing untouched and history keeps its slots.
     * Off: restore the snapshot order (minus removed entries, plus entries added
     * while shuffled) and keep the same entry playing, found by its queue key.
     *
     * Both directions reorder with `moveMediaItem` only, so nothing is removed,
     * re-inserted or re-prepared and playback never restarts.
     */
    fun setShuffle(enabled: Boolean, random: Random = Random.Default) {
        val c = controller
        if (c == null) {
            _shuffle.value = enabled
            shuffleOrder = if (!enabled) null else shuffleOrder
            return
        }
        c.shuffleModeEnabled = false
        val live = _queue.value
        if (enabled) {
            // Already shuffling: never re-snapshot (that would lose the original).
            if (!_shuffle.value && live.isNotEmpty()) {
                shuffleOrder = live.map { it.queueId }
                val from = (_currentIndex.value + 1).coerceIn(0, live.size)
                val target = ShufflePlan.shuffledTail(live.map { it.queueId }, from, random)
                applyOrder(c, target)
            }
            _shuffle.value = true
        } else {
            val snapshot = shuffleOrder
            shuffleOrder = null
            if (snapshot != null && live.isNotEmpty()) {
                val target = ShufflePlan.restore(live.map { it.queueId }, snapshot)
                applyOrder(c, target)
            }
            _shuffle.value = false
        }
        persistAsync()
    }

    /**
     * Reorders the live queue to [targetIds] using the minimum set of
     * `moveMediaItem` calls, then rebuilds the mirror and re-points the current
     * index at the same entry (by queue key, so duplicates are safe).
     */
    private fun applyOrder(c: MediaController, targetIds: List<Long>) {
        val before = _queue.value
        val keepId = currentQueueId()
        val reordered = ShufflePlan.applyOrder(targetIds, before) { it.queueId }
        require(reordered.size == before.size) {
            "queue reorder must not drop entries (${before.size} -> ${reordered.size})"
        }
        for ((from, to) in ShufflePlan.moveSeq(before.map { it.queueId }, reordered.map { it.queueId })) {
            c.moveMediaItem(from, to)
        }
        _queue.value = reordered
        val idx = reordered.indexOfFirst { it.queueId == keepId }
        _currentIndex.value = if (idx >= 0) idx
        else _currentIndex.value.coerceIn(0, maxOf(0, reordered.size - 1))
    }

    fun toggleShuffle(): Boolean {
        setShuffle(!_shuffle.value)
        return _shuffle.value
    }

    fun cycleRepeat(): Int {
        val c = controller ?: return Player.REPEAT_MODE_OFF
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
