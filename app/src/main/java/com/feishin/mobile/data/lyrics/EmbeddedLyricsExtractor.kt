package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin parser for embedded lyrics in audio files.
 * Zero external dependencies (0 KB added to APK).
 *
 * Supported formats:
 *   - ID3v2.3 & ID3v2.4 (MP3): USLT (unsynced/LRC lyrics) and SYLT (synced lyrics).
 *   - FLAC & Ogg Vorbis: Vorbis comments LYRICS, UNSYNCEDLYRICS.
 *   - MP4 (AAC/M4A/ALAC): moov.udta.meta.ilst.©lyr atom.
 */
object EmbeddedLyricsExtractor {

    fun extract(stream: InputStream): Lyrics? {
        val header = ByteArray(12)
        stream.mark(12)
        val read = stream.read(header)
        stream.reset()
        if (read < 4) return null

        return when {
            header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() -> {
                extractId3v2(stream)
            }
            header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte() -> {
                extractFlac(stream)
            }
            header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() && header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte() -> {
                extractOgg(stream)
            }
            read >= 8 && (header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() ||
                header[4] == 'm'.code.toByte() && header[5] == 'o'.code.toByte() && header[6] == 'o'.code.toByte() && header[7] == 'v'.code.toByte()) -> {
                extractMp4(stream)
            }
            else -> null
        }
    }

    // --- ID3v2 Parser ---

    private fun extractId3v2(stream: InputStream): Lyrics? {
        val header = ByteArray(10)
        if (stream.read(header) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null

        val majorVersion = header[3].toInt() // 3 = ID3v2.3, 4 = ID3v2.4
        val flags = header[5].toInt()
        val hasExtendedHeader = (flags and 0x40) != 0

        val tagSize = (header[6].toInt() and 0x7F shl 21) or
            (header[7].toInt() and 0x7F shl 14) or
            (header[8].toInt() and 0x7F shl 7) or
            (header[9].toInt() and 0x7F)

        val tagBytes = ByteArray(tagSize)
        var offset = 0
        while (offset < tagSize) {
            val count = stream.read(tagBytes, offset, tagSize - offset)
            if (count <= 0) break
            offset += count
        }

        var pos = 0
        if (hasExtendedHeader && pos + 4 <= offset) {
            val extSize = if (majorVersion == 4) {
                (tagBytes[pos].toInt() and 0x7F shl 21) or
                    (tagBytes[pos + 1].toInt() and 0x7F shl 14) or
                    (tagBytes[pos + 2].toInt() and 0x7F shl 7) or
                    (tagBytes[pos + 3].toInt() and 0x7F)
            } else {
                (tagBytes[pos].toInt() and 0xFF shl 24) or
                    (tagBytes[pos + 1].toInt() and 0xFF shl 16) or
                    (tagBytes[pos + 2].toInt() and 0xFF shl 8) or
                    (tagBytes[pos + 3].toInt() and 0xFF)
            }
            pos += extSize.coerceAtLeast(4)
        }

        var usltLyrics: Lyrics? = null
        var syltLyrics: Lyrics? = null

        while (pos + 10 <= offset) {
            val frameId = String(tagBytes, pos, 4, StandardCharsets.ISO_8859_1)
            val frameSize = if (majorVersion == 4) {
                (tagBytes[pos + 4].toInt() and 0x7F shl 21) or
                    (tagBytes[pos + 5].toInt() and 0x7F shl 14) or
                    (tagBytes[pos + 6].toInt() and 0x7F shl 7) or
                    (tagBytes[pos + 7].toInt() and 0x7F)
            } else {
                (tagBytes[pos + 4].toInt() and 0xFF shl 24) or
                    (tagBytes[pos + 5].toInt() and 0xFF shl 16) or
                    (tagBytes[pos + 6].toInt() and 0xFF shl 8) or
                    (tagBytes[pos + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || pos + 10 + frameSize > offset) break
            val frameData = tagBytes.copyOfRange(pos + 10, pos + 10 + frameSize)

            if (frameId == "SYLT") {
                syltLyrics = parseSylt(frameData)
            } else if (frameId == "USLT" && usltLyrics == null) {
                usltLyrics = parseUslt(frameData)
            }

            pos += 10 + frameSize
        }

        // SYLT beats USLT
        return syltLyrics ?: usltLyrics
    }

    private fun parseCharset(byte: Byte): Charset = when (byte.toInt()) {
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }

    private fun parseUslt(data: ByteArray): Lyrics? {
        if (data.size < 5) return null
        val encoding = parseCharset(data[0])
        val lang = String(data, 1, 3, StandardCharsets.ISO_8859_1)

        var idx = 4
        // Skip descriptor terminated by null byte(s)
        if (encoding == StandardCharsets.UTF_16 || encoding == StandardCharsets.UTF_16BE) {
            while (idx + 1 < data.size && !(data[idx] == 0.toByte() && data[idx + 1] == 0.toByte())) {
                idx += 2
            }
            idx += 2
        } else {
            while (idx < data.size && data[idx] != 0.toByte()) {
                idx++
            }
            idx++
        }

        if (idx >= data.size) return null
        val text = String(data, idx, data.size - idx, encoding).trim()
        if (text.isBlank()) return null

        val parsed = LrcParser.parse(text, source = LyricsSource.FILE_TAGS)
        return if (parsed.lines.isNotEmpty()) {
            parsed.copy(lang = lang)
        } else {
            Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = text, lang = lang, source = LyricsSource.FILE_TAGS)
        }
    }

    private fun parseSylt(data: ByteArray): Lyrics? {
        if (data.size < 6) return null
        val encoding = parseCharset(data[0])
        val lang = String(data, 1, 3, StandardCharsets.ISO_8859_1)
        val timeFormat = data[4].toInt() // 1 = ms
        if (timeFormat != 1) return null // Only support ms timestamps

        var idx = 6
        // Skip descriptor
        if (encoding == StandardCharsets.UTF_16 || encoding == StandardCharsets.UTF_16BE) {
            while (idx + 1 < data.size && !(data[idx] == 0.toByte() && data[idx + 1] == 0.toByte())) {
                idx += 2
            }
            idx += 2
        } else {
            while (idx < data.size && data[idx] != 0.toByte()) {
                idx++
            }
            idx++
        }

        val lines = mutableListOf<LyricLine>()
        while (idx < data.size) {
            val textStart = idx
            if (encoding == StandardCharsets.UTF_16 || encoding == StandardCharsets.UTF_16BE) {
                while (idx + 1 < data.size && !(data[idx] == 0.toByte() && data[idx + 1] == 0.toByte())) {
                    idx += 2
                }
                val token = String(data, textStart, idx - textStart, encoding).trim()
                idx += 2
                if (idx + 4 <= data.size) {
                    val timeMs = (data[idx].toLong() and 0xFF shl 24) or
                        (data[idx + 1].toLong() and 0xFF shl 16) or
                        (data[idx + 2].toLong() and 0xFF shl 8) or
                        (data[idx + 3].toLong() and 0xFF)
                    idx += 4
                    lines += LyricLine(timeMs, token)
                }
            } else {
                while (idx < data.size && data[idx] != 0.toByte()) {
                    idx++
                }
                val token = String(data, textStart, idx - textStart, encoding).trim()
                idx++
                if (idx + 4 <= data.size) {
                    val timeMs = (data[idx].toLong() and 0xFF shl 24) or
                        (data[idx + 1].toLong() and 0xFF shl 16) or
                        (data[idx + 2].toLong() and 0xFF shl 8) or
                        (data[idx + 3].toLong() and 0xFF)
                    idx += 4
                    lines += LyricLine(timeMs, token)
                }
            }
        }

        if (lines.isEmpty()) return null
        return Lyrics(lines.sortedBy { it.startMs }, 0, LyricsKind.SYNCED, lang = lang, source = LyricsSource.FILE_TAGS)
    }

    // --- FLAC Parser ---

    private fun extractFlac(stream: InputStream): Lyrics? {
        val magic = ByteArray(4)
        if (stream.read(magic) < 4) return null
        if (String(magic, StandardCharsets.ISO_8859_1) != "fLaC") return null

        var isLast = false
        while (!isLast) {
            val header = ByteArray(4)
            if (stream.read(header) < 4) break
            isLast = (header[0].toInt() and 0x80) != 0
            val blockType = header[0].toInt() and 0x7F
            val length = (header[1].toInt() and 0xFF shl 16) or
                (header[2].toInt() and 0xFF shl 8) or
                (header[3].toInt() and 0xFF)

            val blockData = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = stream.read(blockData, read, length - read)
                if (n <= 0) break
                read += n
            }

            if (blockType == 4) { // VORBIS_COMMENT
                return parseVorbisComment(blockData)
            }
        }
        return null
    }

    private fun parseVorbisComment(data: ByteArray): Lyrics? {
        val bais = ByteArrayInputStream(data)
        fun readIntLe(): Int {
            val b = ByteArray(4)
            if (bais.read(b) < 4) return 0
            return (b[0].toInt() and 0xFF) or
                (b[1].toInt() and 0xFF shl 8) or
                (b[2].toInt() and 0xFF shl 16) or
                (b[3].toInt() and 0xFF shl 24)
        }

        val vendorLen = readIntLe()
        if (vendorLen < 0 || vendorLen > bais.available()) return null
        bais.skip(vendorLen.toLong())

        val userCommentCount = readIntLe()
        for (i in 0 until userCommentCount) {
            val commentLen = readIntLe()
            if (commentLen <= 0 || commentLen > bais.available()) break
            val commentBytes = ByteArray(commentLen)
            bais.read(commentBytes)
            val comment = String(commentBytes, StandardCharsets.UTF_8)
            val eqIdx = comment.indexOf('=')
            if (eqIdx > 0) {
                val key = comment.substring(0, eqIdx).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                    val value = comment.substring(eqIdx + 1).trim()
                    if (value.isNotBlank()) {
                        val parsed = LrcParser.parse(value, source = LyricsSource.FILE_TAGS)
                        return if (parsed.lines.isNotEmpty()) {
                            parsed
                        } else {
                            Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = value, source = LyricsSource.FILE_TAGS)
                        }
                    }
                }
            }
        }
        return null
    }

    // --- OGG Parser ---

    private fun extractOgg(stream: InputStream): Lyrics? {
        val pageHeader = ByteArray(27)
        if (stream.read(pageHeader) < 27) return null
        if (String(pageHeader, 0, 4, StandardCharsets.ISO_8859_1) != "OggS") return null

        val numSegments = pageHeader[26].toInt() and 0xFF
        val segmentTable = ByteArray(numSegments)
        stream.read(segmentTable)
        val packet1Size = segmentTable.sumOf { it.toInt() and 0xFF }
        stream.skip(packet1Size.toLong())

        // Second page / packet: vorbis comment
        if (stream.read(pageHeader) < 27) return null
        if (String(pageHeader, 0, 4, StandardCharsets.ISO_8859_1) != "OggS") return null
        val numSegments2 = pageHeader[26].toInt() and 0xFF
        val segmentTable2 = ByteArray(numSegments2)
        stream.read(segmentTable2)
        val packet2Size = segmentTable2.sumOf { it.toInt() and 0xFF }
        val packet2Data = ByteArray(packet2Size)
        var read = 0
        while (read < packet2Size) {
            val n = stream.read(packet2Data, read, packet2Size - read)
            if (n <= 0) break
            read += n
        }

        // Vorbis comment starts with 0x03 + "vorbis" (7 bytes)
        if (packet2Size > 7 && packet2Data[0] == 3.toByte() && String(packet2Data, 1, 6, StandardCharsets.ISO_8859_1) == "vorbis") {
            val commentData = packet2Data.copyOfRange(7, packet2Size)
            return parseVorbisComment(commentData)
        }
        return null
    }

    // --- MP4 Parser (moov.udta.meta.ilst.©lyr) ---

    private fun extractMp4(stream: InputStream): Lyrics? {
        // Search atoms recursively for ©lyr
        return findMp4AtomLyrics(stream, Long.MAX_VALUE)
    }

    private fun findMp4AtomLyrics(stream: InputStream, maxBytes: Long): Lyrics? {
        var remaining = maxBytes
        val header = ByteArray(8)
        while (remaining >= 8) {
            val n = stream.read(header)
            if (n < 8) break
            remaining -= 8
            val size = (header[0].toLong() and 0xFF shl 24) or
                (header[1].toLong() and 0xFF shl 16) or
                (header[2].toLong() and 0xFF shl 8) or
                (header[3].toLong() and 0xFF)
            val atomType = String(header, 4, 4, StandardCharsets.ISO_8859_1)
            val contentSize = (size - 8).coerceAtLeast(0)

            when (atomType) {
                "moov", "udta", "ilst" -> {
                    val result = findMp4AtomLyrics(stream, contentSize)
                    if (result != null) return result
                    remaining -= contentSize
                }
                "meta" -> {
                    // Meta atom has 4 bytes flags/version before sub-atoms
                    if (contentSize >= 4) {
                        stream.skip(4)
                        val result = findMp4AtomLyrics(stream, contentSize - 4)
                        if (result != null) return result
                        remaining -= contentSize
                    } else {
                        stream.skip(contentSize)
                        remaining -= contentSize
                    }
                }
                "©lyr" -> {
                    val atomData = ByteArray(contentSize.toInt())
                    stream.read(atomData)
                    remaining -= contentSize
                    // Data atom inside ©lyr: 4 size + 4 'data' + 4 type + 4 locale + text
                    if (atomData.size >= 16) {
                        val subType = String(atomData, 4, 4, StandardCharsets.ISO_8859_1)
                        if (subType == "data") {
                            val text = String(atomData, 16, atomData.size - 16, StandardCharsets.UTF_8).trim()
                            if (text.isNotBlank()) {
                                val parsed = LrcParser.parse(text, source = LyricsSource.FILE_TAGS)
                                return if (parsed.lines.isNotEmpty()) {
                                    parsed
                                } else {
                                    Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = text, source = LyricsSource.FILE_TAGS)
                                }
                            }
                        }
                    }
                }
                else -> {
                    stream.skip(contentSize)
                    remaining -= contentSize
                }
            }
        }
        return null
    }
}
