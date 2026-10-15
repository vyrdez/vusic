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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val ui by vm.ui.collectAsState()
    // Cover art lands after the song finishes, so recompute when the list changes.
    LaunchedEffect(ui.rows.size, ui.totalBytes) { vm.refreshCoverSize() }
    val active = ui.rows.filter {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED
    }
    val done = ui.rows.filter { it.status == DownloadStatus.COMPLETED }
    val failed = ui.rows.filter { it.status == DownloadStatus.FAILED }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                StorageRow("Downloads (${done.size} songs)", ui.totalLabel)
                StorageRow("Cached cover art", ui.coverLabel)
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearCompleted() }) { Text("Delete downloads") }
                    OutlinedButton(onClick = { vm.clearCoverCache() }) { Text("Clear art cache") }
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
        if (done.isNotEmpty()) {
            item { SectionLabel("Downloaded (${done.size})") }
            items(done, key = { it.songId }) { row -> DoneRow(row, repo) { vm.remove(row.songId) } }
        }
        if (active.isEmpty() && done.isEmpty() && failed.isEmpty()) {
            item { EmptyState("No downloads yet. Use the download button on any song, album or playlist.") }
        }
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
