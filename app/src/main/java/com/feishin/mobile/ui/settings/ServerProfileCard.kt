package com.feishin.mobile.ui.settings

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.ProfileHealth
import com.feishin.mobile.ui.theme.LocalVusicDark
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing
import java.net.URI

/** Immutable wrapper around a list of strings so Compose treats it as stable. */
@Immutable
internal data class ImmutableStrings(val items: List<String> = emptyList())

/** Immutable UI model for [ServerProfileCard] ensuring 0 unstable parameters. */
@Immutable
internal data class ServerProfileUi(
    val id: String,
    val name: String,
    val type: BackendType,
    val url: String,
    val host: String,
    val username: String,
    val enabled: Boolean,
    val extensions: ImmutableStrings,
    val capabilities: BackendCapabilities,
    val productLabel: String?,
    val apiVersion: String?,
    val driveRootFolderName: String? = null,
    val indexedTrackCount: Int = 0,
    val quotaLimited: Boolean = false,
)

internal enum class ProfileStatusTone {
    HEALTHY,
    TESTING,
    ERROR,
    OFFLINE_OR_DISABLED,
}

@Immutable
internal data class ProfileStatusUi(
    val tone: ProfileStatusTone,
    val label: String,
    val isSessionExpired: Boolean = false,
)

@Immutable
internal data class ProfileScanUi(
    val isScanning: Boolean,
    val sublabel: String,
    val canScan: Boolean,
    val lastScanLabel: String? = null,
)

internal fun backendLabel(type: BackendType): String = when (type) {
    BackendType.NAVIDROME -> "Navidrome"
    BackendType.OPENSUBSONIC -> "OpenSubsonic"
    BackendType.JELLYFIN -> "Jellyfin"
    BackendType.GOOGLE_DRIVE -> "Google Drive"
}

internal fun profileUsername(profile: ServerProfile): String = when (val c = profile.credentials) {
    is ProfileCredentials.Subsonic -> c.username
    is ProfileCredentials.Jellyfin -> c.username
    is ProfileCredentials.GoogleDrive -> c.accountEmail
}

internal fun extractHost(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val host = parsed?.host
    return when {
        !host.isNullOrBlank() -> if (parsed.port > 0) "$host:${parsed.port}" else host
        else -> trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .ifBlank { trimmed }
    }
}

internal fun ServerProfile.toServerProfileUi(
    serverInfo: ServerInfo? = null,
    indexedTrackCount: Int = 0,
    quotaLimited: Boolean = false,
): ServerProfileUi = ServerProfileUi(
    id = id,
    name = name,
    type = type,
    url = url,
    host = extractHost(url),
    username = profileUsername(this),
    enabled = enabled,
    extensions = ImmutableStrings(openSubsonicExtensions.rawNames),
    capabilities = BackendCapabilities.forType(type),
    productLabel = serverInfo?.product?.takeIf { it.isNotBlank() },
    apiVersion = serverInfo?.apiVersion?.takeIf { it.isNotBlank() },
    driveRootFolderName = if (type == BackendType.GOOGLE_DRIVE) {
        driveRootFolderName?.takeIf { it.isNotBlank() }
            ?: if (driveRootFolderId.isNullOrBlank()) "All audio files" else null
    } else {
        driveRootFolderName
    },
    indexedTrackCount = indexedTrackCount,
    quotaLimited = quotaLimited,
)

internal fun resolveProfileStatus(
    enabled: Boolean,
    online: Boolean,
    testState: TestState,
    health: ProfileHealth?,
    authStatus: ProfileAuthStatus,
    latencyMs: Long? = null,
    backendType: BackendType = BackendType.NAVIDROME,
    scanState: ScanUiState? = null,
    indexedTrackCount: Int = 0,
    quotaLimited: Boolean = false,
): ProfileStatusUi = when {
    testState is TestState.Testing -> ProfileStatusUi(
        tone = ProfileStatusTone.TESTING,
        label = "Testing…",
    )
    !enabled -> ProfileStatusUi(
        tone = ProfileStatusTone.OFFLINE_OR_DISABLED,
        label = "Disabled",
    )
    authStatus == ProfileAuthStatus.SESSION_EXPIRED -> ProfileStatusUi(
        tone = ProfileStatusTone.ERROR,
        label = "Session expired",
        isSessionExpired = true,
    )
    backendType == BackendType.GOOGLE_DRIVE && scanState is ScanUiState.Scanning -> {
        val total = scanState.totalCount
        val scanLabel = when {
            total != null -> {
                val pct = if (total > 0) {
                    ((scanState.count.coerceIn(0, total) * 100L) / total).toInt()
                } else {
                    100
                }
                "Indexing… $pct% (${formatScanCount(scanState.count)}/${formatScanCount(total)})"
            }
            scanState.count > 0 -> "Discovering… ${formatScanCount(scanState.count)} files"
            else -> "Discovering…"
        }
        ProfileStatusUi(
            tone = ProfileStatusTone.TESTING,
            label = scanLabel,
        )
    }
    !online -> ProfileStatusUi(
        tone = ProfileStatusTone.OFFLINE_OR_DISABLED,
        label = "Offline",
    )
    backendType == BackendType.GOOGLE_DRIVE &&
        (quotaLimited || (scanState is ScanUiState.Finished && scanState.quotaLimited)) -> ProfileStatusUi(
        tone = ProfileStatusTone.ERROR,
        label = "Quota limited",
    )
    testState is TestState.Failed || health?.degraded == true ||
        (backendType == BackendType.GOOGLE_DRIVE && scanState is ScanUiState.Error) -> ProfileStatusUi(
        tone = ProfileStatusTone.ERROR,
        label = if (backendType == BackendType.GOOGLE_DRIVE && scanState is ScanUiState.Error) {
            scanState.message
        } else {
            "Unreachable"
        },
    )
    backendType == BackendType.GOOGLE_DRIVE -> {
        val noun = if (indexedTrackCount == 1) "track" else "tracks"
        ProfileStatusUi(
            tone = ProfileStatusTone.HEALTHY,
            label = "Indexed · ${formatScanCount(indexedTrackCount)} $noun",
        )
    }
    else -> ProfileStatusUi(
        tone = ProfileStatusTone.HEALTHY,
        label = if (latencyMs != null) "Healthy · $latencyMs ms" else "Healthy",
    )
}

internal fun resolveProfileScanUi(
    capabilities: BackendCapabilities,
    enabled: Boolean,
    online: Boolean,
    scanState: ScanUiState,
    backendType: BackendType = BackendType.NAVIDROME,
): ProfileScanUi {
    val isScanning = scanState is ScanUiState.Scanning
    val canScan = enabled && online && capabilities.supportsLibraryScan && !isScanning
    val formattedLastScan = formatLastScan(scanState.lastScan)
    val sublabel = when {
        !capabilities.supportsLibraryScan -> "Not supported"
        !enabled -> "Disabled"
        !online -> "Unavailable offline"
        scanState is ScanUiState.Scanning -> {
            val total = scanState.totalCount
            when {
                total != null -> {
                    val pct = if (total > 0) {
                        ((scanState.count.coerceIn(0, total) * 100L) / total).toInt()
                    } else {
                        100
                    }
                    "Indexing… $pct% (${formatScanCount(scanState.count)}/${formatScanCount(total)})"
                }
                scanState.twoStage -> if (scanState.count > 0) {
                    "Discovering… ${formatScanCount(scanState.count)} files"
                } else {
                    "Discovering…"
                }
                scanState.count > 0 -> "Scanning… ${formatScanCount(scanState.count)}"
                else -> "Scanning…"
            }
        }
        scanState is ScanUiState.Finished -> {
            val base = if (formattedLastScan != null) "Scanned $formattedLastScan" else "Scan finished"
            if (scanState.quotaLimited) "$base (rate limited)" else base
        }
        scanState is ScanUiState.Error -> scanState.message
        else -> {
            if (formattedLastScan != null) {
                "Scanned $formattedLastScan"
            } else if (backendType == BackendType.GOOGLE_DRIVE) {
                "Ready to index"
            } else {
                "Ready to scan"
            }
        }
    }
    return ProfileScanUi(
        isScanning = isScanning,
        sublabel = sublabel,
        canScan = canScan,
        lastScanLabel = formattedLastScan,
    )
}

private fun formatProtocolLabel(ui: ServerProfileUi): String = when (ui.type) {
    BackendType.OPENSUBSONIC ->
        if (ui.apiVersion != null) "OpenSubsonic · API ${ui.apiVersion}" else "OpenSubsonic"
    BackendType.NAVIDROME ->
        if (ui.apiVersion != null) "Navidrome · API ${ui.apiVersion}" else "Navidrome"
    BackendType.JELLYFIN ->
        if (ui.apiVersion != null) "Jellyfin ${ui.apiVersion}" else "Jellyfin"
    BackendType.GOOGLE_DRIVE -> "Google Drive v3"
}

private fun capabilityChips(capabilities: BackendCapabilities): List<String> = buildList {
    if (capabilities.supportsServerPlaylists) add("Playlists")
    if (capabilities.supportsScrobbling) add("Scrobbling")
    if (capabilities.supportsServerLyrics) add("Lyrics")
    if (capabilities.supportsTranscoding) add("Transcoding")
    if (capabilities.supportsRadioMix) add("Radio")
}

private fun driveCapabilityChips(capabilities: BackendCapabilities): List<String> = buildList {
    add(if (capabilities.supportsServerPlaylists) "Server playlists" else "Local playlists")
    add(if (capabilities.supportsServerFavorites) "Server favorites" else "Local favorites")
    add(if (capabilities.supportsScrobbling) "Scrobbling" else "No scrobbling")
    add(if (capabilities.supportsTranscoding) "Transcoding" else "Raw stream")
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Motion.durationScale == 0f) {
            true
        } else {
            val scale = runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
            scale == 0f
        }
    }
}

/**
 * Collapsible server profile card for Settings > Server & Account.
 * All state parameters are @Immutable or primitives so Compose skips unchanged cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerProfileCard(
    ui: ServerProfileUi,
    rank: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    status: ProfileStatusUi,
    isTesting: Boolean,
    scanUi: ProfileScanUi,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTestConnection: () -> Unit,
    onScan: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onRequestRemove: () -> Unit,
    onReconnectDrive: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dark = LocalVusicDark.current
    val okColor = if (dark) Color(0xFF81C784) else Color(0xFF1B5E20)
    val reduceMotion = rememberReduceMotion()
    val cardShape = RoundedCornerShape(12.dp)
    val statusColor = when (status.tone) {
        ProfileStatusTone.HEALTHY -> okColor
        ProfileStatusTone.TESTING -> MaterialTheme.colorScheme.tertiary
        ProfileStatusTone.ERROR -> DangerRed
        ProfileStatusTone.OFFLINE_OR_DISABLED -> VusicColors.SecondaryText.copy(alpha = 0.5f)
    }
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        status.tone == ProfileStatusTone.ERROR -> DangerRed.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val borderWidth = if (isDragging) 1.5.dp else 1.dp

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "profile-chevron-${ui.id}",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 0.985f
                    scaleY = 0.985f
                    alpha = 0.65f
                }
            }
            .clip(cardShape)
            .background(VusicColors.SurfaceHigh)
            .border(BorderStroke(borderWidth, borderColor), cardShape),
    ) {
        CollapsedProfileHeader(
            ui = ui,
            rank = rank,
            expanded = expanded,
            status = status,
            statusColor = statusColor,
            chevronRotation = { chevronRotation },
            onToggleExpanded = onToggleExpanded,
            onToggleEnabled = onToggleEnabled,
        )

        val enterTransition: EnterTransition = if (reduceMotion) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = spring<IntSize>(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(tween(160))
        }
        val exitTransition: ExitTransition = if (reduceMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = spring<IntSize>(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeOut(tween(120))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            ExpandedProfileBody(
                ui = ui,
                status = status,
                statusColor = statusColor,
                isTesting = isTesting,
                scanUi = scanUi,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                dragHandleModifier = dragHandleModifier,
                onCopyEndpoint = onCopyEndpoint,
                onTestConnection = onTestConnection,
                onScan = onScan,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onEdit = onEdit,
                onRequestRemove = onRequestRemove,
                onReconnectDrive = onReconnectDrive,
            )
        }
    }
}

@Composable
private fun CollapsedProfileHeader(
    ui: ServerProfileUi,
    rank: Int,
    expanded: Boolean,
    status: ProfileStatusUi,
    statusColor: Color,
    chevronRotation: () -> Float,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Collapse ${ui.name}" else "Expand ${ui.name}",
                onClick = onToggleExpanded,
            )
            .padding(horizontal = VusicSpacing.CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = ui.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = backendLabel(ui.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = VusicColors.SecondaryText,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            val sublineSource = if (ui.type == BackendType.GOOGLE_DRIVE) {
                ui.username.takeIf { it.isNotBlank() }
            } else {
                ui.host.takeIf { it.isNotBlank() }
            }
            val subline = if (sublineSource != null) {
                "$sublineSource · ${status.label}"
            } else {
                status.label
            }
            Text(
                text = subline,
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = ui.enabled,
            onCheckedChange = onToggleEnabled,
            colors = VusicControlDefaults.switchColors(),
            modifier = Modifier.semantics {
                contentDescription = "Enable ${ui.name}"
            },
        )

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = VusicColors.SecondaryText,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevronRotation() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedProfileBody(
    ui: ServerProfileUi,
    status: ProfileStatusUi,
    statusColor: Color,
    isTesting: Boolean,
    scanUi: ProfileScanUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    dragHandleModifier: Modifier,
    onCopyEndpoint: () -> Unit,
    onTestConnection: () -> Unit,
    onScan: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRequestRemove: () -> Unit,
    onReconnectDrive: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val panelShape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = VusicSpacing.CardPadding,
                end = VusicSpacing.CardPadding,
                bottom = VusicSpacing.CardPadding,
            ),
    ) {
        SettingsDivider()
        Spacer(Modifier.height(12.dp))

        // Inset details panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    panelShape,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.type != BackendType.GOOGLE_DRIVE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Endpoint",
                            style = MaterialTheme.typography.labelSmall,
                            color = VusicColors.SecondaryText,
                        )
                        Text(
                            text = ui.url,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(ui.url))
                            onCopyEndpoint()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy endpoint URL",
                            tint = VusicColors.SecondaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            DetailKeyValueRow(
                label = "Account",
                value = ui.username.ifBlank { "—" },
            )
            if (ui.type == BackendType.GOOGLE_DRIVE) {
                DetailKeyValueRow(
                    label = "Music folder",
                    value = ui.driveRootFolderName?.ifBlank { "All audio files" } ?: "All audio files",
                )
            }
            DetailKeyValueRow(
                label = "Protocol",
                value = formatProtocolLabel(ui),
            )
            if (ui.type == BackendType.GOOGLE_DRIVE) {
                DetailKeyValueRow(
                    label = "Last index",
                    value = scanUi.lastScanLabel ?: "Never",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = VusicColors.SecondaryText,
                )
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Extensions (OpenSubsonic) or Capabilities (Jellyfin / Google Drive)
        val chipLabel: String?
        val chips: List<String>
        when {
            ui.type == BackendType.OPENSUBSONIC && ui.extensions.items.isNotEmpty() -> {
                chipLabel = "Extensions"
                chips = ui.extensions.items
            }
            ui.type == BackendType.JELLYFIN -> {
                chipLabel = "Capabilities"
                chips = capabilityChips(ui.capabilities)
            }
            ui.type == BackendType.GOOGLE_DRIVE -> {
                chipLabel = "Capabilities"
                chips = driveCapabilityChips(ui.capabilities)
            }
            else -> {
                chipLabel = null
                chips = emptyList()
            }
        }
        if (chipLabel != null && chips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = chipLabel,
                style = MaterialTheme.typography.labelSmall,
                color = VusicColors.SecondaryText,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (ui.type == BackendType.GOOGLE_DRIVE &&
            (status.isSessionExpired || status.label == "Session expired")
        ) {
            Button(
                onClick = onReconnectDrive,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VusicColors.Accent,
                    contentColor = VusicColors.OnAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Text(
                    text = "Reconnect Google Drive",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Primary action grid: Test connection & Scan library / Re-index
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isTesting,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Testing…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Test connection",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            OutlinedButton(
                onClick = onScan,
                enabled = scanUi.canScan,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (scanUi.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = if (ui.type == BackendType.GOOGLE_DRIVE) "Re-index" else "Scan library",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = scanUi.sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = VusicColors.SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Secondary toolbar: reorder (▲ ▼ ≡) on left, Edit / Remove on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move ${ui.name} up",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move ${ui.name} down",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder ${ui.name}",
                        tint = VusicColors.SecondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Edit", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onRequestRemove,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.78f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Remove", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DetailKeyValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VusicColors.SecondaryText,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
