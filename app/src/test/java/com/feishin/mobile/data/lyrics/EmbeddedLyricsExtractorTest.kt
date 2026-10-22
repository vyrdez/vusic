package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.network.LyricsKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsExtractorTest {

    @Test
    fun `extracts USLT lyrics from synthetic ID3v2 3 header`() {
        val lyricsText = "[00:05.00]Sample lyrics text"

        // Build USLT frame data:
        // 1 byte encoding (3 = UTF-8), 3 bytes lang ("eng"), 1 byte descriptor null terminator (0), text
        val framePayload = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray(StandardCharsets.ISO_8859_1))
            write(0) // descriptor null terminator
            write(lyricsText.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()

        // Build full ID3v2 tag
        val id3 = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
            write(3) // v2.3
            write(0) // revision
            write(0) // flags

            // Frame: 4 bytes ID + 4 bytes size + 2 bytes flags + payload
            val frameBytes = ByteArrayOutputStream().apply {
                write("USLT".toByteArray(StandardCharsets.ISO_8859_1))
                val size = framePayload.size
                write((size shr 24) and 0xFF)
                write((size shr 16) and 0xFF)
                write((size shr 8) and 0xFF)
                write(size and 0xFF)
                write(0) // flags
                write(0)
                write(framePayload)
            }.toByteArray()

            val tagSize = frameBytes.size
            // 4 syncsafe bytes
            write((tagSize shr 21) and 0x7F)
            write((tagSize shr 14) and 0x7F)
            write((tagSize shr 7) and 0x7F)
            write(tagSize and 0x7F)

            write(frameBytes)
        }.toByteArray()

        val lyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(id3))
        assertNotNull(lyrics)
        assertTrue(lyrics!!.isSynced)
        assertEquals(1, lyrics.lines.size)
        assertEquals(5000L, lyrics.lines[0].startMs)
        assertEquals("Sample lyrics text", lyrics.lines[0].text)
    }

    @Test
    fun `extracts LYRICS from synthetic FLAC Vorbis comment block`() {
        val lyricsText = "Plain lyric content without stamps"

        // Vorbis comment block:
        // 4 bytes vendor len (LE) + vendor
        // 4 bytes comment count (LE)
        // 4 bytes comment 1 len (LE) + comment 1 ("LYRICS=...")
        val commentStr = "LYRICS=$lyricsText"
        val commentBytes = commentStr.toByteArray(StandardCharsets.UTF_8)

        val vorbisPayload = ByteArrayOutputStream().apply {
            // Vendor len 4 ("test")
            write(byteArrayOf(4, 0, 0, 0))
            write("test".toByteArray(StandardCharsets.UTF_8))
            // Comment count = 1
            write(byteArrayOf(1, 0, 0, 0))
            // Comment len
            val clen = commentBytes.size
            write(byteArrayOf((clen and 0xFF).toByte(), ((clen shr 8) and 0xFF).toByte(), 0, 0))
            write(commentBytes)
        }.toByteArray()

        // FLAC structure: "fLaC" + block header (type 4 = vorbis comment, last block) + payload
        val flac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.ISO_8859_1))
            // isLast (0x80) | blockType 4 = 0x84
            write(0x84)
            val len = vorbisPayload.size
            write((len shr 16) and 0xFF)
            write((len shr 8) and 0xFF)
            write(len and 0xFF)
            write(vorbisPayload)
        }.toByteArray()

        val lyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(flac))
        assertNotNull(lyrics)
        assertEquals(LyricsKind.PLAIN, lyrics!!.kind)
        assertEquals(lyricsText, lyrics.plainText)
    }
}
