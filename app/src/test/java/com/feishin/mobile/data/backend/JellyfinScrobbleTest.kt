package com.feishin.mobile.data.backend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.data.cache.MetadataCache
import com.feishin.mobile.data.db.CachedMetadataEntity
import com.feishin.mobile.data.db.MetadataCacheDao
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.db.PendingActionEntity
import com.feishin.mobile.data.download.OfflineSyncStore
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.remote.jellyfin.JellyfinApi
import com.feishin.mobile.data.remote.jellyfin.JellyfinPlaybackProgressInfo
import com.feishin.mobile.data.remote.jellyfin.JellyfinPlaybackStartInfo
import com.feishin.mobile.data.remote.jellyfin.JellyfinPlaybackStopInfo
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
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
import com.feishin.mobile.playback.JellyfinSessionCoordinator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

class JellyfinScrobbleTest {

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
        offlineSync: OfflineSyncStore? = null,
        profile: ServerProfile = testProfile(baseUrl),
    ): JellyfinBackend = JellyfinBackend(
        profile = profile,
        api = createApi(baseUrl),
        cache = MetadataCache(FakeCacheDao()),
        refresh = LibraryRefresh(),
        network = null,
        settings = settings,
        offlineSync = dagger.Lazy { offlineSync ?: error("unused") },
        playlistEvents = dagger.Lazy { PlaylistEvents() },
        cacheScope = scope,
    )

    @Test
    fun sessionLifecyclePreservesPlaySessionIdAndPositionTicksAcrossStartProgressPauseSeekAndEnd() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            when (req.path) {
                "/Sessions/Playing",
                "/Sessions/Playing/Progress",
                "/Sessions/Playing/Stopped", -> 204 to ""
                else -> 404 to "{}"
            }
        }.use { server ->
            val backend = newBackend(server.baseUrl, backgroundScope)
            var nowMs = 1_000L
            val coordinator = JellyfinSessionCoordinator(
                clockMs = { nowMs },
                sessionIdFactory = { "sess-fixed-1" },
                progressIntervalMs = 15_000L,
            )

            // 1. Track starts
            val (stoppedPrev, started) = coordinator.transitionTo(
                newTrackId = "jf1:trk-1",
                isJellyfin = true,
                isDirect = true,
                durationMs = 210_000L,
            )
            assertNull(stoppedPrev)
            assertNotNull(started)
            assertTrue(
                backend.reportPlaybackStart(
                    id = started!!.trackId,
                    positionMs = 0L,
                    playSessionId = started.playSessionId,
                    isDirect = started.isDirect,
                ) is Result.Ok,
            )

            // 2. 14s tick -> no progress report yet; 15s tick -> sends /Progress
            nowMs = 15_000L
            assertNull(coordinator.onTick("jf1:trk-1", positionMs = 14_000L, durationMs = 210_000L))
            nowMs = 16_000L
            val periodic = coordinator.onTick("jf1:trk-1", positionMs = 15_000L, durationMs = 210_000L)
            assertNotNull(periodic)
            assertTrue(
                backend.reportPlaybackProgress(
                    id = periodic!!.trackId,
                    positionMs = periodic.lastPositionMs,
                    isPaused = false,
                    playSessionId = periodic.playSessionId,
                    isDirect = periodic.isDirect,
                ) is Result.Ok,
            )

            // 3. Pause at 22_500ms -> sends /Progress with IsPaused = true
            nowMs = 23_500L
            val paused = coordinator.onPauseOrSeek("jf1:trk-1", positionMs = 22_500L, durationMs = 210_000L)!!
            assertTrue(
                backend.reportPlaybackProgress(
                    id = paused.trackId,
                    positionMs = paused.lastPositionMs,
                    isPaused = true,
                    playSessionId = paused.playSessionId,
                    isDirect = paused.isDirect,
                ) is Result.Ok,
            )

            // 4. Seek to 95_000ms while paused -> sends /Progress with updated PositionTicks
            nowMs = 25_000L
            val seeked = coordinator.onPauseOrSeek("jf1:trk-1", positionMs = 95_000L, durationMs = 210_000L)!!
            assertTrue(
                backend.reportPlaybackProgress(
                    id = seeked.trackId,
                    positionMs = seeked.lastPositionMs,
                    isPaused = true,
                    playSessionId = seeked.playSessionId,
                    isDirect = seeked.isDirect,
                ) is Result.Ok,
            )

            // 5. Completion (STATE_ENDED): ExoPlayer reports currentPosition = 0 and duration = 0/UNSET,
            // coordinator must still report full durationMs (210_000L -> 2_100_000_000 ticks)
            val (endedSession, stopPosMs) = coordinator.onEnded("jf1:trk-1", playerDurationMs = 0L)!!
            assertEquals(210_000L, stopPosMs)
            assertNull(coordinator.active)
            assertTrue(
                backend.reportPlaybackStopped(
                    id = endedSession.trackId,
                    positionMs = stopPosMs,
                    playSessionId = endedSession.playSessionId,
                ) is Result.Ok,
            )

            assertEquals(
                listOf(
                    "/Sessions/Playing",
                    "/Sessions/Playing/Progress",
                    "/Sessions/Playing/Progress",
                    "/Sessions/Playing/Progress",
                    "/Sessions/Playing/Stopped",
                ),
                requests.map { it.path },
            )

            val startPayload = json.decodeFromString<JellyfinPlaybackStartInfo>(requests[0].body)
            assertEquals("trk-1", startPayload.itemId)
            assertEquals("trk-1", startPayload.mediaSourceId)
            assertEquals(0L, startPayload.positionTicks)
            assertEquals(false, startPayload.isPaused)
            assertEquals("sess-fixed-1", startPayload.playSessionId)
            assertEquals("DirectStream", startPayload.playMethod)

            val progress15s = json.decodeFromString<JellyfinPlaybackProgressInfo>(requests[1].body)
            assertEquals(15_000L * 10_000L, progress15s.positionTicks)
            assertEquals(false, progress15s.isPaused)
            assertEquals("sess-fixed-1", progress15s.playSessionId)

            val progressPaused = json.decodeFromString<JellyfinPlaybackProgressInfo>(requests[2].body)
            assertEquals(22_500L * 10_000L, progressPaused.positionTicks)
            assertEquals(true, progressPaused.isPaused)
            assertEquals("sess-fixed-1", progressPaused.playSessionId)

            val progressSeek = json.decodeFromString<JellyfinPlaybackProgressInfo>(requests[3].body)
            assertEquals(95_000L * 10_000L, progressSeek.positionTicks)
            assertEquals(true, progressSeek.isPaused)
            assertEquals("sess-fixed-1", progressSeek.playSessionId)

            val stoppedPayload = json.decodeFromString<JellyfinPlaybackStopInfo>(requests[4].body)
            assertEquals("trk-1", stoppedPayload.itemId)
            assertEquals(210_000L * 10_000L, stoppedPayload.positionTicks)
            assertEquals("sess-fixed-1", stoppedPayload.playSessionId)
        }
    }

    @Test
    fun transcodeMethodAndMidTrackTransitionStopPreviousSession() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            204 to ""
        }.use { server ->
            val backend = newBackend(server.baseUrl, backgroundScope)
            var counter = 0
            val coordinator = JellyfinSessionCoordinator(
                clockMs = { 1_000L },
                sessionIdFactory = { "sess-${++counter}" },
            )

            val (_, first) = coordinator.transitionTo("jf1:trk-a", isJellyfin = true, isDirect = false, durationMs = 180_000L)
            backend.reportPlaybackStart(first!!.trackId, 0L, first.playSessionId, first.isDirect)
            coordinator.onTick("jf1:trk-a", positionMs = 42_000L, durationMs = 180_000L)

            val (stopped, second) = coordinator.transitionTo("jf1:trk-b", isJellyfin = true, isDirect = true, durationMs = 120_000L)
            assertNotNull(stopped)
            assertEquals(42_000L, stopped!!.lastPositionMs)
            backend.reportPlaybackStopped(stopped.trackId, stopped.lastPositionMs, stopped.playSessionId)
            backend.reportPlaybackStart(second!!.trackId, 0L, second.playSessionId, second.isDirect)

            val startA = json.decodeFromString<JellyfinPlaybackStartInfo>(requests[0].body)
            assertEquals("Transcode", startA.playMethod)
            assertEquals("sess-1", startA.playSessionId)

            val stopA = json.decodeFromString<JellyfinPlaybackStopInfo>(requests[1].body)
            assertEquals("trk-a", stopA.itemId)
            assertEquals(42_000L * 10_000L, stopA.positionTicks)
            assertEquals("sess-1", stopA.playSessionId)

            val startB = json.decodeFromString<JellyfinPlaybackStartInfo>(requests[2].body)
            assertEquals("trk-b", startB.itemId)
            assertEquals("DirectStream", startB.playMethod)
            assertEquals("sess-2", startB.playSessionId)
        }
    }

    @Test
    fun offlineCompletionQueuesScrobblePerProfileAndFlushReplaysPlayingAndStopped() = runTest {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        LocalHttpServer { req ->
            requests += req
            204 to ""
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
            val backend1 = newBackend(
                baseUrl = server.baseUrl,
                scope = backgroundScope,
                settings = settings,
                offlineSync = offlineSync,
                profile = testProfile(server.baseUrl, id = "jf1", userId = "u123"),
            )
            val backend2 = newBackend(
                baseUrl = server.baseUrl,
                scope = backgroundScope,
                settings = settings,
                offlineSync = offlineSync,
                profile = testProfile(server.baseUrl, id = "jf2", userId = "u456"),
            )
            registryMap["jf1"] = backend1
            registryMap["jf2"] = backend2

            // Offline scrobble submissions queue without hitting network
            settings.setOfflineMode(true)
            assertTrue(backend1.reportPlaybackStopped("jf1:trk-1", 100_000L, "s1") is Result.Err)
            assertTrue(backend1.scrobble("jf1:trk-1", submission = false) is Result.Ok)
            assertTrue(backend1.scrobble("jf1:trk-1", submission = true) is Result.Ok)
            assertTrue(backend2.scrobble("jf2:trk-2", submission = true) is Result.Ok)
            assertTrue(requests.isEmpty())

            assertEquals(1, pendingDao.pendingForProfile("jf1").size)
            assertEquals(1, pendingDao.pendingForProfile("jf2").size)

            // Back online: flush replays POST /Sessions/Playing + POST /Sessions/Playing/Stopped per profile
            settings.setOfflineMode(false)
            val flushed = offlineSync.flush()
            assertEquals(2, flushed)
            assertTrue(pendingDao.all().isEmpty())

            assertEquals(4, requests.size)
            assertEquals("/Sessions/Playing", requests[0].path)
            assertEquals("/Sessions/Playing/Stopped", requests[1].path)
            assertEquals("/Sessions/Playing", requests[2].path)
            assertEquals("/Sessions/Playing/Stopped", requests[3].path)

            val start1 = json.decodeFromString<JellyfinPlaybackStartInfo>(requests[0].body)
            val stop1 = json.decodeFromString<JellyfinPlaybackStopInfo>(requests[1].body)
            assertEquals("trk-1", start1.itemId)
            assertEquals(start1.playSessionId, stop1.playSessionId)
            assertTrue(stop1.positionTicks > 0L)

            val start2 = json.decodeFromString<JellyfinPlaybackStartInfo>(requests[2].body)
            val stop2 = json.decodeFromString<JellyfinPlaybackStopInfo>(requests[3].body)
            assertEquals("trk-2", start2.itemId)
            assertEquals(start2.playSessionId, stop2.playSessionId)
            assertTrue(stop2.positionTicks > 0L)
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val body: String,
    )

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
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            val name = line.substring(0, sep).trim().lowercase()
                            val value = line.substring(sep + 1).trim()
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

                    val (status, respBody) = handler(RecordedRequest(method, path, body))
                    val bytes = if (status == 204) ByteArray(0) else respBody.toByteArray(Charsets.UTF_8)
                    val reason = when (status) {
                        200 -> "OK"
                        204 -> "No Content"
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
