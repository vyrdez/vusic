package com.feishin.mobile.ui.player

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsMenuTest {

    @Test
    fun parseTextSizeToPercent_handlesLegacyAndPercentages() {
        assertEquals(85, SettingsStore.parseLyricsTextSizePercent("SMALL"))
        assertEquals(100, SettingsStore.parseLyricsTextSizePercent("MEDIUM"))
        assertEquals(125, SettingsStore.parseLyricsTextSizePercent("LARGE"))

        assertEquals(100, SettingsStore.parseLyricsTextSizePercent("100%"))
        assertEquals(150, SettingsStore.parseLyricsTextSizePercent("150%"))
        assertEquals(80, SettingsStore.parseLyricsTextSizePercent("70%")) // clamped min 80
        assertEquals(220, SettingsStore.parseLyricsTextSizePercent("250%")) // clamped max 220
    }

    @Test
    fun lyricsOffset_adjustsTimingAccurately() {
        val lines = listOf(
            LyricLine(startMs = 1000, text = "Line 1"),
            LyricLine(startMs = 3000, text = "Line 2"),
        )
        val original = Lyrics(lines = lines, offsetMs = 0)
        assertEquals(0, original.lineIndexAt(1000))
        assertEquals(0, original.lineIndexAt(2500))
        assertEquals(1, original.lineIndexAt(3000))

        // +500ms offset means line appears 500ms earlier relative to audio position
        val shifted = original.copy(offsetMs = 500)
        // position 2600 - 500 = 2100 -> line 0
        assertEquals(0, shifted.lineIndexAt(2600))
        // position 3500 - 500 = 3000 -> line 1
        assertEquals(1, shifted.lineIndexAt(3500))
    }
}
