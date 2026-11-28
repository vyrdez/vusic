package com.feishin.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Indexed audio track stored for a Google Drive profile.
 * Populated incrementally by `DriveLibraryIndexer` and queried offline-first.
 */
@Entity(
    tableName = "drive_tracks",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["profileId", "albumKey"]),
        Index(value = ["profileId", "artistKey"]),
        Index(value = ["profileId", "parentFolderId"]),
    ],
)
data class DriveTrackEntity(
    @PrimaryKey val songId: String,
    val profileId: String,
    val driveFileId: String,
    val fileName: String,
    val parentFolderId: String? = null,
    @ColumnInfo(defaultValue = "") val parentFolderPath: String = "",
    val title: String,
    val artist: String,
    @ColumnInfo(defaultValue = "") val albumArtist: String = "",
    val album: String,
    val albumKey: String,
    val artistKey: String,
    val trackNo: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    @ColumnInfo(defaultValue = "0") val durationSec: Int = 0,
    val bitRate: Int? = null,
    val suffix: String? = null,
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0L,
    @ColumnInfo(defaultValue = "") val mimeType: String = "",
    val coverArtId: String? = null,
    val embeddedLyricsJson: String? = null,
    @ColumnInfo(defaultValue = "0") val modifiedTimeMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val indexedAtMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val tagsParsed: Boolean = false,
)

/** Incremental sync metadata projection for [DriveTrackDao.allIdsAndModTimes]. */
data class DriveFileModTime(
    val songId: String,
    val driveFileId: String,
    val modifiedTimeMs: Long,
    val tagsParsed: Boolean,
)

/** Aggregated album projection derived from `drive_tracks` (and optionally `local_play_history`). */
data class DriveAlbumRow(
    val albumKey: String,
    val profileId: String,
    val album: String,
    val artist: String,
    val artistKey: String,
    val coverArtId: String?,
    val songCount: Int,
    val durationSec: Int,
    val year: Int?,
    val genre: String?,
    val playCount: Long = 0L,
)

/** Aggregated artist projection derived from `drive_tracks`. */
data class DriveArtistRow(
    val artistKey: String,
    val profileId: String,
    val name: String,
    val coverArtId: String?,
    val albumCount: Int,
)

/** Aggregated genre projection derived from `drive_tracks`. */
data class DriveGenreRow(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Dao
interface DriveTrackDao {
    @Query("SELECT songId, driveFileId, modifiedTimeMs, tagsParsed FROM drive_tracks WHERE profileId = :profileId")
    suspend fun allIdsAndModTimes(profileId: String): List<DriveFileModTime>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<DriveTrackEntity>)

    @Query("DELETE FROM drive_tracks WHERE songId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<String>)

    @Query("DELETE FROM drive_tracks WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)

    @Query("SELECT COUNT(*) FROM drive_tracks WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Query(
        "SELECT * FROM drive_tracks WHERE profileId = :profileId " +
            "ORDER BY album COLLATE NOCASE ASC, COALESCE(trackNo, 999999) ASC, title COLLATE NOCASE ASC",
    )
    suspend fun tracksForProfile(profileId: String): List<DriveTrackEntity>

    @Query("SELECT * FROM drive_tracks WHERE songId = :songId LIMIT 1")
    suspend fun trackById(songId: String): DriveTrackEntity?

    @Query("SELECT embeddedLyricsJson FROM drive_tracks WHERE songId = :songId LIMIT 1")
    suspend fun embeddedLyrics(songId: String): String?

    @Query(
        "SELECT albumKey, profileId, MAX(album) AS album, " +
            "MAX(CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END) AS artist, " +
            "MAX(artistKey) AS artistKey, MIN(coverArtId) AS coverArtId, " +
            "COUNT(*) AS songCount, COALESCE(SUM(durationSec), 0) AS durationSec, " +
            "MAX(year) AS year, MAX(genre) AS genre, 0 AS playCount " +
            "FROM drive_tracks WHERE profileId = :profileId " +
            "GROUP BY albumKey ORDER BY album COLLATE NOCASE ASC",
    )
    suspend fun albumsForProfile(profileId: String): List<DriveAlbumRow>

    @Query(
        "SELECT d.albumKey AS albumKey, d.profileId AS profileId, MAX(d.album) AS album, " +
            "MAX(CASE WHEN d.albumArtist != '' THEN d.albumArtist ELSE d.artist END) AS artist, " +
            "MAX(d.artistKey) AS artistKey, MIN(d.coverArtId) AS coverArtId, " +
            "COUNT(*) AS songCount, COALESCE(SUM(d.durationSec), 0) AS durationSec, " +
            "MAX(d.year) AS year, MAX(d.genre) AS genre, MAX(h.playCount) AS playCount " +
            "FROM drive_tracks d " +
            "INNER JOIN (" +
            "SELECT albumKey, COUNT(*) AS playCount, MAX(playedAtMs) AS lastPlayedAtMs " +
            "FROM local_play_history WHERE profileId = :profileId GROUP BY albumKey" +
            ") h ON d.albumKey = h.albumKey " +
            "WHERE d.profileId = :profileId " +
            "GROUP BY d.albumKey " +
            "ORDER BY MAX(h.lastPlayedAtMs) DESC " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun albumsRecentFromHistory(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<DriveAlbumRow>

    @Query(
        "SELECT d.albumKey AS albumKey, d.profileId AS profileId, MAX(d.album) AS album, " +
            "MAX(CASE WHEN d.albumArtist != '' THEN d.albumArtist ELSE d.artist END) AS artist, " +
            "MAX(d.artistKey) AS artistKey, MIN(d.coverArtId) AS coverArtId, " +
            "COUNT(*) AS songCount, COALESCE(SUM(d.durationSec), 0) AS durationSec, " +
            "MAX(d.year) AS year, MAX(d.genre) AS genre, MAX(h.playCount) AS playCount " +
            "FROM drive_tracks d " +
            "INNER JOIN (" +
            "SELECT albumKey, COUNT(*) AS playCount, MAX(playedAtMs) AS lastPlayedAtMs " +
            "FROM local_play_history WHERE profileId = :profileId GROUP BY albumKey" +
            ") h ON d.albumKey = h.albumKey " +
            "WHERE d.profileId = :profileId " +
            "GROUP BY d.albumKey " +
            "ORDER BY MAX(h.playCount) DESC, MAX(h.lastPlayedAtMs) DESC " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun albumsFrequentFromHistory(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<DriveAlbumRow>

    @Query(
        "SELECT artistKey, profileId, " +
            "MAX(CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END) AS name, " +
            "MIN(coverArtId) AS coverArtId, COUNT(DISTINCT albumKey) AS albumCount " +
            "FROM drive_tracks WHERE profileId = :profileId " +
            "GROUP BY artistKey ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun artistsForProfile(profileId: String): List<DriveArtistRow>

    @Query(
        "SELECT MIN(TRIM(genre)) AS name, COUNT(*) AS songCount, COUNT(DISTINCT albumKey) AS albumCount " +
            "FROM drive_tracks " +
            "WHERE profileId = :profileId AND genre IS NOT NULL AND TRIM(genre) != '' " +
            "GROUP BY LOWER(TRIM(genre)) ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun genresForProfile(profileId: String): List<DriveGenreRow>

    @Query(
        "SELECT * FROM drive_tracks WHERE albumKey = :albumKey " +
            "ORDER BY COALESCE(trackNo, 999999) ASC, title COLLATE NOCASE ASC",
    )
    suspend fun tracksInAlbum(albumKey: String): List<DriveTrackEntity>

    @Query(
        "SELECT * FROM drive_tracks WHERE artistKey = :artistKey " +
            "ORDER BY year DESC, album COLLATE NOCASE ASC, COALESCE(trackNo, 999999) ASC, title COLLATE NOCASE ASC",
    )
    suspend fun tracksByArtist(artistKey: String): List<DriveTrackEntity>

    @Query(
        "SELECT * FROM drive_tracks WHERE profileId = :profileId AND parentFolderId = :parentFolderId " +
            "ORDER BY COALESCE(trackNo, 999999) ASC, fileName COLLATE NOCASE ASC",
    )
    suspend fun tracksByParentFolder(profileId: String, parentFolderId: String): List<DriveTrackEntity>

    @Query(
        "SELECT * FROM drive_tracks WHERE profileId = :profileId " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun allSongsForProfile(profileId: String, limit: Int = 500, offset: Int = 0): List<DriveTrackEntity>

    @Query(
        "SELECT * FROM drive_tracks WHERE profileId = :profileId " +
            "AND (:genre IS NULL OR LOWER(genre) = LOWER(:genre)) " +
            "ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomTracks(profileId: String, limit: Int = 50, genre: String? = null): List<DriveTrackEntity>

    @Query(
        "SELECT * FROM drive_tracks WHERE profileId = :profileId " +
            "AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' " +
            "OR album LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%') " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun searchTracks(profileId: String, query: String, limit: Int = 50): List<DriveTrackEntity>

    @Query(
        "SELECT albumKey, profileId, MAX(album) AS album, " +
            "MAX(CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END) AS artist, " +
            "MAX(artistKey) AS artistKey, MIN(coverArtId) AS coverArtId, " +
            "COUNT(*) AS songCount, COALESCE(SUM(durationSec), 0) AS durationSec, " +
            "MAX(year) AS year, MAX(genre) AS genre, 0 AS playCount " +
            "FROM drive_tracks WHERE profileId = :profileId " +
            "AND (album LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR albumArtist LIKE '%' || :query || '%') " +
            "GROUP BY albumKey ORDER BY album COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun searchAlbums(profileId: String, query: String, limit: Int = 50): List<DriveAlbumRow>

    @Query(
        "SELECT artistKey, profileId, " +
            "MAX(CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END) AS name, " +
            "MIN(coverArtId) AS coverArtId, COUNT(DISTINCT albumKey) AS albumCount " +
            "FROM drive_tracks WHERE profileId = :profileId " +
            "AND (artist LIKE '%' || :query || '%' OR albumArtist LIKE '%' || :query || '%') " +
            "GROUP BY artistKey ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    suspend fun searchArtists(profileId: String, query: String, limit: Int = 50): List<DriveArtistRow>
}

/** Local cross-server & `.m3u` imported playlist header (`playlistId` = `"local_pl:<uuid>"`). */
@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val sourceM3uName: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)

/** Track entry inside a [LocalPlaylistEntity], storing a serialized `Track` snapshot for offline rendering. */
@Entity(
    tableName = "local_playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId"])],
)
data class LocalPlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0L,
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val trackJson: String,
    val addedAtMs: Long = System.currentTimeMillis(),
)

@Dao
interface LocalPlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY updatedAtMs DESC, name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists ORDER BY updatedAtMs DESC, name COLLATE NOCASE ASC")
    suspend fun allPlaylists(): List<LocalPlaylistEntity>

    @Query("SELECT * FROM local_playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun playlistById(playlistId: String): LocalPlaylistEntity?

    @Query("SELECT * FROM local_playlists WHERE name LIKE '%' || :query || '%' ORDER BY updatedAtMs DESC")
    suspend fun searchPlaylists(query: String): List<LocalPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: LocalPlaylistEntity)

    @Query("UPDATE local_playlists SET name = :name, updatedAtMs = :updatedAtMs WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
        updatedAtMs: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE local_playlists SET updatedAtMs = :updatedAtMs WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAtMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, entryId ASC")
    suspend fun tracksForPlaylist(playlistId: String): List<LocalPlaylistTrackEntity>

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, entryId ASC")
    fun observeTracksForPlaylist(playlistId: String): Flow<List<LocalPlaylistTrackEntity>>

    @Query("SELECT * FROM local_playlist_tracks ORDER BY playlistId ASC, position ASC, entryId ASC")
    suspend fun allTracks(): List<LocalPlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<LocalPlaylistTrackEntity>): List<Long>

    @Query("DELETE FROM local_playlist_tracks WHERE entryId IN (:entryIds)")
    suspend fun deleteEntries(entryIds: List<Long>)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId AND position IN (:positions)")
    suspend fun deleteByPositions(playlistId: String, positions: List<Int>)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("UPDATE local_playlist_tracks SET position = :position WHERE entryId = :entryId")
    suspend fun updatePosition(entryId: Long, position: Int)

    @Query("SELECT DISTINCT playlistId FROM local_playlist_tracks WHERE trackId LIKE :profileId || ':%'")
    suspend fun playlistIdsContainingProfile(profileId: String): List<String>

    @Transaction
    suspend fun insertPlaylistWithTracks(
        playlist: LocalPlaylistEntity,
        tracks: List<LocalPlaylistTrackEntity>,
    ) {
        upsertPlaylist(playlist)
        if (tracks.isNotEmpty()) insertTracks(tracks)
    }

    @Transaction
    suspend fun replaceTracks(
        playlistId: String,
        tracks: List<LocalPlaylistTrackEntity>,
        updatedAtMs: Long = System.currentTimeMillis(),
    ) {
        clearPlaylistTracks(playlistId)
        if (tracks.isNotEmpty()) insertTracks(tracks)
        touchPlaylist(playlistId, updatedAtMs)
    }
}

/** Local favorites for backends where `supportsServerFavorites == false` (e.g. Google Drive). */
@Entity(
    tableName = "local_favorites",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["profileId"]),
    ],
)
data class LocalFavoriteEntity(
    @PrimaryKey val targetId: String,
    val kind: String,
    val profileId: String,
    val payloadJson: String,
    val starredAtMs: Long = System.currentTimeMillis(),
)

@Dao
interface LocalFavoriteDao {
    @Query("SELECT * FROM local_favorites ORDER BY starredAtMs DESC")
    suspend fun all(): List<LocalFavoriteEntity>

    @Query("SELECT * FROM local_favorites WHERE kind = :kind ORDER BY starredAtMs DESC")
    suspend fun byKind(kind: String): List<LocalFavoriteEntity>

    @Query("SELECT * FROM local_favorites WHERE profileId = :profileId ORDER BY starredAtMs DESC")
    suspend fun byProfile(profileId: String): List<LocalFavoriteEntity>

    @Query("SELECT * FROM local_favorites WHERE profileId = :profileId AND kind = :kind ORDER BY starredAtMs DESC")
    suspend fun byProfileAndKind(profileId: String, kind: String): List<LocalFavoriteEntity>

    @Query("SELECT targetId FROM local_favorites WHERE kind = :kind")
    suspend fun idsByKind(kind: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM local_favorites WHERE targetId = :targetId)")
    suspend fun isStarred(targetId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: LocalFavoriteEntity)

    @Query("DELETE FROM local_favorites WHERE targetId = :targetId")
    suspend fun delete(targetId: String)

    @Query("DELETE FROM local_favorites WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)

    @Query("DELETE FROM local_favorites")
    suspend fun clear()
}

/** Local play history powering recent/frequent albums, Speed dial, and mixes on non-scrobbling backends. */
@Entity(
    tableName = "local_play_history",
    indices = [
        Index(value = ["profileId", "playedAtMs"]),
        Index(value = ["albumKey"]),
    ],
)
data class LocalPlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val profileId: String,
    val albumKey: String,
    val artistKey: String,
    val trackJson: String,
    val albumJson: String? = null,
    val playedAtMs: Long = System.currentTimeMillis(),
)

/** Aggregated album play count projection from `local_play_history`. */
data class LocalAlbumPlayStats(
    val albumKey: String,
    val profileId: String,
    val albumJson: String?,
    val playCount: Long,
    val lastPlayedAtMs: Long,
)

@Dao
interface LocalPlayHistoryDao {
    @Insert
    suspend fun insert(entry: LocalPlayHistoryEntity): Long

    @Query("SELECT * FROM local_play_history WHERE profileId = :profileId ORDER BY playedAtMs DESC LIMIT :limit")
    suspend fun recentForProfile(profileId: String, limit: Int = 50): List<LocalPlayHistoryEntity>

    @Query("SELECT * FROM local_play_history ORDER BY playedAtMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<LocalPlayHistoryEntity>

    @Query(
        "SELECT albumKey, profileId, MAX(albumJson) AS albumJson, " +
            "COUNT(*) AS playCount, MAX(playedAtMs) AS lastPlayedAtMs " +
            "FROM local_play_history WHERE profileId = :profileId " +
            "GROUP BY albumKey ORDER BY lastPlayedAtMs DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun recentAlbumsForProfile(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<LocalAlbumPlayStats>

    @Query(
        "SELECT albumKey, profileId, MAX(albumJson) AS albumJson, " +
            "COUNT(*) AS playCount, MAX(playedAtMs) AS lastPlayedAtMs " +
            "FROM local_play_history WHERE profileId = :profileId " +
            "GROUP BY albumKey ORDER BY playCount DESC, lastPlayedAtMs DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun frequentAlbumsForProfile(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<LocalAlbumPlayStats>

    @Query("DELETE FROM local_play_history WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)

    @Query("DELETE FROM local_play_history")
    suspend fun clear()
}
