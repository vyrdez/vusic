package com.feishin.mobile.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode

private enum class SettingsRoute {
    HOME, SERVER, APPEARANCE, SOUND, CONNECTIVITY, STORAGE, ABOUT
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLibraryDownloads: () -> Unit = onOpenDownloads,
) {
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HOME) }
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    LaunchedEffect(route) {
        topBar.title = if (route == SettingsRoute.HOME) "Settings" else null
        topBar.alwaysShowTitle = true
    }
    if (route != SettingsRoute.HOME) {
        BackHandler { route = SettingsRoute.HOME }
    }
    when (route) {
        SettingsRoute.HOME -> SettingsHome(vm, onOpen = { route = it })
        SettingsRoute.SERVER -> SubScreen("Server & Account", onBack = { route = SettingsRoute.HOME }) {
            ServerAccountScreen(vm, onLogout)
        }
        SettingsRoute.APPEARANCE -> SubScreen("Appearance & Layout", onBack = { route = SettingsRoute.HOME }) {
            AppearanceScreen(vm)
        }
        SettingsRoute.SOUND -> SubScreen("Sound & Playback", onBack = { route = SettingsRoute.HOME }) {
            SoundScreen(vm)
        }
        SettingsRoute.CONNECTIVITY -> SubScreen("Connectivity", onBack = { route = SettingsRoute.HOME }) {
            ConnectivityScreen(vm)
        }
        SettingsRoute.STORAGE -> SubScreen("Storage", onBack = { route = SettingsRoute.HOME }) {
            StorageScreen(vm, onOpenDownloads, onOpenLibraryDownloads)
        }
        SettingsRoute.ABOUT -> SubScreen("About", onBack = { route = SettingsRoute.HOME }) {
            AboutScreen()
        }
    }
}

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = LocalMiniClearance.current),
        ) {
            content()
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsHome(
    vm: SettingsViewModel,
    onOpen: (SettingsRoute) -> Unit,
) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val online by vm.network.isOnline.collectAsState()
    val s = settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(bottom = LocalMiniClearance.current),
    ) {
        // Same title row as Library: one shared component, identical spacing.
        com.feishin.mobile.ui.components.ScreenTitleRow(title = "Settings")
        Column(Modifier.padding(horizontal = VusicSpacing.ScreenHorizontal)) {
            if (s != null && (s.offlineMode || (s.autoOffline && !online)) && s.showOfflineChip) {
                Spacer(Modifier.height(4.dp))
                OfflineChip()
            }
            Spacer(Modifier.height(8.dp))
            SettingsHomeCard(
                icon = Icons.Default.AccountCircle,
                title = "Server & Account",
                summary = vm.credentials.collectAsState().value?.let { "${it.username} · ${it.serverUrl}" } ?: "Not signed in",
                onClick = { onOpen(SettingsRoute.SERVER) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Palette,
                title = "Appearance & Layout",
                summary = s?.let { "${it.theme.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.defaultLayout.name.lowercase()} layout" } ?: "…",
                onClick = { onOpen(SettingsRoute.APPEARANCE) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Speaker,
                title = "Sound & Playback",
                summary = s?.let {
                    val q = if (it.wifiBitrate == 0) "Original" else "${it.wifiBitrate} kbps"
                    "$q · ${it.replayGainMode.name.lowercase()} gain" +
                        if (it.crossfade) " · crossfade ${it.crossfadeSeconds}s" else ""
                } ?: "…",
                onClick = { onOpen(SettingsRoute.SOUND) },
            )
            SettingsHomeCard(
                icon = Icons.Default.SettingsInputAntenna,
                title = "Connectivity",
                summary = s?.let {
                    buildList {
                        if (it.offlineMode) add("Offline mode")
                        if (it.autoOffline) add("Auto offline")
                        if (it.allowSelfSigned) add("Self-signed allowed")
                        if (isEmpty()) add("Online")
                    }.joinToString(" · ")
                } ?: "…",
                onClick = { onOpen(SettingsRoute.CONNECTIVITY) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Storage,
                title = "Storage",
                summary = "Downloads, song cache and image cache",
                onClick = { onOpen(SettingsRoute.STORAGE) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Info,
                title = "About",
                summary = "Vusic ${BuildConfig.VERSION_NAME}",
                onClick = { onOpen(SettingsRoute.ABOUT) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Vusic ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                modifier = Modifier.padding(start = 4.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun OfflineChip() {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
        Text(
            "Offline",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFFB74D),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// ---------- Server & Account ----------

@Composable
private fun ServerAccountScreen(vm: SettingsViewModel, onLogout: () -> Unit) {
    val creds by vm.credentials.collectAsState()
    val test by vm.test.collectAsState()
    val online by vm.network.isOnline.collectAsState()
    val info by vm.serverInfo.collectAsState()
    val scan by vm.scanStatus.collectAsState()
    val counts by vm.libraryCounts.collectAsState()
    var showLogout by androidx.compose.runtime.remember { mutableStateOf(false) }
    var keepDownloads by androidx.compose.runtime.remember { mutableStateOf(true) }
    var serverUrl by androidx.compose.runtime.remember(creds?.serverUrl) {
        mutableStateOf(creds?.serverUrl.orEmpty())
    }

    LaunchedEffect(Unit) { vm.refreshServerInfo() }

    SettingsSection("SERVER")
    SettingsCard {
        InfoRow("Type", info?.product ?: "…")
        InfoRow("API version", info?.apiVersion ?: "…")
        if (!info?.extensions.isNullOrEmpty()) {
            InfoRow("Extensions", info!!.extensions.take(4).joinToString(", "))
        }
        InfoRow("Server URL", creds?.serverUrl ?: "—")
    }
    var editing by androidx.compose.runtime.remember { mutableStateOf(false) }
    if (!editing) {
        ActionRow("Change server URL", creds?.serverUrl ?: "", buttonLabel = "Edit", onClick = { editing = true })
    } else {
        OutlinedTextField(
            value = serverUrl, onValueChange = { serverUrl = it },
            label = { Text("Server URL") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Row {
            androidx.compose.material3.TextButton(onClick = {
                editing = false
                vm.setServerUrl(serverUrl)
                vm.testConnection()
            }) { Text("Save & test") }
            androidx.compose.material3.TextButton(onClick = { editing = false }) { Text("Cancel") }
        }
    }
    when (val t = test) {
        is TestState.Ok -> Text("Connection OK", color = VusicColors.OnAccent, style = MaterialTheme.typography.bodySmall)
        is TestState.Failed -> Text("Failed: ${t.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else -> Unit
    }

    SettingsSection("LIBRARY")
    SettingsCard {
        InfoRow("Artists", counts.artists.toString())
        InfoRow("Albums", counts.albums.toString())
        InfoRow("Playlists", counts.playlists.toString())
        InfoRow("Downloaded songs", counts.downloaded.toString())
    }
    ActionRow(
        "Scan library",
        when {
            scan?.scanning == true -> "Scanning… (${scan?.count ?: 0} files)"
            !online -> "Unavailable offline"
            else -> "Ask the server to rescan its folders"
        },
        buttonLabel = "Scan",
        onClick = { vm.startScan() },
    )

    SettingsSection("ACCOUNT")
    SettingsCard {
        InfoRow("Username", creds?.username ?: "—")
        InfoRow("Device", vm.deviceName)
    }
    Spacer(Modifier.height(12.dp))
    androidx.compose.material3.OutlinedButton(
        onClick = { showLogout = true },
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF8A80)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Log out", color = Color(0xFFFF8A80))
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Log out?") },
            text = {
                Column {
                    Text("Your credentials, cached library data and caches will be cleared.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Keep downloaded music", modifier = Modifier.weight(1f))
                        Switch(
                            checked = keepDownloads,
                            onCheckedChange = { keepDownloads = it },
                            colors = com.feishin.mobile.ui.theme.VusicControlDefaults.switchColors(),
                        )
                    }
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

// ---------- Appearance & Layout ----------

@Composable
private fun AppearanceScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val s = settings ?: return
    SettingsSection("THEME")
    SettingsCard {
        ChoiceRow(
            "Theme",
            s.theme.name.lowercase().replaceFirstChar(Char::uppercase),
            ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { vm.setTheme(ThemeMode.valueOf(it.uppercase())) },
        )
        SwitchRow("Dynamic Now Playing color", s.dynamicNpColor, vm::setDynamicNpColor)
    }
    SettingsSection("LIBRARY LAYOUT")
    SettingsCard {
        listOf("Songs" to "SONGS", "Albums" to "ALBUMS", "Artists" to "ARTISTS", "Playlists" to "PLAYLISTS").forEach { (label, tab) ->
            val current = s.libraryLayouts[tab] ?: s.defaultLayout
            ChoiceRow(label, current.name.lowercase().replaceFirstChar(Char::uppercase), listOf("Grid", "List"), onSelect = {
                vm.setLibraryLayout(tab, if (it == "Grid") LayoutMode.GRID else LayoutMode.LIST)
            })
        }
    }
    SettingsSection("DEFAULT SORT")
    SettingsCard {
        listOf("Songs" to "SONGS", "Albums" to "ALBUMS", "Artists" to "ARTISTS", "Playlists" to "PLAYLISTS").forEach { (label, tab) ->
            val choice = s.librarySorts[tab]
                ?: com.feishin.mobile.core.common.LibrarySorts.default(tab)
            val options = com.feishin.mobile.core.common.LibrarySorts.options(tab)
            ChoiceRow(
                label,
                "${options.firstOrNull { it.id == choice.option }?.label ?: choice.option} " +
                    if (choice.ascending) "↑" else "↓",
                options.map { it.label },
                onSelect = { picked ->
                    val id = options.firstOrNull { it.label == picked }?.id ?: choice.option
                    vm.setLibrarySort(tab, id, choice.ascending)
                },
            )
        }
    }
    SettingsSection("GENERATED LISTS")
    SettingsCard {
        ChoiceRow(
            "Mix & radio length",
            "${s.radioBatchSize} songs",
            SettingsStore.RADIO_BATCH_OPTIONS.map { "$it songs" },
            onSelect = { vm.setRadioBatchSize(it.split(" ").first().toIntOrNull() ?: 20) },
            description = "Songs per generated mix and radio batch.",
        )
    }
}

// ---------- Sound & Playback ----------

@Composable
private fun SoundScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val storage by vm.storageStats.collectAsState()
    val s = settings ?: return
    var showReset by androidx.compose.runtime.remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshStorageStats() }

    SettingsSection("STREAMING")
    SettingsCard {
        BitrateChoiceRow("Wi-Fi max bitrate", s.wifiBitrate, s.wifiFormat, vm::setWifi)
        BitrateChoiceRow("Mobile max bitrate", s.mobileBitrate, s.mobileFormat, vm::setMobile)
    }
    SettingsSection("DOWNLOADS")
    SettingsCard {
        SwitchRow("Download original files", s.downloadOriginal, vm::setDownloadOriginal)
        if (!s.downloadOriginal) {
            BitrateChoiceRow("Download max bitrate", s.downloadBitrate, s.downloadFormat) { br, fmt ->
                vm.setDownloadBitrate(br)
                vm.setDownloadFormat(fmt)
            }
        }
    }
    SettingsSection("REPLAYGAIN")
    SettingsCard {
        ChoiceRow(
            "Mode", s.replayGainMode.name.lowercase().replaceFirstChar(Char::uppercase),
            ReplayGainMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { vm.setReplayGainMode(ReplayGainMode.valueOf(it.uppercase())) },
        )
        // Discrete-step rows: ReplayGain sliders snap to whole dB.
        val dbSteps = (-15..15 step 3).map { "$it dB" }
        SliderRow(
            "Pre-amp",
            "${s.replayGainPreampDb} dB",
            index = nearestIndex(dbSteps, "${s.replayGainPreampDb} dB"),
            steps = dbSteps,
            onIndex = { vm.setReplayGainPreamp(dbSteps[it].removeSuffix(" dB").toIntOrNull() ?: 0) },
        )
        SliderRow(
            "Fallback gain",
            "${s.replayGainFallbackDb} dB",
            index = nearestIndex(dbSteps, "${s.replayGainFallbackDb} dB"),
            steps = dbSteps,
            onIndex = { vm.setReplayGainFallback(dbSteps[it].removeSuffix(" dB").toIntOrNull() ?: 0) },
        )
    }
    SettingsSection("TRANSITIONS")
    SettingsCard {
        SwitchRow("Gapless playback", s.gapless, vm::setGapless, showDivider = true)
        SwitchRow(
            "Crossfade", s.crossfade, vm::setCrossfade,
            description = "Best-effort volume ramp; true overlap would break gapless.",
            showDivider = s.crossfade,
        )
        if (s.crossfade) {
            val secSteps = listOf(1, 3, 6, 9, 12).map { "${it}s" }
            SliderRow(
                "Crossfade duration",
                "${s.crossfadeSeconds}s",
                index = nearestIndex(secSteps, "${s.crossfadeSeconds}s"),
                steps = secSteps,
                onIndex = { vm.setCrossfadeSeconds(secSteps[it].removeSuffix("s").toIntOrNull() ?: 6) },
            )
        }
    }
    SettingsSection("PLAYBACK CACHE")
    SettingsCard {
        SwitchRow("Cache upcoming tracks", s.cacheUpcoming, vm::setCacheUpcoming, showDivider = true)
        if (s.cacheUpcoming) {
            val trackSteps = (1..5).map { "$it" }
            SliderRow(
                "Tracks to cache",
                "${s.tracksToCache}",
                index = (s.tracksToCache - 1).coerceIn(0, 4),
                steps = trackSteps,
                onIndex = { vm.setTracksToCache(it + 1) },
                description = "Next songs are fully buffered ahead of time.",
            )
        }
        SwitchRow(
            "Cache played songs", s.cachePlayedSongs, vm::setCachePlayedSongs,
            description = "Keep every song you finish (or pass 50% of).",
            showDivider = true,
        )
        SwitchRow("Cache on Wi-Fi only", s.cacheWifiOnly, vm::setCacheWifiOnly, showDivider = true)
        InfoRow(
            "Song cache usage",
            storage?.let { com.feishin.mobile.core.common.CacheSizePolicy.human(it.cachedBytes) } ?: "…",
        )
        if (storage?.let { !com.feishin.mobile.core.common.StorageLimits.cachingAllowed(it.freeBytes) } == true) {
            Text(
                com.feishin.mobile.core.common.StorageLimits.LOW_SPACE_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB74D),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    SettingsSection("BEHAVIOR")
    SettingsCard {
        SwitchRow("Pause on headphone disconnect", s.pauseOnHeadphone, vm::setPauseOnHeadphone, showDivider = true)
        SwitchRow("Resume after audio focus loss", s.resumeOnFocusGain, vm::setResumeOnFocusGain, showDivider = true)
        ActionRow(
            "Battery optimization",
            if (vm.batteryExempted()) "Exempted — playback survives doze" else "Not exempted — playback may pause in background",
            buttonLabel = if (vm.batteryExempted()) "OK" else "Exempt",
            onClick = { if (!vm.batteryExempted()) vm.requestBatteryExemption() },
        )
    }
    SettingsSection("DEFAULTS")
    SettingsCard {
        ActionRow(
            "Reset to defaults", "Restore this screen's original settings",
            confirmTitle = "Reset playback settings?",
            confirmBody = "Streaming, download, ReplayGain, transition and cache settings return to defaults.",
            buttonLabel = "Reset",
            onClick = { vm.resetPlaybackDefaults() },
        )
    }
}

/** Index of [value] in [steps], or the closest match. */
private fun nearestIndex(steps: List<String>, value: String): Int {
    val i = steps.indexOf(value)
    return if (i >= 0) i else 0
}

@Composable
private fun BitrateChoiceRow(label: String, br: Int, fmt: String, onSet: (Int, String) -> Unit) {
    var brMenu by androidx.compose.runtime.remember { mutableStateOf(false) }
    var fmtMenu by androidx.compose.runtime.remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Label on its own line, then the two controls left-aligned under it, so
        // the "Original" / "raw" pills are no longer stranded at the far right.
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(VusicSpacing.ControlGap / 2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box {
                SettingsActionButton(
                    label = if (br == 0) "Original" else "$br kbps",
                    onClick = { brMenu = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            "Open bitrates", tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    },
                )
                androidx.compose.material3.DropdownMenu(brMenu, onDismissRequest = { brMenu = false }) {
                    QualityPolicy.BITRATES.forEach { b ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (b == 0) "Original" else "$b kbps") },
                            onClick = { onSet(b, fmt); brMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.size(VusicSpacing.ControlGap / 2))
            androidx.compose.foundation.layout.Box {
                SettingsActionButton(
                    label = fmt,
                    onClick = { fmtMenu = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            "Open formats", tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    },
                )
                androidx.compose.material3.DropdownMenu(fmtMenu, onDismissRequest = { fmtMenu = false }) {
                    QualityPolicy.FORMATS.forEach { f ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(f) },
                            onClick = { onSet(br, f); fmtMenu = false },
                        )
                    }
                }
            }
        }
    }
}

// ---------- Connectivity ----------

@Composable
private fun ConnectivityScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val online by vm.network.isOnline.collectAsState()
    val s = settings ?: return
    SettingsSection("OFFLINE")
    SettingsCard {
        SwitchRow(
            "Offline mode", s.offlineMode, vm::setOfflineMode,
            description = "Downloaded and cached music only.",
        )
        SwitchRow("Show offline chip", s.showOfflineChip, vm::setShowOfflineChip)
        SwitchRow(
            "Auto offline", s.autoOffline, vm::setAutoOffline,
            description = "Follow the network automatically when offline mode is off.",
        )
        InfoRow("Connection", if (online) "Online" else "Offline")
    }
    SettingsSection("CERTIFICATES")
    SettingsCard {
        SwitchRow(
            "Self-signed certificates", s.allowSelfSigned, vm::setSelfSigned,
            description = "Applies immediately, no restart.",
            warning = "Only enable for servers you trust.",
        )
        SwitchRow(
            "Allow cleartext HTTP", s.allowCleartext, vm::setCleartext,
            warning = "Traffic can be read on public networks.",
        )
        ChoiceRow(
            "Connection timeout", "${s.timeoutSeconds}s",
            listOf("10s", "20s", "30s", "60s", "120s"),
            onSelect = { vm.setTimeout(it.removeSuffix("s").toIntOrNull() ?: 20) },
        )
    }
}

// ---------- Storage ----------

@Composable
private fun StorageScreen(
    vm: SettingsViewModel,
    onOpenDownloads: () -> Unit,
    onOpenLibraryDownloads: () -> Unit,
) {
    val settings by vm.store.flow.collectAsState(initial = null)
    val storage by vm.storageStats.collectAsState()
    val s = settings ?: return
    LaunchedEffect(Unit) { vm.refreshStorageStats() }
    val st = storage
    val deviceMax = vm.deviceMaxBytes()
    val total = (st?.let { it.downloadBytes + it.coverBytes + it.streamCacheBytes + it.imageCacheBytes } ?: 1L)
        .coerceAtLeast(1L)

    SettingsSection("USAGE")
    SettingsCard {
        UsageCard(
            imagesBytes = st?.let { it.imageCacheBytes + it.coverBytes } ?: 0L,
            downloadsBytes = st?.downloadBytes ?: 0L,
            songCacheBytes = st?.streamCacheBytes ?: 0L,
            freeBytes = st?.freeBytes ?: 0L,
            human = { com.feishin.mobile.core.common.CacheSizePolicy.human(it) },
        )
    }
    SettingsSection("LIMITS")
    SettingsCard {
        val capOff = s.maxDownloadMb <= 0
        val capSteps = com.feishin.mobile.core.common.StorageLimits.steps(deviceMax)
        SwitchRow(
            "Limit download storage", !capOff,
            onSet = { on ->
                vm.setMaxDownloadMb(
                    if (on) (capSteps[capSteps.size / 2] / com.feishin.mobile.core.common.StorageLimits.MB).toInt() else 0,
                )
            },
            showDivider = !capOff,
        )
        if (!capOff) {
            val capBytes = s.maxDownloadMb.toLong() * com.feishin.mobile.core.common.StorageLimits.MB
            val capIndex = com.feishin.mobile.core.common.StorageLimits.indexOf(capSteps, capBytes)
            val capWarn = if (st != null && com.feishin.mobile.core.common.StorageLimits.exceedsAvailable(capSteps[capIndex], st.downloadBytes, st.freeBytes)) {
                "Limit is larger than this device's free space plus current usage."
            } else null
            SliderRow(
                "Download cap",
                com.feishin.mobile.core.common.StorageLimits.label(capSteps[capIndex]),
                index = capIndex,
                steps = capSteps.map { com.feishin.mobile.core.common.StorageLimits.label(it) },
                onIndex = {
                    vm.setMaxDownloadMb((capSteps[it] / com.feishin.mobile.core.common.StorageLimits.MB).toInt())
                },
                description = "New downloads pause when the cap is reached.",
                warning = capWarn,
            )
        }
    }
    SettingsSection("DOWNLOADED MUSIC")
    SettingsCard {
        InfoRow("Songs", st?.downloadSongs?.toString() ?: "…", showDivider = true)
        InfoRow("Disk usage", st?.let { com.feishin.mobile.core.common.CacheSizePolicy.human(it.downloadBytes) } ?: "…", showDivider = true)
        InfoRow("Concurrent downloads", st?.let { "${it.activeDownloads} active · ${it.queuedDownloads} queued" } ?: "…", showDivider = true)
        ActionRow("Browse downloaded music", buttonLabel = "Browse", onClick = onOpenLibraryDownloads, showDivider = true)
        ActionRow("Download queue", buttonLabel = "Open", onClick = onOpenDownloads)
    }
    SettingsSection("SONG CACHE")
    SettingsCard {
        val cacheSteps = com.feishin.mobile.core.common.StorageLimits.steps(deviceMax)
        val cacheBytes = s.cacheSizeMb.toLong() * com.feishin.mobile.core.common.StorageLimits.MB
        val cacheIndex = com.feishin.mobile.core.common.StorageLimits.indexOf(cacheSteps, cacheBytes)
        InfoRow("Songs cached", st?.cachedSongs?.toString() ?: "…", showDivider = true)
        SliderRow(
            "Cache size",
            com.feishin.mobile.core.common.StorageLimits.label(cacheSteps[cacheIndex]),
            index = cacheIndex,
            steps = cacheSteps.map { com.feishin.mobile.core.common.StorageLimits.label(it) },
            onIndex = { vm.setCache((cacheSteps[it] / com.feishin.mobile.core.common.StorageLimits.MB).toInt()) },
            description = "Applies immediately; shrinking trims the cache now.",
            showDivider = true,
        )
        ActionRow(
            "Clear song cache", "Also removes the Cached collection",
            confirmTitle = "Clear song cache?",
            buttonLabel = "Clear",
            onClick = { vm.clearSongCache() },
        )
    }
    SettingsSection("IMAGE CACHE")
    SettingsCard {
        val imageSteps = com.feishin.mobile.core.common.StorageLimits.steps(deviceMax)
        val imageBytes = s.imageCacheMb.toLong() * com.feishin.mobile.core.common.StorageLimits.MB
        val imageIndex = com.feishin.mobile.core.common.StorageLimits.indexOf(imageSteps, imageBytes)
        SliderRow(
            "Cache size",
            com.feishin.mobile.core.common.StorageLimits.label(imageSteps[imageIndex]),
            index = imageIndex,
            steps = imageSteps.map { com.feishin.mobile.core.common.StorageLimits.label(it) },
            onIndex = { vm.setImageCache((imageSteps[it] / com.feishin.mobile.core.common.StorageLimits.MB).toInt()) },
            description = "Shrinking clears cached images immediately; growth takes effect on next launch.",
            showDivider = true,
        )
        ActionRow("Clear image cache", buttonLabel = "Clear", onClick = { vm.clearImageCache() })
        val restartPending by vm.imageCacheRestartPending.collectAsState()
        if (restartPending) {
            ActionRow(
                "Restart to apply",
                "The image cache can only grow after a restart.",
                buttonLabel = "Restart now",
                danger = false,
                onClick = { vm.restartApp() },
            )
        }
    }
    SettingsSection("DANGEROUS")
    SettingsCard {
        ActionRow(
            "Delete all downloads", "Removes every downloaded song and its files",
            danger = true, confirmTitle = "Delete all downloads?",
            buttonLabel = "Delete",
            onClick = { vm.deleteAllDownloads() },
        )
        ActionRow(
            "Clear library cache", "Browsing falls back to the network",
            danger = true, confirmTitle = "Clear library cache?",
            buttonLabel = "Clear",
            onClick = { vm.clearMetadataCache() },
        )
    }
}

// ---------- About ----------

@Composable
private fun AboutScreen() {
    SettingsSection("APP")
    SettingsCard {
        InfoRow("Vusic", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        InfoRow("Protocol", "Subsonic / OpenSubsonic API")
    }
    SettingsSection("OPEN SOURCE")
    SettingsCard {
        InfoRow("Kotlin, Compose, Media3", "Apache 2.0")
        InfoRow("Hilt, Room, WorkManager", "Apache 2.0")
        InfoRow("Coil, OkHttp, Retrofit", "Apache 2.0 / MIT")
    }
}
