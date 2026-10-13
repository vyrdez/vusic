package com.feishin.mobile.core.common

import kotlin.math.pow

/** How ReplayGain is applied. */
enum class ReplayGainMode { OFF, TRACK, ALBUM }

/**
 * ReplayGain math, kept pure so it is unit-testable.
 *
 * ReplayGain values are dB (typically negative). Converting to a linear scale and
 * clamping with the track peak avoids clipping: a track that would clip is scaled
 * down further so its loudest sample never exceeds full scale.
 */
object ReplayGain {

    /** dB -> linear amplitude multiplier. */
    fun linearFromDb(db: Double): Double = 10.0.pow(db / 20.0)

    /**
     * Volume scale for [gainDb] with pre-amp applied, peak-limited by [peak].
     *
     * @param peak track peak in [0,1+] as reported by the server; null disables
     *        peak limiting (we then trust the pre-amp clamp).
     * @return a multiplier safe for `Player.setVolume` (0f..1f). ReplayGain only
     *         ever attenuates, because boosting above 1 would clip on many devices.
     */
    fun scale(gainDb: Double?, preampDb: Double = 0.0, peak: Double? = null): Float {
        if (gainDb == null) return 1f
        var linear = linearFromDb(gainDb + preampDb)
        if (linear <= 0.0 || !linear.isFinite()) return 1f
        // Peak limiting: keep the loudest sample at or below full scale.
        if (peak != null && peak > 0.0) {
            linear = minOf(linear, 1.0 / peak)
        }
        return linear.coerceIn(0.0, 1.0).toFloat()
    }

    /** The gain to use for a track, honouring the mode and the album fallback. */
    fun pickGain(
        mode: ReplayGainMode,
        trackGain: Double?,
        albumGain: Double?,
        fallbackDb: Double,
    ): Double? = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> trackGain ?: albumGain ?: fallbackDb
        ReplayGainMode.ALBUM -> albumGain ?: trackGain ?: fallbackDb
    }

    /**
     * The peak that pairs with [pickGain]'s choice, so TRACK mode limits on the
     * track peak and ALBUM mode on the album peak.
     */
    fun pickPeak(mode: ReplayGainMode, trackPeak: Double?, albumPeak: Double?): Double? = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> trackPeak ?: albumPeak
        ReplayGainMode.ALBUM -> albumPeak ?: trackPeak
    }
}
