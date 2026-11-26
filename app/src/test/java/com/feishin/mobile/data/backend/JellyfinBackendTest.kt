package com.feishin.mobile.data.backend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class JellyfinBackendTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun testProfile(
        baseUrl: String,
        id: String = "jf1",
        name: String = "Home Jellyfin",
        userId: String = "u123",
    ): ServerProfile = ServerProfile(
        id = id,
        name = name,
        type = BackendType.JELLYFIN,
        url = baseUrl,
        userId = userId,
        credentials = ProfileCredentials.Jellyfin(
            username = "alice",
            accessToken = "tok-abc",
            serverId = "srv-1",
            deviceId = "dev-1",
        ),
    )

    private fun createApi(baseUrl: String): JellyfinApi =
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JellyfinApi::class.java)

    private fun newSettingsStore(scope: CoroutineScope): SettingsStore =
        SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )

    private fun newBackend(
        baseUrl: String,
        scope: CoroutineScope,
        settings: SettingsStore = newSettingsStore(scope),
        cacheDao: FakeCacheDao = FakeCacheDao(),
        refresh: LibraryRefresh = LibraryRefresh(),
        profile: ServerProfile = testProfile(baseUrl),
    ): Triple<JellyfinBackend, MetadataCache, LibraryRefresh> {
        val cache = MetadataCache(cacheDao)
        val backend = JellyfinBackend(
            profile = profile,
            api = createApi(baseUrl),
            cache = cache,
            refresh = refresh,
            network = null,
            settings = settings,
            offlineSync = dagger.Lazy { error("unused") },
            playlistEvents = dagger.Lazy { error("unused") },
            cacheScope = scope,
        )
        return Triple(backend, cache, refresh)
    }

    @Test
    fun pingAndServerInfoMapPublicSystemInfoCleanly() = runTest {
        LocalHttpServer { req ->
            if (req.path == "/System/Info/Public") {
                200 to """{"Version":"10.9.11","ProductName":"","Id":"srv-1","ServerName":"MusicBox"}"""
            } else {
                404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            assertTrue(backend.ping() is Result.Ok)

            val info = backend.serverInfo()
            assertTrue(info is Result.Ok)
            val data = (info as Result.Ok).value
            assertEquals("Jellyfin", data.product)
            assertEquals("10.9.11", data.apiVersion)
            assertTrue(data.extensions.isEmpty())

            val scanStatus = backend.scanStatus()
            assertTrue(scanStatus is Result.Ok)
            assertEquals(false, (scanStatus as Result.Ok).value.scanning)
        }
    }

    @Test
    fun startScanPostsToLibraryRefresh() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            recorded += req
            when (req.path) {
                "/Library/Refresh" -> 204 to ""
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.startScan()
            assertTrue(res is Result.Ok)
            assertEquals(1, recorded.size)
            assertEquals("POST", recorded[0].method)
            assertEquals("/Library/Refresh", recorded[0].path)
        }
    }

    @Test
    fun albumListNewestUsesLatestOnPageZeroAndPagedItemsOnOffsetGreaterThanZero() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            recorded += req
            when (req.path) {
                "/Users/u123/Items/Latest" -> 200 to """
                    [
                      {
                        "Id": "alb-latest",
                        "Name": "Latest Album",
                        "Type": "MusicAlbum",
                        "AlbumArtist": "Artist One",
                        "AlbumArtists": [{"Name": "Artist One", "Id": "art-1"}],
                        "Genres": ["Electronic"],
                        "ImageTags": {"Primary": "img-1"}
                      }
                    ]
                """.trimIndent()
                "/Users/u123/Items" -> 200 to """
                    {
                      "Items": [
                        {
                          "Id": "alb-page2",
                          "Name": "Older Album",
                          "Type": "MusicAlbum",
                          "AlbumArtist": "Artist Two",
                          "AlbumArtists": [{"Name": "Artist Two", "Id": "art-2"}],
                          "Genres": ["Ambient"]
                        }
                      ],
                      "TotalRecordCount": 2,
                      "StartIndex": 25
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)

            val page0 = backend.albumList(type = "newest", size = 25, offset = 0)
            assertTrue(page0 is Result.Ok)
            val albums0 = (page0 as Result.Ok).value
            assertEquals(1, albums0.size)
            assertEquals("jf1:alb-latest", albums0[0].id)
            assertEquals("jf1", albums0[0].sourceProfileId)
            assertEquals("Home Jellyfin", albums0[0].sourceLabel)

            val page1 = backend.albumList(type = "newest", size = 25, offset = 25)
            assertTrue(page1 is Result.Ok)
            val albums1 = (page1 as Result.Ok).value
            assertEquals(1, albums1.size)
            assertEquals("jf1:alb-page2", albums1[0].id)

            assertEquals("/Users/u123/Items/Latest", recorded[0].path)
            assertEquals("MusicAlbum", recorded[0].queryParams()["IncludeItemTypes"])
            assertEquals("Genres", recorded[0].queryParams()["Fields"])
            assertEquals("25", recorded[0].queryParams()["Limit"])

            assertEquals("/Users/u123/Items", recorded[1].path)
            assertEquals("MusicAlbum", recorded[1].queryParams()["IncludeItemTypes"])
            assertEquals("DateCreated", recorded[1].queryParams()["SortBy"])
            assertEquals("Descending", recorded[1].queryParams()["SortOrder"])
            assertEquals("25", recorded[1].queryParams()["StartIndex"])
            assertEquals("25", recorded[1].queryParams()["Limit"])
        }
    }

    @Test
    fun albumListByGenreRecentAndRandomApplyExpectedParametersAndCacheRules() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        var randomCalls = 0
        LocalHttpServer { req ->
            recorded += req
            val params = req.queryParams()
            when {
                req.path == "/Users/u123/Items" && params["Genres"] == "Jazz" -> 200 to """
                    {
                      "Items": [
                        {"Id": "alb-jazz", "Name": "Kind of Blue", "Type": "MusicAlbum", "Genres": ["Jazz"]}
                      ]
                    }
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["SortBy"] == "DatePlayed" && params["Filters"] == "IsPlayed" ->
                    200 to """{"Items": []}"""
                req.path == "/Users/u123/Items" && params["SortBy"] == "DatePlayed" -> 200 to """
                    {
                      "Items": [
                        {"Id": "alb-fallback", "Name": "Fallback Album", "Type": "MusicAlbum"}
                      ]
                    }
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["SortBy"] == "Random" -> {
                    randomCalls++
                    200 to """
                        {
                          "Items": [
                            {"Id": "alb-rand-$randomCalls", "Name": "Random $randomCalls", "Type": "MusicAlbum"}
                          ]
                        }
                    """.trimIndent()
                }
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)

            val byGenre = backend.albumList(type = "byGenre", size = 30, offset = 0, genre = "Jazz")
            assertTrue(byGenre is Result.Ok)
            assertEquals(listOf("jf1:alb-jazz"), (byGenre as Result.Ok).value.map { it.id })
            assertEquals("Jazz", recorded[0].queryParams()["Genres"])
            assertEquals("SortName", recorded[0].queryParams()["SortBy"])
            assertEquals("Ascending", recorded[0].queryParams()["SortOrder"])

            val recent = backend.albumList(type = "recent", size = 10, offset = 0)
            assertTrue(recent is Result.Ok)
            assertEquals(listOf("jf1:alb-fallback"), (recent as Result.Ok).value.map { it.id })

            val rand1 = backend.albumList(type = "random", size = 5, offset = 0)
            val rand2 = backend.albumList(type = "random", size = 5, offset = 0)
            assertEquals(listOf("jf1:alb-rand-1"), (rand1 as Result.Ok).value.map { it.id })
            assertEquals(listOf("jf1:alb-rand-2"), (rand2 as Result.Ok).value.map { it.id })
            assertEquals(2, randomCalls)
        }
    }

    @Test
    fun playlistsFiltersOutVideoPlaylistsAndPreservesAudioPlaylists() = runTest {
        LocalHttpServer { req ->
            if (req.path == "/Users/u123/Items") {
                200 to """
                    {
                      "Items": [
                        {"Id": "pl-audio", "Name": "Roadtrip", "Type": "Playlist", "MediaType": "Audio", "ChildCount": 8},
                        {"Id": "pl-video", "Name": "Movies", "Type": "Playlist", "MediaType": "Video", "ChildCount": 3},
                        {"Id": "pl-null", "Name": "Mixed", "Type": "Playlist", "ChildCount": 2}
                      ]
                    }
                """.trimIndent()
            } else {
                404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.playlists()
            assertTrue(res is Result.Ok)
            val playlists = (res as Result.Ok).value
            assertEquals(1, playlists.size)
            assertEquals("jf1:pl-audio", playlists[0].id)
            assertEquals("Roadtrip", playlists[0].name)
            assertEquals(8, playlists[0].songCount)
        }
    }

    @Test
    fun albumDetailSortsMultiDiscTracksAndConvertsBitrateToKbpsWithGlobalIds() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            recorded += req
            when (req.path) {
                "/Users/u123/Items/alb-9" -> 200 to """
                    {
                      "Id": "alb-9",
                      "Name": "Double LP",
                      "Type": "MusicAlbum",
                      "AlbumArtist": "Artist X",
                      "AlbumArtists": [{"Name": "Artist X", "Id": "art-x"}],
                      "ImageTags": {"Primary": "tag-9"}
                    }
                """.trimIndent()
                "/Users/u123/Items" -> 200 to """
                    {
                      "Items": [
                        {
                          "Id": "t-2-1",
                          "Name": "Disc 2 Track 1",
                          "Type": "Audio",
                          "ParentIndexNumber": 2,
                          "IndexNumber": 1,
                          "AlbumId": "alb-9",
                          "RunTimeTicks": 1800000000,
                          "MediaSources": [{"Id": "t-2-1", "Container": "mp3", "Bitrate": 320000}]
                        },
                        {
                          "Id": "t-1-2",
                          "Name": "Disc 1 Track 2",
                          "Type": "Audio",
                          "ParentIndexNumber": 1,
                          "IndexNumber": 2,
                          "AlbumId": "alb-9",
                          "RunTimeTicks": 2400000000,
                          "MediaSources": [{"Id": "t-1-2", "Container": "flac", "Bitrate": 1411200}]
                        },
                        {
                          "Id": "t-1-1",
                          "Name": "Disc 1 Track 1",
                          "Type": "Audio",
                          "ParentIndexNumber": 1,
                          "IndexNumber": 1,
                          "AlbumId": "alb-9",
                          "RunTimeTicks": 1500000000,
                          "MediaSources": [{"Id": "t-1-1", "Container": "flac", "Bitrate": 950000}]
                        }
                      ]
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.album("jf1:alb-9")
            assertTrue(res is Result.Ok)
            val detail = (res as Result.Ok).value
            assertEquals("jf1:alb-9", detail.id)
            assertEquals(listOf("jf1:t-1-1", "jf1:t-1-2", "jf1:t-2-1"), detail.tracks.map { it.id })
            assertEquals(950, detail.tracks[0].bitRate)
            assertEquals(1411, detail.tracks[1].bitRate)
            assertEquals(320, detail.tracks[2].bitRate)
            assertEquals("jf1", detail.tracks[0].sourceProfileId)
            assertEquals("Home Jellyfin", detail.tracks[0].sourceLabel)

            val trackQuery = recorded[1].queryParams()
            assertEquals("alb-9", trackQuery["ParentId"])
            assertEquals("ParentIndexNumber,IndexNumber", trackQuery["SortBy"])
            assertEquals("Audio", trackQuery["IncludeItemTypes"])
        }
    }

    @Test
    fun similarSongsCallsInstantMixWithUnwrappedRawId() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            recorded += req
            when (req.path) {
                "/Items/trk-55/InstantMix" -> 200 to """
                    {
                      "Items": [
                        {
                          "Id": "mix-1",
                          "Name": "Mix Song",
                          "Type": "Audio",
                          "RunTimeTicks": 2000000000,
                          "MediaSources": [{"Container": "opus", "Bitrate": 192000}]
                        }
                      ]
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.similarSongs(songId = "jf1:trk-55", artistId = "jf1:art-1", count = 15)
            assertTrue(res is Result.Ok)
            val tracks = (res as Result.Ok).value
            assertEquals(1, tracks.size)
            assertEquals("jf1:mix-1", tracks[0].id)
            assertEquals(192, tracks[0].bitRate)
            assertEquals("/Items/trk-55/InstantMix", recorded[0].path)
            assertEquals("u123", recorded[0].queryParams()["UserId"])
            assertEquals("15", recorded[0].queryParams()["Limit"])
        }
    }

    @Test
    fun searchFansOutParallelRequestsAndPrefixesGlobalIds() = runTest {
        LocalHttpServer { req ->
            val params = req.queryParams()
            when {
                req.path == "/Artists/AlbumArtists" -> 200 to """
                    {"Items": [{"Id": "ar-1", "Name": "Daft Punk"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["IncludeItemTypes"] == "MusicAlbum" -> 200 to """
                    {"Items": [{"Id": "al-1", "Name": "Discovery", "Type": "MusicAlbum"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["IncludeItemTypes"] == "Audio" -> 200 to """
                    {"Items": [{"Id": "tr-1", "Name": "Digital Love", "Type": "Audio", "MediaSources": [{"Bitrate": 256000}]}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["IncludeItemTypes"] == "Playlist" -> 200 to """
                    {
                      "Items": [
                        {"Id": "pl-1", "Name": "Daft Mix", "Type": "Playlist", "MediaType": "Audio"},
                        {"Id": "pl-v", "Name": "Daft Videos", "Type": "Playlist", "MediaType": "Video"}
                      ]
                    }
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.search("Daft", artistCount = 5, albumCount = 5, songCount = 10)
            assertTrue(res is Result.Ok)
            val results = (res as Result.Ok).value
            assertEquals(listOf("jf1:ar-1"), results.artists.map { it.id })
            assertEquals(listOf("jf1:al-1"), results.albums.map { it.id })
            assertEquals(listOf("jf1:tr-1"), results.tracks.map { it.id })
            assertEquals(listOf(256), results.tracks.map { it.bitRate })
            assertEquals(listOf("jf1:pl-1"), results.playlists.map { it.id })
        }
    }

    @Test
    fun starredSplitsFavoritesByItemTypeAndWritesScopedStaleCache() = runTest {
        val dao = FakeCacheDao()
        LocalHttpServer { req ->
            if (req.path == "/Users/u123/Items" && req.queryParams()["Filters"] == "IsFavorite") {
                200 to """
                    {
                      "Items": [
                        {"Id": "ar-fav", "Name": "Fav Artist", "Type": "MusicArtist"},
                        {"Id": "al-fav", "Name": "Fav Album", "Type": "MusicAlbum"},
                        {"Id": "tr-fav", "Name": "Fav Track", "Type": "Audio", "MediaSources": [{"Bitrate": 320000}]}
                      ]
                    }
                """.trimIndent()
            } else {
                404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope, cacheDao = dao)
            val res = backend.starred()
            assertTrue(res is Result.Ok)
            val starred = (res as Result.Ok).value
            assertEquals(listOf("jf1:ar-fav"), starred.artists.map { it.id })
            assertEquals(listOf("jf1:al-fav"), starred.albums.map { it.id })
            assertEquals(listOf("jf1:tr-fav"), starred.tracks.map { it.id })

            val cached = backend.readCachedStarred()
            assertNotNull(cached)
            assertEquals(starred, cached)
            assertNotNull(dao.get(MetadataCacheKeys.scoped("jf1", MetadataCacheKeys.STARRED)))
        }
    }

    @Test
    fun genresArtistsTopSongsAllSongsGetSongAndUrlsWorkEndToEnd() = runTest {
        LocalHttpServer { req ->
            val params = req.queryParams()
            when {
                req.path == "/MusicGenres" -> 200 to """
                    {"Items": [{"Name": "Electronic", "SongCount": 40, "AlbumCount": 5}]}
                """.trimIndent()
                req.path == "/Artists/AlbumArtists" && params["SearchTerm"] == "Daft Punk" -> 200 to """
                    {"Items": [{"Id": "art-dp", "Name": "Daft Punk"}]}
                """.trimIndent()
                req.path == "/Artists/AlbumArtists" -> 200 to """
                    {"Items": [{"Id": "art-dp", "Name": "Daft Punk", "AlbumCount": 4, "ImageTags": {"Primary": "tag-art"}}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["ArtistIds"] == "art-dp" -> 200 to """
                    {"Items": [{"Id": "top-1", "Name": "Harder Better", "Type": "Audio"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["IncludeItemTypes"] == "Audio" -> 200 to """
                    {"Items": [{"Id": "all-1", "Name": "Track A", "Type": "Audio"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items/all-1" -> 200 to """
                    {"Id": "all-1", "Name": "Track A", "Type": "Audio", "AlbumId": "alb-1"}
                """.trimIndent()
                req.path == "/Users/u123/Items/art-dp" -> 200 to """
                    {"Id": "art-dp", "Name": "Daft Punk", "Overview": "French duo", "ImageTags": {"Primary": "tag-art"}}
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)

            val genres = (backend.genres() as Result.Ok).value
            assertEquals("Electronic", genres[0].name)
            assertEquals(40, genres[0].songCount)
            assertEquals(5, genres[0].albumCount)

            val artists = (backend.artists() as Result.Ok).value
            assertEquals("jf1:art-dp", artists[0].id)

            val topSongs = (backend.topSongs("Daft Punk", 5) as Result.Ok).value
            assertEquals(listOf("jf1:top-1"), topSongs.map { it.id })

            val allSongs = (backend.allSongs(10, 0) as Result.Ok).value
            assertEquals(listOf("jf1:all-1"), allSongs.map { it.id })

            val song = (backend.getSong("jf1:all-1") as Result.Ok).value
            assertEquals("jf1:all-1", song.id)
            assertEquals("jf1:alb-1", song.coverArt)

            val info = (backend.artistInfo("jf1:art-dp") as Result.Ok).value
            assertEquals("French duo", info.biography)
            assertEquals(
                "${server.baseUrl}/Items/art-dp/Images/Primary?maxWidth=512&quality=90",
                info.imageUrl,
            )

            assertEquals(
                "${server.baseUrl}/Audio/all-1/stream?static=true",
                backend.streamUrl(song, maxBitrate = 0, format = "raw"),
            )
            assertEquals(
                "${server.baseUrl}/Items/all-1/Download",
                backend.downloadUrl(song),
            )
            assertNull(backend.coverArtUrl(null))
        }
    }

    @Test
    fun topSongsResolvesUncachedArtistThenFallsBackToSearchTermAndPlaylistMapsDetail() = runTest {
        val recorded = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            recorded += req
            val params = req.queryParams()
            when {
                req.path == "/Artists/AlbumArtists" && params["SearchTerm"] == "Matched Artist" -> 200 to """
                    {"Items": [{"Id": "art-matched", "Name": "matched artist"}]}
                """.trimIndent()
                req.path == "/Artists/AlbumArtists" && params["SearchTerm"] == "Missing Artist" -> 200 to """
                    {"Items": []}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["ArtistIds"] == "art-matched" -> 200 to """
                    {"Items": [{"Id": "t-match", "Name": "Top Hit", "Type": "Audio"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items" && params["SearchTerm"] == "Missing Artist" -> 200 to """
                    {"Items": [{"Id": "t-fallback", "Name": "Fallback Hit", "Type": "Audio"}]}
                """.trimIndent()
                req.path == "/Users/u123/Items/pl-1" -> 200 to """
                    {"Id": "pl-1", "Name": "Mix", "Type": "Playlist", "MediaType": "Audio"}
                """.trimIndent()
                req.path == "/Playlists/pl-1/Items" -> 200 to """
                    {"Items": [{"Id": "t-pl", "Name": "Playlist Song", "Type": "Audio", "PlaylistItemId": "entry-1"}]}
                """.trimIndent()
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)

            val matched = (backend.topSongs("Matched Artist", 3) as Result.Ok).value
            assertEquals(listOf("jf1:t-match"), matched.map { it.id })

            val fallback = (backend.topSongs("Missing Artist", 3) as Result.Ok).value
            assertEquals(listOf("jf1:t-fallback"), fallback.map { it.id })

            val pl = (backend.playlist("jf1:pl-1") as Result.Ok).value
            assertEquals("jf1:pl-1", pl.id)
            assertEquals("Mix", pl.name)
            assertEquals(listOf("jf1:t-pl"), pl.tracks.map { it.id })
            assertEquals(pl, backend.cachedPlaylist("jf1:pl-1"))
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: String,
    ) {
        fun queryParams(): Map<String, String> {
            if (query.isBlank()) return emptyMap()
            return query.split('&').mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null
                else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }.toMap()
        }
    }

    private class LocalHttpServer(
        private val handler: (RecordedRequest) -> Pair<Int, String>,
    ) : Closeable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        val baseUrl: String = "http://127.0.0.1:$port"

        @Volatile
        private var closed = false

        private val worker = Thread {
            while (!closed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: Throwable) {
                    break
                }
                client.use { conn ->
                    val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine() ?: return@use
                    val parts = requestLine.split(' ')
                    val method = parts.getOrElse(0) { "GET" }
                    val target = parts.getOrElse(1) { "/" }
                    val path = target.substringBefore('?')
                    val query = target.substringAfter('?', "")
                    val headers = linkedMapOf<String, String>()
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            val name = line.substring(0, sep).trim().lowercase()
                            val value = line.substring(sep + 1).trim()
                            headers[name] = value
                            if (name == "content-length") {
                                contentLength = value.toIntOrNull() ?: 0
                            }
                        }
                    }
                    val body = if (contentLength > 0) {
                        val chars = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(chars, read, contentLength - read)
                            if (n < 0) break
                            read += n
                        }
                        String(chars, 0, read)
                    } else {
                        ""
                    }

                    val (status, respBody) = handler(RecordedRequest(method, path, query, headers, body))
                    val bytes = if (status == 204) ByteArray(0) else respBody.toByteArray(Charsets.UTF_8)
                    val reason = when (status) {
                        200 -> "OK"
                        204 -> "No Content"
                        401 -> "Unauthorized"
                        404 -> "Not Found"
                        else -> "Status"
                    }
                    val out = conn.getOutputStream()
                    val head = buildString {
                        append("HTTP/1.1 $status $reason\r\n")
                        if (status != 204) {
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                        }
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(head.toByteArray(Charsets.US_ASCII))
                    if (bytes.isNotEmpty()) {
                        out.write(bytes)
                    }
                    out.flush()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            closed = true
            runCatching { socket.close() }
            runCatching { worker.join(1_000) }
        }
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
}
