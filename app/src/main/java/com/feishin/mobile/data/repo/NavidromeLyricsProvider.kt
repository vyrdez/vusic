package com.feishin.mobile.data.repo

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSelection
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.core.network.fromSynced
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.OpenSubsonicExtensions
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
class NavidromeLyricsProvider private constructor(
    private val profileLookup: (String) -> ServerProfile?,
    private val apiLookup: (ServerProfile) -> SubsonicApi,
    private val profilePersister: (ServerProfile) -> Unit,
) : LyricsProvider {

    @Inject
    constructor(
        profileStore: ProfileStore,
        backendFactory: BackendClientFactory,
    ) : this(
        profileLookup = { songId ->
            val pid = GlobalId.profileIdOrNull(songId)
            val byId = pid?.let { profileStore.byId(it) }
            byId ?: profileStore.profiles.value.firstOrNull {
                it.enabled && (it.type == BackendType.NAVIDROME || it.type == BackendType.OPENSUBSONIC)
            } ?: profileStore.byId("p1")
        },
        apiLookup = { profile -> backendFactory.subsonicApi(profile) },
        profilePersister = { updated -> profileStore.upsert(updated) },
    )

    internal constructor(
        profile: ServerProfile,
        api: SubsonicApi,
        profileStore: ProfileStore? = null,
    ) : this(
        profileLookup = { profileStore?.byId(profile.id) ?: profile },
        apiLookup = { api },
        profilePersister = { updated -> profileStore?.upsert(updated) },
    )

    private val probedProfiles = ConcurrentHashMap.newKeySet<String>()

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
            ?: return@withContext Result.Ok(
                Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.SERVER_METADATA),
            )
        val api = apiLookup(profile)
        val rawSongId = GlobalId.rawId(songId)
        fetchLyrics(
            api = api,
            rawSongId = rawSongId,
            title = title,
            artist = artist,
            profile = profile,
            profilePersister = profilePersister,
            probedProfiles = probedProfiles,
        )
    }

    internal companion object {
        internal suspend fun fetchExtensionsTolerant(api: SubsonicApi): OpenSubsonicExtensions {
            val names = try {
                val resp = api.getOpenSubsonicExtensions().response
                if (resp.status == "ok") {
                    resp.openSubsonicExtensions.orEmpty()
                        .map { it.name.trim() }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            return OpenSubsonicExtensions(
                songLyrics = names.any { it.equals("songLyrics", ignoreCase = true) },
                transcodeOffset = names.any { it.equals("transcodeOffset", ignoreCase = true) },
                formPost = names.any { it.equals("formPost", ignoreCase = true) },
                apiKeyAuthentication = names.any { it.equals("apiKeyAuthentication", ignoreCase = true) },
                rawNames = names,
            )
        }

        internal suspend fun fetchLyrics(
            api: SubsonicApi,
            rawSongId: String,
            title: String,
            artist: String,
            profile: ServerProfile,
            profilePersister: (ServerProfile) -> Unit = {},
            probedProfiles: MutableSet<String>? = null,
        ): Result<Lyrics> = withContext(Dispatchers.IO) {
            val deviceLang = Locale.getDefault().language

            val isSongLyricsSupported = if (profile.openSubsonicExtensions.rawNames.isNotEmpty()) {
                profile.openSubsonicExtensions.songLyrics
            } else if (probedProfiles == null || probedProfiles.add(profile.id)) {
                val discovered = fetchExtensionsTolerant(api)
                profilePersister(profile.copy(openSubsonicExtensions = discovered))
                discovered.songLyrics
            } else {
                profile.openSubsonicExtensions.songLyrics
            }

            val candidates = mutableListOf<Lyrics>()

            if (isSongLyricsSupported) {
                try {
                    val structuredResp = api.getLyricsBySongId(rawSongId).response
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
                    // Other non-I/O exceptions; fall through to legacy getLyrics
                }
            }

            // Fall back to legacy getLyrics when songLyrics is unsupported or getLyricsBySongId yielded nothing
            if (!isSongLyricsSupported || candidates.isEmpty()) {
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
}
