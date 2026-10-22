package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSelection
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.core.network.fromSynced
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves lyrics from a Navidrome (or any OpenSubsonic) server.
 *
 * Resolution order:
 *   1. If the server advertises the `songLyrics` OpenSubsonic extension, use
 *      `getLyricsBySongId`. Prefer `structuredLyrics` (modern OpenSubsonic
 *      format: list of lyric tracks with `synced` flag and timed lines);
 *      fall back to `syncedLyrics`/`plainLyrics` if present.
 *   2. Otherwise use legacy `getLyrics` (plain text). If that text carries
 *      `[mm:ss.xx]` LRC stamps it is parsed as synced; otherwise it is plain.
 *
 * Among several lyric tracks: synced in device language > any synced >
 * unsynced in device language > first available.
 */
@Singleton
class NavidromeLyricsProvider @Inject constructor(
    private val api: SubsonicApi,
) : LyricsProvider {

    @Volatile
    private var songLyricsSupported: Boolean? = null

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val deviceLang = Locale.getDefault().language

        // Check extensions if not yet known
        var isSongLyricsSupported = songLyricsSupported
        if (isSongLyricsSupported == null) {
            try {
                val extResp = api.getOpenSubsonicExtensions().response
                val extensions = extResp.openSubsonicExtensions.orEmpty()
                isSongLyricsSupported = extensions.any { it.name == "songLyrics" }
                songLyricsSupported = isSongLyricsSupported
            } catch (e: Exception) {
                // If extension call fails with network error, propagate
                if (e is IOException) {
                    return@withContext Result.Err("Extensions check failed: ${e.message}", e)
                }
                isSongLyricsSupported = false
            }
        }

        val candidates = mutableListOf<Lyrics>()

        if (isSongLyricsSupported) {
            try {
                val structuredResp = api.getLyricsBySongId(songId).response
                val lyricsList = structuredResp.lyricsList

                // Modern OpenSubsonic format: structuredLyrics
                lyricsList?.structuredLyrics?.forEach { sl ->
                    if (sl.synced && sl.line.isNotEmpty()) {
                        candidates += Lyrics(
                            lines = sl.line.map { LyricLine(it.start, it.value) },
                            offsetMs = sl.offset,
                            kind = LyricsKind.SYNCED,
                            lang = sl.lang,
                            source = LyricsSource.SERVER_METADATA,
                        )
                    } else {
                        val text = sl.line.joinToString("\n") { it.value }.trim()
                        if (text.isNotBlank()) {
                            candidates += parsePlain(text, sl.lang)
                        }
                    }
                }

                // Legacy OpenSubsonic fields (syncedLyrics / plainLyrics)
                val synced = lyricsList?.syncedLyrics
                if (synced != null && synced.line.isNotEmpty()) {
                    candidates += fromSynced(
                        SyncedLyrics.parse(
                            synced.line.map { it.start to it.value },
                            // OpenSubsonic reports the offset in milliseconds
                            // (may be negative); do not rescale it.
                            synced.offset,
                        ),
                        kind = LyricsKind.SYNCED,
                        source = LyricsSource.SERVER_METADATA,
                    ).copy(lang = "")
                }

                val plainText = lyricsList?.plainLyrics?.value ?: structuredResp.lyrics?.value
                if (!plainText.isNullOrBlank()) {
                    candidates += parsePlain(plainText, "")
                }
            } catch (e: IOException) {
                return@withContext Result.Err("getLyricsBySongId failed: ${e.message}", e)
            } catch (_: Exception) {
                // Other non-I/O exceptions
            }
        } else {
            try {
                val plain = api.getLyrics(artist, title).response.lyrics?.value
                if (!plain.isNullOrBlank()) {
                    candidates += parsePlain(plain, "")
                }
            } catch (e: IOException) {
                return@withContext Result.Err("getLyrics failed: ${e.message}", e)
            } catch (_: Exception) {
                // Other non-I/O exceptions
            }
        }

        val chosen = LyricsSelection.choose(candidates, deviceLang)
        Result.Ok(chosen.copy(source = LyricsSource.SERVER_METADATA))
    }

    private fun parsePlain(text: String, lang: String): Lyrics {
        val parsed = LrcParser.parse(text, source = LyricsSource.SERVER_METADATA)
        return if (parsed.lines.isNotEmpty()) {
            parsed.copy(lang = lang, source = LyricsSource.SERVER_METADATA)
        } else {
            Lyrics(
                lines = emptyList(),
                offsetMs = 0,
                kind = LyricsKind.PLAIN,
                plainText = text,
                lang = lang,
                source = LyricsSource.SERVER_METADATA,
            )
        }
    }
}
