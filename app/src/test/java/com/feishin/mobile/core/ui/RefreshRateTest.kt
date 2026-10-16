package com.feishin.mobile.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 7.0d: high-refresh-rate mode selection. The emulator exposes a single
 * 60 Hz mode, so this logic is verified here instead of on device.
 */
class RefreshRateTest {

    private val only60 = listOf(
        DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 60f),
    )

    private val res6090 = listOf(
        DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 60f),
        DisplayModeInfo(id = 2, width = 1080, height = 2400, refreshRate = 90f),
    )

    private val res6090120 = listOf(
        DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 60f),
        DisplayModeInfo(id = 2, width = 1080, height = 2400, refreshRate = 90f),
        DisplayModeInfo(id = 3, width = 1080, height = 2400, refreshRate = 120f),
    )

    @Test
    fun maxPicksHighestSameResolutionRate() {
        assertEquals(
            3,
            RefreshRate.selectModeId(res6090120, 1080, 2400, 1, RefreshRateMode.MAX),
        )
        assertEquals(
            2,
            RefreshRate.selectModeId(res6090, 1080, 2400, 1, RefreshRateMode.MAX),
        )
    }

    @Test
    fun autoPicksHighestSameResolutionRate() {
        assertEquals(
            3,
            RefreshRate.selectModeId(res6090120, 1080, 2400, 1, RefreshRateMode.AUTO),
        )
    }

    @Test
    fun standardPicks60() {
        assertEquals(
            1,
            RefreshRate.selectModeId(res6090120, 1080, 2400, 3, RefreshRateMode.STANDARD),
        )
    }

    @Test
    fun higherRateAtLowerResolutionNeverWins() {
        val modes = listOf(
            DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 60f),
            DisplayModeInfo(id = 7, width = 720, height = 1600, refreshRate = 120f),
        )
        assertEquals(
            1,
            RefreshRate.selectModeId(modes, 1080, 2400, 1, RefreshRateMode.MAX),
        )
    }

    @Test
    fun tiePrefersCurrentMode() {
        val modes = listOf(
            DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 120f),
            DisplayModeInfo(id = 2, width = 1080, height = 2400, refreshRate = 120f),
        )
        assertEquals(
            2,
            RefreshRate.selectModeId(modes, 1080, 2400, 2, RefreshRateMode.MAX),
        )
    }

    @Test
    fun single60IsNoOp() {
        assertEquals(
            1,
            RefreshRate.selectModeId(only60, 1080, 2400, 1, RefreshRateMode.MAX),
        )
        assertEquals(
            1,
            RefreshRate.selectModeId(only60, 1080, 2400, 1, RefreshRateMode.STANDARD),
        )
    }

    @Test
    fun unknownCurrentFallsBackToCurrentId() {
        assertEquals(
            9,
            RefreshRate.selectModeId(emptyList(), 1080, 2400, 9, RefreshRateMode.MAX),
        )
    }

    @Test
    fun settingHiddenWithSingleRate() {
        assertFalse(RefreshRate.shouldShowSetting(only60))
        assertTrue(RefreshRate.shouldShowSetting(res6090))
        assertTrue(RefreshRate.shouldShowSetting(res6090120))
        // Same rate twice (different ids) is still a single rate: hide.
        assertFalse(
            RefreshRate.shouldShowSetting(
                listOf(
                    DisplayModeInfo(id = 1, width = 1080, height = 2400, refreshRate = 60f),
                    DisplayModeInfo(id = 2, width = 1080, height = 2400, refreshRate = 60f),
                ),
            ),
        )
    }
}
