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
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.core.network.StreamUrlProvider
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.queue.QueueStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var queueSeq = 0L
    private var restored = false

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

    init {
        connect()
        scope.launch {
            var ticks = 0
            while (true) {
                delay(500)
                val c = controller ?: continue
                _position.value = c.currentPosition.coerceAtLeast(0)
                _duration.value = c.duration.takeIf { it > 0 } ?: 0L
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
                    val id = mediaItem?.mediaId
                    val idx = _queue.value.indexOfFirst { it.song.id == id }.takeIf { it >= 0 }
                        ?: c.currentMediaItemIndex
                    _currentIndex.value = idx.coerceIn(0, maxOf(0, _queue.value.size - 1))
                    _current.value = _queue.value.getOrNull(_currentIndex.value)?.song
                        ?: mediaItem?.let { TrackMediaItems.toTrack(it) }
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
            if (merged != null) rebuilt += QueueItem(merged, queueSeq++)
        }
        _queue.value = rebuilt
    }

    /**
     * Restores the persisted queue after a process kill. Rebuilds fresh (single-use)
     * stream URLs, seeks to the saved position, and stays paused — never auto-plays.
     */
    private suspend fun restoreFromSnapshot(c: MediaController) {
        val snapshot = runCatching { queueStore.load() }.getOrNull()
        if (restored || snapshot == null || creds.state.value == null) {
            restored = true
            return
        }
        val tracks = snapshot.tracks.map { it.toDomain() }
        val items = tracks.mapNotNull { toMediaItem(it) }
        if (items.isEmpty()) {
            restored = true
            return
        }
        _queue.value = tracks.map { QueueItem(it, queueSeq++) }
        val index = snapshot.currentIndex.coerceIn(0, items.size - 1)
        c.setMediaItems(items, index, snapshot.positionMs)
        c.prepare()
        c.pause()
        _currentIndex.value = index
        _current.value = tracks.getOrNull(index)
        _sourceName.value = snapshot.sourceName
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
            queueStore.save(q.map { it.song }, _currentIndex.value.coerceIn(0, q.size - 1), _position.value, _sourceName.value)
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

    private suspend fun toMediaItem(track: Track): MediaItem? {
        val url = streamUrlFor(track) ?: return null
        return TrackMediaItems.toMediaItem(
            track = track,
            streamUrl = url,
            coverArtUrl = coverArtUrl(track.coverArt),
        )
    }

    fun coverArtUrl(coverArtId: String?, size: Int = 600): String? {
        val c = creds.state.value ?: return null
        return CoverArtUrlProvider.build(
            c.serverUrl, c.username, c.password, coverArtId, size,
        )
    }

    /** Plays [tracks] starting at [startIndex]; the caller's list is the queue. */
    fun playAll(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null) {
        scope.launch {
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            val ordered = if (shuffle) tracks.shuffled() else tracks
            val items = ordered.mapNotNull { toMediaItem(it) }
            if (items.isEmpty()) return@launch
            _queue.value = ordered.map { QueueItem(it, queueSeq++) }
            val start = startIndex.coerceIn(0, items.size - 1)
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
            _currentIndex.value = start
            _current.value = ordered.getOrNull(start)
            _shuffle.value = shuffle
            if (source != null) _sourceName.value = source
            persist()
        }
    }

    fun playNext(track: Track) {
        scope.launch {
            val c = controller ?: return@launch
            val item = toMediaItem(track) ?: return@launch
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            _queue.value = QueueOps.insertNext(_queue.value, QueueItem(track, queueSeq++), _currentIndex.value)
            c.addMediaItem(at.coerceAtMost(c.mediaItemCount), item)
            persist()
        }
    }

    /** Inserts a whole list directly after the current item, atomically. */
    fun playNextAll(tracks: List<Track>) {
        scope.launch {
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            val items = tracks.mapNotNull { toMediaItem(it) }
            if (items.isEmpty()) return@launch
            val at = (_currentIndex.value + 1).coerceIn(0, _queue.value.size)
            c.addMediaItems(at.coerceAtMost(c.mediaItemCount), items)
            _queue.value = _queue.value.toMutableList()
                .apply { addAll(at, tracks.map { QueueItem(it, queueSeq++) }) }
            persist()
        }
    }

    fun addToEnd(track: Track) {        scope.launch {
            val c = controller ?: return@launch
            val item = toMediaItem(track) ?: return@launch
            c.addMediaItem(item)
            _queue.value = QueueOps.append(_queue.value, QueueItem(track, queueSeq++))
            persist()
        }
    }

    fun addAllToEnd(tracks: List<Track>) {
        scope.launch {
            val c = controller ?: return@launch
            val items = tracks.mapNotNull { toMediaItem(it) }
            if (items.isEmpty()) return@launch
            c.addMediaItems(items)
            _queue.value = QueueOps.appendAll(_queue.value, tracks.map { QueueItem(it, queueSeq++) })
            persist()
        }
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        val (next, adjusted) = QueueOps.removeAt(_queue.value, index, _currentIndex.value)
        if (next === _queue.value) return
        _queue.value = next
        _currentIndex.value = adjusted
        _current.value = next.getOrNull(adjusted)?.song
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
        persistAsync()
    }

    fun clear() {
        controller?.clearMediaItems()
        controller?.stop()
        _queue.value = emptyList()
        _currentIndex.value = 0
        _current.value = null
        _sourceName.value = null
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

    fun resume() {
        controller?.play()
    }

    fun skipToNext() = controller?.seekToNextMediaItem()

    fun skipToPrevious() = controller?.seekToPreviousMediaItem()

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    /**
     * App-side shuffle: the controller's own shuffle stays off, and only items
     * after the current one are reordered (Fisher-Yates via moveMediaItem), so the
     * visible queue always matches the actual playback order and the current
     * track never glitches. Disabling keeps the shuffled order, like most players.
     */
    fun setShuffle(enabled: Boolean, random: Random = Random.Default) {
        val c = controller ?: run {
            _shuffle.value = enabled
            return
        }
        c.shuffleModeEnabled = false
        if (enabled && _queue.value.size > _currentIndex.value + 1) {
            val from = _currentIndex.value + 1
            val order = (_queue.value.indices.toList().subList(from, _queue.value.size)).shuffled(random)
            // Apply as a permutation of the tail using controller moves.
            val q = _queue.value.toMutableList()
            val tail = order.map { q[it] }
            for ((slot, item) in tail.withIndex()) {
                val target = from + slot
                val currentPos = q.indexOf(item)
                if (currentPos != target) {
                    q.add(target, q.removeAt(currentPos))
                    c.moveMediaItem(currentPos, target)
                }
            }
            _queue.value = q
            _currentIndex.value = q.indexOfFirst { it.song.id == _current.value?.id }.takeIf { it >= 0 }
                ?: _currentIndex.value
        }
        _shuffle.value = enabled
        persistAsync()
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
