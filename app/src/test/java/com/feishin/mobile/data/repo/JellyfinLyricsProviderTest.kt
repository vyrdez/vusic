package com.feishin.mobile.data.repo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.db.LyricsCacheDao
import com.feishin.mobile.data.db.LyricsCacheEntity
import com.feishin.mobile.data.lyrics.FileTagLyricsProvider
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.LyricsProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class JellyfinLyricsProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun jellyfinProfile(id: String = "p2"): ServerProfile = ServerProfile(
        id = id,
        name = "Jellyfin Test",
        type = BackendType.JELLYFIN,
        url = "https://jellyfin.example.com",
        credentials = ProfileCredentials.Jellyfin(
            username = "alice",
            accessToken = "token-1",
            serverId = "srv-1",
            deviceId = "dev-1",
        ),
    )

    private fun createJellyfinApi(
        handler: (String) -> Pair<Int, String>,
    ): JellyfinApi {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val (code, body) = handler(req.url.encodedPath)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return Retrofit.Builder()
            .baseUrl("https://jellyfin.example.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JellyfinApi::class.java)
    }

    private fun createFailingJellyfinApi(): JellyfinApi {
        val interceptor = Interceptor {
            throw IOException("Simulated offline network")
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return Retrofit.Builder()
            .baseUrl("https://jellyfin.example.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JellyfinApi::class.java)
    }

    private fun writeSyntheticId3File(lyricsText: String): File {
        val framePayload = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(lyricsText.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()

        val frameBytes = ByteArrayOutputStream().apply {
            write("USLT".toByteArray(StandardCharsets.ISO_8859_1))
            val size = framePayload.size
            write((size shr 24) and 0xFF)
            write((size shr 16) and 0xFF)
            write((size shr 8) and 0xFF)
            write(size and 0xFF)
            write(0)
            write(0)
            write(framePayload)
        }.toByteArray()

        val id3 = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
            write(3)
            write(0)
            write(0)
            val tagSize = frameBytes.size
            write((tagSize shr 21) and 0x7F)
            write((tagSize shr 14) and 0x7F)
            write((tagSize shr 7) and 0x7F)
            write(tagSize and 0x7F)
            write(frameBytes)
        }.toByteArray()

        return tmp.newFile("downloaded_${UUID.randomUUID()}.mp3").apply {
            writeBytes(id3)
        }
    }

    private fun newProfileStore(
        scope: CoroutineScope,
        profiles: List<ServerProfile>,
    ): Pair<ProfileStore, SettingsStore> {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val store = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
        )
        store.saveAll(profiles)
        return store to settings
    }

    @Test
    fun syncedTicks_convertedToMilliseconds() = runTest {
        val api = createJellyfinApi { path ->
            assertEquals("/Audio/item-1/Lyrics", path)
            200 to """
                {
                  "Metadata": {"IsSynced": true},
                  "Lyrics": [
                    {"Text": "First line", "Start": 12340000},
                    {"Text": "Second line", "Start": 25000000}
                  ]
                }
            """.trimIndent()
        }
        val provider = JellyfinLyricsProvider(
            profile = jellyfinProfile("p2"),
            api = api,
            fileTags = FileTagLyricsProvider(stubContext()),
        )

        val result = provider.lyricsForSong("p2:item-1", "Song", "Artist", null, 180_000L)
        assertTrue(result is Result.Ok)
        val lyrics = (result as Result.Ok).value
        assertEquals(LyricsKind.SYNCED, lyrics.kind)
        assertEquals(LyricsSource.SERVER_METADATA, lyrics.source)
        assertEquals(listOf(LyricLine(1234L, "First line"), LyricLine(2500L, "Second line")), lyrics.lines)
    }

    @Test
    fun unsyncedLines_returnsPlainLyrics_andParsesEmbeddedLrc() = runTest {
        val plainApi = createJellyfinApi {
            200 to """
                {
                  "Lyrics": [
                    {"Text": "Line one"},
                    {"Text": "   "},
                    {"Text": "Line two"}
                  ]
                }
            """.trimIndent()
        }
        val plainProvider = JellyfinLyricsProvider(
            profile = jellyfinProfile("p2"),
            api = plainApi,
            fileTags = FileTagLyricsProvider(stubContext()),
        )
        val plainResult = plainProvider.lyricsForSong("p2:item-2", "Song", "Artist", null, 180_000L)
        assertTrue(plainResult is Result.Ok)
        val plainLyrics = (plainResult as Result.Ok).value
        assertEquals(LyricsKind.PLAIN, plainLyrics.kind)
        assertEquals(LyricsSource.SERVER_METADATA, plainLyrics.source)
        assertEquals("Line one\nLine two", plainLyrics.plainText)

        val lrcApi = createJellyfinApi {
            200 to """
                {
                  "Lyrics": [
                    {"Text": "[00:01.50]Timed line one"},
                    {"Text": "[00:04.00]Timed line two"}
                  ]
                }
            """.trimIndent()
        }
        val lrcProvider = JellyfinLyricsProvider(
            profile = jellyfinProfile("p2"),
            api = lrcApi,
            fileTags = FileTagLyricsProvider(stubContext()),
        )
        val lrcResult = lrcProvider.lyricsForSong("p2:item-3", "Song", "Artist", null, 180_000L)
        assertTrue(lrcResult is Result.Ok)
        val lrcLyrics = (lrcResult as Result.Ok).value
        assertEquals(LyricsKind.SYNCED, lrcLyrics.kind)
        assertEquals(LyricsSource.SERVER_METADATA, lrcLyrics.source)
        assertEquals(1500L, lrcLyrics.lines[0].startMs)
        assertEquals("Timed line one", lrcLyrics.lines[0].text)
    }

    @Test
    fun http404_returnsOkWithLyricsNone() = runTest {
        val api = createJellyfinApi { 404 to """{"Message":"Not Found"}""" }
        val provider = JellyfinLyricsProvider(
            profile = jellyfinProfile("p2"),
            api = api,
            fileTags = FileTagLyricsProvider(stubContext()),
        )

        val result = provider.lyricsForSong("p2:missing", "Song", "Artist", null, 180_000L)
        assertTrue(result is Result.Ok)
        val lyrics = (result as Result.Ok).value
        assertEquals(LyricsKind.NONE, lyrics.kind)
        assertTrue(lyrics.isNone)
        assertEquals(LyricsSource.SERVER_METADATA, lyrics.source)
    }

    @Test
    fun offlineError_fallsBackToDownloadedLocalFile() = runTest {
        val localFile = writeSyntheticId3File("[00:05.00]Offline embedded line")
        val provider = JellyfinLyricsProvider(
            profile = jellyfinProfile("p2"),
            api = createFailingJellyfinApi(),
            fileTags = FileTagLyricsProvider(stubContext()),
            downloadPathLookup = { songId ->
                if (songId == "p2:dl-1") localFile.absolutePath else null
            },
        )

        val result = provider.lyricsForSong("p2:dl-1", "Song", "Artist", null, 180_000L)
        assertTrue(result is Result.Ok)
        val lyrics = (result as Result.Ok).value
        assertEquals(LyricsKind.SYNCED, lyrics.kind)
        assertEquals(LyricsSource.FILE_TAGS, lyrics.source)
        assertEquals(5000L, lyrics.lines.single().startMs)
        assertEquals("Offline embedded line", lyrics.lines.single().text)
    }

    @Test
    fun lyricsRepository_jellyfin404_fallsThroughToNextProvider() = runTest {
        val profile = jellyfinProfile("p2")
        val (profileStore, settingsStore) = newProfileStore(backgroundScope, listOf(profile))
        settingsStore.setLyricsProviderOrder(listOf("LOCAL_DATA", "LRCLIB"))
        settingsStore.setLyricsProviderEnabled(setOf("LOCAL_DATA", "LRCLIB"))
        settingsStore.setLyricsFetchOnline(true)
        settingsStore.setOfflineMode(false)

        val jellyfinProvider = JellyfinLyricsProvider(
            profile = profile,
            api = createJellyfinApi { 404 to "{}" },
            fileTags = FileTagLyricsProvider(stubContext()),
        )
        val fakeLrclib = object : LyricsProvider {
            override suspend fun lyricsForSong(
                songId: String,
                title: String,
                artist: String,
                album: String?,
                durationMs: Long,
                localUri: String?,
                localModifiedSec: Long,
            ): Result<Lyrics> = Result.Ok(
                Lyrics(
                    lines = listOf(LyricLine(2000L, "Fallback LRCLIB line")),
                    kind = LyricsKind.SYNCED,
                    source = LyricsSource.LRCLIB,
                ),
            )
        }
        val noopProvider = object : LyricsProvider {
            override suspend fun lyricsForSong(
                songId: String,
                title: String,
                artist: String,
                album: String?,
                durationMs: Long,
                localUri: String?,
                localModifiedSec: Long,
            ): Result<Lyrics> = Result.Ok(Lyrics(kind = LyricsKind.NONE))
        }

        val repo = LyricsRepository(
            cache = InMemoryLyricsCacheDao(),
            lrclib = fakeLrclib,
            betterLyrics = noopProvider,
            paxsenix = noopProvider,
            kugou = noopProvider,
            navidrome = noopProvider,
            jellyfin = jellyfinProvider,
            fileTags = FileTagLyricsProvider(stubContext()),
            profileStore = profileStore,
            settingsStore = settingsStore,
            json = json,
            context = stubContext(),
        )

        val track = Track(
            id = "p2:item-404",
            title = "Song",
            album = "Album",
            albumId = null,
            artist = "Artist",
            artistId = null,
            coverArt = null,
            durationSec = 180,
            trackNo = null,
            year = null,
            genre = null,
            suffix = null,
            starred = false,
        )
        val result = repo.getLyrics(track)
        assertTrue(result is Result.Ok)
        val lyrics = (result as Result.Ok).value
        assertEquals(LyricsKind.SYNCED, lyrics.kind)
        assertEquals(LyricsSource.LRCLIB, lyrics.source)
        assertEquals("Fallback LRCLIB line", lyrics.lines.single().text)
    }

    private class InMemoryLyricsCacheDao : LyricsCacheDao {
        private val entries = mutableMapOf<String, LyricsCacheEntity>()

        override suspend fun getEntry(id: String): LyricsCacheEntity? = entries[id]
        override suspend fun get(id: String): String? = entries[id]?.json
        override suspend fun put(entry: LyricsCacheEntity) {
            entries[entry.songId] = entry
        }
        override suspend fun remove(id: String) {
            entries.remove(id)
        }
        override suspend fun deleteByProfile(profileId: String) {
            entries.entries.removeAll { it.value.profileId == profileId }
        }
        override suspend fun clear() {
            entries.clear()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                pending.forEach { (k, v) -> if (v == null) target.remove(k) else target[k] = v }
            }
        }
    }
}
