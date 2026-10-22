package com.feishin.mobile.ui.library

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.playback.PlayerManager
import com.feishin.mobile.ui.components.ActionTarget
import com.feishin.mobile.ui.components.AlbumCard
import com.feishin.mobile.ui.components.ArtistCard
import com.feishin.mobile.ui.components.CoverImage
import com.feishin.mobile.ui.components.FilterChipRow
import com.feishin.mobile.ui.components.TrackRow
import com.feishin.mobile.ui.components.LocalMiniClearance
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

@Composable
fun DeviceFilesScreen(
    player: PlayerManager,
    onActions: (ActionTarget) -> Unit,
    modifier: Modifier = Modifier,
    vm: DeviceFilesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by player.current.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppSettingsDialog by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.checkAndLoad()
        } else {
            // "Don't ask again" (or a second denial) cannot be recovered with
            // another request; send the user to the system settings instead.
            val activity = context as? android.app.Activity
            if (activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            ) {
                showAppSettingsDialog = true
            }
        }
    }

    // Returning from system settings should pick up a newly granted permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!state.hasPermission) vm.checkAndLoad()
    }

    if (showAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAppSettingsDialog = false },
            title = { Text("Permission permanently denied") },
            text = { Text("Audio access was denied. Enable \"Music and audio\" for Vusic in system app settings to browse device files.") },
            confirmButton = {
                TextButton(onClick = {
                    showAppSettingsDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { context.startActivity(intent) }
                }) { Text("Open app settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAppSettingsDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (!state.hasPermission) {
        PermissionNeededCard(onGrant = { launcher.launch(permission) }, modifier = modifier)
        return
    }

    // Local Album Detail Screen
    val selectedAlbum = state.selectedAlbum
    if (selectedAlbum != null) {
        LocalAlbumDetailView(
            album = selectedAlbum,
            nowPlaying = nowPlaying,
            isPlaying = isPlaying,
            onBack = { vm.closeAlbum() },
            onPlayTrack = { tracks, idx -> vm.playSongs(tracks, idx) },
            onShuffle = { tracks -> vm.playSongs(tracks.shuffled(), 0) },
            onActions = onActions,
            modifier = modifier,
        )
        return
    }

    // Local Artist Detail Screen
    val selectedArtist = state.selectedArtist
    if (selectedArtist != null) {
        LocalArtistDetailView(
            artist = selectedArtist,
            onBack = { vm.closeArtist() },
            onAlbumClick = { albId -> vm.openAlbum(albId) },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = state.isSearching, enter = fadeIn(), exit = fadeOut()) {
            LibrarySearchField(
                query = state.searchQuery,
                onQuery = { vm.setSearchQuery(it) },
                onClose = { vm.toggleSearching(false) },
                autoFocus = true,
            )
        }

        FilterChipRow(
            options = listOf("Songs", "Albums", "Artists"),
            selected = when (state.selectedTab) {
                "ALBUMS" -> "Albums"
                "ARTISTS" -> "Artists"
                else -> "Songs"
            },
            onSelect = { tabName ->
                vm.selectTab(tabName.uppercase())
            },
        )

        // Subheader row: count + action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countText = when (state.selectedTab) {
                "ALBUMS" -> "${state.albums.size} albums"
                "ARTISTS" -> "${state.artists.size} artists"
                else -> "${state.songs.size} songs"
            }
            Text(
                countText,
                style = MaterialTheme.typography.labelMedium,
                color = VusicColors.SecondaryText,
                modifier = Modifier.weight(1f),
            )

            if (!state.isSearching) {
                IconButton(onClick = { vm.toggleSearching(true) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Search, "Search device files", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (state.selectedTab == "SONGS" && state.songs.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { vm.shuffleAll() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            }
        } else {
            when (state.selectedTab) {
                "ALBUMS" -> {
                    if (state.albums.isEmpty()) {
                        EmptyDeviceFilesView(message = if (state.searchQuery.isBlank()) "No albums found on device" else "No albums match \"${state.searchQuery}\"")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                        ) {
                            items(state.albums, key = { it.id }, contentType = { "album" }) { album ->
                                AlbumCard(
                                    album = album,
                                    artUrl = album.coverArt,
                                    onClick = { vm.openAlbum(album.id) },
                                )
                            }
                        }
                    }
                }
                "ARTISTS" -> {
                    if (state.artists.isEmpty()) {
                        EmptyDeviceFilesView(message = if (state.searchQuery.isBlank()) "No artists found on device" else "No artists match \"${state.searchQuery}\"")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
                        ) {
                            items(state.artists, key = { it.id }, contentType = { "artist" }) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    artUrl = artist.coverArt,
                                    onClick = { vm.openArtist(artist.id) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (state.songs.isEmpty()) {
                        EmptyDeviceFilesView(message = if (state.searchQuery.isBlank()) "No device audio files found" else "No songs match \"${state.searchQuery}\"")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
                        ) {
                            itemsIndexed(state.songs, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { idx, track ->
                                TrackRow(
                                    track = track,
                                    artUrl = track.coverArt,
                                    onClick = { vm.playSongs(state.songs, idx) },
                                    onActions = { onActions(ActionTarget.Track(track)) },
                                    onArtist = { },
                                    isCurrent = track.id == nowPlaying?.id,
                                    isPlaying = isPlaying,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionNeededCard(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Permission needed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vusic needs permission to read audio files so you can browse and play device music.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onGrant) {
                    Text("Grant permission")
                }
            }
        }
    }
}

@Composable
private fun EmptyDeviceFilesView(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VusicColors.SecondaryText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LocalAlbumDetailView(
    album: AlbumDetail,
    nowPlaying: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onActions: (ActionTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                album.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = VusicSpacing.ContentBottom + LocalMiniClearance.current),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverImage(
                        album.coverArt,
                        album.name,
                        modifier = Modifier.size(180.dp),
                        roundedDp = 12.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VusicColors.SecondaryText,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${album.songCount} songs • ${album.durationSec / 60} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = VusicColors.SecondaryText,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onPlayTrack(album.tracks, 0) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { onShuffle(album.tracks) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }

            itemsIndexed(album.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { idx, track ->
                TrackRow(
                    track = track,
                    artUrl = track.coverArt,
                    onClick = { onPlayTrack(album.tracks, idx) },
                    onActions = { onActions(ActionTarget.Track(track)) },
                    onArtist = { },
                    isCurrent = track.id == nowPlaying?.id,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

@Composable
private fun LocalArtistDetailView(
    artist: ArtistDetail,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, VusicSpacing.ContentBottom + LocalMiniClearance.current),
        ) {
            items(artist.albums, key = { it.id }, contentType = { "album" }) { album ->
                AlbumCard(
                    album = album,
                    artUrl = album.coverArt,
                    onClick = { onAlbumClick(album.id) },
                )
            }
        }
    }
}
