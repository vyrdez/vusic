package com.feishin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistCover
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

    init {
        observePins()
        viewModelScope.launch {
            // Stale-while-revalidate: paint the last snapshot immediately (not as
            // "loading") so a cold start shows content before the network answers.
            cache.loadDisk(_ui.value.selectedGenre)?.let { cached ->
                if (_ui.value.recent.isEmpty()) {
                    _ui.value = cached.copy(loading = false)
                    android.util.Log.d("HomePerf", "painted disk snapshot")
                }
            }
            refresh()
        }
    }

    fun refresh() = load(_ui.value.selectedGenre)

    fun selectGenre(genre: String) {
        if (genre == _ui.value.selectedGenre && !_ui.value.loading) return
        load(genre)
    }

    private fun load(genre: String) {
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
            cached.copy(loading = false)
        } else {
            HomeUi(loading = true, selectedGenre = genre, genres = listOf("All"))
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
        if (tiles.isNotEmpty()) _ui.value = _ui.value.copy(speedDial = tiles.take(18))
    }

    /**
     * Every section publishes independently. The previous version awaited all of
     * them — including similar-songs mixes, which take 7-9s on Navidrome — before
     * emitting anything, so Home sat on skeletons for the whole duration.
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
            android.util.Log.d(
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
                val starred = (starredD.await() as? Result.Ok)?.value
                if (starred != null) {
                    val recentIds = _ui.value.recent.map { it.id }.toSet()
                    publish("starred", isContent = false) {
                        it.copy(
                            forgottenAlbums = starred.albums.filter { a -> a.id !in recentIds }.take(10),
                            forgottenTracks = starred.tracks.take(5),
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
            launch { loadMixes() }
        }

        // Only report "nothing loaded" when no section produced content, and never
        // leave Home stuck on skeletons if only non-content sections arrived.
        val hasAnything = _ui.value.recent.isNotEmpty() || _ui.value.newest.isNotEmpty() ||
            _ui.value.quickPicks.isNotEmpty() || _ui.value.frequent.isNotEmpty() ||
            _ui.value.mixes.isNotEmpty() || _ui.value.genres.size > 1
        if (!firstContent.get() && !hasAnything) {
            _ui.value = _ui.value.copy(
                loading = false,
                globalError = "Couldn't load anything. Check your connection and retry.",
            )
        } else {
            if (_ui.value.loading) _ui.value = _ui.value.copy(loading = false)
            cache.saveDisk(genre, _ui.value.copy(loading = false))
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

    /** Mixes are extra content; they must never block the rest of Home. */
    private suspend fun loadMixes() {
        val seeds = _ui.value.frequent.mapNotNull { it.artistId }.distinct().take(3)
        val mixes = ArrayList<Mix>()
        val found = supervisorScope {
            seeds.mapIndexed { i, seed ->
                async {
                    val similar = (repo.similarSongs(null, seed, 25) as? Result.Ok)?.value.orEmpty()
                    if (similar.size >= 5) Mix("My Mix ${i + 1}", similar) else null
                }
            }.awaitAll().filterNotNull()
        }
        mixes += found
        if (mixes.isEmpty()) {
            val songs = _ui.value.quickPicks
            if (songs.size >= 10) mixes += Mix("My Mix 1", songs.shuffled().take(25))
        }
        if (mixes.isNotEmpty()) _ui.value = _ui.value.copy(mixes = mixes)
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
                    android.util.Log.d(
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
            if (tiles.size >= 18) break
            if (seenTitles.add(a.name)) {
                tiles += SpeedTile(
                    "album:${a.id}", a.name, a.artist, a.coverArt,
                    target = SpeedTarget.AlbumTarget(a),
                )
            }
        }
        // Bounded extra fetches: playlists usually have no server art, so we look
        // up a few of them to build the 2x2 collage the design calls for.
        var collageBudget = 3
        for (p in playlists) {
            if (tiles.size >= 18) break
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
