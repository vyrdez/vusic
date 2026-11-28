package com.feishin.mobile.data.remote.drive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.lyrics.EmbeddedLyricsExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

fun buildGoogleDriveFileUrl(fileId: String): String =
    HttpUrl.Builder()
        .scheme("https")
        .host("www.googleapis.com")
        .addPathSegments("drive/v3/files")
        .addPathSegment(fileId)
        .addQueryParameter("alt", "media")
        .build()
        .toString()

fun interface DriveCoverTranscoder {
    fun transcodeToJpeg(imageBytes: ByteArray, maxDimensionPx: Int): ByteArray?
}

object AndroidDriveCoverTranscoder : DriveCoverTranscoder {
    override fun transcodeToJpeg(imageBytes: ByteArray, maxDimensionPx: Int): ByteArray? {
        if (imageBytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimensionPx &&
            bounds.outHeight / (sampleSize * 2) >= maxDimensionPx
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts) ?: return null
        var scaled: Bitmap? = null
        return try {
            val target = if (decoded.width > maxDimensionPx || decoded.height > maxDimensionPx) {
                val ratio = minOf(
                    maxDimensionPx.toFloat() / decoded.width.toFloat(),
                    maxDimensionPx.toFloat() / decoded.height.toFloat(),
                )
                val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
                scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
                scaled
            } else {
                decoded
            }
            ByteArrayOutputStream().use { out ->
                if (!target.compress(Bitmap.CompressFormat.JPEG, 88, out)) return null
                out.toByteArray()
            }
        } finally {
            if (scaled != null && scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}

data class DriveRangeReadResult(
    val metadata: ResolvedTrackMetadata,
    val coverArtId: String? = null,
    val coverFile: File? = null,
    val coverSizeBytes: Long = 0L,
    val httpStatusCode: Int? = null,
    val retryAfterHeader: String? = null,
)

/**
 * Executes HTTP Range reads against Google Drive `/drive/v3/files/{fileId}?alt=media`
 * (tagged with [ProfileIdTag]) to extract audio tags, lyrics, and bounded embedded artwork.
 */
@Singleton
class DriveRangeReader internal constructor(
    private val client: OkHttpClient,
    private val coversDirProvider: () -> File,
    private val transcoder: DriveCoverTranscoder,
    private val fileUrlBuilder: (String) -> String,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ) : this(
        client = client,
        coversDirProvider = { File(context.filesDir, "download_covers").apply { mkdirs() } },
        transcoder = AndroidDriveCoverTranscoder,
        fileUrlBuilder = ::buildGoogleDriveFileUrl,
    )

    companion object {
        const val INITIAL_RANGE_END = 65_535L
        const val MAX_ID3V2_EXPAND_END = 524_287L
        const val MAX_FLAC_METADATA_HOPS = 8
        const val MAX_EMBEDDED_ART_BYTES = 2_097_152L
        const val MAX_COVER_DIMENSION_PX = 512
        private val CONTENT_RANGE_TOTAL_REGEX = Regex("""bytes\s+\d+-\d+/(\d+)""")
    }

    private data class RangeFetchResult(
        val statusCode: Int,
        val bytes: ByteArray?,
        val totalBytes: Long?,
        val retryAfter: String?,
    )

    private data class TagExtractionOutput(
        val parsed: ParsedAudioTags?,
        val inMemoryBuffers: List<Pair<Long, ByteArray>>,
    )

    suspend fun readTrackMetadata(
        profileId: String,
        fileId: String,
        fileName: String,
        parentFolderPath: String = "",
        fileSizeBytes: Long = 0L,
        extractCoverArt: Boolean = true,
    ): DriveRangeReadResult {
        val initial = runCatching {
            fetchRange(profileId, fileId, 0L, INITIAL_RANGE_END)
        }.getOrElse {
            return DriveRangeReadResult(
                metadata = DriveTagParser.inferFromPathAndFileName(fileName, parentFolderPath),
            )
        }

        val initialBytes = initial.bytes
        if (initialBytes == null || initialBytes.isEmpty()) {
            return DriveRangeReadResult(
                metadata = DriveTagParser.inferFromPathAndFileName(fileName, parentFolderPath),
                httpStatusCode = initial.statusCode,
                retryAfterHeader = initial.retryAfter,
            )
        }

        val effectiveFileSize = when {
            fileSizeBytes > 0L -> fileSizeBytes
            initial.totalBytes != null && initial.totalBytes > 0L -> initial.totalBytes
            else -> initialBytes.size.toLong()
        }

        val extracted = extractTagsFromInitialBuffer(
            profileId = profileId,
            fileId = fileId,
            initialBytes = initialBytes,
            effectiveFileSize = effectiveFileSize,
        )

        val resolved = DriveTagParser.resolveMetadata(
            parsed = extracted.parsed,
            fileName = fileName,
            parentFolderPath = parentFolderPath,
            fileSizeBytes = effectiveFileSize,
        )

        var coverArtId: String? = null
        var coverFile: File? = null
        var coverSizeBytes = 0L

        val art = extracted.parsed?.embeddedArt
        if (extractCoverArt && art != null && art.size in 1L..MAX_EMBEDDED_ART_BYTES) {
            val saved = fetchAndSaveArt(
                profileId = profileId,
                fileId = fileId,
                art = art,
                inMemoryBuffers = extracted.inMemoryBuffers,
            )
            if (saved != null) {
                coverArtId = "$profileId:cov:$fileId"
                coverFile = saved
                coverSizeBytes = saved.length()
            }
        }

        return DriveRangeReadResult(
            metadata = resolved,
            coverArtId = coverArtId,
            coverFile = coverFile,
            coverSizeBytes = coverSizeBytes,
            httpStatusCode = initial.statusCode,
        )
    }

    /** Re-extracts and persists embedded cover art on cache miss (e.g. from `DriveCoverFetcher`). */
    suspend fun extractCoverArt(
        profileId: String,
        fileId: String,
        fileSizeBytes: Long = 0L,
    ): File? {
        val result = readTrackMetadata(
            profileId = profileId,
            fileId = fileId,
            fileName = "",
            parentFolderPath = "",
            fileSizeBytes = fileSizeBytes,
            extractCoverArt = true,
        )
        return result.coverFile
    }

    private suspend fun extractTagsFromInitialBuffer(
        profileId: String,
        fileId: String,
        initialBytes: ByteArray,
        effectiveFileSize: Long,
    ): TagExtractionOutput {
        val id3Header = DriveTagParser.inspectId3v2Header(initialBytes)
        if (id3Header != null) {
            return extractId3(profileId, fileId, initialBytes, id3Header, effectiveFileSize)
        }

        if (initialBytes.size >= 4 &&
            initialBytes[0] == 'f'.code.toByte() &&
            initialBytes[1] == 'L'.code.toByte() &&
            initialBytes[2] == 'a'.code.toByte() &&
            initialBytes[3] == 'C'.code.toByte()
        ) {
            return extractFlac(profileId, fileId, initialBytes, effectiveFileSize)
        }

        if (DriveTagParser.isMp4Container(initialBytes)) {
            return extractMp4(profileId, fileId, initialBytes, effectiveFileSize)
        }

        if (initialBytes.size >= 4 &&
            initialBytes[0] == 'O'.code.toByte() &&
            initialBytes[1] == 'g'.code.toByte() &&
            initialBytes[2] == 'g'.code.toByte() &&
            initialBytes[3] == 'S'.code.toByte()
        ) {
            val parsed = DriveTagParser.parseOgg(initialBytes, effectiveFileSize)
            val lyrics = parsed?.lyrics ?: EmbeddedLyricsExtractor.extract(ByteArrayInputStream(initialBytes))
            return TagExtractionOutput(
                parsed = parsed?.copy(lyrics = lyrics),
                inMemoryBuffers = listOf(0L to initialBytes),
            )
        }

        val rawMpeg = DriveTagParser.parseRawMpegAudio(initialBytes, effectiveFileSize)
        return TagExtractionOutput(
            parsed = rawMpeg,
            inMemoryBuffers = listOf(0L to initialBytes),
        )
    }

    private suspend fun extractId3(
        profileId: String,
        fileId: String,
        initialBytes: ByteArray,
        header: Id3v2HeaderInfo,
        effectiveFileSize: Long,
    ): TagExtractionOutput {
        val buffers = mutableListOf(0L to initialBytes)
        var tagBuffer = initialBytes

        if (header.totalTagSpanBytes > initialBytes.size.toLong() &&
            effectiveFileSize > initialBytes.size.toLong()
        ) {
            val expandedEnd = minOf(header.tagSize.toLong() + 9L, MAX_ID3V2_EXPAND_END, effectiveFileSize - 1L)
            if (expandedEnd >= initialBytes.size.toLong()) {
                val expanded = runCatching {
                    fetchRange(profileId, fileId, 0L, expandedEnd).bytes
                }.getOrNull()
                if (expanded != null && expanded.size > initialBytes.size) {
                    tagBuffer = expanded
                    buffers[0] = 0L to expanded
                }
            }
        }

        val firstPass = DriveTagParser.parseId3v2(tagBuffer, effectiveFileSize)
        var tags = firstPass?.tags
        if (tags != null && tags.lyrics == null && header.totalTagSpanBytes <= tagBuffer.size.toLong()) {
            val extractorLyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(tagBuffer))
            if (extractorLyrics != null) {
                tags = tags.copy(lyrics = extractorLyrics)
            }
        }

        // If an oversized frame (e.g. >512 KB APIC) truncated the 512 KB header fetch before
        // trailing text/lyrics frames, issue one targeted seek past that frame to finish parsing.
        val nextOffset = firstPass?.nextFrameOffset
        if (tags != null &&
            nextOffset != null &&
            nextOffset < firstPass.id3EndOffset &&
            nextOffset < effectiveFileSize
        ) {
            val tailEnd = minOf(firstPass.id3EndOffset + 4095L, nextOffset + 131_071L, effectiveFileSize - 1L)
            if (tailEnd >= nextOffset) {
                val tailBytes = runCatching {
                    fetchRange(profileId, fileId, nextOffset, tailEnd).bytes
                }.getOrNull()
                if (tailBytes != null && tailBytes.isNotEmpty()) {
                    buffers += nextOffset to tailBytes
                    val tailResult = DriveTagParser.parseId3v2FramesSegment(
                        segment = tailBytes,
                        baseOffset = nextOffset,
                        majorVersion = header.majorVersion,
                        id3EndOffset = firstPass.id3EndOffset,
                        fileSizeBytes = effectiveFileSize,
                    )
                    tags = mergeTags(tags, tailResult.tags, effectiveFileSize)
                }
            }
        }

        return TagExtractionOutput(parsed = tags, inMemoryBuffers = buffers)
    }

    private suspend fun extractFlac(
        profileId: String,
        fileId: String,
        initialBytes: ByteArray,
        effectiveFileSize: Long,
    ): TagExtractionOutput {
        val buffers = mutableListOf(0L to initialBytes)
        val firstResult = DriveTagParser.parseFlacBlocks(initialBytes, baseOffset = 0L, fileSizeBytes = effectiveFileSize)
            ?: return TagExtractionOutput(null, buffers)

        var merged = firstResult.tags
        var hops = 0
        var nextOffset = firstResult.nextBlockOffset
        var truncatedVorbisOffset = firstResult.truncatedVorbisCommentOffset
        var truncatedVorbisSize = firstResult.truncatedVorbisCommentTotalBytes
        var reachedVorbis = firstResult.reachedVorbisComment
        var reachedLast = firstResult.reachedLastBlock

        while (!reachedVorbis && !reachedLast && hops < MAX_FLAC_METADATA_HOPS) {
            val targetOffset: Long
            val fetchEnd: Long
            if (truncatedVorbisOffset != null && truncatedVorbisSize > 0L) {
                targetOffset = truncatedVorbisOffset
                fetchEnd = minOf(
                    truncatedVorbisOffset + truncatedVorbisSize - 1L,
                    truncatedVorbisOffset + MAX_ID3V2_EXPAND_END,
                    effectiveFileSize - 1L,
                )
            } else if (nextOffset != null && nextOffset < effectiveFileSize) {
                targetOffset = nextOffset
                fetchEnd = minOf(nextOffset + INITIAL_RANGE_END, effectiveFileSize - 1L)
            } else {
                break
            }

            if (fetchEnd < targetOffset) break
            hops++
            val chunk = runCatching {
                fetchRange(profileId, fileId, targetOffset, fetchEnd).bytes
            }.getOrNull() ?: break
            if (chunk.isEmpty()) break

            buffers += targetOffset to chunk
            val step = DriveTagParser.parseFlacBlocks(chunk, baseOffset = targetOffset, fileSizeBytes = effectiveFileSize)
                ?: break
            merged = mergeTags(merged, step.tags, effectiveFileSize)
            reachedVorbis = step.reachedVorbisComment
            reachedLast = step.reachedLastBlock
            nextOffset = step.nextBlockOffset
            truncatedVorbisOffset = step.truncatedVorbisCommentOffset
            truncatedVorbisSize = step.truncatedVorbisCommentTotalBytes
        }

        if (merged.lyrics == null) {
            val fallbackLyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(initialBytes))
            if (fallbackLyrics != null) {
                merged = merged.copy(lyrics = fallbackLyrics)
            }
        }

        return TagExtractionOutput(parsed = merged, inMemoryBuffers = buffers)
    }

    private suspend fun extractMp4(
        profileId: String,
        fileId: String,
        initialBytes: ByteArray,
        effectiveFileSize: Long,
    ): TagExtractionOutput {
        val faststartMoov = DriveTagParser.findFaststartMp4Moov(initialBytes)
        val moov = faststartMoov ?: Mp4TailMoovFetcher.fetchMoov(
            initialBuffer = initialBytes,
            fileSizeBytes = effectiveFileSize,
        ) { start, end ->
            runCatching { fetchRange(profileId, fileId, start, end).bytes }.getOrNull()
        }

        if (moov == null) {
            return TagExtractionOutput(null, listOf(0L to initialBytes))
        }

        val parsedTags = DriveTagParser.parseMp4Moov(
            moovBytes = moov.moovBytes,
            moovFileOffset = moov.moovOffset,
            fileSizeBytes = effectiveFileSize,
        )
        val lyrics = EmbeddedLyricsExtractor.extract(ByteArrayInputStream(moov.moovBytes)) ?: parsedTags?.lyrics
        val merged = parsedTags?.copy(lyrics = lyrics, tagsFound = parsedTags.tagsFound || lyrics != null)
        return TagExtractionOutput(
            parsed = merged,
            inMemoryBuffers = listOf(0L to initialBytes, moov.moovOffset to moov.moovBytes),
        )
    }

    private suspend fun fetchAndSaveArt(
        profileId: String,
        fileId: String,
        art: EmbeddedArtDescriptor,
        inMemoryBuffers: List<Pair<Long, ByteArray>>,
    ): File? {
        val rawArt = sliceInMemory(art, inMemoryBuffers) ?: runCatching {
            fetchRange(profileId, fileId, art.offset, art.offset + art.size - 1L).bytes
        }.getOrNull()
        if (rawArt == null || rawArt.isEmpty()) return null

        val jpegBytes = transcoder.transcodeToJpeg(rawArt, MAX_COVER_DIMENSION_PX) ?: return null
        if (jpegBytes.isEmpty()) return null

        val dir = coversDirProvider().apply { mkdirs() }
        val safeId = DownloadRepository.sanitizeCoverArtId("$profileId:cov:$fileId")
        val targetFile = File(dir, "$safeId.jpg")
        val tempFile = File(dir, "$safeId.part")
        return runCatching {
            tempFile.writeBytes(jpegBytes)
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.takeIf { it.exists() && it.length() > 0L }
        }.onFailure {
            runCatching { tempFile.delete() }
        }.getOrNull()
    }

    private fun sliceInMemory(
        art: EmbeddedArtDescriptor,
        buffers: List<Pair<Long, ByteArray>>,
    ): ByteArray? {
        val artEnd = art.offset + art.size
        for ((base, buf) in buffers) {
            val bufEnd = base + buf.size.toLong()
            if (art.offset >= base && artEnd <= bufEnd) {
                val localStart = (art.offset - base).toInt()
                val localEnd = (artEnd - base).toInt()
                return buf.copyOfRange(localStart, localEnd)
            }
        }
        return null
    }

    private fun mergeTags(
        primary: ParsedAudioTags,
        secondary: ParsedAudioTags,
        fileSizeBytes: Long,
    ): ParsedAudioTags {
        val duration = if (primary.durationSec > 0) primary.durationSec else secondary.durationSec
        val rawBitrate = primary.bitRateKbps ?: secondary.bitRateKbps
        return ParsedAudioTags(
            title = primary.title ?: secondary.title,
            artist = primary.artist ?: secondary.artist,
            albumArtist = primary.albumArtist ?: secondary.albumArtist,
            album = primary.album ?: secondary.album,
            trackNo = primary.trackNo ?: secondary.trackNo,
            year = primary.year ?: secondary.year,
            genre = primary.genre ?: secondary.genre,
            durationSec = duration,
            bitRateKbps = DriveTagParser.normalizeBitrate(rawBitrate, duration, fileSizeBytes),
            embeddedArt = primary.embeddedArt ?: secondary.embeddedArt,
            lyrics = primary.lyrics ?: secondary.lyrics,
            tagsFound = primary.tagsFound || secondary.tagsFound,
        )
    }

    private suspend fun fetchRange(
        profileId: String,
        fileId: String,
        start: Long,
        endInclusive: Long,
    ): RangeFetchResult {
        val request = Request.Builder()
            .url(fileUrlBuilder(fileId))
            .header("Range", "bytes=$start-$endInclusive")
            .tag(ProfileIdTag::class.java, ProfileIdTag(profileId))
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val retryAfter = resp.header("Retry-After")
                        val total = resp.header("Content-Range")?.let { header ->
                            CONTENT_RANGE_TOTAL_REGEX.find(header)?.groupValues?.get(1)?.toLongOrNull()
                        } ?: resp.body?.contentLength()?.takeIf { resp.code == 200 && it > 0L }
                        val bodyBytes = if (resp.code == 206 || resp.code == 200) {
                            resp.body?.bytes()
                        } else {
                            null
                        }
                        if (cont.isActive) {
                            cont.resume(
                                RangeFetchResult(
                                    statusCode = resp.code,
                                    bytes = bodyBytes,
                                    totalBytes = total,
                                    retryAfter = retryAfter,
                                ),
                            )
                        }
                    }
                }
            })
        }
    }
}
