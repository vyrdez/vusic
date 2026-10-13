package com.feishin.mobile.ui.offline

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.EmptyState
import com.feishin.mobile.ui.components.SectionHeader
import com.feishin.mobile.ui.theme.VusicColors

@Composable
fun DownloadsScreen(vm: DownloadsViewModel = hiltViewModel(), repo: MusicDataRepository) {
    val list by vm.downloads.collectAsState(initial = emptyList())
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        topBar.title = "Offline downloads"
        topBar.alwaysShowTitle = true
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Offline downloads (${list.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::clearAll) { Text("Clear") }
        }
        if (list.isEmpty()) {
            EmptyState("No downloads yet. Use download buttons on songs.")
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            items(list, key = { it.songId }) { d ->
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(repo.coverArtUrl(d.coverArt, 128), d.title, modifier = Modifier.size(56.dp), roundedDp = 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${d.artist} · ${(d.sizeBytes / 1024 / 1024)} MB",
                            style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(onClick = { vm.remove(d.songId) }) { Text("Remove") }
                }
            }
        }
    }
}
