package com.feishin.mobile.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.core.common.PlaylistDuplicates
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.favorites.FAVORITE_SONGS_PLAYLIST_ID
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.data.favorites.PlaylistEvent
import com.feishin.mobile.data.favorites.PlaylistEvents
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.speedDialKey
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistCover
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.theme.VusicColors
import kotlinx.coroutines.launch

sealed interface ActionTarget {
    data class Track(val track: com.feishin.mobile.domain.model.Track) : ActionTarget
    data class Album(
        val album: com.feishin.mobile.domain.model.Album,
        val tracks: List<com.feishin.mobile.domain.model.Track>? = null,
    ) : ActionTarget
    data class Artist(val artist: com.feishin.mobile.domain.model.Artist) : ActionTarget
    data class Playlist(
        val playlist: com.feishin.mobile.domain.model.Playlist,
        val tracks: List<com.feishin.mobile.domain.model.Track>? = null,
    ) : ActionTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityActionsSheet(
    target: ActionTarget?,
    onDismiss: () -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    favs: FavoritesStore,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents? = null,
    onStartRadio: () -> Unit = {},
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    if (target == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (target) {
            is ActionTarget.Track -> TrackSheetContent(target.track, onDismiss, player, repo, settings, messenger, favs, downloads, playlistEvents, onStartRadio, onAlbum, onArtist)
            is ActionTarget.Album -> AlbumSheetContent(target.album, target.tracks, onDismiss, player, repo, settings, messenger, downloads, playlistEvents, onStartRadio, onAlbum, onArtist)
            is ActionTarget.Artist -> ArtistSheetContent(target.artist, onDismiss, player, repo, settings, messenger, downloads, playlistEvents, onStartRadio, onAlbum, onArtist)
            is ActionTarget.Playlist -> PlaylistSheetContent(target.playlist, target.tracks, onDismiss, player, repo, settings, messenger, downloads, playlistEvents, onAlbum, onArtist)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    starred: Boolean,
    starEnabled: Boolean,
    onStar: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (starEnabled) {
                IconButton(onClick = onStar, modifier = Modifier.size(48.dp)) {
                    Icon(if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = Color.White)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ActionTiles(
    onPlayNext: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionTile("Play next", Icons.AutoMirrored.Filled.PlaylistPlay, Modifier.weight(1f), onPlayNext)
        ActionTile("Save to playlist", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), onSaveToPlaylist)
        ActionTile("Share", Icons.Default.Share, Modifier.weight(1f), onShare)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.4f)
                .clip(RoundedCornerShape(16.dp)).background(VusicColors.SurfaceHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else VusicColors.SecondaryText, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.White else VusicColors.SecondaryText,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = VusicColors.SecondaryText)
        }
    }
}



@Composable
private fun TrackSheetContent(
    track: Track,
    onDismiss: () -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    favs: FavoritesStore,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents?,
    onStartRadio: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var starred by remember(track.id) { mutableStateOf(track.starred) }
    // Guarded optimistic star + convergence to the server collection, like the
    // Now Playing chip: the passed track snapshot can be stale.
    var starring by remember(track.id) { mutableStateOf(false) }
    val favTracks by favs.tracks.collectAsState()
    val favsLoaded by favs.loaded.collectAsState()
    LaunchedEffect(track.id, favTracks, favsLoaded) {
        if (favsLoaded && !starring) {
            starred = favTracks.any { it.id == track.id }
        }
    }
    var showPicker by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val pins by settings.flow.collectAsState(initial = null)
    val pinKey = speedDialKey("album", track.albumId ?: track.album)
    val pinned = pins?.pins?.contains(pinKey) == true

    fun closeAnd(block: () -> Unit) {
        onDismiss()
        block()
    }

    SheetHeader(
        title = track.title, subtitle = track.artist, starred = starred, starEnabled = true,
        onStar = {
            if (starring) return@SheetHeader
            val next = !starred
            starred = next
            starring = true
            scope.launch {
                when (favs.setTrackStarred(track.id, next)) {
                    is Result.Ok -> messenger.show(if (next) "Saved to library" else "Removed from library")
                    is Result.Err -> {
                        starred = !next
                        messenger.show("Could not update favorite")
                    }
                }
                starring = false
            }
        },
        onClose = onDismiss,
    )
    ActionTiles(
        onPlayNext = {
            player.playNext(track)
            messenger.show("Playing next: ${track.title}")
            onDismiss()
        },
        onSaveToPlaylist = { showPicker = true },
        onShare = {
            shareText(context, "${track.title} - ${track.artist}")
            onDismiss()
        },
    )
    SheetRow("Start radio", Icons.Default.Radio, onClick = {
        // The seed starts instantly as item 1; the 20-song batch fills in
        // behind it and endless appends keep it going.
        player.startRadio(track) { messenger.show("Your library is empty") }
        onDismiss()
        onStartRadio()
    })
    SheetRow("Add to queue", Icons.Default.QueueMusic, onClick = {
        player.addToEnd(track)
        messenger.show("Added to queue: ${track.title}")
        onDismiss()
    })
    SheetRow(if (starred) "Remove from library" else "Save to library",
        if (starred) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
        onClick = {
            if (starring) return@SheetRow
            val next = !starred
            starred = next
            starring = true
            scope.launch {
                when (favs.setTrackStarred(track.id, next)) {
                    is Result.Ok -> messenger.show(if (next) "Saved to library" else "Removed from library")
                    is Result.Err -> {
                        starred = !next
                        messenger.show("Could not update favorite")
                    }
                }
                starring = false
            }
        })
    DownloadTrackRow(track, downloads, messenger, scope)
    if (track.albumId?.isNotBlank() == true) {
        SheetRow("Go to album", Icons.Default.Album, onClick = { closeAnd { onAlbum(track.albumId) } })
    }
    if (track.artistId?.isNotBlank() == true) {
        SheetRow("Go to artist", Icons.Default.Person, onClick = { closeAnd { onArtist(track.artistId) } })
    }
    SheetRow("View song details", Icons.Default.Info, onClick = { showDetails = true })
    SheetRow(if (pinned) "Unpin from Speed dial" else "Pin to Speed dial", Icons.Default.PushPin, onClick = {
        scope.launch {
            settings.setPinned(pinKey, !pinned)
            messenger.show(if (!pinned) "Pinned to Speed dial" else "Unpinned from Speed dial")
        }
    })

    if (showPicker) {
        PlaylistPickerSheet(tracks = listOf(track), title = track.title, onDismiss = { showPicker = false }, repo = repo, messenger = messenger, playlistEvents = playlistEvents)
    }
    if (showDetails) {
        TrackDetailsDialog(track = track, onDismiss = { showDetails = false })
    }
}

// ---------- Album sheet ----------

@Composable
private fun AlbumSheetContent(
    album: Album,
    preloaded: List<Track>?,
    onDismiss: () -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents?,
    onStartRadio: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var starred by remember(album.id) { mutableStateOf(album.starred) }
    var showPicker by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AlbumDetail?>(null) }
    val pins by settings.flow.collectAsState(initial = null)
    val pinKey = speedDialKey("album", album.id)
    val pinned = pins?.pins?.contains(pinKey) == true

    LaunchedEffect(album.id) {
        if (preloaded == null) {
            detail = (repo.album(album.id) as? Result.Ok)?.value
        }
    }
    val tracks = preloaded ?: detail?.tracks.orEmpty()
    val knownDetail = detail ?: preloaded?.let {
        AlbumDetail(album.id, album.name, album.artist, album.artistId, album.coverArt, it.size, it.sumOf { t -> t.durationSec }, album.year, album.genre, starred, it)
    }

    fun closeAnd(block: () -> Unit) {
        onDismiss()
        block()
    }

    SheetHeader(
        title = album.name, subtitle = album.artist, starred = starred, starEnabled = true,
        onStar = {
            val next = !starred
            starred = next
            scope.launch {
                when (repo.starAlbum(album.id, next)) {
                    is Result.Ok -> playlistEvents?.emit(PlaylistEvent.FavoritesChanged)
                    is Result.Err -> {
                        starred = !next
                        messenger.show("Could not update favorite")
                    }
                }
            }
        },
        onClose = onDismiss,
    )
    ActionTiles(
        onPlayNext = {
            if (tracks.isNotEmpty()) {
                player.playNextAll(tracks)
                messenger.show("Playing next: ${album.name}")
            } else messenger.show("Album tracks not loaded yet")
            onDismiss()
        },
        onSaveToPlaylist = { showPicker = true },
        onShare = {
            shareText(context, "${album.name} - ${album.artist}")
            onDismiss()
        },
    )
    SheetRow("Play", Icons.Default.PlayArrow, onClick = {
        if (tracks.isNotEmpty()) player.playAll(tracks, source = album.name)
        onDismiss()
    })
    SheetRow("Shuffle", Icons.Default.Shuffle, onClick = {
        if (tracks.isNotEmpty()) player.playAll(tracks, shuffle = true, source = album.name)
        onDismiss()
    })
    SheetRow("Add to queue", Icons.Default.QueueMusic, onClick = {
        player.addAllToEnd(tracks)
        messenger.show("Added to queue: ${album.name}")
        onDismiss()
    })
    SheetRow("Start radio", Icons.Default.Radio, onClick = {
        // The album's first song is the seed and starts instantly.
        scope.launch {
            val seed = tracks.firstOrNull()
                ?: (repo.album(album.id) as? Result.Ok)?.value?.tracks?.firstOrNull()
            if (seed == null) {
                messenger.show("No tracks found for ${album.name}")
            } else {
                player.startRadio(seed) { messenger.show("Your library is empty") }
                onDismiss()
                onStartRadio()
            }
        }
    })
    DownloadAlbumRow(album, tracks, downloads, messenger, scope)
    if (album.artistId?.isNotBlank() == true) {
        SheetRow("Go to artist", Icons.Default.Person, onClick = { closeAnd { onArtist(album.artistId) } })
    }
    SheetRow("View album details", Icons.Default.Info, onClick = { showDetails = true })
    SheetRow(if (pinned) "Unpin from Speed dial" else "Pin to Speed dial", Icons.Default.PushPin, onClick = {
        scope.launch {
            settings.setPinned(pinKey, !pinned)
            messenger.show(if (!pinned) "Pinned to Speed dial" else "Unpinned from Speed dial")
        }
    })

    if (showPicker) {
        PlaylistPickerSheet(tracks = tracks, title = album.name, onDismiss = { showPicker = false }, repo = repo, messenger = messenger, playlistEvents = playlistEvents)
    }
    if (showDetails && knownDetail != null) {
        AlbumDetailsDialog(detail = knownDetail, onDismiss = { showDetails = false })
    } else if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
            text = { Text("Details not loaded yet — try again in a moment.") },
        )
    }
}

// ---------- Artist sheet ----------

@Composable
private fun ArtistSheetContent(
    artist: Artist,
    onDismiss: () -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents?,
    onStartRadio: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var starred by remember(artist.id) { mutableStateOf(artist.starred) }
    var gathering by remember { mutableStateOf(false) }

    suspend fun allTracks(): List<Track> {
        val detail = (repo.artist(artist.id) as? Result.Ok)?.value ?: return emptyList()
        val out = ArrayList<Track>()
        for (al in detail.albums) {
            when (val r = repo.album(al.id)) {
                is Result.Ok -> out += r.value.tracks
                is Result.Err -> Unit
            }
        }
        return out
    }

    fun withTracks(action: suspend (List<Track>) -> Unit) {
        if (gathering) return
        gathering = true
        scope.launch {
            try {
                val tracks = allTracks()
                if (tracks.isEmpty()) messenger.show("No tracks found for ${artist.name}")
                else action(tracks)
            } finally {
                gathering = false
            }
            onDismiss()
        }
    }

    SheetHeader(
        title = artist.name, subtitle = "${artist.albumCount} albums", starred = starred, starEnabled = true,
        onStar = {
            val next = !starred
            starred = next
            scope.launch {
                when (repo.starArtist(artist.id, next)) {
                    is Result.Ok -> playlistEvents?.emit(PlaylistEvent.FavoritesChanged)
                    is Result.Err -> {
                        starred = !next
                        messenger.show("Could not update favorite")
                    }
                }
            }
        },
        onClose = onDismiss,
    )
    ActionTiles(
        onPlayNext = { withTracks { player.playNextAll(it) } },
        onSaveToPlaylist = { messenger.show("Open a song to save it to a playlist") },
        onShare = {
            shareText(context, artist.name)
            onDismiss()
        },
    )
    SheetRow("Play all", Icons.Default.PlayArrow, onClick = { withTracks { player.playAll(it, source = artist.name) } }, enabled = !gathering)
    SheetRow("Shuffle all", Icons.Default.Shuffle, onClick = { withTracks { player.playAll(it, shuffle = true, source = artist.name) } }, enabled = !gathering)
    SheetRow("Add all to queue", Icons.Default.QueueMusic, onClick = { withTracks { player.addAllToEnd(it) } }, enabled = !gathering)
    SheetRow("Start radio", Icons.Default.Radio, onClick = {
        // Seed with one of the artist's top songs; it starts instantly.
        scope.launch {
            val seed = (repo.topSongs(artist.name, 5) as? Result.Ok)?.value?.firstOrNull()
            if (seed == null) {
                messenger.show("No tracks found for ${artist.name}")
            } else {
                player.startRadio(seed) { messenger.show("Your library is empty") }
                onDismiss()
                onStartRadio()
            }
        }
    })
}

// ---------- Playlist sheet ----------

@Composable
private fun PlaylistSheetContent(
    playlist: Playlist,
    preloaded: List<Track>?,
    onDismiss: () -> Unit,
    player: PlayerManager,
    repo: MusicDataRepository,
    settings: SettingsStore,
    messenger: UiMessenger,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    playlistEvents: PlaylistEvents?,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val isVirtual = playlist.id == FAVORITE_SONGS_PLAYLIST_ID
    val groupId = if (isVirtual) "favorites" else "playlist:${playlist.id}"
    val groupType = if (isVirtual) "favorites" else "playlist"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<PlaylistDetail?>(null) }
    val pins by settings.flow.collectAsState(initial = null)
    val pinKey = speedDialKey("playlist", playlist.id)
    val pinned = pins?.pins?.contains(pinKey) == true

    LaunchedEffect(playlist.id) {
        if (preloaded == null) {
            detail = (repo.playlist(playlist.id) as? Result.Ok)?.value
        }
    }
    val tracks = preloaded ?: detail?.tracks.orEmpty()

    SheetHeader(
        title = playlist.name, subtitle = "${playlist.songCount} songs",
        starred = false, starEnabled = false, onStar = {}, onClose = onDismiss,
    )
    ActionTiles(
        onPlayNext = {
            if (tracks.isNotEmpty()) {
                player.playNextAll(tracks)
                messenger.show("Playing next: ${playlist.name}")
            }
            onDismiss()
        },
        onSaveToPlaylist = { showPicker = true },
        onShare = {
            shareText(context, playlist.name)
            onDismiss()
        },
    )
    SheetRow("Play", Icons.Default.PlayArrow, onClick = {
        if (tracks.isNotEmpty()) player.playAll(tracks, source = playlist.name)
        onDismiss()
    })
    SheetRow("Shuffle", Icons.Default.Shuffle, onClick = {
        if (tracks.isNotEmpty()) player.playAll(tracks, shuffle = true, source = playlist.name)
        onDismiss()
    })
    SheetRow("Add to queue", Icons.Default.QueueMusic, onClick = {
        player.addAllToEnd(tracks)
        messenger.show("Added to queue: ${playlist.name}")
        onDismiss()
    })
    if (isVirtual && tracks.isNotEmpty()) {
        // The Favorite songs collection can't be deleted or renamed; its menu
        // offers playback plus collecting into other playlists.
        SheetRow("Play next", Icons.AutoMirrored.Filled.PlaylistPlay, onClick = {
            player.playNextAll(tracks)
            messenger.show("Playing next: ${playlist.name}")
            onDismiss()
        })
        SheetRow("Add all to playlist", Icons.AutoMirrored.Filled.PlaylistAdd, onClick = { showPicker = true })
    }
    // Keep the collection available offline and in sync with the server.
    var keep by remember(playlist.id) { mutableStateOf(false) }
    LaunchedEffect(playlist.id) { keep = downloads.isKeepDownloaded(groupId) }
    SheetRow(
        if (keep) "Stop keeping downloaded" else "Keep downloaded",
        if (keep) Icons.Default.DownloadDone else Icons.Default.Download,
        onClick = {
            scope.launch {
                if (keep) {
                    downloads.setKeepDownloaded(groupId, groupType, playlist.name, false)
                    keep = false
                    messenger.show("Downloads kept, sync stopped")
                } else if (tracks.isEmpty()) {
                    messenger.show("Tracks not loaded yet")
                } else {
                    if (isVirtual) downloads.enqueueFavorites(tracks)
                    else downloads.enqueueTracks(tracks, groupId)
                    downloads.setKeepDownloaded(groupId, groupType, playlist.name, true)
                    keep = true
                    messenger.show("Keeping downloaded: ${playlist.name}")
                }
            }
        },
    )
    if (!isVirtual) {
        SheetRow(if (pinned) "Unpin from Speed dial" else "Pin to Speed dial", Icons.Default.PushPin, onClick = {
            scope.launch {
                settings.setPinned(pinKey, !pinned)
                messenger.show(if (!pinned) "Pinned to Speed dial" else "Unpinned from Speed dial")
            }
        })
    }

    if (showPicker) {
        PlaylistPickerSheet(tracks = tracks, title = playlist.name, onDismiss = { showPicker = false }, repo = repo, messenger = messenger, playlistEvents = playlistEvents)
    }
}

// ---------- Playlist picker ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    tracks: List<Track>,
    title: String,
    onDismiss: () -> Unit,
    repo: MusicDataRepository,
    messenger: UiMessenger,
    playlistEvents: PlaylistEvents? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingDupCheck?>(null) }

    LaunchedEffect(Unit) {
        playlists = (repo.playlists() as? Result.Ok)?.value
    }

    suspend fun addAndReport(playlistId: String, playlistName: String, toAdd: List<Track>, skipped: Int) {
        // Skip-only (everything was already there) needs no server call.
        if (toAdd.isEmpty()) {
            messenger.show(if (skipped > 0) "Skipped $skipped" else "Nothing to add")
            busy = false
            onDismiss()
            return
        }
        when (repo.addToPlaylist(playlistId, toAdd.map { it.id })) {
            is Result.Ok -> {
                messenger.show(
                    if (skipped > 0) "Added ${toAdd.size}, skipped $skipped"
                    else "Saved ${toAdd.size} to $playlistName",
                )
                playlistEvents?.emit(PlaylistEvent.TracksAdded(playlistId))
                onDismiss()
            }
            is Result.Err -> {
                messenger.show("Could not save to $playlistName")
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Save to playlist", style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = VusicColors.SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            SheetRow("New playlist", Icons.Default.Add, onClick = { showNew = true })
            val list = playlists
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Text("No playlists yet — create one above.", color = VusicColors.SecondaryText, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(list, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Server art when the playlist has any, else its tracks' art.
                            val cover = PlaylistCover.select(p.coverArt, emptyList())
                            PlaylistCoverArt(
                                cover = cover,
                                resolver = { id -> repo.coverArtUrl(id, 128) },
                                contentDescription = p.name,
                                modifier = Modifier.size(44.dp),
                                roundedDp = 4.dp,
                            )
                            Box(Modifier.weight(1f)) {
                                SheetRow("${p.name} • ${p.songCount}", Icons.Default.QueueMusic, onClick = {
                                    if (busy) return@SheetRow
                                    busy = true
                                    scope.launch {
                                        val detail = (repo.playlist(p.id) as? Result.Ok)?.value
                                        if (detail == null) {
                                            messenger.show("Could not open ${p.name}")
                                            busy = false
                                            return@launch
                                        }
                                        val part = PlaylistDuplicates.partition(
                                            detail.tracks.map { it.id }.toSet(), tracks,
                                        )
                                        if (part.duplicates.isEmpty()) {
                                            addAndReport(p.id, p.name, part.fresh, 0)
                                        } else {
                                            // Ask before adding: skip, add anyway, or cancel.
                                            pending = PendingDupCheck(p.id, p.name, part)
                                            busy = false
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pending?.let { pd ->
        DuplicateConfirmDialog(
            dupCount = pd.partition.duplicates.size,
            playlistName = pd.playlistName,
            onSkip = {
                pending = null
                busy = true
                scope.launch { addAndReport(pd.playlistId, pd.playlistName, pd.partition.fresh, pd.partition.duplicates.size) }
            },
            onAddAnyway = {
                pending = null
                busy = true
                scope.launch { addAndReport(pd.playlistId, pd.playlistName, tracks, 0) }
            },
            onCancel = { pending = null },
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (repo.createPlaylist(name.trim(), tracks.map { it.id })) {
                                is Result.Ok -> {
                                    messenger.show("Created playlist $name")
                                    onDismiss()
                                }
                                is Result.Err -> {
                                    messenger.show("Could not create playlist")
                                    busy = false
                                }
                            }
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Download rows ----------

/** Single-song download toggle: download, cancel, resume or remove. */
@Composable
private fun DownloadTrackRow(
    track: Track,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    messenger: UiMessenger,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val state by downloads.observeSong(track.id).collectAsState(initial = null)
    val status = state?.statusEnum
    val label = when (status) {
        com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> "Remove download"
        com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
        com.feishin.mobile.data.db.DownloadStatus.QUEUED,
        -> "Cancel download"
        com.feishin.mobile.data.db.DownloadStatus.PAUSED -> "Resume download"
        com.feishin.mobile.data.db.DownloadStatus.FAILED -> "Retry download"
        null -> "Download"
    }
    SheetRow(
        label,
        if (status == com.feishin.mobile.data.db.DownloadStatus.COMPLETED) Icons.Default.DownloadDone
        else Icons.Default.Download,
        onClick = {
            scope.launch {
                when (status) {
                    com.feishin.mobile.data.db.DownloadStatus.COMPLETED -> {
                        downloads.removeFromLibrary(track.id)
                        messenger.show("Download removed")
                    }
                    com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING,
                    com.feishin.mobile.data.db.DownloadStatus.QUEUED,
                    -> {
                        downloads.pause(track.id)
                        messenger.show("Download cancelled")
                    }
                    com.feishin.mobile.data.db.DownloadStatus.PAUSED,
                    com.feishin.mobile.data.db.DownloadStatus.FAILED,
                    -> {
                        downloads.resume(track.id)
                        messenger.show("Download resumed")
                    }
                    null -> {
                        downloads.enqueueTracks(listOf(track))
                        messenger.show("Downloading: ${track.title}")
                    }
                }
            }
        },
        trailing = state?.let {
            if (it.statusEnum == com.feishin.mobile.data.db.DownloadStatus.DOWNLOADING) {
                "${(it.progress * 100).toInt()}%"
            } else null
        },
    )
}

/** Album download: queues the whole album as one keep-downloaded group. */
@Composable
private fun DownloadAlbumRow(
    album: Album,
    preloaded: List<Track>,
    downloads: com.feishin.mobile.data.download.DownloadRepository,
    messenger: UiMessenger,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var keep by remember(album.id) { mutableStateOf(false) }
    LaunchedEffect(album.id) { keep = downloads.isKeepDownloaded("album:${album.id}") }
    SheetRow(
        if (keep) "Remove album download" else "Download album",
        if (keep) Icons.Default.DownloadDone else Icons.Default.Download,
        onClick = {
            scope.launch {
                if (keep) {
                    downloads.setKeepDownloaded("album:${album.id}", "album", album.name, false)
                    for (t in preloaded) downloads.removeFromLibrary(t.id)
                    keep = false
                    messenger.show("Album download removed")
                } else {
                    if (preloaded.isEmpty()) {
                        messenger.show("Album tracks not loaded yet")
                    } else {
                        downloads.enqueueAlbum(
                            com.feishin.mobile.domain.model.AlbumDetail(
                                album.id, album.name, album.artist, album.artistId, album.coverArt,
                                album.songCount, album.durationSec, album.year, album.genre,
                                album.starred, preloaded,
                            ),
                        )
                        keep = true
                        messenger.show("Downloading album: ${album.name}")
                    }
                }
            }
        },
    )
}

// ---------- Duplicate confirmation ----------

private data class PendingDupCheck(
    val playlistId: String,
    val playlistName: String,
    val partition: PlaylistDuplicates.Partition,
)

/**
 * "X songs are already in this playlist": skip the duplicates, add everything
 * anyway, or cancel.
 */
@Composable
fun DuplicateConfirmDialog(
    dupCount: Int,
    playlistName: String,
    onSkip: () -> Unit,
    onAddAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Already in playlist") },
        text = {
            Text(
                "$dupCount song${if (dupCount == 1) " is" else "s are"} already in \"$playlistName\".",
            )
        },
        confirmButton = { TextButton(onClick = onSkip) { Text("Skip duplicates") } },
        dismissButton = {
            Row {
                TextButton(onClick = onAddAnyway) { Text("Add anyway") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

// ---------- Details dialogs ----------

@Composable
fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    val rows = listOf(
        "Title" to track.title,
        "Artist" to track.artist,
        "Album" to track.album.ifBlank { "—" },
        "Year" to (track.year?.toString() ?: "—"),
        "Genre" to (track.genre ?: "—"),
        "Track" to (track.trackNo?.toString() ?: "—"),
        "Format" to (track.suffix?.uppercase() ?: "—"),
        "Bitrate" to (track.bitRate?.let { "$it kbps" } ?: "—"),
        "Size" to if (track.sizeBytes > 0) "%.1f MB".format(track.sizeBytes / 1048576.0) else "—",
        "Duration" to formatDuration(track.durationSec),
        "Path" to track.path.ifBlank { "—" },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Song details") },
        text = {
            LazyColumn {
                items(rows) { (k, v) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = VusicColors.SecondaryText)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

@Composable
fun AlbumDetailsDialog(detail: AlbumDetail, onDismiss: () -> Unit) {
    val rows = listOf(
        "Album" to detail.name,
        "Artist" to detail.artist,
        "Year" to (detail.year?.toString() ?: "—"),
        "Genre" to (detail.genre ?: "—"),
        "Songs" to detail.songCount.toString(),
        "Duration" to formatDuration(detail.durationSec),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Album details") },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = VusicColors.SecondaryText)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

fun shareText(context: android.content.Context, text: String) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text).setType("text/plain"),
                null,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
