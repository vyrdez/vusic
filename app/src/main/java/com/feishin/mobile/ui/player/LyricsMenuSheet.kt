package com.feishin.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsCandidate
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.ui.theme.VusicColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenuSheet(
    track: Track,
    lyricsUi: LyricsUi,
    lyricsVm: LyricsViewModel,
    settings: SettingsStore.Settings,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
    onTrackDetails: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }

    val currentLyrics = (lyricsUi as? LyricsUi.Synced)?.lyrics
    val songOffset = currentLyrics?.offsetMs ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VusicColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header row: 4 action buttons (Edit, Refetch, Search, Copy)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    onClick = {
                        showEditDialog = true
                    },
                )
                ActionButton(
                    icon = Icons.Default.Refresh,
                    label = "Refetch",
                    onClick = {
                        lyricsVm.searchAgain(track)
                        onShowMessage("Refetching lyrics…")
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                )
                ActionButton(
                    icon = Icons.Default.Search,
                    label = "Search",
                    onClick = {
                        showSearchDialog = true
                    },
                )
                ActionButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    onClick = {
                        val textToCopy = formatLyricsForCopy(lyricsUi)
                        if (textToCopy.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            onShowMessage("Lyrics copied to clipboard")
                        } else {
                            onShowMessage("No lyrics to copy")
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Option 1: Respect agent positioning
            MenuSwitchRow(
                title = "Respect agent positioning",
                description = "Align lead & background vocalists on opposite sides (TTML)",
                checked = settings.lyricsRespectAgentPositioning,
                onCheckedChange = { scope.launch { lyricsVm.settings.setLyricsRespectAgentPositioning(it) } },
            )

            // Option 2: Show interval indicator
            MenuSwitchRow(
                title = "Show interval indicator",
                description = "Show note indicator during instrumental gaps between lines",
                checked = settings.lyricsShowIntervalIndicator,
                onCheckedChange = { scope.launch { lyricsVm.settings.setLyricsShowIntervalIndicator(it) } },
            )

            // Option 3: Lyrics offset (per song)
            val offsetLabel = if (songOffset >= 0) "+$songOffset ms" else "$songOffset ms"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOffsetDialog = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lyrics offset", style = MaterialTheme.typography.bodyLarge)
                    Text("Adjust timing for this song only", style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                }
                Text(
                    text = offsetLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Option 4: Romanize current track
            val isRomanized = track.id in (lyricsVm.romanizedTracks.value)
            MenuSwitchRow(
                title = "Romanize current track",
                description = "Show romanized pronunciation below non-Latin lines",
                checked = isRomanized,
                onCheckedChange = { lyricsVm.toggleRomanize(track.id) },
            )

            // Option 5: Text size stepper
            val currentSizePct = parseTextSizeToPercent(settings.lyricsTextSize)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Text size", style = MaterialTheme.typography.bodyLarge)
                    Text("Adjust lyrics line text scaling", style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            val newPct = (currentSizePct - 10).coerceIn(80, 220)
                            scope.launch { lyricsVm.settings.setLyricsTextSize("${newPct}%") }
                        },
                        enabled = currentSizePct > 80,
                        modifier = Modifier
                            .size(36.dp)
                            .background(VusicColors.SurfaceHigh, CircleShape),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease text size", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "$currentSizePct%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = {
                            val newPct = (currentSizePct + 10).coerceIn(80, 220)
                            scope.launch { lyricsVm.settings.setLyricsTextSize("${newPct}%") }
                        },
                        enabled = currentSizePct < 220,
                        modifier = Modifier
                            .size(36.dp)
                            .background(VusicColors.SurfaceHigh, CircleShape),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase text size", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (onTrackDetails != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onTrackDetails()
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Track details & actions", style = MaterialTheme.typography.bodyLarge)
                        Text("View track details, album, artist, or playlist options", style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VusicColors.SecondaryText)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditDialog) {
        val initialText = formatLyricsForEdit(lyricsUi)
        EditLyricsDialog(
            initialText = initialText,
            onSave = { edited ->
                lyricsVm.saveEditedLyrics(track, edited)
                onShowMessage("Lyrics updated and cached")
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showSearchDialog) {
        SearchLyricsDialog(
            track = track,
            lyricsVm = lyricsVm,
            onApply = { candidate ->
                lyricsVm.applyCandidate(track, candidate)
                onShowMessage("Lyrics updated from candidate")
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false },
        )
    }

    if (showOffsetDialog) {
        LyricsOffsetDialog(
            currentOffsetMs = songOffset,
            onSave = { newOffset ->
                lyricsVm.setPerSongOffset(track, newOffset)
                showOffsetDialog = false
            },
            onDismiss = { showOffsetDialog = false },
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(VusicColors.SurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MenuSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditLyricsDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Lyrics") },
        text = {
            Column {
                Text(
                    "Edit or paste lyrics below (LRC timestamp format [mm:ss.xx] supported). Saved lyrics will be cached for this song.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VusicColors.SecondaryText,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    placeholder = { Text("Paste or type lyrics here…") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SearchLyricsDialog(
    track: Track,
    lyricsVm: LyricsViewModel,
    onApply: (LyricsCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var isSearching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<LyricsCandidate>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Lyrics") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            isSearching = true
                            lyricsVm.searchCandidates(title, artist) { results ->
                                candidates = results
                                isSearching = false
                            }
                        },
                        enabled = !isSearching && title.isNotBlank(),
                    ) {
                        Text(if (isSearching) "Searching…" else "Search candidates")
                    }
                }

                if (candidates.isNotEmpty()) {
                    Text(
                        "Candidates found:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    candidates.forEach { c ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(VusicColors.SurfaceHigh)
                                .clickable { onApply(c) }
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(c.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                if (c.isSynced) {
                                    Text("Synced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("${c.artist} · ${c.durationSec / 60}:${String.format("%02d", c.durationSec % 60)}", style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                } else if (!isSearching && candidates.isEmpty()) {
                    Text("No candidates yet. Tap Search to query providers.", style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun LyricsOffsetDialog(
    currentOffsetMs: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(currentOffsetMs.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics offset") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val formatted = if (offset >= 0) "+${offset.toInt()} ms" else "${offset.toInt()} ms"
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    text = "Adjust timing (-5000 ms to +5000 ms in 50 ms steps) for this track only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VusicColors.SecondaryText,
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = offset,
                    onValueChange = {
                        val stepped = (Math.round(it / 50f) * 50).toFloat()
                        offset = stepped.coerceIn(-5000f, 5000f)
                    },
                    valueRange = -5000f..5000f,
                    steps = 199, // (10000 / 50) - 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { offset = (offset - 50f).coerceIn(-5000f, 5000f) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-50ms")
                    }
                    TextButton(onClick = { offset = 0f }) {
                        Text("Reset to 0")
                    }
                    IconButton(
                        onClick = { offset = (offset + 50f).coerceIn(-5000f, 5000f) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+50ms")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(offset.toLong()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatLyricsForCopy(ui: LyricsUi): String = when (ui) {
    is LyricsUi.Synced -> formatToLrc(ui.lyrics)
    is LyricsUi.Plain -> ui.text
    else -> ""
}

private fun formatLyricsForEdit(ui: LyricsUi): String = when (ui) {
    is LyricsUi.Synced -> formatToLrc(ui.lyrics)
    is LyricsUi.Plain -> ui.text
    else -> ""
}

private fun formatToLrc(lyrics: Lyrics): String {
    if (!lyrics.isSynced) {
        return lyrics.lines.joinToString("\n") { it.text }.ifBlank { lyrics.plainText }
    }
    val sb = StringBuilder()
    if (lyrics.offsetMs != 0L) {
        sb.append("[offset:${lyrics.offsetMs}]\n")
    }
    for (line in lyrics.lines) {
        val totalSec = line.startMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val ms = (line.startMs % 1000) / 10
        val stamp = String.format("[%02d:%02d.%02d]", min, sec, ms)
        sb.append(stamp).append(" ").append(line.text).append("\n")
    }
    return sb.toString().trimEnd()
}

/** Parses legacy and percentage text size values into an Int percentage. */
fun parseTextSizeToPercent(raw: String): Int {
    val clean = raw.trim().removeSuffix("%")
    val num = clean.toIntOrNull()
    if (num != null) return num.coerceIn(80, 220)
    return when (raw.uppercase()) {
        "SMALL" -> 85
        "LARGE" -> 125
        else -> 100
    }
}
