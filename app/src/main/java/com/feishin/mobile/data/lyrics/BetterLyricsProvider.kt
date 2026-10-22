package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.core.network.TtmlLyricsParser
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class BetterLyricsResponse(
    val ttml: String? = null,
    val plainLyrics: String? = null,
)

@Singleton
class BetterLyricsProvider @Inject constructor(
    private val json: Json,
) : LyricsProvider {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Vusic/1.2.1 (personal use)")
                .build()
            chain.proceed(req)
        }
        .build()

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
        val isUnknownArtist = artist.isBlank() ||
            artist.equals("Unknown Artist", ignoreCase = true) ||
            artist.equals("<unknown>", ignoreCase = true)

        if (isUnknownArtist && (durationSec <= 0 || title.length < 3)) {
            return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.BETTER_LYRICS))
        }

        val cleanTitle = LrclibMatcher.cleanTitle(title)
        val cleanArtist = if (isUnknownArtist) "" else artist

        val url = buildUrl(cleanTitle, cleanArtist, album, durationSec)

        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        val parsedDto = runCatching {
                            json.decodeFromString<BetterLyricsResponse>(body)
                        }.getOrNull()

                        val ttml = parsedDto?.ttml
                        if (!ttml.isNullOrBlank()) {
                            val lyrics = TtmlLyricsParser.parse(ttml, LyricsSource.BETTER_LYRICS)
                            if (!lyrics.isNone) return@withContext Result.Ok(lyrics)
                        }

                        val plain = parsedDto?.plainLyrics
                        if (!plain.isNullOrBlank()) {
                            return@withContext Result.Ok(
                                Lyrics(
                                    lines = plain.lineSequence()
                                        .filter { it.isNotBlank() }
                                        .map { com.feishin.mobile.core.network.LyricLine(0L, it.trim()) }
                                        .toList(),
                                    kind = LyricsKind.PLAIN,
                                    plainText = plain,
                                    source = LyricsSource.BETTER_LYRICS,
                                ),
                            )
                        }

                        Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.BETTER_LYRICS))
                    }
                    404, 422 -> {
                        Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.BETTER_LYRICS))
                    }
                    else -> {
                        Result.Err("BetterLyrics HTTP error: ${response.code}")
                    }
                }
            }
        } catch (e: IOException) {
            Result.Err("BetterLyrics network error: ${e.message}", e)
        } catch (e: Exception) {
            Result.Err("BetterLyrics unexpected error: ${e.message}", e)
        }
    }

    private fun buildUrl(
        title: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val sb = StringBuilder("https://api.betterlyrics.org/getLyrics?")
        sb.append("s=").append(enc(title))
        if (artist.isNotBlank()) {
            sb.append("&a=").append(enc(artist))
        }
        if (!album.isNullOrBlank()) {
            sb.append("&al=").append(enc(album))
        }
        if (durationSec > 0) {
            sb.append("&d=").append(durationSec)
        }
        return sb.toString()
    }
}
