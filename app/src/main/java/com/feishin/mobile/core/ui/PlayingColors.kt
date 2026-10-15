package com.feishin.mobile.core.ui

/**
 * One artwork-derived tonal set. Every text/background pair in here is
 * verified to its stated ratio at construction (see [schemeFor`), so
 * composables can use these colors without re-checking:
 *
 * - [onContainer] vs [container]: >= 4.5 (titles)
 * - [secondary] vs [container]: >= 4.5 (artist lines, durations)
 * - [primary] vs [container] and vs [track]: >= 3.0 (ring, active icons)
 * - [onPrimary] vs [primary]: >= 4.5 (text on the Save pill)
 * - [surfaceText] vs [surfaceTop]/[surfaceBottom]: >= 4.5 (text straight on
 *   the queue / Now Playing gradient)
 * - [surfaceSecondary] vs [surfaceTop]/[surfaceBottom]: >= 4.5
 *
 * [track] is the ring's faint track: blended 82% toward [container], so the
 * enforced primary-vs-container ratio carries over to it.
 *
 * [miniContainer] is the mini player's fill, which is deliberately much darker
 * than [container] (the row highlight) — see [DarkToneSpec]. [miniOutline] is
 * the 1dp hairline that keeps the pill distinct from the page background.
 */
data class PlayingScheme(
    /** Null when this is the theme fallback rather than artwork. */
    val seedArgb: Long?,
    val container: Long,
    val onContainer: Long,
    val secondary: Long,
    val primary: Long,
    val onPrimary: Long,
    val track: Long,
    val surfaceTop: Long,
    val surfaceBottom: Long,
    val surfaceText: Long,
    val surfaceSecondary: Long,
    val dark: Boolean,
    /** Mini player fill: darker than [container] in Dark, same in Light. */
    val miniContainer: Long = container,
    /** Mini player hairline, so the pill reads as a shape on the background. */
    val miniOutline: Long = primary,
)

/**
 * Builds a [PlayingScheme] from an artwork seed color.
 *
 * No material-kolor / material-color-utilities dependency is used on purpose:
 * neither is in the version catalog, and the whole engine is ~120 lines of
 * HSL plus WCAG enforcement — small enough to own, test and tune, with zero
 * version-compatibility risk. The contrast loops below are what guarantee the
 * hard readability requirement, not the hue source.
 *
 * @param seedArgb cover seed (vibrant preferred, else dominant); null or gray
 *        (saturation < [GRAY_SATURATION]) falls back to [fallbackSeedArgb]
 *        when that is usable, else to the theme pair.
 * @param fallbackSeedArgb the app theme's primary: a gray cover still gets a
 *        tinted scheme from the accent instead of going fully neutral.
 * @param fallbackContainer / [fallbackOnContainer] the app theme's
 *        primaryContainer / onPrimaryContainer, used when no seed is usable
 *        (and the "Dynamic Now Playing color" setting is on but there is no
 *        art, or the accent itself is gray).
 */
object PlayingColors {

    /** Below this saturation a seed is "gray" and unusable. */
    const val GRAY_SATURATION = 0.12f

    /**
     * Ring track: the container tinted 18% toward the primary. If that lands
     * too close to the primary (ratio < 3.05), the tint is halved toward the
     * container until it passes — at 0% the ratio equals primary-vs-container,
     * which is enforced >= 3.0, so this always terminates passing.
     */
    fun ringTrack(container: Long, primary: Long): Long {
        var share = 0.18f
        var track = Contrast.blend(primary, container, share)
        var guard = 0
        while (Contrast.ratio(primary, track) < 3.05 && guard < 8) {
            share /= 2f
            track = Contrast.blend(primary, container, share)
            guard++
        }
        return track
    }

    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L
    private const val NEAR_BLACK = 0xFF141414L

    fun schemeFor(
        seedArgb: Long?,
        fallbackContainer: Long,
        fallbackOnContainer: Long,
        dark: Boolean,
        fallbackSeedArgb: Long? = null,
    ): PlayingScheme {
        val usable = { c: Long -> Contrast.saturationOf(c) >= GRAY_SATURATION }
        val seed = when {
            seedArgb != null && usable(seedArgb) -> seedArgb
            fallbackSeedArgb != null && usable(fallbackSeedArgb) -> fallbackSeedArgb
            else -> null
        }
        if (seed == null) {
            return fallbackScheme(fallbackContainer, fallbackOnContainer, dark)
        }
        val hsl = Contrast.toHsl(seed)
        return if (dark) darkScheme(seed, hsl.h, hsl.s) else lightScheme(seed, hsl.h, hsl.s)
    }

    // ---- fallback (theme pair, still verified) ------------------------------

    private fun fallbackScheme(container: Long, onContainer: Long, dark: Boolean): PlayingScheme {
        // One of black/white always clears 4.5, so exactly one of the two
        // branches below is live; the theme pair is preferred when it passes.
        val fixedOn = if (Contrast.ratio(onContainer, container) >= 4.5) {
            onContainer
        } else {
            Contrast.pickBest(BLACK, WHITE, container)
        }
        // onContainer at 4.5+ always clears 3.0, so primary is safe as-is.
        val fixedPrimary = fixedOn
        val top = if (dark) Contrast.fromHsl(0f, 0f, 0.10f) else Contrast.fromHsl(0f, 0f, 0.96f)
        val bottom = if (dark) Contrast.fromHsl(0f, 0f, 0.06f) else Contrast.fromHsl(0f, 0f, 0.91f)
        val surfaceText = if (dark) WHITE else NEAR_BLACK
        // No usable hue (gray or missing art): the whole scheme is the app
        // theme, untouched — exactly what the "Dynamic Now Playing color"
        // setting off produces, so both neutral paths look identical.
        return PlayingScheme(
            seedArgb = null,
            container = container,
            onContainer = fixedOn,
            secondary = fixedOn,
            primary = fixedPrimary,
            onPrimary = Contrast.pickBest(BLACK, WHITE, fixedPrimary),
            track = ringTrack(container, fixedPrimary),
            surfaceTop = top,
            surfaceBottom = bottom,
            surfaceText = surfaceText,
            surfaceSecondary = surfaceText,
            dark = dark,
        )
    }

    // ---- dark theme ----------------------------------------------------------
    //
    // Every tone/chroma comes from DarkToneSpec so the dark look is tunable in
    // one place. Nothing here uses the seed's own lightness: the seed only
    // supplies the HUE, and the tone/chroma caps decide how dark and how
    // saturated each surface is. That is what keeps a vivid cover from painting
    // a neon highlight.

    /** Tone/chroma with the seed's hue, chroma never above [cap]. */
    private fun toned(hue: Double, tone: Float, cap: Float, seedChroma: Double): Long =
        Contrast.fromToneChroma(hue, tone, minOf(cap.toDouble(), seedChroma).toFloat())

    /** Lightens (or darkens) in 2-tone steps until [minRatio] vs [bg] passes. */
    private fun enforce(
        hue: Double,
        startTone: Float,
        chroma: Float,
        bg: Long,
        minRatio: Double,
        lighter: Boolean,
    ): Long {
        var tone = startTone
        repeat(30) {
            val c = Contrast.fromToneChroma(hue, tone, chroma)
            if (Contrast.ratio(c, bg) >= minRatio) return c
            tone = if (lighter) (tone + 2f).coerceAtMost(100f) else (tone - 2f).coerceAtLeast(0f)
        }
        // Nothing at this tone/chroma works: black or white always does.
        return Contrast.pickBest(BLACK, WHITE, bg)
    }

    private fun darkScheme(seed: Long, h: Float, s: Float): PlayingScheme {
        val hue = Contrast.labHue(seed)
        val seedChroma = Contrast.chromaOf(seed)

        // Highlight row: a deep, softly tinted surface (tone 22, chroma <= 22).
        val container = toned(hue, DarkToneSpec.HIGHLIGHT_TONE, DarkToneSpec.HIGHLIGHT_CHROMA, seedChroma)
        // Text on it: light and near-neutral, contrast enforced not assumed.
        val onContainer = enforce(
            hue, DarkToneSpec.ON_HIGHLIGHT_TONE, DarkToneSpec.ON_HIGHLIGHT_CHROMA,
            container, 4.5, lighter = true,
        )
        val secondary = enforce(
            hue, DarkToneSpec.SECONDARY_TONE, DarkToneSpec.SECONDARY_CHROMA,
            container, 4.5, lighter = true,
        )
        // Accent (ring, mini icons/title, queue toggles, Save pill): tone ~75
        // with a capped chroma, so it reads without glowing.
        val primary = enforce(
            hue, DarkToneSpec.ACCENT_TONE, DarkToneSpec.ACCENT_CHROMA,
            container, 3.0, lighter = true,
        )
        // Ring track: a faint groove; the ring must clear 3:1 on it.
        val track = enforce(
            hue, DarkToneSpec.RING_TRACK_TONE, DarkToneSpec.RING_TRACK_CHROMA,
            primary, 3.0, lighter = false,
        )
        // Mini player: nearly black with a hint of the hue, plus a hairline so
        // it stays a distinct shape against the page background and nav bar.
        val miniContainer = toned(hue, DarkToneSpec.MINI_TONE, DarkToneSpec.MINI_CHROMA, seedChroma)
        val miniOutline = toned(hue, DarkToneSpec.MINI_OUTLINE_TONE, DarkToneSpec.MINI_OUTLINE_CHROMA, seedChroma)
        // Full-screen gradients: darker than the highlight, top slightly lighter
        // than the bottom, same muted chroma.
        val top = toned(hue, DarkToneSpec.SURFACE_TOP_TONE, DarkToneSpec.SURFACE_CHROMA, seedChroma)
        val bottom = toned(hue, DarkToneSpec.SURFACE_BOTTOM_TONE, DarkToneSpec.SURFACE_CHROMA, seedChroma)
        val surfaceText = enforce(
            hue, DarkToneSpec.SURFACE_TEXT_TONE, DarkToneSpec.SURFACE_TEXT_CHROMA,
            top, 4.5, lighter = true,
        )
        val surfaceSecondary = enforce(
            hue, DarkToneSpec.SURFACE_SECONDARY_TONE, DarkToneSpec.SURFACE_SECONDARY_CHROMA,
            bottom, 4.5, lighter = true,
        ).let { if (Contrast.ratio(it, top) >= 4.5) it else surfaceText }

        return PlayingScheme(
            seedArgb = seed,
            container = container,
            onContainer = onContainer,
            secondary = secondary,
            primary = primary,
            onPrimary = Contrast.pickBest(BLACK, WHITE, primary),
            track = track,
            surfaceTop = top,
            surfaceBottom = bottom,
            surfaceText = surfaceText,
            surfaceSecondary = surfaceSecondary,
            dark = true,
            miniContainer = miniContainer,
            miniOutline = miniOutline,
        )
    }

    // ---- light theme ----------------------------------------------------------

    private fun lightScheme(seed: Long, h: Float, s: Float): PlayingScheme {
        val sat = s.coerceAtMost(0.55f)
        // Container: pale seed tint. Adjusted below if the text fails.
        var container = Contrast.fromHsl(h, sat, 0.88f)
        // On-container: deep seed tone first, near-black as the backstop.
        var onContainer = Contrast.fromHsl(h, 0.35f, 0.10f)
        var guard = 0
        while (Contrast.ratio(onContainer, container) < 4.5 && guard < 16) {
            if (Contrast.ratio(NEAR_BLACK, container) >= 4.5) {
                onContainer = NEAR_BLACK
                break
            }
            // Even near-black fails: the container is too dark, lighten it.
            container = Contrast.withLightness(container, Contrast.toHsl(container).l + 0.02f)
            guard++
        }
        if (Contrast.ratio(onContainer, container) < 4.5) onContainer = NEAR_BLACK
        val secondary = Contrast.blend(container, onContainer, 0.30f)
            .let { if (Contrast.ratio(it, container) >= 4.5) it else onContainer }
        // Primary: deep seed tone, darkened until it clears 3:1.
        var primary = Contrast.fromHsl(h, sat.coerceAtLeast(0.50f), 0.35f)
        guard = 0
        while (Contrast.ratio(primary, container) < 3.0 && guard < 16) {
            primary = Contrast.withLightness(primary, Contrast.toHsl(primary).l - 0.03f)
            guard++
        }
        if (Contrast.ratio(primary, container) < 3.0) primary = onContainer
        val track = ringTrack(container, primary)
        // Gradient surfaces: light seed tones, lightened until near-black text
        // clears 4.5 on both ends.
        var top = Contrast.fromHsl(h, sat, 0.93f)
        guard = 0
        while (Contrast.ratio(NEAR_BLACK, top) < 4.5 && guard < 16) {
            top = Contrast.withLightness(top, Contrast.toHsl(top).l + 0.01f)
            guard++
        }
        var bottom = Contrast.fromHsl(h, sat, 0.86f)
        guard = 0
        while (Contrast.ratio(NEAR_BLACK, bottom) < 4.5 && guard < 16) {
            bottom = Contrast.withLightness(bottom, Contrast.toHsl(bottom).l + 0.01f)
            guard++
        }
        val surfaceSecondary = Contrast.blend(top, NEAR_BLACK, 0.30f)
            .let {
                if (Contrast.ratio(it, top) >= 4.5 && Contrast.ratio(it, bottom) >= 4.5) it else NEAR_BLACK
            }
        return PlayingScheme(
            seedArgb = seed,
            container = container,
            onContainer = onContainer,
            secondary = secondary,
            primary = primary,
            onPrimary = Contrast.pickBest(BLACK, WHITE, primary),
            track = ringTrack(container, primary),
            surfaceTop = top,
            surfaceBottom = bottom,
            surfaceText = NEAR_BLACK,
            surfaceSecondary = surfaceSecondary,
            dark = false,
        )
    }
}
