package com.feishin.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.remote.drive.DriveAboutResponse
import com.feishin.mobile.data.remote.drive.DriveApi
import com.feishin.mobile.data.remote.drive.DriveFileDto
import com.feishin.mobile.data.remote.drive.DriveFileListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveFolderPickerViewModelTest {

    private val createdVms = mutableListOf<ViewModel>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun <T : ViewModel> track(vm: T): T {
        createdVms += vm
        return vm
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        createdVms.forEach { it.viewModelScope.cancel() }
        createdVms.clear()
        Dispatchers.resetMain()
    }

    private fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun loadInitial_highlightsCaseInsensitiveMusicAtTopAsSuggestedDefault() {
        val requestedParents = mutableListOf<String>()
        val vm = track(
            DriveFolderPickerViewModel(
                dataSource = DriveFolderDataSource { parentId ->
                    requestedParents += parentId
                    Result.Ok(
                        listOf(
                            DriveFileDto(id = "f-1", name = "Archive"),
                            DriveFileDto(id = "f-2", name = "music"),
                            DriveFileDto(id = "f-3", name = "Podcasts"),
                        ),
                    )
                },
                ioDispatcher = testDispatcher,
            ),
        )

        vm.loadInitial()
        awaitCondition { !vm.ui.value.loading }

        assertEquals(listOf("root"), requestedParents)
        val folders = vm.ui.value.folders
        assertEquals(listOf("music", "Archive", "Podcasts"), folders.map { it.name })
        assertTrue(folders.first().isSuggestedMusic)
        assertFalse(folders[1].isSuggestedMusic)
        assertEquals("f-2", vm.ui.value.selectedFolder?.id)
        assertEquals("f-2" to "music", vm.ui.value.resolvedSelection())
        assertTrue(vm.ui.value.canSelectCurrentFolder)
    }

    @Test
    fun breadcrumbNavigation_drillsIntoSubfoldersAndBackToRoot() {
        val tree = mapOf(
            "root" to listOf(
                DriveFileDto(id = "f-audio", name = "Audio"),
                DriveFileDto(id = "f-docs", name = "Documents"),
            ),
            "f-audio" to listOf(
                DriveFileDto(id = "f-flac", name = "FLAC"),
                DriveFileDto(id = "f-mp3", name = "MP3"),
            ),
            "f-flac" to emptyList(),
        )
        val vm = track(
            DriveFolderPickerViewModel(
                dataSource = DriveFolderDataSource { parentId ->
                    Result.Ok(tree[parentId].orEmpty())
                },
                ioDispatcher = testDispatcher,
            ),
        )

        vm.loadInitial()
        awaitCondition { !vm.ui.value.loading && vm.ui.value.folders.size == 2 }
        assertNull(vm.ui.value.selectedFolder)
        assertFalse(vm.ui.value.canSelectCurrentFolder)

        vm.openFolder(vm.ui.value.folders.first { it.id == "f-audio" })
        awaitCondition { !vm.ui.value.loading && vm.ui.value.folders.size == 2 }
        assertEquals(listOf("root", "f-audio"), vm.ui.value.breadcrumbs.map { it.id })
        assertEquals(listOf("FLAC", "MP3"), vm.ui.value.folders.map { it.name })
        // Inside subfolder, current breadcrumb folder can be selected even before picking a child.
        assertTrue(vm.ui.value.canSelectCurrentFolder)
        assertEquals("f-audio" to "Audio", vm.ui.value.resolvedSelection())

        vm.openFolder(vm.ui.value.folders.first { it.id == "f-flac" })
        awaitCondition { !vm.ui.value.loading && vm.ui.value.breadcrumbs.size == 3 }
        assertTrue(vm.ui.value.folders.isEmpty())
        assertEquals("f-flac" to "FLAC", vm.ui.value.resolvedSelection())

        // Jump back to My Drive via breadcrumb
        vm.navigateToBreadcrumb(0)
        awaitCondition { !vm.ui.value.loading && vm.ui.value.breadcrumbs.size == 1 }
        assertEquals(listOf("Audio", "Documents"), vm.ui.value.folders.map { it.name })
    }

    @Test
    fun emptyRootAndErrorRetry_transitionCleanly() {
        var failFirst = true
        val vm = track(
            DriveFolderPickerViewModel(
                dataSource = DriveFolderDataSource { _ ->
                    if (failFirst) {
                        failFirst = false
                        Result.Err("Network error")
                    } else {
                        Result.Ok(emptyList())
                    }
                },
                ioDispatcher = testDispatcher,
            ),
        )

        vm.loadInitial()
        awaitCondition { !vm.ui.value.loading && vm.ui.value.errorMessage != null }
        assertEquals("Network error", vm.ui.value.errorMessage)

        vm.retry()
        awaitCondition { !vm.ui.value.loading && vm.ui.value.errorMessage == null }
        assertTrue(vm.ui.value.folders.isEmpty())
        assertFalse(vm.ui.value.canSelectCurrentFolder)
    }

    @Test
    fun fetchDriveSubfolders_passesExactDriveQueryFieldsAndPaginates() = runBlocking {
        val queries = mutableListOf<Triple<String?, String, String?>>()
        val fakeApi = object : DriveApi {
            override suspend fun getAbout(fields: String): DriveAboutResponse = DriveAboutResponse()

            override suspend fun listFiles(
                q: String?,
                fields: String,
                pageSize: Int,
                pageToken: String?,
                orderBy: String?,
                profileIdTag: com.feishin.mobile.core.network.ProfileIdTag?,
            ): DriveFileListResponse {
                queries += Triple(q, fields, pageToken)
                assertEquals(200, pageSize)
                assertEquals("name", orderBy)
                return if (pageToken == null) {
                    DriveFileListResponse(
                        nextPageToken = "page-2",
                        files = listOf(
                            DriveFileDto(id = "f-1", name = "Music"),
                            DriveFileDto(id = "f-trashed", name = "Old", trashed = true),
                        ),
                    )
                } else {
                    DriveFileListResponse(
                        nextPageToken = null,
                        files = listOf(DriveFileDto(id = "f-2", name = "Soundtracks")),
                    )
                }
            }
        }

        val res = fetchDriveSubfolders(fakeApi, "root")
        assertTrue(res is Result.Ok)
        val items = (res as Result.Ok).value
        assertEquals(listOf("f-1", "f-2"), items.map { it.id })
        assertEquals(
            "mimeType='application/vnd.google-apps.folder' and trashed=false and 'root' in parents",
            queries[0].first,
        )
        assertEquals("nextPageToken,files(id,name,parents)", queries[0].second)
        assertNull(queries[0].third)
        assertEquals("page-2", queries[1].third)
    }
}
