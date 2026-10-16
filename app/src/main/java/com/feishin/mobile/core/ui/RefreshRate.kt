package com.feishin.mobile.core.ui

/**
 * High-refresh-rate mode selection (pure logic; Android `Display.Mode`
 * mapping lives in MainActivity so this stays unit-testable).
 *
 * Rules:
 * - Only modes with the SAME resolution as the current one are candidates.
 *   A higher rate at a lower resolution must never win (it would rescale
 *   the whole UI).
 * - AUTO/MAX pick the highest same-resolution rate; STANDARD picks the
 *   same-resolution mode closest to 60 Hz.
 * - Ties prefer the current mode (no-op switch); then the lower mode id.
 * - If nothing matches (shouldn't happen — the current mode is always in
 *   the list), the current mode id is returned, i.e. change nothing.
 */
enum class RefreshRateMode { AUTO, MAX, STANDARD }

data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

object RefreshRate {

    /** Target rate for [RefreshRateMode.STANDARD]. */
    const val STANDARD_RATE = 60f

    fun selectModeId(
        modes: List<DisplayModeInfo>,
        currentWidth: Int,
        currentHeight: Int,
        currentId: Int,
        mode: RefreshRateMode,
    ): Int {
        val target = when (mode) {
            RefreshRateMode.STANDARD -> STANDARD_RATE
            RefreshRateMode.AUTO, RefreshRateMode.MAX -> Float.POSITIVE_INFINITY
        }
        return selectForTarget(modes, currentWidth, currentHeight, currentId, target)
    }

    private fun selectForTarget(
        modes: List<DisplayModeInfo>,
        width: Int,
        height: Int,
        currentId: Int,
        targetRate: Float,
    ): Int {
        val sameRes = modes.filter { it.width == width && it.height == height }
        if (sameRes.isEmpty()) return currentId
        if (targetRate.isInfinite()) {
            val best = sameRes.maxOf { it.refreshRate }
            return sameRes.filter { it.refreshRate == best }.minWithOrNull(
                compareBy({ if (it.id == currentId) 0 else 1 }, { it.id }),
            )?.id ?: currentId
        }
        return sameRes.minWithOrNull(
            compareBy(
                { kotlin.math.abs(it.refreshRate - targetRate) },
                { if (it.id == currentId) 0 else 1 },
                { it.id },
            ),
        )?.id ?: currentId
    }

    /**
     * The setting is hidden when the device exposes a single rate: there is
     * nothing to choose between.
     */
    fun shouldShowSetting(modes: List<DisplayModeInfo>): Boolean =
        modes.map { it.refreshRate }.toSet().size > 1

    fun label(mode: RefreshRateMode): String = when (mode) {
        RefreshRateMode.AUTO -> "Auto"
        RefreshRateMode.MAX -> "Max"
        RefreshRateMode.STANDARD -> "Standard 60 Hz"
    }
}
