package com.feishin.mobile.data.lyrics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.data.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizerTest {

    private val cjk = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
    private val macron = Regex("[āīūēō]")

    private fun rom(text: String): String {
        val out = Romanizer.romanizeLine(text)
        assertNotNull("expected romanization for: $text", out)
        return out!!
    }

    /** Real lyric lines exercising Kuromoji tokenization, particles, and long vowels. */
    private val fixtures = listOf(
        "愛してよ 忘れられない",
        "あたしにも貸してくれてた香水",
        "もう側にはない",
        "名前は知らないけど",
        "大好きだったこの香り",
        "鮮明な記憶まだ色褪せない",
        "此処には居ないとわかってるのに",
        "似たような空気吸うたび",
        "あなたを無意識に探してた",
        "今はただ苦しいの",
        "依存してるだけ",
        "薄れやすいはずなのに",
    )

    @Test
    fun testJapaneseFixturesHaveNoCjkNoMacronsAndAreSpaced() {
        for (line in fixtures) {
            val out = rom(line)
            assertFalse("CJK left: $line -> $out", cjk.containsMatchIn(out))
            assertFalse("macron: $line -> $out", macron.containsMatchIn(out))
            assertEquals("untrimmed: $line -> $out", out.trim(), out)
            assertFalse("double space: $line -> $out", out.contains("  "))
            assertTrue("tokens not spaced: $line -> $out", out.contains(" "))
        }
    }

    @Test
    fun testJapaneseFixtureSubstrings() {
        // Each substring stays inside one morpheme, so spacing cannot break it.
        val expected = mapOf(
            "愛してよ 忘れられない" to listOf("aishi", "wasure"),
            "あたしにも貸してくれてた香水" to listOf("atashi", "kousui"),
            "もう側にはない" to listOf("mou", "ni wa"),
            "名前は知らないけど" to listOf("namae wa"),
            "大好きだったこの香り" to listOf("daisuki", "kaori"),
            "鮮明な記憶まだ色褪せない" to listOf("senmei", "kioku"),
            "此処には居ないとわかってるのに" to listOf("koko", "ni wa"),
            "似たような空気吸うたび" to listOf("kuuki"),
            "あなたを無意識に探してた" to listOf("muishiki", "sagashi"),
            "今はただ苦しいの" to listOf("ima wa", "kurushii"),
            "依存してるだけ" to listOf("izon"),
            "薄れやすいはずなのに" to listOf("usure"),
        )
        for ((line, bits) in expected) {
            val out = rom(line)
            bits.forEach { assertTrue("expected '$it' in: $line -> $out", out.contains(it)) }
        }
    }

    @Test
    fun testParticlesAndLongVowels() {
        assertTrue(rom("名前は知らない").contains("namae wa"))
        assertTrue(rom("東京へ行く").contains("toukyou e"))
        assertTrue(rom("水を飲む").contains("mizu o"))
        assertTrue(rom("もう").contains("mou"))
        assertTrue(rom("空気").contains("kuuki"))
    }

    @Test
    fun testMixedJapaneseAndEnglish() {
        val out = rom("Baby 愛してる")
        assertTrue("Latin dropped: $out", out.contains("Baby"))
        assertTrue("kana not romanized: $out", out.contains("itoshi"))
        assertFalse(cjk.containsMatchIn(out))
    }

    @Test
    fun testFullyLatinReturnsNull() {
        assertNull(Romanizer.romanizeLine("Never gonna give you up"))
        assertNull(Romanizer.romanizeLine("Hello 123"))
        assertNull(Romanizer.romanizeLine("♪"))
        assertNull(Romanizer.romanizeLine("   "))
        assertNull(Romanizer.romanizeLine("12345!"))
    }

    @Test
    fun testHangulAndHanRomanization() {
        assertEquals("annyeong", rom("안녕"))
        assertEquals("hangugeo", rom("한국어"))
        assertEquals("gamsahamnida", rom("감사합니다"))
        assertEquals("nǐ hǎo", rom("你好"))
        assertFalse(cjk.containsMatchIn(rom("我爱你")))
    }

    @Test
    fun testProviderRomanizationSanityCheck() {
        assertTrue(Romanizer.isValidProviderRomanization("namae wa shiranai"))
        assertFalse(Romanizer.isValidProviderRomanization(null))
        assertFalse(Romanizer.isValidProviderRomanization("   "))
        assertFalse(Romanizer.isValidProviderRomanization("名前は知らない"))
        assertFalse(Romanizer.isValidProviderRomanization("안녕"))
        assertFalse(Romanizer.isValidProviderRomanization("こんにちは"))
    }

    @Test
    fun testProviderRomanizationPreferredOverOnDevice() {
        val result = Romanizer.romanizeLyrics(
            Lyrics(
                lines = listOf(
                    LyricLine(0L, "名前は知らない", romanizedText = "Namae wa shiranai"),
                    LyricLine(1000L, "名前は知らない"),
                )
            )
        )
        assertEquals("Namae wa shiranai", result.lines[0].romanizedText)
        assertTrue(result.lines[1].romanizedText?.contains("namae wa") == true)
    }

    /** Builds an isolated DataStore backed by [file] for migration checks. */
    private fun newStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun testGlobalRomanizeSettingMigratesOldPerTrackState() = runTest {
        val dir = Files.createTempDirectory("romanize-settings").toFile()
        val autoKey = booleanPreferencesKey("lyrics_romanize_automatically")
        val idsKey = stringPreferencesKey("lyrics_romanized_track_ids")
        val newKey = booleanPreferencesKey("lyrics_romanize")

        val autoOn = newStore(backgroundScope, File(dir, "a.preferences_pb"))
        autoOn.edit { it[autoKey] = true }
        assertTrue(SettingsStore(autoOn).flow.first().lyricsRomanize)

        val idsOn = newStore(backgroundScope, File(dir, "b.preferences_pb"))
        idsOn.edit { it[idsKey] = "track1" }
        assertTrue(SettingsStore(idsOn).flow.first().lyricsRomanize)

        val fresh = newStore(backgroundScope, File(dir, "c.preferences_pb"))
        assertFalse(SettingsStore(fresh).flow.first().lyricsRomanize)

        // Explicit new value beats legacy state.
        val explicitOff = newStore(backgroundScope, File(dir, "d.preferences_pb"))
        explicitOff.edit {
            it[newKey] = false
            it[autoKey] = true
            it[idsKey] = "track1"
        }
        assertFalse(SettingsStore(explicitOff).flow.first().lyricsRomanize)

        dir.deleteRecursively()
    }
}
