package com.feishin.mobile.data.remote.drive

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Coil [Fetcher] for `drive-cover://{profileId}/{driveFileId}` URIs.
 *
 * Serves pre-extracted JPEG artwork from [CachedCoverDao] when present on disk, or lazily
 * extracts bounded (`<= 2 MB`, `<= 512 px`) embedded artwork via [DriveRangeReader] on cache miss.
 */
class DriveCoverFetcher(
    private val profileId: String,
    private val fileId: String,
    private val cachedCoverDao: CachedCoverDao,
    private val driveRangeReader: DriveRangeReader,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val coverArtId = "$profileId:cov:$fileId"
        val existingPath = cachedCoverDao.path(coverArtId)
        if (!existingPath.isNullOrBlank()) {
            val file = File(existingPath)
            if (file.exists() && file.length() > 0L) {
                return@withContext SourceResult(
                    source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
                    mimeType = MIME_JPEG,
                    dataSource = DataSource.DISK,
                )
            }
        }

        val extracted = driveRangeReader.extractCoverArt(
            profileId = profileId,
            fileId = fileId,
        ) ?: return@withContext null

        if (!extracted.exists() || extracted.length() <= 0L) return@withContext null

        cachedCoverDao.upsert(
            CachedCoverEntity(
                coverArtId = coverArtId,
                filePath = extracted.absolutePath,
                sizeBytes = extracted.length(),
            ),
        )
        SourceResult(
            source = ImageSource(extracted.toOkioPath(), FileSystem.SYSTEM),
            mimeType = MIME_JPEG,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory @Inject constructor(
        private val cachedCoverDao: CachedCoverDao,
        private val driveRangeReader: DriveRangeReader,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val (profileId, fileId) = parseDriveCoverUri(
                scheme = runCatching { data.scheme }.getOrNull(),
                host = runCatching { data.host }.getOrNull(),
                firstSegment = runCatching { data.pathSegments?.firstOrNull() }.getOrNull(),
                rawFallback = runCatching { data.toString() }.getOrNull(),
            ) ?: return null
            return DriveCoverFetcher(
                profileId = profileId,
                fileId = fileId,
                cachedCoverDao = cachedCoverDao,
                driveRangeReader = driveRangeReader,
            )
        }
    }

    companion object {
        const val SCHEME_DRIVE_COVER = "drive-cover"
        private const val MIME_JPEG = "image/jpeg"
        private val DRIVE_COVER_URI_REGEX = Regex("""^drive-cover://([^/]+)/([^/?#]+)""", RegexOption.IGNORE_CASE)

        internal fun parseDriveCoverUri(
            scheme: String?,
            host: String?,
            firstSegment: String?,
            rawFallback: String?,
        ): Pair<String, String>? {
            if (scheme.equals(SCHEME_DRIVE_COVER, ignoreCase = true)) {
                val p = host?.trim()?.takeIf { GlobalId.PROFILE_ID_REGEX.matches(it) }
                val f = firstSegment?.trim()?.takeIf { it.isNotEmpty() }
                if (p != null && f != null) return p to f
            }
            val match = rawFallback?.let { DRIVE_COVER_URI_REGEX.find(it) } ?: return null
            val p = match.groupValues[1].trim()
            val f = match.groupValues[2].trim()
            if (!GlobalId.PROFILE_ID_REGEX.matches(p) || f.isEmpty()) return null
            return p to f
        }
    }
}
