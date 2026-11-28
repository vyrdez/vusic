package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.db.DriveTrackDao
import com.feishin.mobile.domain.repo.LyricsProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Offline [LyricsProvider] for Google Drive tracks.
 * Reads pre-parsed `@Serializable Lyrics` JSON from [DriveTrackDao.embeddedLyrics].
 */
@Singleton
class DriveEmbeddedLyricsProvider @Inject constructor(
    private val driveTrackDao: DriveTrackDao,
    private val json: Json,
) : LyricsProvider {

    /** Returns decoded embedded [Lyrics] from `drive_tracks`, or `null` when absent/invalid. */
    suspend fun readEmbeddedLyrics(songId: String): Lyrics? = withContext(Dispatchers.IO) {
        val rawJson = queryEmbeddedJson(songId) ?: return@withContext null
        val decoded = runCatching { json.decodeFromString(Lyrics.serializer(), rawJson) }.getOrNull()
            ?: return@withContext null
        decoded.takeUnless { it.isNone }
    }

    private suspend fun queryEmbeddedJson(songId: String): String? {
        driveTrackDao.embeddedLyrics(songId)?.takeIf { it.isNotBlank() }?.let { return it }
        val rawId = GlobalId.rawId(songId)
        if (rawId != songId) {
            driveTrackDao.embeddedLyrics(rawId)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> {
        val lyrics = runCatching { readEmbeddedLyrics(songId) }.getOrNull()
        return Result.Ok(lyrics ?: Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.FILE_TAGS))
    }
}
