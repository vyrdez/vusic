package com.feishin.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.core.common.LibrarySorts
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.PlaylistCoverArt
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.ui.theme.VusicColors

/**
 * Inline library search: back arrow to close, field kept per tab, clear X.
 */
@Composable
fun LibrarySearchField(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", tint = Color.White)
        }
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(VusicColors.SurfaceHigh)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    "Filter this tab…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VusicColors.SecondaryText,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "Clear", tint = Color.White)
            }
        }
    }
}

/**
 * Circular artist photo with Coil: info image, else cover art, else a styled
 * initial. The caller prefetches the info image lazily per visible row.
 */
@Composable
fun ArtistPhoto(
    name: String,
    photoUrl: String?,
    repo: MusicDataRepository,
    modifier: Modifier = Modifier,
) {
    if (photoUrl != null) {
        CoverImage(photoUrl, name, modifier = modifier.clip(CircleShape))
    } else {
        Box(
            modifier.clip(CircleShape).background(VusicColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = VusicColors.SecondaryText,
            )
        }
    }
}

/** Grid cell: circular photo with the name below. */
@Composable
fun ArtistPhotoCard(
    artist: Artist,
    photoUrl: String?,
    repo: MusicDataRepository,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistPhoto(
            name = artist.name, photoUrl = photoUrl, repo = repo,
            modifier = Modifier.size(120.dp),
        )
        Text(
            artist.name, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "${artist.albumCount} albums",
            style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** List row: 56dp circular avatar, name, album count. */
@Composable
fun ArtistPhotoRow(
    artist: Artist,
    photoUrl: String?,
    repo: MusicDataRepository,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistPhoto(
            name = artist.name, photoUrl = photoUrl, repo = repo,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Pinned "Cached" collection row: collage of the latest cached songs, or a
 * styled download icon when the cache is empty.
 */
@Composable
fun CachedSongsRow(
    count: Int,
    covers: List<String?>,
    repo: MusicDataRepository,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ids = covers.filterNotNull().distinct().take(4)
        if (ids.isEmpty()) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(4.dp))
                    .background(VusicColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Download, "Cached", tint = VusicColors.SecondaryText)
            }
        } else {
            PlaylistCoverArt(
                cover = PlaylistCover.Collage(ids),
                resolver = { id -> repo.coverArtUrl(id, 128) },
                contentDescription = "Cached",
                modifier = Modifier.size(56.dp),
                roundedDp = 4.dp,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Cached", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$count songs",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
            )
        }
    }
}

/**
 * "Sort by" bottom sheet: readable radio options for the tab, an
 * ascending/descending toggle, and a checkmark on the selection. The choice
 * persists per tab in DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    tabName: String,
    current: SortChoice,
    onSelect: (SortChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var draft by remember(tabName, current) { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(4.dp))
            LibrarySorts.options(tabName).forEach { option ->
                val selected = draft.option == option.id
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { draft = draft.copy(option = option.id).also(onSelect) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) Color.White else VusicColors.SecondaryText,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Default.Check, "Selected", tint = Color.White)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        draft = draft.copy(ascending = !draft.ascending).also(onSelect)
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (draft.ascending) "Ascending" else "Descending",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (draft.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    if (draft.ascending) "Ascending" else "Descending",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
