package com.feishin.mobile.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.VusicColors

/**
 * Outcomes shown inline on the login / Add-Edit server form.
 */
sealed interface ConnectionBannerState {
    data object Idle : ConnectionBannerState
    data class Testing(val message: String) : ConnectionBannerState
    data class Success(val message: String) : ConnectionBannerState
    data class Error(val message: String, val details: String? = null) : ConnectionBannerState
}

/**
 * Animated inline status banner for connection probing and authentication.
 *
 * Uses [LiveRegionMode.Polite] for TalkBack announcements, crossfades between
 * visible states (e.g. Testing -> Success/Error) without collapse flicker, and
 * degrades animations to `snap()` when system animator scale is 0.
 */
@Composable
fun ConnectionBanner(
    state: ConnectionBannerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberLoginReduceMotion()
    var lastVisible by remember { mutableStateOf<ConnectionBannerState>(ConnectionBannerState.Idle) }
    if (state !is ConnectionBannerState.Idle) {
        lastVisible = state
    }

    AnimatedVisibility(
        visible = state !is ConnectionBannerState.Idle,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        enter = if (reduceMotion) {
            fadeIn(snap()) + expandVertically(snap())
        } else {
            fadeIn(tween(170)) + expandVertically(spring(dampingRatio = 0.9f))
        },
        exit = if (reduceMotion) {
            fadeOut(snap()) + shrinkVertically(snap())
        } else {
            fadeOut(tween(120)) + shrinkVertically()
        },
    ) {
        val visibleState = if (state is ConnectionBannerState.Idle) lastVisible else state
        if (visibleState is ConnectionBannerState.Idle) return@AnimatedVisibility

        val targetContainer = when (visibleState) {
            is ConnectionBannerState.Testing -> VusicColors.SurfaceHigh
            is ConnectionBannerState.Success -> MaterialTheme.colorScheme.primaryContainer
            is ConnectionBannerState.Error -> MaterialTheme.colorScheme.errorContainer
            is ConnectionBannerState.Idle -> VusicColors.SurfaceHigh
        }
        val targetContent = when (visibleState) {
            is ConnectionBannerState.Testing -> MaterialTheme.colorScheme.onSurface
            is ConnectionBannerState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
            is ConnectionBannerState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is ConnectionBannerState.Idle -> MaterialTheme.colorScheme.onSurface
        }

        val containerColor by animateColorAsState(
            targetValue = targetContainer,
            animationSpec = if (reduceMotion) snap() else tween(170),
            label = "banner-container",
        )
        val contentColor by animateColorAsState(
            targetValue = targetContent,
            animationSpec = if (reduceMotion) snap() else tween(170),
            label = "banner-content",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AnimatedContent(
                targetState = visibleState,
                transitionSpec = {
                    val spec = if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                    }
                    spec.using(SizeTransform(clip = false))
                },
                label = "banner-inner",
            ) { current ->
                when (current) {
                    is ConnectionBannerState.Idle -> Unit
                    is ConnectionBannerState.Testing -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = contentColor,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(
                                    "Dismiss",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                    is ConnectionBannerState.Success -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(
                                    "Dismiss",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                    is ConnectionBannerState.Error -> {
                        var detailsExpanded by remember(current.details) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = current.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text(
                                        "Dismiss",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = contentColor,
                                    )
                                }
                            }
                            if (!current.details.isNullOrBlank()) {
                                TextButton(
                                    onClick = { detailsExpanded = !detailsExpanded },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = if (detailsExpanded) "Hide details" else "Details",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = contentColor,
                                    )
                                }
                                if (detailsExpanded) {
                                    Text(
                                        text = current.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor,
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
