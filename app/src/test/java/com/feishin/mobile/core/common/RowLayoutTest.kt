package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part C: a Settings row keeps its control on the right unless the text would
 * be squeezed below 40% of the row — only then does the control wrap to the
 * next line, aligned END.
 */
class RowLayoutTest {

    private val avail = 348f // typical card inner width on a 411dp screen

    @Test
    fun roomyRow_staysBeside() {
        val text = RowLayout.estimateTextWidthDp("Connection timeout", null, 1f)
        val ctrl = RowLayout.estimateButtonWidthDp("20s", hasIcon = true, 1f)
        val p = RowLayout.decide(avail, text, ctrl)
        assertTrue(p is RowLayout.Placement.Beside)
        // The capped width never exceeds 45% of the row.
        assertTrue((p as RowLayout.Placement.Beside).controlWidthDp <= avail * 0.45f + 1e-3f)
    }

    @Test
    fun switchRow_staysBeside() {
        val text = RowLayout.estimateTextWidthDp(
            "Self-signed certificates",
            "Applies immediately, no restart.",
            1f,
        )
        val p = RowLayout.decide(avail, text, RowLayout.SWITCH_WIDTH_DP)
        assertTrue(p is RowLayout.Placement.Beside)
    }

    @Test
    fun segmentedReplayGain_staysBeside() {
        val text = RowLayout.estimateTextWidthDp("Mode", null, 1f)
        val ctrl = RowLayout.estimateSegmentedWidthDp(listOf("Off", "Track", "Album"), 1f)
        assertTrue(RowLayout.decide(avail, text, ctrl) is RowLayout.Placement.Beside)
    }

    @Test
    fun twoControls_bitrateRow_staysBeside() {
        val text = RowLayout.estimateTextWidthDp("Wi-Fi max bitrate", null, 1f)
        val w1 = RowLayout.estimateButtonWidthDp("Original", hasIcon = true, 1f)
        val w2 = RowLayout.estimateButtonWidthDp("raw", hasIcon = true, 1f)
        val ctrl = w1 + RowLayout.CONTROL_GAP_DP + w2
        // Uncapped like the real row: the pair is measured whole (207dp) and
        // still leaves the label a 36% column.
        val p = RowLayout.decide(avail, text, ctrl, capControl = false)
        assertTrue(p is RowLayout.Placement.Beside)
        assertEquals(ctrl, (p as RowLayout.Placement.Beside).controlWidthDp, 1e-3f)
    }

    @Test
    fun veryLongText_wrapsToNextLineEnd() {
        // A 200-character description cannot wrap into 3 lines beside any
        // control: the control goes to the next line, END-aligned.
        val text = RowLayout.estimateTextWidthDp("Short", "x".repeat(200), 1f)
        val ctrl = RowLayout.estimateButtonWidthDp("Open", hasIcon = false, 1f)
        assertEquals(RowLayout.Placement.NextLineEnd, RowLayout.decide(avail, text, ctrl))
    }

    @Test
    fun tinyRow_wrapsToNextLineEnd() {
        val text = RowLayout.estimateTextWidthDp("Label", "Description", 1f)
        val ctrl = RowLayout.estimateButtonWidthDp("Some long action", hasIcon = false, 1f)
        assertEquals(RowLayout.Placement.NextLineEnd, RowLayout.decide(70f, text, ctrl))
    }

    @Test
    fun largeFontScale_canFlipToNextLine() {
        // Same content at scale 1.0 stays beside on a normal row...
        // (on a 220dp row the 207dp pair genuinely cannot fit beside any text,
        // so stacking there is correct — the example assumes a phone width).
        val label = "Wi-Fi max bitrate"
        val t1 = RowLayout.estimateTextWidthDp(label, null, 1f)
        val c1 = RowLayout.estimateButtonWidthDp("Original", hasIcon = true, 1f) +
            RowLayout.CONTROL_GAP_DP +
            RowLayout.estimateButtonWidthDp("raw", hasIcon = true, 1f)
        assertTrue(RowLayout.decide(avail, t1, c1) is RowLayout.Placement.Beside)
        // ...but a narrow screen at 1.3x with a long label stacks it.
        val t2 = RowLayout.estimateTextWidthDp(
            "A very long setting label that keeps going",
            "And a description that does not help matters at all",
            1.3f,
        )
        val c2 = RowLayout.estimateButtonWidthDp("Original", hasIcon = true, 1.3f) +
            RowLayout.CONTROL_GAP_DP +
            RowLayout.estimateButtonWidthDp("raw", hasIcon = true, 1.3f)
        assertEquals(RowLayout.Placement.NextLineEnd, RowLayout.decide(220f, t2, c2))
    }

    @Test
    fun controlIsCappedAt45Percent() {
        val text = RowLayout.estimateTextWidthDp("OK", null, 1f)
        val p = RowLayout.decide(avail, text, 10_000f)
        assertTrue(p is RowLayout.Placement.Beside)
        assertEquals(avail * 0.45f, (p as RowLayout.Placement.Beside).controlWidthDp, 1e-3f)
    }

    @Test
    fun zeroOrNegativeWidth_neverBeside() {
        val text = RowLayout.estimateTextWidthDp("Label", null, 1f)
        assertEquals(RowLayout.Placement.NextLineEnd, RowLayout.decide(0f, text, 50f))
        assertEquals(RowLayout.Placement.NextLineEnd, RowLayout.decide(-10f, text, 50f))
    }

    @Test
    fun uncappedSegmented_stacksWholeWhenTooWide() {
        // Four Theme cells never squeeze: measured whole, they take ~78% of a
        // 348dp row, so the control stacks to the next line END-aligned.
        val text = RowLayout.estimateTextWidthDp("Theme", null, 1f)
        val ctrl = RowLayout.estimateSegmentedWidthDp(
            listOf("System", "Dark", "Amoled", "Light"), 1f,
        )
        assertEquals(
            RowLayout.Placement.NextLineEnd,
            RowLayout.decide(avail, text, ctrl, capControl = false),
        )
        // ...while Grid/List still fits beside the label.
        val grid = RowLayout.estimateSegmentedWidthDp(listOf("Grid", "List"), 1f)
        assertTrue(
            RowLayout.decide(avail, RowLayout.estimateTextWidthDp("Songs", null, 1f), grid, capControl = false)
                is RowLayout.Placement.Beside,
        )
    }

    @Test
    fun emptyLabelAndNoControl_staysBeside() {
        assertTrue(
            RowLayout.decide(avail, RowLayout.estimateTextWidthDp("", null, 1f), 0f)
                is RowLayout.Placement.Beside,
        )
    }

    @Test
    fun estimatesScaleWithFontScale() {
        val b1 = RowLayout.estimateButtonWidthDp("Browse", hasIcon = false, 1f)
        val b2 = RowLayout.estimateButtonWidthDp("Browse", hasIcon = false, 1.3f)
        assertEquals(b1 * 1.3f, b2, 1e-3f)
        val s1 = RowLayout.estimateSegmentedWidthDp(listOf("Grid", "List"), 1f)
        val s2 = RowLayout.estimateSegmentedWidthDp(listOf("Grid", "List"), 1.3f)
        assertEquals(s1 * 1.3f, s2, 1e-3f)
    }

    @Test
    fun realRows_allStayBesideAtDefaultScale() {
        // Every row on the current screens, with realistic card widths.
        data class Row(val label: String, val desc: String?, val ctrl: Float)
        val rows = listOf(
            Row("Connection timeout", null, RowLayout.estimateButtonWidthDp("20s", true, 1f)),
            Row("Reset to defaults", "Restore this screen's original settings", RowLayout.estimateButtonWidthDp("Reset", false, 1f)),
            Row(
                "Battery optimization", "Not exempted — playback may pause in background",
                RowLayout.estimateButtonWidthDp("Exempt", false, 1f),
            ),
            Row("Browse downloaded music", null, RowLayout.estimateButtonWidthDp("Browse", false, 1f)),
            Row(
                "Mix & radio length", "Songs per generated mix and radio batch.",
                RowLayout.estimateButtonWidthDp("20 songs", true, 1f),
            ),
            Row("Mode", null, RowLayout.estimateSegmentedWidthDp(listOf("Off", "Track", "Album"), 1f)),
            Row("Songs", null, RowLayout.estimateSegmentedWidthDp(listOf("Grid", "List"), 1f)),
            Row("Download original files", null, RowLayout.SWITCH_WIDTH_DP),
            Row("Change server URL", "https://server.example.com", RowLayout.estimateButtonWidthDp("Edit", false, 1f)),
        )
        for (r in rows) {
            val text = RowLayout.estimateTextWidthDp(r.label, r.desc, 1f)
            assertTrue(
                "${r.label} should stay beside",
                RowLayout.decide(avail, text, r.ctrl) is RowLayout.Placement.Beside,
            )
        }
    }
}