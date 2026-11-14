package com.feishin.mobile.core.ui

/**
 * One artwork-derived tonal set. Every text/background pair in here is
 * verified to its stated ratio at construction (see [PlayingColors.schemeFor]
 * and [PlayingColors.buildMaterialScheme]), so composables can use these
 * colors without re-checking:
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
 * than [container] (the row highlight) in Dark mode and a tinted card (`T=91.0`)
 * in Light mode — see [DarkToneSpec] and [LightTintSpec]. [miniOutline] is the
 * 1dp hairline that keeps the pill distinct from the page background.
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
    /** Mini player fill: darker than [container] in Dark, pale card in Light. */
    val miniContainer: Long = container,
    /** Mini player hairline, so the pill reads as a shape on the background. */
    val miniOutline: Long = primary,
) {
    companion object {
        /** Adapts a root [VusicDynamicScheme] into a [PlayingScheme] for MiniPlayer, TrackRow, and NowPlaying. */
        fun fromDynamic(scheme: VusicDynamicScheme): PlayingScheme = PlayingScheme(
            seedArgb = scheme.seedArgb,
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            secondary = scheme.rowHighlightSecondary,
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            track = scheme.ringTrack,
            surfaceTop = scheme.surfaceTop,
            surfaceBottom = scheme.surfaceBottom,
            surfaceText = scheme.surfaceText,
            surfaceSecondary = scheme.surfaceSecondary,
            dark = scheme.dark,
            miniContainer = scheme.miniContainer,
            miniOutline = scheme.miniOutline,
        )
    }
}

/**
 * Pure-JVM full Material 3 + Vusic control & player color scheme derived from
 * an artwork seed color (or static Dark/Light defaults).
 *
 * All values are packed `0xFFRRGGBBL` Longs with zero `android.*` / Compose
 * dependencies so the entire scheme and contrast guards are unit-testable on
 * the JVM.
 */
data class VusicDynamicScheme(
    val seedArgb: Long?,
    val dark: Boolean,
    // Material 3 core roles
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val surfaceContainerLowest: Long,
    val surfaceContainerLow: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val outline: Long,
    val outlineVariant: Long,
    val error: Long,
    val onError: Long,
    // Vusic control tokens
    val switchOffTrack: Long,
    val switchOffOutline: Long,
    val switchOffThumb: Long,
    val disabledTrack: Long,
    val disabledOutline: Long,
    val disabledThumb: Long,
    val sliderInactiveTrack: Long,
    val chipOffFill: Long,
    val chipOffText: Long,
    // Player & gradient roles
    val miniContainer: Long,
    val miniOutline: Long,
    val ringTrack: Long,
    val surfaceTop: Long,
    val surfaceBottom: Long,
    val surfaceText: Long,
    val surfaceSecondary: Long,
    val rowHighlightSecondary: Long,
) {
    fun toPlayingScheme(): PlayingScheme = PlayingScheme.fromDynamic(this)
}

/**
 * Every tone (CIELAB L*) and chroma cap for the LIGHT artwork tint, in one
 * place so the look can be tuned without hunting through [PlayingColors].
 *
 * Terms, matching [DarkToneSpec]:
 * - **tone** is perceptual lightness L* (0 = black, 100 = white).
 * - **chroma** is CIELAB C*ab distance from neutral gray (0 = pure gray).
 *
 * Light surfaces use a calm, clearly visible pastel tint of the song color
 * (~94.5 background, ~90-92 surface cards) while neutral artwork (white, gray,
 * black) stays neutral gray.
 */
object LightTintSpec {

    // ---- Chroma scaling bounds ---------------------------------------------

    /** Seeds below this CIELAB chroma C*ab stay neutral gray (target chroma = 0). */
    const val NEUTRAL_CHROMA_FLOOR = 4.0

    /** Seed chroma at which the smooth tint scaling factor alpha reaches 1.0. */
    const val FULL_CHROMA_PIVOT = 18.0

    // ---- Page background & base surface ------------------------------------

    /** Page background tone (clearly tinted off-white without washing out). */
    const val BACKGROUND_TONE = 94.5f

    /** Page background chroma cap (within sRGB gamut). */
    const val BACKGROUND_CHROMA = 12.0f

    /** Primary surface tone, matching [BACKGROUND_TONE]. */
    const val SURFACE_TONE = 94.5f

    /** Primary surface chroma cap. */
    const val SURFACE_CHROMA = 12.0f

    // ---- Surface containers & cards (a notch darker than background) -------

    /** Lowest container tone (brightest elevated sheet/tile). */
    const val CONTAINER_LOWEST_TONE = 96.5f
    const val CONTAINER_LOWEST_CHROMA = 8.0f

    /** Low container tone (subtle card step below background). */
    const val CONTAINER_LOW_TONE = 92.0f
    const val CONTAINER_LOW_CHROMA = 13.0f

    /** Default container tone (cards, nav bar, surfaceContainer). */
    const val CONTAINER_TONE = 91.0f
    const val CONTAINER_CHROMA = 14.5f

    /** Mini player fill tone & chroma (aligned with [CONTAINER_TONE]). */
    const val MINI_CONTAINER_TONE = 91.0f
    const val MINI_CONTAINER_CHROMA = 14.5f

    /** High container tone (chips, elevated cards, dropdowns). */
    const val CONTAINER_HIGH_TONE = 90.0f
    const val CONTAINER_HIGH_CHROMA = 16.0f

    /** Surface variant tone & chroma (aligned with [CONTAINER_HIGH_TONE]). */
    const val SURFACE_VARIANT_TONE = 90.0f
    const val SURFACE_VARIANT_CHROMA = 16.0f

    // ---- Accent containers & Now Playing / Queue gradient stops ------------

    const val PRIMARY_CONTAINER_TONE = 88.0f
    const val PRIMARY_CONTAINER_CHROMA = 24.0f

    const val SECONDARY_CONTAINER_TONE = 89.0f
    const val SECONDARY_CONTAINER_CHROMA = 18.0f

    const val SURFACE_TOP_TONE = 95.0f
    const val SURFACE_TOP_CHROMA = 12.0f

    const val SURFACE_BOTTOM_TONE = 90.5f
    const val SURFACE_BOTTOM_CHROMA = 16.0f
}

/**
 * Builds [VusicDynamicScheme] and [PlayingScheme] from an artwork seed color.
 *
 * In Dynamic Theme v2, neutral seeds (white, near-white, gray, black) are
 * first-class citizens: [buildMaterialScheme] and [schemeForSeed] map neutral
 * seeds (C*ab < 4) to true neutral gray/white/charcoal schemes (C*ab = 0)
 * instead of falling back to a blue or slate theme accent.
 */
object PlayingColors {

    /** Below this HSL saturation, legacy [schemeFor] treated a seed as gray. */
    const val GRAY_SATURATION = 0.12f

    /** Below this CIELAB chroma C*ab, a seed is treated as pure neutral (target chroma = 0). */
    const val NEUTRAL_CHROMA_FLOOR = LightTintSpec.NEUTRAL_CHROMA_FLOOR

    /** Chroma at which the smooth neutral scaling factor alpha reaches 1.0. */
    const val FULL_CHROMA_PIVOT = LightTintSpec.FULL_CHROMA_PIVOT

    /** Default light-mode surface tint strength (0f = pure neutral surfaces, 1f = full song tint). */
    const val DefaultLightTintStrength = 1.0f

    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L
    private const val NEAR_BLACK = 0xFF141414L
    private const val ERROR_RED = 0xFFB3261EL

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
        if (Contrast.ratio(primary, track) < 3.05) {
            track = if (Contrast.ratio(primary, container) >= 3.05) {
                container
            } else {
                Contrast.pickBest(BLACK, WHITE, primary)
            }
        }
        return track
    }

    /**
     * Adjusts a foreground role at ([hue], [startTone], [chroma]) in 1.5-tone
     * steps until it achieves at least [minRatio] against EVERY background in
     * [backgrounds]. If even extreme tones fail across the set, selects between
     * pure black and pure white by maximizing the minimum ratio across all
     * [backgrounds].
     */
    fun enforceContrastAcross(
        hue: Double,
        startTone: Float,
        chroma: Float,
        backgrounds: List<Long>,
        minRatio: Double,
        preferLighter: Boolean? = null,
    ): Long {
        if (backgrounds.isEmpty()) {
            return Contrast.fromToneChroma(hue, startTone, chroma)
        }
        val initial = Contrast.fromToneChroma(hue, startTone, chroma)
        if (backgrounds.all { Contrast.ratio(initial, it) >= minRatio }) {
            return initial
        }
        val maxBgLum = backgrounds.maxOf { Contrast.luminance(it) }
        val moveLighter = preferLighter ?: (maxBgLum <= NowPlayingColors.LIGHT_SURFACE_LUMINANCE)
        val step = if (moveLighter) 1.5f else -1.5f
        var tone = startTone
        repeat(68) {
            val cand = Contrast.fromToneChroma(hue, tone, chroma)
            if (backgrounds.all { Contrast.ratio(cand, it) >= minRatio }) {
                return cand
            }
            tone = (tone + step).coerceIn(0f, 100f)
        }
        // Try neutral (chroma = 0) along the same direction before black/white extreme.
        tone = startTone
        repeat(68) {
            val cand = Contrast.fromToneChroma(hue, tone, 0f)
            if (backgrounds.all { Contrast.ratio(cand, it) >= minRatio }) {
                return cand
            }
            tone = (tone + step).coerceIn(0f, 100f)
        }
        return Contrast.pickBestAcross(BLACK, WHITE, backgrounds)
    }

    /**
     * Static default [VusicDynamicScheme] matching `Theme.kt`'s `Dark` and `Light`
     * palettes when dynamic colors are disabled or nothing is playing.
     */
    fun staticMaterialScheme(dark: Boolean): VusicDynamicScheme = if (dark) {
        val primary = WHITE
        val primaryContainer = 0xFF3A3F46L
        val onPrimaryContainer = 0xFFEAF0F7L
        val background = 0xFF0F0F0FL
        val surface = 0xFF1E1E1EL
        val surfaceHigh = 0xFF2A2A2AL
        val secondaryText = 0xFF9AA0A6L
        val surfaceTop = Contrast.fromHsl(0f, 0f, 0.10f)
        val surfaceBottom = Contrast.fromHsl(0f, 0f, 0.06f)
        VusicDynamicScheme(
            seedArgb = null,
            dark = true,
            primary = primary,
            onPrimary = BLACK,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryText,
            onSecondary = WHITE,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            background = background,
            onBackground = WHITE,
            surface = surface,
            onSurface = WHITE,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = secondaryText,
            surfaceContainerLowest = BLACK,
            surfaceContainerLow = background,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            outline = 0xFF3A3A3AL,
            outlineVariant = 0xFF2A2A2AL,
            error = ERROR_RED,
            onError = WHITE,
            switchOffTrack = 0xFF3A3A3AL,
            switchOffOutline = 0xFF8A8A8AL,
            switchOffThumb = 0xFFB8B8B8L,
            disabledTrack = 0xFF232323L,
            disabledOutline = 0xFF4A4A4AL,
            disabledThumb = 0xFF5A5A5AL,
            sliderInactiveTrack = 0xFF6E6E6EL,
            chipOffFill = 0xFF2A2A2AL,
            chipOffText = 0xFFEDEDEDL,
            miniContainer = primaryContainer,
            miniOutline = primary,
            ringTrack = ringTrack(primaryContainer, primary),
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
            surfaceText = WHITE,
            surfaceSecondary = enforceContrastAcross(0.0, 78f, 0f, listOf(surfaceTop, surfaceBottom), 4.5, true),
            rowHighlightSecondary = onPrimaryContainer,
        )
    } else {
        val primary = 0xFF0B6BA8L
        val primaryContainer = 0xFFCFE8FFL
        val onPrimaryContainer = 0xFF001D33L
        val background = 0xFFF7F9FBL
        val surface = WHITE
        val surfaceVariant = 0xFFE7EDF3L
        val onSurface = 0xFF0B0F14L
        val onSurfaceVariant = 0xFF3F4A52L
        val surfaceContainerLow = 0xFFF2F5F9L
        val surfaceContainer = 0xFFECEFF4L
        val surfaceContainerHigh = 0xFFE4E8EEL
        val surfaceTop = Contrast.fromHsl(0f, 0f, 0.96f)
        val surfaceBottom = Contrast.fromHsl(0f, 0f, 0.91f)
        VusicDynamicScheme(
            seedArgb = null,
            dark = false,
            primary = primary,
            onPrimary = WHITE,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = 0xFF48626FL,
            onSecondary = WHITE,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = 0xFF6E7A85L,
            outlineVariant = 0xFFC3CCD5L,
            error = ERROR_RED,
            onError = WHITE,
            switchOffTrack = 0xFFD3D6DAL,
            switchOffOutline = 0xFF5F6368L,
            switchOffThumb = 0xFF5F6368L,
            disabledTrack = 0xFFE8EAEDL,
            disabledOutline = 0xFFBDC1C6L,
            disabledThumb = 0xFFBDC1C6L,
            sliderInactiveTrack = 0xFF8A9096L,
            chipOffFill = 0xFFE7EDF3L,
            chipOffText = 0xFF10141AL,
            miniContainer = surfaceContainer,
            miniOutline = 0xFF5F6368L,
            ringTrack = ringTrack(surfaceContainer, primary),
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
            surfaceText = NEAR_BLACK,
            surfaceSecondary = enforceContrastAcross(0.0, 34f, 0f, listOf(surfaceTop, surfaceBottom), 4.5, false),
            rowHighlightSecondary = onPrimaryContainer,
        )
    }

    /**
     * Generates a complete [VusicDynamicScheme] for [dark] (Dark or Light mode)
     * from [seedArgb] (including neutral white, gray, and black covers).
     *
     * @param seedArgb packed `0xFFRRGGBBL` seed color (or `null` for static theme).
     * @param dark `true` for Dark theme, `false` for Light theme.
     * @param lightTintStrength hook in `[0f, 1f]` scaling light surface chroma caps
     *        (defaults to [DefaultLightTintStrength] so Section D can tune light surface tint).
     */
    fun buildMaterialScheme(
        seedArgb: Long?,
        dark: Boolean,
        lightTintStrength: Float = DefaultLightTintStrength,
    ): VusicDynamicScheme {
        if (seedArgb == null) return staticMaterialScheme(dark)
        val normalizedSeed = seedArgb or 0xFF000000L
        return if (dark) {
            buildDarkMaterialScheme(normalizedSeed)
        } else {
            buildLightMaterialScheme(normalizedSeed, lightTintStrength.coerceIn(0f, 1f))
        }
    }

    /**
     * Dynamic Theme v2 builder returning a [PlayingScheme] for any [seedArgb]
     * (including neutral white/gray/black seeds, which stay neutral rather than
     * falling back to a blue or slate accent).
     */
    fun schemeForSeed(
        seedArgb: Long?,
        dark: Boolean,
        lightTintStrength: Float = DefaultLightTintStrength,
    ): PlayingScheme = buildMaterialScheme(seedArgb, dark, lightTintStrength).toPlayingScheme()

    /**
     * Builds a [VusicDynamicScheme] directly from raw cover pixels (`IntArray`).
     * Returns `null` if the cover has no opaque pixels.
     */
    fun schemeFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        dark: Boolean,
        lightTintStrength: Float = DefaultLightTintStrength,
    ): VusicDynamicScheme? {
        val seed = ArtworkColorExtractor.extractSourceColor(pixels, width, height) ?: return null
        return buildMaterialScheme(seed, dark, lightTintStrength)
    }

    private fun roleChroma(seedChroma: Double, alpha: Double, cap: Float): Float {
        if (seedChroma < NEUTRAL_CHROMA_FLOOR) return 0f
        return minOf(cap.toDouble() * alpha, seedChroma).toFloat()
    }

    private fun buildDarkMaterialScheme(seed: Long): VusicDynamicScheme {
        val hue = Contrast.labHue(seed)
        val seedChroma = Contrast.chromaOf(seed)
        val isNeutral = seedChroma < NEUTRAL_CHROMA_FLOOR
        val alpha = if (isNeutral) 0.0 else (seedChroma / FULL_CHROMA_PIVOT).coerceIn(0.0, 1.0)

        fun c(cap: Float): Float = roleChroma(seedChroma, alpha, cap)

        // Surfaces: tone 6-14 (never lightened even when seed is pure white)
        val surfaceContainerLowest = Contrast.fromToneChroma(hue, 4f, c(4f))
        val background = Contrast.fromToneChroma(hue, 6f, c(6f))
        val surfaceContainerLow = background
        val surface = Contrast.fromToneChroma(hue, 11f, c(10f))
        val surfaceContainer = surface
        val surfaceContainerHigh = Contrast.fromToneChroma(hue, 16f, c(14f))
        val surfaceVariant = surfaceContainerHigh

        // Containers a few tones above surface
        val primaryContainer = Contrast.fromToneChroma(hue, DarkToneSpec.HIGHLIGHT_TONE, c(DarkToneSpec.HIGHLIGHT_CHROMA))
        val secondaryContainer = Contrast.fromToneChroma(hue, 20f, c(16f))

        // MiniPlayer base & outline
        val miniContainer = Contrast.fromToneChroma(hue, DarkToneSpec.MINI_TONE, c(DarkToneSpec.MINI_CHROMA))
        val miniOutline = Contrast.fromToneChroma(hue, DarkToneSpec.MINI_OUTLINE_TONE, c(DarkToneSpec.MINI_OUTLINE_CHROMA))

        // Full-screen Now Playing / Queue gradients
        val surfaceTop = Contrast.fromToneChroma(hue, DarkToneSpec.SURFACE_TOP_TONE, c(DarkToneSpec.SURFACE_CHROMA))
        val surfaceBottom = Contrast.fromToneChroma(hue, DarkToneSpec.SURFACE_BOTTOM_TONE, c(DarkToneSpec.SURFACE_CHROMA))

        // Primary / Accent: tone ~75-85 (82 for neutral white/gray/black, 78 for chromatic)
        val basePrimaryTone = if (isNeutral) 82f else 78f
        val primaryChroma = c(DarkToneSpec.ACCENT_CHROMA)
        val primary = enforceContrastAcross(
            hue = hue,
            startTone = basePrimaryTone,
            chroma = primaryChroma,
            backgrounds = listOf(background, surface, surfaceContainer, surfaceContainerHigh, primaryContainer, secondaryContainer, miniContainer, surfaceTop, surfaceBottom),
            minRatio = 4.5,
            preferLighter = true,
        )

        val onPrimary = enforceContrastAcross(
            hue = hue,
            startTone = 12f,
            chroma = c(10f),
            backgrounds = listOf(primary),
            minRatio = 4.5,
            preferLighter = false,
        )

        val onPrimaryContainer = enforceContrastAcross(
            hue = hue,
            startTone = DarkToneSpec.ON_HIGHLIGHT_TONE,
            chroma = c(DarkToneSpec.ON_HIGHLIGHT_CHROMA),
            backgrounds = listOf(primaryContainer),
            minRatio = 4.5,
            preferLighter = true,
        )

        val onSecondaryContainer = enforceContrastAcross(
            hue = hue,
            startTone = 92f,
            chroma = c(10f),
            backgrounds = listOf(secondaryContainer, surface, background, surfaceContainerHigh),
            minRatio = 4.5,
            preferLighter = true,
        )

        val rowHighlightSecondary = enforceContrastAcross(
            hue = hue,
            startTone = DarkToneSpec.SECONDARY_TONE,
            chroma = c(DarkToneSpec.SECONDARY_CHROMA),
            backgrounds = listOf(primaryContainer),
            minRatio = 4.5,
            preferLighter = true,
        )

        val secondary = enforceContrastAcross(
            hue = hue,
            startTone = 78f,
            chroma = c(14f),
            backgrounds = listOf(background, surface, primaryContainer),
            minRatio = 4.5,
            preferLighter = true,
        )
        val onSecondary = enforceContrastAcross(
            hue = hue,
            startTone = 12f,
            chroma = c(8f),
            backgrounds = listOf(secondary),
            minRatio = 4.5,
            preferLighter = false,
        )

        val surfaceBgs = listOf(background, surface, surfaceContainerHigh)
        val onSurface = enforceContrastAcross(
            hue = hue,
            startTone = 96f,
            chroma = c(4f),
            backgrounds = surfaceBgs,
            minRatio = 4.5,
            preferLighter = true,
        )
        val onBackground = onSurface
        val onSurfaceVariant = enforceContrastAcross(
            hue = hue,
            startTone = 76f,
            chroma = c(8f),
            backgrounds = surfaceBgs,
            minRatio = 4.5,
            preferLighter = true,
        )

        val outline = Contrast.fromToneChroma(hue, 38f, c(10f))
        val outlineVariant = Contrast.fromToneChroma(hue, 24f, c(8f))

        // Controls
        val chipOffFill = surfaceContainerHigh
        val chipOffText = enforceContrastAcross(
            hue = hue,
            startTone = 93f,
            chroma = c(4f),
            backgrounds = listOf(chipOffFill),
            minRatio = 4.5,
            preferLighter = true,
        )
        val switchOffTrack = Contrast.fromToneChroma(hue, 24f, c(8f))
        val switchOffOutline = enforceContrastAcross(
            hue = hue,
            startTone = 58f,
            chroma = c(8f),
            backgrounds = listOf(surface, background, chipOffFill),
            minRatio = 3.0,
            preferLighter = true,
        )
        val switchOffThumb = enforceContrastAcross(
            hue = hue,
            startTone = 76f,
            chroma = c(6f),
            backgrounds = listOf(switchOffTrack),
            minRatio = 3.0,
            preferLighter = true,
        )
        val sliderInactiveTrack = enforceContrastAcross(
            hue = hue,
            startTone = 46f,
            chroma = c(8f),
            backgrounds = listOf(surface, background),
            minRatio = 2.0,
            preferLighter = true,
        )

        // Now Playing gradient inks
        val gradBgs = listOf(surfaceTop, surfaceBottom)
        val surfaceText = enforceContrastAcross(
            hue = hue,
            startTone = DarkToneSpec.SURFACE_TEXT_TONE,
            chroma = c(DarkToneSpec.SURFACE_TEXT_CHROMA),
            backgrounds = gradBgs,
            minRatio = 4.5,
            preferLighter = true,
        )
        val surfaceSecondary = enforceContrastAcross(
            hue = hue,
            startTone = DarkToneSpec.SURFACE_SECONDARY_TONE,
            chroma = c(DarkToneSpec.SURFACE_SECONDARY_CHROMA),
            backgrounds = gradBgs,
            minRatio = 4.5,
            preferLighter = true,
        )

        // Ring track: derived downstream from miniContainer & primary, verified >= 3.05:1
        val track = ringTrack(miniContainer, primary)

        return VusicDynamicScheme(
            seedArgb = seed,
            dark = true,
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = outline,
            outlineVariant = outlineVariant,
            error = ERROR_RED,
            onError = WHITE,
            switchOffTrack = switchOffTrack,
            switchOffOutline = switchOffOutline,
            switchOffThumb = switchOffThumb,
            disabledTrack = 0xFF232323L,
            disabledOutline = 0xFF4A4A4AL,
            disabledThumb = 0xFF5A5A5AL,
            sliderInactiveTrack = sliderInactiveTrack,
            chipOffFill = chipOffFill,
            chipOffText = chipOffText,
            miniContainer = miniContainer,
            miniOutline = miniOutline,
            ringTrack = track,
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
            surfaceText = surfaceText,
            surfaceSecondary = surfaceSecondary,
            rowHighlightSecondary = rowHighlightSecondary,
        )
    }

    private fun buildLightMaterialScheme(seed: Long, lightTintStrength: Float): VusicDynamicScheme {
        val hue = Contrast.labHue(seed)
        val seedChroma = Contrast.chromaOf(seed)
        val isNeutral = seedChroma < LightTintSpec.NEUTRAL_CHROMA_FLOOR
        val alpha = if (isNeutral) {
            0.0
        } else {
            ((seedChroma - LightTintSpec.NEUTRAL_CHROMA_FLOOR) /
                (LightTintSpec.FULL_CHROMA_PIVOT - LightTintSpec.NEUTRAL_CHROMA_FLOOR))
                .coerceIn(0.0, 1.0)
        }
        val tint = lightTintStrength.toDouble().coerceIn(0.0, 1.0)

        fun c(cap: Float): Float = roleChroma(seedChroma, alpha, cap)
        fun surfaceChroma(cap: Float): Float = roleChroma(seedChroma, alpha * tint, cap)

        // Clamped lightness invariant: light surfaces stay at T=90..96.5 even for a pure black seed.
        val surfaceContainerLowest = Contrast.fromToneChroma(
            hue,
            LightTintSpec.CONTAINER_LOWEST_TONE,
            surfaceChroma(LightTintSpec.CONTAINER_LOWEST_CHROMA),
        )
        val surface = Contrast.fromToneChroma(
            hue,
            LightTintSpec.SURFACE_TONE,
            surfaceChroma(LightTintSpec.SURFACE_CHROMA),
        )
        val background = Contrast.fromToneChroma(
            hue,
            LightTintSpec.BACKGROUND_TONE,
            surfaceChroma(LightTintSpec.BACKGROUND_CHROMA),
        )
        val surfaceContainerLow = Contrast.fromToneChroma(
            hue,
            LightTintSpec.CONTAINER_LOW_TONE,
            surfaceChroma(LightTintSpec.CONTAINER_LOW_CHROMA),
        )
        val surfaceContainer = Contrast.fromToneChroma(
            hue,
            LightTintSpec.CONTAINER_TONE,
            surfaceChroma(LightTintSpec.CONTAINER_CHROMA),
        )
        val surfaceContainerHigh = Contrast.fromToneChroma(
            hue,
            LightTintSpec.CONTAINER_HIGH_TONE,
            surfaceChroma(LightTintSpec.CONTAINER_HIGH_CHROMA),
        )
        val surfaceVariant = Contrast.fromToneChroma(
            hue,
            LightTintSpec.SURFACE_VARIANT_TONE,
            surfaceChroma(LightTintSpec.SURFACE_VARIANT_CHROMA),
        )

        // Containers: soft tint of seed
        val primaryContainer = Contrast.fromToneChroma(
            hue,
            LightTintSpec.PRIMARY_CONTAINER_TONE,
            c(LightTintSpec.PRIMARY_CONTAINER_CHROMA),
        )
        val secondaryContainer = Contrast.fromToneChroma(
            hue,
            LightTintSpec.SECONDARY_CONTAINER_TONE,
            c(LightTintSpec.SECONDARY_CONTAINER_CHROMA),
        )

        // MiniPlayer in Light mode: tinted card (T=91.0) and legible outline
        val miniContainer = Contrast.fromToneChroma(
            hue,
            LightTintSpec.MINI_CONTAINER_TONE,
            surfaceChroma(LightTintSpec.MINI_CONTAINER_CHROMA),
        )
        val miniOutline = enforceContrastAcross(
            hue = hue,
            startTone = 55f,
            chroma = surfaceChroma(8f),
            backgrounds = listOf(background, surface, miniContainer),
            minRatio = 3.0,
            preferLighter = false,
        )

        // Light gradient stops for Now Playing / Queue
        val surfaceTop = Contrast.fromToneChroma(
            hue,
            LightTintSpec.SURFACE_TOP_TONE,
            surfaceChroma(LightTintSpec.SURFACE_TOP_CHROMA),
        )
        val surfaceBottom = Contrast.fromToneChroma(
            hue,
            LightTintSpec.SURFACE_BOTTOM_TONE,
            surfaceChroma(LightTintSpec.SURFACE_BOTTOM_CHROMA),
        )

        // Primary / Accent: tone ~35-45 (charcoal T=38 for neutral sources, never blue)
        val basePrimaryTone = if (isNeutral) 38f else 40f
        val primaryChroma = if (isNeutral) {
            0f
        } else {
            minOf(42.0 * alpha, maxOf(seedChroma, 28.0 * alpha)).toFloat()
        }
        val primary = enforceContrastAcross(
            hue = hue,
            startTone = basePrimaryTone,
            chroma = primaryChroma,
            backgrounds = listOf(
                surfaceContainerLowest,
                background,
                surface,
                surfaceContainerLow,
                surfaceContainer,
                surfaceContainerHigh,
                surfaceVariant,
                primaryContainer,
                secondaryContainer,
                miniContainer,
                surfaceTop,
                surfaceBottom,
            ),
            minRatio = 4.5,
            preferLighter = false,
        )

        val onPrimary = enforceContrastAcross(
            hue = hue,
            startTone = 99f,
            chroma = 0f,
            backgrounds = listOf(primary),
            minRatio = 4.5,
            preferLighter = true,
        )

        val onPrimaryContainer = enforceContrastAcross(
            hue = hue,
            startTone = 14f,
            chroma = c(12f),
            backgrounds = listOf(primaryContainer),
            minRatio = 4.5,
            preferLighter = false,
        )

        val onSecondaryContainer = enforceContrastAcross(
            hue = hue,
            startTone = 14f,
            chroma = c(10f),
            backgrounds = listOf(secondaryContainer, surface, background, surfaceContainerLow, surfaceContainerHigh),
            minRatio = 4.5,
            preferLighter = false,
        )

        val rowHighlightSecondary = enforceContrastAcross(
            hue = hue,
            startTone = 28f,
            chroma = c(12f),
            backgrounds = listOf(primaryContainer),
            minRatio = 4.5,
            preferLighter = false,
        )

        val secondary = enforceContrastAcross(
            hue = hue,
            startTone = 38f,
            chroma = c(14f),
            backgrounds = listOf(background, surface, primaryContainer),
            minRatio = 4.5,
            preferLighter = false,
        )
        val onSecondary = enforceContrastAcross(
            hue = hue,
            startTone = 99f,
            chroma = 0f,
            backgrounds = listOf(secondary),
            minRatio = 4.5,
            preferLighter = true,
        )

        val surfaceBgs = listOf(
            surfaceContainerLowest,
            background,
            surface,
            surfaceContainerLow,
            surfaceContainer,
            miniContainer,
            surfaceContainerHigh,
            surfaceVariant,
        )
        val onSurface = enforceContrastAcross(
            hue = hue,
            startTone = 10f,
            chroma = c(4f),
            backgrounds = surfaceBgs,
            minRatio = 4.5,
            preferLighter = false,
        )
        val onBackground = onSurface
        val onSurfaceVariant = enforceContrastAcross(
            hue = hue,
            startTone = 34f,
            chroma = c(10f),
            backgrounds = surfaceBgs,
            minRatio = 4.5,
            preferLighter = false,
        )

        val outline = Contrast.fromToneChroma(hue, 50f, c(10f))
        val outlineVariant = Contrast.fromToneChroma(hue, 80f, c(8f))

        // Controls
        val chipOffFill = surfaceContainerHigh
        val chipOffText = enforceContrastAcross(
            hue = hue,
            startTone = 12f,
            chroma = c(4f),
            backgrounds = listOf(chipOffFill),
            minRatio = 4.5,
            preferLighter = false,
        )
        val switchOffTrack = Contrast.fromToneChroma(hue, 86f, surfaceChroma(6f))
        val switchOffOutline = enforceContrastAcross(
            hue = hue,
            startTone = 42f,
            chroma = c(8f),
            backgrounds = listOf(surface, background, chipOffFill),
            minRatio = 3.0,
            preferLighter = false,
        )
        val switchOffThumb = enforceContrastAcross(
            hue = hue,
            startTone = 38f,
            chroma = c(6f),
            backgrounds = listOf(switchOffTrack),
            minRatio = 3.0,
            preferLighter = false,
        )
        val sliderInactiveTrack = enforceContrastAcross(
            hue = hue,
            startTone = 58f,
            chroma = c(8f),
            backgrounds = listOf(surface, background),
            minRatio = 2.0,
            preferLighter = false,
        )

        // Now Playing gradient inks
        val gradBgs = listOf(surfaceTop, surfaceBottom)
        val surfaceText = enforceContrastAcross(
            hue = hue,
            startTone = 10f,
            chroma = c(4f),
            backgrounds = gradBgs,
            minRatio = 4.5,
            preferLighter = false,
        )
        val surfaceSecondary = enforceContrastAcross(
            hue = hue,
            startTone = 32f,
            chroma = c(8f),
            backgrounds = gradBgs,
            minRatio = 4.5,
            preferLighter = false,
        )

        val track = ringTrack(miniContainer, primary)

        return VusicDynamicScheme(
            seedArgb = seed,
            dark = false,
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = outline,
            outlineVariant = outlineVariant,
            error = ERROR_RED,
            onError = WHITE,
            switchOffTrack = switchOffTrack,
            switchOffOutline = switchOffOutline,
            switchOffThumb = switchOffThumb,
            disabledTrack = 0xFFE8EAEDL,
            disabledOutline = 0xFFBDC1C6L,
            disabledThumb = 0xFFBDC1C6L,
            sliderInactiveTrack = sliderInactiveTrack,
            chipOffFill = chipOffFill,
            chipOffText = chipOffText,
            miniContainer = miniContainer,
            miniOutline = miniOutline,
            ringTrack = track,
            surfaceTop = surfaceTop,
            surfaceBottom = surfaceBottom,
            surfaceText = surfaceText,
            surfaceSecondary = surfaceSecondary,
            rowHighlightSecondary = rowHighlightSecondary,
        )
    }

    // ---- Legacy compatibility entry point (`schemeFor`) ---------------------

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

    private fun fallbackScheme(container: Long, onContainer: Long, dark: Boolean): PlayingScheme {
        val fixedOn = if (Contrast.ratio(onContainer, container) >= 4.5) {
            onContainer
        } else {
            Contrast.pickBest(BLACK, WHITE, container)
        }
        val fixedPrimary = fixedOn
        val top = if (dark) Contrast.fromHsl(0f, 0f, 0.10f) else Contrast.fromHsl(0f, 0f, 0.96f)
        val bottom = if (dark) Contrast.fromHsl(0f, 0f, 0.06f) else Contrast.fromHsl(0f, 0f, 0.91f)
        val surfaceText = if (dark) WHITE else NEAR_BLACK
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

    private fun toned(hue: Double, tone: Float, cap: Float, seedChroma: Double): Long =
        Contrast.fromToneChroma(hue, tone, minOf(cap.toDouble(), seedChroma).toFloat())

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
        return Contrast.pickBest(BLACK, WHITE, bg)
    }

    private fun darkScheme(seed: Long, h: Float, s: Float): PlayingScheme {
        val hue = Contrast.labHue(seed)
        val seedChroma = Contrast.chromaOf(seed)

        val container = toned(hue, DarkToneSpec.HIGHLIGHT_TONE, DarkToneSpec.HIGHLIGHT_CHROMA, seedChroma)
        val onContainer = enforce(
            hue, DarkToneSpec.ON_HIGHLIGHT_TONE, DarkToneSpec.ON_HIGHLIGHT_CHROMA,
            container, 4.5, lighter = true,
        )
        val secondary = enforce(
            hue, DarkToneSpec.SECONDARY_TONE, DarkToneSpec.SECONDARY_CHROMA,
            container, 4.5, lighter = true,
        )
        val primary = enforce(
            hue, DarkToneSpec.ACCENT_TONE, DarkToneSpec.ACCENT_CHROMA,
            container, 3.0, lighter = true,
        )
        val track = enforce(
            hue, DarkToneSpec.RING_TRACK_TONE, DarkToneSpec.RING_TRACK_CHROMA,
            primary, 3.0, lighter = false,
        )
        val miniContainer = toned(hue, DarkToneSpec.MINI_TONE, DarkToneSpec.MINI_CHROMA, seedChroma)
        val miniOutline = toned(hue, DarkToneSpec.MINI_OUTLINE_TONE, DarkToneSpec.MINI_OUTLINE_CHROMA, seedChroma)
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

    private fun lightScheme(seed: Long, h: Float, s: Float): PlayingScheme {
        val dynamic = buildLightMaterialScheme(seed, DefaultLightTintStrength)
        return PlayingScheme.fromDynamic(dynamic)
    }
}
