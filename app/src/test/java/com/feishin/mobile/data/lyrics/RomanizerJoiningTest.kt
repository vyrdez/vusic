package com.feishin.mobile.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizerJoiningTest {

    private val cjk = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
    private val macron = Regex("[āīūēō]")

    private fun rom(text: String): String {
        val out = Romanizer.romanizeLine(text)
        assertNotNull("expected romanization for: $text", out)
        return out!!
    }

    private val fixtures = listOf(
        "あなたを無意識に探してた",
        "此処には居ないとわかってるのに",
        "似たような空気吸うたび",
        "今はただ苦しいの",
        "依存してるだけ",
        "薄れやすいはずなのに",
    )

    @Test
    fun testExactOutputFixtures() {
        assertEquals("anata o muishiki ni sagashiteta", rom("あなたを無意識に探してた"))
        assertEquals("ima wa tada kurushii no", rom("今はただ苦しいの"))
    }

    @Test
    fun testSubstringsJoinedFixtures() {
        val koko = rom("此処には居ないとわかってるのに")
        assertTrue("expected 'ni wa' in: $koko", koko.contains("ni wa"))
        assertTrue("expected 'inai' in: $koko", koko.contains("inai"))
        assertTrue("expected 'wakatteru' in: $koko", koko.contains("wakatteru"))

        val nita = rom("似たような空気吸うたび")
        assertTrue("expected 'nita' in: $nita", nita.contains("nita"))
        assertTrue("expected 'kuuki' in: $nita", nita.contains("kuuki"))

        val izon = rom("依存してるだけ")
        assertTrue("expected 'shiteru' in: $izon", izon.contains("shiteru"))

        val usure = rom("薄れやすいはずなのに")
        assertTrue("expected 'usureyasui' in: $usure", usure.contains("usureyasui"))
    }

    @Test
    fun testPropertiesNoCjkNoMacronsLowercaseNoDoubleSpaces() {
        for (line in fixtures) {
            val out = rom(line)
            assertFalse("CJK left in: $out", cjk.containsMatchIn(out))
            assertFalse("macron in: $out", macron.containsMatchIn(out))
            assertEquals("lowercase: $out", out.lowercase(), out)
            assertFalse("double space in: $out", out.contains("  "))
            assertEquals("untrimmed: $out", out.trim(), out)
        }
    }

    @Test
    fun testParticlesRemainSeparate() {
        val line1Words = rom("あなたを無意識に探してた").split(" ")
        assertTrue("particle 'o' separate", line1Words.contains("o"))
        assertTrue("particle 'ni' separate", line1Words.contains("ni"))

        val line2Words = rom("今はただ苦しいの").split(" ")
        assertTrue("particle 'wa' separate", line2Words.contains("wa"))
        assertTrue("particle 'no' separate", line2Words.contains("no"))

        val line3 = rom("此処には居ないとわかってるのに")
        assertTrue("particles 'ni wa' separate", line3.contains("ni wa"))
    }
}
