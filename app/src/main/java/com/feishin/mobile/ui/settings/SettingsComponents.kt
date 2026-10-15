package com.feishin.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing

/** Small uppercase group label used inside every sub-screen. */
@Composable
fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = VusicColors.SecondaryText,
        modifier = Modifier.padding(
            start = 4.dp,
            top = VusicSpacing.SectionTop,
            bottom = VusicSpacing.SectionBottom,
        ),
    )
}

/**
 * Rounded dark card holding a group of rows. Rows are separated by a hairline
 * divider inset to the text start, and the card itself carries 16dp padding.
 */
@Composable
fun SettingsCard(content: @Composable SettingsCardScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VusicColors.SurfaceHigh)
            .padding(horizontal = VusicSpacing.CardPadding)
            .padding(vertical = 4.dp),
    ) {
        SettingsCardScope.content()
    }
}

/** Scope so a card can draw dividers between its own rows. */
object SettingsCardScope

/** Divider inset to the text start, for use between rows in a card. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 0.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.07f),
    )
}

@Composable
private fun RowText(
    label: String,
    description: String?,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.White,
    warning: String? = null,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (description != null) {
            Spacer(Modifier.height(VusicSpacing.TitleToDescription))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                // Descriptions never run under the control: capped at 3 lines.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (warning != null) {
            Spacer(Modifier.height(VusicSpacing.TitleToDescription))
            Text(warning, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), maxLines = 3)
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    value: Boolean,
    onSet: (Boolean) -> Unit,
    description: String? = null,
    warning: String? = null,
    showDivider: Boolean = false,
) {
    SettingControlRow(
        label = label,
        description = description,
        warning = warning,
        controlWidthDp = com.feishin.mobile.core.common.RowLayout.SWITCH_WIDTH_DP,
        showDivider = showDivider,
    ) {
        Switch(
            checked = value,
            onCheckedChange = onSet,
            colors = VusicControlDefaults.switchColors(),
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}

/**
 * Choice between a few options. The control always sits on the right of the
 * label's line: a segmented single-row selector when [segmented] (Grid/List,
 * Off/Track/Album, Theme — equal-height cells, selected filled white), a
 * dropdown button otherwise (sorts, timeouts, mix length). If the control
 * cannot fit beside the text it stacks to the next line, END-aligned.
 */
@Composable
fun ChoiceRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    description: String? = null,
    showDivider: Boolean = false,
    segmented: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    if (segmented) {
        SettingControlRow(
            label = label,
            description = description,
            controlWidthDp = com.feishin.mobile.core.common.RowLayout.estimateSegmentedWidthDp(options, 1f),
            showDivider = showDivider,
            cappedControl = false,
        ) {
            SegmentedControl(options = options, selected = value, onSelect = onSelect)
        }
        return
    }
    SettingControlRow(
        label = label,
        description = description,
        controlWidthDp = com.feishin.mobile.core.common.RowLayout.estimateButtonWidthDp(value, hasIcon = true, 1f),
        showDivider = showDivider,
    ) {
        Box {
            SettingsActionButton(
                label = value,
                onClick = { open = true },
                leadingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown, "Open options",
                        tint = Color.White, modifier = Modifier.size(20.dp),
                    )
                },
            )
            DropdownMenu(open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o, modifier = Modifier.weight(1f))
                                if (o == value) {
                                    Icon(
                                        Icons.Default.Check, "Selected",
                                        tint = Color.White, modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(o); open = false },
                    )
                }
            }
        }
    }
}

/**
 * Discrete-step slider whose thumb rides the filled track (Material3 handles the
 * attachment; discrete steps stop the ticks from overflowing) with the value
 * label on the right of the title and helper text 8dp below.
 */
@Composable
fun SliderRow(
    label: String,
    valueLabel: String,
    index: Int,
    steps: List<String>,
    onIndex: (Int) -> Unit,
    description: String? = null,
    warning: String? = null,
    showDivider: Boolean = false,
) {
    val maxIndex = (steps.size - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = VusicSpacing.RowVertical, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(VusicSpacing.ControlGap))
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Slider(
                value = index.toFloat().coerceIn(0f, maxIndex.toFloat()),
                onValueChange = { onIndex(it.toInt().coerceIn(0, maxIndex)) },
                valueRange = 0f..maxIndex.toFloat(),
                steps = (maxIndex - 1).coerceAtLeast(0),
                colors = VusicControlDefaults.sliderColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            if (description != null) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText, maxLines = 3)
            }
            if (warning != null) {
                Spacer(Modifier.height(8.dp))
                Text(warning, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), maxLines = 3)
            }
            Spacer(Modifier.height(VusicSpacing.RowVertical))
        }
        if (showDivider) SettingsDivider()
    }
}

@Composable
fun InfoRow(label: String, value: String, showDivider: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = VusicSpacing.RowMinHeight)
                .padding(vertical = VusicSpacing.RowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(VusicSpacing.ControlGap))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = VusicColors.SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        if (showDivider) SettingsDivider()
    }
}

/**
 * A row with an action button. The button sits on the right of the label's
 * line (vertically centred), with at least 16dp between text and control; the
 * text wraps to at most 3 lines and never runs under the button. Only when the
 * text would be squeezed below ~40% of the row does the button go on the next
 * line, aligned END. Destructive actions keep the red style plus a confirm.
 */@Composable
fun ActionRow(
    label: String,
    description: String? = null,
    danger: Boolean = false,
    confirmTitle: String? = null,
    confirmBody: String? = null,
    buttonLabel: String = "Open",
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    SettingControlRow(
        label = label,
        description = description,
        danger = danger,
        controlWidthDp = com.feishin.mobile.core.common.RowLayout.estimateButtonWidthDp(
            buttonLabel, hasIcon = false, 1f,
        ),
        showDivider = showDivider,
    ) {
        ActionButton(danger, enabled, buttonLabel) { if (confirmTitle != null) confirm = true else onClick() }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(confirmTitle.orEmpty()) },
            text = { if (confirmBody != null) Text(confirmBody) },
            confirmButton = { TextButton(onClick = { confirm = false; onClick() }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionButton(danger: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    SettingsActionButton(label = label, onClick = onClick, danger = danger, enabled = enabled)
}

/**
 * Usage island: a 12dp rounded bar whose segments are proportional to bytes
 * (including free space), a total label, and one legend row per item.
 * Segment widths animate when the values change.
 */
@Composable
fun UsageCard(
    imagesBytes: Long,
    downloadsBytes: Long,
    songCacheBytes: Long,
    freeBytes: Long,
    human: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val math = remember(imagesBytes, downloadsBytes, songCacheBytes, freeBytes) {
        com.feishin.mobile.core.common.UsageBarMath.compute(
            vusicSegments = listOf(
                com.feishin.mobile.core.common.UsageBarMath.Segment("Images", imagesBytes, 0),
                com.feishin.mobile.core.common.UsageBarMath.Segment("Music downloads", downloadsBytes, 1),
                com.feishin.mobile.core.common.UsageBarMath.Segment("Song cache", songCacheBytes, 2),
            ),
            freeBytes = freeBytes,
        )
    }
    val colors = listOf(Color(0xFF8AB4F8), Color(0xFF7DD8A8), Color(0xFFFFC66E))
    // Free space reuses the secondary-text token at low alpha: it must read as
    // "empty" against both the bar track and the card, and SurfaceHigh (the
    // card background) is invisible for that job. Revisited in the color pass.
    val freeColor = VusicColors.SecondaryText.copy(alpha = 0.35f)
    val barColors = colors + freeColor

    // 16dp inner padding on all sides (the card already supplies horizontal 16
    // and 4 vertical, so 12 more vertical lands exactly on 16).
    Column(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UsageBar(fractions = math.fractions, colors = barColors)
        Text(
            com.feishin.mobile.core.common.UsageBarMath.totalLabel(math.usedBytes, math.freeBytes, human),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UsageLegendRow("Images", human(imagesBytes), colors[0])
            UsageLegendRow("Music downloads", human(downloadsBytes), colors[1])
            UsageLegendRow("Song cache", human(songCacheBytes), colors[2])
            UsageLegendRow("Free space", human(freeBytes), freeColor)
        }
    }
}

/**
 * Stacked usage bar. [fractions] must sum to 1 (see UsageBarMath); widths are
 * animated so a resize or a download updates smoothly.
 */
@Composable
fun UsageBar(
    fractions: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(VusicColors.Surface),
    ) {
        fractions.forEachIndexed { i, f ->
            if (f > 0f) {
                val animated by animateFloatAsState(
                    targetValue = f,
                    animationSpec = Motion.layoutSpring(),
                    label = "usage-$i",
                )
                Box(
                    Modifier
                        .weight(animated.coerceAtLeast(0.0001f))
                        .height(12.dp)
                        .background(colors.getOrElse(i) { VusicColors.SurfaceHigh }),
                )
            }
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

/**
 * Home card: icon, title, one-line state summary, chevron.
 *
 * [index] drives a short stagger on first appearance only (a remember-ed flag,
 * so re-composition never replays it) plus press feedback, which is what makes
 * the Settings home feel like it has motion without delaying anything.
 */
@Composable
fun SettingsHomeCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    index: Int = 0,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (appeared) Motion.Normal else 0,
            delayMillis = (index * Motion.Stagger).coerceAtMost(Motion.Slow),
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "card-enter",
    )
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = Motion.scale(Motion.Fast),
        label = "card-press",
    )
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = VusicSpacing.CardGap / 4)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 16.dp.toPx()
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(VusicColors.SurfaceHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .defaultMinSize(minHeight = VusicSpacing.RowMinHeight)
            .padding(VusicSpacing.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(VusicSpacing.ControlGap))
        Text("›", style = MaterialTheme.typography.headlineSmall, color = VusicColors.SecondaryText)
    }
}

/** Home card: icon, title, one-line state summary, chevron. */
