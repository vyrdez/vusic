package com.feishin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    private var lastRecent: List<Album> = emptyList()
    private var lastFrequent: List<Album> = emptyList()
    private var lastPlaylists: List<Playlist> = emptyList()

    init {
        refresh()
        observePins()
    }

    /** Rebuilds just the speed dial when pins change, without refetching everything. */
    private fun observePins() {
        viewModelScope.launch {
            settings.flow.map { it.pins }.distinctUntilChanged().collect { pins ->
                if (lastRecent.isEmpty() && lastFrequent.isEmpty() && lastPlaylists.isEmpty()) return@collect
                val tiles = buildSpeedDial(pins, lastRecent, lastFrequent, lastPlaylists)
                _ui.value = _ui.value.copy(speedDial = tiles.take(9))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, globalError = null)
            loadAll(_ui.value.selectedGenre)
        }
    }

    fun selectGenre(genre: String) {
        if (genre == _ui.value.selectedGenre) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(selectedGenre = genre, loading = true)
            loadAll(genre)
        }
    }

    private suspend fun CoroutineScope.loadAll(genre: String) {
        val genreParam = genre.takeIf { it != "All" }
        val pins = settings.pinsNow()
        val genresD = async { repo.genres() }
        val recentD = async { repo.albumList(if (genreParam != null) "byGenre" else "recent", 20, 0, genreParam) }
        val newestD = async { repo.albumList(if (genreParam != null) "byGenre" else "newest", 20, 0, genreParam) }
        val frequentD = async { repo.albumList(if (genreParam != null) "byGenre" else "frequent", 20, 0, genreParam) }
        val randomD = async { repo.albumList("random", 20, 0) }
        val songsD = async { repo.randomSongs(24, genreParam) }
        val starredD = async { repo.starred() }
        val playlistsD = async { repo.playlists() }

        val genres = (genresD.await() as? Result.Ok)?.value.orEmpty()
            .sortedByDescending { it.songCount }.take(10).map { it.name }
        val recent = (recentD.await() as? Result.Ok)?.value.orEmpty()
        val newest = (newestD.await() as? Result.Ok)?.value.orEmpty()
        val frequent = (frequentD.await() as? Result.Ok)?.value.orEmpty()
        val random = (randomD.await() as? Result.Ok)?.value.orEmpty()
        val songs = (songsD.await() as? Result.Ok)?.value.orEmpty()
        val starred = (starredD.await() as? Result.Ok)?.value
        val playlists = (playlistsD.await() as? Result.Ok)?.value.orEmpty()

        val recentIds = recent.map { it.id }.toSet()
        val forgottenAlbums = starred?.albums.orEmpty().filter { it.id !in recentIds }.take(10)
        val forgottenTracks = starred?.tracks.orEmpty().take(5)

        // Speed dial: pinned items first, then recent + frequent + playlists.
        lastRecent = recent
        lastFrequent = frequent
        lastPlaylists = playlists
        val tiles = buildSpeedDial(pins, recent, frequent, playlists)

        // More from <artist>: most-played artist across frequent albums.
        val topArtistEntry = frequent.groupBy { it.artistId ?: it.artist }
            .mapValues { e -> e.value.sumOf { it.playCount } }
            .maxByOrNull { it.value }
        var moreArtist: Artist? = null
        var moreArtistAlbums: List<Album> = emptyList()
        val topId = topArtistEntry?.key
        if (topId != null) {
            val byId = frequent.firstOrNull { (it.artistId ?: it.artist) == topId }
            val detail = byId?.artistId?.let { (repo.artist(it) as? Result.Ok)?.value }
            if (detail != null) {
                moreArtist = Artist(detail.id, detail.name, detail.coverArt, detail.albumCount, detail.starred)
                moreArtistAlbums = detail.albums
            } else if (byId != null) {
                moreArtist = Artist(byId.artistId ?: "", byId.artist, null, 0, false)
                moreArtistAlbums = frequent.filter { (it.artistId ?: it.artist) == topId }
            }
        }

        // Mixed for you: one mix per top artist seed, falling back to genre randoms.
        val mixes = ArrayList<Mix>()
        val seeds = frequent.mapNotNull { it.artistId ?: it.artist }.distinct().take(3)
        val seedJobs = seeds.mapIndexed { i, seed ->
            async {
                val sim = repo.similarSongs(null, seed.takeIf { frequent.any { a -> a.artistId == seed } }, 25)
                val list = (sim as? Result.Ok)?.value.orEmpty()
                if (list.size >= 5) Mix("My Mix ${i + 1}", list) else null
            }
        }
        mixes += seedJobs.awaitAll().filterNotNull()
        if (mixes.isEmpty() && songs.size >= 10) {
            mixes += Mix("My Mix 1", songs.shuffled().take(25))
        }

        val discover = random.firstOrNull { it.id !in recentIds } ?: random.firstOrNull()

        val failedAll = recent.isEmpty() && newest.isEmpty() && frequent.isEmpty() &&
            songs.isEmpty() && random.isEmpty() && starred == null && playlists.isEmpty()

        _ui.value = HomeUi(
            loading = false,
            genres = listOf("All") + genres,
            selectedGenre = genre,
            speedDial = tiles.take(9),
            recent = recent,
            quickPicks = songs,
            newest = newest,
            forgottenAlbums = forgottenAlbums,
            forgottenTracks = forgottenTracks,
            moreArtist = moreArtist,
            moreArtistAlbums = moreArtistAlbums,
            mixes = mixes,
            discover = discover,
            frequent = frequent,
            globalError = if (failedAll) "Couldn't load anything. Check your connection and retry." else null,
        )
    }

    private suspend fun CoroutineScope.buildSpeedDial(
        pins: Set<String>,
        recent: List<Album>,
        frequent: List<Album>,
        playlists: List<Playlist>,
    ): List<SpeedTile> {
        val tiles = ArrayList<SpeedTile>()
        val pinnedJobs = pins.map { key ->
            async {
                val (type, id) = key.split(":", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                when (type) {
                    "album" -> (repo.album(id) as? Result.Ok)?.value?.let {
                        SpeedTile(key, it.name, it.artist, it.coverArt, SpeedTarget.AlbumTarget(
                            Album(it.id, it.name, it.artist, it.artistId, it.coverArt, it.songCount, it.durationSec, 0, it.year, it.genre, it.starred),
                        ))
                    }
                    "playlist" -> (repo.playlist(id) as? Result.Ok)?.value?.let {
                        SpeedTile(key, it.name, "${it.songCount} songs", null, SpeedTarget.PlaylistTarget(
                            Playlist(it.id, it.name, it.songCount, it.durationSec, null),
                        ))
                    }
                    else -> null
                }
            }
        }
        tiles += pinnedJobs.awaitAll().filterNotNull()
        val seenTitles = tiles.map { it.title }.toMutableSet()
        for (a in (recent + frequent).distinctBy { it.id }) {
            if (tiles.size >= 9) break
            if (seenTitles.add(a.name)) {
                tiles += SpeedTile("album:${a.id}", a.name, a.artist, a.coverArt, SpeedTarget.AlbumTarget(a))
            }
        }
        for (p in playlists) {
            if (tiles.size >= 9) break
            if (seenTitles.add(p.name)) {
                tiles += SpeedTile("playlist:${p.id}", p.name, "${p.songCount} songs", p.coverArt, SpeedTarget.PlaylistTarget(p))
            }
        }
        return tiles
    }
}
