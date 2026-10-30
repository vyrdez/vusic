package com.feishin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.MixGenerator
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class Mix(val name: String, val tracks: List<Track>) {
    val covers: List<String?> get() = tracks.map { it.coverArt }.distinct().take(4)
}

sealed interface SpeedTarget {
    data class AlbumTarget(val album: Album) : SpeedTarget
    data class PlaylistTarget(val playlist: Playlist) : SpeedTarget
}

data class SpeedTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverArt: String?,
    /** Playlists use a collage/placeholder when the server has no cover art. */
    val playlistCover: PlaylistCover? = null,
    val target: SpeedTarget,
)

data class HomeUi(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val genres: List<String> = listOf("All"),
    val selectedGenre: String = "All",
    val speedDial: List<SpeedTile> = emptyList(),
    val recent: List<Album> = emptyList(),
    val quickPicks: List<Track> = emptyList(),
    val newest: List<Album> = emptyList(),
    val forgottenAlbums: List<Album> = emptyList(),
    val forgottenTracks: List<Track> = emptyList(),
    val moreArtist: Artist? = null,
    val moreArtistAlbums: List<Album> = emptyList(),
    val mixes: List<Mix> = emptyList(),
    val discover: Album? = null,
    val frequent: List<Album> = emptyList(),
    val globalError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: MusicDataRepository,
    private val settings: SettingsStore,
    private val cache: HomeCache,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var lastRecent: List<Album> = emptyList()
    private var lastFrequent: List<Album> = emptyList()
    private var lastPlaylists: List<Playlist> = emptyList()
    private val prefetched = HashSet<String>()
    /** Advances on every mixes load so refresh/genre change re-seeds the shelf. */
    private var mixGeneration = 0L

    init {
        observePins()
        viewModelScope.launch {
            // Stale-while-revalidate: paint the last snapshot immediately (not as
            // "loading") so a cold start shows content before the network answers.
            cache.loadDisk(_ui.value.selectedGenre)?.let { cached ->
                if (_ui.value.recent.isEmpty()) {
                    _ui.value = cached.copy(loading = false, refreshing = false)
                    VLog.d("HomePerf", "painted disk snapshot")
                }
            }
            load(_ui.value.selectedGenre, isManualRefresh = false)
        }
    }

    fun refresh() = load(_ui.value.selectedGenre, isManualRefresh = true)

    fun selectGenre(genre: String) {
        if (genre == _ui.value.selectedGenre && !_ui.value.loading && !_ui.value.refreshing) return
        load(genre, isManualRefresh = false)
    }

    private fun load(genre: String, isManualRefresh: Boolean = false) {
        // Cancel the previous genre load so rapid chip taps don't fight each other.
        loadJob?.cancel()
        // Drop the previous genre's section data so a late-arriving cancelled job
        // cannot mix its albums/playlists into this genre's speed dial.
        lastRecent = emptyList()
        lastFrequent = emptyList()
        lastPlaylists = emptyList()
        val cached = cache.get(genre)
        // Stale-while-revalidate: if we have content, paint it immediately (not
        // loading) and refresh in the background. Only show skeletons on a miss.
        _ui.value = if (cached != null) {
            cached.copy(loading = false, refreshing = isManualRefresh)
        } else {
            HomeUi(loading = true, refreshing = isManualRefresh, selectedGenre = genre, genres = listOf("All"))
        }
        loadJob = viewModelScope.launch {
            loadSections(genre)
            prefetchNeighbours(genre)
        }
    }

    private fun observePins() {
        viewModelScope.launch {
            settings.flow.map { it.pins }.distinctUntilChanged().collect { pins ->
                if (lastRecent.isEmpty() && lastFrequent.isEmpty() && lastPlaylists.isEmpty()) return@collect
                publishSpeedDial(buildSpeedDial(pins, lastRecent, lastFrequent, lastPlaylists))
            }
        }
    }

    private fun publishSpeedDial(tiles: List<SpeedTile>) {
        if (tiles.isNotEmpty()) _ui.value = _ui.value.copy(speedDial = tiles.take(27))
    }

    /**
     * Every section publishes independently. Mixes build from already-fetched
     * lists plus one bounded random-songs page, so Home never waits on the old
     * per-artist similar-songs fan-out (7-9s on Navidrome) before emitting.
     *
     * Timings are logged under `HomePerf` so a slow endpoint is easy to spot.
     */
    private suspend fun loadSections(genre: String) {
        val genreParam = genre.takeIf { it != "All" }
        val firstContent = AtomicBoolean(false)
        val startedAt = android.os.SystemClock.elapsedRealtime()

        /**
         * [isContent] marks the sections that justify leaving the skeleton state.
         * Light metadata (genres) must not clear it early, otherwise the user sees
         * an empty Home while the real sections are still in flight.
         */
        fun publish(name: String, isContent: Boolean, transform: (HomeUi) -> HomeUi) {
            val next = transform(_ui.value)
            val fresh = isContent && firstContent.compareAndSet(false, true)
            _ui.value = if (fresh) next.copy(loading = false) else next
            VLog.d(
                "HomePerf",
                "$name +${android.os.SystemClock.elapsedRealtime() - startedAt}ms" +
                    if (fresh) " (first content)" else "",
            )
        }

        supervisorScope {
            // Kick every fetch off before any suspending local read, so nothing
            // serializes in front of the first network call.
            val genresD = async { repo.genres() }
            val recentD = async { repo.albumList(if (genreParam != null) "byGenre" else "recent", 20, 0, genreParam) }
            val newestD = async { repo.albumList(if (genreParam != null) "byGenre" else "newest", 20, 0, genreParam) }
            val frequentD = async { repo.albumList(if (genreParam != null) "byGenre" else "frequent", 20, 0, genreParam) }
            val randomD = async { repo.albumList("random", 20, 0) }
            val songsD = async { repo.randomSongs(24, genreParam) }
            val starredD = async { repo.starred() }
            val playlistsD = async { repo.playlists() }
            // Only needed once tiles are built, so it no longer delays the fetches.
            val pinsD = async { settings.pinsNow() }
            val pins = pinsD.await()

            launch {
                val list = (genresD.await() as? Result.Ok)?.value.orEmpty()
                    .sortedByDescending { it.songCount }.take(10).map { it.name }
                if (list.isNotEmpty()) publish("genres", isContent = false) { it.copy(genres = listOf("All") + list) }
            }
            launch {
                val recent = (recentD.await() as? Result.Ok)?.value.orEmpty()
                if (recent.isNotEmpty()) {
                    lastRecent = recent
                    publish("recent", isContent = true) { it.copy(recent = recent) }
                    publishSpeedDial(buildSpeedDial(pins, lastRecent, lastFrequent, lastPlaylists))
                }
            }
            launch {
                val newest = (newestD.await() as? Result.Ok)?.value.orEmpty()
                if (newest.isNotEmpty()) publish("newest", isContent = true) { it.copy(newest = newest) }
            }
            launch {
                val frequent = (frequentD.await() as? Result.Ok)?.value.orEmpty()
                if (frequent.isNotEmpty()) {
                    lastFrequent = frequent
                    publish("frequent", isContent = true) { it.copy(frequent = frequent) }
                    publishSpeedDial(buildSpeedDial(pins, lastRecent, lastFrequent, lastPlaylists))
                    launch { loadMoreFromArtist(frequent) }
                }
            }
            launch {
                val songs = (songsD.await() as? Result.Ok)?.value.orEmpty()
                if (songs.isNotEmpty()) publish("quickPicks", isContent = true) { it.copy(quickPicks = songs) }
            }
            launch {
                val random = (randomD.await() as? Result.Ok)?.value.orEmpty()
                if (random.isNotEmpty()) {
                    val recentIds = _ui.value.recent.map { it.id }.toSet()
                    val discover = random.firstOrNull { it.id !in recentIds } ?: random.firstOrNull()
                    if (discover != null) publish("discover", isContent = false) { it.copy(discover = discover) }
                }
            }
            launch {
                val playlists = (playlistsD.await() as? Result.Ok)?.value.orEmpty()
                val starred = (starredD.await() as? Result.Ok)?.value
                val recent = (recentD.await() as? Result.Ok)?.value.orEmpty()
                val recentAlbumIds = (recent.map { it.id } + _ui.value.recent.map { it.id }).toSet()
                val recentAlbumNames = (recent.map { it.name.trim().lowercase() } + _ui.value.recent.map { it.name.trim().lowercase() })
                    .filter { it.isNotBlank() }
                    .toSet()
                val songs = (songsD.await() as? Result.Ok)?.value.orEmpty()
                val quickPickIds = (songs.map { it.id } + _ui.value.quickPicks.map { it.id }).toSet()

                // Fetch songs from user playlists
                val playlistTracks = supervisorScope {
                    playlists.take(10).map { p ->
                        async {
                            (repo.playlist(p.id) as? Result.Ok)?.value?.tracks.orEmpty()
                        }
                    }.awaitAll().flatten()
                }

                val starredTracks = starred?.tracks.orEmpty()
                val allFavorites = (playlistTracks + starredTracks).distinctBy { it.id }

                // Filter out recently played tracks or tracks currently in Quick picks
                val notPlayedRecently = allFavorites.filter { t ->
                    t.albumId !in recentAlbumIds &&
                    t.album.trim().lowercase() !in recentAlbumNames &&
                    t.id !in quickPickIds
                }

                val forgottenTracks = if (notPlayedRecently.size >= 24) {
                    notPlayedRecently.take(24)
                } else {
                    (notPlayedRecently + allFavorites.filter { it !in notPlayedRecently }).take(24)
                }

                if (forgottenTracks.isNotEmpty()) {
                    publish("forgotten", isContent = false) {
                        it.copy(
                            forgottenAlbums = emptyList(),
                            forgottenTracks = forgottenTracks,
                        )
                    }
                }
            }
            launch {
                val playlists = (playlistsD.await() as? Result.Ok)?.value.orEmpty()
                if (playlists.isNotEmpty()) {
                    lastPlaylists = playlists
                    publishSpeedDial(buildSpeedDial(pins, lastRecent, lastFrequent, lastPlaylists))
                }
            }
            launch {
                loadMixes(
                    genre = genreParam,
                    recent = (recentD.await() as? Result.Ok<List<Album>>)?.value.orEmpty(),
                    frequent = (frequentD.await() as? Result.Ok<List<Album>>)?.value.orEmpty(),
                    songs = (songsD.await() as? Result.Ok<List<Track>>)?.value.orEmpty(),
                    starredTracks = (starredD.await() as? Result.Ok<StarredCollection>)?.value?.tracks.orEmpty(),
                    playlists = (playlistsD.await() as? Result.Ok<List<Playlist>>)?.value.orEmpty(),
                )
            }
        }

        // Only report "nothing loaded" when no section produced content, and never
        // leave Home stuck on skeletons if only non-content sections arrived.
        val hasAnything = _ui.value.recent.isNotEmpty() || _ui.value.newest.isNotEmpty() ||
            _ui.value.quickPicks.isNotEmpty() || _ui.value.frequent.isNotEmpty() ||
            _ui.value.mixes.isNotEmpty() || _ui.value.forgottenTracks.isNotEmpty() ||
            _ui.value.genres.size > 1
        if (!firstContent.get() && !hasAnything) {
            _ui.value = _ui.value.copy(
                loading = false,
                refreshing = false,
                globalError = "Couldn't load anything. Check your connection and retry.",
            )
        } else {
            if (_ui.value.loading || _ui.value.refreshing) {
                _ui.value = _ui.value.copy(loading = false, refreshing = false)
            }
            cache.saveDisk(genre, _ui.value.copy(loading = false, refreshing = false))
        }
    }

    private suspend fun loadMoreFromArtist(frequent: List<Album>) {
        val top = frequent.groupBy { it.artistId ?: it.artist }
            .mapValues { e -> e.value.sumOf { it.playCount } }
            .maxByOrNull { it.value } ?: return
        val byId = frequent.firstOrNull { (it.artistId ?: it.artist) == top.key } ?: return
        val detail = byId.artistId?.let { (repo.artist(it) as? Result.Ok)?.value }
        if (detail != null) {
            _ui.value = _ui.value.copy(
                moreArtist = Artist(detail.id, detail.name, detail.coverArt, detail.albumCount, detail.starred),
                moreArtistAlbums = detail.albums,
            )
        } else {
            _ui.value = _ui.value.copy(
                moreArtist = Artist(byId.artistId ?: "", byId.artist, null, 0, false),
                moreArtistAlbums = frequent.filter { (it.artistId ?: it.artist) == top.key },
            )
        }
    }

    /**
     * Mixes are extra content; they must never block the rest of Home.
     *
     * Always publishes exactly 5 deterministic mixes (when the library holds
     * at least 5 songs) via [MixGenerator]. The seed advances on every load,
     * so pull-to-refresh or a genre change re-seeds the shelf instead of
     * replaying identical mixes.
     */
    private suspend fun loadMixes(
        genre: String?,
        recent: List<Album>,
        frequent: List<Album>,
        songs: List<Track>,
        starredTracks: List<Track>,
        playlists: List<Playlist>,
    ) {
        val batch = settings.flow.first().radioBatchSize
        val seed = (genre ?: "All").hashCode().toLong() * 31L + (mixGeneration++)
        // Pool depth: one extra page so each of the 5 mixes can fill `batch`
        // tracks without starving on mid-size libraries.
        val extra = (repo.randomSongs(batch * 5, genre) as? Result.Ok<List<Track>>)?.value.orEmpty()
        val playlistTracks = supervisorScope {
            playlists.take(10).map { p ->
                async { (repo.playlist(p.id) as? Result.Ok<PlaylistDetail>)?.value?.tracks.orEmpty() }
            }.awaitAll().flatten()
        }
        val generated = MixGenerator.generate(
            randomSongs = (songs + extra).distinctBy { it.id },
            starredTracks = starredTracks,
            playlistTracks = playlistTracks,
            frequentAlbums = frequent.ifEmpty { lastFrequent },
            recentAlbums = recent.ifEmpty { lastRecent },
            batchSize = batch,
            seed = seed,
            genreFilter = genre,
        )
        if (generated.isNotEmpty()) _ui.value = _ui.value.copy(mixes = generated.map { Mix(it.name, it.tracks) })
    }

    /** Warm the next few genres so their chips switch without a network wait. */
    private fun prefetchNeighbours(current: String) {
        val genres = _ui.value.genres.filter { it != current && it != "All" }.take(3)
        val started = android.os.SystemClock.elapsedRealtime()
        for (g in genres) {
            if (!prefetched.add(g)) continue
            viewModelScope.launch {
                val albums = (repo.albumList("byGenre", 20, 0, g) as? Result.Ok)?.value.orEmpty()
                val songs = (repo.randomSongs(24, g) as? Result.Ok)?.value.orEmpty()
                if (albums.isNotEmpty()) {
                    cache.put(
                        g,
                        HomeUi(
                            loading = true,
                            genres = _ui.value.genres,
                            selectedGenre = g,
                            recent = albums,
                            newest = albums,
                            quickPicks = songs,
                        ),
                    )
                    VLog.d(
                        "HomePerf",
                        "prefetched '$g' +${android.os.SystemClock.elapsedRealtime() - started}ms",
                    )
                }
            }
        }
    }

    private suspend fun buildSpeedDial(
        pins: Set<String>,
        recent: List<Album>,
        frequent: List<Album>,
        playlists: List<Playlist>,
    ): List<SpeedTile> {
        val tiles = ArrayList<SpeedTile>()
        val pinnedTiles = supervisorScope {
            pins.map { key ->
                async {
                    val parts = key.split(":", limit = 2)
                    val type = parts.getOrElse(0) { "" }
                    val id = parts.getOrElse(1) { "" }
                    when (type) {
                        "album" -> (repo.album(id) as? Result.Ok)?.value?.let {
                            SpeedTile(
                                key, it.name, it.artist, it.coverArt,
                                target = SpeedTarget.AlbumTarget(
                                    Album(
                                        it.id, it.name, it.artist, it.artistId, it.coverArt,
                                        it.songCount, it.durationSec, 0, it.year, it.genre, it.starred,
                                    ),
                                ),
                            )
                        }
                        "playlist" -> (repo.playlist(id) as? Result.Ok)?.value?.let { pl ->
                            // PlaylistDetail carries no server cover id, so build the
                            // cover from the playlist's own tracks.
                            val cover = PlaylistCover.select(null, pl.tracks.map { t -> t.coverArt })
                            SpeedTile(
                                key, pl.name, "${pl.songCount} songs", null,
                                playlistCover = cover,
                                target = SpeedTarget.PlaylistTarget(
                                    Playlist(pl.id, pl.name, pl.songCount, pl.durationSec, null),
                                ),
                            )
                        }
                        else -> null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        tiles += pinnedTiles

        val seenTitles = tiles.map { it.title }.toMutableSet()
        for (a in (recent + frequent).distinctBy { it.id }) {
            if (tiles.size >= 27) break
            if (seenTitles.add(a.name)) {
                tiles += SpeedTile(
                    "album:${a.id}", a.name, a.artist, a.coverArt,
                    target = SpeedTarget.AlbumTarget(a),
                )
            }
        }
        // Bounded extra fetches: playlists usually have no server art, so we look
        // up a few of them to build the 2x2 collage the design calls for.
        var collageBudget = 5
        for (p in playlists) {
            if (tiles.size >= 27) break
            if (!seenTitles.add(p.name)) continue
            var cover = PlaylistCover.select(p.coverArt, emptyList())
            if (cover !is PlaylistCover.Server && collageBudget > 0) {
                collageBudget--
                val detail = (repo.playlist(p.id) as? Result.Ok)?.value
                if (detail != null) cover = PlaylistCover.select(null, detail.tracks.map { t -> t.coverArt })
            }
            tiles += SpeedTile(
                "playlist:${p.id}", p.name, "${p.songCount} songs", p.coverArt,
                playlistCover = cover,
                target = SpeedTarget.PlaylistTarget(p),
            )
        }
        return tiles
    }
}
