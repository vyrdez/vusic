package com.feishin.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QueueItem(val song: Track, val queueId: Long)

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val player: ExoPlayer,
    private val repo: MusicDataRepository,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var queueSeq = 0L

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

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                _current.value = _queue.value.getOrNull(idx)?.song
                _current.value?.let { s ->
                    scope.launch { repo.scrobble(s.id, submission = false) }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    _current.value?.let { s -> scope.launch { repo.scrobble(s.id, submission = true) } }
                }
            }
        })
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                if (player.isPlaying) {
                    _position.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0)
                }
            }
        }
        scope.launch {
            settings.flow.collect { s ->
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    private suspend fun streamParams(): Pair<Int, String> {
        val s = settings.flow.first()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        return if (wifi) s.wifiBitrate to s.wifiFormat else s.mobileBitrate to s.mobileFormat
    }

    private suspend fun Track.toMediaItem(): MediaItem {
        val (br, fmt) = streamParams()
        val url = repo.streamUrl(this, br, fmt)
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(repo.coverArtUrl(coverArt)?.let { android.net.Uri.parse(it) })
            .build()
        return MediaItem.Builder().setUri(url).setMediaId(id).setMediaMetadata(meta).build()
    }

    fun playAll(songs: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        scope.launch {
            val list = if (shuffle) songs.shuffled() else songs
            _queue.value = list.map { QueueItem(it, queueSeq++) }
            val items = list.map { it.toMediaItem() }
            player.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
            player.shuffleModeEnabled = false
            player.prepare()
            player.play()
            _current.value = list.getOrNull(startIndex)
        }
    }

    fun playNext(song: Track) {
        scope.launch {
            val item = QueueItem(song, queueSeq++)
            val idx = player.currentMediaItemIndex
            val q = _queue.value.toMutableList()
            q.add((idx + 1).coerceIn(0, q.size), item)
            _queue.value = q
            player.addMediaItem((idx + 1).coerceIn(0, player.mediaItemCount), song.toMediaItem())
        }
    }

    fun addToEnd(song: Track) {
        scope.launch {
            _queue.value = _queue.value + QueueItem(song, queueSeq++)
            player.addMediaItem(song.toMediaItem())
        }
    }

    fun addAllToEnd(songs: List<Track>) {
        scope.launch {
            _queue.value = _queue.value + songs.map { QueueItem(it, queueSeq++) }
            songs.forEach { player.addMediaItem(it.toMediaItem()) }
        }
    }

    fun removeAt(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) {
            q.removeAt(index)
            _queue.value = q
            player.removeMediaItem(index)
        }
    }

    fun move(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q
        player.moveMediaItem(from, to)
    }

    fun clear() {
        _queue.value = emptyList()
        player.clearMediaItems()
        player.stop()
        _current.value = null
    }

    fun playAt(index: Int) {
        if (index in _queue.value.indices) {
            player.seekTo(index, 0)
            player.play()
            _current.value = _queue.value[index].song
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun toggleShuffle(): Boolean {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        return player.shuffleModeEnabled
    }

    fun cycleRepeat(): Int {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        return player.repeatMode
    }
}
