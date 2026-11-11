package com.feishin.mobile.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.feishin.mobile.core.ui.ArtworkColorExtractor
import com.feishin.mobile.core.ui.ArtworkPixels
import com.feishin.mobile.core.ui.Contrast
import com.feishin.mobile.core.ui.DebugCoverOverride
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.core.ui.PlayingScheme
import com.feishin.mobile.core.ui.VusicDynamicScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The artwork color scheme for the currently playing track, or null when the
 * "Dynamic Now Playing color" setting is off (or nothing is playing). Null
 * means "use the app theme's primaryContainer / onPrimaryContainer".
 */
val LocalPlayingColors = staticCompositionLocalOf<PlayingScheme?> { null }

/** Thread-safe LRU cache of precomputed [VusicDynamicScheme]s by `"$dark|$lightTintStrength|$coverKey"`. */
internal object DynamicSchemeCache {
    private const val MAX = 64
    private val map = object : LinkedHashMap<String, VusicDynamicScheme>(MAX, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, VusicDynamicScheme>,
        ): Boolean = size > MAX
    }

    @Synchronized
    fun get(key: String): VusicDynamicScheme? = map[key]

    @Synchronized
    fun put(key: String, value: VusicDynamicScheme) {
        map[key] = value
    }
}

private fun isSoftwareBitmap(bitmap: Bitmap): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bitmap.config != Bitmap.Config.HARDWARE

private fun extractSeedFromSoftwareBitmap(bitmap: Bitmap): Long? {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    return ArtworkColorExtractor.extractSourceColor(pixels, w, h)
}

/**
 * Computes (off the main thread) and caches the full [VusicDynamicScheme] for
 * [coverUrl] or an active [DebugCoverOverride].
 *
 * Key invariants:
 * 1. If [DebugCoverOverride.current] is non-null, extracts directly from those
 *    [ArtworkPixels] (keyed by pixel hash) so debug tests can exercise all 9
 *    fixtures live via broadcast even when no track is loaded.
 * 2. When switching from one track to another (`enabled && hasTrack`), retains
 *    the previous track's scheme while the new cover is decoding so the UI
 *    never flashes the static default theme between songs.
 * 3. Pre-seeds synchronously from [DynamicSchemeCache] and checks Coil's
 *    in-memory cache with a `config != Bitmap.Config.HARDWARE` guard before
 *    falling back to an async software decode.
 */
@Composable
fun rememberDynamicScheme(
    coverUrl: String?,
    enabled: Boolean,
    dark: Boolean,
    hasTrack: Boolean = coverUrl != null,
    lightTintStrength: Float = PlayingColors.DefaultLightTintStrength,
): VusicDynamicScheme? {
    val debugOverride by DebugCoverOverride.current.collectAsState()
    val ctx = LocalContext.current

    val effectiveEnabled = enabled && (hasTrack || debugOverride != null)
    val cacheKey = remember(debugOverride, coverUrl, dark, lightTintStrength, effectiveEnabled) {
        when {
            !effectiveEnabled -> null
            debugOverride != null -> "$dark|$lightTintStrength|debug:${debugOverride.hashCode()}"
            coverUrl != null -> "$dark|$lightTintStrength|url:$coverUrl"
            else -> "$dark|$lightTintStrength|none"
        }
    }

    var activeScheme by remember { mutableStateOf<VusicDynamicScheme?>(null) }

    // Synchronous cache / debug-override lookup to avoid a 1-frame flash when cached.
    val cachedNow = remember(cacheKey, debugOverride, dark, lightTintStrength) {
        if (cacheKey == null) {
            null
        } else {
            DynamicSchemeCache.get(cacheKey) ?: debugOverride?.let { pixels: ArtworkPixels ->
                val seed = ArtworkColorExtractor.extractSourceColor(pixels)
                seed?.let {
                    PlayingColors.buildMaterialScheme(it, dark, lightTintStrength)
                        .also { scheme -> DynamicSchemeCache.put(cacheKey, scheme) }
                }
            }
        }
    }

    if (!effectiveEnabled) {
        if (activeScheme != null) activeScheme = null
        return null
    }

    if (cachedNow != null && activeScheme != cachedNow) {
        activeScheme = cachedNow
    } else if (activeScheme != null && activeScheme?.dark != dark) {
        // Theme polarity flipped while a seed was already known: re-tone immediately.
        val prevSeed = activeScheme?.seedArgb
        if (prevSeed != null) {
            activeScheme = PlayingColors.buildMaterialScheme(prevSeed, dark, lightTintStrength)
        }
    }

    LaunchedEffect(cacheKey, debugOverride, coverUrl, effectiveEnabled, dark, lightTintStrength) {
        if (!effectiveEnabled || cacheKey == null) {
            activeScheme = null
            return@LaunchedEffect
        }
        DynamicSchemeCache.get(cacheKey)?.let {
            activeScheme = it
            return@LaunchedEffect
        }
        val overridePixels = debugOverride
        if (overridePixels != null) {
            val computed = withContext(Dispatchers.Default) {
                val seed = ArtworkColorExtractor.extractSourceColor(overridePixels)
                seed?.let { PlayingColors.buildMaterialScheme(it, dark, lightTintStrength) }
            }
            if (computed != null) {
                DynamicSchemeCache.put(cacheKey, computed)
                activeScheme = computed
            } else {
                activeScheme = null
            }
            return@LaunchedEffect
        }
        if (coverUrl == null) {
            activeScheme = null
            return@LaunchedEffect
        }

        // Check Coil memory cache first, guarding against HARDWARE bitmaps.
        val memBitmap = runCatching {
            ctx.imageLoader.memoryCache?.get(MemoryCache.Key(coverUrl))?.bitmap
        }.getOrNull()

        val computed = if (memBitmap != null && isSoftwareBitmap(memBitmap)) {
            withContext(Dispatchers.Default) {
                runCatching {
                    val seed = extractSeedFromSoftwareBitmap(memBitmap)
                    seed?.let { PlayingColors.buildMaterialScheme(it, dark, lightTintStrength) }
                }.getOrNull()
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .size(96, 96)
                        .allowHardware(false)
                        .build()
                    val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
                        ?: return@runCatching null
                    if (!isSoftwareBitmap(bmp)) return@runCatching null
                    withContext(Dispatchers.Default) {
                        val seed = extractSeedFromSoftwareBitmap(bmp)
                        seed?.let { PlayingColors.buildMaterialScheme(it, dark, lightTintStrength) }
                    }
                }.getOrNull()
            }
        }

        if (computed != null) {
            DynamicSchemeCache.put(cacheKey, computed)
            activeScheme = computed
        }
    }

    return cachedNow ?: activeScheme
}

/**
 * Legacy adapter returning a [PlayingScheme] directly from [rememberDynamicScheme].
 */
@Composable
fun rememberPlayingScheme(
    coverUrl: String?,
    enabled: Boolean,
    dark: Boolean,
    fallbackSeedArgb: Long = 0L,
    fallbackContainerArgb: Long = 0L,
    fallbackOnContainerArgb: Long = 0L,
    hasTrack: Boolean = coverUrl != null,
): PlayingScheme? {
    val dynamic = rememberDynamicScheme(
        coverUrl = coverUrl,
        enabled = enabled,
        dark = dark,
        hasTrack = hasTrack,
    )
    return remember(dynamic) { dynamic?.toPlayingScheme() }
}

private fun blendPlayingScheme(from: PlayingScheme, to: PlayingScheme, t: Float): PlayingScheme {
    if (t >= 0.999f) return to
    if (t <= 0.001f) return from
    return to.copy(
        container = Contrast.blend(to.container, from.container, t),
        onContainer = Contrast.blend(to.onContainer, from.onContainer, t),
        secondary = Contrast.blend(to.secondary, from.secondary, t),
        primary = Contrast.blend(to.primary, from.primary, t),
        onPrimary = Contrast.blend(to.onPrimary, from.onPrimary, t),
        track = Contrast.blend(to.track, from.track, t),
        surfaceTop = Contrast.blend(to.surfaceTop, from.surfaceTop, t),
        surfaceBottom = Contrast.blend(to.surfaceBottom, from.surfaceBottom, t),
        surfaceText = Contrast.blend(to.surfaceText, from.surfaceText, t),
        surfaceSecondary = Contrast.blend(to.surfaceSecondary, from.surfaceSecondary, t),
        miniContainer = Contrast.blend(to.miniContainer, from.miniContainer, t),
        miniOutline = Contrast.blend(to.miniOutline, from.miniOutline, t),
    )
}

private fun blendDynamicScheme(
    from: VusicDynamicScheme,
    to: VusicDynamicScheme,
    t: Float,
): VusicDynamicScheme {
    if (t >= 0.999f) return to
    if (t <= 0.001f) return from
    return to.copy(
        primary = Contrast.blend(to.primary, from.primary, t),
        onPrimary = Contrast.blend(to.onPrimary, from.onPrimary, t),
        primaryContainer = Contrast.blend(to.primaryContainer, from.primaryContainer, t),
        onPrimaryContainer = Contrast.blend(to.onPrimaryContainer, from.onPrimaryContainer, t),
        secondary = Contrast.blend(to.secondary, from.secondary, t),
        onSecondary = Contrast.blend(to.onSecondary, from.onSecondary, t),
        secondaryContainer = Contrast.blend(to.secondaryContainer, from.secondaryContainer, t),
        onSecondaryContainer = Contrast.blend(to.onSecondaryContainer, from.onSecondaryContainer, t),
        background = Contrast.blend(to.background, from.background, t),
        onBackground = Contrast.blend(to.onBackground, from.onBackground, t),
        surface = Contrast.blend(to.surface, from.surface, t),
        onSurface = Contrast.blend(to.onSurface, from.onSurface, t),
        surfaceVariant = Contrast.blend(to.surfaceVariant, from.surfaceVariant, t),
        onSurfaceVariant = Contrast.blend(to.onSurfaceVariant, from.onSurfaceVariant, t),
        surfaceContainerLowest = Contrast.blend(to.surfaceContainerLowest, from.surfaceContainerLowest, t),
        surfaceContainerLow = Contrast.blend(to.surfaceContainerLow, from.surfaceContainerLow, t),
        surfaceContainer = Contrast.blend(to.surfaceContainer, from.surfaceContainer, t),
        surfaceContainerHigh = Contrast.blend(to.surfaceContainerHigh, from.surfaceContainerHigh, t),
        outline = Contrast.blend(to.outline, from.outline, t),
        outlineVariant = Contrast.blend(to.outlineVariant, from.outlineVariant, t),
        switchOffTrack = Contrast.blend(to.switchOffTrack, from.switchOffTrack, t),
        switchOffOutline = Contrast.blend(to.switchOffOutline, from.switchOffOutline, t),
        switchOffThumb = Contrast.blend(to.switchOffThumb, from.switchOffThumb, t),
        sliderInactiveTrack = Contrast.blend(to.sliderInactiveTrack, from.sliderInactiveTrack, t),
        chipOffFill = Contrast.blend(to.chipOffFill, from.chipOffFill, t),
        chipOffText = Contrast.blend(to.chipOffText, from.chipOffText, t),
        miniContainer = Contrast.blend(to.miniContainer, from.miniContainer, t),
        miniOutline = Contrast.blend(to.miniOutline, from.miniOutline, t),
        ringTrack = Contrast.blend(to.ringTrack, from.ringTrack, t),
        surfaceTop = Contrast.blend(to.surfaceTop, from.surfaceTop, t),
        surfaceBottom = Contrast.blend(to.surfaceBottom, from.surfaceBottom, t),
        surfaceText = Contrast.blend(to.surfaceText, from.surfaceText, t),
        surfaceSecondary = Contrast.blend(to.surfaceSecondary, from.surfaceSecondary, t),
        rowHighlightSecondary = Contrast.blend(to.rowHighlightSecondary, from.rowHighlightSecondary, t),
    )
}

/**
 * Smoothly interpolates between consecutive [VusicDynamicScheme]s over ~400 ms
 * using a single scalar [Animatable] blend factor (`0f..1f`) and primitive ARGB math.
 * Snaps immediately on cold start (when cached) or theme polarity switch, and fades
 * cleanly to/from static defaults when tracks start or clear.
 */
@Composable
fun animatedDynamicScheme(raw: VusicDynamicScheme?, dark: Boolean): VusicDynamicScheme? {
    val activeRaw = raw?.takeIf { it.dark == dark }
    val staticScheme = remember(dark) { PlayingColors.staticMaterialScheme(dark) }
    var fromScheme by remember { mutableStateOf(activeRaw) }
    var targetScheme by remember { mutableStateOf(activeRaw) }
    var fadingToNull by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(activeRaw, dark) {
        val currentTarget = targetScheme
        if (currentTarget != null && currentTarget.dark != dark) {
            fromScheme = activeRaw
            targetScheme = activeRaw
            fadingToNull = false
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (activeRaw == null) {
            if (currentTarget == null || fadingToNull) {
                fromScheme = null
                targetScheme = null
                fadingToNull = false
                progress.snapTo(1f)
                return@LaunchedEffect
            }
            val currentDisplayed = fromScheme?.let {
                blendDynamicScheme(it, currentTarget, progress.value)
            } ?: currentTarget
            fromScheme = currentDisplayed
            targetScheme = staticScheme
            fadingToNull = true
            val duration = Motion.scaled(DYNAMIC_THEME_TRANSITION_MS)
            if (duration <= 0) {
                fromScheme = null
                targetScheme = null
                fadingToNull = false
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing,
                    ),
                )
                fromScheme = null
                targetScheme = null
                fadingToNull = false
            }
            return@LaunchedEffect
        }

        if (currentTarget == null && !fadingToNull) {
            fromScheme = staticScheme
            targetScheme = activeRaw
            fadingToNull = false
            val duration = Motion.scaled(DYNAMIC_THEME_TRANSITION_MS)
            if (duration <= 0) {
                fromScheme = activeRaw
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else if (currentTarget != activeRaw || fadingToNull) {
            val currentDisplayed = if (currentTarget != null) {
                fromScheme?.let { blendDynamicScheme(it, currentTarget, progress.value) } ?: currentTarget
            } else {
                staticScheme
            }
            fromScheme = currentDisplayed
            targetScheme = activeRaw
            fadingToNull = false
            val duration = Motion.scaled(DYNAMIC_THEME_TRANSITION_MS)
            if (duration <= 0) {
                fromScheme = activeRaw
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    val target = targetScheme ?: return activeRaw
    if (target.dark != dark) return activeRaw
    if (fadingToNull && progress.value >= 0.999f) return null
    val start = fromScheme ?: target
    return blendDynamicScheme(start, target, progress.value)
}

/** Duration in ms for smooth track-to-track color transitions. */
const val DYNAMIC_THEME_TRANSITION_MS = 400

/**
 * Smoothly interpolates between consecutive [PlayingScheme]s over ~400 ms using a
 * single scalar [Animatable] blend factor (`0f..1f`) and primitive ARGB math.
 * Snaps immediately on initial composition / cold start so restored queues never
 * sweep from an uninitialized state.
 */
@Composable
fun animatedPlayingScheme(raw: PlayingScheme?): PlayingScheme? {
    var fromScheme by remember { mutableStateOf(raw) }
    var targetScheme by remember { mutableStateOf(raw) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(raw) {
        if (raw == null) {
            fromScheme = null
            targetScheme = null
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        val currentTarget = targetScheme
        if (currentTarget == null || currentTarget.dark != raw.dark) {
            // Cold start, restored queue, or theme polarity switch: snap immediately.
            fromScheme = raw
            targetScheme = raw
            progress.snapTo(1f)
        } else if (currentTarget != raw) {
            val currentDisplayed = fromScheme?.let {
                blendPlayingScheme(it, currentTarget, progress.value)
            } ?: currentTarget
            fromScheme = currentDisplayed
            targetScheme = raw
            val duration = Motion.scaled(DYNAMIC_THEME_TRANSITION_MS)
            if (duration <= 0) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    if (raw == null) return null
    val target = targetScheme ?: raw
    val start = fromScheme ?: target
    return blendPlayingScheme(start, target, progress.value)
}

/** Compose color from a scheme Long (opaque by construction). */
fun playingColor(argb: Long): Color = Color(argb.toInt())
