package com.feishin.mobile.data.playlist

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.LocalPlaylistDao
import com.feishin.mobile.data.db.LocalPlaylistEntity
import com.feishin.mobile.data.db.LocalPlaylistTrackEntity
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.model.Track
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val LOCAL_PLAYLIST_ID_PREFIX = "local_pl:"
private const val COLLAGE_MARKER_PREFIX = "local_collage:"

/** Row inside a `local_pl:*` playlist with its Room row id, 0-based position, and availability flag. */
data class LocalPlaylistEntry(
    val entryId: Long,
    val position: Int,
    val track: Track,
    val isSourceAvailable: Boolean,
)

/** Cross-server download partition for a `local_pl:*` playlist or track list. */
data class CrossServerDownloadPartition(
    val downloadableByProfile: Map<String, List<Track>>,
    val localTracks: List<Track>,
    val unavailableTracks: List<Track>,
)

/**
 * Persistence and domain helpers for local cross-server & `.m3u` imported playlists (`local_pl:*`).
 */
@Singleton
class LocalPlaylistRepository @Inject constructor(
    private val dao: LocalPlaylistDao,
    private val profileStore: ProfileStore,
    json: Json,
) {
    internal constructor(
        dao: LocalPlaylistDao,
        profileStore: ProfileStore,
    ) : this(dao = dao, profileStore = profileStore, json = DEFAULT_JSON)

    private val safeJson = Json(json) {
        ignoreUnknownKeys = true
    }

    suspend fun allPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val headers = dao.allPlaylists()
        if (headers.isEmpty()) return@withContext emptyList()
        val tracksByPlaylist = dao.allTracks().groupBy { it.playlistId }
        headers.map { header ->
            val tracks = decodeTracks(tracksByPlaylist[header.playlistId].orEmpty())
            toDomainPlaylist(header, tracks)
        }
    }

    suspend fun playlist(playlistId: String): Pair<Playlist, List<Track>>? = withContext(Dispatchers.IO) {
        val header = dao.playlistById(playlistId) ?: return@withContext null
        val tracks = decodeTracks(dao.tracksForPlaylist(playlistId))
        toDomainPlaylist(header, tracks) to tracks
    }

    suspend fun playlistEntries(playlistId: String): List<LocalPlaylistEntry> = withContext(Dispatchers.IO) {
        val rows = dao.tracksForPlaylist(playlistId)
        val activeIds = activeProfileIdSet()
        rows.mapNotNull { row ->
            val track = decodeTrack(row.trackJson) ?: return@mapNotNull null
            LocalPlaylistEntry(
                entryId = row.entryId,
                position = row.position,
                track = track,
                isSourceAvailable = isTrackSourceAvailable(track, activeIds),
            )
        }
    }

    suspend fun createPlaylist(
        name: String,
        tracks: List<Track> = emptyList(),
        sourceM3uName: String? = null,
    ): Playlist = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val playlistId = "$LOCAL_PLAYLIST_ID_PREFIX${UUID.randomUUID()}"
        val cleanName = name.trim().ifBlank { DEFAULT_PLAYLIST_NAME }
        val header = LocalPlaylistEntity(
            playlistId = playlistId,
            name = cleanName,
            sourceM3uName = sourceM3uName?.takeIf { it.isNotBlank() },
            createdAtMs = now,
            updatedAtMs = now,
        )
        val trackRows = tracks.mapIndexed { index, track ->
            LocalPlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                position = index,
                trackJson = safeJson.encodeToString(Track.serializer(), track),
                addedAtMs = now,
            )
        }
        dao.insertPlaylistWithTracks(header, trackRows)
        toDomainPlaylist(header, tracks)
    }

    suspend fun addTracks(playlistId: String, tracks: List<Track>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        dao.playlistById(playlistId) ?: return@withContext
        val now = System.currentTimeMillis()
        val startPos = dao.maxPosition(playlistId) + 1
        val rows = tracks.mapIndexed { index, track ->
            LocalPlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                position = startPos + index,
                trackJson = safeJson.encodeToString(Track.serializer(), track),
                addedAtMs = now,
            )
        }
        dao.insertTracks(rows)
        dao.touchPlaylist(playlistId, now)
    }

    suspend fun removeTracks(
        playlistId: String,
        positions: List<Int> = emptyList(),
        entryIds: List<Long> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        if (positions.isEmpty() && entryIds.isEmpty()) return@withContext
        dao.playlistById(playlistId) ?: return@withContext

        if (entryIds.isNotEmpty()) {
            val playlistEntryIds = dao.tracksForPlaylist(playlistId).mapTo(HashSet()) { it.entryId }
            val ownedEntryIds = entryIds.filter { it in playlistEntryIds }
            if (ownedEntryIds.isNotEmpty()) {
                dao.deleteEntries(ownedEntryIds)
            }
        }
        if (positions.isNotEmpty()) {
            dao.deleteByPositions(playlistId, positions)
        }

        val remaining = dao.tracksForPlaylist(playlistId)
        remaining.forEachIndexed { index, entity ->
            if (entity.position != index) {
                dao.updatePosition(entity.entryId, index)
            }
        }
        dao.touchPlaylist(playlistId, System.currentTimeMillis())
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext
        dao.renamePlaylist(playlistId, cleanName, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun search(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext allPlaylists()
        val headers = dao.searchPlaylists(trimmed)
        if (headers.isEmpty()) return@withContext emptyList()
        val tracksByPlaylist = dao.allTracks().groupBy { it.playlistId }
        headers.map { header ->
            val tracks = decodeTracks(tracksByPlaylist[header.playlistId].orEmpty())
            toDomainPlaylist(header, tracks)
        }
    }

    // ---- Source profile membership ("View my" filtering) ----

    suspend fun playlistSourceProfileIds(playlistId: String): Set<String> = withContext(Dispatchers.IO) {
        dao.tracksForPlaylist(playlistId)
            .mapNotNull { GlobalId.profileIdOrNull(it.trackId) }
            .toSet()
    }

    suspend fun allPlaylistSourceMap(): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, MutableSet<String>>()
        for (header in dao.allPlaylists()) {
            result[header.playlistId] = linkedSetOf()
        }
        for (row in dao.allTracks()) {
            val pid = GlobalId.profileIdOrNull(row.trackId) ?: continue
            result.getOrPut(row.playlistId) { linkedSetOf() }.add(pid)
        }
        result
    }

    // ---- Collage cover art helpers ----

    fun extractCoverArtIds(tracks: List<Track>): List<String> =
        tracks.asSequence()
            .mapNotNull { it.coverArt?.trim()?.takeIf { art -> art.isNotEmpty() } }
            .distinct()
            .take(MAX_COLLAGE_COVERS)
            .toList()

    fun encodeCoverArtMarker(coverArtIds: List<String>): String? {
        val distinct = coverArtIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_COLLAGE_COVERS)
            .toList()
        return when (distinct.size) {
            0 -> null
            1 -> distinct.first()
            else -> "$COLLAGE_MARKER_PREFIX${safeJson.encodeToString(distinct)}"
        }
    }

    fun decodeCoverArtMarker(coverArt: String?): List<String> {
        if (coverArt.isNullOrBlank()) return emptyList()
        if (!coverArt.startsWith(COLLAGE_MARKER_PREFIX)) {
            return listOf(coverArt)
        }
        val payload = coverArt.removePrefix(COLLAGE_MARKER_PREFIX)
        return runCatching {
            safeJson.decodeFromString<List<String>>(payload)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_COLLAGE_COVERS)
        }.getOrDefault(emptyList())
    }

    fun resolvePlaylistCover(playlist: Playlist): PlaylistCover {
        val ids = decodeCoverArtMarker(playlist.coverArt)
        return if (ids.isEmpty()) PlaylistCover.Placeholder else PlaylistCover.Collage(ids)
    }

    suspend fun playlistCover(playlistId: String): PlaylistCover = withContext(Dispatchers.IO) {
        val tracks = decodeTracks(dao.tracksForPlaylist(playlistId))
        val ids = extractCoverArtIds(tracks)
        if (ids.isEmpty()) PlaylistCover.Placeholder else PlaylistCover.Collage(ids)
    }

    suspend fun allPlaylistCovers(): Map<String, PlaylistCover> = withContext(Dispatchers.IO) {
        val tracksByPlaylist = dao.allTracks().groupBy { it.playlistId }
        dao.allPlaylists().associate { header ->
            val tracks = decodeTracks(tracksByPlaylist[header.playlistId].orEmpty())
            val ids = extractCoverArtIds(tracks)
            header.playlistId to if (ids.isEmpty()) PlaylistCover.Placeholder else PlaylistCover.Collage(ids)
        }
    }

    // ---- Missing / disabled profile handling ----

    fun isTrackSourceAvailable(track: Track): Boolean =
        isTrackSourceAvailable(track, activeProfileIdSet())

    fun isTrackIdSourceAvailable(trackId: String): Boolean {
        if (GlobalId.isDeviceLocal(trackId)) return true
        val profileId = GlobalId.profileIdOrNull(trackId) ?: return false
        return profileId in activeProfileIdSet()
    }

    private fun isTrackSourceAvailable(track: Track, activeProfileIds: Set<String>): Boolean {
        if (track.isLocal || GlobalId.isDeviceLocal(track.id)) return true
        val profileId = GlobalId.profileIdOrNull(track.id)
            ?: track.sourceProfileId?.takeIf { it.isNotBlank() }
            ?: return false
        return profileId in activeProfileIds
    }

    // ---- Cross-server download partitioning ----

    fun partitionDownloadableByProfile(
        tracks: List<Track>,
        requireAvailableProfile: Boolean = true,
    ): Map<String, List<Track>> {
        val activeIds = activeProfileIdSet()
        val grouped = linkedMapOf<String, MutableList<Track>>()
        for (track in tracks) {
            if (track.id.startsWith("local:") || track.localUri != null) continue
            val profileId = GlobalId.profileIdOrNull(track.id)
                ?: track.sourceProfileId?.takeIf { it.isNotBlank() }
                ?: continue
            if (requireAvailableProfile && profileId !in activeIds) continue
            grouped.getOrPut(profileId) { mutableListOf() }.add(track)
        }
        return grouped
    }

    fun partitionTracksForDownload(tracks: List<Track>): CrossServerDownloadPartition {
        val activeIds = activeProfileIdSet()
        val downloadable = linkedMapOf<String, MutableList<Track>>()
        val local = mutableListOf<Track>()
        val unavailable = mutableListOf<Track>()

        for (track in tracks) {
            if (track.id.startsWith("local:") || track.localUri != null) {
                local.add(track)
                continue
            }
            val profileId = GlobalId.profileIdOrNull(track.id)
                ?: track.sourceProfileId?.takeIf { it.isNotBlank() }
            if (profileId == null || profileId !in activeIds) {
                unavailable.add(track)
            } else {
                downloadable.getOrPut(profileId) { mutableListOf() }.add(track)
            }
        }
        return CrossServerDownloadPartition(
            downloadableByProfile = downloadable,
            localTracks = local,
            unavailableTracks = unavailable,
        )
    }

    suspend fun downloadableTracksByProfile(
        playlistId: String,
        requireAvailableProfile: Boolean = true,
    ): Map<String, List<Track>> = withContext(Dispatchers.IO) {
        val tracks = decodeTracks(dao.tracksForPlaylist(playlistId))
        partitionDownloadableByProfile(tracks, requireAvailableProfile = requireAvailableProfile)
    }

    private fun activeProfileIdSet(): Set<String> =
        profileStore.activeProfiles.value.mapTo(HashSet()) { it.id }

    private fun toDomainPlaylist(header: LocalPlaylistEntity, tracks: List<Track>): Playlist =
        Playlist(
            id = header.playlistId,
            name = header.name,
            songCount = tracks.size,
            durationSec = tracks.sumOf { it.durationSec.coerceAtLeast(0) },
            coverArt = encodeCoverArtMarker(extractCoverArtIds(tracks)),
        )

    private fun decodeTracks(entities: List<LocalPlaylistTrackEntity>): List<Track> =
        entities.mapNotNull { decodeTrack(it.trackJson) }

    private fun decodeTrack(trackJson: String): Track? =
        runCatching { safeJson.decodeFromString(Track.serializer(), trackJson) }.getOrNull()

    companion object {
        private const val DEFAULT_PLAYLIST_NAME = "Untitled playlist"
        private const val MAX_COLLAGE_COVERS = 4

        internal val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        fun isLocalPlaylist(playlistId: String): Boolean =
            playlistId.startsWith(LOCAL_PLAYLIST_ID_PREFIX)
    }
}
