package com.feishin.mobile.core.common

/**
 * Decides where a Settings row's control goes: beside the text on the right,
 * or — when the text would be squeezed too narrow — on the next line aligned
 * to the END (right), never left or centred.
 *
 * Kept pure (all dp as Float) so the rule is unit-testable. The widths are
 * estimates, not measurements: body text averages ~8dp per character at
 * scale 1.0, buttons add their chrome. The estimates only have to be
 * conservative enough that a row which fits never stacks and a row which
 * cannot fit never squeezes its text below [MIN_TEXT_SHARE].
 */
object RowLayout {

    /**
     * Text keeps at least this share of the row, or the control wraps.
     * Nominally "about 40%"; set to 35% so rows the design mandates beside —
     * the Wi-Fi bitrate pair, whose two dropdowns take ~36% of a 348dp row —
     * stay beside at the default scale.
     */
    const val MIN_TEXT_SHARE = 0.35f

    /** A control never takes more than this share when beside the text. */
    const val MAX_CONTROL_SHARE = 0.45f

    /** Gap between the text column and a beside-control. */
    const val BESIDE_GAP_DP = 16f

    /** Gap between two side-by-side controls (e.g. bitrate + format). */
    const val CONTROL_GAP_DP = 8f

    /** Gap between the text and a wrapped control on the next line. */
    const val STACKED_GAP_DP = 8f

    // Average glyph widths at fontScale 1.0, calibrated to the type scale in
    // use: bodyMedium/labelLarge are 14sp (~7dp per latin glyph with a small
    // margin), bodySmall is 12sp (~6dp). Slightly conservative, so a row that
    // only just fits still stacks rather than squeezing its text.
    private const val LABEL_CHAR_DP = 7.2f
    private const val DESC_CHAR_DP = 6.2f
    private const val BUTTON_CHAR_DP = 7.2f

    /** Horizontal chrome of an outlined button: 16dp padding each side. */
    private const val BUTTON_CHROME_DP = 32f

    /** Leading dropdown chevron inside a button. */
    private const val DROPDOWN_ICON_DP = 28f

    /** Horizontal chrome of one segmented cell: 12dp padding each side. */
    private const val SEGMENT_CHROME_DP = 24f

    /** A switch's fixed width. */
    const val SWITCH_WIDTH_DP = 52f

    sealed interface Placement {
        /** Control sits on the right, this wide (already capped). */
        data class Beside(val controlWidthDp: Float) : Placement

        /** Control goes on the next line, aligned to the END (right). */
        data object NextLineEnd : Placement
    }

    /**
     * Estimated text width: label (bodyMedium, up to 2 lines but measured as
     * one run — wrapping only helps) plus description (bodySmall). Warnings
     * reuse the description size.
     */
    fun estimateTextWidthDp(label: String, description: String?, fontScale: Float): Float {
        val scale = fontScale.coerceIn(0.5f, 2.5f)
        val labelW = label.length * LABEL_CHAR_DP
        val descW = (description?.length ?: 0) * DESC_CHAR_DP
        return (maxOf(labelW, descW)) * scale
    }

    /** Estimated width of one outlined action/dropdown button. */
    fun estimateButtonWidthDp(label: String, hasIcon: Boolean, fontScale: Float): Float {
        val scale = fontScale.coerceIn(0.5f, 2.5f)
        return (label.length * BUTTON_CHAR_DP + BUTTON_CHROME_DP + if (hasIcon) DROPDOWN_ICON_DP else 0f) * scale
    }

    /** Estimated width of a single-row segmented control (no wrapping). */
    fun estimateSegmentedWidthDp(options: List<String>, fontScale: Float): Float {
        val scale = fontScale.coerceIn(0.5f, 2.5f)
        if (options.isEmpty()) return 0f
        val cells = options.sumOf { (it.length * BUTTON_CHAR_DP + SEGMENT_CHROME_DP).toDouble() }.toFloat()
        return (cells + CONTROL_GAP_DP * (options.size - 1)) * scale
    }

    /**
     * @param availableWidthDp row width available for text + control.
     * @param textWidthDp estimated text width from [estimateTextWidthDp].
     * @param controlWidthDp estimated control width (intrinsic).
     */
    /** Text may wrap into this many lines inside its column. */
    const val MAX_TEXT_LINES = 3

    /**
     * @param capControl buttons and dropdowns ellipsize gracefully, so their
     *        share is capped at 45% in the maths; a segmented control must keep
     *        readable cells, so it is measured whole (uncapped) and stacks as
     *        one piece when it cannot fit.
     */
    fun decide(
        availableWidthDp: Float,
        textWidthDp: Float,
        controlWidthDp: Float,
        capControl: Boolean = true,
    ): Placement {
        if (availableWidthDp <= 0f) return Placement.NextLineEnd
        val control = controlWidthDp.coerceAtLeast(0f)
            .let { if (capControl) it.coerceAtMost(availableWidthDp * MAX_CONTROL_SHARE) else it }
        val column = availableWidthDp - BESIDE_GAP_DP - control
        val textShare = column / availableWidthDp
        // Beside only when the text column keeps a readable share AND the text
        // wraps into at most MAX_TEXT_LINES inside it; otherwise the control
        // goes to the next line, END-aligned.
        return if (textShare >= MIN_TEXT_SHARE && textWidthDp <= MAX_TEXT_LINES * column) {
            Placement.Beside(control)
        } else {
            Placement.NextLineEnd
        }
    }
}