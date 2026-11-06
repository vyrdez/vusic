package com.feishin.mobile.ui.settings

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SyncedLyrics
import com.feishin.mobile.data.cache.LibraryRefresh
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.ArtistInfo
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.ServerInfo
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import com.feishin.mobile.domain.repo.ScanStatus
import com.feishin.mobile.ui.components.UiMessenger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanCoordinatorTest {

    private class FakeRepo : MusicDataRepository {
        var startScanResult: Result<Unit> = Result.Ok(Unit)
        val scanStatusSequence = mutableListOf<Result<ScanStatus>>()
        var defaultScanStatus: Result<ScanStatus> = Result.Ok(ScanStatus(scanning = false, count = 0))
        var scanStatusCalls = 0
        var startScanCalls = 0

        override suspend fun ping(): Result<Unit> = Result.Ok(Unit)
        override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> = Result.Ok(emptyList())
        override suspend fun artists(): Result<List<Artist>> = Result.Ok(emptyList())
        override suspend fun genres(): Result<List<Genre>> = Result.Ok(emptyList())
        override suspend fun playlists(): Result<List<Playlist>> = Result.Ok(emptyList())
        override suspend fun search(query: String): Result<SearchResults> = Result.Ok(SearchResults())
        override suspend fun search(query: String, artistCount: Int, albumCount: Int, songCount: Int): Result<SearchResults> = Result.Ok(SearchResults())
        override suspend fun serverInfo(): Result<ServerInfo> = Result.Ok(ServerInfo())
        override suspend fun startScan(): Result<Unit> {
            startScanCalls++
            return startScanResult
        }
        override suspend fun scanStatus(): Result<ScanStatus> {
            scanStatusCalls++
            return if (scanStatusSequence.isNotEmpty()) scanStatusSequence.removeAt(0) else defaultScanStatus
        }
        override suspend fun album(id: String): Result<AlbumDetail> = Result.Err("stub")
        override suspend fun artist(id: String): Result<ArtistDetail> = Result.Err("stub")
        override suspend fun artistInfo(id: String): Result<ArtistInfo> = Result.Ok(ArtistInfo())
        override suspend fun randomSongs(size: Int, genre: String?): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun topSongs(artist: String, count: Int): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun similarSongs(songId: String?, artistId: String?, count: Int): Result<List<Track>> = Result.Ok(emptyList())
        override suspend fun playlist(id: String): Result<PlaylistDetail> = Result.Err("stub")
        override suspend fun starred(): Result<StarredCollection> = Result.Ok(StarredCollection())
        override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = Result.Ok(Unit)
        override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = Result.Ok(Unit)
        override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = Result.Ok(Unit)
        override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> = Result.Ok("pl_1")
        override suspend fun cachedPlaylist(id: String): PlaylistDetail? = null
        override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = Result.Ok(Unit)
        override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = Result.Ok(Unit)
        override suspend fun deletePlaylist(id: String): Result<Unit> = Result.Ok(Unit)
        override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = Result.Ok(Unit)
        override suspend fun scrobbleAt(id: String, timeMs: Long, submission: Boolean): Result<Unit> = Result.Ok(Unit)
        override suspend fun lyricsForTrack(id: String, artist: String, title: String): Result<SyncedLyrics> = Result.Err("stub")
        override suspend fun lyrics(artist: String, title: String): Result<String> = Result.Ok("")
        override fun streamUrl(track: Track, maxBitrate: Int, format: String): String = ""
        override fun coverArtUrl(coverArtId: String?, size: Int): String? = null
        override fun downloadUrl(track: Track): String = ""
    }

    @Test
    fun testSequenceIdleToScanningCountZeroToRisingToFinished() = runTest {
        val repo = FakeRepo()
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        repo.scanStatusSequence.addAll(
            listOf(
                Result.Ok(ScanStatus(scanning = true, count = 0)),
                Result.Ok(ScanStatus(scanning = true, count = 250, folderCount = 10)),
                Result.Ok(ScanStatus(scanning = true, count = 1240, folderCount = 50)),
                Result.Ok(ScanStatus(scanning = false, count = 1240, folderCount = 50, lastScan = "2026-11-06T07:25:00Z")),
            )
        )

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
            delayMs = 1000L,
        )

        assertTrue(coordinator.state.value is ScanUiState.Idle)

        coordinator.startScan()
        scope.testScheduler.advanceTimeBy(1)
        assertTrue(coordinator.state.value is ScanUiState.Scanning)
        assertEquals(0, (coordinator.state.value as ScanUiState.Scanning).count)

        // Poll 1: 0 count
        scope.testScheduler.advanceTimeBy(1000L)
        assertEquals(0, (coordinator.state.value as ScanUiState.Scanning).count)

        // Poll 2: 250 count
        scope.testScheduler.advanceTimeBy(1000L)
        assertEquals(250, (coordinator.state.value as ScanUiState.Scanning).count)
        assertEquals(10, (coordinator.state.value as ScanUiState.Scanning).folderCount)

        // Poll 3: 1240 count
        scope.testScheduler.advanceTimeBy(1000L)
        assertEquals(1240, (coordinator.state.value as ScanUiState.Scanning).count)

        // Poll 4: finished
        scope.testScheduler.advanceTimeBy(1000L)
        assertTrue(coordinator.state.value is ScanUiState.Finished)
        val finished = coordinator.state.value as ScanUiState.Finished
        assertEquals(1240, finished.count)
        assertEquals(50, finished.folderCount)
        assertEquals("2026-11-06T07:25:00Z", finished.lastScan)

        // Cache-first library invalidated on finish
        val desc = formatScanDescription(finished, online = true)
        assertTrue(desc.contains("Scan finished: 1,240 files (50 folders)"))
        val expectedLastScan = formatLastScan("2026-11-06T07:25:00Z")
        assertTrue(desc.contains("Last scan: $expectedLastScan"))
    }

    @Test
    fun testAdminErrorDoesNotStickOnScanning() = runTest {
        val repo = FakeRepo()
        repo.startScanResult = Result.Err("Only admin accounts can scan the library")
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()
        val messages = mutableListOf<String>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            messenger.messages.toList(messages)
        }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
        )

        coordinator.startScan()
        scope.testScheduler.advanceUntilIdle()

        assertTrue(coordinator.state.value is ScanUiState.Error)
        val err = coordinator.state.value as ScanUiState.Error
        assertEquals("Only admin accounts can scan the library", err.message)
        assertEquals("Only admin accounts can scan the library", messages.firstOrNull())
        collectJob.cancel()
    }

    @Test
    fun testNetworkFailureShowsServerUnreachable() = runTest {
        val repo = FakeRepo()
        repo.startScanResult = Result.Err("Connection failed", java.io.IOException("Network down"))
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()
        val messages = mutableListOf<String>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            messenger.messages.toList(messages)
        }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
        )

        coordinator.startScan()
        scope.testScheduler.advanceUntilIdle()

        assertTrue(coordinator.state.value is ScanUiState.Error)
        val err = coordinator.state.value as ScanUiState.Error
        assertEquals("Server unreachable", err.message)
        assertEquals("Server unreachable", messages.firstOrNull())
        collectJob.cancel()
    }

    @Test
    fun testTimeoutStopsPollingAndShowsError() = runTest {
        val repo = FakeRepo()
        repo.defaultScanStatus = Result.Ok(ScanStatus(scanning = true, count = 10))
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()
        val messages = mutableListOf<String>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            messenger.messages.toList(messages)
        }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
            delayMs = 1000L,
            timeoutMs = 5000L,
        )

        coordinator.startScan()
        scope.testScheduler.advanceTimeBy(1000L)
        assertTrue(coordinator.state.value is ScanUiState.Scanning)

        // Advance past safety timeout (5000ms)
        scope.testScheduler.advanceTimeBy(6000L)
        assertTrue(coordinator.state.value is ScanUiState.Error)
        val err = coordinator.state.value as ScanUiState.Error
        assertEquals("Library scan timed out", err.message)
        assertEquals("Library scan timed out", messages.firstOrNull())
        collectJob.cancel()
    }

    @Test
    fun testPollingStopsWhenFinished() = runTest {
        val repo = FakeRepo()
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()

        repo.scanStatusSequence.addAll(
            listOf(
                Result.Ok(ScanStatus(scanning = true, count = 100)),
                Result.Ok(ScanStatus(scanning = false, count = 100)),
            )
        )

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
            delayMs = 1000L,
        )

        coordinator.startScan()
        scope.testScheduler.runCurrent()
        scope.testScheduler.advanceTimeBy(1000L) // Poll 1: scanning = true
        scope.testScheduler.runCurrent()
        scope.testScheduler.advanceTimeBy(1000L) // Poll 2: scanning = false (finished)
        scope.testScheduler.runCurrent()
        assertTrue(coordinator.state.value is ScanUiState.Finished)

        val callsAtFinish = repo.scanStatusCalls
        // Advance time further by 5000ms
        scope.testScheduler.advanceTimeBy(5000L)
        scope.testScheduler.runCurrent()
        assertEquals("Polling must stop once scanning is false", callsAtFinish, repo.scanStatusCalls)
    }

    @Test
    fun testReentryResumesDisplayAndPicksUpActiveScan() = runTest {
        val repo = FakeRepo()
        val libraryRefresh = LibraryRefresh()
        val messenger = UiMessenger()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val coordinator = ScanCoordinator(
            repo = repo,
            libraryRefresh = libraryRefresh,
            messenger = messenger,
            externalScope = scope,
            delayMs = 1000L,
        )

        // Server is actively scanning in background (e.g. started from web interface or surviving re-entry)
        repo.defaultScanStatus = Result.Ok(ScanStatus(scanning = true, count = 350))
        coordinator.refreshStatus()
        scope.testScheduler.advanceTimeBy(1)

        assertTrue(coordinator.state.value is ScanUiState.Scanning)
        assertEquals(350, (coordinator.state.value as ScanUiState.Scanning).count)

        // Re-entering again while scanning does not wipe state
        coordinator.refreshStatus()
        assertTrue(coordinator.state.value is ScanUiState.Scanning)
        assertEquals(350, (coordinator.state.value as ScanUiState.Scanning).count)
    }

    @Test
    fun testZeroFilesNeverProducedInDescription() {
        val scanningZero = ScanUiState.Scanning(count = 0, folderCount = null)
        val descZero = formatScanDescription(scanningZero, online = true)
        assertEquals("Scanning…", descZero)
        assertFalse(descZero.contains("0 files"))
        assertFalse(descZero.contains("(0 files)"))

        val scanningZeroWithFolders = ScanUiState.Scanning(count = 0, folderCount = 0)
        val descZeroFolders = formatScanDescription(scanningZeroWithFolders, online = true)
        assertEquals("Scanning…", descZeroFolders)
        assertFalse(descZeroFolders.contains("0 files"))

        val scanningNegative = ScanUiState.Scanning(count = -1)
        val descNegative = formatScanDescription(scanningNegative, online = true)
        assertEquals("Scanning…", descNegative)
        assertFalse(descNegative.contains("0 files"))

        val scanningWithCount = ScanUiState.Scanning(count = 1240, folderCount = null)
        val descCount = formatScanDescription(scanningWithCount, online = true)
        assertEquals("Scanning… 1,240 files", descCount)

        val scanningWithCountAndFolders = ScanUiState.Scanning(count = 1240, folderCount = 50)
        val descCountFolders = formatScanDescription(scanningWithCountAndFolders, online = true)
        assertEquals("Scanning… 1,240 files (50 folders)", descCountFolders)

        val finished = ScanUiState.Finished(count = 1240, folderCount = 50, lastScan = "2026-11-06T07:25:00Z")
        val descFinished = formatScanDescription(finished, online = true)
        assertTrue(descFinished.contains("Scan finished: 1,240 files (50 folders)"))
        val expectedLastScan = formatLastScan("2026-11-06T07:25:00Z")
        assertTrue(descFinished.contains("Last scan: $expectedLastScan"))

        val offline = formatScanDescription(scanningZero, online = false)
        assertEquals("Unavailable offline", offline)
    }
}
