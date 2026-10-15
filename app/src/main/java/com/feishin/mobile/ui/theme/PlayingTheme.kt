package com.feishin.mobile.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.core.ui.PlayingScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The artwork color scheme for the currently playing track, or null when the
 * "Dynamic Now Playing color" setting is off (or nothing is playing). Null
 * means "use the app theme's primaryContainer / onPrimaryContainer".
 */
val LocalPlayingColors = staticCompositionLocalOf<PlayingScheme?> { null }

/** Raw (unanimated) schemes by "$dark|$coverUrl", so re-showing a track is free. */
private object PlayingSchemeCache {
    private const val MAX = 24
    private val map = object : LinkedHashMap<String, PlayingScheme>(MAX, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PlayingScheme>,
        ): Boolean = size > MAX
    }

    @Synchronized
    fun get(key: String): PlayingScheme? = map[key]

    @Synchronized
    fun put(key: String, value: PlayingScheme) {
        map[key] = value
    }
}

// Scheme Longs are packed 0xAARRGGBB. They must go through the Int
// constructor: Color(ULong) reads the top bits as a color-space id, which
// crashes (ArrayIndexOutOfBounds in getColorSpace) on plain ARGB values.
private fun Long.toCompose(): Color = Color(this.toInt())

/**
 * Computes (off the main thread) and caches the raw scheme for [coverUrl].
 * Seed = Palette vibrant preferred, else dominant; gray/missing seeds fall
 * back to the theme primary, then to the theme container pair — see
 * [PlayingColors.schemeFor].
 */
@Composable
fun rememberPlayingScheme(
    coverUrl: String?,
    enabled: Boolean,
    dark: Boolean,
    fallbackSeedArgb: Long,
    fallbackContainerArgb: Long,
    fallbackOnContainerArgb: Long,
): PlayingScheme? {
    var raw by remember { mutableStateOf<PlayingScheme?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(coverUrl, enabled, dark) {
        if (!enabled || coverUrl == null) {
            raw = null
            return@LaunchedEffect
        }
        val key = "$dark|$coverUrl"
        PlayingSchemeCache.get(key)?.let {
            raw = it
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(ctx).data(coverUrl)
                    .size(96, 96).allowHardware(false).build()
                val bmp = (ctx.imageLoader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: return@runCatching null
                val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                val vibrant = palette.getVibrantColor(0)
                val dominant = palette.getDominantColor(0)
                val seed = (if (vibrant != 0) vibrant else dominant)
                    .takeIf { it != 0 }?.toLong()?.and(0xFFFFFFFFL)
                PlayingColors.schemeFor(
                    seedArgb = seed,
                    fallbackContainer = fallbackContainerArgb,
                    fallbackOnContainer = fallbackOnContainerArgb,
                    dark = dark,
                    fallbackSeedArgb = fallbackSeedArgb,
                )
            }.getOrNull()
        }
        if (computed != null) {
            PlayingSchemeCache.put(key, computed)
            raw = computed
        }
    }
    return raw
}

/**
 * Crossfades every channel of [raw] over [Motion.SchemeChange] (instant when
 * the system animation scale is off). All animate calls run unconditionally so
 * hooks stay stable; a null input yields null output.
 */
@Composable
fun animatedPlayingScheme(raw: PlayingScheme?): PlayingScheme? {
    val spec = Motion.color(Motion.SchemeChange)
    val container by animateColorAsState(raw?.container?.toCompose() ?: Color.Transparent, spec, label = "scheme-container")
    val onContainer by animateColorAsState(raw?.onContainer?.toCompose() ?: Color.Transparent, spec, label = "scheme-onContainer")
    val secondary by animateColorAsState(raw?.secondary?.toCompose() ?: Color.Transparent, spec, label = "scheme-secondary")
    val primary by animateColorAsState(raw?.primary?.toCompose() ?: Color.Transparent, spec, label = "scheme-primary")
    val onPrimary by animateColorAsState(raw?.onPrimary?.toCompose() ?: Color.Transparent, spec, label = "scheme-onPrimary")
    val track by animateColorAsState(raw?.track?.toCompose() ?: Color.Transparent, spec, label = "scheme-track")
    val surfaceTop by animateColorAsState(raw?.surfaceTop?.toCompose() ?: Color.Transparent, spec, label = "scheme-top")
    val surfaceBottom by animateColorAsState(raw?.surfaceBottom?.toCompose() ?: Color.Transparent, spec, label = "scheme-bottom")
    val surfaceText by animateColorAsState(raw?.surfaceText?.toCompose() ?: Color.Transparent, spec, label = "scheme-surfaceText")
    val surfaceSecondary by animateColorAsState(raw?.surfaceSecondary?.toCompose() ?: Color.Transparent, spec, label = "scheme-surfaceSecondary")
    if (raw == null) return null
    fun Color.toSchemeLong(): Long = toArgb().toLong().and(0xFFFFFFFFL)
    return raw.copy(
        container = container.toSchemeLong(),
        onContainer = onContainer.toSchemeLong(),
        secondary = secondary.toSchemeLong(),
        primary = primary.toSchemeLong(),
        onPrimary = onPrimary.toSchemeLong(),
        track = track.toSchemeLong(),
        surfaceTop = surfaceTop.toSchemeLong(),
        surfaceBottom = surfaceBottom.toSchemeLong(),
        surfaceText = surfaceText.toSchemeLong(),
        surfaceSecondary = surfaceSecondary.toSchemeLong(),
    )
}

/** Compose color from a scheme Long (opaque by construction). */
fun playingColor(argb: Long): Color = Color(argb.toInt())
