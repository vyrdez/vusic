package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LrcParser
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class LrclibLyricsProvider @Inject constructor(
    private val json: Json,
) : LyricsProvider {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Vusic/1.2.1 (personal use)")
                .build()
            chain.proceed(req)
        }
        .build()

    /**
     * LRCLIB's fair-use policy is per IP, so one song lookup (up to four
     * requests) must not turn into a burst when the user skips tracks quickly.
     * Serialises every request app-wide with a small gap between them.
     */
    private val paceLock = Any()
    private var lastRequestAtMs = 0L

    private fun pace() = synchronized(paceLock) {
        val wait = MIN_REQUEST_GAP_MS - (System.currentTimeMillis() - lastRequestAtMs)
        if (wait > 0) Thread.sleep(wait)
        lastRequestAtMs = System.currentTimeMillis()
    }

    override suspend fun lyricsForSong(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        localUri: String?,
        localModifiedSec: Long,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        val durationSec = durationMs / 1000
        val isUnknownArtist = artist.isBlank() || artist.equals("Unknown Artist", ignoreCase = true) || artist.equals("<unknown>", ignoreCase = true)

        // For device files with missing tags, only query online if title is descriptive enough
        if (isUnknownArtist && (durationSec <= 0 || title.length < 3 || title.startsWith("track", ignoreCase = true))) {
            return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.LRCLIB))
        }

        var requestCount = 0
        val maxRequests = 4

        // 1. Try exact match via /api/get
        try {
            val getUrl = buildGetUrl(title, artist, album, durationSec)
            requestCount++
            val getCandidate = executeGet(getUrl)
            if (getCandidate != null) {
                val score = LrclibMatcher.scoreCandidate(
                    candidate = getCandidate,
                    targetTitle = title,
                    targetArtist = artist,
                    targetDurationSec = durationSec,
                    isUnknownArtist = isUnknownArtist,
                )
                val threshold = if (isUnknownArtist) LrclibMatcher.UNKNOWN_ARTIST_THRESHOLD else LrclibMatcher.CONFIDENCE_THRESHOLD
                if (score >= threshold) {
                    val lyrics = toLyrics(getCandidate)
                    if (!lyrics.isNone) return@withContext Result.Ok(lyrics)
                }
            }
        } catch (e: IOException) {
            // If the exact get failed with a network error, return Err so it's not negatively cached
            return@withContext Result.Err("LRCLIB network error: ${e.message}", e)
        } catch (e: Exception) {
            // Other exception
        }

        // 2. Query variants via /api/search
        val searchVariants = mutableListOf<Pair<String, String>>() // title, artist

        val cleanT = LrclibMatcher.cleanTitle(title)
        val primaryArtist = LrclibMatcher.splitArtists(artist).firstOrNull() ?: artist

        // Variant A: cleaned title + primary artist
        if (cleanT != title || primaryArtist != artist) {
            searchVariants += cleanT to primaryArtist
        }

        // Variant B: bilingual parts if present
        val parts = LrclibMatcher.splitTitleParts(title)
        for (part in parts) {
            if (part != title && part != cleanT) {
                searchVariants += part to primaryArtist
            }
        }

        // Variant C: cleaned title only (without artist if artist is unknown or multi-artist)
        if (isUnknownArtist || searchVariants.isEmpty()) {
            searchVariants += cleanT to ""
        }

        for ((vTitle, vArtist) in searchVariants) {
            if (requestCount >= maxRequests) break
            requestCount++

            try {
                val searchUrl = buildSearchUrl(vTitle, vArtist)
                val candidates = executeSearch(searchUrl)
                if (candidates.isNotEmpty()) {
                    // Pick the highest scoring candidate
                    val best = candidates.map { c ->
                        c to LrclibMatcher.scoreCandidate(
                            candidate = c,
                            targetTitle = title,
                            targetArtist = artist,
                            targetDurationSec = durationSec,
                            isUnknownArtist = isUnknownArtist,
                        )
                    }.maxByOrNull { it.second }

                    val threshold = if (isUnknownArtist) LrclibMatcher.UNKNOWN_ARTIST_THRESHOLD else LrclibMatcher.CONFIDENCE_THRESHOLD
                    if (best != null && best.second >= threshold) {
                        val lyrics = toLyrics(best.first)
                        if (!lyrics.isNone) return@withContext Result.Ok(lyrics)
                    }
                }
            } catch (e: IOException) {
                return@withContext Result.Err("LRCLIB search error: ${e.message}", e)
            } catch (_: Exception) {
                // Continue to next variant
            }
        }

        Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.LRCLIB))
    }

    private fun buildGetUrl(title: String, artist: String, album: String?, durationSec: Long): String {
        val sb = StringBuilder("https://lrclib.net/api/get?")
        sb.append("track_name=").append(urlEncode(title))
        sb.append("&artist_name=").append(urlEncode(artist))
        if (!album.isNullOrBlank()) {
            sb.append("&album_name=").append(urlEncode(album))
        }
        if (durationSec > 0) {
            sb.append("&duration=").append(durationSec)
        }
        return sb.toString()
    }

    private fun buildSearchUrl(title: String, artist: String): String {
        val sb = StringBuilder("https://lrclib.net/api/search?")
        sb.append("track_name=").append(urlEncode(title))
        if (artist.isNotBlank() && !artist.equals("Unknown Artist", ignoreCase = true)) {
            sb.append("&artist_name=").append(urlEncode(artist))
        }
        return sb.toString()
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun searchCandidates(title: String, artist: String): List<com.feishin.mobile.core.network.LyricsCandidate> = withContext(Dispatchers.IO) {
        val searchUrl = buildSearchUrl(title, artist)
        val dtos = runCatching { executeSearch(searchUrl) }.getOrDefault(emptyList())
        dtos.map { dto ->
            val lyrics = toLyrics(dto)
            com.feishin.mobile.core.network.LyricsCandidate(
                title = dto.trackName ?: title,
                artist = dto.artistName ?: artist,
                album = dto.albumName,
                durationSec = dto.duration?.toLong() ?: 0L,
                source = LyricsSource.LRCLIB,
                isSynced = !dto.syncedLyrics.isNullOrBlank(),
                lyrics = lyrics,
            )
        }
    }

    private fun executeGet(url: String): LrclibResponseDto? {
        val request = Request.Builder().url(url).build()
        pace()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: return null
            return runCatching { json.decodeFromString<LrclibResponseDto>(body) }.getOrNull()
        }
    }

    private fun executeSearch(url: String): List<LrclibResponseDto> {
        val request = Request.Builder().url(url).build()
        pace()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return emptyList()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: return emptyList()
            return runCatching { json.decodeFromString<List<LrclibResponseDto>>(body) }.getOrDefault(emptyList())
        }
    }

    private fun toLyrics(dto: LrclibResponseDto): Lyrics {
        if (dto.instrumental == true) {
            return Lyrics(emptyList(), 0, LyricsKind.INSTRUMENTAL, source = LyricsSource.LRCLIB)
        }
        val synced = dto.syncedLyrics
        if (!synced.isNullOrBlank()) {
            val parsed = LrcParser.parse(synced, source = LyricsSource.LRCLIB)
            if (parsed.lines.isNotEmpty()) return parsed
        }
        val plain = dto.plainLyrics
        if (!plain.isNullOrBlank()) {
            return Lyrics(emptyList(), 0, LyricsKind.PLAIN, plainText = plain.trim(), source = LyricsSource.LRCLIB)
        }
        return Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.LRCLIB)
    }

    private companion object {
        /** 250 ms between requests keeps a 4-request lookup under ~1 s. */
        const val MIN_REQUEST_GAP_MS = 250L
    }
}
