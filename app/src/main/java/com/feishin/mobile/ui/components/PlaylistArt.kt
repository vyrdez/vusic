package com.feishin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.ui.theme.VusicColors
import coil.compose.AsyncImage

/**
 * Renders a playlist cover: server art, a 2x2 collage of track covers, a single
 * cover when only one is known, or a styled placeholder when the playlist is
 * empty. [resolver] turns a cover-art id into a signed URL (usually
 * `repo::coverArtUrl`) so this stays free of repository dependencies.
 */
@Composable
fun PlaylistCoverArt(
    cover: PlaylistCover,
    resolver: (String?) -> String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    roundedDp: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(roundedDp)
    when (cover) {
        is PlaylistCover.Server -> CoverImage(
            resolver(cover.coverArtId), contentDescription,
            modifier = modifier, roundedDp = roundedDp,
        )

        is PlaylistCover.Collage -> {
            val urls = cover.coverArtIds.map { resolver(it) }
            // Count-aware so a 2-track playlist shows two covers side by side rather
            // than the same art repeated to fill a 2x2.
            when (urls.size) {
                // Empty collage (no resolvable art): fall back to the styled
                // placeholder instead of indexing an empty list.
                1 -> CoverImage(urls[0], contentDescription, modifier = modifier, roundedDp = roundedDp)
                2 -> Column(modifier.clip(shape).background(VusicColors.SurfaceHigh)) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CollageCell(urls[0], contentDescription)
                        CollageCell(urls[1], contentDescription)
                    }
                }
                3 -> Column(modifier.clip(shape).background(VusicColors.SurfaceHigh)) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CollageCell(urls[0], contentDescription)
                        CollageCell(urls[1], contentDescription)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CollageCell(urls[2], contentDescription)
                    }
                }
                in 4..Int.MAX_VALUE -> Column(modifier.clip(shape).background(VusicColors.SurfaceHigh)) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CollageCell(urls[0], contentDescription)
                        CollageCell(urls[1], contentDescription)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CollageCell(urls[2], contentDescription)
                        CollageCell(urls[3], contentDescription)
                    }
                }
                else -> Box(
                    modifier.clip(shape).background(VusicColors.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote, contentDescription,
                        tint = VusicColors.SecondaryText,
                    )
                }
            }
        }

        PlaylistCover.Placeholder -> Box(
            modifier.clip(shape).background(VusicColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MusicNote, contentDescription,
                tint = VusicColors.SecondaryText,
                modifier = Modifier.size(36.dp),
            )
        }

        is PlaylistCover.Favorites -> {
            val urls = cover.coverArtIds.map { resolver(it) }.filterNotNull().take(4)
            if (urls.isEmpty()) {
                Box(
                    modifier.clip(shape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8E2DE2), Color(0xFFFF6B6B)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Favorite, contentDescription,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                // Collage of the latest favorites with a heart badge.
                Box(modifier.clip(shape)) {
                    PlaylistCoverArt(
                        cover = PlaylistCover.Collage(urls),
                        resolver = resolver,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        roundedDp = 0.dp,
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Favorite, contentDescription,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CollageCell(url: String?, description: String?) {
    // Collage cells deliberately skip the placeholder art: a wall of music-note
    // placeholders looks cluttered, so cells stay solid until their image lands.
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = description,
            modifier = Modifier.fillMaxHeight().weight(1f),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxHeight().weight(1f).background(VusicColors.SurfaceHigh))
    }
}

/** Square variant sized by aspect ratio, for grids and pagers. */
@Composable
fun PlaylistCoverArtSquare(
    cover: PlaylistCover,
    resolver: (String?) -> String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    roundedDp: Dp = 8.dp,
) {
    PlaylistCoverArt(
        cover = cover,
        resolver = resolver,
        contentDescription = contentDescription,
        modifier = modifier.aspectRatio(1f),
        roundedDp = roundedDp,
    )
}
