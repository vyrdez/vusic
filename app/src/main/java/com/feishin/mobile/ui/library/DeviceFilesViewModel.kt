package com.feishin.mobile.ui.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.DeviceFilesFilter
import com.feishin.mobile.core.common.SortChoice
import com.feishin.mobile.data.settings.LayoutMode
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceFilesState(
    val songs: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val selectedTab: String = "SONGS",
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val hasPermission: Boolean = true,
    val isLoading: Boolean = false,
    val selectedAlbum: AlbumDetail? = null,
    val selectedArtist: ArtistDetail? = null,
)

@HiltViewModel
class DeviceFilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: PlayerManager,
    val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceFilesState())
    val state: StateFlow<DeviceFilesState> = _state.asStateFlow()

    private var allTracks: List<Track> = emptyList()
    private var allAlbums: List<Album> = emptyList()
    private var allArtists: List<Artist> = emptyList()

    private var contentObserver: ContentObserver? = null
    private var reloadJob: Job? = null

    init {
        registerObserver()
        checkAndLoad()
    }

    private fun requiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun checkAndLoad() {
        val perm = requiredPermission()
        val granted = ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _state.value = _state.value.copy(hasPermission = false, isLoading = false)
            return
        }
        _state.value = _state.value.copy(hasPermission = true)
        loadData()
    }

    fun loadData() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val s = settings.flow.first()
            val minTrackSec = s.deviceFilesMinDurationSec

            val (tracks, albums, artists) = try {
                withContext(Dispatchers.IO) {
                    queryMediaStore(minTrackSec)
                }
            } catch (_: SecurityException) {
                // Permission was revoked between the check and the query.
                _state.value = _state.value.copy(hasPermission = false, isLoading = false)
                return@launch
            }

            allTracks = tracks
            allAlbums = albums
            allArtists = artists

            applyFilterAndSort()
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun registerObserver() {
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadJob?.cancel()
                reloadJob = viewModelScope.launch {
                    delay(500) // Debounce observer triggers
                    // The permission may have been revoked since the last load.
                    checkAndLoad()
                }
            }
        }
        context.contentResolver.registerContentObserver(uri, true, contentObserver!!)
    }

    override fun onCleared() {
        super.onCleared()
        contentObserver?.let { context.contentResolver.unregisterContentObserver(it) }
    }

    private fun queryMediaStore(minTrackSec: Int): Triple<List<Track>, List<Album>, List<Artist>> {
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val musicCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
            val ringCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
            val alarmCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)
            val notifCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val isMusic = c.getInt(musicCol) != 0
                val isRingtone = c.getInt(ringCol) != 0
                val isAlarm = c.getInt(alarmCol) != 0
                val isNotif = c.getInt(notifCol) != 0
                val durMs = c.getLong(durCol)

                if (!DeviceFilesFilter.shouldInclude(durMs, minTrackSec, isMusic, isRingtone, isAlarm, isNotif)) {
                    continue
                }

                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val rawTitle = c.getString(titleCol) ?: ""
                val title = rawTitle.ifBlank { "Track $id" }
                val rawArtist = c.getString(artistCol) ?: ""
                val artist = if (rawArtist.isBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else rawArtist
                val rawAlbum = c.getString(albumCol) ?: ""
                val album = if (rawAlbum.isBlank() || rawAlbum.equals("<unknown>", ignoreCase = true)) "Unknown Album" else rawAlbum
                val albumId = c.getLong(albumIdCol)
                val trackNo = c.getInt(trackCol).takeIf { it > 0 }
                val year = c.getInt(yearCol).takeIf { it > 0 }
                val size = c.getLong(sizeCol)
                val path = c.getString(dataCol) ?: ""
                val modSec = c.getLong(modCol)

                val albumKey = "local_album:$albumId"
                val artistKey = "local_artist:${artist.hashCode()}"

                tracks += Track(
                    id = "local:$id",
                    title = title,
                    album = album,
                    albumId = albumKey,
                    artist = artist,
                    artistId = artistKey,
                    coverArt = uri,
                    durationSec = (durMs / 1000).toInt(),
                    trackNo = trackNo,
                    year = year,
                    genre = null,
                    suffix = path.substringAfterLast('.', "").ifBlank { null },
                    starred = false,
                    sizeBytes = size,
                    bitRate = null,
                    path = path,
                    localUri = uri,
                    localModifiedSec = modSec,
                )
            }
        }

        // Group into Albums
        val albums = tracks.groupBy { it.albumId ?: it.album }.map { (albKey, albTracks) ->
            val first = albTracks.first()
            Album(
                id = albKey,
                name = first.album,
                artist = first.artist,
                artistId = first.artistId,
                coverArt = first.coverArt,
                songCount = albTracks.size,
                durationSec = albTracks.sumOf { it.durationSec },
                playCount = 0,
                year = albTracks.firstNotNullOfOrNull { it.year },
                genre = null,
                starred = false,
            )
        }

        // Group into Artists
        val artists = tracks.groupBy { it.artistId ?: it.artist }.map { (artKey, artTracks) ->
            val first = artTracks.first()
            val distinctAlbumCount = artTracks.mapNotNull { it.albumId }.distinct().size.coerceAtLeast(1)
            Artist(
                id = artKey,
                name = first.artist,
                coverArt = first.coverArt,
                albumCount = distinctAlbumCount,
                starred = false,
            )
        }

        return Triple(tracks, albums, artists)
    }

    fun selectTab(tab: String) {
        _state.value = _state.value.copy(selectedTab = tab)
        applyFilterAndSort()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilterAndSort()
    }

    fun toggleSearching(searching: Boolean) {
        _state.value = _state.value.copy(
            isSearching = searching,
            searchQuery = if (!searching) "" else _state.value.searchQuery,
        )
        applyFilterAndSort()
    }

    fun openAlbum(albumId: String) {
        val album = allAlbums.find { it.id == albumId } ?: return
        val albTracks = allTracks.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.trackNo ?: 0 }, { it.title.lowercase() }))
        val detail = AlbumDetail(
            id = album.id,
            name = album.name,
            artist = album.artist,
            artistId = album.artistId,
            coverArt = album.coverArt,
            songCount = albTracks.size,
            durationSec = albTracks.sumOf { it.durationSec },
            year = album.year,
            genre = album.genre,
            starred = false,
            tracks = albTracks,
        )
        _state.value = _state.value.copy(selectedAlbum = detail)
    }

    fun closeAlbum() {
        _state.value = _state.value.copy(selectedAlbum = null)
    }

    fun openArtist(artistId: String) {
        val artist = allArtists.find { it.id == artistId } ?: return
        val artAlbums = allAlbums.filter { it.artistId == artistId }
        val detail = ArtistDetail(
            id = artist.id,
            name = artist.name,
            coverArt = artist.coverArt,
            albumCount = artAlbums.size,
            starred = false,
            albums = artAlbums,
        )
        _state.value = _state.value.copy(selectedArtist = detail)
    }

    fun closeArtist() {
        _state.value = _state.value.copy(selectedArtist = null)
    }

    fun playSong(track: Track) {
        player.playAll(listOf(track), startIndex = 0, source = "Device files")
    }

    fun playSongs(tracks: List<Track>, startIndex: Int = 0) {
        player.playAll(tracks, startIndex = startIndex, source = "Device files")
    }

    fun shuffleAll() {
        if (_state.value.songs.isNotEmpty()) {
            player.playAll(_state.value.songs, startIndex = 0, shuffle = true, source = "Device files")
        }
    }

    private fun applyFilterAndSort() {
        val query = _state.value.searchQuery
        val defaultSort = SortChoice("TITLE", true)

        val filteredSongs = DeviceFilesFilter.filterAndSort(allTracks, query, defaultSort)

        val filteredAlbums = if (query.isBlank()) {
            allAlbums.sortedBy { it.name.lowercase() }
        } else {
            val q = query.trim().lowercase()
            allAlbums.filter { it.name.lowercase().contains(q) || it.artist.lowercase().contains(q) }
                .sortedBy { it.name.lowercase() }
        }

        val filteredArtists = if (query.isBlank()) {
            allArtists.sortedBy { it.name.lowercase() }
        } else {
            val q = query.trim().lowercase()
            allArtists.filter { it.name.lowercase().contains(q) }
                .sortedBy { it.name.lowercase() }
        }

        _state.value = _state.value.copy(
            songs = filteredSongs,
            albums = filteredAlbums,
            artists = filteredArtists,
        )
    }
}
