package com.feishin.mobile.data.playlist

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.Track
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MAX_M3U_BYTES: Long = 5L * 1024L * 1024L

/** Explicit exception thrown when an imported `.m3u`/`.m3u8` stream exceeds [MAX_M3U_BYTES]. */
class M3uFileTooLargeException(
    val maxBytes: Long = MAX_M3U_BYTES,
) : IllegalArgumentException("M3U file exceeds maximum allowed size of $maxBytes bytes")

data class M3uEntry(
    val rawLocation: String,
    val normalizedPath: String,
    val parentFolderPath: String,
    val fileName: String,
    val durationSec: Int? = null,
    val artist: String? = null,
    val title: String? = null,
)

data class M3uImportResult(
    val importedCount: Int,
    val unmatchedCount: Int,
    val playlistId: String,
    val unmatchedLines: List<String>,
)

/**
 * Pure JVM `.m3u` / `.m3u8` parser.
 *
 * Enforces a hard [MAX_M3U_BYTES] cap, strips UTF-8 BOM, handles CRLF/LF line endings,
 * parses `#EXTINF` metadata, normalizes `\` -> `/`, and cleans optional file/URL prefixes.
 */
object M3uParser {

    private val SCHEME_PREFIX_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val HTTP_HOST_REGEX = Regex("^https?://[^/]+", RegexOption.IGNORE_CASE)
    private val WINDOWS_DRIVE_REGEX = Regex("^[A-Za-z]:/")
    private val MULTI_SLASH_REGEX = Regex("/{2,}")

    private data class PendingExtInf(
        val durationSec: Int?,
        val artist: String?,
        val title: String?,
    )

    fun parse(inputStream: InputStream): List<M3uEntry> {
        val bytes = readBoundedBytes(inputStream)
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF')
        if (text.isBlank()) return emptyList()

        val entries = mutableListOf<M3uEntry>()
        var pendingExtInf: PendingExtInf? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimStart('\uFEFF').trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    pendingExtInf = parseExtInf(line.substring("#EXTINF:".length))
                }
                // Skip #EXTM3U, #EXTINF (consumed above), and other # comments.
                continue
            }

            val normalized = normalizeLocation(line)
            if (normalized.isNotEmpty()) {
                val slashIdx = normalized.lastIndexOf('/')
                val parent = if (slashIdx > 0) normalized.substring(0, slashIdx) else ""
                val fileName = if (slashIdx >= 0) normalized.substring(slashIdx + 1) else normalized
                if (fileName.isNotEmpty()) {
                    entries.add(
                        M3uEntry(
                            rawLocation = line,
                            normalizedPath = normalized,
                            parentFolderPath = parent,
                            fileName = fileName,
                            durationSec = pendingExtInf?.durationSec,
                            artist = pendingExtInf?.artist,
                            title = pendingExtInf?.title,
                        ),
                    )
                }
            }
            pendingExtInf = null
        }

        return entries
    }

    private fun readBoundedBytes(inputStream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > MAX_M3U_BYTES) {
                throw M3uFileTooLargeException(MAX_M3U_BYTES)
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parseExtInf(rawPayload: String): PendingExtInf {
        val commaIdx = rawPayload.indexOf(',')
        val metaPart = if (commaIdx >= 0) rawPayload.substring(0, commaIdx) else rawPayload
        val displayPart = if (commaIdx >= 0) rawPayload.substring(commaIdx + 1).trim() else ""

        val durationToken = metaPart.trim().split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
        val durationSec = durationToken.toDoubleOrNull()?.roundToInt()?.takeIf { it >= 0 }

        if (displayPart.isEmpty()) {
            return PendingExtInf(durationSec = durationSec, artist = null, title = null)
        }

        val sepIdx = displayPart.indexOf(" - ")
        return if (sepIdx > 0) {
            val artist = displayPart.substring(0, sepIdx).trim().takeIf { it.isNotEmpty() }
            val title = displayPart.substring(sepIdx + 3).trim().takeIf { it.isNotEmpty() }
            PendingExtInf(durationSec = durationSec, artist = artist, title = title)
        } else {
            PendingExtInf(durationSec = durationSec, artist = null, title = displayPart)
        }
    }

    internal fun normalizeLocation(rawLocation: String): String {
        var path = rawLocation.trim()
        if (path.isEmpty()) return ""

        val isUri = SCHEME_PREFIX_REGEX.containsMatchIn(path)
        if (isUri) {
            path = path.substringBefore('#').substringBefore('?')
            path = when {
                path.startsWith("file://localhost/", ignoreCase = true) ->
                    path.substring("file://localhost/".length)
                path.startsWith("file://", ignoreCase = true) ->
                    path.substring("file://".length)
                HTTP_HOST_REGEX.containsMatchIn(path) ->
                    HTTP_HOST_REGEX.replaceFirst(path, "")
                else -> path.replaceFirst(SCHEME_PREFIX_REGEX, "")
            }
            path = decodePercentEscapes(path)
        }

        path = path.replace('\\', '/')
        path = MULTI_SLASH_REGEX.replace(path, "/")
        while (path.startsWith("./")) {
            path = path.removePrefix("./")
        }
        path = path.trimStart('/')
        if (WINDOWS_DRIVE_REGEX.containsMatchIn(path)) {
            path = path.substring(3)
        }
        return path.trimEnd('/')
    }

    private fun decodePercentEscapes(value: String): String {
        if ('%' !in value) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val byteVal = hex.toIntOrNull(16)
                if (byteVal != null) {
                    out.write(byteVal)
                    i += 3
                    continue
                }
            }
            out.write(ch.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/**
 * Imports `.m3u`/`.m3u8` streams into editable `local_pl:*` playlists by deterministically
 * matching parsed entries against indexed `drive_tracks` and optional federated/device tracks.
 */
@Singleton
class M3uImporter @Inject constructor(
    private val driveTrackDao: DriveTrackDao,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val profileStore: ProfileStore,
) {
    private data class IndexedCandidate(
        val track: Track,
        val normalizedRelPath: String,
        val fileNameLower: String,
        val fileStemLower: String,
        val normArtist: String,
        val normTitle: String,
    )

    suspend fun import(
        inputStream: InputStream,
        playlistName: String,
        sourceM3uName: String? = null,
        candidateTracks: List<Track> = emptyList(),
        candidateTrackLookup: (suspend () -> List<Track>)? = null,
    ): M3uImportResult = withContext(Dispatchers.IO) {
        val entries = M3uParser.parse(inputStream)
        val candidates = loadOrderedCandidates(candidateTracks, candidateTrackLookup)

        val matchedTracks = mutableListOf<Track>()
        val unmatchedLines = mutableListOf<String>()

        for (entry in entries) {
            val match = findBestMatch(entry, candidates)
            if (match != null) {
                matchedTracks.add(match)
            } else {
                unmatchedLines.add(entry.rawLocation)
            }
        }

        val playlist: Playlist = localPlaylistRepository.createPlaylist(
            name = playlistName,
            tracks = matchedTracks,
            sourceM3uName = sourceM3uName,
        )

        M3uImportResult(
            importedCount = matchedTracks.size,
            unmatchedCount = unmatchedLines.size,
            playlistId = playlist.id,
            unmatchedLines = unmatchedLines,
        )
    }

    private suspend fun loadOrderedCandidates(
        candidateTracks: List<Track>,
        candidateTrackLookup: (suspend () -> List<Track>)?,
    ): List<IndexedCandidate> {
        runCatching { profileStore.preloadIfNeeded() }
        val activeDriveProfiles = profileStore.activeProfiles.value
            .filter { it.type == BackendType.GOOGLE_DRIVE }
            .sortedWith(compareBy({ it.priority }, { it.id }))

        val seenIds = HashSet<String>()
        val ordered = mutableListOf<IndexedCandidate>()

        for (profile in activeDriveProfiles) {
            val entities = driveTrackDao.tracksForProfile(profile.id)
            for (entity in entities) {
                val track = entity.toDomainTrack(profile.name)
                if (seenIds.add(track.id)) {
                    ordered.add(indexCandidate(track))
                }
            }
        }

        val extraTracks = buildList {
            addAll(candidateTracks)
            if (candidateTrackLookup != null) {
                addAll(candidateTrackLookup())
            }
        }
        for (track in extraTracks) {
            if (seenIds.add(track.id)) {
                ordered.add(indexCandidate(track))
            }
        }

        return ordered
    }

    private fun indexCandidate(track: Track): IndexedCandidate {
        val normPath = M3uParser.normalizeLocation(track.path)
        val normPathLower = normPath.lowercase(Locale.ROOT)
        val fileNameLower = normPathLower.substringAfterLast('/', normPathLower)
        val fileStemLower = stripExtension(fileNameLower)
        return IndexedCandidate(
            track = track,
            normalizedRelPath = normPathLower,
            fileNameLower = fileNameLower,
            fileStemLower = fileStemLower,
            normArtist = normalizeMeta(track.artist),
            normTitle = normalizeMeta(track.title),
        )
    }

    private fun findBestMatch(
        entry: M3uEntry,
        candidates: List<IndexedCandidate>,
    ): Track? {
        if (candidates.isEmpty()) return null

        val entryPathLower = entry.normalizedPath.lowercase(Locale.ROOT)
        val entryFileLower = entry.fileName.lowercase(Locale.ROOT)
        val entryStemLower = stripExtension(entryFileLower)

        // Tier 1: normalized parentFolderPath/fileName
        if (entry.parentFolderPath.isNotEmpty()) {
            candidates.firstOrNull { it.normalizedRelPath == entryPathLower }?.let { return it.track }
            candidates.firstOrNull { candidate ->
                candidate.normalizedRelPath.contains('/') &&
                    (
                        entryPathLower.endsWith("/${candidate.normalizedRelPath}") ||
                            candidate.normalizedRelPath.endsWith("/$entryPathLower")
                        )
            }?.let { return it.track }
        } else {
            candidates.firstOrNull { it.normalizedRelPath == entryPathLower }?.let { return it.track }
        }

        // Tier 2: exact case-insensitive fileName
        if (entryFileLower.isNotEmpty()) {
            candidates.firstOrNull { it.fileNameLower == entryFileLower }?.let { return it.track }
        }

        // Tier 3a: normalized #EXTINF artist + title (or title-only fallback)
        val normEntryTitle = entry.title?.let(::normalizeMeta)?.takeIf { it.isNotEmpty() }
        val normEntryArtist = entry.artist?.let(::normalizeMeta)?.takeIf { it.isNotEmpty() }
        if (normEntryTitle != null) {
            if (normEntryArtist != null) {
                candidates.firstOrNull {
                    it.normArtist == normEntryArtist && it.normTitle == normEntryTitle
                }?.let { return it.track }
            } else {
                candidates.firstOrNull { it.normTitle == normEntryTitle }?.let { return it.track }
            }
        }

        // Tier 3b: strip-extension fallback against candidate file stem / metadata
        if (entryStemLower.isNotEmpty()) {
            candidates.firstOrNull { it.fileStemLower == entryStemLower }?.let { return it.track }

            val sepIdx = entryStemLower.indexOf(" - ")
            if (sepIdx > 0) {
                val stemArtist = normalizeMeta(entryStemLower.substring(0, sepIdx))
                val stemTitle = normalizeMeta(entryStemLower.substring(sepIdx + 3))
                if (stemArtist.isNotEmpty() && stemTitle.isNotEmpty()) {
                    candidates.firstOrNull {
                        it.normArtist == stemArtist && it.normTitle == stemTitle
                    }?.let { return it.track }
                }
            }

            val normStem = normalizeMeta(entryStemLower)
            if (normStem.isNotEmpty()) {
                candidates.firstOrNull { it.normTitle == normStem }?.let { return it.track }
            }
        }

        return null
    }

    private fun DriveTrackEntity.toDomainTrack(sourceLabel: String?): Track {
        val globalSongId = if (songId.startsWith("$profileId:")) songId else GlobalId.format(profileId, songId)
        val relPath = if (parentFolderPath.isNotBlank()) {
            "${parentFolderPath.trimEnd('/')}/$fileName"
        } else {
            fileName
        }
        return Track(
            id = globalSongId,
            title = title,
            album = album,
            albumId = albumKey,
            artist = artist,
            artistId = artistKey,
            coverArt = coverArtId,
            durationSec = durationSec,
            trackNo = trackNo,
            year = year,
            genre = genre,
            suffix = suffix,
            starred = false,
            sizeBytes = sizeBytes,
            bitRate = bitRate,
            path = relPath,
            sourceProfileId = profileId,
            sourceLabel = sourceLabel,
        )
    }

    private fun stripExtension(fileName: String): String {
        val dotIdx = fileName.lastIndexOf('.')
        return if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
    }

    private fun normalizeMeta(value: String): String =
        value.trim()
            .replace(MULTI_SPACE_REGEX, " ")
            .lowercase(Locale.ROOT)

    private companion object {
        val MULTI_SPACE_REGEX = Regex("\\s+")
    }
}
