package com.feishin.mobile.core.ui

/**
 * Every tone (CIELAB L*) and chroma cap for the DARK artwork tint, in one
 * place so the look can be tuned without hunting through the scheme builder.
 *
 * Terms, as used by the Material/HCT spec this follows:
 * - **tone** is perceptual lightness, 0 = black, 100 = white. 5-6 is the app's
 *   near-black page background, 22 is "surface container high".
 * - **chroma** is distance from gray. 0 is neutral, ~100+ is fully vivid.
 *
 * The intent is "a soft tint of the cover colour", not a vivid swatch: the
 * highlight is a deep, lightly-tinted surface, the mini player is nearly black
 * with only a hint of the hue, and the full-screen gradients are darker still.
 * Light theme has its own (unchanged) tones in [PlayingColors.lightScheme].
 */
object DarkToneSpec {

    // ---- Highlighted (current) row: muted mid-dark tint ----------------------

    /** Container tone. 22 sits in the "surface container high" band. */
    const val HIGHLIGHT_TONE = 22f

    /** Container chroma cap. Well under the seed's own chroma, so it reads soft. */
    const val HIGHLIGHT_CHROMA = 22f

    /** Title tone on the highlight container (light, low chroma). */
    const val ON_HIGHLIGHT_TONE = 92f

    /** Title chroma: near-neutral, so text never looks tinted-neon. */
    const val ON_HIGHLIGHT_CHROMA = 10f

    /** Secondary (artist / duration) tone; dimmer than the title, still >= 4.5. */
    const val SECONDARY_TONE = 78f
    const val SECONDARY_CHROMA = 12f

    // ---- Mini player: nearly black, faint hue -------------------------------

    /** Container tone: just above the page background, so the pill is a shape. */
    const val MINI_TONE = 12f

    /** Container chroma cap: a hint of the cover hue, nothing more. */
    const val MINI_CHROMA = 13f

    /** 1dp outline tone so the pill is clearly delineated on the background. */
    const val MINI_OUTLINE_TONE = 26f
    const val MINI_OUTLINE_CHROMA = 10f

    // ---- Accent (ring, mini icons/title, queue toggles, Save pill) ---------

    /** Accent tone: light enough to read on the dark surfaces, not neon. */
    const val ACCENT_TONE = 75f

    /** Accent chroma cap: visible hue without the saturated glow. */
    const val ACCENT_CHROMA = 34f

    /** Ring track tone: a faint groove on the mini player. */
    const val RING_TRACK_TONE = 30f
    const val RING_TRACK_CHROMA = 16f

    // ---- Full-screen gradients (queue + Now Playing) -----------------------

    /** Top of the gradient: darker than the highlight, lighter than the bottom. */
    const val SURFACE_TOP_TONE = 11f

    /** Bottom of the gradient. */
    const val SURFACE_BOTTOM_TONE = 7f

    /** Gradient chroma cap: the same muted hue as everything else. */
    const val SURFACE_CHROMA = 14f

    /** Text on the gradients: the theme's near-white, essentially neutral. */
    const val SURFACE_TEXT_TONE = 98f
    const val SURFACE_TEXT_CHROMA = 4f

    /** Secondary text on the gradients; still >= 4.5 on both stops. */
    const val SURFACE_SECONDARY_TONE = 78f
    const val SURFACE_SECONDARY_CHROMA = 6f
}