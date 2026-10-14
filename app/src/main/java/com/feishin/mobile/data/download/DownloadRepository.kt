package com.feishin.mobile.data.download

import android.content.Context
import com.feishin.mobile.core.common.DownloadStateMachine
import com.feishin.mobile.core.common.QualityPolicy
import com.feishin.mobile.core.common.StreamQuality
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import com.feishin.mobile.data.db.DownloadDao
import com.feishin.mobile.data.db.DownloadEntity
import com.feishin.mobile.data.db.DownloadGroupDao
import com.feishin.mobile.data.db.DownloadGroupEntity
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Snapshot of overall download activity, for banners and the notifications. */
data class DownloadProgress(
    val total: Int,
    val queued: Int,
    val fraction: Float,
    val currentTitle: String?,
)

/**
 * Owns download state: the Room rows, the on-disk layout, group sync and the
 * lifecycle calls (enqueue/pause/resume/remove). The actual transfer lives in
 * [DownloadWorker]; this class only schedules it.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val groups: DownloadGroupDao,
    private val covers: CachedCoverDao,
    private val settings: SettingsStore,
    private val creds: CredentialsStore,
    private val repo: MusicDataRepository,
    private val scheduler: DownloadScheduler,
) {
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()
    val completed: Flow<List<DownloadEntity>> = dao.observeCompleted()

    /** songId -> row, for downloaded indicators across the UI. */
    val bySongId: Flow<Map<String, DownloadEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.songId } }

    val totalSizeBytes: Flow<Long> = dao.observeCompleted().map { list ->
        list.fold(0L) { acc, e -> acc + e.sizeBytes }
    }

    val progress: Flow<DownloadProgress?> = dao.observeAll().map { list ->
        val active = list.filter {
            it.statusEnum == DownloadStatus.DOWNLOADING || it.statusEnum == DownloadStatus.QUEUED
        }
        if (active.isEmpty()) return@map null
        DownloadProgress(
            total = active.size,
            queued = active.count { it.statusEnum == DownloadStatus.QUEUED },
            fraction = active.map { it.progress }.average().toFloat(),
            currentTitle = active.firstOrNull { it.statusEnum == DownloadStatus.DOWNLOADING }?.title,
        )
    }

    fun observeSong(songId: String): Flow<DownloadEntity?> = dao.observeById(songId)

    fun observeGroup(groupId: String, type: String, name: String): Flow<DownloadGroupEntity> =
        groups.observeAll().map { all ->
            all.firstOrNull { it.groupId == groupId } ?: DownloadGroupEntity(groupId, type, name, false)
        }

    // ---------- lookup ----------

    /** The finished local file for a song, when it exists on disk. */
    suspend fun localFile(songId: String): File? {
        val row = dao.byId(songId) ?: return null
        if (row.statusEnum != DownloadStatus.COMPLETED) return null
        val f = File(row.filePath ?: return null)
        return if (f.exists() && f.length() > 0) f else null
    }

    suspend fun completedIds(): Set<String> = dao.completedIds().toSet()

    suspend fun completedTracks(): List<DownloadEntity> = dao.completed()

    /** Actively transferring rows (for the Storage screen counters). */
    suspend fun activeDownloads(): List<DownloadEntity> = dao.pending()

    suspend fun activeCount(): Int = activeDownloads().count {
        it.statusEnum == com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING
    }

    suspend fun queuedCount(): Int = activeDownloads().count {
        it.statusEnum == com.feishin.mobile.data.db.DownloadStatus.QUEUED
    }

    suspend fun totalBytes(): Long = dao.completed().fold(0L) { acc, e -> acc + e.sizeBytes }

    // ---------- enqueue ----------

    suspend fun enqueueTracks(tracks: List<Track>, groupId: String? = null) {
        if (tracks.isEmpty()) return
        val existing = tracks.mapNotNull { dao.byId(it.id) }.associateBy { it.songId }
        val rows = tracks.mapNotNull { t ->
            val prior = existing[t.id]
            val finishedOnDisk = prior?.statusEnum == DownloadStatus.COMPLETED &&
                File(prior.filePath ?: "").exists()
            if (finishedOnDisk) return@mapNotNull null
            val partial = if (prior != null && DownloadStateMachine.isRunnable(prior.statusEnum)) {
                prior.bytesDownloaded
            } else {
                0L
            }
            DownloadEntity(
                songId = t.id,
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumId = t.albumId,
                artistId = t.artistId,
                coverArt = t.coverArt,
                durationSec = t.durationSec,
                trackNo = t.trackNo,
                year = t.year,
                genre = t.genre,
                suffix = t.suffix,
                path = t.path,
                replayGainTrack = t.replayGainTrack,
                replayGainAlbum = t.replayGainAlbum,
                replayGainPeak = t.replayGainPeak,
                tempPath = prior?.tempPath,
                bytesDownloaded = partial,
                expectedBytes = t.sizeBytes,
                status = DownloadStatus.QUEUED.name,
                progress = DownloadStateMachine.progress(partial, t.sizeBytes),
                error = null,
                groupId = groupId ?: prior?.groupId,
                enqueuedAt = prior?.enqueuedAt ?: System.currentTimeMillis(),
            )
        }
        if (rows.isNotEmpty()) {
            dao.upsertAll(rows)
            scheduler.enqueue()
        }
    }

    suspend fun enqueueAlbum(album: AlbumDetail) {
        val groupId = "album:${album.id}"
        groups.upsert(DownloadGroupEntity(groupId, "album", album.name, keepDownloaded = true))
        enqueueTracks(album.tracks, groupId)
    }

    suspend fun enqueuePlaylist(playlist: PlaylistDetail) {
        val groupId = "playlist:${playlist.id}"
        groups.upsert(DownloadGroupEntity(groupId, "playlist", playlist.name, keepDownloaded = true))
        enqueueTracks(playlist.tracks, groupId)
    }

    suspend fun enqueueFavorites(tracks: List<Track>) {
        groups.upsert(DownloadGroupEntity("favorites", "favorites", "Favorite songs", keepDownloaded = true))
        enqueueTracks(tracks, "favorites")
    }

    // ---------- keep-downloaded sync ----------

    /**
     * Re-syncs every "keep downloaded" collection: new tracks are queued and
     * tracks that left the collection are removed from disk. [resolver] returns
     * the current track list for a group id (or null when it cannot be loaded).
     */
    suspend fun syncKeepGroups(resolver: suspend (DownloadGroupEntity) -> List<Track>?) {
        for (group in groups.observeAllSnapshot()) {
            if (!group.keepDownloaded) continue
            val desired = resolver(group) ?: continue
            if (desired.isEmpty()) continue
            enqueueTracks(desired, group.groupId)
            val desiredIds = desired.map { it.id }.toSet()
            for (row in dao.byGroup(group.groupId)) {
                if (row.songId !in desiredIds) remove(row.songId)
            }
        }
    }

    suspend fun isKeepDownloaded(groupId: String): Boolean =
        groups.byId(groupId)?.keepDownloaded == true

    suspend fun setKeepDownloaded(groupId: String, type: String, name: String, keep: Boolean) {
        val current = groups.byId(groupId)
        groups.upsert(
            if (current != null) current.copy(keepDownloaded = keep)
            else DownloadGroupEntity(groupId, type, name, keepDownloaded = keep),
        )
    }

    // ---------- lifecycle ----------

    suspend fun pause(songId: String) {
        dao.byId(songId)?.let { row ->
            if (DownloadStateMachine.canTransition(row.statusEnum, DownloadStatus.PAUSED)) {
                dao.setStatus(songId, DownloadStatus.PAUSED.name)
            }
        }
        scheduler.cancelCurrent()
    }

    suspend fun resume(songId: String) {
        dao.byId(songId)?.let { row ->
            if (row.statusEnum == DownloadStatus.PAUSED || row.statusEnum == DownloadStatus.FAILED) {
                dao.setStatus(songId, DownloadStatus.QUEUED.name)
                scheduler.enqueue()
            }
        }
    }

    /** Removes the row and its files (finished, partial, cached cover). */
    suspend fun remove(songId: String) {
        dao.byId(songId)?.let { row ->
            row.filePath?.let { runCatching { File(it).delete() } }
            row.tempPath?.let { runCatching { File(it).delete() } }
            row.coverArt?.let { id -> runCatching { File(covers.path(id) ?: return@runCatching).delete() } }
        }
        dao.delete(songId)
    }

    /**
     * User-initiated removal: also stops the group from syncing it back, so a
     * deliberate delete is not silently undone on the next refresh.
     */
    suspend fun removeFromLibrary(songId: String) {
        dao.byId(songId)?.groupId?.let { groupId ->
            groups.byId(groupId)?.let { groups.upsert(it.copy(keepDownloaded = false)) }
        }
        remove(songId)
    }

    suspend fun removeAll() {
        dao.observeAllSnapshot().forEach { row ->
            row.filePath?.let { runCatching { File(it).delete() } }
            row.tempPath?.let { runCatching { File(it).delete() } }
        }
        dao.clear()
        groups.clear()
        covers.clear()
        runCatching { coversDir().deleteRecursively(); coversDir().mkdirs() }
        scheduler.cancelCurrent()
    }

    suspend fun clearCompleted() {
        dao.completed().forEach { row ->
            row.filePath?.let { runCatching { File(it).delete() } }
            dao.delete(row.songId)
        }
    }

    // ---------- storage layout ----------

    fun downloadsDir(): File = File(context.filesDir, "downloads/${serverFolder()}").apply { mkdirs() }

    fun coversDir(): File = File(context.filesDir, "download_covers").apply { mkdirs() }

    fun partialDir(): File = File(context.filesDir, "download_partials").apply { mkdirs() }

    private fun serverFolder(): String {
        val host = runCatching {
            java.net.URI(creds.state.value?.serverUrl ?: "").host
        }.getOrNull()
        val raw = host?.takeIf { it.isNotBlank() } ?: (creds.state.value?.serverUrl ?: "default")
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /** Free space on the files partition, in bytes. */
    fun freeBytes(): Long = runCatching {
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE)

    // ---------- cover art ----------

    suspend fun cachedCoverPath(coverArtId: String?): String? {
        if (coverArtId.isNullOrBlank()) return null
        val path = covers.path(coverArtId) ?: return null
        return if (File(path).exists()) path else null
    }

    suspend fun storeCover(coverArtId: String, file: File) {
        covers.upsert(CachedCoverEntity(coverArtId, file.absolutePath, file.length()))
    }

    suspend fun coverCacheBytes(): Long = covers.allSizes().fold(0L) { acc, v -> acc + v }

    /** Finished-download bytes on disk. */
    suspend fun downloadedBytes(): Long = dao.completed().fold(0L) { acc, e -> acc + e.sizeBytes }

    /**
     * True when storing [extraBytes] more would exceed the user's download
     * storage cap (0 = unlimited).
     */
    suspend fun overDownloadCap(extraBytes: Long): Boolean {
        val capMb = settings.settingsNow().maxDownloadMb
        if (capMb <= 0 || extraBytes <= 0) return false
        return downloadedBytes() + extraBytes > capMb.toLong() * 1024 * 1024
    }

    suspend fun clearCoverCache() {
        covers.clear()
        runCatching { coversDir().deleteRecursively(); coversDir().mkdirs() }
    }

    // ---------- policy helpers ----------

    /** Quality the downloader should request, from the user's download settings. */
    suspend fun downloadQuality(): StreamQuality {
        val s = settings.settingsNow()
        return QualityPolicy.forDownload(s.downloadBitrate, s.downloadFormat, s.downloadOriginal)
    }

    suspend fun wifiOnly(): Boolean = settings.settingsNow().downloadWifiOnly

    // ---------- signed URLs for the transfer ----------

    /**
     * The URL the worker should fetch for a row: the original `download` endpoint
     * when the quality is Original, otherwise the transcoded `stream` endpoint.
     */
    suspend fun signedUrlFor(row: DownloadEntity, quality: StreamQuality): String? {
        val track = Track(
            id = row.songId, title = row.title, album = row.album, albumId = row.albumId,
            artist = row.artist, artistId = row.artistId, coverArt = row.coverArt,
            durationSec = row.durationSec, trackNo = row.trackNo, year = row.year,
            genre = row.genre, suffix = row.suffix, starred = false, path = row.path,
        )
        return runCatching {
            if (quality.isOriginal) repo.downloadUrl(track)
            else repo.streamUrl(track, quality.bitrateKbps, quality.format)
        }.getOrNull()
    }

    /** Cover art URL for the downloader (large enough for lock-screen art). */
    suspend fun signedCoverUrl(coverArtId: String): String? =
        runCatching { repo.coverArtUrl(coverArtId, 600) }.getOrNull()
}

/** Snapshot helpers that avoid exposing DAO types to the UI layer. */
private suspend fun DownloadDao.observeAllSnapshot(): List<DownloadEntity> =
    observeAll().first()

private suspend fun DownloadGroupDao.observeAllSnapshot(): List<DownloadGroupEntity> =
    observeAll().first()
