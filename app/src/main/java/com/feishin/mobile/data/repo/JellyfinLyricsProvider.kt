package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.PlaybackSource
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.db.DownloadDao
import com.feishin.mobile.data.db.DownloadStatus
import com.feishin.mobile.data.lyrics.FileTagLyricsProvider
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Resolves lyrics from a Jellyfin server (`GET /Audio/{itemId}/Lyrics`).
 *
 * Jellyfin lyric timestamps are reported in 100-nanosecond ticks (`Start / 10_000L` ms).
 * HTTP 404 or empty lyrics return `Result.Ok(LyricsKind.NONE)` so [LyricsRepository] falls
 * through to external providers without treating missing server lyrics as an error.
 * If the server request fails offline/over the network (or the profile is unavailable)
 * and a completed downloaded local file exists for `songId`, embedded file tags are used.
 */
@Singleton
class JellyfinLyricsProvider private constructor(
    private val profileLookup: (String) -> ServerProfile?,
    private val apiLookup: (ServerProfile) -> JellyfinApi,
    private val downloadPathLookup: suspend (String) -> String?,
    private val fileTags: FileTagLyricsProvider,
) : LyricsProvider {

    @Inject
    constructor(
        profileStore: ProfileStore,
        backendFactory: BackendClientFactory,
        downloadDao: DownloadDao,
        fileTags: FileTagLyricsProvider,
    ) : this(
        profileLookup = { songId ->
            GlobalId.profileIdOrNull(songId)
                ?.let { profileStore.byId(it) }
                ?.takeIf { it.type == BackendType.JELLYFIN }
        },
        apiLookup = { profile -> backendFactory.jellyfinApi(profile) },
        downloadPathLookup = { songId ->
            val row = downloadDao.byId(songId)
            if (row != null && row.statusEnum == DownloadStatus.COMPLETED) {
                row.filePath?.takeIf { path ->
                    PlaybackSource.isPlayableLocalFile(
                        path,
                        { File(it).exists() },
                        { File(it).length() },
                    )
                }
            } else {
                null
            }
        },
        fileTags = fileTags,
    )

    internal constructor(
        profile: ServerProfile?,
        api: JellyfinApi,
        fileTags: FileTagLyricsProvider,
        downloadPathLookup: suspend (String) -> String? = { null },
    ) : this(
        profileLookup = { profile?.takeIf { it.type == BackendType.JELLYFIN } },
        apiLookup = { api },
        downloadPathLookup = downloadPathLookup,
        fileTags = fileTags,
    )

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val profile = profileLookup(songId)
        if (profile == null) {
            val local = fallbackToDownloadedFile(songId, title, artist, album, durationMs, localUri, localModifiedSec)
            return@withContext Result.Ok(local ?: emptyServerLyrics())
        }

        val api = apiLookup(profile)
        val rawId = GlobalId.rawId(songId)

        try {
            val dto = api.getLyrics(rawId)
            if (dto.lyrics.isEmpty()) {
                return@withContext Result.Ok(emptyServerLyrics())
            }

            if (dto.lyrics.any { it.start != null }) {
                val syncedLines = dto.lyrics.mapNotNull { line ->
                    val startTicks = line.start ?: return@mapNotNull null
                    LyricLine(startMs = startTicks / TICKS_PER_MILLISECOND, text = line.text)
                }
                return@withContext Result.Ok(
                    if (syncedLines.isEmpty()) {
                        emptyServerLyrics()
                    } else {
                        Lyrics(
                            lines = syncedLines,
                            offsetMs = 0L,
                            kind = LyricsKind.SYNCED,
                            source = LyricsSource.SERVER_METADATA,
                        )
                    },
                )
            }

            val joined = dto.lyrics
                .map { it.text.trimEnd() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            if (joined.isBlank()) {
                return@withContext Result.Ok(emptyServerLyrics())
            }

            val parsed = LrcParser.parse(joined, source = LyricsSource.SERVER_METADATA)
            val resolved = if (parsed.lines.isNotEmpty()) {
                parsed.copy(source = LyricsSource.SERVER_METADATA)
            } else {
                Lyrics(
                    lines = emptyList(),
                    offsetMs = 0L,
                    kind = LyricsKind.PLAIN,
                    plainText = joined,
                    source = LyricsSource.SERVER_METADATA,
                )
            }
            Result.Ok(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Result.Ok(emptyServerLyrics())
            } else {
                val local = fallbackToDownloadedFile(songId, title, artist, album, durationMs, localUri, localModifiedSec)
                if (local != null) {
                    Result.Ok(local)
                } else {
                    Result.Err("Jellyfin getLyrics failed (${e.code()}): ${e.message()}", e)
                }
            }
        } catch (e: IOException) {
            val local = fallbackToDownloadedFile(songId, title, artist, album, durationMs, localUri, localModifiedSec)
            if (local != null) {
                Result.Ok(local)
            } else {
                Result.Err("Jellyfin getLyrics failed: ${e.message}", e)
            }
        } catch (e: Exception) {
            val local = fallbackToDownloadedFile(songId, title, artist, album, durationMs, localUri, localModifiedSec)
            if (local != null) {
                Result.Ok(local)
            } else {
                Result.Err("Jellyfin getLyrics failed: ${e.message}", e)
            }
        }
    }

    private suspend fun fallbackToDownloadedFile(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Lyrics? {
        val downloadedPath = localUri ?: downloadPathLookup(songId) ?: return null
        val res = fileTags.lyricsForSong(
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            localUri = downloadedPath,
            localModifiedSec = localModifiedSec,
        )
        return (res as? Result.Ok)?.value?.takeUnless { it.isNone }
    }

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L

        fun emptyServerLyrics(): Lyrics = Lyrics(
            lines = emptyList(),
            offsetMs = 0L,
            kind = LyricsKind.NONE,
            source = LyricsSource.SERVER_METADATA,
        )
    }
}
