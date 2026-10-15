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
            else -> Color(0xFF6E6E6E)
        },
        animationSpec = Motion.color(Motion.Fast),
        label = "btn-border",
    )
    val contentColor = when {
        !enabled -> VusicControlColors.DisabledThumb
        danger -> DangerRed
        else -> Color.White
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
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
 * A single-choice pill (ReplayGain "Track", bitrate "Original"/"raw", ...).
 *
 * Always left-aligned, directly under the row's title and description, and
 * several pills sit left to right with 8dp gaps, wrapping to the next line when
 * they do not fit.
 */
@Composable
fun ChoicePills(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Simple flow layout: pills that fit share a line, the rest wrap.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VusicSpacing.ControlGap / 2),
        verticalArrangement = Arrangement.spacedBy(VusicSpacing.ControlGap / 2),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) Color.White else VusicControlColors.ChipOffFill,
                animationSpec = Motion.color(Motion.Fast),
                label = "pill-bg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) Color.Black else VusicControlColors.ChipOffText,
                animationSpec = Motion.color(Motion.Fast),
                label = "pill-fg",
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
            color = Color.White,
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