package com.feishin.mobile.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.feishin.mobile.core.common.DeviceFilesFilter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.BuildConfig
import com.feishin.mobile.core.common.CacheSizePolicy
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.ReplayGainMode
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.ui.login.LoginViewModel
import com.feishin.mobile.ui.login.ServerFormContent
import com.feishin.mobile.ui.theme.LocalVusicDark
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

private enum class SettingsRoute {
    HOME, SERVER, SERVER_EDIT, APPEARANCE, SOUND, CONNECTIVITY, STORAGE, ABOUT
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLibraryDownloads: () -> Unit = onOpenDownloads,
) {
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HOME) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    // Direction of the last navigation: forward into a sub-screen or back out.
    // Drives the AnimatedContent slide direction below, so the back arrow,
    // Cancel, Save, and system back gesture reverse the enter animation.
    var forward by rememberSaveable { mutableStateOf(true) }
    fun go(target: SettingsRoute, isForward: Boolean = target != SettingsRoute.HOME) {
        forward = isForward
        com.feishin.mobile.core.common.VLog.d("SettingsNav", "route=$route -> $target forward=$forward")
        route = target
    }
    val topBar = com.feishin.mobile.ui.LocalTopBarController.current
    LaunchedEffect(route) {
        topBar.title = if (route == SettingsRoute.HOME) "Settings" else null
        topBar.alwaysShowTitle = true
    }
    if (route != SettingsRoute.HOME && route != SettingsRoute.SERVER_EDIT) {
        BackHandler { go(SettingsRoute.HOME, isForward = false) }
    }
    // Sub-screens used to swap with a bare `when`, so the shared NavHost
    // transitions never applied and screens popped with no animation at all.
    androidx.compose.animation.AnimatedContent(
        targetState = route,
        transitionSpec = {
            val dir = if (forward) {
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start
            } else {
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End
            }
            com.feishin.mobile.core.common.VLog.d("SettingsNav", "animating $initialState -> $targetState dir=$dir")
            (slideIntoContainer(dir, tween(com.feishin.mobile.ui.theme.Motion.scaled(260), easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                fadeIn(tween(com.feishin.mobile.ui.theme.Motion.scaled(200)))) togetherWith
                (slideOutOfContainer(dir, tween(com.feishin.mobile.ui.theme.Motion.scaled(260), easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                    fadeOut(tween(com.feishin.mobile.ui.theme.Motion.scaled(200))))
        },
        label = "settings-route",
    ) { r ->
        when (r) {
            SettingsRoute.HOME -> SettingsHome(vm, onOpen = { go(it) })
            SettingsRoute.SERVER -> SubScreen(
                title = "Server & Account",
                onBack = { go(SettingsRoute.HOME, isForward = false) },
                scrollable = false,
            ) {
                ServerAccountScreen(
                    vm = vm,
                    onLogout = onLogout,
                    onAddServer = {
                        vm.collapseAllProfiles()
                        editingProfileId = null
                        go(SettingsRoute.SERVER_EDIT, isForward = true)
                    },
                    onEditServer = { profileId ->
                        vm.collapseAllProfiles()
                        editingProfileId = profileId
                        go(SettingsRoute.SERVER_EDIT, isForward = true)
                    },
                )
            }
            SettingsRoute.SERVER_EDIT -> ServerEditScreen(
                editingProfileId = editingProfileId,
                settingsVm = vm,
                onSaved = {
                    vm.collapseAllProfiles()
                    vm.refreshServerInfo()
                    editingProfileId = null
                    go(SettingsRoute.SERVER, isForward = false)
                },
                onCancel = {
                    vm.collapseAllProfiles()
                    editingProfileId = null
                    go(SettingsRoute.SERVER, isForward = false)
                },
            )
            SettingsRoute.APPEARANCE -> SubScreen("Appearance & Layout", onBack = { go(SettingsRoute.HOME) }) {
                AppearanceScreen(vm)
            }
            SettingsRoute.SOUND -> SubScreen("Sound & Playback", onBack = { go(SettingsRoute.HOME) }) {
                SoundScreen(vm)
            }
            SettingsRoute.CONNECTIVITY -> SubScreen("Connectivity", onBack = { go(SettingsRoute.HOME) }) {
                ConnectivityScreen(vm)
            }
            SettingsRoute.STORAGE -> SubScreen("Storage", onBack = { go(SettingsRoute.HOME) }) {
                StorageScreen(vm, onOpenDownloads, onOpenLibraryDownloads)
            }
            SettingsRoute.ABOUT -> SubScreen("About", onBack = { go(SettingsRoute.HOME) }) {
                AboutScreen()
            }
        }
    }
}

@Composable
private fun ServerEditScreen(
    editingProfileId: String?,
    settingsVm: SettingsViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    loginVm: LoginViewModel = hiltViewModel(),
) {
    val ui by loginVm.ui.collectAsStateWithLifecycle()
    val profiles by settingsVm.profiles.collectAsStateWithLifecycle()
    val existingProfile = remember(editingProfileId, profiles) {
        editingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    }
    val busy = ui.busy

    BackHandler(enabled = !busy) { onCancel() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (editingProfileId != null) "Edit server" else "Add server",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text("Cancel")
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = VusicSpacing.ScreenHorizontal * 1.5f)
                .padding(bottom = LocalMiniClearance.current + 24.dp),
        ) {
            Spacer(Modifier.height(VusicSpacing.SectionBottom))
            ServerFormContent(
                vm = loginVm,
                messenger = settingsVm.uiMessenger,
                existingProfile = existingProfile,
                addMode = true,
                onSuccess = onSaved,
            )
        }
    }
}

@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (scrollable) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = LocalMiniClearance.current),
            ) {
                content()
                Spacer(Modifier.height(48.dp))
            }
        } else {
            Box(
                Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsHome(
    vm: SettingsViewModel,
    onOpen: (SettingsRoute) -> Unit,
) {
    val settings by vm.store.flow.collectAsStateWithLifecycle(initialValue = null)
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val online by vm.isOnline.collectAsStateWithLifecycle()
    val s = settings
    val serverSummary = remember(profiles, s?.deviceFilesStandalone) {
        when {
            profiles.isEmpty() ->
                if (s?.deviceFilesStandalone == true) "Device files only" else "Not signed in"
            profiles.size == 1 -> {
                val p = profiles.single()
                val user = profileUsername(p).ifBlank { p.name }
                "$user · ${p.url}"
            }
            else -> {
                val active = profiles.filter { it.enabled }.ifEmpty { profiles }
                val labels = active.take(3).joinToString(", ") {
                    it.name.ifBlank { backendLabel(it.type) }
                }
                val suffix = if (active.size > 3) ", +${active.size - 3}" else ""
                "${profiles.size} servers · $labels$suffix"
            }
        }
    }
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
                Spacer(Modifier.height(4.dp))
            }
            SettingsHomeCard(
                icon = Icons.Default.AccountCircle,
                title = "Server & Account",
                summary = serverSummary,
                onClick = { onOpen(SettingsRoute.SERVER) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Palette,
                title = "Appearance & Layout",
                summary = s?.let { "${it.theme.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.defaultLayout.name.lowercase()} layout · lyrics" } ?: "…",
                onClick = { onOpen(SettingsRoute.APPEARANCE) },
            )
            SettingsHomeCard(
                icon = Icons.Default.Speaker,
                title = "Sound & Playback",
                summary = s?.let {
                    val q = if (it.wifiBitrate == 0) "Original" else "${it.wifiBitrate} kbps"
                    "$q · ${it.replayGainMode.name.lowercase()} gain" +
                        (if (it.crossfade) " · crossfade ${it.crossfadeSeconds}s" else "") +
                        " · ${it.radioBatchSize}-song mixes"
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerAccountScreen(
    vm: SettingsViewModel,
    onLogout: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val testStates by vm.profileTestStates.collectAsStateWithLifecycle()
    val profileServerInfos by vm.profileServerInfos.collectAsStateWithLifecycle()
    val profileScanStates by vm.profileScanStates.collectAsStateWithLifecycle()
    val healthState by vm.healthState.collectAsStateWithLifecycle()
    val authStates by vm.authStates.collectAsStateWithLifecycle()
    val expandedIdsState = vm.expandedProfileIds.collectAsStateWithLifecycle()
    val online by vm.isOnline.collectAsStateWithLifecycle()
    val counts by vm.libraryCounts.collectAsStateWithLifecycle()
    val driveTrackCounts by vm.driveTrackCounts.collectAsStateWithLifecycle()

    var pendingRemoveProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var pendingReconnectProfileId by remember { mutableStateOf("") }
    var pendingReconnectEmail by remember { mutableStateOf("") }

    val driveReconnectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (pendingReconnectProfileId.isNotBlank()) {
            vm.onDriveReconnectResolution(
                profileId = pendingReconnectProfileId,
                resultCode = activityResult.resultCode,
                data = activityResult.data,
                fallbackEmail = pendingReconnectEmail,
            )
            pendingReconnectProfileId = ""
            pendingReconnectEmail = ""
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SettingsEvent.LaunchDriveReconnect -> {
                    pendingReconnectProfileId = event.profileId
                    pendingReconnectEmail = event.email
                    driveReconnectLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build(),
                    )
                }
            }
        }
    }

    var displayProfiles by remember(profiles) { mutableStateOf(profiles) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyColumnState(listState) { from, to ->
        val fromIdx = displayProfiles.indexOfFirst { it.id == from.key }
        val toIdx = displayProfiles.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            val mutable = displayProfiles.toMutableList()
            val item = mutable.removeAt(fromIdx)
            mutable.add(toIdx, item)
            displayProfiles = mutable
        }
    }

    LaunchedEffect(Unit) { vm.refreshServerInfo() }
    DisposableEffect(Unit) {
        onDispose { vm.collapseAllProfiles() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = LocalMiniClearance.current + 48.dp),
    ) {
        item(key = "header") {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.align(Alignment.CenterVertically)) {
                    SettingsSection("SERVER PROFILES")
                }
                SettingsActionButton(
                    label = "+ Add backend",
                    onClick = {
                        vm.collapseAllProfiles()
                        onAddServer()
                    },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        if (displayProfiles.isEmpty()) {
            item(key = "empty_profiles") {
                SettingsCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(
                            "No remote servers configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add a Navidrome, OpenSubsonic, Jellyfin or Google Drive backend to stream your music library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VusicColors.SecondaryText,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = displayProfiles,
            key = { _, profile -> profile.id },
            contentType = { _, _ -> "profile" },
        ) { index, profile ->
            val expanded by remember(profile.id) {
                derivedStateOf { profile.id in expandedIdsState.value }
            }
            val serverInfo = profileServerInfos[profile.id]
            val testState = testStates[profile.id] ?: TestState.Idle
            val health = healthState.profiles[profile.id]
            val authStatus = authStates[profile.id] ?: ProfileAuthStatus.OK
            val scanState = profileScanStates[profile.id] ?: ScanUiState.Idle()
            val indexedTrackCount = driveTrackCounts[profile.id] ?: 0
            val quotaLimited = (scanState as? ScanUiState.Finished)?.quotaLimited == true ||
                (scanState as? ScanUiState.Scanning)?.quotaLimited == true

            val cardUi = remember(profile, serverInfo, indexedTrackCount, quotaLimited) {
                profile.toServerProfileUi(
                    serverInfo = serverInfo,
                    indexedTrackCount = indexedTrackCount,
                    quotaLimited = quotaLimited,
                )
            }
            val statusUi = remember(
                profile.type,
                profile.enabled,
                online,
                testState,
                health,
                authStatus,
                scanState,
                indexedTrackCount,
                quotaLimited,
            ) {
                resolveProfileStatus(
                    enabled = profile.enabled,
                    online = online,
                    testState = testState,
                    health = health,
                    authStatus = authStatus,
                    backendType = profile.type,
                    scanState = scanState,
                    indexedTrackCount = indexedTrackCount,
                    quotaLimited = quotaLimited,
                )
            }
            val scanUi = remember(profile.type, profile.enabled, online, scanState) {
                resolveProfileScanUi(
                    capabilities = BackendCapabilities.forType(profile.type),
                    enabled = profile.enabled,
                    online = online,
                    scanState = scanState,
                    backendType = profile.type,
                )
            }

            ReorderableItem(reorderState, key = profile.id) { isDragging ->
                val dragModifier = Modifier.draggableHandle(
                    onDragStarted = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStopped = {
                        vm.reorderProfiles(displayProfiles.map { it.id })
                    },
                )
                ServerProfileCard(
                    ui = cardUi,
                    rank = index + 1,
                    expanded = expanded,
                    onToggleExpanded = { vm.toggleExpanded(profile.id) },
                    isDragging = isDragging,
                    dragHandleModifier = dragModifier,
                    canMoveUp = index > 0,
                    canMoveDown = index < displayProfiles.lastIndex,
                    status = statusUi,
                    isTesting = testState is TestState.Testing,
                    scanUi = scanUi,
                    onMoveUp = { vm.moveProfile(profile.id, -1) },
                    onMoveDown = { vm.moveProfile(profile.id, 1) },
                    onToggleEnabled = { vm.setProfileEnabled(profile.id, it) },
                    onEdit = {
                        vm.collapseAllProfiles()
                        onEditServer(profile.id)
                    },
                    onTestConnection = { vm.testProfileConnection(profile) },
                    onScan = { vm.startScan(profile.id) },
                    onCopyEndpoint = { vm.uiMessenger.show("Endpoint URL copied") },
                    onRequestRemove = { pendingRemoveProfile = profile },
                    onReconnectDrive = { vm.reconnectGoogleDrive(profile) },
                )
            }
        }

        item(key = "library_and_account") {
            Column(Modifier.fillMaxWidth()) {
                SettingsSection("LIBRARY")
                SettingsCard {
                    InfoRow("Artists", counts.artists.toString(), showDivider = true)
                    InfoRow("Albums", counts.albums.toString(), showDivider = true)
                    InfoRow("Playlists", counts.playlists.toString(), showDivider = true)
                    InfoRow("Downloaded songs", counts.downloaded.toString())
                }

                SettingsSection("ACCOUNT")
                SettingsCard {
                    InfoRow("Active servers", "${profiles.count { it.enabled }} / ${profiles.size}", showDivider = true)
                    InfoRow("Device", vm.deviceName)
                }
            }
        }
    }

    pendingRemoveProfile?.let { target ->
        var keepDownloads by remember(target.id) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { pendingRemoveProfile = null },
            title = { Text("Remove ${target.name}?") },
            text = {
                Column {
                    Text("Credentials and cached library metadata for this server will be removed.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Keep downloaded music",
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Switch(
                            checked = keepDownloads,
                            onCheckedChange = { keepDownloads = it },
                            colors = VusicControlDefaults.switchColors(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveProfile = null
                        vm.removeProfile(target.id, keepDownloads) { onLogout() }
                    },
                ) { Text("Remove", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveProfile = null }) { Text("Cancel") }
            },
        )
    }
}

// ---------- Appearance & Layout ----------

@Composable
private fun AppearanceScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    var showClearLyricsConfirm by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showLyricsSourcesDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    SettingsSection("THEME")
    SettingsCard {
        ChoiceRow(
            "Theme",
            s.theme.name.lowercase().replaceFirstChar(Char::uppercase),
            ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { vm.setTheme(ThemeMode.valueOf(it.uppercase())) },
            segmented = true,
        )
        SwitchRow("Dynamic Theme", s.dynamicNpColor, vm::setDynamicNpColor)
    }
    SettingsSection(title = "PLAYER")
    SettingsCard {
        val bgStyleOptions = BackgroundStyle.entries.map { it.label }
        ChoiceRow(
            label = "Mini-player background style",
            value = s.miniPlayerBgStyle.label,
            options = bgStyleOptions,
            onSelect = { label ->
                BackgroundStyle.entries.firstOrNull { it.label == label }?.let(vm::setMiniPlayerBgStyle)
            },
        )
        ChoiceRow(
            label = "Player background style",
            value = s.playerBgStyle.label,
            options = bgStyleOptions,
            onSelect = { label ->
                BackgroundStyle.entries.firstOrNull { it.label == label }?.let(vm::setPlayerBgStyle)
            },
        )
        ChoiceRow(
            label = "Queue page style",
            value = s.queueBgStyle.label,
            options = bgStyleOptions,
            onSelect = { label ->
                BackgroundStyle.entries.firstOrNull { it.label == label }?.let(vm::setQueueBgStyle)
            },
        )
    }
    SettingsSection("LYRICS")
    SettingsCard {
        SwitchRow(
            "Fetch lyrics online",
            s.lyricsFetchOnline,
            vm::setLyricsFetchOnline,
            description = "Search online providers using title, artist, album and duration",
        )
        val activeSourcesSummary = s.lyricsProviderOrder
            .filter { it in s.lyricsProviderEnabled }
            .map { id ->
                when (id) {
                    "LRCLIB" -> "LRCLIB"
                    "BETTER_LYRICS" -> "BetterLyrics"
                    "PAXSENIX" -> "Paxsenix"
                    "KUGOU" -> "KuGou"
                    "LOCAL_DATA" -> "Local data"
                    else -> id
                }
            }
            .joinToString(", ")
            .ifEmpty { "None" }
        ActionRow(
            "Lyrics sources",
            activeSourcesSummary,
            buttonLabel = "Configure",
            onClick = { showLyricsSourcesDialog = true },
        )
        val alignOptions = listOf("Left", "Center", "Right")
        ChoiceRow(
            "Lyrics text alignment",
            s.lyricsAlignment.lowercase().replaceFirstChar(Char::uppercase),
            alignOptions,
            onSelect = { vm.setLyricsAlignment(it.uppercase()) },
            segmented = true,
            description = "How lines are aligned on the Lyrics tab.",
        )
        val currentSizePct = com.feishin.mobile.data.settings.SettingsStore.parseLyricsTextSizePercent(s.lyricsTextSize)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Lyrics text size", style = MaterialTheme.typography.bodyLarge)
                Text("Adjust lyrics line text scaling (80% to 220%)", style = MaterialTheme.typography.bodySmall, color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        val newPct = (currentSizePct - 10).coerceIn(80, 220)
                        vm.setLyricsTextSize("${newPct}%")
                    },
                    enabled = currentSizePct > 80,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease text size",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "$currentSizePct%",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = {
                        val newPct = (currentSizePct + 10).coerceIn(80, 220)
                        vm.setLyricsTextSize("${newPct}%")
                    },
                    enabled = currentSizePct < 220,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase text size",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        // Option 6: Line gap stepper
        val currentGapPct = s.lyricsLineGapPercent
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Line gap", style = MaterialTheme.typography.bodyLarge)
                Text("Adjust spacing between lyric lines", style = MaterialTheme.typography.bodySmall, color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { vm.setLyricsLineGapPercent((currentGapPct - 10).coerceIn(50, 200)) },
                    enabled = currentGapPct > 50,
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease line gap",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = "$currentGapPct%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(
                    onClick = { vm.setLyricsLineGapPercent((currentGapPct + 10).coerceIn(50, 200)) },
                    enabled = currentGapPct < 200,
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase line gap",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        SwitchRow(
            "Auto-scroll lyrics",
            s.lyricsAutoScroll,
            vm::setLyricsAutoScroll,
            description = "Follows the current line.",
        )
        SwitchRow(
            "Tap a line to seek",
            s.lyricsTapToSeek,
            vm::setLyricsTapToSeek,
            description = "Tapping a line jumps playback to that line.",
        )
        SwitchRow(
            "Keep screen on",
            s.lyricsKeepScreenOn,
            vm::setLyricsKeepScreenOn,
            description = "While the Lyrics tab is open.",
        )
        SwitchRow(
            "Show unsynced lyrics",
            s.lyricsShowUnsynced,
            vm::setLyricsShowUnsynced,
            description = "Otherwise songs without synced lyrics show the no-lyrics state.",
        )
        SwitchRow(
            "Romanize lyrics",
            s.lyricsRomanize,
            vm::setLyricsRomanizeAutomatically,
            description = "Show romanized pronunciation for Japanese, Korean, and Chinese lyrics",
        )
        // Offset stepper: -2000..+2000 in 50 ms steps
        val offsetSteps = (-40..40).map { (it * 50).toString() }
        val offsetIndex = offsetSteps.map { kotlin.math.abs(it.toInt() - s.lyricsSyncOffsetMs) }
            .withIndex().minByOrNull { it.value }?.index ?: 40
        SliderRow(
            "Lyrics sync offset",
            "${s.lyricsSyncOffsetMs} ms",
            index = offsetIndex,
            steps = offsetSteps,
            onIndex = { vm.setLyricsSyncOffsetMs(offsetSteps[it].toInt()) },
            description = "Use this if lyrics are early or late.",
        )
        ActionRow("Reset sync offset", buttonLabel = "Reset", onClick = { vm.setLyricsSyncOffsetMs(0) })
        ActionRow("Clear lyrics cache", buttonLabel = "Clear", onClick = { showClearLyricsConfirm = true })
    }
    if (showClearLyricsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLyricsConfirm = false },
            title = { Text("Clear lyrics cache?") },
            text = { Text("All cached lyrics and negative lookup results will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearLyricsConfirm = false
                    vm.clearLyricsCache()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLyricsConfirm = false }) { Text("Cancel") }
            },
        )
    }
    if (showLyricsSourcesDialog) {
        LyricsSourcesDialog(
            order = s.lyricsProviderOrder,
            enabled = s.lyricsProviderEnabled,
            onReorder = { vm.setLyricsProviderOrder(it) },
            onToggle = { id, isEnabled ->
                val newSet = if (isEnabled) s.lyricsProviderEnabled + id else s.lyricsProviderEnabled - id
                vm.setLyricsProviderEnabled(newSet)
            },
            onReset = { vm.resetLyricsProviders() },
            onDismiss = { showLyricsSourcesDialog = false },
        )
    }
    SettingsSection("LIBRARY LAYOUT")
    SettingsCard {
        listOf("Songs" to "SONGS", "Albums" to "ALBUMS", "Artists" to "ARTISTS", "Playlists" to "PLAYLISTS").forEach { (label, tab) ->
            val current = s.libraryLayouts[tab] ?: s.defaultLayout
            ChoiceRow(label, current.name.lowercase().replaceFirstChar(Char::uppercase), listOf("Grid", "List"), onSelect = {
                vm.setLibraryLayout(tab, if (it == "Grid") LayoutMode.GRID else LayoutMode.LIST)
            },
            segmented = true,
        )
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
}

// ---------- Sound & Playback ----------

@Composable
private fun SoundScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsStateWithLifecycle(initialValue = null)
    val storage by vm.storageStats.collectAsStateWithLifecycle()
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
            segmented = true,
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

/** Index of [value] in [steps], or the closest match. */
private fun nearestIndex(steps: List<String>, value: String): Int {
    val i = steps.indexOf(value)
    return if (i >= 0) i else 0
}

@Composable
private fun BitrateChoiceRow(label: String, br: Int, fmt: String, onSet: (Int, String) -> Unit) {
    val brLabel = if (br == 0) "Original" else "$br kbps"
    // Both dropdowns share the right side, 8dp apart; only a genuinely cramped
    // row stacks them to the next line, END-aligned.
    val pairWidth = com.feishin.mobile.core.common.RowLayout.estimateButtonWidthDp(brLabel, hasIcon = true, 1f) +
        com.feishin.mobile.core.common.RowLayout.CONTROL_GAP_DP +
        com.feishin.mobile.core.common.RowLayout.estimateButtonWidthDp(fmt, hasIcon = true, 1f)
    SettingControlRow(
        label = label,
        controlWidthDp = pairWidth,
        // Uncapped: a two-button pair cannot squeeze (the 45% cap crushed the
        // second button's text to zero width). Measured whole; if the pair
        // plus label cannot share the row it stacks to the next line instead.
        cappedControl = false,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VusicDropdownSelector(
                value = br,
                options = QualityPolicy.BITRATES,
                onSelect = { onSet(it, fmt) },
                labelProvider = { if (it == 0) "Original" else "$it kbps" },
            )
            Spacer(Modifier.size(com.feishin.mobile.core.common.RowLayout.CONTROL_GAP_DP.dp))
            VusicDropdownSelector(
                value = fmt,
                options = QualityPolicy.FORMATS,
                onSelect = { onSet(br, it) },
            )
        }
    }
}

// ---------- Connectivity ----------

@Composable
private fun ConnectivityScreen(vm: SettingsViewModel) {
    val settings by vm.store.flow.collectAsStateWithLifecycle(initialValue = null)
    val online by vm.isOnline.collectAsStateWithLifecycle()
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
    // Both switches default to OFF, which is the behaviour every install has
    // today: no bulk transfer ever spends mobile data unless the user says so.
    SettingsSection("MOBILE DATA")
    SettingsCard {
        SwitchRow(
            "Download over mobile data", !s.downloadWifiOnly, vm::setDownloadOverMobileData,
            description = "May use mobile data; extra data charges may apply.",
            showDivider = true,
        )
        SwitchRow(
            "Auto-cache over mobile data", !s.cacheWifiOnly, vm::setCacheOverMobileData,
            description = "May use mobile data; extra data charges may apply.",
        )
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
    val settings by vm.store.flow.collectAsStateWithLifecycle(initialValue = null)
    val storage by vm.storageStats.collectAsStateWithLifecycle()
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
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = com.feishin.mobile.ui.theme.VusicSpacing.RowMinHeight)
                    .padding(vertical = com.feishin.mobile.ui.theme.VusicSpacing.RowVertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Simultaneous downloads", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        st?.let { "${it.activeDownloads} active · ${it.queuedDownloads} queued" } ?: "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.feishin.mobile.ui.theme.VusicColors.SecondaryText,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { vm.setMaxConcurrentDownloads(s.maxConcurrentDownloads - 1) },
                        enabled = s.maxConcurrentDownloads > 1,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease simultaneous downloads",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "${s.maxConcurrentDownloads}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = { vm.setMaxConcurrentDownloads(s.maxConcurrentDownloads + 1) },
                        enabled = s.maxConcurrentDownloads < 5,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase simultaneous downloads",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            SettingsDivider()
        }
        ActionRow("Browse downloaded music", buttonLabel = "Browse", onClick = onOpenLibraryDownloads, showDivider = true)
        ActionRow("Download queue", buttonLabel = "Open", onClick = onOpenDownloads)
    }
    SettingsSection("SONG CACHE")
    SettingsCard {
        // The rail is the whole supported range, up to 100 GB, NOT trimmed to
        // the device's size: the user picks a limit, and the warning below
        // says plainly when that limit is bigger than the phone can hold.
        // Trimming it to the device is what kept the old 5 GB plateau in
        // place, and it would hide the very choice the warning is about.
        val cacheSteps = com.feishin.mobile.core.common.CacheSizePolicy.streamStepBytes()
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
            // Free-space aware, same rule as the download cap: the choice stays
            // allowed, but say plainly when it is bigger than the phone.
            warning = if (st != null) {
                com.feishin.mobile.core.common.StorageLimits.cacheSpaceWarning(
                    limitBytes = cacheSteps[cacheIndex],
                    usedBytes = st.streamCacheBytes,
                    freeBytes = st.freeBytes,
                    human = { com.feishin.mobile.core.common.CacheSizePolicy.human(it) },
                )
            } else null,
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
        val imageSteps = com.feishin.mobile.core.common.StorageLimits.cacheSteps(
            com.feishin.mobile.core.common.CacheSizePolicy.imageStepBytes(), deviceMax,
        )
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
        val restartPending by vm.imageCacheRestartPending.collectAsStateWithLifecycle()
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var showPermissionRationale by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showAppSettingsDialog by androidx.compose.runtime.remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.setShowDeviceFiles(true)
        } else {
            vm.setShowDeviceFiles(false)
            val activity = context as? android.app.Activity
            if (activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                showAppSettingsDialog = true
            } else {
                showPermissionRationale = true
            }
        }
    }

    SettingsSection("DEVICE FILES")
    SettingsCard {
        SwitchRow(
            "Show device files",
            s.showDeviceFiles,
            onSet = { enable ->
                if (enable) {
                    val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, permission,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasPerm) {
                        vm.setShowDeviceFiles(true)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                } else {
                    vm.setShowDeviceFiles(false)
                }
            },
            description = "Browse audio files stored on this device.",
            showDivider = true,
        )
        val durationLabels = DeviceFilesFilter.DURATION_CHOICES.map { DeviceFilesFilter.durationLabel(it) }
        val currentDurationLabel = DeviceFilesFilter.durationLabel(s.deviceFilesMinDurationSec)
        ChoiceRow(
            "Minimum track length",
            currentDurationLabel,
            durationLabels,
            onSelect = { label ->
                val idx = durationLabels.indexOf(label)
                if (idx >= 0) {
                    vm.setDeviceFilesMinDurationSec(DeviceFilesFilter.DURATION_CHOICES[idx])
                }
            },
            segmented = true,
            description = "Filter out short audio clips and ringtones.",
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Audio permission needed") },
            text = { Text("Vusic requires permission to read audio files so you can browse and play device music.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionLauncher.launch(permission)
                }) { Text("Grant permission") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) { Text("Cancel") }
            },
        )
    }

    if (showAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAppSettingsDialog = false },
            title = { Text("Permission permanently denied") },
            text = { Text("Audio permission was previously denied. Please enable it in system app settings to show device files.") },
            confirmButton = {
                TextButton(onClick = {
                    showAppSettingsDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("Open app settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAppSettingsDialog = false }) { Text("Cancel") }
            },
        )
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

@Composable
private fun LyricsSourcesDialog(
    order: List<String>,
    enabled: Set<String>,
    onReorder: (List<String>) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics sources") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Reorder priority and toggle sources. Sequential lookups fall back to the next enabled source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VusicColors.SecondaryText,
                )
                order.forEachIndexed { index, id ->
                    val isEnabled = id in enabled
                    val title = when (id) {
                        "LRCLIB" -> "LRCLIB"
                        "BETTER_LYRICS" -> "BetterLyrics"
                        "PAXSENIX" -> "Paxsenix"
                        "KUGOU" -> "KuGou"
                        "LOCAL_DATA" -> "Local data"
                        else -> id
                    }
                    val subtitle = when (id) {
                        "LRCLIB" -> "lrclib.net, line & word sync"
                        "BETTER_LYRICS" -> "public TTML API, word sync"
                        "PAXSENIX" -> "Disabled: Public endpoint is defunct and requires Apple Music track IDs"
                        "KUGOU" -> "Unofficial, may stop working"
                        "LOCAL_DATA" -> "Server lyrics & device file tags"
                        else -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VusicColors.SurfaceHigh, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VusicColors.SecondaryText)
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = order.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    onReorder(mutable)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    val mutable = order.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    onReorder(mutable)
                                }
                            },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(20.dp))
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { onToggle(id, it) },
                            enabled = id != "PAXSENIX",
                            colors = VusicControlDefaults.switchColors(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onReset) { Text("Reset to default") }
        },
    )
}
