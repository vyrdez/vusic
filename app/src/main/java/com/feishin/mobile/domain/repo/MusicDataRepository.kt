package com.feishin.mobile.domain.repo

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.core.network.SyncedLyrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EMPTY_FEDERATED_HEALTH_STATE: StateFlow<FederatedHealthState> =
    MutableStateFlow(FederatedHealthState())

/** Library scan state for the "Scan library" row. */
data class ScanStatus(
    val scanning: Boolean = false,
    val count: Int = 0,
    val folderCount: Int? = null,
    val lastScan: String? = null,
    val totalCount: Int? = null,
    val quotaLimited: Boolean = false,
    val twoStage: Boolean = false,
)

/** UI-facing music data contract. Implementations map wire DTOs to domain models. */
interface MusicDataRepository {
    val healthState: StateFlow<FederatedHealthState> get() = EMPTY_FEDERATED_HEALTH_STATE

    fun capabilitiesFor(id: String): BackendCapabilities =
        if (GlobalId.isDeviceLocal(id)) BackendCapabilities.DEVICE_LOCAL
        else BackendCapabilities.forType(BackendType.NAVIDROME)

    suspend fun ping(): Result<Unit>
    suspend fun ping(profileId: String): Result<Unit> = ping()
    suspend fun albumList(type: String, size: Int = 50, offset: Int = 0, genre: String? = null): Result<List<Album>>
    suspend fun artists(): Result<List<Artist>>
    suspend fun genres(): Result<List<Genre>>
    suspend fun playlists(): Result<List<Playlist>>
    suspend fun search(query: String): Result<SearchResults>
    /** Search with explicit per-type counts (paging single-type views). */
    suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults>
    suspend fun serverInfo(): Result<ServerInfo>
    suspend fun serverInfo(profileId: String): Result<ServerInfo> = serverInfo()
    suspend fun startScan(): Result<Unit>
    suspend fun startScan(profileId: String): Result<Unit> = startScan()
    suspend fun scanStatus(): Result<ScanStatus>
    suspend fun scanStatus(profileId: String): Result<ScanStatus> = scanStatus()
    suspend fun album(id: String): Result<AlbumDetail>
    suspend fun artist(id: String): Result<ArtistDetail>
    suspend fun artistInfo(id: String): Result<ArtistInfo>
    suspend fun randomSongs(size: Int = 20, genre: String? = null): Result<List<Track>>
    suspend fun topSongs(artist: String, count: Int = 10): Result<List<Track>>
    suspend fun topSongs(artist: String, count: Int = 10, preferredProfileId: String?): Result<List<Track>> =
        topSongs(artist, count)
    suspend fun similarSongs(songId: String?, artistId: String?, count: Int = 25): Result<List<Track>>
    suspend fun playlist(id: String): Result<PlaylistDetail>
    suspend fun starred(): Result<StarredCollection>
    suspend fun starTrack(id: String, starred: Boolean): Result<Unit>
    suspend fun starAlbum(id: String, starred: Boolean): Result<Unit>
    suspend fun starArtist(id: String, starred: Boolean): Result<Unit>
    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList()): Result<String>
    suspend fun createPlaylist(name: String, trackIds: List<String>, profileId: String?): Result<String> =
        createPlaylist(name, trackIds)
    suspend fun createPlaylistWithTracks(
        name: String,
        tracks: List<Track>,
        profileId: String? = null,
    ): Result<Playlist> = when (val res = createPlaylist(name, tracks.map { it.id }, profileId)) {
        is Result.Ok -> Result.Ok(
            Playlist(
                id = res.value,
                name = name,
                songCount = tracks.size,
                durationSec = tracks.sumOf { it.durationSec.coerceAtLeast(0) },
                coverArt = null,
            ),
        )
        is Result.Err -> res
    }
    suspend fun cachedPlaylist(id: String): PlaylistDetail? = null
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit>
    suspend fun addToPlaylistOptimistic(
        playlistId: String,
        tracks: List<Track>,
        playlistName: String = "",
        onRollback: (() -> Unit)? = null,
    ): Result<Unit> = addToPlaylist(playlistId, tracks.map { it.id })
    suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit>
    suspend fun removeFromPlaylist(
        playlistId: String,
        indexes: List<Int>,
        entryIds: List<String>,
    ): Result<Unit> = removeFromPlaylist(playlistId, indexes)
    suspend fun deletePlaylist(id: String): Result<Unit>
    suspend fun scrobble(id: String, submission: Boolean): Result<Unit>
    /** Scrobble with an explicit timestamp (replaying an offline play). */
    suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit>
    suspend fun reportPlaybackStart(
        id: String,
        positionMs: Long,
        playSessionId: String,
        isDirect: Boolean = true,
    ): Result<Unit> = Result.Ok(Unit)
    suspend fun reportPlaybackProgress(
        id: String,
        positionMs: Long,
        isPaused: Boolean,
        playSessionId: String,
        isDirect: Boolean = true,
    ): Result<Unit> = Result.Ok(Unit)
    suspend fun reportPlaybackStopped(
        id: String,
        positionMs: Long,
        playSessionId: String,
    ): Result<Unit> = Result.Ok(Unit)
    fun isSessionScrobbleBackend(id: String): Boolean = false
    /** OpenSubsonic synced lyrics for a track; falls back to plain text inside. */
    suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics>
    suspend fun lyrics(artist: String, title: String): Result<String>
    fun streamUrl(track: Track, maxBitrate: Int = 0, format: String = "raw"): String
    fun coverArtUrl(coverArtId: String?, size: Int = 512): String?
    fun downloadUrl(track: Track): String
}
