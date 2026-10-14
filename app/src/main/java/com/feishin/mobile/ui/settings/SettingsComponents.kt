package com.feishin.mobile.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.VusicColors

/** Small uppercase group label used inside every sub-screen. */
@Composable
fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = VusicColors.SecondaryText,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Rounded dark card holding a group of rows. */
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VusicColors.SurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
fun SwitchRow(
    label: String,
    value: Boolean,
    onSet: (Boolean) -> Unit,
    description: String? = null,
    warning: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(value, { onSet(it) })
        }
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        }
        if (warning != null && value) {
            Text(warning, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D))
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    description: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { open = true }) {
                    Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o, modifier = Modifier.weight(1f))
                                    if (o == value) {
                                        Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            onClick = { onSelect(o); open = false },
                        )
                    }
                }
            }
        }
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value, style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ActionRow(
    label: String,
    description: String? = null,
    danger: Boolean = false,
    confirmTitle: String? = null,
    confirmBody: String? = null,
    buttonLabel: String = "Open",
    onClick: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (danger) Color(0xFFFF8A80) else Color.White,
                )
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
                }
            }
            if (danger) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { if (confirmTitle != null) confirm = true else onClick() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF8A80)),
                ) {
                    Text(buttonLabel, color = Color(0xFFFF8A80))
                }
            } else {
                OutlinedButton(onClick = onClick) { Text(buttonLabel) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(confirmTitle.orEmpty()) },
            text = { if (confirmBody != null) Text(confirmBody) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onClick() }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Stacked usage bar: each segment is (fraction, color). */
@Composable
fun UsageBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(VusicColors.Surface),
    ) {
        segments.filter { it.first > 0f }.forEach { (fraction, color) ->
            Box(Modifier.weight(fraction).height(10.dp).background(color))
        }
    }
}

@Composable
fun UsageLegend(entries: List<Triple<String, String, Color>>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.size(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
            }
        }
    }
}

/** Home card: icon, title, one-line state summary, chevron. */
@Composable
fun SettingsHomeCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VusicColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = VusicColors.SecondaryText)
    }
}
