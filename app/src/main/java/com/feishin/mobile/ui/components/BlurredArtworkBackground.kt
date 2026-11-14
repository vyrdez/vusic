package com.feishin.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.core.ui.ArtworkBlurCache
import com.feishin.mobile.core.ui.BlurGradientAxis
import com.feishin.mobile.core.ui.BlurScrim
import com.feishin.mobile.core.ui.BlurSurfaceRole
import com.feishin.mobile.core.ui.BlurredArtwork
import com.feishin.mobile.core.ui.DebugCoverOverride
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.playingColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * True when blurred artwork backgrounds are supported on the running device.
 *
 * API 31+ uses hardware `RenderEffect` (`Modifier.blur`) on the downscaled artwork,
 * while API 26..30 uses a one-time background-thread 3-pass separable box blur
 * cached in [ArtworkBlurCache].
 */
val blurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/** Default crossfade duration in ms between cover artworks before animation-scale adjustment. */
private const val COVER_CROSSFADE_MS = 400

/** Hardware blur radius applied on API 31+ to the low-res cached artwork layer. */
private val HARDWARE_BLUR_RADIUS = 68.dp

/**
 * Renders the shared 3-layer opaque blurred artwork background stack:
 * 1. Solid 100% opaque base (`baseAlpha == 1.0f`) outside the cover crossfade.
 * 2. Clipped, low-resolution blurred artwork at [BlurScrim.artworkAlpha] (`0.80f..0.90f`),
 *    using [BlurredEdgeTreatment.Rectangle] on API 31+ so edges never sample transparent black.
 * 3. Uniform contrast scrim from [BlurScrim.resolveStack] preserving spatial artwork colors.
 */
@Composable
fun BlurredArtworkBackground(
    coverId: String?,
    bitmap: ImageBitmap?,
    scrimColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    role: BlurSurfaceRole = BlurSurfaceRole.PLAYER,
    accentTintColor: Color = scrimColor,
) {
    val context = LocalContext.current
    val debugOverride by DebugCoverOverride.current.collectAsState()
    val debugBitmap = remember(debugOverride) {
        val ov = debugOverride
        if (ov != null && ov.width > 0 && ov.height > 0 && ov.argb.size >= ov.width * ov.height) {
            Bitmap.createBitmap(ov.argb, ov.width, ov.height, Bitmap.Config.ARGB_8888)
        } else {
            null
        }
    }
    val effectiveCoverId = remember(debugOverride, coverId) {
        if (debugOverride != null) {
            "debug_fixture_${debugOverride.hashCode()}"
        } else {
            coverId
        }
    }
    var entry by remember(effectiveCoverId) {
        mutableStateOf(effectiveCoverId?.let { ArtworkBlurCache.peek(it) })
    }

    LaunchedEffect(effectiveCoverId, bitmap, debugBitmap) {
        val key = effectiveCoverId
        if (key.isNullOrBlank()) {
            entry = null
            return@LaunchedEffect
        }
        ArtworkBlurCache.peek(key)?.let { cached ->
            entry = cached
            return@LaunchedEffect
        }
        val sourceBitmap = when {
            debugBitmap != null -> debugBitmap
            bitmap != null -> runCatching { bitmap.asAndroidBitmap() }.getOrNull()
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(context)
                        .data(key)
                        .size(ArtworkBlurCache.TARGET_SIZE_PX, ArtworkBlurCache.TARGET_SIZE_PX)
                        .allowHardware(false)
                        .build()
                    (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
                }.getOrNull()
            }
        }
        entry = if (sourceBitmap != null) {
            ArtworkBlurCache.get(key, sourceBitmap)
        } else {
            null
        }
    }

    val animDurationMs = remember(context, Motion.durationScale) {
        val sysScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Motion.durationScale,
            )
        }.getOrDefault(Motion.durationScale)
        val effectiveScale = if (Motion.durationScale == 0f || sysScale == 0f) {
            0f
        } else {
            Motion.durationScale.coerceIn(0f, 4f)
        }
        (COVER_CROSSFADE_MS * effectiveScale).toInt()
    }
    val crossfadeSpec = remember(animDurationMs) { tween<Float>(durationMillis = animDurationMs) }

    val blurModifier = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier
                .fillMaxSize()
                .blur(
                    radius = HARDWARE_BLUR_RADIUS,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                )
        } else {
            Modifier.fillMaxSize()
        }
    }

    val opaqueBaseColor = remember(scrimColor) { scrimColor.copy(alpha = BlurScrim.BASE_ALPHA) }
    val opaqueAccentColor = remember(accentTintColor) { accentTintColor.copy(alpha = 1.0f) }

    // Layer 1: 100% opaque base sits outside Crossfade so cover transitions or
    // initial loads never dip combined surface opacity below 1.0.
    Box(
        modifier = modifier
            .clipToBounds()
            .background(opaqueBaseColor),
    ) {
        Crossfade(
            targetState = entry,
            animationSpec = crossfadeSpec,
            label = "blurred-artwork-crossfade",
        ) { currentEntry: BlurredArtwork? ->
            if (currentEntry != null && blurSupported) {
                val stack = remember(
                    role,
                    currentEntry.avgLuminance,
                    isDark,
                    opaqueBaseColor,
                    opaqueAccentColor,
                ) {
                    BlurScrim.resolveStack(
                        role = role,
                        avgLuminance = currentEntry.avgLuminance,
                        isDark = isDark,
                        baseColorArgb = opaqueBaseColor.toArgb().toLong() and 0xFFFFFFFFL,
                        accentTintArgb = opaqueAccentColor.toArgb().toLong() and 0xFFFFFFFFL,
                    )
                }
                val imageBitmap = remember(currentEntry.coverId, currentEntry.bitmap) {
                    currentEntry.bitmap.asImageBitmap()
                }
                val isUniformScrim = stack.scrimStartArgb == stack.scrimEndArgb &&
                    stack.scrimStartAlpha == stack.scrimEndAlpha
                val uniformScrimColor = remember(stack) {
                    playingColor(stack.scrimStartArgb).copy(alpha = stack.scrimStartAlpha)
                }
                val scrimBrush = remember(stack, isUniformScrim) {
                    if (isUniformScrim) {
                        null
                    } else {
                        val stops = listOf(
                            playingColor(stack.scrimStartArgb).copy(alpha = stack.scrimStartAlpha),
                            playingColor(stack.scrimEndArgb).copy(alpha = stack.scrimEndAlpha),
                        )
                        when (stack.gradientAxis) {
                            BlurGradientAxis.VERTICAL -> Brush.verticalGradient(stops)
                            BlurGradientAxis.HORIZONTAL -> Brush.horizontalGradient(stops)
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    // Layer 2: Clipped blurred artwork over the solid opaque base.
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = stack.artworkAlpha,
                        modifier = if (currentEntry.isPreBlurred) Modifier.fillMaxSize() else blurModifier,
                    )
                    // Layer 3: Uniform contrast scrim preserving spatial artwork colors.
                    Box(
                        if (scrimBrush != null) {
                            Modifier.fillMaxSize().background(scrimBrush)
                        } else {
                            Modifier.fillMaxSize().background(uniformScrimColor)
                        },
                    )
                }
            }
        }
    }
}
