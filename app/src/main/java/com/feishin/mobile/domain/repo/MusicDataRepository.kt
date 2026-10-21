package com.feishin.mobile.domain.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ScanStatus
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.core.network.SyncedLyrics

/** UI-facing music data contract. Implementations map wire DTOs to domain models. */
interface MusicDataRepository {
    suspend fun ping(): Result<Unit>
    suspend fun albumList(type: String, size: Int = 50, offset: Int = 0, genre: String? = null): Result<List<Album>>
    suspend fun artists(): Result<List<Artist>>
    suspend fun genres(): Result<List<Genre>>
    suspend fun playlists(): Result<List<Playlist>>
    suspend fun search(query: String): Result<SearchResults>
    /** Search with explicit per-type counts (paging single-type views). */
    suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults>
    suspend fun serverInfo(): Result<ServerInfo>
    suspend fun startScan(): Result<Unit>
    suspend fun scanStatus(): Result<ScanStatus>
    suspend fun album(id: String): Result<AlbumDetail>
    suspend fun artist(id: String): Result<ArtistDetail>
    suspend fun artistInfo(id: String): Result<ArtistInfo>
    suspend fun randomSongs(size: Int = 20, genre: String? = null): Result<List<Track>>
    suspend fun topSongs(artist: String, count: Int = 10): Result<List<Track>>
    suspend fun similarSongs(songId: String?, artistId: String?, count: Int = 25): Result<List<Track>>
    suspend fun playlist(id: String): Result<PlaylistDetail>
    suspend fun starred(): Result<StarredCollection>
    suspend fun starTrack(id: String, starred: Boolean): Result<Unit>
    suspend fun starAlbum(id: String, starred: Boolean): Result<Unit>
    suspend fun starArtist(id: String, starred: Boolean): Result<Unit>
    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList()): Result<String>
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit>
    suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit>
    suspend fun deletePlaylist(id: String): Result<Unit>
    suspend fun scrobble(id: String, submission: Boolean): Result<Unit>
    /** Scrobble with an explicit timestamp (replaying an offline play). */
    suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit>
    /** OpenSubsonic synced lyrics for a track; falls back to plain text inside. */
    suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics>
    suspend fun lyrics(artist: String, title: String): Result<String>
    fun streamUrl(track: Track, maxBitrate: Int = 0, format: String = "raw"): String
    fun coverArtUrl(coverArtId: String?, size: Int = 512): String?
    fun downloadUrl(track: Track): String
}
