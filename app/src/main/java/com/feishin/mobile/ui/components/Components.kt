package com.feishin.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.feishin.mobile.R
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/** Scroll states that survive rotation via rememberSaveable. */
@Composable
fun rememberSavableListState(): LazyListState =
    rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

@Composable
fun rememberSavableGridState(): LazyGridState =
    rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

/**
 * Single shared cover loader: placeholder + error fallback, Coil memory/disk
 * cache (stable signed URLs), crossfade from the global ImageLoader.
 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    roundedDp: Dp = 8.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val shape = RoundedCornerShape(roundedDp)
    if (url != null) {
        AsyncImage(
            url, contentDescription,
            modifier = modifier.clip(shape),
            contentScale = contentScale,
            placeholder = painterResource(R.drawable.art_placeholder),
            error = painterResource(R.drawable.art_error),
            fallback = painterResource(R.drawable.art_placeholder),
        )
    } else {
        androidx.compose.foundation.Image(
            painterResource(R.drawable.art_placeholder), contentDescription,
            modifier = modifier.clip(shape).background(VusicColors.SurfaceHigh),
            contentScale = contentScale,
        )
    }
}

/** Animated 3-bar equalizer shown on the current track's cover. */
@Composable
fun EqualizerOverlay(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val h by t.animateFloat(
                initialValue = 4f, targetValue = 12f + i * 2f,
                animationSpec = infiniteRepeatable(tween(380 + i * 140, easing = LinearEasing), RepeatMode.Reverse),
                label = "eq$i",
            )
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    label: String? = null,
    avatarUrl: String? = null,
    onPlayAll: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = VusicSpacing.SectionGap, end = 8.dp, bottom = VusicSpacing.HeaderToContent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl != null) {
            CoverImage(avatarUrl, title, modifier = Modifier.size(40.dp), roundedDp = 20.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = VusicColors.SecondaryText, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onPlayAll != null) {
            Text(
                "Play all",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        if (onOpen != null) {
            IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ChevronRight, "Open", tint = Color.White)
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, artUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        CoverImage(artUrl, album.name, modifier = Modifier.aspectRatio(1f).fillMaxWidth(), roundedDp = 8.dp)
        Text(album.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text(
            "Album • ${album.artist} • ${album.year?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ArtistCard(artist: Artist, artUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        if (artUrl != null) {
            CoverImage(artUrl, artist.name, modifier = Modifier.aspectRatio(1f).fillMaxWidth(), roundedDp = 8.dp)
        } else {
            Box(
                Modifier.aspectRatio(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(VusicColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = VusicColors.SecondaryText,
                )
            }
        }
        Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text(
            "${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall,
            color = VusicColors.SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Shared track row: 56dp art, title, "Artist • duration", current-track highlight
 * with equalizer overlay, ⋮ button + long-press opening the actions sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    artUrl: String?,
    onClick: () -> Unit,
    onActions: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onArtist: ((String) -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showMenuButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onActions)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.BottomStart) {
            CoverImage(artUrl, track.title, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
            if (isCurrent && isPlaying) {
                EqualizerOverlay(Modifier.padding(6.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableName(
                    name = track.artist,
                    enabled = onArtist != null && track.artistId?.isNotBlank() == true,
                    onClick = { track.artistId?.let { onArtist?.invoke(it) } },
                )
                Text(
                    " • ${formatDuration(track.durationSec)}",
                    style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
                    maxLines = 1,
                )
            }
        }
        if (onRemove != null) {
            Text(
                "✕", color = VusicColors.SecondaryText,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(12.dp),
            )
        }
        if (showMenuButton) {
            IconButton(onClick = onActions, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Track options")
            }
        }
    }
}

/** Tappable name with a 48dp touch target and visible affordance. */
@Composable
fun ClickableName(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (enabled) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = VusicColors.SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 12.dp),
        )
    } else {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = VusicColors.SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

@Composable
fun FilterChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it }) { option ->
            val isSel = option == selected
            AssistChip(
                onClick = { onSelect(option) },
                label = { Text(option, maxLines = 1) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSel) Color.White else VusicColors.SurfaceHigh,
                    labelColor = if (isSel) Color.Black else Color.White,
                ),
                border = null,
            )
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, null, tint = VusicColors.SecondaryText, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, color = VusicColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Retry",
                style = MaterialTheme.typography.labelLarge, color = Color.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable(onClick = onRetry).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** Shimmering placeholder boxes shown while sections load. */
@Composable
fun SkeletonLoader(modifier: Modifier = Modifier, cardCount: Int = 3) {
    val t = rememberInfiniteTransition(label = "skel")
    val alpha by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "skel-a",
    )
    val color = VusicColors.SurfaceHigh.copy(alpha = alpha)
    Column(modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Box(Modifier.width(160.dp).height(22.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
            items(cardCount) {
                Column {
                    Box(Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(color))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeTitle(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text, style = style, color = color, maxLines = 1,
        textAlign = textAlign,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
    )
}
