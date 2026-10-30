package com.feishin.mobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Every duration and curve in the app lives here, so motion stays consistent
 * (and is tunable in one place) instead of scattering magic numbers.
 *
 * Rules the whole app follows:
 *  - 150-300 ms for anything the finger is waiting on; nothing longer, because
 *    a long animation on a gesture is a dropped gesture.
 *  - Springs for anything the finger is *holding* (sheets, drag release), tweens
 *    for everything else.
 *  - Nothing animates on first frame: content that must be there at startup
 *    renders in its final state, and enter animations are opt-in.
 *  - `Motion.durationScale` is read from the system animation scale, so when the
 *    user turns animations off every duration below becomes 0 and every spring
 *    snaps.
 */
object Motion {

    /**
     * System animation scale (0 = animations off, 1 = normal, 2 = 200%).
     * Set once from the platform; defaults to 1.
     */
    @Volatile
    var durationScale: Float = 1f

    private val eased: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Shortest transition: chips, ripples, pressed states. */
    const val Fast = 150

    /** Default transition: enter/exit, content changes, crossfades. */
    const val Normal = 220

    /** Longest allowed: only for large surfaces, never for input feedback. */
    const val Slow = 300

    /** Settings home <-> sub-screen push. Between Normal and Slow on purpose. */
    const val SubScreen = 250

    /** Artwork scheme crossfade on track change. */
    const val SchemeChange = 450

    /** Colour cross-fade, for borders, pill fills and warning text. */
    fun color(duration: Int = Normal) = tweenSpec<androidx.compose.ui.graphics.Color>(duration)

    /** Stagger step for list/card entrance animations. */
    const val Stagger = 28

    /** Distance an entrance animates from. */
    val EnterOffset = 16

    private fun <T> tweenSpec(duration: Int) = tween<T>(
        durationMillis = scaled(duration),
        easing = eased,
    )

    fun scaled(duration: Int): Int =
        (duration * durationScale.coerceIn(0f, 4f)).toInt()

    // ---- Tweens ------------------------------------------------------------

    /** Fade in/out over [Normal] ms. */
    fun fade(duration: Int = Normal) = tweenSpec<Float>(duration)

    /** Enter from [EnterOffset] below with a fade. */
    fun enterItem() = tweenSpec<Float>(Normal)

    /** Scale-in for chips and small controls. */
    fun scale(duration: Int = Fast) = tweenSpec<Float>(duration)

    /** Value change inside a row (summaries, counters). */
    fun value(duration: Int = Normal) = tweenSpec<Float>(duration)

    // ---- Springs -----------------------------------------------------------

    /** Sheet release: critically-ish damped, no visible bounce. */
    fun sheetSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A card being dragged back into place. */
    fun snapBackSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Anything that should move but must never overshoot (bars, padding). */
    fun layoutSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessLow,
    )

    /** Dp variant of [layoutSpring], for paddings and sizes. */
    fun layoutDp(): FiniteAnimationSpec<androidx.compose.ui.unit.Dp> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessLow,
    )

    /** IntSize variant, for expand/shrink of a container's height. */
    fun layoutSize(): FiniteAnimationSpec<androidx.compose.ui.unit.IntSize> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessLow,
    )

    /**
     * IntSize variant over a fixed duration, for a reveal whose timing should
     * read as deliberate rather than springy (the library filter row).
     */
    fun layoutTween(duration: Int = Normal) =
        tweenSpec<androidx.compose.ui.unit.IntSize>(duration)

    /** IntOffset variant, for navigation slides. */
    fun navSlide(): FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * IntOffset variant over a fixed duration, for content that slides a short
     * distance (it should read as travel decided up front, not as a spring).
     */
    fun slide(duration: Int = Slow) = tweenSpec<IntOffset>(duration)

    // ---- Navigation --------------------------------------------------------

    /** Horizontal push for forward detail navigation. */
    const val NavForward = 280

    /** Faster reverse, which is what users actually notice. */
    const val NavBack = 200

    /** Fade-through between bottom-nav tabs. */
    const val TabFade = 180

    // ---- Navigation transitions -------------------------------------------

    fun navForwardOffset(full: Int): IntOffset =
        IntOffset(if (durationScale == 0f) 0 else full, 0)

    fun navBackOffset(full: Int): IntOffset =
        IntOffset(if (durationScale == 0f) 0 else -full, 0)
}