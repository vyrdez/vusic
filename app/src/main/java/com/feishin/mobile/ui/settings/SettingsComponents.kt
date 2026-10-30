package com.feishin.mobile.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import com.feishin.mobile.ui.theme.LocalVusicDark
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
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RowText(
    label: String,
    description: String?,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
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
 * Unified, polished dropdown selector for Settings.
 *
 * Trigger Button: RoundedCornerShape(12.dp), subtle surface fill (VusicColors.SurfaceHigh),
 * animated 1.5.dp border highlighting with Theme primary when expanded.
 * Current selected label + trailing chevron (Icons.Default.KeyboardArrowDown) rotating
 * 0 -> 180 degrees (animateFloatAsState with tween(220, FastOutSlowInEasing)).
 *
 * Dropdown Popup: RoundedCornerShape(16.dp), 1.dp subtle outline, elevated shadow,
 * padded interior (vertical 6.dp, horizontal 6.dp). Deterministic placement from
 * the full measured height (below the anchor when it fits, otherwise above);
 * entrance/exit run purely on graphicsLayer (scale 0.92f -> 1.0f + alpha fade,
 * 180ms FastOutSlowInEasing) so layout bounds never change and the menu cannot
 * jump vertically.
 *
 * Menu items: RoundedCornerShape(10.dp) row, comfortable touch height (44.dp).
 * Currently selected option has soft tinted background pill (primary.copy(alpha = 0.16f) in Dark,
 * 0.12f in Light), checkmark (Icons.Default.Check, 18.dp) on the LEFT. Unselected items
 * have matching leading spacing so labels align.
 */
@Composable
fun <T> VusicDropdownSelector(
    value: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelProvider: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val dark = LocalVusicDark.current
    val density = LocalDensity.current

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.scale(Motion.Fast),
        label = "dropdown-press-scale",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> VusicControlColors.DisabledOutline
            expanded -> MaterialTheme.colorScheme.primary
            dark -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dropdown-border-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (expanded) 1.5.dp else 1.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dropdown-border-width",
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dropdown-chevron-rot",
    )

    val triggerShape = RoundedCornerShape(12.dp)
    val selectedLabel = labelProvider(value)

    // Deterministic placement decided once from the full measured popup size
    // (frame 0): below the anchor when it fits, otherwise above it. The layout
    // bounds never change during the animation, so the popup cannot jump.
    val showAbove = remember { mutableStateOf(false) }
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val marginPx = with(density) { 8.dp.roundToPx() }
                val gapPx = with(density) { 6.dp.roundToPx() }

                val x = (anchorBounds.right - popupContentSize.width).coerceIn(
                    marginPx,
                    (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
                )

                val fitsBelow = anchorBounds.bottom + gapPx + popupContentSize.height <=
                    windowSize.height - marginPx
                showAbove.value = !fitsBelow
                val y = if (fitsBelow) {
                    anchorBounds.bottom + gapPx
                } else {
                    anchorBounds.top - popupContentSize.height - gapPx
                }

                return IntOffset(x, y)
            }
        }
    }

    // Entrance/exit run purely on graphicsLayer (scale + alpha); the popup's
    // layout bounds stay fixed so there is no vertical jump.
    val menuProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "dropdown-menu-progress",
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(triggerShape)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .background(VusicColors.SurfaceHigh)
                .border(BorderStroke(borderWidth, borderColor), triggerShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = { expanded = !expanded },
                )
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = selectedLabel,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else VusicControlColors.DisabledThumb,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Close options" else "Open options",
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        if (expanded || menuProgress > 0.01f) {
            Popup(
                onDismissRequest = { expanded = false },
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    Modifier.graphicsLayer {
                        scaleX = 0.92f + 0.08f * menuProgress
                        scaleY = 0.92f + 0.08f * menuProgress
                        alpha = menuProgress
                        transformOrigin = if (showAbove.value) {
                            TransformOrigin(1f, 1f)
                        } else {
                            TransformOrigin(1f, 0f)
                        }
                    },
                ) {
                    val popupShape = RoundedCornerShape(16.dp)
                    val popupBorderColor = if (dark) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                    val popupBg = if (dark) {
                        Color(0xFF1E1E1E)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }

                    Surface(
                        shape = popupShape,
                        color = popupBg,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, popupBorderColor),
                    ) {
                        val itemShape = RoundedCornerShape(10.dp)
                        val selectedBgAlpha = if (dark) 0.16f else 0.12f

                        Column(
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .width(IntrinsicSize.Max)
                                .widthIn(min = 140.dp)
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            options.forEach { option ->
                                val isSelected = option == value
                                val itemBg = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = selectedBgAlpha)
                                } else {
                                    Color.Transparent
                                }
                                val textColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 44.dp)
                                        .clip(itemShape)
                                        .background(itemBg)
                                        .clickable(role = Role.DropdownList) {
                                            onSelect(option)
                                            expanded = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = labelProvider(option),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
        VusicDropdownSelector(
            value = value,
            options = options,
            onSelect = onSelect,
        )
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Slider(
                value = index.toFloat().coerceIn(0f, maxIndex.toFloat()),
                // roundToInt, not toInt: the snapped position of the LAST step
                // arrives as 15.999998, and truncating it made the last value
                // on every rail unreachable (the song cache stopped at 75 GB
                // instead of 100 GB).
                onValueChange = { onIndex(it.roundToInt().coerceIn(0, maxIndex)) },
                valueRange = 0f..maxIndex.toFloat(),
                // `steps` is the number of DISCRETE POSITIONS on the rail, and
                // Compose spaces them evenly over the range. With one position
                // too few, the rail had one position fewer than the list it
                // indexes, the spacing was off, and one size per rail could
                // not be picked at all: the old song-cache rail (256 MB ..
                // 5 GB) skipped 2 GB, and the new one stopped at 75 GB.
                steps = maxIndex.coerceAtLeast(0),
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
            color = MaterialTheme.colorScheme.onSurface,
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
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
