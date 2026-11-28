package com.feishin.mobile.playback

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.OfflineRadio
import com.feishin.mobile.core.common.RadioProgrammer
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.data.db.DriveTrackEntity
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import javax.inject.Inject

/**
 * Builds one radio batch (up to [RadioProgrammer.BATCH_SIZE] songs) for a seed
 * track, falling through silently: similar songs for the seed artist, then
 * same-genre randoms, then plain randoms. For backends without server radio
 * mixes (such as Google Drive) or offline mode, delegates to [OfflineRadio]
 * over downloaded songs + indexed Drive tracks.
 *
 * Returns [Result.Err] only when every source failed (transient network/rate
 * limit); an empty [Result.Ok] means the library truly yielded nothing, so the
 * caller can tell "retry later" apart from "library empty".
 */
class RadioFetcher private constructor(
    private val repo: MusicDataRepository,
    private val driveTrackDao: DriveTrackDao?,
    private val profileStore: ProfileStore?,
    private val downloads: DownloadRepository?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    @Inject
    constructor(
        repo: MusicDataRepository,
        driveTrackDao: DriveTrackDao,
        profileStore: ProfileStore,
        downloads: DownloadRepository,
    ) : this(
        repo = repo,
        driveTrackDao = driveTrackDao,
        profileStore = profileStore,
        downloads = downloads,
        marker = Unit,
    )

    constructor(repo: MusicDataRepository) : this(repo, null, null, null, Unit)

    internal companion object {
        fun forTest(
            repo: MusicDataRepository,
            driveTrackDao: DriveTrackDao? = null,
            profileStore: ProfileStore? = null,
            downloads: DownloadRepository? = null,
        ): RadioFetcher = RadioFetcher(
            repo = repo,
            driveTrackDao = driveTrackDao,
            profileStore = profileStore,
            downloads = downloads,
            marker = Unit,
        )
    }

    suspend fun fetchLocalBatch(
        seed: Track,
        excludeIds: Set<String>,
        want: Int = RadioProgrammer.BATCH_SIZE,
        includeDriveTracks: Boolean = true,
        downloadedOverride: List<Track>? = null,
    ): List<Track> {
        val downloaded = downloadedOverride ?: downloads?.completedTracks()?.map { it.toDomainTrack() }.orEmpty()
        if (!includeDriveTracks || driveTrackDao == null) {
            return OfflineRadio.pick(downloaded, seed.genre, excludeIds, want)
        }
        val driveTracks = loadDriveTracksForRadio(seed)
        return OfflineRadio.pick(downloaded, driveTracks, seed.genre, excludeIds, want)
    }

    private suspend fun loadDriveTracksForRadio(seed: Track): List<Track> {
        val dao = driveTrackDao ?: return emptyList()
        val store = profileStore
        if (store != null) {
            runCatching { store.preloadIfNeeded() }
            val driveProfiles = store.activeProfiles.value.filter { it.type == BackendType.GOOGLE_DRIVE }
            if (driveProfiles.isNotEmpty()) {
                return driveProfiles.flatMap { profile ->
                    dao.tracksForProfile(profile.id).map { it.toDomainTrack(sourceLabel = profile.name) }
                }
            }
        }
        val seedProfileId = GlobalId.profileIdOrNull(seed.id) ?: return emptyList()
        if (repo.capabilitiesFor(seed.id).supportsRadioMix) return emptyList()
        return dao.tracksForProfile(seedProfileId).map { it.toDomainTrack(sourceLabel = seed.sourceLabel) }
    }

    suspend fun fetchBatch(
        seed: Track,
        excludeIds: Set<String>,
        want: Int = RadioProgrammer.BATCH_SIZE,
    ): Result<List<Track>> {
        if (!repo.capabilitiesFor(seed.id).supportsRadioMix) {
            return Result.Ok(fetchLocalBatch(seed, excludeIds, want, includeDriveTracks = true))
        }
        val acc = ArrayList<Track>(want)
        var anyOk = false

        fun take(r: Result<List<Track>>) {
            if (acc.size >= want) return
            val list = (r as? Result.Ok)?.value
            if (list == null) return
            anyOk = true
            val accIds = acc.map { it.id }.toHashSet()
            for (t in list) {
                if (acc.size >= want) break
                if (t.id !in excludeIds && t.id !in accIds) {
                    acc.add(t)
                    accIds.add(t.id)
                }
            }
        }

        // 1. Similar songs for the seed artist (or the seed song itself).
        val artistId = seed.artistId?.takeIf { it.isNotBlank() }
        if (artistId != null || seed.artist.isNotBlank() || seed.id.isNotBlank()) {
            val sim = repo.similarSongs(
                songId = seed.id.takeIf { artistId == null },
                artistId = artistId,
                count = 50,
            )
            take((sim as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: sim)
        }
        // 2. Random songs of the same genre.
        if (acc.size < want && !seed.genre.isNullOrBlank()) {
            val g = repo.randomSongs(40, seed.genre)
            take((g as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: g)
        }
        // 3. Plain random songs.
        if (acc.size < want) {
            val r = repo.randomSongs(40, null)
            take((r as? Result.Ok)?.let { Result.Ok(it.value.shuffled()) } ?: r)
        }
        if (!anyOk) return Result.Err("Radio unavailable")
        return Result.Ok(RadioProgrammer.buildBatch(acc, emptySet(), want))
    }
}

/** A stored download as a playable domain track (offline radio + Downloads tab). */
fun com.feishin.mobile.data.db.DownloadEntity.toDomainTrack(): Track = Track(
    id = songId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    durationSec = durationSec,
    trackNo = trackNo,
    year = year,
    genre = genre,
    suffix = suffix,
    starred = false,
    path = path,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    replayGainPeak = replayGainPeak,
)

/** An indexed Google Drive track mapped to a playable domain track for radio mixes. */
fun DriveTrackEntity.toDomainTrack(
    starred: Boolean = false,
    sourceLabel: String? = null,
): Track = Track(
    id = songId,
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
    starred = starred,
    sizeBytes = sizeBytes,
    bitRate = bitRate?.takeIf { it > 0 }?.let { if (it > 10_000) it / 1000 else it },
    path = parentFolderPath,
    sourceProfileId = profileId,
    sourceLabel = sourceLabel,
)
