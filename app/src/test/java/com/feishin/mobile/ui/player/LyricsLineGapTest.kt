package com.feishin.mobile.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLineGapTest {

    private fun parseLyricsLineGapPercent(raw: Int?): Int = (raw ?: 100).coerceIn(50, 200)

    private fun lineGap(percent: Int): Dp = (22f * (percent.coerceIn(50, 200) / 100f)).dp

    @Test
    fun defaultPercent_isOneHundred() {
        assertEquals(100, parseLyricsLineGapPercent(null))
    }

    @Test
    fun clamping_restrictsToFiftyThroughTwoHundred() {
        assertEquals(50, parseLyricsLineGapPercent(40))
        assertEquals(50, parseLyricsLineGapPercent(50))
        assertEquals(100, parseLyricsLineGapPercent(100))
        assertEquals(200, parseLyricsLineGapPercent(200))
        assertEquals(200, parseLyricsLineGapPercent(250))
    }

    @Test
    fun tenPercentSteps_adjustValuesCorrectly() {
        val initial = 100
        val steppedDown = parseLyricsLineGapPercent(initial - 10)
        val steppedUp = parseLyricsLineGapPercent(initial + 10)
        assertEquals(90, steppedDown)
        assertEquals(110, steppedUp)

        // Step below minimum clamps to 50
        assertEquals(50, parseLyricsLineGapPercent(50 - 10))
        // Step above maximum clamps to 200
        assertEquals(200, parseLyricsLineGapPercent(200 + 10))
    }

    @Test
    fun lineGapDpCalculation_computesProportionalValues() {
        assertEquals(22.dp, lineGap(100))
        assertEquals(11.dp, lineGap(50))
        assertEquals(44.dp, lineGap(200))
        assertEquals(33.dp, lineGap(150))
        // Clamping within lineGap
        assertEquals(11.dp, lineGap(10))
        assertEquals(44.dp, lineGap(300))
    }

    @Test
    fun autoScrollAnchor_scalesAcrossGapChanges() {
        val viewportHeight = 1000
        val baseItemHeight = 40
        // Target index 5 with different gap sizes added to item height
        val gap100 = 22
        val gap50 = 11
        val gap200 = 44

        val offset100 = computeScrollOffset(5, viewportHeight, baseItemHeight + gap100)
        val offset50 = computeScrollOffset(5, viewportHeight, baseItemHeight + gap50)
        val offset200 = computeScrollOffset(5, viewportHeight, baseItemHeight + gap200)

        // 5 * 62 - 350 = 310 - 350 = -40 -> coerced to 0
        assertEquals(0, offset100)
        // For higher index, verify proportional scale with anchor ratio
        val targetIndex = 15
        val higherOffset100 = computeScrollOffset(targetIndex, viewportHeight, baseItemHeight + gap100)
        val higherOffset50 = computeScrollOffset(targetIndex, viewportHeight, baseItemHeight + gap50)
        val higherOffset200 = computeScrollOffset(targetIndex, viewportHeight, baseItemHeight + gap200)

        assertEquals(15 * 62 - 350, higherOffset100)
        assertEquals(15 * 51 - 350, higherOffset50)
        assertEquals(15 * 84 - 350, higherOffset200)
    }
}
