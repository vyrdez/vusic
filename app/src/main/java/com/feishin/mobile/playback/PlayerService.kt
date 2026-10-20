@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil.ImageLoader
import coil.request.ImageRequest
import com.feishin.mobile.MainActivity
import com.feishin.mobile.R
import com.feishin.mobile.core.common.AutoBrowse
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.common.PlaybackSource
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.cache.CACHED_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.cache.toTrack
import com.feishin.mobile.data.download.DownloadRepository
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.network.NetworkMonitor
import com.feishin.mobile.data.queue.QueueStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the real ExoPlayer and publishes it through a [MediaLibrarySession],
 * which gives us the media notification, lock-screen controls, Bluetooth /
 * headset buttons — and Android Auto browse + voice — for free. ExoPlayer
 * handles audio focus and noisy transitions.
 *
 * Phone behavior is unchanged: the app drives this same player through its
 * MediaController, so the queue Auto shows is always the phone's queue. Items
 * that already carry a stream URI (every phone-initiated request) pass through
 * [MediaLibrarySession.Callback.onAddMediaItems] untouched; only Auto browse /
 * voice placeholders are resolved here, and the resolution mirrors the queue
 * back into [PlayerManager] without touching the controller.
 */
@AndroidEntryPoint
class PlayerService : MediaLibraryService() {

    @Inject lateinit var repo: MusicDataRepository
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var creds: CredentialsStore
    @Inject lateinit var downloads: DownloadRepository
    @Inject lateinit var favorites: FavoritesStore
    @Inject lateinit var cachedTracks: CachedTrackStore
    @Inject lateinit var network: NetworkMonitor
    @Inject lateinit var playerManager: PlayerManager
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var queueStore: com.feishin.mobile.data.queue.QueueStore
    @Inject @Named("streaming") lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var cache: androidx.media3.datasource.cache.SimpleCache

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.feishin.mobile.core.common.CrashGuard.handler)
    private val scrobblePolicy = com.feishin.mobile.core.network.ScrobblePolicy()
    private var ticker: Job? = null

    /** songId -> exact Track for already-browsed items (adopt mirror + resolve). */
    private val resolvedSongs = java.util.concurrent.ConcurrentHashMap<String, Track>()

    /** Voice query -> resolved playable tracks for onGetSearchResult paging. */
    private val searchStash = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()

    /** Voice queries that asked for shuffle ("shuffle play X"). */
    private val shuffleQueries = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    override fun onCreate() {
        super.onCreate()
        VLog.d(TAG, "service onCreate")
        // Brand the playback notification with the Vusic note. Media3 would
        // otherwise post its own generic small icon; the shape must match the
        // app icon, and it must be a flat silhouette for the status bar.
        runCatching {
            setMediaNotificationProvider(
                androidx.media3.session.DefaultMediaNotificationProvider(this).apply {
                    setSmallIcon(R.drawable.ic_stat_vusic)
                },
            )
        }
        // Scheme-aware upstream: downloaded tracks are opened from file:// by
        // DefaultDataSource (FileDataSource), while network streams still go
        // through the shared OkHttp client (self-signed + path prefix).
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(UpstreamLogger(upstream))
            // Stable keys (song + quality, no per-request salt): prefetched
            // bytes and replays hit the same spans instead of re-downloading.
            .setCacheKeyFactory { spec ->
                com.feishin.mobile.core.common.StableCacheKey.keyFor(spec.uri.toString())
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(
                object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        VLog.d(TAG, "cache hit bytes=$cachedBytesRead cacheSize=$cacheSizeBytes")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        VLog.d(TAG, "cache ignored reason=$reason")
                    }
                },
            )

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                // "Now playing" scrobble + reset the submission rule for the new track.
                scope.launch { repo.scrobble(id, submission = false) }
                startTicker()
                pushWidget()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val id = player?.currentMediaItem?.mediaId
                    if (id != null && scrobblePolicy.onTrackEnded(id, player?.duration ?: 0L)) {
                        scope.launch { repo.scrobble(id, submission = true) }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                pushWidget()
            }

            override fun onPlayerError(error: PlaybackException) {
                stopTicker()
            }
        })

        player = exo
        mediaSession = MediaLibrarySession.Builder(this, exo, AutoCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            // Auto cannot load per-request authenticated artwork reliably, so the
            // session serves bitmaps through the shared (self-signed-aware) Coil
            // loader instead.
            .setBitmapLoader(CoilBitmapLoader())
            .build()
        startTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        VLog.d(TAG, "onGetSession pkg=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        // Last chance to keep the queue: the task may already be gone.
        runCatching { playerManager.saveBlocking() }
        stopTicker()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app out of recents tears the session down. The queue has
        // to survive that moment, paused, so the mini player comes back on the
        // next launch. Flushed before the service reports itself gone.
        runCatching { playerManager.saveBlocking() }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Pushes the home widget from the session player (covers the app-dead
     * path). Event-driven: track transitions and play/pause flips only, never
     * the 1s ticker. Skips no-op repeats so background transitions don't spam
     * RemoteViews updates.
     */
    private var lastWidgetKey: Triple<String?, Boolean, Int>? = null

    private fun pushWidget() {
        val p = player ?: return
        // ExoPlayer is app-thread confined: snapshot here (this runs on Main
        // from the listener), then fan out to IO for prefs and art.
        val item = p.currentMediaItem
        val count = p.mediaItemCount
        val playing = p.isPlaying
        val track = item?.let { TrackMediaItems.toTrack(it) }
        val artUri = item?.mediaMetadata?.artworkUri?.toString()
        val key = Triple(track?.id, playing, count)
        if (key == lastWidgetKey) return
        lastWidgetKey = key
        scope.launch(Dispatchers.IO) {
            val saved = com.feishin.mobile.widget.WidgetStateStore(this@PlayerService).load()
            val state = if (track == null || count == 0) {
                com.feishin.mobile.widget.WidgetState.empty()
            } else {
                val starred = if (saved.trackId == track.id) saved.starred else false
                com.feishin.mobile.widget.WidgetState(
                    hasQueue = true,
                    title = track.title,
                    artist = track.artist,
                    coverArtId = track.coverArt,
                    trackId = track.id,
                    isPlaying = playing,
                    starred = starred,
                )
            }
            runCatching {
                com.feishin.mobile.widget.WidgetUpdater.update(
                    this@PlayerService, state, imageLoader,
                ) { id ->
                    artUri ?: coverUrl(id, 256)?.toString()
                }
            }
        }
    }

    /** Drives the 50%-or-4-minutes scrobble submission rule. */
    private fun startTicker() {        stopTicker()
        ticker = scope.launch {
            while (true) {
                val p = player ?: break
                val id = p.currentMediaItem?.mediaId
                if (id != null && p.isPlaying) {
                    val submit = scrobblePolicy.onProgress(
                        id = id,
                        positionMs = p.currentPosition,
                        durationMs = p.duration,
                        isPlaying = true,
                    )
                    if (submit) repo.scrobble(id, submission = true)
                }
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    // ---- Auto helpers (used by the callback) -------------------------------

    private fun rememberTracks(tracks: List<Track>) {
        for (t in tracks) {
            if (resolvedSongs.size > 1000) resolvedSongs.clear()
            resolvedSongs[t.id] = t
        }
    }

    private fun coverUrl(coverArtId: String?, size: Int = 512): Uri? {
        val c = creds.state.value ?: return null
        return CoverArtUrlProvider.build(c.serverUrl, c.username, c.password, coverArtId, size)
            ?.let { Uri.parse(it) }
    }

    private fun folder(id: String, title: String, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .apply { if (art != null) setArtworkUri(art) }
                    .build(),
            )
            .build()

    /** Browse placeholder for a track: no URI yet (resolved on play). */
    private fun trackPlaceholder(track: Track, parentId: String, index: Int): MediaItem {
        rememberTracks(listOf(track))
        return MediaItem.Builder()
            .setMediaId("auto_${track.id}_$index")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setDurationMs(if (track.durationSec > 0) track.durationSec * 1000L else 0L)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .apply { coverUrl(track.coverArt, 512)?.let { setArtworkUri(it) } }
                    .setExtras(
                        androidx.core.os.bundleOf(
                            EXTRA_SONG_ID to track.id,
                            EXTRA_PARENT_ID to parentId,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    private fun songIdOf(item: MediaItem): String? =
        item.mediaMetadata.extras?.getString(EXTRA_SONG_ID)

    /** Offline when the toggle says so or the network is gone. */
    private suspend fun isOffline(): Boolean =
        runCatching { settings.settingsNow().offlineMode }.getOrDefault(false) ||
            network.isOnline.value != true

    /**
     * Playable MediaItem for [track]: finished downloads play from the local
     * file (offline-safe), otherwise the Wi-Fi-quality stream URL. Null when
     * offline with nothing downloaded (the item is then dropped, never a dead
     * entry that errors in the car).
     */
    private suspend fun resolveTrack(track: Track, queueIdHint: Long = -1L): MediaItem? {
        val local = downloads.localFile(track.id)
        val uriStr = if (local != null) {
            PlaybackSource.localUri(local.absolutePath)
        } else {
            if (isOffline()) return null
            val s = runCatching { settings.settingsNow() }.getOrNull()
            // The car is unmetered: stream at the Wi-Fi quality setting.
            repo.streamUrl(track, s?.wifiBitrate ?: 0, s?.wifiFormat ?: "raw")
        }
        return TrackMediaItems.toMediaItem(
            track = track,
            streamUrl = uriStr,
            coverArtUrl = coverUrl(track.coverArt, 512)?.toString(),
            queueId = queueIdHint.takeIf { it >= 0 },
        )
    }

    private suspend fun collectionTracks(kind: String, id: String): List<Track> {
        return when (kind) {
            "pl" -> (repo.playlist(id) as? Result.Ok)?.value?.tracks.orEmpty()
            "al" -> (repo.album(id) as? Result.Ok)?.value?.tracks.orEmpty()
            "ar" -> {
                val detail = (repo.artist(id) as? Result.Ok)?.value ?: return emptyList()
                // Artist page order: first album's tracks (newest first).
                val first = detail.albums.sortedByDescending { it.year ?: 0 }.firstOrNull()
                    ?: return emptyList()
                (repo.album(first.id) as? Result.Ok)?.value?.tracks.orEmpty()
            }
            "mix" -> when (id) {
                "random" -> (repo.randomSongs(50) as? Result.Ok)?.value.orEmpty()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private suspend fun voiceTracksFor(query: String): List<Track> {
        val q = AutoBrowse.normalizeVoiceQuery(query)
        if (q.isBlank()) return emptyList()
        val res = (repo.search(q, 20, 10, 50) as? Result.Ok)?.value
            ?: return emptyList()
        val picked = AutoBrowse.voiceTracks(res, q)
        if (picked.isNotEmpty()) return picked
        // "play <album>": the album's tracks.
        val album = res.albums.firstOrNull()
        if (album != null) {
            val tracks = (repo.album(album.id) as? Result.Ok)?.value?.tracks.orEmpty()
            if (tracks.isNotEmpty()) return tracks.take(50)
        }
        // "play <artist>": their top songs.
        val artist = res.artists.firstOrNull()
        if (artist != null) {
            val top = (repo.topSongs(artist.name, 50) as? Result.Ok)?.value.orEmpty()
            if (top.isNotEmpty()) return top
        }
        return emptyList()
    }

    private inner class AutoCallback : MediaLibrarySession.Callback {

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Play tapped with an empty player (widget/media button after the
            // app process died): restore the persisted queue paused, exactly
            // like a cold start, instead of failing the command.
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val snapshot = runCatching { queueStore.load() }.getOrNull()
                if (snapshot == null || snapshot.tracks.isEmpty()) {
                    future.setException(IllegalStateException("Nothing to resume"))
                    return@launch
                }
                val built = snapshot.tracks.mapNotNull { p ->
                    val track = p.toDomain()
                    resolveTrack(track, p.queueId.takeIf { it >= 0 } ?: -1L)
                }
                if (built.isEmpty()) {
                    future.setException(IllegalStateException("Nothing to resume"))
                    return@launch
                }
                val index = snapshot.currentIndex.coerceIn(0, built.size - 1)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        built, index, snapshot.positionMs,
                    ),
                )
            }
            return future
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            VLog.d(TAG, "onConnect pkg=${controller.packageName}")
            // Car controllers get Favorite + Start-radio actions; the phone's
            // own controller (same package) is left alone.
            if (controller.packageName != packageName) {
                session.setCustomLayout(
                    controller,
                    listOf(
                        CommandButton.Builder()
                            .setDisplayName("Favorite")
                            .setSessionCommand(SessionCommand(COMMAND_FAVORITE, Bundle.EMPTY))
                            .setIconResId(R.drawable.ic_auto_favorite)
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("Start radio")
                            .setSessionCommand(SessionCommand(COMMAND_RADIO, Bundle.EMPTY))
                            .setIconResId(R.drawable.ic_auto_radio)
                            .build(),
                    ),
                )
            }
            // Custom commands must be routable. The base defaults are session
            // commands only — browsers need the LIBRARY set too, otherwise
            // every browse call fails PERMISSION_DENIED before the callback.
            val commands = SessionCommands.Builder()
                .addSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.commands)
                .add(SessionCommand(COMMAND_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_RADIO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                commands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            VLog.d(TAG, "onGetLibraryRoot")
            val root = MediaItem.Builder()
                .setMediaId(AutoBrowse.ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Vusic")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                val items = runCatching { childrenOf(parentId) }.getOrDefault(emptyList())
                val sliced = AutoBrowse.page(items, page, pageSize)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(sliced), params))
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            scope.launch {
                val raw = query.trim()
                if (raw.startsWith("shuffle ", ignoreCase = true)) shuffleQueries += raw
                val tracks = runCatching { voiceTracksFor(raw) }.getOrDefault(emptyList())
                searchStash[raw] = tracks
                rememberTracks(tracks)
                future.set(LibraryResult.ofVoid())
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                val tracks = searchStash[query]
                    ?: runCatching { voiceTracksFor(query) }.getOrDefault(emptyList())
                if (!searchStash.containsKey(query)) {
                    searchStash[query] = tracks
                    rememberTracks(tracks)
                }
                // The raw query rides in the parent id so the play request can
                // pick up the shuffle flag ("shuffle play X").
                val parent = "search::$query"
                val items = AutoBrowse.page(tracks, page, pageSize)
                    .mapIndexed { i, t -> trackPlaceholder(t, parent, page * pageSize + i) }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // Phone-initiated requests already carry stream URIs: untouched.
            if (mediaItems.all { it.localConfiguration?.uri?.toString().isNullOrEmpty().not() }) {
                return Futures.immediateFuture(mediaItems)
            }
            val future = SettableFuture.create<List<MediaItem>>()
            scope.launch {
                val out = runCatching { resolvePlayRequest(mediaItems) }.getOrDefault(mediaItems)
                future.set(out)
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            browser: MediaSession.ControllerInfo,
            command: SessionCommand,
            extras: Bundle,
        ): ListenableFuture<SessionResult> {
            val future = SettableFuture.create<SessionResult>()
            scope.launch {
                val code = runCatching {
                    when (command.customAction) {
                        COMMAND_FAVORITE -> {
                            val current = playerManager.current.value ?: return@runCatching SessionResult.RESULT_ERROR_UNKNOWN
                            favorites.setTrackStarred(current.id, !current.starred)
                            SessionResult.RESULT_SUCCESS
                        }
                        COMMAND_RADIO -> {
                            val seed = playerManager.current.value
                                ?: (repo.randomSongs(1) as? Result.Ok)?.value?.firstOrNull()
                                ?: return@runCatching SessionResult.RESULT_ERROR_UNKNOWN
                            playerManager.startRadio(seed)
                            SessionResult.RESULT_SUCCESS
                        }
                        else -> SessionResult.RESULT_ERROR_UNKNOWN
                    }
                }.getOrDefault(SessionResult.RESULT_ERROR_UNKNOWN)
                future.set(SessionResult(code))
            }
            return future
        }
    }

    private fun rememberTracksFromItemsUnused() = Unit

    private suspend fun childrenOf(parentId: String): List<MediaItem> {
        if (parentId == AutoBrowse.ROOT_ID) {
            return AutoBrowse.rootTabs().map { folder(it.id, it.title) }
        }
        // Home nodes render their own children.
        when (parentId) {
            AutoBrowse.TAB_HOME -> return AutoBrowse.homeChildren().map { n ->
                folder(n.id, n.title).takeIf { n.browsable }
                    ?: actionItem(n.id, n.title)
            }
            AutoBrowse.TAB_LIBRARY -> return AutoBrowse.libraryChildren().map { folder(it.id, it.title) }
            AutoBrowse.TAB_FAVORITES -> return AutoBrowse.favoritesChildren().map { folder(it.id, it.title) }
            AutoBrowse.TAB_DOWNLOADS -> return AutoBrowse.downloadsChildren().map { folder(it.id, it.title) }
            AutoBrowse.LIB_PLAYLISTS -> {
                val pinned = listOf(
                    folder(AutoBrowse.collectionNode("pl", FAVORITE_SONGS_PLAYLIST_ID), "Favorite songs"),
                    folder(AutoBrowse.collectionNode("pl", CACHED_SONGS_PLAYLIST_ID), "Cached"),
                )
                val lists = (repo.playlists() as? Result.Ok)?.value.orEmpty()
                return pinned + lists.map { p ->
                    folder(AutoBrowse.collectionNode("pl", p.id), "${p.name} (${p.songCount})", coverUrl(p.coverArt, 256))
                }
            }
            AutoBrowse.LIB_ALBUMS -> {
                val albums = (repo.albumList("alphabeticalByName", 100, 0) as? Result.Ok)?.value.orEmpty()
                return albums.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.LIB_ARTISTS -> {
                val artists = (repo.artists() as? Result.Ok)?.value.orEmpty()
                return artists.map { a ->
                    folder(AutoBrowse.collectionNode("ar", a.id), "${a.name} (${a.albumCount})", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.HOME_RECENT -> {
                val recent = playerManager.recentSnapshot().takeLast(50).reversed()
                return recent.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.HOME_NEWEST -> {
                val albums = (repo.albumList("newest", 20, 0) as? Result.Ok)?.value.orEmpty()
                return albums.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.HOME_RANDOM -> {
                val tracks = (repo.randomSongs(50) as? Result.Ok)?.value.orEmpty()
                return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.HOME_RADIO -> return listOf(actionItem(ACTION_RADIO_NODE, "Play radio mix"))
            AutoBrowse.FAV_SONGS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.tracks.orEmpty()
                return favs.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            AutoBrowse.FAV_ALBUMS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.albums.orEmpty()
                return favs.map { a ->
                    folder(AutoBrowse.collectionNode("al", a.id), "${a.name} — ${a.artist}", coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.FAV_ARTISTS -> {
                val favs = (repo.starred() as? Result.Ok)?.value?.artists.orEmpty()
                return favs.map { a ->
                    folder(AutoBrowse.collectionNode("ar", a.id), a.name, coverUrl(a.coverArt, 256))
                }
            }
            AutoBrowse.DL_TRACKS -> {
                val done = downloads.completedTracks()
                return done.mapIndexed { i, e ->
                    trackPlaceholder(e.toDomainTrack(), parentId, i)
                }
            }
        }
        // Collection tracks (playlist/album/artist/mix).
        val ref = AutoBrowse.collectionRefOf(parentId)
        if (ref != null) {
            val (kind, id) = ref
            if (kind == "pl" && (id == FAVORITE_SONGS_PLAYLIST_ID || id == CACHED_SONGS_PLAYLIST_ID)) {
                val tracks = if (id == FAVORITE_SONGS_PLAYLIST_ID) {
                    (repo.starred() as? Result.Ok)?.value?.tracks.orEmpty()
                } else {
                    // Cached collection: fully cached songs from Room.
                    cachedTracksSnapshot()
                }
                return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
            }
            val tracks = collectionTracks(kind, id)
            return tracks.mapIndexed { i, t -> trackPlaceholder(t, parentId, i) }
        }
        return emptyList()
    }

    private suspend fun cachedTracksSnapshot(): List<Track> =
        runCatching { cachedTracks.all().map { it.toTrack() } }.getOrDefault(emptyList())

    /** Non-browsable playable action (e.g. "Play radio mix"). */
    private fun actionItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(androidx.core.os.bundleOf(EXTRA_ACTION to id))
                    .build(),
            )
            .build()

    /**
     * Resolves an Auto play request. Normal placeholders become playable items
     * (same builder as phone playback) and the queue is mirrored into
     * [PlayerManager]; the "start radio" action boots the radio instead and the
     * car's stale set is answered with the live session playlist so nothing the
     * radio just queued is clobbered.
     */
    private suspend fun resolvePlayRequest(mediaItems: List<MediaItem>): List<MediaItem> {
        val action = mediaItems.firstOrNull { it.mediaId == ACTION_RADIO_NODE }
        if (action != null) {
            val seed = playerManager.current.value
                ?: (repo.randomSongs(1) as? Result.Ok)?.value?.firstOrNull()
            if (seed != null) {
                val seedItem = resolveTrack(seed, -1L)
                // Boot radio through the phone path (passthrough-safe), then
                // answer the car's set with the same seed item: both writers
                // converge on [seed] and the background batches append on top.
                playerManager.startRadio(seed)
                if (seedItem != null) {
                    playerManager.adoptExternalQueue(listOf(seed), "Android Auto")
                    return listOf(seedItem)
                }
            }
            return mediaItems
        }
        val tracks = mediaItems.mapNotNull { item ->
            val id = songIdOf(item) ?: return@mapNotNull null
            resolvedSongs[id]
                ?: TrackMediaItems.toTrack(item)?.copy(id = id)
        }
        if (tracks.isEmpty()) return mediaItems
        val parent = mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString(EXTRA_PARENT_ID)
        val shuffle = parent?.removePrefix("search::")?.let { shuffleQueries.remove(it) } == true
        val resolved = tracks.mapIndexed { i, t -> resolveTrack(t, -1L) }
        // Drop offline-unresolvable entries, never play dead items.
        val playable = tracks.zip(resolved).filter { it.second != null }
        if (playable.isEmpty()) return mediaItems
        playerManager.adoptExternalQueue(playable.map { it.first }, "Android Auto")
        if (shuffle) playerManager.setShuffle(true)
        return playable.map { it.second!! }
    }

    /** Coil-backed artwork for the session (notification + Auto now-playing). */
    private inner class CoilBitmapLoader : androidx.media3.common.util.BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean =
            mimeType.startsWith("image/")

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                val bmp = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = ImageRequest.Builder(this@PlayerService).data(data).build()
                        (imageLoader.execute(req).drawable?.toBitmap())
                            ?: BitmapFactory.decodeByteArray(data, 0, data.size)
                    }
                }.getOrNull() ?: placeholderBitmap()
                future.set(bmp)
            }
            return future
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()
            scope.launch {
                val bmp = runCatching {
                    withContext(Dispatchers.IO) {
                        val req = ImageRequest.Builder(this@PlayerService).data(uri.toString()).build()
                        imageLoader.execute(req).drawable?.toBitmap()
                    }
                }.getOrNull() ?: placeholderBitmap()
                future.set(bmp)
            }
            return future
        }

        private fun placeholderBitmap(): Bitmap {
            val d = getDrawable(R.drawable.art_placeholder)
            return d?.toBitmap(256, 256) ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        }
    }

    companion object {
        const val TAG = "PlayerCache"
        const val COMMAND_FAVORITE = "vusic_favorite"
        const val COMMAND_RADIO = "vusic_radio"
        const val ACTION_RADIO_NODE = "auto_action_radio"
        const val EXTRA_SONG_ID = "auto_song_id"
        const val EXTRA_PARENT_ID = "auto_parent_id"
        const val EXTRA_ACTION = "auto_action"
    }
}

/**
 * Upstream decorator that logs every real network open with its stable key.
 * A second play of a fully cached song must produce cache-hit logs and no
 * "upstream open" line at all — that is the zero-network-request proof.
 */
private class UpstreamLogger(
    private val delegate: androidx.media3.datasource.DataSource.Factory,
) : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource {
        val inner = delegate.createDataSource()
        return object : androidx.media3.datasource.DataSource by inner {
            override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                val key = com.feishin.mobile.core.common.StableCacheKey.keyFor(dataSpec.uri.toString())
                VLog.d(PlayerService.TAG, "upstream open key=$key uri=${dataSpec.uri.host}${dataSpec.uri.path}")
                return inner.open(dataSpec)
            }
        }
    }
}
