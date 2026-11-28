package com.feishin.mobile.data.remote.drive

import com.feishin.mobile.data.lyrics.EmbeddedLyricsExtractor
import java.nio.charset.StandardCharsets

data class MoovBoxResult(
    val moovOffset: Long,
    val moovBytes: ByteArray,
)

/**
 * Walks top-level ISO BMFF / MP4 boxes via minimal HTTP Range peeks to locate and
 * fetch the trailing `moov` atom in non-faststart M4A/MP4 files.
 */
object Mp4TailMoovFetcher {
    const val MAX_BOX_HOPS = 32
    val MAX_MOOV_BYTES: Long = EmbeddedLyricsExtractor.MAX_TAG_BYTES.toLong()

    suspend fun fetchMoov(
        initialBuffer: ByteArray,
        fileSizeBytes: Long,
        fetchRange: suspend (start: Long, endInclusive: Long) -> ByteArray?,
    ): MoovBoxResult? {
        if (fileSizeBytes < 8L) return null

        var offset = 0L
        var hops = 0
        while (hops < MAX_BOX_HOPS && offset <= fileSizeBytes - 8L) {
            hops++

            val headerBytes: ByteArray
            val headerOffset: Int
            if (offset + 16L <= initialBuffer.size.toLong()) {
                headerBytes = initialBuffer
                headerOffset = offset.toInt()
            } else if (offset + 8L <= initialBuffer.size.toLong()) {
                val size32Peek = readUint32BE(initialBuffer, offset.toInt())
                if (size32Peek != 1L) {
                    headerBytes = initialBuffer
                    headerOffset = offset.toInt()
                } else {
                    if (fileSizeBytes - offset < 16L) return null
                    val peekEnd = minOf(offset + 15L, fileSizeBytes - 1L)
                    headerBytes = fetchRange(offset, peekEnd) ?: return null
                    headerOffset = 0
                }
            } else {
                val peekEnd = minOf(offset + 15L, fileSizeBytes - 1L)
                headerBytes = fetchRange(offset, peekEnd) ?: return null
                headerOffset = 0
            }

            if (headerBytes.size - headerOffset < 8) return null
            val size32 = readUint32BE(headerBytes, headerOffset)
            val boxType = String(headerBytes, headerOffset + 4, 4, StandardCharsets.ISO_8859_1)

            val atomSize: Long = when {
                size32 == 1L -> {
                    if (headerBytes.size - headerOffset < 16) return null
                    val size64 = readUint64BE(headerBytes, headerOffset + 8)
                    if (size64 < 16L) return null
                    size64
                }
                size32 == 0L -> fileSizeBytes - offset
                else -> size32
            }

            if (atomSize < 8L || atomSize > fileSizeBytes - offset) return null

            if (boxType == "moov") {
                if (atomSize > MAX_MOOV_BYTES) return null
                val moovBytes = if (offset + atomSize <= initialBuffer.size.toLong()) {
                    initialBuffer.copyOfRange(offset.toInt(), (offset + atomSize).toInt())
                } else {
                    fetchRange(offset, offset + atomSize - 1L)
                        ?.takeIf { it.size.toLong() >= atomSize }
                        ?: return null
                }
                return MoovBoxResult(moovOffset = offset, moovBytes = moovBytes)
            }

            offset += atomSize
        }

        return null
    }
}

internal fun readUint32BE(buf: ByteArray, offset: Int): Long =
    ((buf[offset].toLong() and 0xFF) shl 24) or
        ((buf[offset + 1].toLong() and 0xFF) shl 16) or
        ((buf[offset + 2].toLong() and 0xFF) shl 8) or
        (buf[offset + 3].toLong() and 0xFF)

internal fun readUint64BE(buf: ByteArray, offset: Int): Long =
    ((buf[offset].toLong() and 0xFF) shl 56) or
        ((buf[offset + 1].toLong() and 0xFF) shl 48) or
        ((buf[offset + 2].toLong() and 0xFF) shl 40) or
        ((buf[offset + 3].toLong() and 0xFF) shl 32) or
        ((buf[offset + 4].toLong() and 0xFF) shl 24) or
        ((buf[offset + 5].toLong() and 0xFF) shl 16) or
        ((buf[offset + 6].toLong() and 0xFF) shl 8) or
        (buf[offset + 7].toLong() and 0xFF)
