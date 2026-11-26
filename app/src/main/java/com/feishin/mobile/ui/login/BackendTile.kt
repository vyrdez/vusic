package com.feishin.mobile.ui.login

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.Motion.pressScale
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlColors

private val TileShape = RoundedCornerShape(12.dp)

fun BackendType.loginSubtitle(): String = when (this) {
    BackendType.NAVIDROME -> "Connect to your Navidrome server"
    BackendType.OPENSUBSONIC -> "Connect to your OpenSubsonic server"
    BackendType.JELLYFIN -> "Connect to your Jellyfin server"
    BackendType.GOOGLE_DRIVE -> "Connect to your Google Drive"
}

@Composable
internal fun rememberLoginReduceMotion(): Boolean {
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

private data class BackendTileOption(
    val type: BackendType,
    val title: String,
    val subtitle: String,
    val soon: Boolean = false,
)

private val BackendOptions = listOf(
    BackendTileOption(BackendType.NAVIDROME, "Navidrome", "Native & OpenSubsonic"),
    BackendTileOption(BackendType.OPENSUBSONIC, "OpenSubsonic", "Subsonic API v1.16+"),
    BackendTileOption(BackendType.JELLYFIN, "Jellyfin", "Direct audio stream"),
    BackendTileOption(BackendType.GOOGLE_DRIVE, "Google Drive", "Cloud folder library", soon = true),
)

/**
 * 2x2 backend selector grid shared by first-run login and Add/Edit server.
 *
 * When [locked] is true (Edit mode), tiles are non-interactive and a one-line
 * hint explains why backend type cannot be switched on an existing profile.
 */
@Composable
fun BackendTileGrid(
    selected: BackendType,
    enabled: Boolean,
    locked: Boolean,
    onSelect: (BackendType) -> Unit,
    onSoonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactive = enabled && !locked
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackendOptions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { option ->
                        BackendTile(
                            name = option.title,
                            subtitle = option.subtitle,
                            selected = selected == option.type,
                            soon = option.soon,
                            enabled = interactive,
                            onClick = {
                                if (option.soon) {
                                    onSoonClick()
                                } else {
                                    onSelect(option.type)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
        if (locked) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Backend type can't be changed. Remove and re-add to switch.",
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
            )
        }
    }
}

/**
 * Single selectable tile in the 2x2 backend type grid.
 * Uses SettingsCard radius (12.dp), VusicColors tokens, and reduced-motion fallbacks.
 */
@Composable
fun BackendTile(
    name: String,
    subtitle: String,
    selected: Boolean,
    soon: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberLoginReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> VusicColors.Accent
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = if (reduceMotion) snap() else tween(150),
        label = "backend-tile-border",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> VusicColors.Accent.copy(alpha = 0.08f)
            else -> VusicColors.SurfaceHigh
        },
        animationSpec = if (reduceMotion) snap() else tween(150),
        label = "backend-tile-bg",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "backend-tile-dot",
    )

    val clickModifier = if (soon) {
        Modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.RadioButton,
            onClick = onClick,
        )
    }

    val scaleModifier = if (!soon && !reduceMotion) {
        Modifier.pressScale(pressed = pressed && enabled, targetScale = 0.98f)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(scaleModifier)
            .clip(TileShape)
            .then(clickModifier),
        shape = TileShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (soon) 0.58f else 1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (soon) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (selected) VusicColors.Accent else VusicControlColors.RadioOffOutline,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dotScale > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        scaleX = dotScale
                                        scaleY = dotScale
                                    }
                                    .clip(CircleShape)
                                    .background(VusicColors.Accent),
                            )
                        }
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
