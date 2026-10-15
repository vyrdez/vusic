package com.feishin.mobile.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlColors
import com.feishin.mobile.ui.theme.VusicSpacing

/** Shared shape for every settings control, so rows read as one system. */
private val ControlShape = RoundedCornerShape(12.dp)

/** Red used for destructive / data-removing actions. */
val DangerRed = Color(0xFFCF6679)

/**
 * One button for every Settings screen.
 *
 * Rules this encodes (they used to be violated row by row):
 *  - always outlined, 1dp border, rounded, 40dp minimum height, 16dp horizontal
 *    padding, content width (never stretched, never a solid block);
 *  - left-aligned under its row's text, never floating mid-row or at the far
 *    right, so the eye reads label -> explanation -> control;
 *  - destructive actions get red text plus a red border at ~75% opacity.
 */
@Composable
fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.scale(Motion.Fast),
        label = "btn-scale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> VusicControlColors.DisabledOutline
            danger -> DangerRed.copy(alpha = 0.78f)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.color(Motion.Fast),
        label = "btn-border",
    )
    val contentColor = when {
        !enabled -> VusicControlColors.DisabledThumb
        danger -> DangerRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .clip(ControlShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(Color.Transparent)
            .then(
                Modifier.border(BorderStroke(1.dp, borderColor), ControlShape),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 40.dp)
            .widthIn(min = 96.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        // Centered: with the 96dp minimum, a short label would otherwise hug
        // the left edge and leave dead space on the right.
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.let {
                it()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One shared row for every Settings row with a trailing control: label and
 * description on the left, the control on the right, vertically centred, with
 * at least 16dp between them. The text takes the rest and wraps to at most 3
 * lines; it never runs under the control.
 *
 * When the text column would drop below ~40% of the row (long labels, large
 * font scale, small screens — see RowLayout), the control goes on the next
 * line, aligned to the END (right), never left or centred.
 *
 * @param controlWidthDp the control's intrinsic width estimate, used only for
 *        the beside/next-line decision (the control still sizes itself, capped
 *        at 45% of the row when beside the text).
 */
@Composable
fun SettingControlRow(
    label: String,
    description: String? = null,
    warning: String? = null,
    danger: Boolean = false,
    controlWidthDp: Float,
    showDivider: Boolean = false,
    cappedControl: Boolean = true,
    control: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = VusicSpacing.RowMinHeight)
                .padding(vertical = VusicSpacing.RowVertical),
        ) {
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            // Hoisted: inside Row/Box scopes below, maxWidth would need an
            // explicit receiver.
            val rowWidth = maxWidth
            val textEst = com.feishin.mobile.core.common.RowLayout.estimateTextWidthDp(
                label, description ?: warning, fontScale,
            )
            when (val at = com.feishin.mobile.core.common.RowLayout.decide(
                rowWidth.value, textEst, controlWidthDp * fontScale, capControl = cappedControl,
            )) {
                is com.feishin.mobile.core.common.RowLayout.Placement.Beside -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingRowText(
                            label = label, description = description, warning = warning,
                            danger = danger, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(VusicSpacing.ControlGap))
                        // Capped controls (buttons, dropdowns) never take more
                        // than 45% beside the text; segmented controls keep
                        // their intrinsic width instead of being squeezed.
                        val cap = if (cappedControl) {
                            Modifier.widthIn(
                                max = rowWidth *
                                    com.feishin.mobile.core.common.RowLayout.MAX_CONTROL_SHARE,
                            )
                        } else {
                            Modifier
                        }
                        Box(cap, contentAlignment = Alignment.CenterEnd) {
                            control()
                        }
                    }
                }
                com.feishin.mobile.core.common.RowLayout.Placement.NextLineEnd -> {
                    Column {
                        SettingRowText(
                            label = label, description = description, warning = warning,
                            danger = danger, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(com.feishin.mobile.core.common.RowLayout.STACKED_GAP_DP.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            control()
                        }
                    }
                }
            }
        }
        if (showDivider) {
            com.feishin.mobile.ui.settings.SettingsDivider()
        }
    }
}

/** Label + description + warning column shared by every control row. */
@Composable
private fun SettingRowText(
    label: String,
    description: String?,
    warning: String?,
    danger: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) DangerRed else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (description != null) {
            Spacer(Modifier.height(VusicSpacing.TitleToDescription))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (warning != null) {
            Spacer(Modifier.height(VusicSpacing.TitleToDescription))
            Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB74D),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Segmented single-row selector (Grid/List, Off/Track/Album, ...): equal-height
 * cells in one outlined container, the selected cell filled white. Never wraps;
 * if it cannot fit beside the text the whole row stacks via SettingControlRow.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Intrinsic width: the row is exactly as wide as its widest cell makes it,
// with every cell equal. Never stretched, never squeezed.
    Row(
        modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .clip(ControlShape)
            .background(Color.Transparent)
            .then(Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), ControlShape))
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = Motion.color(Motion.Fast),
                label = "seg-bg-$i",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else VusicControlColors.ChipOffText,
                animationSpec = Motion.color(Motion.Fast),
                label = "seg-fg-$i",
            )
            // IntrinsicSize.Max per cell makes every cell equal to the widest
            // label. `weight(1f)` here made each cell claim the full available
            // width, which stretched the whole control across the row (most
            // visible on the 4-option Theme selector).
            Box(
                Modifier
                    .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** A legend dot + label + right-aligned value, 8dp between rows. */
@Composable
fun UsageLegendRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(VusicSpacing.ControlGap / 2))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(VusicSpacing.ControlGap / 2))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = VusicColors.SecondaryText,
            maxLines = 1,
        )
    }
}

/** Small note under a control (helper text / inline warning), animated in. */
@Composable
fun SettingsNote(text: String, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = androidx.compose.animation.fadeIn(Motion.fade(Motion.Fast)),
        exit = androidx.compose.animation.fadeOut(Motion.fade(Motion.Fast)),
        modifier = modifier,
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Row wrapper that keeps a control block left-aligned under its text. */
@Composable
fun SettingsControlBlock(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}
