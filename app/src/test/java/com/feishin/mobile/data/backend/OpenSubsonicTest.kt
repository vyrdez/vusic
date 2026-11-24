package com.feishin.mobile.data.backend

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.repo.NavidromeLyricsProvider
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.OpenSubsonicExtensions
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
import com.feishin.mobile.playback.RadioFetcher
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class OpenSubsonicTest {

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

    private fun testProfile(
        id: String = "p1",
        extensions: OpenSubsonicExtensions = OpenSubsonicExtensions(),
    ): ServerProfile = ServerProfile(
        id = id,
        name = "OpenSubsonic Test",
        type = BackendType.OPENSUBSONIC,
        url = "https://music.example.com",
        credentials = ProfileCredentials.Subsonic("alice", "secret"),
        openSubsonicExtensions = extensions,
    )

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

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val formBody: String?,
    )

    private fun createApi(
        handler: (RecordedRequest) -> Pair<Int, String>,
    ): Pair<SubsonicApi, List<RecordedRequest>> {
        val recorded = mutableListOf<RecordedRequest>()
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val bodyStr = req.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
            val rec = RecordedRequest(
                method = req.method,
                path = req.url.encodedPath,
                query = req.url.encodedQuery,
                formBody = bodyStr,
            )
            recorded += rec
            val (code, jsonBody) = handler(rec)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(jsonBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val api = Retrofit.Builder()
            .baseUrl("https://music.example.com/rest/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
        return api to recorded
    }

    private fun newBackend(
        profile: ServerProfile,
        api: SubsonicApi,
        profileStore: ProfileStore,
        settings: SettingsStore,
        scope: CoroutineScope,
    ): SubsonicBackend = SubsonicBackend(
        profile = profile,
        api = api,
        cache = MetadataCache(FakeCacheDao()),
        refresh = LibraryRefresh(),
        network = null,
        settings = settings,
        offlineSync = dagger.Lazy { error("unused") },
        playlistEvents = dagger.Lazy { error("unused") },
        cacheScope = scope,
        profileStore = profileStore,
    )

    @Test
    fun ping_discoversOpenSubsonicExtensionsAndPersistsToProfileStore() = runTest {
        val profile = testProfile()
        val (profileStore, settings) = newProfileStore(backgroundScope, listOf(profile))
        val (api, _) = createApi { req ->
            when {
                req.path.endsWith("ping.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                req.path.endsWith("getOpenSubsonicExtensions.view") ->
                    200 to """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[{"name":"songLyrics"},{"name":"formPost"},{"name":"transcodeOffset"},{"name":"apiKeyAuthentication"}]}}"""
                else -> 404 to "{}"
            }
        }
        val backend = newBackend(profile, api, profileStore, settings, backgroundScope)

        val res = backend.ping()
        assertTrue(res is Result.Ok)
        val persisted = profileStore.byId("p1")?.openSubsonicExtensions
        assertEquals(
            OpenSubsonicExtensions(
                songLyrics = true,
                transcodeOffset = true,
                formPost = true,
                apiKeyAuthentication = true,
                rawNames = listOf("songLyrics", "formPost", "transcodeOffset", "apiKeyAuthentication"),
            ),
            persisted,
        )
    }

    @Test
    fun ping_tolerates404OnGetOpenSubsonicExtensions() = runTest {
        val profile = testProfile()
        val (profileStore, settings) = newProfileStore(backgroundScope, listOf(profile))
        val (api, _) = createApi { req ->
            when {
                req.path.endsWith("ping.view") ->
                    200 to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                req.path.endsWith("getOpenSubsonicExtensions.view") ->
                    404 to """{"error":"Not Found"}"""
                else -> 404 to "{}"
            }
        }
        val backend = newBackend(profile, api, profileStore, settings, backgroundScope)

        val res = backend.ping()
        assertTrue(res is Result.Ok)
        val persisted = profileStore.byId("p1")?.openSubsonicExtensions
        assertEquals(OpenSubsonicExtensions(), persisted)
    }

    @Test
    fun addToPlaylist_usesSinglePostWhenFormPostSupported_andChunkedGetWhenUnsupported() = runTest {
        val trackIds = (1..150).map { "p1:t$it" }

        // 1. formPost == true -> single POST
        val postProfile = testProfile(
            extensions = OpenSubsonicExtensions(formPost = true, rawNames = listOf("formPost")),
        )
        val (postStore, postSettings) = newProfileStore(backgroundScope, listOf(postProfile))
        val (postApi, postReqs) = createApi { _ ->
            200 to """{"subsonic-response":{"status":"ok"}}"""
        }
        val postBackend = newBackend(postProfile, postApi, postStore, postSettings, backgroundScope)
        val postRes = postBackend.addToPlaylist("p1:pl1", trackIds)
        assertTrue(postRes is Result.Ok)
        assertEquals(1, postReqs.size)
        assertEquals("POST", postReqs.single().method)
        assertTrue(postReqs.single().formBody!!.contains("playlistId=pl1"))
        assertTrue(postReqs.single().formBody!!.contains("songIdToAdd=t150"))

        // 2. formPost == false -> chunked GET (100 + 50)
        val getProfile = testProfile(
            extensions = OpenSubsonicExtensions(formPost = false, rawNames = listOf("songLyrics")),
        )
        val (getStore, getSettings) = newProfileStore(backgroundScope, listOf(getProfile))
        val (getApi, getReqs) = createApi { _ ->
            200 to """{"subsonic-response":{"status":"ok"}}"""
        }
        val getBackend = newBackend(getProfile, getApi, getStore, getSettings, backgroundScope)
        val getRes = getBackend.addToPlaylist("p1:pl1", trackIds)
        assertTrue(getRes is Result.Ok)
        assertEquals(2, getReqs.size)
        assertTrue(getReqs.all { it.method == "GET" })
    }

    @Test
    fun removeFromPlaylist_usesPostWhenFormPostSupported_andGetWhenUnsupported() = runTest {
        val postProfile = testProfile(
            extensions = OpenSubsonicExtensions(formPost = true, rawNames = listOf("formPost")),
        )
        val (postStore, postSettings) = newProfileStore(backgroundScope, listOf(postProfile))
        val (postApi, postReqs) = createApi { _ ->
            200 to """{"subsonic-response":{"status":"ok"}}"""
        }
        val postBackend = newBackend(postProfile, postApi, postStore, postSettings, backgroundScope)
        assertTrue(postBackend.removeFromPlaylist("p1:pl1", listOf(0, 2)) is Result.Ok)
        assertEquals("POST", postReqs.single().method)
        assertTrue(postReqs.single().formBody!!.contains("songIndexToRemove=2"))

        val getProfile = testProfile(
            extensions = OpenSubsonicExtensions(formPost = false, rawNames = listOf("songLyrics")),
        )
        val (getStore, getSettings) = newProfileStore(backgroundScope, listOf(getProfile))
        val (getApi, getReqs) = createApi { _ ->
            200 to """{"subsonic-response":{"status":"ok"}}"""
        }
        val getBackend = newBackend(getProfile, getApi, getStore, getSettings, backgroundScope)
        assertTrue(getBackend.removeFromPlaylist("p1:pl1", listOf(0, 2)) is Result.Ok)
        assertEquals("GET", getReqs.single().method)
    }

    @Test
    fun navidromeLyricsProvider_songLyrics_prefersStructuredLyricsAndFallsBackToGetLyrics() = runTest {
        val profile = testProfile(
            extensions = OpenSubsonicExtensions(songLyrics = true, rawNames = listOf("songLyrics")),
        )
        val (store, _) = newProfileStore(backgroundScope, listOf(profile))

        // Case A: getLyricsBySongId returns structuredLyrics
        val (apiA, reqsA) = createApi { req ->
            when {
                req.path.endsWith("getLyricsBySongId.view") ->
                    200 to """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[{"synced":true,"lang":"en","offset":0,"line":[{"start":1000,"value":"Line one"}]}]}}}"""
                else -> 404 to "{}"
            }
        }
        val providerA = NavidromeLyricsProvider(profile, apiA, store)
        val resA = providerA.lyricsForSong("p1:s1", "Song", "Artist", null, 180_000L, null, 0L)
        assertTrue(resA is Result.Ok)
        val lyricsA = (resA as Result.Ok).value
        assertEquals(LyricsKind.SYNCED, lyricsA.kind)
        assertEquals("Line one", lyricsA.lines.single().text)
        assertTrue(reqsA.none { it.path.endsWith("getOpenSubsonicExtensions.view") })

        // Case B: getLyricsBySongId returns empty -> falls back to getLyrics
        val (apiB, reqsB) = createApi { req ->
            when {
                req.path.endsWith("getLyricsBySongId.view") ->
                    200 to """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[]}}}"""
                req.path.endsWith("getLyrics.view") ->
                    200 to """{"subsonic-response":{"status":"ok","lyrics":{"value":"Fallback plain lyrics"}}}"""
                else -> 404 to "{}"
            }
        }
        val providerB = NavidromeLyricsProvider(profile, apiB, store)
        val resB = providerB.lyricsForSong("p1:s1", "Song", "Artist", null, 180_000L, null, 0L)
        assertTrue(resB is Result.Ok)
        assertEquals("Fallback plain lyrics", (resB as Result.Ok).value.plainText)
        assertTrue(reqsB.any { it.path.endsWith("getLyricsBySongId.view") })
        assertTrue(reqsB.any { it.path.endsWith("getLyrics.view") })

        // Case C: songLyrics == false -> skips getLyricsBySongId, calls getLyrics directly
        val legacyProfile = testProfile(
            extensions = OpenSubsonicExtensions(songLyrics = false, rawNames = listOf("formPost")),
        )
        val (legacyStore, _) = newProfileStore(backgroundScope, listOf(legacyProfile))
        val (apiC, reqsC) = createApi { req ->
            when {
                req.path.endsWith("getLyrics.view") ->
                    200 to """{"subsonic-response":{"status":"ok","lyrics":{"value":"[00:01.00]Synced LRC"}}}"""
                else -> 404 to "{}"
            }
        }
        val providerC = NavidromeLyricsProvider(legacyProfile, apiC, legacyStore)
        val resC = providerC.lyricsForSong("p1:s1", "Song", "Artist", null, 180_000L, null, 0L)
        assertTrue(resC is Result.Ok)
        assertEquals(LyricsKind.SYNCED, (resC as Result.Ok).value.kind)
        assertFalse(reqsC.any { it.path.endsWith("getLyricsBySongId.view") })
    }

    @Test
    fun similarSongs_fallsBackToGenreRandomSongs_thenPlainRandomSongs() = runTest {
        val profile = testProfile()
        val (store, settings) = newProfileStore(backgroundScope, listOf(profile))

        // 1. Empty getSimilarSongs2 -> getSong resolves genre -> getRandomSongs(genre)
        val (apiGenre, reqsGenre) = createApi { req ->
            when {
                req.path.endsWith("getSimilarSongs2.view") ->
                    200 to """{"subsonic-response":{"status":"ok","similarSongs2":{"song":[]}}}"""
                req.path.endsWith("getSong.view") ->
                    200 to """{"subsonic-response":{"status":"ok","song":{"id":"s1","title":"Seed","artist":"A","album":"Al","genre":"Post-Rock"}}}"""
                req.path.endsWith("getRandomSongs.view") && req.query?.contains("genre=Post-Rock") == true ->
                    200 to """{"subsonic-response":{"status":"ok","randomSongs":{"song":[{"id":"s2","title":"Genre Track","artist":"B","album":"Bl","duration":200}]}}}"""
                else -> 404 to "{}"
            }
        }
        val backendGenre = newBackend(profile, apiGenre, store, settings, backgroundScope)
        val genreRes = backendGenre.similarSongs(songId = "p1:s1", artistId = null, count = 20)
        assertTrue(genreRes is Result.Ok)
        val genreTracks = (genreRes as Result.Ok).value
        assertEquals(1, genreTracks.size)
        assertEquals(GlobalId.format("p1", "s2"), genreTracks.single().id)
        assertEquals("p1", genreTracks.single().sourceProfileId)
        assertEquals(profile.name, genreTracks.single().sourceLabel)
        assertTrue(reqsGenre.any { it.path.endsWith("getSong.view") })

        // 2. Empty getSimilarSongs2 + empty getRandomSongs(genre) -> plain getRandomSongs(null)
        val (apiPlain, reqsPlain) = createApi { req ->
            when {
                req.path.endsWith("getSimilarSongs2.view") ->
                    200 to """{"subsonic-response":{"status":"ok","similarSongs2":{"song":[]}}}"""
                req.path.endsWith("getSong.view") ->
                    200 to """{"subsonic-response":{"status":"ok","song":{"id":"s1","title":"Seed","genre":"Post-Rock"}}}"""
                req.path.endsWith("getRandomSongs.view") && req.query?.contains("genre=") == true ->
                    200 to """{"subsonic-response":{"status":"ok","randomSongs":{"song":[]}}}"""
                req.path.endsWith("getRandomSongs.view") ->
                    200 to """{"subsonic-response":{"status":"ok","randomSongs":{"song":[{"id":"s3","title":"Random Track","artist":"C","album":"Cl","duration":180}]}}}"""
                else -> 404 to "{}"
            }
        }
        val backendPlain = newBackend(profile, apiPlain, store, settings, backgroundScope)
        val plainRes = backendPlain.similarSongs(songId = "p1:s1", artistId = null, count = 20)
        assertTrue(plainRes is Result.Ok)
        assertEquals(GlobalId.format("p1", "s3"), (plainRes as Result.Ok).value.single().id)
        assertEquals(2, reqsPlain.count { it.path.endsWith("getRandomSongs.view") })
    }

    @Test
    fun radioFetcher_continuesThroughGenreAndPlainRandomWhenSimilarSongsReturnsEmptyOk() = runTest {
        val fakeRepo = object : FakeMusicDataRepository() {
            override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> =
                Result.Ok(emptyList())

            override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> =
                if (genre == "Jazz") {
                    Result.Ok(listOf(Track(id = "p1:j1", title = "Jazz 1", album = "A", albumId = null, artist = "B", artistId = null, coverArt = null, durationSec = 120, trackNo = 1, year = null, genre = "Jazz", suffix = "flac", starred = false)))
                } else {
                    Result.Ok(listOf(Track(id = "p1:r1", title = "Rand 1", album = "A", albumId = null, artist = "B", artistId = null, coverArt = null, durationSec = 120, trackNo = 1, year = null, genre = null, suffix = "flac", starred = false)))
                }
        }
        val fetcher = RadioFetcher(fakeRepo)
        val seed = Track(
            id = "p1:seed",
            title = "Seed",
            album = "Al",
            albumId = null,
            artist = "Artist",
            artistId = "p1:ar1",
            coverArt = null,
            durationSec = 180,
            trackNo = 1,
            year = null,
            genre = "Jazz",
            suffix = "flac",
            starred = false,
        )
        val batch = fetcher.fetchBatch(seed, excludeIds = emptySet(), want = 5)
        assertTrue(batch is Result.Ok)
        val ids = (batch as Result.Ok).value.map { it.id }.toSet()
        assertTrue("p1:j1" in ids)
        assertTrue("p1:r1" in ids)
    }

    private class FakeCacheDao : MetadataCacheDao {
        private val map = LinkedHashMap<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(entry: CachedMetadataEntity) {
            map[entry.key] = entry.json
        }
        override suspend fun remove(key: String) {
            map.remove(key)
        }
        override suspend fun deleteByPrefix(prefix: String) {
            val keys = map.keys.filter { it.startsWith(prefix) }
            keys.forEach { map.remove(it) }
        }
        override suspend fun clear() {
            map.clear()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(pending)
            }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private open class FakeMusicDataRepository : MusicDataRepository {
        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<Album>())
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) =
            Result.Ok(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) =
            Result.Ok(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))
        override suspend fun serverInfo() = Result.Ok(ServerInfo("1.16.1", "Test", emptyList()))
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus(false, 0))
        override suspend fun album(id: String) = Result.Err("unused")
        override suspend fun artist(id: String) = Result.Err("unused")
        override suspend fun artistInfo(id: String) = Result.Err("unused")
        override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun playlist(id: String) = Result.Err("unused")
        override suspend fun starred() = Result.Ok(StarredCollection(emptyList(), emptyList(), emptyList()))
        override suspend fun starTrack(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Ok("pl1")
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean) = Result.Ok(Unit)
        override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Err("unused")
        override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
        override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
        override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
        override fun downloadUrl(track: Track) = ""
    }
}
