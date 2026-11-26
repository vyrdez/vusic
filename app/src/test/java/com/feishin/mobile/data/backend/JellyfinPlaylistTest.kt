package com.feishin.mobile.data.backend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.cache.MetadataCacheKeys
import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.db.PendingActionEntity
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.remote.jellyfin.CreateJellyfinPlaylistRequest
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.BackendRegistry
import com.feishin.mobile.domain.repo.MusicBackend
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class JellyfinPlaylistTest {

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
        userId: String = "u123",
    ): ServerProfile = ServerProfile(
        id = id,
        name = "Home Jellyfin",
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
        offlineSync: OfflineSyncStore? = null,
        playlistEvents: PlaylistEvents = PlaylistEvents(),
        profile: ServerProfile = testProfile(baseUrl),
    ): Triple<JellyfinBackend, MetadataCache, PlaylistEvents> {
        val cache = MetadataCache(cacheDao)
        val backend = JellyfinBackend(
            profile = profile,
            api = createApi(baseUrl),
            cache = cache,
            refresh = refresh,
            network = null,
            settings = settings,
            offlineSync = dagger.Lazy { offlineSync ?: error("unused") },
            playlistEvents = dagger.Lazy { playlistEvents },
            cacheScope = scope,
        )
        return Triple(backend, cache, playlistEvents)
    }

    @Test
    fun duplicateTracksKeepDistinctPlaylistItemIdsAndRemoveTargetsExactEntryId() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val items = mutableListOf(
            """{"Id":"t-dup","Name":"Repeat Song","Type":"Audio","PlaylistItemId":"entry-1"}""",
            """{"Id":"t-dup","Name":"Repeat Song","Type":"Audio","PlaylistItemId":"entry-2"}""",
        )

        LocalHttpServer { req ->
            requests += req
            when {
                req.method == "GET" && req.path == "/Users/u123/Items/pl-1" -> 200 to """
                    {"Id":"pl-1","Name":"Dup Mix","Type":"Playlist","MediaType":"Audio"}
                """.trimIndent()
                req.method == "GET" && req.path == "/Playlists/pl-1/Items" ->
                    200 to """{"Items":[${items.joinToString(",")}]}"""
                req.method == "DELETE" && req.path == "/Playlists/pl-1/Items" -> {
                    val toRemove = req.queryParams()["EntryIds"]?.split(',').orEmpty().toSet()
                    items.removeAll { raw -> toRemove.any { id -> raw.contains("\"PlaylistItemId\":\"$id\"") } }
                    204 to ""
                }
                else -> 404 to "{}"
            }
        }.use { server ->
            val cacheDao = FakeCacheDao()
            val (backend, cache, _) = newBackend(server.baseUrl, backgroundScope, cacheDao = cacheDao)

            cache.write(
                MetadataCacheKeys.scoped("jf1", MetadataCacheKeys.PLAYLISTS),
                ListSerializer(Playlist.serializer()),
                listOf(Playlist("jf1:pl-1", "Dup Mix", 2, 0, null)),
            )

            val initial = (backend.playlist("jf1:pl-1") as Result.Ok).value
            assertEquals(listOf("jf1:t-dup", "jf1:t-dup"), initial.tracks.map { it.id })
            assertEquals(listOf("entry-1", "entry-2"), initial.tracks.map { it.playlistItemId })

            requests.clear()
            val removed = backend.removeFromPlaylist(
                playlistId = "jf1:pl-1",
                indexes = listOf(1),
                entryIds = listOfNotNull(initial.tracks[1].playlistItemId),
            )
            assertTrue(removed is Result.Ok)

            val deleteReq = requests.single()
            assertEquals("DELETE", deleteReq.method)
            assertEquals("/Playlists/pl-1/Items", deleteReq.path)
            assertEquals("entry-2", deleteReq.queryParams()["EntryIds"])

            assertNull(backend.cachedPlaylist("jf1:pl-1"))
            assertNull(cacheDao.get(MetadataCacheKeys.scoped("jf1", MetadataCacheKeys.PLAYLISTS)))

            val after = (backend.playlist("jf1:pl-1") as Result.Ok).value
            assertEquals(listOf("entry-1"), after.tracks.map { it.playlistItemId })
        }
    }

    @Test
    fun removeFromPlaylistTwoArgFallbackResolvesEntryIdByIndex() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            when {
                req.method == "GET" && req.path == "/Playlists/pl-1/Items" -> 200 to """
                    {"Items":[
                      {"Id":"t-dup","Name":"Repeat Song","Type":"Audio","PlaylistItemId":"entry-1"},
                      {"Id":"t-dup","Name":"Repeat Song","Type":"Audio","PlaylistItemId":"entry-2"}
                    ]}
                """.trimIndent()
                req.method == "DELETE" && req.path == "/Playlists/pl-1/Items" -> 204 to ""
                else -> 404 to "{}"
            }
        }.use { server ->
            val (backend, _, _) = newBackend(server.baseUrl, backgroundScope)
            val res = backend.removeFromPlaylist("jf1:pl-1", listOf(1))
            assertTrue(res is Result.Ok)

            val deleteReq = requests.last()
            assertEquals("DELETE", deleteReq.method)
            assertEquals("/Playlists/pl-1/Items", deleteReq.path)
            assertEquals("entry-2", deleteReq.queryParams()["EntryIds"])
        }
    }

    @Test
    fun playlistCrudCreateAddOptimisticAndDelete() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            when {
                req.method == "POST" && req.path == "/Playlists" -> 200 to """{"Id":"pl-new"}"""
                req.method == "POST" && req.path == "/Playlists/pl-new/Items" -> 204 to ""
                req.method == "DELETE" && req.path == "/Items/pl-new" -> 204 to ""
                else -> 404 to "{}"
            }
        }.use { server ->
            val cacheDao = FakeCacheDao()
            val (backend, cache, events) = newBackend(server.baseUrl, backgroundScope, cacheDao = cacheDao)

            val playlistsKey = MetadataCacheKeys.scoped("jf1", MetadataCacheKeys.PLAYLISTS)
            cache.write(
                playlistsKey,
                ListSerializer(Playlist.serializer()),
                listOf(Playlist("jf1:pl-old", "Old", 1, 100, null)),
            )

            val created = backend.createPlaylist("Roadtrip", listOf("jf1:t-1", "jf1:t-2"))
            assertEquals(Result.Ok("jf1:pl-new"), created)
            assertNull(cacheDao.get(playlistsKey))

            val createReq = requests.first()
            assertEquals("POST", createReq.method)
            assertEquals("/Playlists", createReq.path)
            val createBody = json.decodeFromString<CreateJellyfinPlaylistRequest>(createReq.body)
            assertEquals("Roadtrip", createBody.name)
            assertEquals(listOf("t-1", "t-2"), createBody.ids)
            assertEquals("u123", createBody.userId)
            assertEquals("Audio", createBody.mediaType)

            val detailKey = MetadataCacheKeys.scoped("jf1", MetadataCacheKeys.playlist("jf1:pl-new"))
            cache.write(
                detailKey,
                PlaylistDetail.serializer(),
                PlaylistDetail("jf1:pl-new", "Roadtrip", 2, 200, emptyList()),
            )
            val addRes = backend.addToPlaylist("jf1:pl-new", listOf("jf1:t-3", "jf1:t-4"))
            assertTrue(addRes is Result.Ok)
            assertNull(cacheDao.get(detailKey))

            val addReq = requests[1]
            assertEquals("POST", addReq.method)
            assertEquals("/Playlists/pl-new/Items", addReq.path)
            assertEquals("t-3,t-4", addReq.queryParams()["Ids"])
            assertEquals("u123", addReq.queryParams()["UserId"])

            // Optimistic add updates cache immediately and emits TracksAdded event
            cache.write(
                detailKey,
                PlaylistDetail.serializer(),
                PlaylistDetail("jf1:pl-new", "Roadtrip", 2, 200, emptyList()),
            )
            val nextEventDeferred = kotlinx.coroutines.CompletableDeferred<PlaylistEvent>()
            val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                nextEventDeferred.complete(events.events.first())
            }
            val addedTrack = Track(
                id = "jf1:t-5",
                title = "Five",
                album = "Alb",
                albumId = null,
                artist = "Art",
                artistId = null,
                coverArt = null,
                durationSec = 180,
                trackNo = 1,
                year = null,
                genre = null,
                suffix = "mp3",
                starred = false,
            )
            val optRes = backend.addToPlaylistOptimistic("jf1:pl-new", listOf(addedTrack))
            assertTrue(optRes is Result.Ok)
            assertEquals(3, backend.cachedPlaylist("jf1:pl-new")?.songCount)
            assertEquals(PlaylistEvent.TracksAdded("jf1:pl-new"), nextEventDeferred.await())
            collector.cancel()

            val delRes = backend.deletePlaylist("jf1:pl-new")
            assertTrue(delRes is Result.Ok)
            assertNull(backend.cachedPlaylist("jf1:pl-new"))
            assertEquals("DELETE", requests.last().method)
            assertEquals("/Items/pl-new", requests.last().path)
        }
    }

    @Test
    fun favoritesOnlineAndOfflineQueueReplayViaBackendRegistry() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            when {
                req.path.startsWith("/Users/u123/FavoriteItems/") -> 200 to "{}"
                else -> 404 to "{}"
            }
        }.use { server ->
            val settings = newSettingsStore(backgroundScope)
            val pendingDao = FakePendingActionDao()
            val registryMap = linkedMapOf<String, MusicBackend>()
            val registry = object : BackendRegistry {
                override fun all(): List<MusicBackend> = registryMap.values.toList()
                override fun enabled(): List<MusicBackend> = registryMap.values.toList()
                override fun get(profileId: String): MusicBackend? = registryMap[profileId]
            }
            val offlineSync = OfflineSyncStore(pendingDao, StubMusicRepo(), registry)
            val (backend, _, _) = newBackend(
                baseUrl = server.baseUrl,
                scope = backgroundScope,
                settings = settings,
                offlineSync = offlineSync,
            )
            registryMap["jf1"] = backend

            // Online calls hit FavoriteItems endpoints directly
            assertTrue(backend.starTrack("jf1:t-1", true) is Result.Ok)
            assertTrue(backend.starTrack("jf1:t-1", false) is Result.Ok)
            assertTrue(backend.starAlbum("jf1:alb-1", true) is Result.Ok)
            assertTrue(backend.starArtist("jf1:art-1", false) is Result.Ok)

            assertEquals(
                listOf(
                    "POST /Users/u123/FavoriteItems/t-1",
                    "DELETE /Users/u123/FavoriteItems/t-1",
                    "POST /Users/u123/FavoriteItems/alb-1",
                    "DELETE /Users/u123/FavoriteItems/art-1",
                ),
                requests.map { "${it.method} ${it.path}" },
            )

            // Offline queues actions under profileId "jf1" with no network requests
            requests.clear()
            settings.setOfflineMode(true)
            assertTrue(backend.starTrack("jf1:t-9", true) is Result.Ok)
            assertTrue(backend.starAlbum("jf1:alb-9", false) is Result.Ok)
            assertTrue(backend.starArtist("jf1:art-9", true) is Result.Ok)
            assertTrue(requests.isEmpty())
            assertEquals(3, pendingDao.pendingForProfile("jf1").size)

            // Back online: flush replays all 3 actions to JellyfinBackend via BackendRegistry
            settings.setOfflineMode(false)
            val flushed = offlineSync.flush()
            assertEquals(3, flushed)
            assertTrue(pendingDao.pendingForProfile("jf1").isEmpty())
            assertEquals(
                setOf(
                    "POST /Users/u123/FavoriteItems/t-9",
                    "DELETE /Users/u123/FavoriteItems/alb-9",
                    "POST /Users/u123/FavoriteItems/art-9",
                ),
                requests.map { "${it.method} ${it.path}" }.toSet(),
            )
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
        val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

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

    private class FakePendingActionDao : PendingActionDao {
        private var nextId = 1L
        private val rows = mutableListOf<PendingActionEntity>()
        private val countFlow = MutableStateFlow(0)

        override suspend fun all(): List<PendingActionEntity> =
            rows.sortedWith(compareBy<PendingActionEntity> { it.timeMs }.thenBy { it.id })

        override suspend fun pendingForProfile(profileId: String): List<PendingActionEntity> =
            rows.filter { it.profileId == profileId }
                .sortedWith(compareBy<PendingActionEntity> { it.timeMs }.thenBy { it.id })

        override suspend fun distinctProfileIds(): List<String> =
            rows.map { it.profileId }.distinct().sorted()

        override suspend fun insert(action: PendingActionEntity): Long {
            val id = if (action.id > 0) action.id else nextId++
            rows += action.copy(id = id)
            countFlow.value = rows.size
            return id
        }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
            countFlow.value = rows.size
        }

        override suspend fun deleteByProfile(profileId: String) {
            rows.removeAll { it.profileId == profileId }
            countFlow.value = rows.size
        }

        override fun observeCount(): Flow<Int> = countFlow

        override suspend fun clear() {
            rows.clear()
            countFlow.value = 0
        }
    }

    private class StubMusicRepo : MusicDataRepository {
        override suspend fun ping() = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?) = Result.Ok(emptyList<Album>())
        override suspend fun artists() = Result.Ok(emptyList<Artist>())
        override suspend fun genres() = Result.Ok(emptyList<Genre>())
        override suspend fun playlists() = Result.Ok(emptyList<Playlist>())
        override suspend fun search(query: String) = Result.Ok(SearchResults(emptyList(), emptyList(), emptyList()))
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int) =
            Result.Ok(SearchResults(emptyList(), emptyList(), emptyList()))
        override suspend fun serverInfo() = Result.Ok(ServerInfo())
        override suspend fun startScan() = Result.Ok(Unit)
        override suspend fun scanStatus() = Result.Ok(ScanStatus())
        override suspend fun album(id: String) = Result.Err("unused")
        override suspend fun artist(id: String) = Result.Err("unused")
        override suspend fun artistInfo(id: String) = Result.Err("unused")
        override suspend fun randomSongs(size: Int, genre: String?) = Result.Ok(emptyList<Track>())
        override suspend fun topSongs(artist: String, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int) = Result.Ok(emptyList<Track>())
        override suspend fun playlist(id: String) = Result.Err("unused")
        override suspend fun starred() = Result.Ok(StarredCollection())
        override suspend fun starTrack(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starAlbum(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun starArtist(id: String, starred: Boolean) = Result.Ok(Unit)
        override suspend fun createPlaylist(name: String, trackIds: List<String>) = Result.Err("unused")
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String) = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean) = Result.Ok(Unit)
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean) = Result.Ok(Unit)
        override suspend fun lyricsForTrack(id: String, artist: String, title: String) = Result.Ok(SyncedLyrics.EMPTY)
        override suspend fun lyrics(artist: String, title: String) = Result.Ok("")
        override fun streamUrl(track: Track, maxBitrate: Int, format: String) = ""
        override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
        override fun downloadUrl(track: Track) = ""
    }
}
