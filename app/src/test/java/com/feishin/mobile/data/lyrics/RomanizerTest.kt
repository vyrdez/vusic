package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizerTest {

    @Test
    fun testJapaneseKanaAndDigraphs() {
        val input = "とうきょう" // Tokyo (Tōkyō)
        val rom = Romanizer.romanizeLine(input)
        assertEquals("tōkyō", rom)

        val inputKatakana = "コーヒー" // Coffee (kōhī)
        val romKatakana = Romanizer.romanizeLine(inputKatakana)
        assertEquals("kōhī", romKatakana)
    }

    @Test
    fun testJapaneseParticlesAndSokuon() {
        // は as particle
        val wa = Romanizer.romanizeLine("わたし は")
        assertEquals("watashi wa", wa)

        // Small tsu (sokuon)
        val kitto = Romanizer.romanizeLine("きっと")
        assertEquals("kitto", kitto)

        val matcha = Romanizer.romanizeLine("まっちゃ")
        assertEquals("matcha", matcha)
    }

    @Test
    fun testJapaneseLongVowelsAndN() {
        val arigato = Romanizer.romanizeLine("ありがとう")
        assertEquals("arigatō", arigato)

        val ramen = Romanizer.romanizeLine("ラーメン")
        assertEquals("rāmen", ramen)

        val konin = Romanizer.romanizeLine("こんいん")
        assertEquals("kon'in", konin)
    }

    @Test
    fun testKoreanHangulSyllablesAndBoundaries() {
        // Basic Hangul
        val annyeong = Romanizer.romanizeLine("안녕")
        assertEquals("annyeong", annyeong)

        // Resyllabification before silent initial ㅇ (한국어 -> hangugeo)
        val hangugeo = Romanizer.romanizeLine("한국어")
        assertEquals("hangugeo", hangugeo)

        // Lateralization (설날 -> seollal)
        val seollal = Romanizer.romanizeLine("설날")
        assertEquals("seollal", seollal)

        // Nasalization (감사합니다 -> gamsahamnida)
        val gamsahamnida = Romanizer.romanizeLine("감사합니다")
        assertEquals("gamsahamnida", gamsahamnida)
    }

    @Test
    fun testChinesePinyinWithTones() {
        val nihao = Romanizer.romanizeLine("你好")
        assertEquals("nǐ hǎo", nihao)

        val woaini = Romanizer.romanizeLine("我爱你")
        assertEquals("wǒ ài nǐ", woaini)
    }

    @Test
    fun testDetectionOfJapaneseVsChinese() {
        // Japanese line containing Kanji and Kana: Kanji must NOT be converted to Chinese Pinyin!
        val jpLine = "愛してる"
        val jpRom = Romanizer.romanizeLine(jpLine)
        assertTrue("Japanese line should contain Hepburn kana and keep kanji", jpRom?.contains("shiteru") == true)
        assertFalse("Japanese line must not convert kanji to Chinese pinyin", jpRom?.contains("ài") == true)

        // Pure Chinese line (no Kana) -> converts Hanzi to Pinyin
        val cnLine = "全世界"
        val cnRom = Romanizer.romanizeLine(cnLine)
        assertEquals("quán shì jiè", cnRom)
    }

    @Test
    fun testMixedLinesAndEdgeCases() {
        // Mixed English and Chinese
        val mixed = Romanizer.romanizeLine("Hello 世界 123")
        assertEquals("Hello shì jiè 123", mixed)

        // Non-romanizable lines (English, punctuation, note symbol)
        assertNull(Romanizer.romanizeLine("Never gonna give you up"))
        assertNull(Romanizer.romanizeLine("♪"))
        assertNull(Romanizer.romanizeLine("   "))
        assertNull(Romanizer.romanizeLine("12345!"))
    }

    @Test
    fun testProviderSuppliedRomanizationPreferred() {
        val original = Lyrics(
            lines = listOf(
                LyricLine(startMs = 0L, text = "你好", romanizedText = "Custom Provider Romanization"),
                LyricLine(startMs = 1000L, text = "世界", romanizedText = null),
            )
        )

        val result = Romanizer.romanizeLyrics(original)
        assertEquals("Custom Provider Romanization", result.lines[0].romanizedText)
        assertEquals("shì jiè", result.lines[1].romanizedText)
    }

    @Test
    fun testTrackRomanizationRules() {
        val defaultSettings = com.feishin.mobile.data.settings.SettingsStore.Settings()
        // Default: auto is false, track not romanized
        assertFalse(com.feishin.mobile.data.settings.SettingsStore.isTrackRomanized("track1", hasCjk = true, defaultSettings))

        // Auto is true: romanizes if hasCjk is true
        val autoSettings = defaultSettings.copy(lyricsRomanizeAutomatically = true)
        assertTrue(com.feishin.mobile.data.settings.SettingsStore.isTrackRomanized("track1", hasCjk = true, autoSettings))
        assertFalse(com.feishin.mobile.data.settings.SettingsStore.isTrackRomanized("track1", hasCjk = false, autoSettings))

        // Track manually overridden ON
        val manualOnSettings = defaultSettings.copy(lyricsRomanizedTrackIds = setOf("track1"))
        assertTrue(com.feishin.mobile.data.settings.SettingsStore.isTrackRomanized("track1", hasCjk = true, manualOnSettings))

        // Track manually overridden OFF even when auto is true
        val manualOffSettings = autoSettings.copy(lyricsDeRomanizedTrackIds = setOf("track1"))
        assertFalse(com.feishin.mobile.data.settings.SettingsStore.isTrackRomanized("track1", hasCjk = true, manualOffSettings))
    }
}
