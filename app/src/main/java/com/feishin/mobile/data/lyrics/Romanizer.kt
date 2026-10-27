package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import java.io.DataInputStream
import java.io.InputStream

/**
 * On-device romanization engine for Japanese (Hepburn romaji), Korean (Revised Romanization),
 * and Chinese (Hanyu Pinyin with tone marks).
 *
 * Runs 100% offline with zero online requests.
 */
object Romanizer {

    private const val PINYIN_START_CP = 0x4E00
    private const val PINYIN_END_CP = 0x9FA5
    private const val PINYIN_COUNT = PINYIN_END_CP - PINYIN_START_CP + 1

    @Volatile
    private var pinyinWords: Array<String>? = null

    @Volatile
    private var pinyinMap: ShortArray? = null

    private val lock = Any()

    fun initPinyin(stream: InputStream?) {
        if (pinyinMap != null || stream == null) return
        synchronized(lock) {
            if (pinyinMap != null) return
            try {
                DataInputStream(stream.buffered()).use { dis ->
                    val wordCount = dis.readUnsignedShort()
                    val words = Array(wordCount) { "" }
                    for (i in 0 until wordCount) {
                        val len = dis.readUnsignedByte()
                        val bytes = ByteArray(len)
                        dis.readFully(bytes)
                        words[i] = String(bytes, Charsets.UTF_8)
                    }
                    val map = ShortArray(PINYIN_COUNT)
                    for (i in 0 until PINYIN_COUNT) {
                        map[i] = dis.readShort()
                    }
                    pinyinWords = words
                    pinyinMap = map
                }
            } catch (_: Exception) {
                // Ignore failure, falls back to unromanized Hanzi
            }
        }
    }

    private fun ensurePinyinLoaded() {
        if (pinyinMap != null) return
        val stream = Romanizer::class.java.classLoader?.getResourceAsStream("lyrics/pinyin.bin")
            ?: Romanizer::class.java.getResourceAsStream("/lyrics/pinyin.bin")
            ?: Romanizer::class.java.getResourceAsStream("/assets/lyrics/pinyin.bin")
        initPinyin(stream)
    }

    private val CJK_REGEX = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")

    private var tokenizerInstance: com.atilika.kuromoji.ipadic.Tokenizer? = null
    private var tokenizerInitFailed = false

    @Synchronized
    private fun getTokenizer(): com.atilika.kuromoji.ipadic.Tokenizer? {
        if (tokenizerInitFailed) return null
        if (tokenizerInstance == null) {
            try {
                tokenizerInstance = com.atilika.kuromoji.ipadic.Tokenizer()
            } catch (t: Throwable) {
                tokenizerInitFailed = true
                android.util.Log.e("Romanizer", "Failed to initialize Kuromoji tokenizer", t)
            }
        }
        return tokenizerInstance
    }

    private const val MAX_CACHE_SIZE = 1000
    private val lineCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE_SIZE
        }
    )

    fun isValidProviderRomanization(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return !CJK_REGEX.containsMatchIn(raw)
    }

    /**
     * Romanizes [lyrics]. Provider-supplied romanization on [LyricLine.romanizedText] takes
     * priority if valid; lines without valid romanization are processed on-device.
     */
    fun romanizeLyrics(lyrics: Lyrics): Lyrics {
        if (lyrics.lines.isEmpty()) return lyrics
        val updatedLines = lyrics.lines.map { line ->
            val providerRom = line.romanizedText
            if (isValidProviderRomanization(providerRom)) {
                line
            } else {
                val romanized = romanizeLine(line.text)
                if (romanized != null && romanized != line.text) {
                    line.copy(romanizedText = romanized)
                } else {
                    line.copy(romanizedText = null)
                }
            }
        }
        return lyrics.copy(lines = updatedLines)
    }

    /**
     * Romanizes a single line of text.
     * Returns null if the line contains no romanizable CJK/Hangul characters.
     */
    fun romanizeLine(text: String): String? {
        if (text.isBlank()) return null
        if (!hasRomanizableScript(text)) return null

        val cached = lineCache[text]
        if (cached != null) return cached

        val hasKana = containsKana(text)
        val hasHangul = containsHangul(text)

        val result = when {
            hasHangul -> romanizeKorean(text)
            hasKana -> romanizeJapanese(text)
            else -> {
                val tok = getTokenizer()
                val tokens = if (tok != null) runCatching { tok.tokenize(text) }.getOrNull() else null
                val allKnownJp = !tokens.isNullOrEmpty() && tokens.all {
                    it.isKnown && !it.reading.isNullOrBlank() && it.reading != "*"
                }
                if (allKnownJp) {
                    romanizeJapanese(text)
                } else {
                    romanizeChinese(text)
                }
            }
        }
        lineCache[text] = result
        return result
    }

    fun hasRomanizableScript(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isKana(cp) || isHangul(cp) || isHan(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isKana(cp: Int): Boolean =
        (cp in 0x3041..0x3096) || (cp in 0x30A1..0x30FA) || cp == 0x30FC

    private fun isHangul(cp: Int): Boolean =
        (cp in 0xAC00..0xD7A3) || (cp in 0x1100..0x11FF) || (cp in 0x3130..0x318F)

    private fun isHan(cp: Int): Boolean =
        (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF)

    fun containsKana(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isKana(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun containsHangul(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isHangul(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /* ---------------- Japanese Kana -> Hepburn Romaji ---------------- */

    private val DIGRAPHS = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "シェ" to "she", "ジェ" to "je", "チェ" to "che",
        "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "フュ" to "fyu",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴ" to "vu", "ヴェ" to "ve", "ヴォ" to "vo",
    )

    private val SINGLE_KANA = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "o",
        'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        // Katakana
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o",
        'ン' to "n",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ヴ' to "vu",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
    )

    private fun toKatakana(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (c in '\u3041'..'\u3096') {
                sb.append((c.code + 0x60).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun katakanaToHepburn(raw: String): String {
        val s = toKatakana(raw)
        val sb = StringBuilder()
        var i = 0
        var sokuon = false

        while (i < s.length) {
            val c = s[i]

            // Sokuon ッ
            if (c == 'ッ') {
                sokuon = true
                i++
                continue
            }

            // Chōonpu ー
            if (c == 'ー') {
                if (sb.isNotEmpty()) {
                    val prev = sb.last()
                    when (prev) {
                        'a' -> sb.append('a')
                        'i' -> sb.append('i')
                        'u' -> sb.append('u')
                        'e' -> sb.append('e')
                        'o' -> sb.append('u')
                        else -> sb.append(prev)
                    }
                }
                i++
                continue
            }

            // Digraphs (2 kana)
            if (i + 1 < s.length) {
                val pair = s.substring(i, i + 2)
                val digraph = DIGRAPHS[pair]
                if (digraph != null) {
                    appendHepburn(sb, digraph, sokuon)
                    sokuon = false
                    i += 2
                    continue
                }
            }

            // Hatsun ン
            if (c == 'ン') {
                val next = s.getOrNull(i + 1)
                if (next != null && isVowelOrYKana(next)) {
                    appendHepburn(sb, "n'", sokuon)
                } else {
                    appendHepburn(sb, "n", sokuon)
                }
                sokuon = false
                i++
                continue
            }

            // Single Kana
            val single = SINGLE_KANA[c]
            if (single != null) {
                appendHepburn(sb, single, sokuon)
                sokuon = false
                i++
                continue
            }

            // Other character (Latin, punctuation, etc.)
            sokuon = false
            sb.append(c)
            i++
        }

        val out = sb.toString()
        return if (CJK_REGEX.containsMatchIn(out)) {
            romanizeChinese(out)
        } else {
            out
        }
    }

    private fun romanizeJapanese(text: String): String {
        val tok = getTokenizer()
        val tokens = if (tok != null) runCatching { tok.tokenize(text) }.getOrNull() else null
        if (tokens.isNullOrEmpty()) {
            return katakanaToHepburn(text)
        }
        val words = mutableListOf<String>()

        for (token in tokens) {
            val surface = token.surface
            if (surface.isBlank()) continue

            val pos1 = token.partOfSpeechLevel1
            val wordRom = if (pos1 == "助詞" && (surface == "は" || surface == "へ" || surface == "を")) {
                when (surface) {
                    "は" -> "wa"
                    "へ" -> "e"
                    "を" -> "o"
                    else -> surface
                }
            } else {
                val reading = token.reading
                val raw = if (!reading.isNullOrEmpty() && reading != "*") reading else surface
                katakanaToHepburn(raw)
            }

            val trimmed = wordRom.trim()
            if (trimmed.isNotEmpty()) {
                words.add(trimmed)
            }
        }

        return words.joinToString(" ")
    }

    private fun appendHepburn(sb: StringBuilder, rom: String, sokuon: Boolean) {
        if (sokuon && rom.isNotEmpty()) {
            if (rom.startsWith("ch")) {
                sb.append('t')
            } else {
                val first = rom.first()
                if (first in "bcdfghjklmnprstvwxyz") {
                    sb.append(first)
                }
            }
        }
        sb.append(rom)
    }

    private fun isVowelOrYKana(c: Char): Boolean =
        c in "アイウエオヤユヨァィゥェォャュョ"

    /* ---------------- Korean Hangul -> Revised Romanization ---------------- */

    private val INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )

    private val MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )

    private val FINALS = arrayOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t",
    )

    private fun romanizeKorean(text: String): String {
        val sb = StringBuilder()
        var i = 0
        var nextInitialOverride: String? = null

        while (i < text.length) {
            val c = text[i]
            val cp = c.code

            if (cp in 0xAC00..0xD7A3) {
                val sIndex = cp - 0xAC00
                val lIndex = sIndex / 588
                val vIndex = (sIndex % 588) / 28
                val tIndex = sIndex % 28

                val initialRom = nextInitialOverride ?: INITIALS[lIndex]
                nextInitialOverride = null
                val medialRom = MEDIALS[vIndex]

                sb.append(initialRom)
                sb.append(medialRom)

                // Final consonant with boundary lookahead
                if (tIndex != 0) {
                    val nextChar = text.getOrNull(i + 1)
                    val nextCp = nextChar?.code ?: 0

                    if (nextCp in 0xAC00..0xD7A3) {
                        val nextSIndex = nextCp - 0xAC00
                        val nextLIndex = nextSIndex / 588

                        // 1. Resyllabification before silent initial ㅇ (lIndex == 11)
                        if (nextLIndex == 11) {
                            val carryOver = when (tIndex) {
                                1 -> "g" // ㄱ
                                4 -> "n" // ㄴ
                                7 -> "d" // ㄷ
                                8 -> "r" // ㄹ
                                16 -> "m" // ㅁ
                                17 -> "b" // ㅂ
                                19 -> "s" // ㅅ
                                20 -> "ss" // ㅆ
                                22 -> "j" // ㅈ
                                23 -> "ch" // ㅊ
                                24 -> "k" // ㅋ
                                25 -> "t" // ㅌ
                                26 -> "p" // ㅍ
                                27 -> "h" // ㅎ
                                else -> FINALS[tIndex]
                            }
                            sb.append(carryOver)
                            i++
                            continue
                        }

                        // 2. Lateralization (ㄹ + ㄹ -> ll, ㄴ + ㄹ -> ll, ㄹ + ㄴ -> ll)
                        if (tIndex == 8 && nextLIndex == 5) { // ㄹ + ㄹ
                            sb.append("l")
                            nextInitialOverride = "l"
                            i++
                            continue
                        }
                        if (tIndex == 4 && nextLIndex == 5) { // ㄴ + ㄹ
                            sb.append("l")
                            nextInitialOverride = "l"
                            i++
                            continue
                        }
                        if (tIndex == 8 && nextLIndex == 2) { // ㄹ + ㄴ
                            sb.append("l")
                            nextInitialOverride = "l"
                            i++
                            continue
                        }

                        // 3. Nasalization before ㄴ (2) or ㅁ (6)
                        if (nextLIndex == 2 || nextLIndex == 6) {
                            val nasalFinal = when (tIndex) {
                                1, 2, 3, 9, 24 -> "ng" // ㄱ, ㄲ, ㄳ, ㄺ, ㅋ -> ng
                                7, 19, 20, 22, 23, 25, 27 -> "n" // ㄷ, ㅅ, ㅆ, ㅈ, ㅊ, ㅌ, ㅎ -> n
                                17, 18, 26 -> "m" // ㅂ, ㅄ, ㅍ -> m
                                else -> FINALS[tIndex]
                            }
                            sb.append(nasalFinal)
                            i++
                            continue
                        }
                    }

                    sb.append(FINALS[tIndex])
                }
            } else {
                nextInitialOverride = null
                sb.append(c)
            }
            i++
        }

        return sb.toString()
    }

    /* ---------------- Chinese Hanzi -> Hanyu Pinyin with Tones ---------------- */

    private fun romanizeChinese(text: String): String {
        ensurePinyinLoaded()
        val words = pinyinWords
        val map = pinyinMap
        if (words == null || map == null) return text

        val sb = StringBuilder()
        var i = 0
        var prevWasHanzi = false

        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)

            if (cp in PINYIN_START_CP..PINYIN_END_CP) {
                val index = map[cp - PINYIN_START_CP].toInt() and 0xFFFF
                val py = if (index > 0 && index < words.size) words[index] else ""
                if (py.isNotEmpty()) {
                    if (prevWasHanzi) sb.append(' ')
                    sb.append(py)
                    prevWasHanzi = true
                } else {
                    sb.appendCodePoint(cp)
                    prevWasHanzi = false
                }
            } else {
                sb.appendCodePoint(cp)
                prevWasHanzi = false
            }
            i += charCount
        }

        return sb.toString()
    }
}
