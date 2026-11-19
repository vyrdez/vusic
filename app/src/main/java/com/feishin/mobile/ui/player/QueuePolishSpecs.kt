package com.feishin.mobile.ui.player

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.feishin.mobile.core.ui.BlurScrim
import com.feishin.mobile.core.ui.Contrast
import com.feishin.mobile.core.ui.NowPlayingColors
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.ui.theme.playingColor
import kotlin.math.roundToInt

/**
 * Design & geometry constants plus contrast-guarded style resolvers for the
 * Phase 11.1 Section D queue sheet polish (D1 row spacing, D2 overlaid bottom
 * bar + edge fade, D3 concentric current-row shape, D4 style-aware highlight).
 */
object QueuePolishSpecs {

    // ---- D1: Row spacing & sizing ------------------------------------------

    /** Vertical spacing between queue rows via `Arrangement.spacedBy`. */
    const val ROW_VERTICAL_GAP_DP = 7

    /** Minimum height of each queue row so touch targets and covers stay uncrowded. */
    const val ROW_MIN_HEIGHT_DP = 64

    /** Horizontal padding for the queue list container. */
    const val LIST_HORIZONTAL_PADDING_DP = 14

    // ---- D2: Bottom controls & soft edge fade ------------------------------

    /** Height of the overlaid bottom control row (shuffle / close / repeat). */
    const val BOTTOM_CONTROLS_HEIGHT_DP = 56

    /** Height of the vertical alpha gradient fade at the bottom of the list viewport. */
    const val BOTTOM_FADE_HEIGHT_DP = 28

    /** Transparent bottom control container (no solid color block). */
    const val BOTTOM_BAR_CONTAINER_ALPHA = 0f

    // ---- D3: Highlighted row shape & concentric radii ----------------------

    /** Outer corner radius of the highlighted current-song row. */
    const val HIGHLIGHT_CORNER_RADIUS_DP = 20

    /** Equal inner padding on all four sides (start, top, end, bottom). */
    const val HIGHLIGHT_INNER_PADDING_DP = 10

    /**
     * Concentric inner cover corner radius: `outerRadius - innerPadding = 10.dp`
     * so the artwork arc tracks the row container smoothly.
     */
    const val COVER_CORNER_RADIUS_DP = HIGHLIGHT_CORNER_RADIUS_DP - HIGHLIGHT_INNER_PADDING_DP

    /** Uniform cover art size across highlighted and non-highlighted rows. */
    const val COVER_SIZE_DP = 48

    /** Full-cover scrim alpha behind the centered equalizer/pause glyph. */
    const val COVER_SCRIM_ALPHA = 0.40f

    /** Crossfade duration when the active queue item changes. */
    const val HIGHLIGHT_CROSSFADE_MS = 200

    // ---- D4: Highlight fill & border tokens --------------------------------

    const val DEFAULT_SUBTITLE_ALPHA = 0.82f
    const val BLUR_DARK_FILL_ALPHA_CLAMP_MIN = 0.14f
    const val BLUR_DARK_FILL_ALPHA_MIN = 0.16f
    const val BLUR_DARK_FILL_ALPHA_DEFAULT = 0.20f
    const val BLUR_DARK_FILL_ALPHA_MAX = 0.24f
    const val BLUR_LIGHT_FILL_ALPHA_MIN = 0.45f
    const val BLUR_LIGHT_FILL_ALPHA_DEFAULT = 0.50f
    const val BLUR_LIGHT_FILL_ALPHA_MAX = 0.55f
    const val BLUR_BORDER_ALPHA = 0.12f

    // ---- Phase 11.2 Section A: Transparent collapsed strip morph -----------

    /** Sheet drag progress at which the Queue sheet's own surface reaches full opacity. */
    const val QUEUE_SURFACE_FADE_IN_END_PROGRESS = 0.25f
}

/** Resolved colors and hairline border for the highlighted current queue row. */
data class QueueHighlightStyle(
    val containerColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val iconColor: Color,
    val borderColor: Color,
    val borderAlpha: Float,
)

/** Resolved transparent-container icon and pill colors for `QueueBottomBar`. */
data class QueueBottomControlColors(
    val containerColor: Color,
    val chevronTint: Color,
    val inactiveIconTint: Color,
    val activeIconTint: Color,
    val activePillColor: Color,
    val activePillBorderColor: Color = Color.Transparent,
)

/** Total vertical stride (row height + inter-row gap) in pixels for drag math. */
fun computeQueueItemStridePx(itemHeightPx: Int, rowGapPx: Int): Int =
    itemHeightPx.coerceAtLeast(0) + rowGapPx.coerceAtLeast(0)

/** Target queue index for a dragged row top offset given the full [itemStridePx]. */
fun computeDropTargetIndex(dragTopPx: Float, itemStridePx: Int, itemCount: Int): Int {
    if (itemCount <= 0 || itemStridePx <= 0 || !dragTopPx.isFinite()) return 0
    return (dragTopPx / itemStridePx.toFloat()).roundToInt().coerceIn(0, itemCount - 1)
}

/** Convenience overload computing stride from [itemHeightPx] and [rowGapPx]. */
fun computeDropTargetIndex(
    dragTopPx: Float,
    itemHeightPx: Int,
    rowGapPx: Int,
    itemCount: Int,
): Int = computeDropTargetIndex(
    dragTopPx = dragTopPx,
    itemStridePx = computeQueueItemStridePx(itemHeightPx, rowGapPx),
    itemCount = itemCount,
)

/**
 * Bottom `contentPadding` for `QueueList` so the final track scrolls clear of
 * the overlaid bottom controls, navigation bar inset, and breathing clearance.
 */
fun computeListBottomContentPaddingDp(
    controlsHeightDp: Int,
    navBarBottomInsetDp: Int,
    extraClearanceDp: Int = 8,
): Int = controlsHeightDp.coerceAtLeast(0) +
    navBarBottomInsetDp.coerceAtLeast(0) +
    extraClearanceDp.coerceAtLeast(0)

/**
 * Normalized `[startStop, endStop]` vertical gradient stops in `0f..1f` for the
 * `DstIn` bottom edge fade mask on `QueueList`, fading out across [fadeHeightPx]
 * at the top of the overlaid bottom controls above [navBarBottomPx].
 * Returns `null` when the viewport is too small to render a non-degenerate fade.
 */
fun computeQueueBottomFadeStops(
    viewportHeightPx: Float,
    fadeHeightPx: Float,
    bottomControlsHeightPx: Float,
    navBarBottomPx: Float,
): Pair<Float, Float>? {
    if (!viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return null
    val fade = fadeHeightPx.coerceAtLeast(0f)
    val controls = bottomControlsHeightPx.coerceAtLeast(0f)
    val nav = navBarBottomPx.coerceAtLeast(0f)
    if (fade <= 0f) return null
    val fadeStartPx = (viewportHeightPx - nav - controls).coerceIn(0f, viewportHeightPx)
    val fadeEndPx = (fadeStartPx + fade).coerceIn(fadeStartPx, viewportHeightPx)
    if (fadeEndPx <= fadeStartPx) return null
    val startStop = (fadeStartPx / viewportHeightPx).coerceIn(0f, 1f)
    val endStop = (fadeEndPx / viewportHeightPx).coerceIn(startStop, 1f)
    return if (endStop > startStop) startStop to endStop else null
}

private const val WHITE_ARGB = 0xFFFFFFFFL
private const val NEAR_BLACK_ARGB = 0xFF141414L
private const val BLACK_ARGB = 0xFF000000L

private fun Color.toPackedArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun ensureReadableToward(
    inkArgb: Long,
    targetArgb: Long,
    backgrounds: List<Long>,
    minRatio: Double,
): Long {
    if (backgrounds.isEmpty() || backgrounds.all { Contrast.ratio(inkArgb, it) >= minRatio }) {
        return inkArgb
    }
    var share = 0.05f
    while (share <= 1.001f) {
        val candidate = Contrast.blend(targetArgb, inkArgb, share)
        if (backgrounds.all { Contrast.ratio(candidate, it) >= minRatio }) {
            return candidate
        }
        share += 0.05f
    }
    if (backgrounds.all { Contrast.ratio(targetArgb, it) >= minRatio }) {
        return targetArgb
    }
    return Contrast.pickBestAcross(BLACK_ARGB, WHITE_ARGB, backgrounds)
}

private fun grayscaleForLuminance(l: Float): Long {
    val clamped = l.coerceIn(0f, 1f)
    val channel = if (clamped <= 0.0031308f) {
        (12.92f * clamped * 255f).toInt()
    } else {
        ((1.055 * Math.pow(clamped.toDouble(), 1.0 / 2.4) - 0.055) * 255.0).toInt()
    }.coerceIn(0, 255)
    return Contrast.argb(255, channel, channel, channel)
}

private fun blurBackdrops(
    scrimArgb: Long,
    isDark: Boolean,
    avgArtworkLuminance: Float? = null,
): List<Long> {
    val luminances = buildList {
        for (step in 0..20) add(step / 20f)
        if (avgArtworkLuminance != null && avgArtworkLuminance.isFinite()) {
            add(avgArtworkLuminance.coerceIn(0f, 1f))
        }
    }
    val result = ArrayList<Long>(1 + luminances.size * 2)
    result += scrimArgb
    for (l in luminances) {
        val art = grayscaleForLuminance(l)
        val scrimAlpha = BlurScrim.scrimAlpha(l, isDark)
        result += BlurScrim.compositeLayerColor(
            baseColorArgb = scrimArgb,
            artworkArgb = art,
            artworkAlpha = BlurScrim.artworkAlpha(l, isDark),
            scrimColorArgb = scrimArgb,
            scrimAlpha = scrimAlpha,
        )
        result += Contrast.blend(scrimArgb, art, scrimAlpha)
    }
    return result
}

/**
 * Resolves the highlighted current-song row colors for [style] (`Default`,
 * `Gradient`, or `Blur`) in Dark or Light mode, guaranteeing >= 4.5:1 text and
 * >= 3.0:1 icon contrast across the full artwork luminance range `[0f, 1f]`.
 */
fun resolveQueueHighlightStyle(
    style: BackgroundStyle,
    isDark: Boolean,
    colorScheme: ColorScheme,
    avgArtworkLuminance: Float? = null,
): QueueHighlightStyle = when (style) {
    BackgroundStyle.DEFAULT,
    BackgroundStyle.GRADIENT,
    -> {
        val container = colorScheme.secondaryContainer
        val containerArgb = container.toPackedArgb()
        val targetInk = if (Contrast.luminance(containerArgb) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE) {
            NEAR_BLACK_ARGB
        } else {
            WHITE_ARGB
        }
        val titleArgb = ensureReadableToward(
            inkArgb = colorScheme.onSecondaryContainer.toPackedArgb(),
            targetArgb = targetInk,
            backgrounds = listOf(containerArgb),
            minRatio = NowPlayingColors.TEXT_MIN_RATIO,
        )
        val titleColor = playingColor(titleArgb)
        val subtitleAlpha = listOf(
            QueuePolishSpecs.DEFAULT_SUBTITLE_ALPHA,
            0.88f,
            0.94f,
            1.0f,
        ).firstOrNull { alpha ->
            val composited = Contrast.blend(titleArgb, containerArgb, alpha)
            Contrast.ratio(composited, containerArgb) >= NowPlayingColors.TEXT_MIN_RATIO
        } ?: 1.0f
        val iconArgb = ensureReadableToward(
            inkArgb = titleArgb,
            targetArgb = targetInk,
            backgrounds = listOf(containerArgb),
            minRatio = NowPlayingColors.ICON_MIN_RATIO,
        )
        QueueHighlightStyle(
            containerColor = container,
            titleColor = titleColor,
            subtitleColor = titleColor.copy(alpha = subtitleAlpha),
            iconColor = playingColor(iconArgb),
            borderColor = Color.Transparent,
            borderAlpha = 0f,
        )
    }

    BackgroundStyle.BLUR -> {
        val scrimArgb = colorScheme.surface.toPackedArgb()
        val baseBlurBgs = blurBackdrops(scrimArgb, isDark, avgArtworkLuminance)
        val targetInk = if (isDark) WHITE_ARGB else NEAR_BLACK_ARGB
        val fillAlpha = if (isDark) {
            listOf(
                QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_DEFAULT,
                0.18f,
                QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_MIN,
                0.15f,
                QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_CLAMP_MIN,
            ).firstOrNull { alpha ->
                val quantizedAlpha = Color.White.copy(alpha = alpha).alpha
                val rowBgs = baseBlurBgs.map { Contrast.blend(WHITE_ARGB, it, quantizedAlpha) }
                rowBgs.all { Contrast.ratio(WHITE_ARGB, it) >= NowPlayingColors.TEXT_MIN_RATIO }
            } ?: QueuePolishSpecs.BLUR_DARK_FILL_ALPHA_CLAMP_MIN
        } else {
            listOf(
                QueuePolishSpecs.BLUR_LIGHT_FILL_ALPHA_DEFAULT,
                0.48f,
                QueuePolishSpecs.BLUR_LIGHT_FILL_ALPHA_MIN,
            ).firstOrNull { alpha ->
                val quantizedAlpha = Color.White.copy(alpha = alpha).alpha
                val rowBgs = baseBlurBgs.map { Contrast.blend(WHITE_ARGB, it, quantizedAlpha) }
                rowBgs.all { Contrast.ratio(NEAR_BLACK_ARGB, it) >= NowPlayingColors.TEXT_MIN_RATIO }
            } ?: QueuePolishSpecs.BLUR_LIGHT_FILL_ALPHA_MIN
        }
        val quantizedFillAlpha = Color.White.copy(alpha = fillAlpha).alpha
        val rowBgs = baseBlurBgs.map { Contrast.blend(WHITE_ARGB, it, quantizedFillAlpha) }
        val titleCandidate = if (isDark) WHITE_ARGB else colorScheme.onSurface.toPackedArgb()
        val titleArgb = ensureReadableToward(
            inkArgb = titleCandidate,
            targetArgb = targetInk,
            backgrounds = rowBgs,
            minRatio = NowPlayingColors.TEXT_MIN_RATIO,
        )
        val subCandidate = if (isDark) {
            Contrast.blend(WHITE_ARGB, colorScheme.onSurfaceVariant.toPackedArgb(), 0.6f)
        } else {
            colorScheme.onSurfaceVariant.toPackedArgb()
        }
        val subArgb = ensureReadableToward(
            inkArgb = subCandidate,
            targetArgb = targetInk,
            backgrounds = rowBgs,
            minRatio = NowPlayingColors.TEXT_MIN_RATIO,
        )
        val iconArgb = ensureReadableToward(
            inkArgb = titleArgb,
            targetArgb = targetInk,
            backgrounds = rowBgs,
            minRatio = NowPlayingColors.ICON_MIN_RATIO,
        )
        val borderColor = if (isDark) {
            Color.White.copy(alpha = QueuePolishSpecs.BLUR_BORDER_ALPHA)
        } else {
            colorScheme.outline.copy(alpha = QueuePolishSpecs.BLUR_BORDER_ALPHA)
        }
        QueueHighlightStyle(
            containerColor = Color.White.copy(alpha = fillAlpha),
            titleColor = playingColor(titleArgb),
            subtitleColor = playingColor(subArgb),
            iconColor = playingColor(iconArgb),
            borderColor = borderColor,
            borderAlpha = QueuePolishSpecs.BLUR_BORDER_ALPHA,
        )
    }
}

/**
 * Resolves transparent-container control colors for the overlaid queue bottom
 * bar across [BackgroundStyle.DEFAULT], [BackgroundStyle.GRADIENT], and
 * [BackgroundStyle.BLUR], guaranteeing >= 3.0:1 icon contrast in Dark & Light.
 */
fun resolveQueueBottomControlColors(
    style: BackgroundStyle,
    isDark: Boolean,
    colorScheme: ColorScheme,
    onSheetColor: Color,
): QueueBottomControlColors {
    val surfaceArgb = colorScheme.surface.toPackedArgb()
    val sheetBackdrops = when (style) {
        BackgroundStyle.DEFAULT -> listOf(
            surfaceArgb,
            colorScheme.surfaceContainer.toPackedArgb(),
        )
        BackgroundStyle.GRADIENT -> listOf(
            surfaceArgb,
            colorScheme.surfaceContainer.toPackedArgb(),
            colorScheme.surfaceContainerHigh.toPackedArgb(),
        )
        BackgroundStyle.BLUR -> blurBackdrops(surfaceArgb, isDark)
    }
    val targetInk = if (isDark) WHITE_ARGB else NEAR_BLACK_ARGB
    val chevronArgb = ensureReadableToward(
        inkArgb = onSheetColor.toPackedArgb(),
        targetArgb = targetInk,
        backgrounds = sheetBackdrops,
        minRatio = NowPlayingColors.ICON_MIN_RATIO,
    )
    val dimmedSeed = Contrast.blend(chevronArgb, surfaceArgb, 0.72f)
    val inactiveArgb = ensureReadableToward(
        inkArgb = dimmedSeed,
        targetArgb = targetInk,
        backgrounds = sheetBackdrops,
        minRatio = 3.1,
    )
    val (activePillColor, activeIconTint, activeBorder) = if (style == BackgroundStyle.BLUR) {
        val highlight = resolveQueueHighlightStyle(BackgroundStyle.BLUR, isDark, colorScheme)
        Triple(highlight.containerColor, highlight.iconColor, highlight.borderColor)
    } else {
        val pill = colorScheme.secondaryContainer
        val pillArgb = pill.toPackedArgb()
        val pillTarget = if (Contrast.luminance(pillArgb) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE) {
            NEAR_BLACK_ARGB
        } else {
            WHITE_ARGB
        }
        val activeArgb = ensureReadableToward(
            inkArgb = colorScheme.onSecondaryContainer.toPackedArgb(),
            targetArgb = pillTarget,
            backgrounds = listOf(pillArgb),
            minRatio = NowPlayingColors.ICON_MIN_RATIO,
        )
        Triple(pill, playingColor(activeArgb), Color.Transparent)
    }
    return QueueBottomControlColors(
        containerColor = Color.Transparent.copy(alpha = QueuePolishSpecs.BOTTOM_BAR_CONTAINER_ALPHA),
        chevronTint = playingColor(chevronArgb),
        inactiveIconTint = playingColor(inactiveArgb),
        activeIconTint = activeIconTint,
        activePillColor = activePillColor,
        activePillBorderColor = activeBorder,
    )
}

// ---- Phase 11.2 Section A: Collapsed Queue Strip & Lyrics Clearance --------

/** Resolved drag-handle pill and "Playing from ..." label colors for the collapsed queue strip. */
data class CollapsedStripColors(
    val handleColor: Color,
    val textColor: Color,
)

/**
 * Opacity of the Queue sheet's own surface layer as it morphs open from the
 * transparent collapsed strip (`0f` at `<= 0f` or non-finite, strictly monotonic
 * across `0f..QUEUE_SURFACE_FADE_IN_END_PROGRESS`, `1f` for `>= 0.25f`).
 */
fun queueSheetSurfaceAlpha(progress: Float): Float {
    if (!progress.isFinite() || progress <= 0f) return 0f
    val end = QueuePolishSpecs.QUEUE_SURFACE_FADE_IN_END_PROGRESS
    if (progress >= end) return 1f
    return (progress / end).coerceIn(0f, 1f)
}

/**
 * Total bottom clearance (in dp) reserved below `LyricsPanel` so lyrics stop
 * strictly above the collapsed queue strip and system navigation bar inset.
 */
fun computeLyricsBottomClearanceDp(
    stripHeightDp: Int = 80,
    navBarBottomInsetDp: Int = 0,
): Int = stripHeightDp.coerceAtLeast(0) + navBarBottomInsetDp.coerceAtLeast(0)

/**
 * Alpha multiplier produced by `Modifier.lyricsEdgeFade()` at vertical coordinate
 * [yInPanelPx] inside a lyrics panel of height [panelHeightPx] with top/bottom
 * edge fade [edgeFadeHeightPx]. Reaches `0f` at `yInPanelPx >= panelHeightPx`.
 */
fun computeLyricsEdgeFadeAlpha(
    yInPanelPx: Float,
    panelHeightPx: Float,
    edgeFadeHeightPx: Float,
): Float {
    if (!yInPanelPx.isFinite() || !panelHeightPx.isFinite() || !edgeFadeHeightPx.isFinite()) return 0f
    if (panelHeightPx <= 0f) return 0f
    if (yInPanelPx <= 0f || yInPanelPx >= panelHeightPx) return 0f
    val fade = edgeFadeHeightPx.coerceIn(0f, panelHeightPx / 2f)
    if (fade <= 0f) return 1f
    val topAlpha = (yInPanelPx / fade).coerceIn(0f, 1f)
    val bottomAlpha = ((panelHeightPx - yInPanelPx) / fade).coerceIn(0f, 1f)
    return minOf(topAlpha, bottomAlpha)
}

/**
 * Resolves the collapsed queue strip's drag-handle pill and "Playing from ..." text colors
 * over the continuous full-screen Player backdrop ([playerStyle]), guaranteeing >= 4.5:1
 * text contrast and >= 3.1:1 handle contrast across Dark & Light themes and artwork luminances.
 */
fun resolveCollapsedStripColors(
    playerStyle: BackgroundStyle,
    isDark: Boolean,
    colorScheme: ColorScheme,
    playerSurfaceColor: Color,
    contentColor: Color,
    dimColor: Color,
    avgArtworkLuminance: Float? = null,
): CollapsedStripColors {
    val playerSurfaceArgb = playerSurfaceColor.toPackedArgb()
    val schemeSurfaceArgb = colorScheme.surface.toPackedArgb()
    val playerIsLight = Contrast.luminance(playerSurfaceArgb) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE
    val targetInk = when (playerStyle) {
        BackgroundStyle.BLUR -> if (isDark) WHITE_ARGB else NEAR_BLACK_ARGB
        BackgroundStyle.DEFAULT,
        BackgroundStyle.GRADIENT,
        -> if (playerIsLight) NEAR_BLACK_ARGB else WHITE_ARGB
    }

    fun List<Long>.addIfSamePolarity(argb: Long): List<Long> =
        if ((Contrast.luminance(argb) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE) == playerIsLight) {
            this + argb
        } else {
            this
        }

    val backdrops = when (playerStyle) {
        BackgroundStyle.DEFAULT -> listOf(playerSurfaceArgb)
            .addIfSamePolarity(schemeSurfaceArgb)
            .addIfSamePolarity(colorScheme.surfaceContainer.toPackedArgb())
            .addIfSamePolarity(colorScheme.background.toPackedArgb())
            .distinct()

        BackgroundStyle.GRADIENT -> listOf(playerSurfaceArgb)
            .addIfSamePolarity(schemeSurfaceArgb)
            .addIfSamePolarity(colorScheme.background.toPackedArgb())
            .addIfSamePolarity(colorScheme.surfaceContainerLowest.toPackedArgb())
            .addIfSamePolarity(colorScheme.surfaceContainer.toPackedArgb())
            .addIfSamePolarity(colorScheme.surfaceContainerHigh.toPackedArgb())
            .distinct()

        BackgroundStyle.BLUR -> buildList {
            add(playerSurfaceArgb)
            val playerLum = Contrast.luminance(playerSurfaceArgb)
            val isBaseSurface = if (isDark) playerLum <= 0.08 else playerLum >= 0.65
            if (isBaseSurface) {
                addAll(blurBackdrops(playerSurfaceArgb, isDark, avgArtworkLuminance))
            }
            if (schemeSurfaceArgb != playerSurfaceArgb || !isBaseSurface) {
                if ((Contrast.luminance(schemeSurfaceArgb) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE) == !isDark) {
                    addAll(blurBackdrops(schemeSurfaceArgb, isDark, avgArtworkLuminance))
                }
            }
        }
    }

    val contentOpaqueArgb = if (contentColor.alpha >= 0.999f) {
        contentColor.toPackedArgb()
    } else {
        Contrast.blend(contentColor.toPackedArgb(), playerSurfaceArgb, contentColor.alpha)
    }
    val dimOpaqueArgb = if (dimColor.alpha >= 0.999f) {
        dimColor.toPackedArgb()
    } else {
        Contrast.blend(dimColor.toPackedArgb(), playerSurfaceArgb, dimColor.alpha)
    }

    val textArgb = ensureReadableToward(
        inkArgb = dimOpaqueArgb,
        targetArgb = targetInk,
        backgrounds = backdrops,
        minRatio = NowPlayingColors.TEXT_MIN_RATIO,
    )
    val handleSeed = Contrast.blend(contentOpaqueArgb, playerSurfaceArgb, 0.55f)
    val handleArgb = ensureReadableToward(
        inkArgb = handleSeed,
        targetArgb = targetInk,
        backgrounds = backdrops,
        minRatio = 3.1,
    )
    return CollapsedStripColors(
        handleColor = playingColor(handleArgb),
        textColor = playingColor(textArgb),
    )
}
