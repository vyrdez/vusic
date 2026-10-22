package com.feishin.mobile.core.network

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Decodes and parses KuGou KRC (Karaoke) encrypted & compressed lyrics format into [Lyrics].
 * Supports word/syllable-level timestamps (`<relStart, duration, 0>word`).
 */
object KrcLyricsParser {

    private val KRC_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
    )

    private val linePattern = Pattern.compile("""^\[(\d+),(\d+)\](.*)$""")
    private val wordPattern = Pattern.compile("""<(\d+),(\d+),(\d+)>([^<]*)""")
    private val offsetPattern = Pattern.compile("""^\[offset:\s*([+-]?\d+)\]""", Pattern.CASE_INSENSITIVE)

    fun parseBase64(
        base64Content: String,
        source: LyricsSource = LyricsSource.KUGOU,
    ): Lyrics {
        if (base64Content.isBlank()) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }
        val rawBytes = runCatching {
            Base64.decode(base64Content, Base64.DEFAULT)
        }.getOrNull() ?: return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)

        return parseBytes(rawBytes, source)
    }

    fun parseBytes(
        rawBytes: ByteArray,
        source: LyricsSource = LyricsSource.KUGOU,
    ): Lyrics {
        if (rawBytes.size < 4) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }

        val decrypted = decryptKrc(rawBytes) ?: return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        val text = decompressZlib(decrypted) ?: return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)

        return parseText(text, source)
    }

    fun parseText(
        krcText: String,
        source: LyricsSource = LyricsSource.KUGOU,
    ): Lyrics {
        if (krcText.isBlank()) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }

        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()

        krcText.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // Check offset tag
            val offsetMatcher = offsetPattern.matcher(trimmed)
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                return@forEach
            }

            // Timed line: [start_ms, duration_ms]<rel_start, dur, 0>word...
            val lineMatcher = linePattern.matcher(trimmed)
            if (lineMatcher.matches()) {
                val lineStartMs = lineMatcher.group(1)?.toLongOrNull() ?: return@forEach
                val lineBody = lineMatcher.group(3).orEmpty()

                val words = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()
                val wordMatcher = wordPattern.matcher(lineBody)

                while (wordMatcher.find()) {
                    val relStart = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                    val wordText = wordMatcher.group(4).orEmpty()

                    if (wordText.isNotEmpty()) {
                        words.add(LyricWord(startMs = lineStartMs + relStart, text = wordText))
                        lineTextBuilder.append(wordText)
                    }
                }

                val lineText = if (words.isNotEmpty()) {
                    lineTextBuilder.toString().trim()
                } else {
                    lineBody.replace(Regex("""<[^>]+>"""), "").trim()
                }

                if (lineText.isNotEmpty()) {
                    lines.add(LyricLine(startMs = lineStartMs, text = lineText, words = words))
                }
            }
        }

        if (lines.isEmpty()) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }

        return Lyrics(
            lines = lines.sortedBy { it.startMs },
            offsetMs = offsetMs,
            kind = LyricsKind.SYNCED,
            source = source,
        )
    }

    /**
     * KuGou KRC XOR decryption:
     * First 4 bytes are magic "krc1".
     * Starting at index 4, each byte is XOR-ed with KRC_KEY[(i - 4) % 16].
     */
    fun decryptKrc(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) return null
        val offset = if (bytes[0] == 'k'.code.toByte() &&
            bytes[1] == 'r'.code.toByte() &&
            bytes[2] == 'c'.code.toByte() &&
            bytes[3] == '1'.code.toByte()
        ) 4 else 0

        val decrypted = ByteArray(bytes.size - offset)
        for (i in offset until bytes.size) {
            val keyByte = KRC_KEY[(i - offset) % KRC_KEY.size]
            decrypted[i - offset] = (bytes[i].toInt() xor keyByte.toInt()).toByte()
        }
        return decrypted
    }

    /**
     * Decompresses ZLIB/Deflate payload into UTF-8 text.
     */
    fun decompressZlib(data: ByteArray): String? {
        // Try standard ZLIB Inflater first
        runCatching {
            val inflater = Inflater(false)
            val buffer = ByteArray(4096)
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            inflater.end()
            return out.toString(StandardCharsets.UTF_8.name())
        }

        // Try nowrap (raw deflate) if ZLIB header was omitted
        runCatching {
            val inflater = Inflater(true)
            val buffer = ByteArray(4096)
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            inflater.end()
            return out.toString(StandardCharsets.UTF_8.name())
        }

        return null
    }
}
