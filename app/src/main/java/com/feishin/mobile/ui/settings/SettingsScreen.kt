package com.feishin.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.ui.theme.VusicColors

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val test by vm.test.collectAsState()
    val online by vm.network.isOnline.collectAsState()
    val pending by vm.offlineSync.pendingCount.collectAsState(initial = 0)
    val totalBytes by vm.downloads.totalSizeBytes.collectAsState(initial = 0L)
    val s = settings

    var showLogout by remember { mutableStateOf(false) }
    var keepDownloads by remember { mutableStateOf(true) }
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    val creds by vm.credentials.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        topBar.title = "Settings"
        topBar.alwaysShowTitle = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (s == null) {
            Text("Loading…")
            return@Column
        }

        // ---------- Account ----------
        Section("Account")
        var serverUrl by remember(creds?.serverUrl) { mutableStateOf(creds?.serverUrl.orEmpty()) }
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Signed in as ${creds?.username ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = VusicColors.SecondaryText,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.setServerUrl(serverUrl); vm.testConnection() },
                enabled = test !is TestState.Testing && serverUrl.isNotBlank(),
            ) { Text(if (test is TestState.Testing) "Testing…" else "Test connection") }
            OutlinedButton(onClick = { showLogout = true }) { Text("Logout") }
        }
        when (val t = test) {
            is TestState.Ok -> Label("Connection OK", VusicColors.OnAccent)
            is TestState.Failed -> Label("Failed: ${t.message}", MaterialTheme.colorScheme.error)
            else -> Unit
        }
        if (!online) Label("You're offline — showing cached library", VusicColors.SecondaryText)

        // ---------- Playback ----------
        Section("Playback")
        BitrateRow("Wi-Fi", s.wifiBitrate, s.wifiFormat, vm::setWifi)
        BitrateRow("Mobile", s.mobileBitrate, s.mobileFormat, vm::setMobile)
        SwitchRow("Offline mode (downloaded music only)", s.offlineMode, vm::setOfflineMode)
        SwitchRow("Gapless playback", s.gapless, vm::setGapless)
        SwitchRow("Pause when headphones disconnect", s.pauseOnHeadphone, vm::setPauseOnHeadphone)
        SwitchRow("Resume after audio focus loss", s.resumeOnFocusGain, vm::setResumeOnFocusGain)
        SwitchRow("Crossfade (best-effort volume ramp)", s.crossfade, vm::setCrossfade)
        if (s.crossfade) {
            NumberRow("Crossfade length", "${s.crossfadeSeconds}s", listOf(1, 3, 6, 9, 12)) {
                vm.setCrossfadeSeconds(it)
            }
        }

        // ---------- ReplayGain ----------
        SwitchRow("ReplayGain", s.replayGain, vm::setReplayGain)
        if (s.replayGain) {
            ChoiceRow(
                "Mode",
                ReplayGainMode.entries.map { it.name },
                s.replayGainMode.name,
            ) { vm.setReplayGainMode(runCatching { ReplayGainMode.valueOf(it) }.getOrDefault(ReplayGainMode.TRACK)) }
            NumberRow("Pre-amp", "${s.replayGainPreampDb} dB", (-15..15 step 3).toList()) {
                vm.setReplayGainPreamp(it)
            }
            NumberRow("Fallback gain", "${s.replayGainFallbackDb} dB", (-15..15 step 3).toList()) {
                vm.setReplayGainFallback(it)
            }
        }

        // ---------- Downloads & storage ----------
        Section("Downloads & storage")
        SwitchRow("Download over Wi-Fi only", s.downloadWifiOnly, vm::setDownloadWifiOnly)
        SwitchRow("Download original files", s.downloadOriginal, vm::setDownloadOriginal)
        if (!s.downloadOriginal) {
            ChoiceRow("Download quality", QualityPolicy.BITRATES.map { it.toString() }, s.downloadBitrate.toString()) {
                vm.setDownloadBitrate(it.toIntOrNull() ?: 320)
            }
            ChoiceRow("Download format", QualityPolicy.FORMATS, s.downloadFormat, vm::setDownloadFormat)
        }
        Label("Downloads: ${CacheSizePolicy.human(totalBytes)} · ${pending} pending upload(s)", VusicColors.SecondaryText)
        NumberRow(
            "Stream cache",
            "${s.cacheSizeMb} MB",
            CacheSizePolicy.STREAM_SIZES_MB,
        ) { vm.setCache(it) }
        NumberRow(
            "Image cache",
            "${s.imageCacheMb} MB",
            CacheSizePolicy.IMAGE_SIZES_MB,
        ) { vm.setImageCache(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenDownloads) { Text("Manage downloads") }
            OutlinedButton(onClick = { vm.clearMetadataCache() }) { Text("Clear library cache") }
        }

        // ---------- Network & security ----------
        Section("Network & security")
        SwitchRow("Allow self-signed certificates", s.allowSelfSigned, vm::setSelfSigned)
        SwitchRow("Allow cleartext HTTP (insecure on public networks)", s.allowCleartext, vm::setCleartext)
        NumberRow("Connection timeout", "${s.timeoutSeconds}s", listOf(10, 20, 30, 60, 120)) { vm.setTimeout(it) }

        // ---------- Appearance ----------
        Section("Appearance")
        ChoiceRow("Theme", ThemeMode.entries.map { it.name }, s.theme.name) {
            vm.setTheme(runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.DARK))
        }
        SwitchRow("Dynamic Now Playing color", s.dynamicNpColor, vm::setDynamicNpColor)
        ChoiceRow("Default layout", LayoutMode.entries.map { it.name }, s.defaultLayout.name) {
            vm.setDefaultLayout(runCatching { LayoutMode.valueOf(it) }.getOrDefault(LayoutMode.GRID))
        }

        // ---------- About ----------
        Section("About")
        Label("Vusic ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", VusicColors.SecondaryText)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { /* licenses screen lives in the About sheet */ }) { Text("Open source licenses") }
        Spacer(Modifier.height(48.dp))
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Log out?") },
            text = {
                Column {
                    Text("Your credentials, cached library data and caches will be cleared.")
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Keep downloaded music", keepDownloads) { keepDownloads = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    vm.logout(keepDownloads) { onLogout() }
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Label(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, { onSet(it) })
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: String, onSet: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(selected) }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { onSet(o); open = false })
                }
            }
        }
    }
}

@Composable
private fun NumberRow(label: String, current: String, options: List<Int>, onSet: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(current) }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o.toString()) }, onClick = { onSet(o); open = false })
                }
            }
        }
    }
}

@Composable
private fun BitrateRow(label: String, br: Int, fmt: String, onSet: (Int, String) -> Unit) {
    var brMenu by remember { mutableStateOf(false) }
    var fmtMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label streaming", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Row {
            Box {
                OutlinedButton(onClick = { brMenu = true }) { Text(if (br == 0) "Original" else "$br kbps") }
                DropdownMenu(brMenu, onDismissRequest = { brMenu = false }) {
                    QualityPolicy.BITRATES.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(if (b == 0) "Original" else "$b kbps") },
                            onClick = { onSet(b, fmt); brMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Box {
                OutlinedButton(onClick = { fmtMenu = true }) { Text(fmt) }
                DropdownMenu(fmtMenu, onDismissRequest = { fmtMenu = false }) {
                    QualityPolicy.FORMATS.forEach { f ->
                        DropdownMenuItem(text = { Text(f) }, onClick = { onSet(br, f); fmtMenu = false })
                    }
                }
            }
        }
    }
}
