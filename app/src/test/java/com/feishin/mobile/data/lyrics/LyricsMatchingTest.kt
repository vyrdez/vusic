package com.feishin.mobile.data.lyrics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSelection
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section C unit tests: variants, duration-first scoring, request cap, migration, selection. */
class LyricsMatchingTest {

    private fun dto(title: String, artist: String, duration: Double, synced: String? = null) =
        LrclibResponseDto(trackName = title, artistName = artist, duration = duration, syncedLyrics = synced)

    private fun score(candidate: LrclibResponseDto, title: String, artist: String, sec: Long) =
        LrclibMatcher.scoreCandidate(candidate, title, artist, sec)

    @Test
    fun `title variants strip tags, split bilingual dashes, keep raw and NFKC`() {
        val variants = LrclibMatcher.generateTitleVariants("晩餐歌 - Bansanka (feat. tuki.)")
        assertTrue("clean title missing: $variants", variants.contains("晩餐歌 - Bansanka"))
        assertTrue("original part missing: $variants", variants.contains("晩餐歌"))
        assertTrue("romanized part missing: $variants", variants.contains("Bansanka"))
        assertEquals("not deduplicated: $variants", variants.distinct(), variants)
        assertTrue("cap exceeded: $variants", variants.size <= 5)

        val raw = LrclibMatcher.generateTitleVariants("Mahou (Acoustic version)")
        assertTrue("raw title dropped: $raw", raw.contains("Mahou (Acoustic version)"))
        val nfkc = LrclibMatcher.generateTitleVariants("Ａ Ｆｕ Ｒｅ Ｒｕ")
        assertTrue("NFKC form missing: $nfkc", nfkc.any { it == "A Fu Re Ru" })
    }

    @Test
    fun `artist variants split credits and add empty title-only variant`() {
        val variants = LrclibMatcher.generateArtistVariants("mahiru, RINZO")
        assertTrue("primary missing: $variants", variants.contains("mahiru"))
        assertTrue("co-artist missing: $variants", variants.contains("RINZO"))
        assertTrue("title-only missing: $variants", variants.contains(""))
        assertEquals("not deduplicated: $variants", variants.distinct(), variants)
        assertTrue("cap exceeded: $variants", variants.size <= 4)

        val slashed = LrclibMatcher.generateArtistVariants("Wuthering Waves / baitian / Victor Bo")
        assertTrue(slashed.contains("Wuthering Waves"))
        assertTrue(slashed.contains("baitian"))
        assertTrue(slashed.contains("Victor Bo"))
    }

    @Test
    fun `script mismatch accepted when duration and title agree`() {
        val s = score(dto("fragrance", "mahiru", 205.0), "フレグランス - fragrance", "茉ひる", 205L)
        assertTrue("kanji vs romaji artist must not block a close-duration match, got $s", s > 0.0)
    }

    @Test
    fun `duration difference over 5 seconds is rejected`() {
        assertEquals(0.0, score(dto("Bansanka", "tuki.", 230.0), "晩餐歌 - Bansanka", "tuki.", 217L), 0.001)
    }

    @Test
    fun `bilingual title matches romaji candidate`() {
        val s = score(dto("Bansanka", "tuki.", 217.0), "晩餐歌 - Bansanka", "tuki.", 217L)
        assertTrue("expected accepted bilingual match, got $s", s >= LrclibMatcher.CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `very short title needs artist agreement beyond one second delta`() {
        val ok = score(dto("Io", "Rokudenashi", 185.0), "Io", "Rokudenashi", 185L)
        assertTrue("exact short-title match rejected, got $ok", ok > 0.0)
        val bad = score(dto("Io", "Someone Else", 188.0), "Io", "Rokudenashi", 185L)
        assertEquals("short generic title with no artist match must be rejected", 0.0, bad, 0.001)
    }

    @Test
    fun `synced candidate scores higher than the same candidate without timing`() {
        // Partial title match keeps the score below the 1.0 ceiling so the bonus is visible.
        val plain = score(dto("Gifted", "Myuk", 195.0), "Gift", "Myuk", 195L)
        val synced = score(dto("Gifted", "Myuk", 195.0, "[00:01.00]a"), "Gift", "Myuk", 195L)
        assertTrue("synced bonus missing: plain=$plain synced=$synced", synced > plain)
    }

    @Test
    fun `selection prefers synced over plain`() {
        val plain = Lyrics(listOf(LyricLine(0L, "no timing")), kind = LyricsKind.PLAIN,
            plainText = "no timing", source = LyricsSource.KUGOU)
        val synced = Lyrics(listOf(LyricLine(1000L, "timed")), kind = LyricsKind.SYNCED,
            source = LyricsSource.LRCLIB)
        assertEquals(synced, LyricsSelection.choose(listOf(plain, synced)))
    }

    /** Replaces the provider's own client so no real request leaves the JVM. */
    private fun stubLrclib(respond: (Request) -> Pair<Int, String>): Pair<LrclibLyricsProvider, () -> Int> {
        val provider = LrclibLyricsProvider(Json { ignoreUnknownKeys = true })
        var calls = 0
        val field = provider.javaClass.getDeclaredField("client").apply { isAccessible = true }
        val stubbed = (field.get(provider) as OkHttpClient).newBuilder()
            .addInterceptor { chain ->
                calls++
                val (code, body) = respond(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("stub")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }
            .build()
        field.set(provider, stubbed)
        return provider to { calls }
    }

    @Test
    fun `lrclib stops at the request cap when nothing matches`() = runBlocking {
        val (provider, calls) = stubLrclib { r ->
            if (r.url.encodedPath.endsWith("/get")) 404 to "" else 200 to "[]"
        }
        val result = provider.lyricsForSong("cap", "晩餐歌 - Bansanka (feat. RINZO)", "tuki., RINZO",
            null, 217_000L, null, 0L)
        val used = calls()
        assertTrue("provider exceeded 6-request cap: $used", used <= 6)
        assertTrue((result as Result.Ok).value.isNone)
    }

    @Test
    fun `lrclib exits early once synced lyrics are found`() = runBlocking {
        val (provider, calls) = stubLrclib { r ->
            if (r.url.encodedPath.endsWith("/get")) {
                200 to """{"trackName":"Bansanka","artistName":"tuki.","duration":217.0,""" +
                    """"syncedLyrics":"[00:01.00]first\n[00:02.00]second"}"""
            } else {
                200 to "[]"
            }
        }
        val result = provider.lyricsForSong("early", "Bansanka", "tuki.", null, 217_000L, null, 0L)
        assertTrue("expected synced lyrics", (result as Result.Ok).value.isSynced)
        assertEquals("synced exact hit must stop after one request", 1, calls())
    }

    private fun newStore(): Pair<SettingsStore, DataStore<Preferences>> {
        val dir = Files.createTempDirectory("lyrics-settings").toFile()
        val dataStore = PreferenceDataStoreFactory.create { File(dir, "s.preferences_pb") }
        return SettingsStore(dataStore) to dataStore
    }

    @Test
    fun `stored order is replaced by the new default before migration`() = runBlocking {
        val (store, dataStore) = newStore()
        dataStore.edit {
            it[stringPreferencesKey("lyrics_provider_order")] = "LRCLIB,KUGOU"
            it[stringPreferencesKey("lyrics_provider_enabled")] = "LRCLIB,KUGOU"
        }
        val settings = store.flow.first()
        assertEquals(SettingsStore.DEFAULT_LYRICS_PROVIDER_ORDER, settings.lyricsProviderOrder)
        assertEquals("enabled flags must be preserved", setOf("LRCLIB", "KUGOU"), settings.lyricsProviderEnabled)
    }

    @Test
    fun `migrated flag keeps a custom order and reset restores the default`() = runBlocking {
        val (store, dataStore) = newStore()
        dataStore.edit {
            it[stringPreferencesKey("lyrics_provider_order")] = "KUGOU,LRCLIB"
            it[booleanPreferencesKey("lyrics_provider_order_migrated_92")] = true
        }
        assertEquals(listOf("KUGOU", "LRCLIB"), store.flow.first().lyricsProviderOrder)
        store.resetLyricsProviders()
        assertEquals(SettingsStore.DEFAULT_LYRICS_PROVIDER_ORDER, store.flow.first().lyricsProviderOrder)
    }
}
