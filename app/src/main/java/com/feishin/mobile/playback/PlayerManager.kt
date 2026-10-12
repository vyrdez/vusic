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
import com.feishin.mobile.core.network.StreamUrlProvider
import com.feishin.mobile.data.auth.CredentialsStore
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

data class QueueItem(val song: Track, val queueId: Long)

/**
 * App-side view of playback. Talks to [PlayerService] through a [MediaController],
 * so the app process and the service can come and go independently while the
 * session (and its notification) survive.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val creds: CredentialsStore,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var queueSeq = 0L

    @Volatile private var controller: MediaController? = null

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

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

    init {
        connect()
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                _position.value = c.currentPosition.coerceAtLeast(0)
                _duration.value = c.duration.takeIf { it > 0 } ?: 0L
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
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _current.value = mediaItem?.let { TrackMediaItems.toTrack(it) }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffle.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_IDLE) _isPlaying.value = c.isPlaying
                }
            })
            syncQueueFromController(c)
            _current.value = c.currentMediaItem?.let { TrackMediaItems.toTrack(it) }
            _isPlaying.value = c.isPlaying
            _shuffle.value = c.shuffleModeEnabled
            _repeatMode.value = c.repeatMode
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
        return com.feishin.mobile.core.network.CoverArtUrlProvider.build(
            c.serverUrl, c.username, c.password, coverArtId, size,
        )
    }

    /** Plays [tracks] starting at [startIndex]; the caller's list is the queue. */
    fun playAll(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        scope.launch {
            val c = controller ?: return@launch
            if (tracks.isEmpty()) return@launch
            val ordered = if (shuffle) tracks.shuffled() else tracks
            val items = ordered.mapNotNull { toMediaItem(it) }
            if (items.isEmpty()) return@launch
            _queue.value = ordered.map { QueueItem(it, queueSeq++) }
            val start = startIndex.coerceIn(0, items.size - 1)
            c.setMediaItems(items, start, 0L)
            c.shuffleModeEnabled = false
            c.prepare()
            c.play()
            _current.value = ordered.getOrNull(start)
        }
    }

    fun playNext(track: Track) {
        scope.launch {
            val c = controller ?: return@launch
            val item = toMediaItem(track) ?: return@launch
            val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
            c.addMediaItem(at, item)
            val q = _queue.value.toMutableList()
            q.add(at.coerceAtMost(q.size), QueueItem(track, queueSeq++))
            _queue.value = q
        }
    }

    fun addToEnd(track: Track) {
        scope.launch {
            val c = controller ?: return@launch
            val item = toMediaItem(track) ?: return@launch
            c.addMediaItem(item)
            _queue.value = _queue.value + QueueItem(track, queueSeq++)
        }
    }

    fun addAllToEnd(tracks: List<Track>) {
        scope.launch {
            val c = controller ?: return@launch
            val items = tracks.mapNotNull { toMediaItem(it) }
            if (items.isEmpty()) return@launch
            c.addMediaItems(items)
            _queue.value = _queue.value + tracks.map { QueueItem(it, queueSeq++) }
        }
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        val q = _queue.value.toMutableList()
        q.removeAt(index)
        _queue.value = q
        c.removeMediaItem(index)
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        val q = _queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        q.add(to, q.removeAt(from))
        _queue.value = q
        c.moveMediaItem(from, to)
    }

    fun clear() {
        controller?.clearMediaItems()
        controller?.stop()
        _queue.value = emptyList()
        _current.value = null
        _isPlaying.value = false
        _position.value = 0
        _duration.value = 0
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index in _queue.value.indices) {
            c.seekTo(index, 0)
            c.play()
            _current.value = _queue.value[index].song
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

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        _shuffle.value = enabled
    }

    fun toggleShuffle(): Boolean {
        val c = controller ?: return false
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        _shuffle.value = c.shuffleModeEnabled
        return c.shuffleModeEnabled
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