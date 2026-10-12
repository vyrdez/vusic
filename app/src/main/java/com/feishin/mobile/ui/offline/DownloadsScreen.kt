package com.feishin.mobile.ui.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DownloadsScreen(vm: DownloadsViewModel = hiltViewModel()) {
    val list by vm.downloads.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Offline downloads (${list.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::clearAll) { Text("Clear") }
        }
        if (list.isEmpty()) {
            Text("No downloads yet. Use download buttons on songs.", modifier = Modifier.padding(16.dp))
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            items(list, key = { it.songId }) { d ->
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium)
                        Text("${d.artist} · ${(d.sizeBytes / 1024 / 1024)} MB", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.remove(d.songId) }) { Text("Remove") }
                }
            }
        }
    }
}
