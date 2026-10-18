package com.feishin.mobile.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.theme.VusicColors

/**
 * Downloads manager: active transfers with progress, finished files, and the
 * per-category cache controls that used to live nowhere.
 */
@Composable
fun DownloadsScreen(vm: DownloadsViewModel = hiltViewModel(), repo: MusicDataRepository) {
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    LaunchedEffect(Unit) {
        topBar.title = "Downloads & storage"
        topBar.alwaysShowTitle = true
        vm.refreshCoverSize()
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Cover art lands after the song finishes, so recompute when the list changes.
    LaunchedEffect(ui.rows.size, ui.totalBytes) { vm.refreshCoverSize() }
    val active = ui.rows.filter {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED
    }
    val done = ui.rows.filter { it.status == DownloadStatus.COMPLETED }
    val failed = ui.rows.filter { it.status == DownloadStatus.FAILED }

    // View-only ordering, same rules as a playlist. Duration is not offered:
    // a download row carries no track duration, only a file size.
    val sort by vm.sort.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    // Search lives in the shared bar, so the filter no longer inserts a row
    // into the list and the storage header never moves.
    val closeSearch = {
        searchOpen = false
        vm.setQuery("")
    }
    val barSearch = remember {
        com.feishin.mobile.ui.TopBarSearch("Filter downloads…", vm::setQuery, closeSearch)
    }
    LaunchedEffect(searchOpen) {
        topBar.search = if (searchOpen) barSearch else null
        topBar.action = if (searchOpen) {
            null
        } else {
            com.feishin.mobile.ui.TopBarAction(
                androidx.compose.material.icons.Icons.Filled.Search,
                "Search downloads",
            ) { searchOpen = true }
        }
    }
    LaunchedEffect(query) { barSearch.query = query }
    androidx.activity.compose.BackHandler(enabled = searchOpen) { closeSearch() }
    val downloadableKeys = remember {
        com.feishin.mobile.core.common.PlaylistSortKey.entries
            .filter { it != com.feishin.mobile.core.common.PlaylistSortKey.DURATION }
    }
    // Fall back to the default when the remembered sort names a key this list
    // cannot express (a download row carries no duration). This has to happen
    // HERE, not only where the controls are drawn: otherwise the sort sheet
    // would show nothing selected while the list was still being ordered by
    // that key, and `durationAt = { 0 }` would silently make it a no-op sort
    // for every row.
    val effectiveSort = remember(sort, downloadableKeys) {
        if (sort.key in downloadableKeys) sort
        else com.feishin.mobile.core.common.PlaylistOrdering.DEFAULT
    }
    val doneIndices = remember(done, effectiveSort, query) {
        com.feishin.mobile.core.common.PlaylistOrdering.visibleIndices(
            size = done.size,
            sort = effectiveSort,
            query = query,
            titleAt = { done[it].title },
            artistAt = { done[it].artist },
            albumAt = { done[it].album },
            // Never read: DURATION is filtered out of downloadableKeys above.
            durationAt = { 0 },
        )
    }
    val visibleDone = remember(doneIndices, done) { doneIndices.map { done[it] } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                StorageRow("Downloads (${done.size} songs)", ui.totalLabel)
                StorageRow("Cached cover art", ui.coverLabel)
                Spacer(Modifier.size(12.dp))
                // Both remove data, so both are red with confirms — same style
                // as every Settings action button.
                var confirmDownloads by androidx.compose.runtime.remember { mutableStateOf(false) }
                var confirmArt by androidx.compose.runtime.remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.feishin.mobile.ui.settings.SettingsActionButton(
                        label = "Delete downloads", danger = true,
                        onClick = { confirmDownloads = true },
                    )
                    com.feishin.mobile.ui.settings.SettingsActionButton(
                        label = "Clear art cache", danger = true,
                        onClick = { confirmArt = true },
                    )
                }
                if (confirmDownloads) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmDownloads = false },
                        title = { Text("Delete all downloads?") },
                        text = { Text("Removes every downloaded song and its files.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                confirmDownloads = false
                                vm.clearCompleted()
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { confirmDownloads = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
                if (confirmArt) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmArt = false },
                        title = { Text("Clear cached cover art?") },
                        text = { Text("Artwork is re-downloaded on demand.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                confirmArt = false
                                vm.clearCoverCache()
                            }) { Text("Clear") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { confirmArt = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionLabel("Downloading (${active.size})") }
            items(active, key = { "a${it.songId}" }) { row -> ActiveRow(row, vm) }
        }
        if (failed.isNotEmpty()) {
            item { SectionLabel("Failed (${failed.size})") }
            items(failed, key = { "f${it.songId}" }) { row -> ActiveRow(row, vm) }
        }
        // Sort + filter apply to the downloaded songs themselves; the
        // in-flight sections above are transient state, not a list to browse.
        if (done.isNotEmpty()) {
            item {
                Column {
                    com.feishin.mobile.ui.detail.TrackListControlsRow(
                        onSortClick = { showSortSheet = true },
                    )
                    Text(
                        if (query.isBlank()) "Downloaded (${done.size})"
                        else "Downloaded (${visibleDone.size} of ${done.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    if (visibleDone.isEmpty() && query.isNotBlank()) {
                        Text(
                            "No downloads match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VusicColors.SecondaryText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            items(visibleDone, key = { it.songId }, contentType = { "item" }) { row ->
                DoneRow(row, repo) { vm.remove(row.songId) }
            }
        }
        if (active.isEmpty() && done.isEmpty() && failed.isEmpty()) {
            item { EmptyState("No downloads yet. Use the download button on any song, album or playlist.") }
        }
    }
    if (showSortSheet) {
        // The Library's sheet, reused: same options list, same direction
        // control, same look as every other sortable list.
        com.feishin.mobile.ui.library.SortBottomSheet(
            options = downloadableKeys.map {
                com.feishin.mobile.ui.library.SortOption(it.name, it.label)
            },
            selectedId = effectiveSort.key.name,
            ascending = !effectiveSort.descending,
            onSelect = { id, ascending ->
                val key = com.feishin.mobile.core.common.PlaylistSortKey.valueOf(id)
                vm.setSort(key, descending = !ascending)
            },
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText)
    }
}

@Composable
private fun ActiveRow(row: DownloadRow, vm: DownloadsViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressThumb(row)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (row.status) {
                    DownloadStatus.DOWNLOADING -> "${(row.progress * 100).toInt()}%"
                    DownloadStatus.QUEUED -> "Queued"
                    DownloadStatus.PAUSED -> row.error?.let { "Paused — $it" } ?: "Paused"
                    DownloadStatus.FAILED -> row.error ?: "Failed"
                    else -> CacheSizePolicy.human(row.sizeBytes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { row.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        when (row.status) {
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                IconButton(onClick = { vm.resume(row.songId) }) { Icon(Icons.Default.PlayArrow, "Resume") }
                IconButton(onClick = { vm.remove(row.songId) }) { Icon(Icons.Default.Close, "Cancel") }
            }
            DownloadStatus.DOWNLOADING -> {
                IconButton(onClick = { vm.pause(row.songId) }) { Icon(Icons.Default.Pause, "Pause") }
                IconButton(onClick = { vm.remove(row.songId) }) { Icon(Icons.Default.Close, "Cancel") }
            }
            else -> IconButton(onClick = { vm.remove(row.songId) }) { Icon(Icons.Default.Close, "Cancel") }
        }
    }
}

@Composable
private fun DoneRow(row: DownloadRow, repo: MusicDataRepository, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(repo.coverArtUrl(row.coverArt, 128), row.title, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.artist} · ${CacheSizePolicy.human(row.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.Done, "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove download") }
    }
}

@Composable
private fun ProgressThumb(row: DownloadRow) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(VusicColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.status == DownloadStatus.COMPLETED) Icons.Default.Done else Icons.Default.Refresh,
                null,
                tint = VusicColors.SecondaryText,
            )
        }
        if (row.status == DownloadStatus.DOWNLOADING) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
