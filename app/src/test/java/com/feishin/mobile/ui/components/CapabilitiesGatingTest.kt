package com.feishin.mobile.ui.components

import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.ui.player.resolveNowPlayingChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesGatingTest {

    private fun track(
        id: String,
        localUri: String? = null,
        albumId: String? = "p1:al1",
        artistId: String? = "p1:ar1",
        sourceProfileId: String? = null,
    ) = Track(
        id = id,
        title = "Track $id",
        album = "Album",
        albumId = albumId,
        artist = "Artist",
        artistId = artistId,
        coverArt = null,
        durationSec = 180,
        trackNo = 1,
        year = 2024,
        genre = "Rock",
        suffix = "flac",
        starred = false,
        localUri = localUri,
        sourceProfileId = sourceProfileId,
    )

    private fun playlist(id: String, name: String = id) = Playlist(
        id = id,
        name = name,
        songCount = 3,
        durationSec = 540,
        coverArt = null,
    )

    private fun capsFor(id: String): BackendCapabilities = when {
        id.startsWith("local:") -> BackendCapabilities.DEVICE_LOCAL
        id.startsWith("gd1:") -> BackendCapabilities.forType(BackendType.GOOGLE_DRIVE)
        else -> BackendCapabilities.forType(BackendType.NAVIDROME)
    }

    @Test
    fun resolveTrackCapabilitiesGatesByBackendAndIds() {
        val navidromeCaps = BackendCapabilities.forType(BackendType.NAVIDROME)
        val full = resolveTrackCapabilities(
            isLocal = false,
            caps = navidromeCaps,
            albumId = "p1:al1",
            artistId = "p1:ar1",
        )
        assertTrue(full.canFavorite)
        assertTrue(full.canSaveToPlaylist)
        assertTrue(full.canStartRadio)
        assertTrue(full.canDownload)
        assertTrue(full.canNavigateAlbum)
        assertTrue(full.canNavigateArtist)
        assertTrue(full.canPin)

        val missingNavIds = resolveTrackCapabilities(
            isLocal = false,
            caps = navidromeCaps,
            albumId = "",
            artistId = null,
        )
        assertFalse(missingNavIds.canNavigateAlbum)
        assertFalse(missingNavIds.canNavigateArtist)

        val drive = resolveTrackCapabilities(
            isLocal = false,
            caps = BackendCapabilities.forType(BackendType.GOOGLE_DRIVE),
            albumId = "gd1:al1",
            artistId = "gd1:ar1",
        )
        assertTrue(drive.canFavorite)
        assertFalse(drive.canSaveToPlaylist)
        assertFalse(drive.canStartRadio)
        assertTrue(drive.canDownload)

        val local = resolveTrackCapabilities(
            isLocal = true,
            caps = BackendCapabilities.DEVICE_LOCAL,
            albumId = "local_album:1",
            artistId = "local_artist:1",
        )
        assertFalse(local.canFavorite)
        assertFalse(local.canSaveToPlaylist)
        assertFalse(local.canStartRadio)
        assertFalse(local.canDownload)
        assertFalse(local.canNavigateAlbum)
        assertFalse(local.canNavigateArtist)
        assertFalse(local.canPin)
    }

    @Test
    fun resolveNowPlayingChipsHidesUnsupportedChips() {
        val remote = resolveNowPlayingChips(
            local = false,
            capabilities = BackendCapabilities.forType(BackendType.OPENSUBSONIC),
        )
        assertTrue(remote.showFavorite)
        assertTrue(remote.showSaveToPlaylist)
        assertTrue(remote.showShare)
        assertTrue(remote.showDownload)

        val drive = resolveNowPlayingChips(
            local = false,
            capabilities = BackendCapabilities.forType(BackendType.GOOGLE_DRIVE),
        )
        assertTrue(drive.showFavorite)
        assertFalse(drive.showSaveToPlaylist)
        assertTrue(drive.showShare)
        assertTrue(drive.showDownload)

        val local = resolveNowPlayingChips(
            local = true,
            capabilities = BackendCapabilities.DEVICE_LOCAL,
        )
        assertFalse(local.showFavorite)
        assertFalse(local.showSaveToPlaylist)
        assertTrue(local.showShare)
        assertFalse(local.showDownload)
    }

    @Test
    fun filterPlaylistsForSingleRemoteProfileScopesToOwningServer() {
        val playlists = listOf(
            playlist("p1:pl1", "Home Mix"),
            playlist("p2:pl2", "Work Mix"),
            playlist("pl_bare", "Legacy"),
        )
        val result = filterPlaylistsForTracks(
            tracks = listOf(track("p1:s1"), track("p1:s2")),
            playlists = playlists,
            capabilitiesFor = ::capsFor,
        )
        assertEquals(listOf("p1:pl1"), result.playlists.map { it.id })
        assertEquals("p1", result.owningProfileId)
        assertTrue(result.canCreatePlaylist)
        assertNull(result.helperMessage)
    }

    @Test
    fun filterPlaylistsKeepsBareIdsForLegacySingleServerFakes() {
        val playlists = listOf(
            playlist("pl1", "Bare 1"),
            playlist("pl2", "Bare 2"),
            playlist("p2:pl3", "Other Profile"),
        )
        val result = filterPlaylistsForTracks(
            tracks = listOf(track("s1"), track("s2")),
            playlists = playlists,
            capabilitiesFor = ::capsFor,
        )
        assertEquals(listOf("pl1", "pl2"), result.playlists.map { it.id })
        assertNull(result.owningProfileId)
        assertTrue(result.canCreatePlaylist)
        assertNull(result.helperMessage)
    }

    @Test
    fun filterPlaylistsRejectsMixedServerSelectionWithHelperMessage() {
        val playlists = listOf(
            playlist("p1:pl1", "Home Mix"),
            playlist("p2:pl2", "Work Mix"),
        )
        val result = filterPlaylistsForTracks(
            tracks = listOf(track("p1:s1"), track("p2:s2")),
            playlists = playlists,
            capabilitiesFor = ::capsFor,
        )
        assertTrue(result.playlists.isEmpty())
        assertNull(result.owningProfileId)
        assertFalse(result.canCreatePlaylist)
        assertEquals(MIXED_OR_UNSUPPORTED_PLAYLIST_MESSAGE, result.helperMessage)
    }

    @Test
    fun filterPlaylistsRejectsLocalOrUnsupportedBackendTracks() {
        val playlists = listOf(playlist("p1:pl1", "Home Mix"))

        val localResult = filterPlaylistsForTracks(
            tracks = listOf(track("local:1", localUri = "content://media/1")),
            playlists = playlists,
            capabilitiesFor = ::capsFor,
        )
        assertTrue(localResult.playlists.isEmpty())
        assertFalse(localResult.canCreatePlaylist)
        assertEquals(MIXED_OR_UNSUPPORTED_PLAYLIST_MESSAGE, localResult.helperMessage)

        val driveResult = filterPlaylistsForTracks(
            tracks = listOf(track("gd1:s1")),
            playlists = playlists,
            capabilitiesFor = ::capsFor,
        )
        assertTrue(driveResult.playlists.isEmpty())
        assertFalse(driveResult.canCreatePlaylist)
        assertEquals(MIXED_OR_UNSUPPORTED_PLAYLIST_MESSAGE, driveResult.helperMessage)
    }
}
