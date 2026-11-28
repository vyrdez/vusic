package com.feishin.mobile.data.remote.drive

import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.lyrics.EmbeddedLyricsExtractor
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class EmbeddedArtDescriptor(
    val offset: Long,
    val size: Long,
    val mimeType: String,
)

data class ParsedAudioTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNo: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationSec: Int = 0,
    val bitRateKbps: Int? = null,
    val embeddedArt: EmbeddedArtDescriptor? = null,
    val lyrics: Lyrics? = null,
    val tagsFound: Boolean = false,
)

data class Id3v2HeaderInfo(
    val majorVersion: Int,
    val flags: Int,
    val tagSize: Int,
) {
    val totalTagSpanBytes: Long get() = 10L + tagSize.toLong()
}

data class Id3v2ParseResult(
    val tags: ParsedAudioTags,
    val nextFrameOffset: Long?,
    val id3EndOffset: Long,
)

data class FlacParseResult(
    val tags: ParsedAudioTags,
    val nextBlockOffset: Long?,
    val reachedLastBlock: Boolean,
    val reachedVorbisComment: Boolean,
    val truncatedVorbisCommentOffset: Long? = null,
    val truncatedVorbisCommentTotalBytes: Long = 0L,
)

data class ResolvedTrackMetadata(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNo: Int?,
    val year: Int?,
    val genre: String?,
    val durationSec: Int,
    val bitRate: Int?,
    val suffix: String?,
    val lyrics: Lyrics?,
    val tagsParsed: Boolean,
)

/**
 * Pure Kotlin parser for audio container headers, metadata tags, durations, bitrates,
 * embedded art descriptors, and deterministic filename/folder fallback inference.
 */
object DriveTagParser {

    private val TRACK_PREFIX_REGEX = Regex("""^(\d{1,3})\s*[-._)]\s*(.+)$""")
    private val YEAR_REGEX = Regex("""(\d{4})""")
    private val ID3_GENRE_REF_REGEX = Regex("""^\((\d+)\)(.*)$""")

    // --- Public Inspection & Parsing Entry Points ---

    fun inspectId3v2Header(buffer: ByteArray): Id3v2HeaderInfo? {
        if (buffer.size < 10) return null
        if (buffer[0] != 'I'.code.toByte() || buffer[1] != 'D'.code.toByte() || buffer[2] != '3'.code.toByte()) {
            return null
        }
        val major = buffer[3].toInt() and 0xFF
        if (major !in 3..4) return null
        val b6 = buffer[6].toInt() and 0xFF
        val b7 = buffer[7].toInt() and 0xFF
        val b8 = buffer[8].toInt() and 0xFF
        val b9 = buffer[9].toInt() and 0xFF
        if ((b6 or b7 or b8 or b9) and 0x80 != 0) return null
        val tagSize = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
        return Id3v2HeaderInfo(majorVersion = major, flags = buffer[5].toInt() and 0xFF, tagSize = tagSize)
    }

    fun parseId3v2(buffer: ByteArray, fileSizeBytes: Long = 0L): Id3v2ParseResult? {
        val header = inspectId3v2Header(buffer) ?: return null
        val id3End = header.totalTagSpanBytes
        var pos = 10
        val tagLimit = minOf(buffer.size.toLong(), id3End).toInt()

        if ((header.flags and 0x40) != 0 && pos + 4 <= tagLimit) {
            val extSize = if (header.majorVersion == 4) {
                readSynchsafe32(buffer, pos).coerceAtLeast(4)
            } else {
                4 + readUint32BE(buffer, pos).toInt().coerceAtLeast(0)
            }
            pos += extSize
        }

        val acc = TagAccumulator()
        val nextFrameOffset = parseId3FramesLoop(
            buffer = buffer,
            startPos = pos,
            limit = tagLimit,
            baseOffset = 0L,
            majorVersion = header.majorVersion,
            acc = acc,
        )

        val audioStart = id3End.toInt().takeIf { id3End <= buffer.size.toLong() }
        if (audioStart != null && audioStart < buffer.size) {
            parseMpegHeader(buffer, audioStart, fileSizeBytes, id3End, acc)
        }

        return Id3v2ParseResult(
            tags = acc.build(fileSizeBytes),
            nextFrameOffset = nextFrameOffset?.takeIf { it < id3End },
            id3EndOffset = id3End,
        )
    }

    /**
     * Parses ID3v2 frames starting from an arbitrary [baseOffset] (used after seeking past
     * an oversized `APIC` frame that exceeded the 512 KB ID3 header fetch cap).
     */
    fun parseId3v2FramesSegment(
        segment: ByteArray,
        baseOffset: Long,
        majorVersion: Int,
        id3EndOffset: Long,
        fileSizeBytes: Long,
    ): Id3v2ParseResult {
        val acc = TagAccumulator()
        val limit = minOf(segment.size.toLong(), (id3EndOffset - baseOffset).coerceAtLeast(0L)).toInt()
        val nextFrameOffset = parseId3FramesLoop(
            buffer = segment,
            startPos = 0,
            limit = limit,
            baseOffset = baseOffset,
            majorVersion = majorVersion,
            acc = acc,
        )
        val audioLocalOffset = (id3EndOffset - baseOffset).toInt()
        if (audioLocalOffset >= 0 && audioLocalOffset < segment.size) {
            parseMpegHeader(segment, audioLocalOffset, fileSizeBytes, id3EndOffset, acc)
        }
        return Id3v2ParseResult(
            tags = acc.build(fileSizeBytes),
            nextFrameOffset = nextFrameOffset?.takeIf { it < id3EndOffset },
            id3EndOffset = id3EndOffset,
        )
    }

    fun parseRawMpegAudio(buffer: ByteArray, fileSizeBytes: Long): ParsedAudioTags? {
        val acc = TagAccumulator()
        parseMpegHeader(buffer, 0, fileSizeBytes, 0L, acc)
        return if (acc.durationSec > 0 || acc.bitRateKbps != null) acc.build(fileSizeBytes) else null
    }

    fun parseFlacBlocks(
        buffer: ByteArray,
        baseOffset: Long = 0L,
        fileSizeBytes: Long = 0L,
    ): FlacParseResult? {
        var pos = 0
        if (baseOffset == 0L) {
            if (buffer.size < 4 ||
                buffer[0] != 'f'.code.toByte() ||
                buffer[1] != 'L'.code.toByte() ||
                buffer[2] != 'a'.code.toByte() ||
                buffer[3] != 'C'.code.toByte()
            ) {
                return null
            }
            pos = 4
        }

        val acc = TagAccumulator()
        var reachedLast = false
        var reachedVorbis = false
        var nextBlockOffset: Long? = null
        var truncatedVorbisOffset: Long? = null
        var truncatedVorbisTotal = 0L

        while (pos + 4 <= buffer.size) {
            val b0 = buffer[pos].toInt() and 0xFF
            val isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val blockLen = ((buffer[pos + 1].toInt() and 0xFF) shl 16) or
                ((buffer[pos + 2].toInt() and 0xFF) shl 8) or
                (buffer[pos + 3].toInt() and 0xFF)
            val dataStart = pos + 4
            val blockEndLong = dataStart.toLong() + blockLen.toLong()

            if (blockType == 6 && buffer.size > dataStart) {
                parseFlacPictureHeader(buffer, dataStart, minOf(buffer.size, dataStart + blockLen), baseOffset, acc)
            }

            if (blockEndLong > buffer.size.toLong()) {
                if (blockType == 4 && blockLen in 1..EmbeddedLyricsExtractor.MAX_TAG_BYTES) {
                    truncatedVorbisOffset = baseOffset + pos.toLong()
                    truncatedVorbisTotal = 4L + blockLen.toLong()
                } else if (!isLast) {
                    nextBlockOffset = baseOffset + blockEndLong
                }
                break
            }

            val blockData = buffer.copyOfRange(dataStart, blockEndLong.toInt())
            when (blockType) {
                0 -> parseFlacStreamInfo(blockData, acc)
                4 -> {
                    reachedVorbis = true
                    parseVorbisCommentsInto(blockData, acc)
                }
            }

            pos = blockEndLong.toInt()
            if (isLast) {
                reachedLast = true
                break
            }
        }

        return FlacParseResult(
            tags = acc.build(fileSizeBytes),
            nextBlockOffset = nextBlockOffset,
            reachedLastBlock = reachedLast,
            reachedVorbisComment = reachedVorbis,
            truncatedVorbisCommentOffset = truncatedVorbisOffset,
            truncatedVorbisCommentTotalBytes = truncatedVorbisTotal,
        )
    }

    fun isMp4Container(buffer: ByteArray): Boolean {
        if (buffer.size < 8) return false
        val type = String(buffer, 4, 4, StandardCharsets.ISO_8859_1)
        return type == "ftyp" || type == "moov" || type == "mdat" || type == "free" || type == "wide"
    }

    /**
     * Parses a faststart MP4 buffer starting at file offset 0 if a complete `moov` box
     * is already present inside [buffer]. Returns `null` if `moov` is absent or truncated.
     */
    fun findFaststartMp4Moov(buffer: ByteArray): MoovBoxResult? {
        var pos = 0
        while (pos + 8 <= buffer.size) {
            val box = readMp4BoxHeader(buffer, pos, buffer.size) ?: return null
            if (box.totalSize <= 0L || pos.toLong() + box.totalSize > buffer.size.toLong()) {
                return null
            }
            val end = (pos.toLong() + box.totalSize).toInt()
            if (box.type == "moov") {
                return MoovBoxResult(
                    moovOffset = pos.toLong(),
                    moovBytes = buffer.copyOfRange(pos, end),
                )
            }
            pos = end
        }
        return null
    }

    fun parseMp4Moov(
        moovBytes: ByteArray,
        moovFileOffset: Long = 0L,
        fileSizeBytes: Long = 0L,
    ): ParsedAudioTags? {
        val header = readMp4BoxHeader(moovBytes, 0, moovBytes.size) ?: return null
        if (header.type != "moov") return null
        val acc = TagAccumulator()
        val end = minOf(moovBytes.size.toLong(), header.totalSize).toInt().coerceAtLeast(header.payloadOffset)
        parseMp4Boxes(
            data = moovBytes,
            start = header.payloadOffset,
            end = end,
            baseOffset = moovFileOffset,
            acc = acc,
        )
        return acc.build(fileSizeBytes)
    }

    fun parseOgg(buffer: ByteArray, fileSizeBytes: Long = 0L): ParsedAudioTags? {
        if (buffer.size < 27 ||
            buffer[0] != 'O'.code.toByte() ||
            buffer[1] != 'g'.code.toByte() ||
            buffer[2] != 'g'.code.toByte() ||
            buffer[3] != 'S'.code.toByte()
        ) {
            return null
        }

        val packets = readOggPackets(buffer, maxPackets = 2)
        if (packets.isEmpty()) return null

        val acc = TagAccumulator()
        var nominalBitrateBps = 0

        val idPacket = packets[0]
        if (idPacket.size >= 28 &&
            idPacket[0] == 1.toByte() &&
            String(idPacket, 1, 6, StandardCharsets.ISO_8859_1) == "vorbis"
        ) {
            nominalBitrateBps = readInt32LE(idPacket, 20)
        }

        val commentPacket = packets.getOrNull(1)
        if (commentPacket != null) {
            val commentStart = when {
                commentPacket.size > 7 &&
                    commentPacket[0] == 3.toByte() &&
                    String(commentPacket, 1, 6, StandardCharsets.ISO_8859_1) == "vorbis" -> 7
                commentPacket.size > 8 &&
                    String(commentPacket, 0, 8, StandardCharsets.ISO_8859_1) == "OpusTags" -> 8
                else -> -1
            }
            if (commentStart > 0) {
                val commentBytes = commentPacket.copyOfRange(commentStart, commentPacket.size)
                parseVorbisCommentsInto(commentBytes, acc)
            }
        }

        if (nominalBitrateBps > 0) {
            acc.bitRateKbps = (nominalBitrateBps / 1000).takeIf { it > 0 }
        }
        if (acc.durationSec == 0 && (acc.bitRateKbps ?: 0) > 0 && fileSizeBytes > 0L) {
            acc.durationSec = ((fileSizeBytes * 8L) / (acc.bitRateKbps!! * 1000L)).toInt()
        }

        return acc.build(fileSizeBytes)
    }

    // --- Deterministic Filename & Parent Folder Inference ---

    fun inferFromPathAndFileName(
        fileName: String,
        parentFolderPath: String = "",
    ): ResolvedTrackMetadata {
        val suffix = extractSuffix(fileName)
        val dotIndex = fileName.lastIndexOf('.')
        val stem = if (dotIndex > 0) fileName.substring(0, dotIndex).trim() else fileName.trim()

        val trackMatch = TRACK_PREFIX_REGEX.matchEntire(stem)
        val inferredTrackNo = trackMatch?.groupValues?.get(1)?.toIntOrNull()
        val remainder = (trackMatch?.groupValues?.get(2) ?: stem).trim()

        val sepIdx = remainder.indexOf(" - ")
        val inferredArtist: String?
        val inferredTitle: String
        if (sepIdx > 0) {
            inferredArtist = remainder.substring(0, sepIdx).trim().takeIf { it.isNotBlank() }
            inferredTitle = remainder.substring(sepIdx + 3).trim().ifBlank { remainder }
        } else {
            inferredArtist = null
            inferredTitle = remainder.ifBlank { fileName }
        }

        val segments = parentFolderPath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val folderAlbum = segments.lastOrNull()
        val folderArtist = if (segments.size >= 2) segments[segments.size - 2] else null

        return ResolvedTrackMetadata(
            title = inferredTitle,
            artist = inferredArtist ?: folderArtist ?: "Unknown Artist",
            albumArtist = "",
            album = folderAlbum ?: "Unknown Album",
            trackNo = inferredTrackNo,
            year = null,
            genre = null,
            durationSec = 0,
            bitRate = null,
            suffix = suffix,
            lyrics = null,
            tagsParsed = false,
        )
    }

    fun resolveMetadata(
        parsed: ParsedAudioTags?,
        fileName: String,
        parentFolderPath: String = "",
        fileSizeBytes: Long = 0L,
    ): ResolvedTrackMetadata {
        val fallback = inferFromPathAndFileName(fileName, parentFolderPath)
        if (parsed == null) {
            return fallback
        }
        val durationSec = parsed.durationSec.coerceAtLeast(0)
        val bitRate = normalizeBitrate(parsed.bitRateKbps, durationSec, fileSizeBytes)
        return ResolvedTrackMetadata(
            title = parsed.title?.takeIf { it.isNotBlank() } ?: fallback.title,
            artist = parsed.artist?.takeIf { it.isNotBlank() } ?: fallback.artist,
            albumArtist = parsed.albumArtist?.takeIf { it.isNotBlank() } ?: fallback.albumArtist,
            album = parsed.album?.takeIf { it.isNotBlank() } ?: fallback.album,
            trackNo = parsed.trackNo ?: fallback.trackNo,
            year = parsed.year,
            genre = parsed.genre?.takeIf { it.isNotBlank() },
            durationSec = durationSec,
            bitRate = bitRate,
            suffix = fallback.suffix,
            lyrics = parsed.lyrics,
            tagsParsed = parsed.tagsFound,
        )
    }

    fun normalizeBitrate(
        rawKbps: Int?,
        durationSec: Int,
        fileSizeBytes: Long,
    ): Int? {
        if (rawKbps != null && rawKbps > 0) return rawKbps
        if (durationSec > 0 && fileSizeBytes > 0L) {
            val estimated = ((fileSizeBytes * 8L) / (durationSec.toLong() * 1000L)).toInt()
            if (estimated > 0) return estimated
        }
        return null
    }

    private fun extractSuffix(fileName: String): String? {
        val idx = fileName.lastIndexOf('.')
        if (idx <= 0 || idx == fileName.lastIndex) return null
        return fileName.substring(idx + 1).lowercase().takeIf { it.isNotBlank() }
    }

    // --- ID3v2 Internals ---

    private fun parseId3FramesLoop(
        buffer: ByteArray,
        startPos: Int,
        limit: Int,
        baseOffset: Long,
        majorVersion: Int,
        acc: TagAccumulator,
    ): Long? {
        var pos = startPos
        while (pos + 10 <= limit) {
            if (buffer[pos] == 0.toByte()) break
            val frameId = String(buffer, pos, 4, StandardCharsets.ISO_8859_1)
            if (!isValidId3FrameId(frameId)) break

            val frameSize = if (majorVersion == 4) {
                readSynchsafe32(buffer, pos + 4)
            } else {
                readUint32BE(buffer, pos + 4).toInt()
            }
            if (frameSize <= 0) break

            val flagsLow = buffer[pos + 9].toInt() and 0xFF
            val compressed = if (majorVersion == 4) (flagsLow and 0x08) != 0 else (flagsLow and 0x80) != 0
            val dataStart = pos + 10
            val frameEndLong = dataStart.toLong() + frameSize.toLong()

            if (frameEndLong > limit.toLong()) {
                if (frameId == "APIC" && !compressed) {
                    parseApicHeader(
                        buffer = buffer,
                        start = dataStart,
                        availableEnd = limit,
                        totalFrameSize = frameSize.toLong(),
                        fileOffsetBase = baseOffset,
                        acc = acc,
                    )
                }
                return baseOffset + frameEndLong
            }

            if (!compressed) {
                val frameData = buffer.copyOfRange(dataStart, frameEndLong.toInt())
                when (frameId) {
                    "TIT2" -> decodeId3Text(frameData)?.let { acc.title = it }
                    "TPE1" -> decodeId3Text(frameData)?.let { acc.artist = it }
                    "TPE2" -> decodeId3Text(frameData)?.let { acc.albumArtist = it }
                    "TALB" -> decodeId3Text(frameData)?.let { acc.album = it }
                    "TRCK" -> decodeId3Text(frameData)?.let { acc.trackNo = parseTrackNumber(it) }
                    "TDRC" -> decodeId3Text(frameData)?.let { parseYear(it)?.let { y -> acc.year = y } }
                    "TYER" -> if (acc.year == null) {
                        decodeId3Text(frameData)?.let { parseYear(it)?.let { y -> acc.year = y } }
                    }
                    "TCON" -> decodeId3Text(frameData)?.let { acc.genre = normalizeGenre(it) }
                    "TLEN" -> if (acc.durationSec == 0) {
                        decodeId3Text(frameData)?.trim()?.toLongOrNull()?.let { ms ->
                            if (ms > 0) acc.durationSec = (ms / 1000L).toInt()
                        }
                    }
                    "APIC" -> parseApicHeader(
                        buffer = buffer,
                        start = dataStart,
                        availableEnd = frameEndLong.toInt(),
                        totalFrameSize = frameSize.toLong(),
                        fileOffsetBase = baseOffset,
                        acc = acc,
                    )
                    "SYLT" -> EmbeddedLyricsExtractor.parseSylt(frameData)?.let { acc.syltLyrics = it }
                    "USLT" -> if (acc.usltLyrics == null) {
                        EmbeddedLyricsExtractor.parseUslt(frameData)?.let { acc.usltLyrics = it }
                    }
                }
            }

            pos = frameEndLong.toInt()
        }
        return null
    }

    private fun isValidId3FrameId(id: String): Boolean =
        id.length == 4 && id.all { it in 'A'..'Z' || it in '0'..'9' }

    private fun decodeId3Text(data: ByteArray): String? {
        if (data.size <= 1) return null
        val charset = id3Charset(data[0])
        val raw = String(data, 1, data.size - 1, charset)
        return raw.split('\u0000')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun id3Charset(encoding: Byte): Charset = when (encoding.toInt() and 0xFF) {
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }

    private fun parseApicHeader(
        buffer: ByteArray,
        start: Int,
        availableEnd: Int,
        totalFrameSize: Long,
        fileOffsetBase: Long,
        acc: TagAccumulator,
    ) {
        if (availableEnd - start < 4) return
        val encoding = buffer[start]
        var idx = start + 1

        val mimeStart = idx
        while (idx < availableEnd && buffer[idx] != 0.toByte()) idx++
        if (idx >= availableEnd) return
        val mime = String(buffer, mimeStart, idx - mimeStart, StandardCharsets.ISO_8859_1).trim()
        idx++ // skip null
        if (mime == "-->") return

        if (idx >= availableEnd) return
        val picType = buffer[idx].toInt() and 0xFF
        idx++

        // Skip description terminated by 1 or 2 null bytes
        if (encoding.toInt() == 1 || encoding.toInt() == 2) {
            while (idx + 1 < availableEnd && !(buffer[idx] == 0.toByte() && buffer[idx + 1] == 0.toByte())) {
                idx += 2
            }
            if (idx + 1 >= availableEnd) return
            idx += 2
        } else {
            while (idx < availableEnd && buffer[idx] != 0.toByte()) {
                idx++
            }
            if (idx >= availableEnd) return
            idx++
        }

        val headerLen = (idx - start).toLong()
        val imageSize = totalFrameSize - headerLen
        if (imageSize <= 0L) return

        val descriptor = EmbeddedArtDescriptor(
            offset = fileOffsetBase + idx.toLong(),
            size = imageSize,
            mimeType = mime,
        )
        if (acc.embeddedArt == null || picType == 3) {
            acc.embeddedArt = descriptor
            acc.artPicType = picType
        }
    }

    // --- MPEG Audio Frame + Xing / Info / VBRI ---

    private fun parseMpegHeader(
        buffer: ByteArray,
        startOffset: Int,
        fileSizeBytes: Long,
        audioStartFileOffset: Long,
        acc: TagAccumulator,
    ) {
        val scanLimit = minOf(buffer.size - 4, startOffset + 8192)
        var pos = startOffset
        while (pos <= scanLimit) {
            val b0 = buffer[pos].toInt() and 0xFF
            val b1 = buffer[pos + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03 // 0=2.5, 1=res, 2=2, 3=1
                val layerBits = (b1 ushr 1) and 0x03   // 1=III, 2=II, 3=I
                val b2 = buffer[pos + 2].toInt() and 0xFF
                val bitrateIdx = (b2 ushr 4) and 0x0F
                val sampleRateIdx = (b2 ushr 2) and 0x03
                val b3 = buffer[pos + 3].toInt() and 0xFF
                val channelMode = (b3 ushr 6) and 0x03

                if (versionBits != 1 && layerBits != 0 && bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                    val sampleRate = mpegSampleRate(versionBits, sampleRateIdx)
                    val frameBitrateKbps = mpegBitrateKbps(versionBits, layerBits, bitrateIdx)
                    val samplesPerFrame = when {
                        layerBits == 3 -> 384 // Layer I
                        versionBits == 3 -> 1152 // MPEG1 Layer II/III
                        layerBits == 2 -> 1152 // MPEG2 Layer II
                        else -> 576 // MPEG2/2.5 Layer III
                    }
                    val audioBytes = if (fileSizeBytes > audioStartFileOffset) {
                        fileSizeBytes - audioStartFileOffset
                    } else {
                        0L
                    }

                    // Check Xing / Info header
                    val sideInfoSize = when {
                        versionBits == 3 -> if (channelMode == 3) 17 else 32
                        else -> if (channelMode == 3) 9 else 17
                    }
                    val xingPos = pos + 4 + sideInfoSize
                    if (xingPos + 12 <= buffer.size) {
                        val tag = String(buffer, xingPos, 4, StandardCharsets.ISO_8859_1)
                        if (tag == "Xing" || tag == "Info") {
                            val flags = readUint32BE(buffer, xingPos + 4).toInt()
                            if ((flags and 0x01) != 0) {
                                val totalFrames = readUint32BE(buffer, xingPos + 8)
                                if (totalFrames > 0L && sampleRate > 0) {
                                    val dur = ((totalFrames * samplesPerFrame) / sampleRate).toInt()
                                    if (dur > 0) {
                                        acc.durationSec = dur
                                        if (audioBytes > 0L) {
                                            acc.bitRateKbps = ((audioBytes * 8L) / (dur.toLong() * 1000L)).toInt()
                                                .takeIf { it > 0 }
                                        }
                                        return
                                    }
                                }
                            }
                        }
                    }

                    // Check VBRI header at pos + 4 + 32
                    val vbriPos = pos + 36
                    if (vbriPos + 18 <= buffer.size &&
                        String(buffer, vbriPos, 4, StandardCharsets.ISO_8859_1) == "VBRI"
                    ) {
                        val totalFrames = readUint32BE(buffer, vbriPos + 14)
                        if (totalFrames > 0L && sampleRate > 0) {
                            val dur = ((totalFrames * samplesPerFrame) / sampleRate).toInt()
                            if (dur > 0) {
                                acc.durationSec = dur
                                if (audioBytes > 0L) {
                                    acc.bitRateKbps = ((audioBytes * 8L) / (dur.toLong() * 1000L)).toInt()
                                        .takeIf { it > 0 }
                                }
                                return
                            }
                        }
                    }

                    // CBR fallback
                    if (frameBitrateKbps > 0) {
                        acc.bitRateKbps = frameBitrateKbps
                        if (acc.durationSec == 0 && audioBytes > 0L) {
                            acc.durationSec = ((audioBytes * 8L) / (frameBitrateKbps.toLong() * 1000L)).toInt()
                        }
                    }
                    return
                }
            }
            pos++
        }
    }

    private fun mpegSampleRate(versionBits: Int, idx: Int): Int {
        val base = when (idx) {
            0 -> 44100
            1 -> 48000
            else -> 32000
        }
        return when (versionBits) {
            3 -> base // MPEG1
            2 -> base / 2 // MPEG2
            else -> base / 4 // MPEG2.5
        }
    }

    private fun mpegBitrateKbps(versionBits: Int, layerBits: Int, idx: Int): Int {
        val table = when {
            versionBits == 3 && layerBits == 3 -> MPEG1_L1
            versionBits == 3 && layerBits == 2 -> MPEG1_L2
            versionBits == 3 && layerBits == 1 -> MPEG1_L3
            layerBits == 3 -> MPEG2_L1
            else -> MPEG2_L23
        }
        return table[idx]
    }

    // --- FLAC / Vorbis Internals ---

    private fun parseFlacStreamInfo(data: ByteArray, acc: TagAccumulator) {
        if (data.size < 18) return
        val sampleRate = ((data[10].toInt() and 0xFF) shl 12) or
            ((data[11].toInt() and 0xFF) shl 4) or
            ((data[12].toInt() and 0xF0) ushr 4)
        val totalSamples = ((data[13].toLong() and 0x0F) shl 32) or
            ((data[14].toLong() and 0xFF) shl 24) or
            ((data[15].toLong() and 0xFF) shl 16) or
            ((data[16].toLong() and 0xFF) shl 8) or
            (data[17].toLong() and 0xFF)
        if (sampleRate > 0 && totalSamples > 0L) {
            acc.durationSec = (totalSamples / sampleRate.toLong()).toInt()
        }
    }

    private fun parseFlacPictureHeader(
        buffer: ByteArray,
        start: Int,
        endLimit: Int,
        baseOffset: Long,
        acc: TagAccumulator,
    ) {
        if (endLimit - start < 32) return
        val picType = readUint32BE(buffer, start).toInt()
        val mimeLen = readUint32BE(buffer, start + 4).toInt()
        if (mimeLen < 0 || start + 8 + mimeLen + 24 > endLimit) return
        val mime = String(buffer, start + 8, mimeLen, StandardCharsets.UTF_8).trim()
        val descLenPos = start + 8 + mimeLen
        val descLen = readUint32BE(buffer, descLenPos).toInt()
        if (descLen < 0) return
        val dataLenPos = descLenPos + 4 + descLen + 16
        if (dataLenPos + 4 > endLimit) return
        val dataLen = readUint32BE(buffer, dataLenPos)
        if (dataLen <= 0L) return

        val artOffset = baseOffset + (dataLenPos + 4).toLong()
        val descriptor = EmbeddedArtDescriptor(offset = artOffset, size = dataLen, mimeType = mime)
        if (acc.embeddedArt == null || picType == 3) {
            acc.embeddedArt = descriptor
            acc.artPicType = picType
        }
    }

    private fun parseVorbisCommentsInto(data: ByteArray, acc: TagAccumulator) {
        val bais = ByteArrayInputStream(data)
        fun readIntLe(): Int {
            val b = ByteArray(4)
            if (bais.read(b) < 4) return -1
            return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
        }

        val vendorLen = readIntLe()
        if (vendorLen < 0 || vendorLen > bais.available()) return
        bais.skip(vendorLen.toLong())

        val count = readIntLe()
        if (count <= 0) return
        for (i in 0 until count) {
            val len = readIntLe()
            if (len <= 0 || len > bais.available()) break
            val bytes = ByteArray(len)
            bais.read(bytes)
            val entry = String(bytes, StandardCharsets.UTF_8)
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val key = entry.substring(0, eq).trim().uppercase()
            val value = entry.substring(eq + 1).trim()
            if (value.isBlank()) continue
            when (key) {
                "TITLE" -> acc.title = value
                "ARTIST" -> acc.artist = value
                "ALBUMARTIST", "ALBUM ARTIST" -> acc.albumArtist = value
                "ALBUM" -> acc.album = value
                "TRACKNUMBER" -> acc.trackNo = parseTrackNumber(value)
                "DATE" -> parseYear(value)?.let { acc.year = it }
                "YEAR" -> if (acc.year == null) parseYear(value)?.let { acc.year = it }
                "GENRE" -> acc.genre = value
                "LYRICS", "UNSYNCEDLYRICS" -> if (acc.usltLyrics == null) {
                    val parsed = LrcParser.parse(value, source = LyricsSource.FILE_TAGS)
                    acc.usltLyrics = if (parsed.lines.isNotEmpty()) {
                        parsed
                    } else {
                        Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = value, source = LyricsSource.FILE_TAGS)
                    }
                }
            }
        }
    }

    // --- MP4 / M4A Internals ---

    private data class Mp4BoxHeader(
        val type: String,
        val payloadOffset: Int,
        val totalSize: Long,
    )

    private fun readMp4BoxHeader(data: ByteArray, pos: Int, limit: Int): Mp4BoxHeader? {
        if (pos + 8 > limit) return null
        val size32 = readUint32BE(data, pos)
        val type = String(data, pos + 4, 4, StandardCharsets.ISO_8859_1)
        return when {
            size32 == 1L -> {
                if (pos + 16 > limit) return null
                val size64 = readUint64BE(data, pos + 8)
                if (size64 < 16L) return null
                Mp4BoxHeader(type = type, payloadOffset = pos + 16, totalSize = size64)
            }
            size32 == 0L -> Mp4BoxHeader(type = type, payloadOffset = pos + 8, totalSize = (limit - pos).toLong())
            size32 >= 8L -> Mp4BoxHeader(type = type, payloadOffset = pos + 8, totalSize = size32)
            else -> null
        }
    }

    private fun parseMp4Boxes(
        data: ByteArray,
        start: Int,
        end: Int,
        baseOffset: Long,
        acc: TagAccumulator,
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val box = readMp4BoxHeader(data, pos, end) ?: break
            val boxEndLong = pos.toLong() + box.totalSize
            if (boxEndLong > end.toLong() || boxEndLong <= pos.toLong()) break
            val boxEnd = boxEndLong.toInt()

            when (box.type) {
                "udta", "ilst", "trak", "mdia" -> {
                    parseMp4Boxes(data, box.payloadOffset, boxEnd, baseOffset, acc)
                }
                "meta" -> {
                    if (box.payloadOffset + 4 <= boxEnd) {
                        parseMp4Boxes(data, box.payloadOffset + 4, boxEnd, baseOffset, acc)
                    }
                }
                "mvhd" -> parseMp4DurationBox(data, box.payloadOffset, boxEnd, overwrite = true, acc = acc)
                "mdhd" -> if (acc.durationSec == 0) {
                    parseMp4DurationBox(data, box.payloadOffset, boxEnd, overwrite = false, acc = acc)
                }
                "©nam" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { acc.title = it }
                "©ART" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { acc.artist = it }
                "aART" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { acc.albumArtist = it }
                "©alb" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { acc.album = it }
                "©day" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { parseYear(it)?.let { y -> acc.year = y } }
                "©gen" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { acc.genre = it }
                "gnre" -> readMp4DataBytes(data, box.payloadOffset, boxEnd)?.let { (_, payload) ->
                    if (payload.size >= 2) {
                        val idx = (((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)) - 1
                        if (idx in ID3V1_GENRES.indices && acc.genre == null) {
                            acc.genre = ID3V1_GENRES[idx]
                        }
                    }
                }
                "trkn" -> readMp4DataBytes(data, box.payloadOffset, boxEnd)?.let { (_, payload) ->
                    if (payload.size >= 4) {
                        val trk = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                        if (trk > 0) acc.trackNo = trk
                    }
                }
                "©lyr" -> readMp4DataText(data, box.payloadOffset, boxEnd)?.let { text ->
                    val parsed = LrcParser.parse(text, source = LyricsSource.FILE_TAGS)
                    acc.usltLyrics = if (parsed.lines.isNotEmpty()) {
                        parsed
                    } else {
                        Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = text, source = LyricsSource.FILE_TAGS)
                    }
                }
                "covr" -> parseMp4Covr(data, box.payloadOffset, boxEnd, baseOffset, acc)
            }

            pos = boxEnd
        }
    }

    private fun parseMp4DurationBox(
        data: ByteArray,
        start: Int,
        end: Int,
        overwrite: Boolean,
        acc: TagAccumulator,
    ) {
        if (start >= end) return
        val version = data[start].toInt() and 0xFF
        val sec = if (version == 1) {
            if (start + 32 > end) return
            val timescale = readUint32BE(data, start + 20)
            val duration = readUint64BE(data, start + 24)
            if (timescale > 0L && duration > 0L) (duration / timescale).toInt() else 0
        } else {
            if (start + 20 > end) return
            val timescale = readUint32BE(data, start + 12)
            val duration = readUint32BE(data, start + 16)
            if (timescale > 0L && duration > 0L) (duration / timescale).toInt() else 0
        }
        if (sec > 0 && (overwrite || acc.durationSec == 0)) {
            acc.durationSec = sec
        }
    }

    private fun readMp4DataText(data: ByteArray, start: Int, end: Int): String? {
        val (typeIndicator, payload) = readMp4DataBytes(data, start, end) ?: return null
        val charset = if (typeIndicator == 2) StandardCharsets.UTF_16BE else StandardCharsets.UTF_8
        return String(payload, charset).trim().takeIf { it.isNotBlank() }
    }

    private fun readMp4DataBytes(data: ByteArray, start: Int, end: Int): Pair<Int, ByteArray>? {
        var pos = start
        while (pos + 8 <= end) {
            val child = readMp4BoxHeader(data, pos, end) ?: break
            val childEndLong = pos.toLong() + child.totalSize
            if (childEndLong > end.toLong() || childEndLong <= pos.toLong()) break
            val childEnd = childEndLong.toInt()
            if (child.type == "data" && child.payloadOffset + 8 <= childEnd) {
                val typeIndicator = readUint32BE(data, child.payloadOffset).toInt()
                val payload = data.copyOfRange(child.payloadOffset + 8, childEnd)
                return typeIndicator to payload
            }
            pos = childEnd
        }
        return null
    }

    private fun parseMp4Covr(
        data: ByteArray,
        start: Int,
        end: Int,
        baseOffset: Long,
        acc: TagAccumulator,
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val child = readMp4BoxHeader(data, pos, end) ?: break
            val childEndLong = pos.toLong() + child.totalSize
            if (childEndLong > end.toLong() || childEndLong <= pos.toLong()) break
            val childEnd = childEndLong.toInt()
            if (child.type == "data" && child.payloadOffset + 8 < childEnd) {
                val typeIndicator = readUint32BE(data, child.payloadOffset).toInt()
                val payloadOffset = child.payloadOffset + 8
                val payloadSize = (childEnd - payloadOffset).toLong()
                if (payloadSize > 0L && acc.embeddedArt == null) {
                    val mime = when (typeIndicator) {
                        13 -> "image/jpeg"
                        14 -> "image/png"
                        27 -> "image/bmp"
                        else -> ""
                    }
                    acc.embeddedArt = EmbeddedArtDescriptor(
                        offset = baseOffset + payloadOffset.toLong(),
                        size = payloadSize,
                        mimeType = mime,
                    )
                }
                return
            }
            pos = childEnd
        }
    }

    // --- Ogg Packet Reader ---

    private fun readOggPackets(buffer: ByteArray, maxPackets: Int): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var current = java.io.ByteArrayOutputStream()
        var pos = 0

        while (pos + 27 <= buffer.size && packets.size < maxPackets) {
            if (buffer[pos] != 'O'.code.toByte() ||
                buffer[pos + 1] != 'g'.code.toByte() ||
                buffer[pos + 2] != 'g'.code.toByte() ||
                buffer[pos + 3] != 'S'.code.toByte()
            ) {
                break
            }
            val numSegs = buffer[pos + 26].toInt() and 0xFF
            val segTableStart = pos + 27
            if (segTableStart + numSegs > buffer.size) break

            var dataPos = segTableStart + numSegs
            for (i in 0 until numSegs) {
                val lace = buffer[segTableStart + i].toInt() and 0xFF
                if (dataPos + lace > buffer.size) return packets
                current.write(buffer, dataPos, lace)
                dataPos += lace
                if (lace < 255) {
                    packets += current.toByteArray()
                    current = java.io.ByteArrayOutputStream()
                    if (packets.size >= maxPackets) return packets
                }
            }
            pos = dataPos
        }
        return packets
    }

    // --- Helpers ---

    private fun readSynchsafe32(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0x7F) shl 21) or
            ((buf[offset + 1].toInt() and 0x7F) shl 14) or
            ((buf[offset + 2].toInt() and 0x7F) shl 7) or
            (buf[offset + 3].toInt() and 0x7F)

    private fun readInt32LE(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun parseTrackNumber(raw: String): Int? =
        raw.substringBefore('/').trim().toIntOrNull()?.takeIf { it > 0 }

    private fun parseYear(raw: String): Int? =
        YEAR_REGEX.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1000..2999 }

    private fun normalizeGenre(raw: String): String {
        val m = ID3_GENRE_REF_REGEX.matchEntire(raw.trim()) ?: return raw.trim()
        val trailing = m.groupValues[2].trim()
        if (trailing.isNotBlank()) return trailing
        val idx = m.groupValues[1].toIntOrNull()
        return if (idx != null && idx in ID3V1_GENRES.indices) ID3V1_GENRES[idx] else raw.trim()
    }

    private class TagAccumulator {
        var title: String? = null
        var artist: String? = null
        var albumArtist: String? = null
        var album: String? = null
        var trackNo: Int? = null
        var year: Int? = null
        var genre: String? = null
        var durationSec: Int = 0
        var bitRateKbps: Int? = null
        var embeddedArt: EmbeddedArtDescriptor? = null
        var artPicType: Int = -1
        var usltLyrics: Lyrics? = null
        var syltLyrics: Lyrics? = null

        fun build(fileSizeBytes: Long): ParsedAudioTags {
            val hasAnyTag = title != null ||
                artist != null ||
                albumArtist != null ||
                album != null ||
                trackNo != null ||
                year != null ||
                genre != null ||
                usltLyrics != null ||
                syltLyrics != null ||
                embeddedArt != null
            return ParsedAudioTags(
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                trackNo = trackNo,
                year = year,
                genre = genre,
                durationSec = durationSec,
                bitRateKbps = normalizeBitrate(bitRateKbps, durationSec, fileSizeBytes),
                embeddedArt = embeddedArt,
                lyrics = syltLyrics ?: usltLyrics,
                tagsFound = hasAnyTag,
            )
        }
    }

    private val MPEG1_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val MPEG1_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val MPEG1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MPEG2_L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    private val MPEG2_L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    private val ID3V1_GENRES = arrayOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
        "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap",
        "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
        "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance",
        "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock",
        "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
        "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi",
        "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
    )
}
