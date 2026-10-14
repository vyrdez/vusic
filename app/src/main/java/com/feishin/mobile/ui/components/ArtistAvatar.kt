package com.feishin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.feishin.mobile.core.common.AvatarFallback
import com.feishin.mobile.ui.theme.VusicColors

/**
 * The single artist avatar used by the Artist page, Library, and Search.
 *
 * Fallback chain: provider info image -> artist cover art -> first album cover
 * -> a styled initial. Provider placeholder assets count as missing. Circular by
 * default; pass [square] for album-style art.
 */
@Composable
fun ArtistAvatar(
    name: String,
    /** Provider image (already resolved to a URL), if any. */
    infoImageUrl: String? = null,
    /** Artist cover-art URL, if any. */
    coverArtUrl: String? = null,
    /** First album's cover-art URL, if any. */
    firstAlbumCoverUrl: String? = null,
    modifier: Modifier = Modifier,
    square: Boolean = false,
) {
    val shape: Shape = if (square) RoundedCornerShape(12.dp) else CircleShape
    val url = AvatarFallback.pick(infoImageUrl, coverArtUrl, firstAlbumCoverUrl)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier.clip(shape).background(VusicColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.displayMedium,
                color = VusicColors.SecondaryText,
            )
        }
    }
}
