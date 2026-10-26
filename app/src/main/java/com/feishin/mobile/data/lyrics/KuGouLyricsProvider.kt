package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.KrcLyricsParser
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class KugouSearchSong(
    val hash: String = "",
    val songname: String = "",
    val singername: String = "",
    val duration: Int = 0,
)

@Serializable
private data class KugouSearchData(
    val info: List<KugouSearchSong> = emptyList(),
)

@Serializable
private data class KugouSearchResponse(
    val status: Int = 0,
    val data: KugouSearchData? = null,
)

@Serializable
private data class KugouCandidate(
    val id: String = "",
    val accesskey: String = "",
    val score: Int = 0,
    val duration: Long = 0L,
)

@Serializable
private data class KugouCandidatesResponse(
    val status: Int = 0,
    val candidates: List<KugouCandidate> = emptyList(),
)

@Serializable
private data class KugouDownloadResponse(
    val status: Int = 0,
    val content: String = "",
    val fmt: String = "",
)

@Singleton
class KuGouLyricsProvider @Inject constructor(
    private val json: Json,
) : LyricsProvider {

    private val jsonLenient = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
            return@withContext Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.KUGOU))
        }

        val cleanTitle = LrclibMatcher.cleanTitle(title)
        val primaryArtist = if (isUnknownArtist) "" else (LrclibMatcher.splitArtists(artist).firstOrNull() ?: artist)

        // Generate query variants according to Section C spec:
        // cleanTitle, titlePart1, titlePart2, cleanTitle + " " + primaryArtist
        val queries = mutableListOf<String>()
        queries += cleanTitle
        if (title.contains(" - ")) {
            val parts = title.split(" - ").map { LrclibMatcher.cleanTitle(it) }.filter { it.isNotBlank() }
            queries.addAll(parts)
        }
        if (primaryArtist.isNotBlank()) {
            queries += "$cleanTitle $primaryArtist"
        }
        val distinctQueries = queries.distinct()

        var callCount = 0
        val maxCalls = 6

        try {
            for (query in distinctQueries) {
                if (callCount >= maxCalls) break
                // Step 1: Search song to retrieve hash (1 call)
                callCount++
                val hash = searchSongHash(query, targetDurationSec = durationSec) ?: continue

                if (callCount >= maxCalls) break
                // Step 2: Search candidates to retrieve download id & accesskey (1 call)
                callCount++
                val candidate = searchCandidates(cleanTitle, durationMs, hash) ?: continue

                if (callCount >= maxCalls) break
                // Step 3: Download KRC (1 call)
                callCount++
                val krcBase64 = downloadKrc(candidate.id, candidate.accesskey) ?: continue

                // Step 4: Parse KRC
                val lyrics = KrcLyricsParser.parseBase64(krcBase64, LyricsSource.KUGOU)
                if (lyrics.isSynced) return@withContext Result.Ok(lyrics)
            }
            Result.Ok(Lyrics(emptyList(), 0, LyricsKind.NONE, source = LyricsSource.KUGOU))
        } catch (e: IOException) {
            Result.Err("KuGou network error: ${e.message}", e)
        } catch (e: Exception) {
            Result.Err("KuGou unexpected error: ${e.message}", e)
        }
    }

    private fun searchSongHash(query: String, targetDurationSec: Long): String? {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        // mobilecdn's certificate is issued for a different host name, so TLS
        // cannot be verified there; the search request stays on HTTP. Nothing
        // but title/artist leaves this call.
        val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${enc(query)}&page=1&pagesize=5"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { jsonLenient.decodeFromString<KugouSearchResponse>(body) }.getOrNull()
            val items = parsed?.data?.info ?: return null
            if (items.isEmpty()) return null

            if (targetDurationSec > 0) {
                // Find candidate within +/- 3 seconds (Section C spec)
                val match = items.firstOrNull { Math.abs(it.duration - targetDurationSec) <= 3 }
                if (match != null && match.hash.isNotBlank()) return match.hash
            }

            return items.firstOrNull { it.hash.isNotBlank() }?.hash
        }
    }

    private fun searchCandidates(title: String, durationMs: Long, hash: String): KugouCandidate? {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val url = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${enc(title)}&duration=$durationMs&hash=$hash"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { jsonLenient.decodeFromString<KugouCandidatesResponse>(body) }.getOrNull()
            val candidates = parsed?.candidates ?: return null
            if (candidates.isEmpty()) return null

            return candidates.maxByOrNull { it.score } ?: candidates.first()
        }
    }

    private fun downloadKrc(id: String, accessKey: String): String? {
        val url = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { jsonLenient.decodeFromString<KugouDownloadResponse>(body) }.getOrNull()
            return parsed?.content?.takeIf { it.isNotBlank() }
        }
    }
}
