package com.feishin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import com.feishin.mobile.playback.PlayerManager

/** Docked directly above the nav bar. Tap or swipe up opens Now Playing,
 *  swipe left/right skips tracks. Hidden when the queue is empty. */
@Composable
fun MiniPlayer(player: PlayerManager, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val queue by player.queue.collectAsState()
    val current by player.current.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    if (queue.isEmpty()) return
    val song = current ?: return

    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                var totalX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalX = 0f },
                    onHorizontalDrag = { _, amount -> totalX += amount },
                    onDragEnd = {
                        when {
                            totalX < -120f -> player.skipToNext()
                            totalX > 120f -> player.skipToPrevious()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var totalY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalY = 0f },
                    onVerticalDrag = { _, amount -> totalY += amount },
                    onDragEnd = { if (totalY < -120f) onOpen() },
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                player.coverArtUrl(song.coverArt, 128), song.title,
                modifier = Modifier.size(48.dp), roundedDp = 8.dp,
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = player::togglePlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = player::skipToNext) {
                Icon(Icons.Default.SkipNext, "Next track")
            }
        }
        Row(Modifier.fillMaxWidth().height(2.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight(progress.coerceAtLeast(0.001f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight((1f - progress).coerceAtLeast(0.001f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
