package com.feishin.mobile.data.remote.drive

import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.lyrics.EmbeddedLyricsExtractor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveRangeReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var coversDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        coversDir = tempFolder.newFolder("download_covers")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createReader(
        capturedTags: MutableList<ProfileIdTag?> = CopyOnWriteArrayList(),
        transcoder: DriveCoverTranscoder = DriveCoverTranscoder { bytes, _ -> bytes },
    ): DriveRangeReader {
        val tagRecorder = Interceptor { chain ->
            val req = chain.request()
            capturedTags += req.tag(ProfileIdTag::class.java)
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(tagRecorder)
            .build()
        return DriveRangeReader(
            client = client,
            coversDirProvider = { coversDir },
            transcoder = transcoder,
            fileUrlBuilder = { fileId -> server.url("/drive/v3/files/$fileId?alt=media").toString() },
        )
    }

    private fun installRangeDispatcher(
        files: Map<String, ByteArray>,
        recordedRanges: MutableList<String> = CopyOnWriteArrayList(),
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: ""
                val fileId = path.substringAfter("/drive/v3/files/", "")
                val data = files[fileId] ?: return MockResponse().setResponseCode(404)
                val rangeHeader = request.getHeader("Range") ?: return MockResponse().setResponseCode(400)
                recordedRanges += rangeHeader

                val match = Regex("""bytes=(\d+)-(\d+)""").matchEntire(rangeHeader)
                    ?: return MockResponse().setResponseCode(416)
                val start = match.groupValues[1].toLong()
                val reqEnd = match.groupValues[2].toLong()
                if (start < 0 || start >= data.size) {
                    return MockResponse().setResponseCode(416)
                }
                val end = minOf(reqEnd, data.size.toLong() - 1L)
                val slice = data.copyOfRange(start.toInt(), end.toInt() + 1)
                return MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/${data.size}")
                    .setBody(Buffer().write(slice))
            }
        }
    }

    @Test
    fun `buildGoogleDriveFileUrl builds expected Drive alt media URL`() {
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/file_123?alt=media",
            buildGoogleDriveFileUrl("file_123"),
        )
    }

    @Test
    fun `reads ID3v2 tags lyrics Xing duration and attaches ProfileIdTag via HTTP 206`() = runTest {
        val frames = listOf(
            id3TextFrame("TIT2", "Night Drive"),
            id3TextFrame("TPE1", "The Midnight"),
            id3TextFrame("TPE2", "The Midnight Collective"),
            id3TextFrame("TALB", "Endless Summer"),
            id3TextFrame("TRCK", "03/10"),
            id3TextFrame("TDRC", "2016-08-05"),
            id3TextFrame("TCON", "(52)"),
            id3UsltFrame("[00:12.00]Out on the highway"),
        )
        val id3 = buildId3v2(version = 4, frames = frames)
        // MPEG1 Layer III 44100 Hz stereo frame + Xing header with 5000 frames (~130s)
        val xingAudio = buildMpegXingAudio(totalFrames = 5000, paddingSize = 4096)
        val fileBytes = id3 + xingAudio
        val totalSize = id3.size.toLong() + 2_600_000L // 130s @ 160 kbps audio stream after ID3v2 header

        val ranges = CopyOnWriteArrayList<String>()
        val tags = CopyOnWriteArrayList<ProfileIdTag?>()
        installRangeDispatcher(mapOf("id3_file" to fileBytes), ranges)

        val result = createReader(tags).readTrackMetadata(
            profileId = "drive_p2",
            fileId = "id3_file",
            fileName = "03 - Night Drive.mp3",
            parentFolderPath = "Other/Folder",
            fileSizeBytes = totalSize,
        )

        assertEquals(listOf("bytes=0-65535"), ranges)
        assertEquals(listOf(ProfileIdTag("drive_p2")), tags)
        assertEquals(206, result.httpStatusCode)
        assertTrue(result.metadata.tagsParsed)
        assertEquals("Night Drive", result.metadata.title)
        assertEquals("The Midnight", result.metadata.artist)
        assertEquals("The Midnight Collective", result.metadata.albumArtist)
        assertEquals("Endless Summer", result.metadata.album)
        assertEquals(3, result.metadata.trackNo)
        assertEquals(2016, result.metadata.year)
        assertEquals("Electronic", result.metadata.genre)
        assertEquals("mp3", result.metadata.suffix)
        assertEquals(130, result.metadata.durationSec)
        assertEquals(160, result.metadata.bitRate)
        assertNotNull(result.metadata.lyrics)
        assertTrue(result.metadata.lyrics!!.isSynced)
        assertEquals("Out on the highway", result.metadata.lyrics!!.lines[0].text)
    }

    @Test
    fun `reads FLAC STREAMINFO hops past large PICTURE block to VORBIS_COMMENT and fetches art range`() = runTest {
        val fakeJpeg = ByteArray(80_000) { (it % 251).toByte() }
        val streamInfo = buildFlacStreamInfo(sampleRate = 44_100, totalSamples = 44_100L * 180L)
        val pictureBlock = buildFlacPictureBlock(mime = "image/jpeg", imageBytes = fakeJpeg)
        val vorbisBlock = buildVorbisCommentBlock(
            "TITLE" to "Crystal Echo",
            "ARTIST" to "Boards",
            "ALBUMARTIST" to "Boards Collective",
            "ALBUM" to "Geogaddi",
            "TRACKNUMBER" to "7/23",
            "DATE" to "2002",
            "GENRE" to "IDM",
            "LYRICS" to "Plain FLAC line",
        )
        val flacBytes = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.ISO_8859_1))
            write(flacBlockHeader(isLast = false, type = 0, length = streamInfo.size))
            write(streamInfo)
            write(flacBlockHeader(isLast = false, type = 6, length = pictureBlock.size))
            write(pictureBlock)
            write(flacBlockHeader(isLast = true, type = 4, length = vorbisBlock.size))
            write(vorbisBlock)
            write(ByteArray(1024))
        }.toByteArray()

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("flac_1" to flacBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p2",
            fileId = "flac_1",
            fileName = "07 - Crystal Echo.flac",
            fileSizeBytes = flacBytes.size.toLong(),
        )

        assertTrue(ranges.size >= 2)
        assertEquals("bytes=0-65535", ranges[0])
        assertTrue("Expected FLAC block hop past PICTURE", ranges.any { it.startsWith("bytes=80") })
        assertTrue(result.metadata.tagsParsed)
        assertEquals("Crystal Echo", result.metadata.title)
        assertEquals("Boards", result.metadata.artist)
        assertEquals("Boards Collective", result.metadata.albumArtist)
        assertEquals("Geogaddi", result.metadata.album)
        assertEquals(7, result.metadata.trackNo)
        assertEquals(2002, result.metadata.year)
        assertEquals("IDM", result.metadata.genre)
        assertEquals(180, result.metadata.durationSec)
        assertEquals(LyricsKind.PLAIN, result.metadata.lyrics?.kind)
        assertEquals("Plain FLAC line", result.metadata.lyrics?.plainText)
        assertEquals("p2:cov:flac_1", result.coverArtId)
        assertNotNull(result.coverFile)
        assertTrue(result.coverFile!!.exists())
        assertEquals(fakeJpeg.size.toLong(), result.coverSizeBytes)
    }

    @Test
    fun `reads faststart M4A in single 64KB Range request`() = runTest {
        val coverPayload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        val ftyp = mp4Box("ftyp", "M4A ".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(4))
        val moov = buildSyntheticMoov(
            use64BitMoov = false,
            durationSec = 210,
            title = "Faststart Song",
            artist = "M4A Artist",
            albumArtist = "M4A AlbumArtist",
            album = "M4A Album",
            trackNo = 4,
            year = "2024",
            genre = "Synthpop",
            lyrics = "[00:01.50]Faststart lyric",
            coverJpeg = coverPayload,
        )
        val mdat = mp4Box("mdat", ByteArray(4096))
        val fileBytes = ftyp + moov + mdat

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("m4a_fast" to fileBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p3",
            fileId = "m4a_fast",
            fileName = "faststart.m4a",
            fileSizeBytes = fileBytes.size.toLong(),
        )

        assertEquals(listOf("bytes=0-65535"), ranges)
        assertEquals("Faststart Song", result.metadata.title)
        assertEquals("M4A Artist", result.metadata.artist)
        assertEquals("M4A AlbumArtist", result.metadata.albumArtist)
        assertEquals("M4A Album", result.metadata.album)
        assertEquals(4, result.metadata.trackNo)
        assertEquals(2024, result.metadata.year)
        assertEquals("Synthpop", result.metadata.genre)
        assertEquals(210, result.metadata.durationSec)
        assertTrue(result.metadata.lyrics!!.isSynced)
        assertEquals("Faststart lyric", result.metadata.lyrics!!.lines[0].text)
        assertEquals("p3:cov:m4a_fast", result.coverArtId)
        assertEquals(coverPayload.size.toLong(), result.coverSizeBytes)
    }

    @Test
    fun `large ID3v2 APIC before text and lyrics frames expands header and fetches exact art range`() = runTest {
        // 600 KB APIC (> 512 KB cap, <= 2 MB) followed by TIT2, TPE1, USLT
        val artPayload = ByteArray(600_000) { ((it * 7) and 0xFF).toByte() }
        val apicFrame = id3ApicFrame(mime = "image/jpeg", imageBytes = artPayload)
        val id3 = buildId3v2(
            version = 3,
            frames = listOf(
                apicFrame,
                id3TextFrame("TIT2", "After Huge Cover"),
                id3TextFrame("TPE1", "Tag Artist"),
                id3UsltFrame("Verse after 600KB APIC"),
            ),
        )
        val fileBytes = id3 + buildMpegXingAudio(totalFrames = 2000, paddingSize = 2048)

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("big_apic" to fileBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p4",
            fileId = "big_apic",
            fileName = "big_apic.mp3",
            fileSizeBytes = fileBytes.size.toLong(),
        )

        assertEquals("bytes=0-65535", ranges[0])
        assertEquals("bytes=0-524287", ranges[1])
        assertTrue("Expected tail seek past 600KB APIC", ranges.size >= 4)

        val parsed = DriveTagParser.parseId3v2(fileBytes, fileBytes.size.toLong())!!
        val artDesc = parsed.tags.embeddedArt!!
        val expectedArtRange = "bytes=${artDesc.offset}-${artDesc.offset + artDesc.size - 1L}"
        assertTrue("Expected exact art Range $expectedArtRange in $ranges", expectedArtRange in ranges)

        assertEquals("After Huge Cover", result.metadata.title)
        assertEquals("Tag Artist", result.metadata.artist)
        assertEquals("Verse after 600KB APIC", result.metadata.lyrics?.plainText)
        assertEquals("p4:cov:big_apic", result.coverArtId)
        assertEquals(artPayload.size.toLong(), result.coverSizeBytes)
    }

    @Test
    fun `synthetic 32-bit non-faststart M4A walks tail moov and extracts tags plus EmbeddedLyricsExtractor`() = runTest {
        val ftyp = mp4Box("ftyp", "M4A ".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(4))
        val mdat = mp4Box("mdat", ByteArray(90_000))
        val moov = buildSyntheticMoov(
            use64BitMoov = false,
            durationSec = 195,
            title = "Tail Moov 32",
            artist = "Tail Artist",
            album = "Tail Album",
            lyrics = "[00:10.00]Tail moov 32 line",
        )
        val fileBytes = ftyp + mdat + moov
        val expectedMoovOffset = (ftyp.size + mdat.size).toLong()

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("m4a_32" to fileBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p5",
            fileId = "m4a_32",
            fileName = "tail32.m4a",
            fileSizeBytes = fileBytes.size.toLong(),
        )

        assertEquals("bytes=0-65535", ranges[0])
        assertTrue("Expected 16-byte peek at $expectedMoovOffset", "bytes=$expectedMoovOffset-${expectedMoovOffset + 15}" in ranges)
        assertTrue(
            "Expected single moov range fetch",
            "bytes=$expectedMoovOffset-${expectedMoovOffset + moov.size - 1}" in ranges,
        )
        assertEquals("Tail Moov 32", result.metadata.title)
        assertEquals("Tail Artist", result.metadata.artist)
        assertEquals("Tail Album", result.metadata.album)
        assertEquals(195, result.metadata.durationSec)
        assertTrue(result.metadata.lyrics!!.isSynced)
        assertEquals("Tail moov 32 line", result.metadata.lyrics!!.lines[0].text)
    }

    @Test
    fun `synthetic 64-bit non-faststart M4A walks tail moov and EmbeddedLyricsExtractor handles offset 16`() = runTest {
        val ftyp = mp4Box("ftyp", "M4A ".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(4))
        val mdat64 = mp4Box64("mdat", ByteArray(85_000))
        val moov64 = buildSyntheticMoov(
            use64BitMoov = true,
            durationSec = 240,
            title = "Tail Moov 64",
            artist = "64Bit Artist",
            album = "64Bit Album",
            lyrics = "[00:20.00]64-bit extended box lyrics",
        )
        val fileBytes = ftyp + mdat64 + moov64
        val expectedMoovOffset = (ftyp.size + mdat64.size).toLong()

        // Direct EmbeddedLyricsExtractor check on 64-bit moov bytes (child boxes at offset 16)
        val directLyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(moov64))
        assertNotNull(directLyrics)
        assertEquals("64-bit extended box lyrics", directLyrics!!.lines[0].text)

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("m4a_64" to fileBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p6",
            fileId = "m4a_64",
            fileName = "tail64.m4a",
            fileSizeBytes = fileBytes.size.toLong(),
        )

        assertTrue("bytes=$expectedMoovOffset-${expectedMoovOffset + 15}" in ranges)
        assertTrue("bytes=$expectedMoovOffset-${expectedMoovOffset + moov64.size - 1}" in ranges)
        assertEquals("Tail Moov 64", result.metadata.title)
        assertEquals("64Bit Artist", result.metadata.artist)
        assertEquals(240, result.metadata.durationSec)
        assertEquals("64-bit extended box lyrics", result.metadata.lyrics?.lines?.firstOrNull()?.text)
    }

    @Test
    fun `skips embedded art larger than 2 MB without fetching image payload`() = runTest {
        val oversizedArt = ByteArray(2_200_000) { 0x11 }
        val apicFrame = id3ApicFrame(mime = "image/png", imageBytes = oversizedArt)
        val id3 = buildId3v2(
            version = 3,
            frames = listOf(
                apicFrame,
                id3TextFrame("TIT2", "Oversized Art Track"),
            ),
        )
        val fileBytes = id3 + ByteArray(2048)

        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("huge_art" to fileBytes), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p7",
            fileId = "huge_art",
            fileName = "huge.mp3",
            fileSizeBytes = fileBytes.size.toLong(),
        )

        assertNull(result.coverArtId)
        assertNull(result.coverFile)
        assertEquals(0L, result.coverSizeBytes)
        assertEquals("Oversized Art Track", result.metadata.title)

        val parsed = DriveTagParser.parseId3v2(fileBytes, fileBytes.size.toLong())!!
        val artDesc = parsed.tags.embeddedArt!!
        assertEquals(2_200_000L, artDesc.size)
        val forbiddenArtRange = "bytes=${artDesc.offset}-${artDesc.offset + artDesc.size - 1L}"
        assertFalse("Must never fetch > 2 MB art range", forbiddenArtRange in ranges)
    }

    @Test
    fun `untagged file falls back deterministically to parentFolderPath and filename`() = runTest {
        val rawAudio = ByteArray(1024) { 0x00 }
        val ranges = CopyOnWriteArrayList<String>()
        installRangeDispatcher(mapOf("untagged_1" to rawAudio), ranges)

        val result = createReader().readTrackMetadata(
            profileId = "p8",
            fileId = "untagged_1",
            fileName = "01 - Title.mp3",
            parentFolderPath = "Artist/Album",
            fileSizeBytes = 2_000_000L,
        )

        assertFalse(result.metadata.tagsParsed)
        assertEquals("Title", result.metadata.title)
        assertEquals("Artist", result.metadata.artist)
        assertEquals("Album", result.metadata.album)
        assertEquals(1, result.metadata.trackNo)
        assertEquals("mp3", result.metadata.suffix)

        // Verify Artist - Title filename inference + bitrate estimation normalization
        val dashInferred = DriveTagParser.inferFromPathAndFileName(
            fileName = "12. Boards of Canada - Roygbiv.flac",
            parentFolderPath = "Music/Geogaddi",
        )
        assertEquals(12, dashInferred.trackNo)
        assertEquals("Boards of Canada", dashInferred.artist)
        assertEquals("Roygbiv", dashInferred.title)
        assertEquals("Geogaddi", dashInferred.album)
        assertEquals(160, DriveTagParser.normalizeBitrate(null, durationSec = 100, fileSizeBytes = 2_000_000L))
    }

    @Test
    fun `parses Ogg Vorbis comments and OpusTags`() = runTest {
        val oggBytes = buildOggVorbis(
            nominalBitrateBps = 192_000,
            comments = listOf(
                "TITLE" to "Ogg Track",
                "ARTIST" to "Ogg Artist",
                "ALBUMARTIST" to "Ogg AA",
                "ALBUM" to "Ogg Album",
                "TRACKNUMBER" to "2",
                "DATE" to "2021",
                "GENRE" to "Ambient",
                "LYRICS" to "[00:02.00]Ogg lyric",
            ),
        )
        installRangeDispatcher(mapOf("ogg_1" to oggBytes))

        val result = createReader().readTrackMetadata(
            profileId = "p9",
            fileId = "ogg_1",
            fileName = "track.ogg",
            fileSizeBytes = 2_400_000L,
        )

        assertEquals("Ogg Track", result.metadata.title)
        assertEquals("Ogg Artist", result.metadata.artist)
        assertEquals("Ogg AA", result.metadata.albumArtist)
        assertEquals("Ogg Album", result.metadata.album)
        assertEquals(2, result.metadata.trackNo)
        assertEquals(2021, result.metadata.year)
        assertEquals(192, result.metadata.bitRate)
        assertEquals(100, result.metadata.durationSec)
        assertTrue(result.metadata.lyrics!!.isSynced)

        val opusPacket1 = "OpusHead".toByteArray(StandardCharsets.ISO_8859_1) + ByteArray(11)
        val opusTags = "OpusTags".toByteArray(StandardCharsets.ISO_8859_1) + buildVorbisCommentBlock(
            "TITLE" to "Opus Track",
            "ARTIST" to "Opus Artist",
            "LYRICS" to "Opus plain lyric",
        )
        val opusOgg = oggPage(opusPacket1) + oggPage(opusTags)
        val opusParsed = DriveTagParser.parseOgg(opusOgg, fileSizeBytes = 1_000_000L)!!
        assertEquals("Opus Track", opusParsed.title)
        assertEquals("Opus Artist", opusParsed.artist)
        assertEquals("Opus plain lyric", opusParsed.lyrics?.plainText)
    }

    // --- Synthetic Binary Builders ---

    private fun id3TextFrame(id: String, value: String): ByteArray {
        val payload = byteArrayOf(3) + value.toByteArray(StandardCharsets.UTF_8)
        return id3Frame(id, payload, synchsafe = false)
    }

    private fun id3UsltFrame(text: String): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(3)
            write("eng".toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(text.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        return id3Frame("USLT", payload, synchsafe = false)
    }

    private fun id3ApicFrame(mime: String, imageBytes: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(0)
            write(mime.toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(3) // Front Cover
            write(0) // Empty description
            write(imageBytes)
        }.toByteArray()
        return id3Frame("APIC", payload, synchsafe = false)
    }

    private fun id3Frame(id: String, payload: ByteArray, synchsafe: Boolean): ByteArray =
        ByteArrayOutputStream().apply {
            write(id.toByteArray(StandardCharsets.ISO_8859_1))
            val len = payload.size
            if (synchsafe) {
                write((len shr 21) and 0x7F)
                write((len shr 14) and 0x7F)
                write((len shr 7) and 0x7F)
                write(len and 0x7F)
            } else {
                write((len shr 24) and 0xFF)
                write((len shr 16) and 0xFF)
                write((len shr 8) and 0xFF)
                write(len and 0xFF)
            }
            write(0)
            write(0)
            write(payload)
        }.toByteArray()

    private fun buildId3v2(version: Int, frames: List<ByteArray>): ByteArray {
        val converted = if (version == 4) {
            frames.map { raw ->
                val id = String(raw, 0, 4, StandardCharsets.ISO_8859_1)
                id3Frame(id, raw.copyOfRange(10, raw.size), synchsafe = true)
            }
        } else {
            frames
        }
        val body = ByteArrayOutputStream().apply { converted.forEach { write(it) } }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
            write(version)
            write(0)
            write(0)
            val s = body.size
            write((s shr 21) and 0x7F)
            write((s shr 14) and 0x7F)
            write((s shr 7) and 0x7F)
            write(s and 0x7F)
            write(body)
        }.toByteArray()
    }

    private fun buildMpegXingAudio(totalFrames: Int, paddingSize: Int): ByteArray {
        val frame = ByteArray(paddingSize)
        // MPEG1 Layer III, 128 kbps (idx 9), 44100 Hz (idx 0), stereo (mode 0)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()
        // Side info = 32 bytes for MPEG1 stereo -> Xing at offset 36
        val xingPos = 36
        "Xing".toByteArray(StandardCharsets.ISO_8859_1).copyInto(frame, xingPos)
        frame[xingPos + 7] = 0x01 // frames flag
        frame[xingPos + 8] = ((totalFrames shr 24) and 0xFF).toByte()
        frame[xingPos + 9] = ((totalFrames shr 16) and 0xFF).toByte()
        frame[xingPos + 10] = ((totalFrames shr 8) and 0xFF).toByte()
        frame[xingPos + 11] = (totalFrames and 0xFF).toByte()
        return frame
    }

    private fun flacBlockHeader(isLast: Boolean, type: Int, length: Int): ByteArray = byteArrayOf(
        ((if (isLast) 0x80 else 0) or (type and 0x7F)).toByte(),
        ((length shr 16) and 0xFF).toByte(),
        ((length shr 8) and 0xFF).toByte(),
        (length and 0xFF).toByte(),
    )

    private fun buildFlacStreamInfo(sampleRate: Int, totalSamples: Long): ByteArray {
        val out = ByteArray(34)
        out[10] = ((sampleRate shr 12) and 0xFF).toByte()
        out[11] = ((sampleRate shr 4) and 0xFF).toByte()
        out[12] = ((sampleRate and 0x0F) shl 4).toByte()
        out[13] = ((totalSamples shr 32) and 0x0F).toByte()
        out[14] = ((totalSamples shr 24) and 0xFF).toByte()
        out[15] = ((totalSamples shr 16) and 0xFF).toByte()
        out[16] = ((totalSamples shr 8) and 0xFF).toByte()
        out[17] = (totalSamples and 0xFF).toByte()
        return out
    }

    private fun buildFlacPictureBlock(mime: String, imageBytes: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            writeIntBE(3) // Front Cover
            val mimeBytes = mime.toByteArray(StandardCharsets.UTF_8)
            writeIntBE(mimeBytes.size)
            write(mimeBytes)
            writeIntBE(0) // description len
            writeIntBE(500)
            writeIntBE(500)
            writeIntBE(24)
            writeIntBE(0)
            writeIntBE(imageBytes.size)
            write(imageBytes)
        }.toByteArray()

    private fun buildVorbisCommentBlock(vararg comments: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().apply {
            writeIntLE(4)
            write("test".toByteArray(StandardCharsets.UTF_8))
            writeIntLE(comments.size)
            for ((k, v) in comments) {
                val bytes = "$k=$v".toByteArray(StandardCharsets.UTF_8)
                writeIntLE(bytes.size)
                write(bytes)
            }
        }.toByteArray()

    private fun buildSyntheticMoov(
        use64BitMoov: Boolean,
        durationSec: Int,
        title: String? = null,
        artist: String? = null,
        albumArtist: String? = null,
        album: String? = null,
        trackNo: Int? = null,
        year: String? = null,
        genre: String? = null,
        lyrics: String? = null,
        coverJpeg: ByteArray? = null,
    ): ByteArray {
        val mvhdPayload = ByteArray(20).apply {
            // version 0, timescale 1000
            val ts = 1000
            val dur = durationSec * ts
            this[12] = ((ts shr 24) and 0xFF).toByte()
            this[13] = ((ts shr 16) and 0xFF).toByte()
            this[14] = ((ts shr 8) and 0xFF).toByte()
            this[15] = (ts and 0xFF).toByte()
            this[16] = ((dur shr 24) and 0xFF).toByte()
            this[17] = ((dur shr 16) and 0xFF).toByte()
            this[18] = ((dur shr 8) and 0xFF).toByte()
            this[19] = (dur and 0xFF).toByte()
        }
        val mvhd = mp4Box("mvhd", mvhdPayload)

        val ilstChildren = ByteArrayOutputStream().apply {
            title?.let { write(mp4IlstText("©nam", it)) }
            artist?.let { write(mp4IlstText("©ART", it)) }
            albumArtist?.let { write(mp4IlstText("aART", it)) }
            album?.let { write(mp4IlstText("©alb", it)) }
            year?.let { write(mp4IlstText("©day", it)) }
            genre?.let { write(mp4IlstText("©gen", it)) }
            trackNo?.let { trk ->
                val trkPayload = byteArrayOf(0, 0, ((trk shr 8) and 0xFF).toByte(), (trk and 0xFF).toByte(), 0, 0, 0, 0)
                write(mp4Box("trkn", mp4DataBox(type = 0, payload = trkPayload)))
            }
            lyrics?.let { write(mp4IlstText("©lyr", it)) }
            coverJpeg?.let { write(mp4Box("covr", mp4DataBox(type = 13, payload = it))) }
        }.toByteArray()

        val ilst = mp4Box("ilst", ilstChildren)
        val meta = mp4Box("meta", ByteArray(4) + ilst)
        val udta = mp4Box("udta", meta)
        val moovPayload = mvhd + udta
        return if (use64BitMoov) mp4Box64("moov", moovPayload) else mp4Box("moov", moovPayload)
    }

    private fun mp4IlstText(atomType: String, text: String): ByteArray =
        mp4Box(atomType, mp4DataBox(type = 1, payload = text.toByteArray(StandardCharsets.UTF_8)))

    private fun mp4DataBox(type: Int, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            writeIntBE(type)
            writeIntBE(0) // locale
            write(payload)
        }.toByteArray()
        return mp4Box("data", body)
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            writeIntBE(8 + payload.size)
            write(type.toByteArray(StandardCharsets.ISO_8859_1))
            write(payload)
        }.toByteArray()

    private fun mp4Box64(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            writeIntBE(1)
            write(type.toByteArray(StandardCharsets.ISO_8859_1))
            val total = 16L + payload.size.toLong()
            for (shift in 56 downTo 0 step 8) {
                write(((total shr shift) and 0xFFL).toInt())
            }
            write(payload)
        }.toByteArray()

    private fun buildOggVorbis(
        nominalBitrateBps: Int,
        comments: List<Pair<String, String>>,
    ): ByteArray {
        val idPacket = ByteArray(30).apply {
            this[0] = 1
            "vorbis".toByteArray(StandardCharsets.ISO_8859_1).copyInto(this, 1)
            this[20] = (nominalBitrateBps and 0xFF).toByte()
            this[21] = ((nominalBitrateBps shr 8) and 0xFF).toByte()
            this[22] = ((nominalBitrateBps shr 16) and 0xFF).toByte()
            this[23] = ((nominalBitrateBps shr 24) and 0xFF).toByte()
        }
        val commentPayload = buildVorbisCommentBlock(*comments.toTypedArray())
        val commentPacket = byteArrayOf(3) + "vorbis".toByteArray(StandardCharsets.ISO_8859_1) + commentPayload
        return oggPage(idPacket) + oggPage(commentPacket)
    }

    private fun oggPage(packet: ByteArray): ByteArray {
        val segments = mutableListOf<Int>()
        var rem = packet.size
        while (rem >= 255) {
            segments += 255
            rem -= 255
        }
        segments += rem
        return ByteArrayOutputStream().apply {
            write("OggS".toByteArray(StandardCharsets.ISO_8859_1))
            write(ByteArray(22))
            write(segments.size)
            segments.forEach { write(it) }
            write(packet)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntBE(v: Int) {
        write((v shr 24) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 8) and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }
}
