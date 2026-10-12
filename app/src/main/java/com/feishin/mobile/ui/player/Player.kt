package com.feishin.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.core.common.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: MusicDataRepository,
) : ViewModel() {
    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()
    private var lastId: String? = null

    fun loadLyrics(song: Track?) {
        if (song == null || song.id == lastId) return
        lastId = song.id
        _lyrics.value = null
        viewModelScope.launch {
            _lyrics.value = (repo.lyrics(song.artist, song.title) as? Result.Ok)?.value?.takeIf { it.isNotBlank() }
        }
    }

    fun toggleStar(song: Track, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.starTrack(song.id, !song.starred)
            onDone()
        }
    }
}

@Composable
fun MiniPlayer(player: PlayerManager, repo: MusicDataRepository, onOpen: () -> Unit) {
    val current by player.current.collectAsState()
    val playing by player.isPlaying.collectAsState()
    val song = current ?: return
    Card(
        Modifier.fillMaxWidth().padding(8.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(repo.coverArtUrl(song.coverArt, 128), song.title, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    player: PlayerManager,
    repo: MusicDataRepository,
    pvm: PlayerViewModel,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.fillMaxSize()) {
        NowPlayingContent(player, repo, pvm, onOpenQueue)
    }
}

@Composable
fun NowPlayingContent(player: PlayerManager, repo: MusicDataRepository, pvm: PlayerViewModel, onOpenQueue: () -> Unit) {
    val current by player.current.collectAsState()
    val playing by player.isPlaying.collectAsState()
    val pos by player.position.collectAsState()
    val dur by player.duration.collectAsState()
    val lyrics by pvm.lyrics.collectAsState()
    val song = current
    LaunchedEffect(song?.id) { pvm.loadLyrics(song) }

    var bg by remember { mutableStateOf(Color(0xFF141A22)) }
    val ctx = LocalContext.current
    LaunchedEffect(song?.coverArt) {
        val id = song?.coverArt
        if (id != null) {
            withContext(Dispatchers.IO) {
                try {
                    val req = ImageRequest.Builder(ctx).data(repo.coverArtUrl(id, 128)).allowHardware(false).build()
                    val res = ctx.imageLoader.execute(req)
                    val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        val px = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                        bg = Color(px).copy(alpha = 1f)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.55f), MaterialTheme.colorScheme.background))),
    ) {
        if (song == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing playing") }
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(repo.coverArtUrl(song.coverArt, 512), song.title, modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(16.dp))
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                    Text(song.artist, style = MaterialTheme.typography.bodyLarge)
                    Text(song.album, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = pos.toFloat(),
                        onValueChange = { player.seekTo(it.toLong()) },
                        valueRange = 0f..(dur.coerceAtLeast(1).toFloat()),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(pos)); Text(fmt(dur))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.toggleShuffle() }) { Icon(Icons.Default.Shuffle, "Shuffle") }
                        IconButton(onClick = { player.playAt((player.queue.value.indexOfFirst { it.song.id == song.id } - 1).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.SkipPrevious, "Previous")
                        }
                        IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", modifier = Modifier.size(48.dp))
                        }
                        IconButton(onClick = {
                            val q = player.queue.value
                            val i = q.indexOfFirst { it.song.id == song.id }
                            if (i >= 0 && i + 1 < q.size) player.playAt(i + 1)
                        }) { Icon(Icons.Default.SkipNext, "Next") }
                        IconButton(onClick = { player.cycleRepeat() }) { Icon(Icons.Default.Repeat, "Repeat") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var starred by remember(song.id) { mutableStateOf(song.starred) }
                        IconButton(onClick = {
                            pvm.toggleStar(song.copy(starred = !starred)) { starred = !starred }
                        }) { Icon(if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Star") }
                        IconButton(onClick = onOpenQueue) { Icon(Icons.Default.RepeatOne, "Queue") }
                    }
                    lyrics?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Lyrics", style = MaterialTheme.typography.titleMedium)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerManager, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val queue by player.queue.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Queue (${queue.size})", style = MaterialTheme.typography.titleMedium)
                Row {
                    androidx.compose.material3.TextButton(onClick = { player.clear() }) { Text("Clear") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
            }
            LazyColumn {
                itemsIndexed(queue, key = { _, it -> it.queueId }) { i, item ->
                    Row(Modifier.fillMaxWidth().clickable { player.playAt(i) }.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        androidx.compose.material3.TextButton(onClick = { player.removeAt(i) }) { Text("✕") }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
