package com.feishin.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.store.ThemeMode

val BITRATES = listOf(0, 64, 128, 192, 256, 320)
val FORMATS = listOf("raw", "mp3", "opus", "aac")

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val s = settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (s == null) {
            Text("Loading…")
            return
        }
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row {
            ThemeMode.entries.forEach { m ->
                OutlinedButton(
                    onClick = { vm.setTheme(m) },
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = s.theme != m,
                ) { Text(m.name) }
            }
        }
        Spacer(Modifier.height(16.dp))
        BitrateRow("Wi-Fi", s.wifiBitrate, s.wifiFormat, vm::setWifi)
        BitrateRow("Mobile", s.mobileBitrate, s.mobileFormat, vm::setMobile)
        Spacer(Modifier.height(16.dp))
        SwitchRow("Allow self-signed certificates (applies immediately)", s.allowSelfSigned, vm::setSelfSigned)
        SwitchRow("Gapless playback", s.gapless, vm::setGapless)
        SwitchRow("Crossfade (best-effort)", s.crossfade, vm::setCrossfade)
        SwitchRow("ReplayGain normalization (best-effort)", s.replayGain, vm::setReplayGain)
        Spacer(Modifier.height(8.dp))
        Text("Stream cache size: ${s.cacheSizeMb} MB", style = MaterialTheme.typography.bodyMedium)
        Row {
            listOf(128, 512, 1024, 2048).forEach { mb ->
                OutlinedButton(onClick = { vm.setCache(mb) }, modifier = Modifier.padding(end = 8.dp), enabled = s.cacheSizeMb != mb) {
                    Text("$mb")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenDownloads, modifier = Modifier.fillMaxWidth()) { Text("Offline downloads") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun BitrateRow(label: String, br: Int, fmt: String, onSet: (Int, String) -> Unit) {
    var brMenu by remember { mutableStateOf(false) }
    var fmtMenu by remember { mutableStateOf(false) }
    Text("$label streaming", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { brMenu = true }) { Text(if (br == 0) "Original" else "$br kbps") }
        DropdownMenu(brMenu, onDismissRequest = { brMenu = false }) {
            BITRATES.forEach { b ->
                DropdownMenuItem(text = { Text(if (b == 0) "Original" else "$b kbps") }, onClick = { onSet(b, fmt); brMenu = false })
            }
        }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = { fmtMenu = true }) { Text(fmt) }
        DropdownMenu(fmtMenu, onDismissRequest = { fmtMenu = false }) {
            FORMATS.forEach { f ->
                DropdownMenuItem(text = { Text(f) }, onClick = { onSet(br, f); fmtMenu = false })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, { onSet(it) })
    }
}
