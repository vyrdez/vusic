package com.feishin.mobile.data.lyrics

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.domain.repo.LyricsProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * Manual probe (not a regression test): runs the real lyrics providers in the
 * order LyricsRepository uses and reports best-sync level per song.
 *
 * LyricsRepository itself always sees "offline" under JVM unit tests:
 * isNetworkAvailable() needs NetworkCapabilities.hasCapability(INTERNET), which
 * the mockable android.jar never reports true. So this probe calls the same
 * provider instances and applies the repository's own winner rule directly.
 */
class LyricsProbeTest {

    private data class Row(val title: String, val artist: String, val album: String?, val sec: Int)
    private data class Hit(val id: String, val lyrics: Lyrics, val requests: Int) {
        val synced get() = lyrics.isSynced
        val word get() = lyrics.lines.any { it.words.isNotEmpty() }
        val plain get() = lyrics.isPlain
        val level get() = when {
            word -> "word"
            synced -> "line"
            plain -> "plain"
            else -> "-"
        }
    }

    private fun readRows(): List<Row> = listOf(File("tools/lyrics_probe.txt"), File("../tools/lyrics_probe.txt"))
        .first { it.isFile }
        .readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { line ->
            val f = line.split("|").map { it.trim() }
            Row(f[0], f.getOrElse(1) { "" }, f.getOrElse(2) { "" }.ifBlank { null }, f.getOrElse(3) { "0" }.toIntOrNull() ?: 0)
        }

    /**
     * Counts real HTTP calls by rewriting the provider's private OkHttpClient.
     * Providers without a client field (Paxsenix is a disabled placeholder)
     * are left as-is and report zero requests.
     */
    private fun counted(provider: LyricsProvider): Triple<LyricsProvider, () -> Int, () -> Unit> {
        var n = 0
        val field = runCatching { provider.javaClass.getDeclaredField("client").apply { isAccessible = true } }
            .getOrNull() ?: return Triple(provider, { 0 }, {})
        val original = field.get(provider) as OkHttpClient
        field.set(provider, original.newBuilder()
            .addInterceptor { chain -> n++; chain.proceed(chain.request()) }.build())
        return Triple(provider, { n }, { field.set(provider, original) })
    }

    @Test
    fun probe() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val base = listOf(
            "LRCLIB" to LrclibLyricsProvider(json),
            "BETTER_LYRICS" to BetterLyricsProvider(json),
            "KUGOU" to KuGouLyricsProvider(json),
            "PAXSENIX" to PaxsenixLyricsProvider(),
        )
        val providers = base.map { (id, p) -> val (q, count, _) = counted(p); Triple(id, q, count) }

        val rows = readRows()
        val perProv = providers.associate { it.first to intArrayOf(0, 0, 0, 0) } // synced, word, req, wins
        var overallSynced = 0
        println("song | artist | winner | sync | requests")

        rows.forEachIndexed { i, row ->
            val hits = providers.map { (id, p, count) ->
                val before = count()
                val r = runBlocking {
                    runCatching { p.lyricsForSong("probe-$i", row.title, row.artist, row.album, row.sec * 1000L, null, 0L) }
                }.getOrNull()
                val lyrics = (r as? Result.Ok)?.value
                if (lyrics != null) Hit(id, lyrics, count() - before) else Hit(id, Lyrics(kind = LyricsKind.NONE), count() - before)
            }
            // Same rule as LyricsRepository: preferWordByWord = true (app default).
            var best: Hit? = null
            var plainHit: Hit? = null
            for (h in hits) {
                if (h.synced) {
                    if (best == null) {
                        best = h
                        if (h.word) break
                    } else if (best.word.not() && h.word) {
                        best = h
                        break
                    }
                } else if (h.plain && plainHit == null) plainHit = h
            }
            val win = best ?: plainHit
            hits.groupBy { it.id }.forEach { (id, hs) -> perProv[id]?.set(2, perProv[id]!![2] + hs.sumOf { it.requests }) }
            if (win?.synced == true) overallSynced++
            win?.let { h ->
                perProv[h.id]?.let { a -> if (h.synced) a[0]++; if (h.word) a[1]++; a[3]++ }
            }
            println("${i + 1}. ${row.title} | ${row.artist} | ${win?.id ?: "NONE"} | ${win?.level ?: "-"} | ${hits.sumOf { it.requests }}")
        }

        val n = rows.size
        println()
        println("provider | synced | word | wins | requests")
        perProv.forEach { (id, a) -> println("$id | ${a[0]}/$n | ${a[1]}/$n | ${a[3]}/$n | ${a[2]}") }
        println("OVERALL synced rate: $overallSynced/$n")
    }
}
