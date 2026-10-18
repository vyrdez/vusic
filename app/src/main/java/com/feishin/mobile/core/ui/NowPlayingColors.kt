package com.feishin.mobile.core.ui

/**
 * Every colour Now Playing paints with, resolved in one place.
 *
 * Why this exists: the screen used to read its colours from two independent
 * systems. With the "Dynamic Now Playing color" setting ON it used an
 * artwork-derived [PlayingScheme]; with the setting OFF it fell back to
 * hardcoded literals - `Color.White` for every piece of content, and a gradient
 * whose stops came from the cover art. In the Light theme that second path
 * painted white text on a near-white surface ("Playing from ..." became
 * unreadable) while still tinting the background from artwork the user had
 * explicitly asked not to be used.
 *
 * Both paths now produce this one struct, and [NowPlayingColors.static] never
 * looks at artwork. It also never assumes a colour: the content ink is derived
 * from the measured luminance of the surface behind it (see [contentFor]) and
 * every pair is checked against [Contrast.ratio] before it is returned.
 *
 * All values are packed `0xAARRGGBB` Longs so this file stays free of any
 * Android/Compose dependency and can be unit-tested on the JVM.
 */
data class NowPlayingPalette(
    /** Top stop of the full-screen gradient (directly under the status bar). */
    val backdropTop: Long,
    /** Bottom stop of the full-screen gradient. */
    val backdropBottom: Long,
    /** Primary ink: titles, transport icons, the queue peek strip. */
    val content: Long,
    /** Secondary ink: artist lines, durations, counts. Still >= 4.5:1. */
    val dim: Long,
    /** One accent for the whole screen: play button, toggles, Save pill. */
    val accent: Long,
    /** Ink on top of [accent]. */
    val onAccent: Long,
    /** Queue sheet gradient. */
    val sheetTop: Long,
    val sheetBottom: Long,
    val sheetContent: Long,
    val sheetDim: Long,
    /** Background of the "now playing" row in the queue. */
    val rowHighlight: Long,
    val rowHighlightContent: Long,
    val rowHighlightDim: Long,
    /** Opaque fill behind the queue's bottom controls. */
    val scrim: Long,
    /** Track groove behind the mini player's progress ring. */
    val ringTrack: Long,
) {
    /**
     * True when the backdrop behind the status bar is a LIGHT surface, so the
     * system status/navigation icons must be dark. Derived from measured
     * luminance rather than from the theme flag, because a tinted Now Playing
     * can be light even in the Dark theme and vice versa.
     */
    val lightBackdrop: Boolean
        get() = Contrast.luminance(backdropTop) > NowPlayingColors.LIGHT_SURFACE_LUMINANCE
}

object NowPlayingColors {

    /** WCAG floor for body text anywhere on Now Playing. */
    const val TEXT_MIN_RATIO = 4.5

    /** WCAG floor for icons and control edges. */
    const val ICON_MIN_RATIO = 3.0

    /**
     * Relative luminance above which a surface counts as light and therefore
     * takes dark content. 0.18 is roughly where white-on-black stops being the
     * better of the two options (mid grey).
     */
    const val LIGHT_SURFACE_LUMINANCE = 0.18

    private const val WHITE = 0xFFFFFFFFL
    private const val NEAR_BLACK = 0xFF141414L

    /**
     * The content ink for [background], chosen from its measured luminance and
     * never from a hardcoded literal: dark ink on a light surface, light ink on
     * a dark one.
     */
    fun contentFor(background: Long): Long =
        if (Contrast.luminance(background) > LIGHT_SURFACE_LUMINANCE) NEAR_BLACK else WHITE

    /**
     * Returns [ink] if it already clears [minRatio] on every background, else
     * the nearest blend toward the extreme that always wins on them. Falls back
     * to that extreme outright if blending never gets there, so this cannot
     * return an unreadable pair.
     */
    fun ensureReadable(ink: Long, backgrounds: List<Long>, minRatio: Double, fallback: Long): Long {
        if (backgrounds.all { Contrast.ratio(ink, it) >= minRatio }) return ink
        val target = if (backgrounds.any { Contrast.luminance(it) > LIGHT_SURFACE_LUMINANCE }) {
            NEAR_BLACK
        } else {
            WHITE
        }
        // Fine steps: the window in which a mid-grey ink clears 4.5:1 against
        // two nearby surfaces is only a few thousandths of luminance wide, so a
        // coarse first step walks straight past it.
        var share = 0.05f
        while (share <= 1.001f) {
            val candidate = Contrast.blend(target, ink, share)
            if (backgrounds.all { Contrast.ratio(candidate, it) >= minRatio }) return candidate
            share += 0.05f
        }
        return target
    }

    /**
     * The palette used when dynamic Now Playing color is OFF, and also when it
     * is ON but no artwork scheme could be built (no art, or a cover so gray it
     * has no hue to work with).
     *
     * Nothing here reads artwork: the backdrop is a gradient between two theme
     * surfaces, and every ink is a theme role verified against it. In the Light
     * theme that means a light surface with dark content; in Dark, a dark
     * surface with light content.
     *
     * @param backdropEnd the bottom gradient stop. Callers pass the theme's
     *        darkest (Dark) or lightest (Light) surface so the gradient always
     *        runs *away* from the content ink.
     */
    fun static(
        background: Long,
        backdropEnd: Long,
        onBackground: Long,
        onSurface: Long,
        onSurfaceVariant: Long,
        surface: Long,
        surfaceContainer: Long,
        primary: Long,
        onPrimary: Long,
        primaryContainer: Long,
        onPrimaryContainer: Long,
        track: Long,
    ): NowPlayingPalette {
        val backdrops = listOf(background, backdropEnd)
        val content = ensureReadable(onBackground, backdrops, TEXT_MIN_RATIO, contentFor(background))
        val dim = ensureReadable(onSurfaceVariant, backdrops, TEXT_MIN_RATIO, content)
        val accent = ensureReadable(primary, backdrops, ICON_MIN_RATIO, content)
        val onAccent = ensureReadable(onPrimary, listOf(accent), TEXT_MIN_RATIO, contentFor(accent))
        val sheetBackdrops = listOf(surface, surfaceContainer)
        val sheetContent = ensureReadable(onSurface, sheetBackdrops, TEXT_MIN_RATIO, contentFor(surface))
        val sheetDim = ensureReadable(onSurfaceVariant, sheetBackdrops, TEXT_MIN_RATIO, sheetContent)
        return NowPlayingPalette(
            backdropTop = background,
            backdropBottom = backdropEnd,
            content = content,
            dim = dim,
            accent = accent,
            onAccent = onAccent,
            sheetTop = surface,
            sheetBottom = surfaceContainer,
            sheetContent = sheetContent,
            sheetDim = sheetDim,
            rowHighlight = primaryContainer,
            rowHighlightContent = ensureReadable(
                onPrimaryContainer, listOf(primaryContainer), TEXT_MIN_RATIO, contentFor(primaryContainer),
            ),
            rowHighlightDim = ensureReadable(
                Contrast.blend(NEAR_BLACK, onPrimaryContainer, 0.55f),
                listOf(primaryContainer), TEXT_MIN_RATIO, contentFor(primaryContainer),
            ),
            scrim = surfaceContainer,
            ringTrack = ensureReadable(track, listOf(accent), ICON_MIN_RATIO, accent),
        )
    }

    /**
     * The palette for the artwork-derived scheme. Built from [PlayingScheme],
     * which already enforces its own contrast pairs; the checks here are the
     * ones the scheme cannot make on its own, namely that the *surface* inks
     * clear the bar on BOTH gradient stops and on the queue sheet's own stops.
     */
    fun fromScheme(scheme: PlayingScheme): NowPlayingPalette {
        val backdrops = listOf(scheme.surfaceTop, scheme.surfaceBottom)
        val content = ensureReadable(
            scheme.surfaceText, backdrops, TEXT_MIN_RATIO, contentFor(scheme.surfaceTop),
        )
        val dim = ensureReadable(
            scheme.surfaceSecondary, backdrops, TEXT_MIN_RATIO, content,
        )
        val accent = ensureReadable(scheme.primary, backdrops, ICON_MIN_RATIO, content)
        val onAccent = ensureReadable(
            scheme.onPrimary, listOf(accent), TEXT_MIN_RATIO, contentFor(accent),
        )
        return NowPlayingPalette(
            backdropTop = scheme.surfaceTop,
            backdropBottom = scheme.surfaceBottom,
            content = content,
            dim = dim,
            accent = accent,
            onAccent = onAccent,
            sheetTop = scheme.surfaceTop,
            sheetBottom = scheme.surfaceBottom,
            sheetContent = content,
            sheetDim = dim,
            rowHighlight = scheme.container,
            rowHighlightContent = ensureReadable(
                scheme.onContainer, listOf(scheme.container), TEXT_MIN_RATIO, contentFor(scheme.container),
            ),
            rowHighlightDim = ensureReadable(
                scheme.secondary, listOf(scheme.container), TEXT_MIN_RATIO, contentFor(scheme.container),
            ),
            scrim = scheme.surfaceBottom,
            ringTrack = ensureReadable(scheme.track, listOf(accent), ICON_MIN_RATIO, accent),
        )
    }

    /**
     * The pre-dynamic-colour fallback that is still used when dynamic color is
     * ON but produced no scheme: a cover-derived dark gradient. Kept because
     * turning the setting on must not change how a track with no usable artwork
     * looks. The inks are still picked from luminance, so this path is
     * readable too.
     */
    fun legacyCoverGradient(top: Long, mid: Long, bottom: Long): NowPlayingPalette {
        val backdrops = listOf(top, mid, bottom)
        val content = ensureReadable(contentFor(top), backdrops, TEXT_MIN_RATIO, contentFor(top))
        val dim = ensureReadable(
            Contrast.blend(content, top, 0.3f), backdrops, TEXT_MIN_RATIO, content,
        )
        val accent = ensureReadable(content, backdrops, ICON_MIN_RATIO, content)
        return NowPlayingPalette(
            backdropTop = top,
            backdropBottom = bottom,
            content = content,
            dim = dim,
            accent = accent,
            onAccent = ensureReadable(contentFor(accent), listOf(accent), TEXT_MIN_RATIO, contentFor(accent)),
            sheetTop = top,
            sheetBottom = bottom,
            sheetContent = content,
            sheetDim = dim,
            rowHighlight = mid,
            rowHighlightContent = content,
            rowHighlightDim = dim,
            scrim = bottom,
            ringTrack = ensureReadable(
                Contrast.blend(content, top, 0.25f), backdrops, ICON_MIN_RATIO, accent,
            ),
        )
    }

    /**
     * A de-emphasised ink for inactive rows (lyrics lines, played queue rows):
     * pushed toward the background but never past [TEXT_MIN_RATIO], so
     * "dimmer" never means "unreadable".
     */
    fun deEmphasise(ink: Long, background: Long): Long =
        ensureReadable(
            Contrast.blend(background, ink, 0.45f), listOf(background), TEXT_MIN_RATIO, ink,
        )
}