package com.feishin.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.feishin.mobile.data.auth.ProfileAuthStatus
import com.feishin.mobile.domain.model.BackendCapabilities
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.ui.theme.FeishinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerProfileCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleUi(
        id: String = "p1",
        name: String = "Home Jellyfin",
        type: BackendType = BackendType.JELLYFIN,
        username: String = "alice",
        enabled: Boolean = true,
        extensions: List<String> = emptyList(),
        driveRootFolderName: String? = null,
        indexedTrackCount: Int = 0,
        quotaLimited: Boolean = false,
    ) = ServerProfileUi(
        id = id,
        name = name,
        type = type,
        url = "https://music.example.com:8096",
        host = "music.example.com:8096",
        username = username,
        enabled = enabled,
        extensions = ImmutableStrings(extensions),
        capabilities = BackendCapabilities.forType(type),
        productLabel = "Jellyfin",
        apiVersion = "10.9.11",
        driveRootFolderName = driveRootFolderName,
        indexedTrackCount = indexedTrackCount,
        quotaLimited = quotaLimited,
    )

    private fun hasStateDescription(expected: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        expected,
    )

    @Test
    fun headerTap_expandsAndCollapses_withSemanticsStateDescription() {
        var expanded by mutableStateOf(false)
        composeRule.setContent {
            FeishinTheme(dark = true) {
                ServerProfileCard(
                    ui = sampleUi(),
                    rank = 1,
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                    isDragging = false,
                    dragHandleModifier = Modifier,
                    canMoveUp = false,
                    canMoveDown = true,
                    status = ProfileStatusUi(ProfileStatusTone.HEALTHY, "Healthy"),
                    isTesting = false,
                    scanUi = ProfileScanUi(isScanning = false, sublabel = "Ready to scan", canScan = true),
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleEnabled = {},
                    onEdit = {},
                    onTestConnection = {},
                    onScan = {},
                    onCopyEndpoint = {},
                    onRequestRemove = {},
                )
            }
        }

        val headerNode = composeRule.onNodeWithText("Home Jellyfin")
        headerNode.assert(hasStateDescription("Collapsed"))
        composeRule.onNodeWithText("Test connection").assertDoesNotExist()

        headerNode.performClick()
        composeRule.waitForIdle()
        assertTrue(expanded)
        headerNode.assert(hasStateDescription("Expanded"))
        composeRule.onNodeWithText("Test connection").assertIsDisplayed()
        composeRule.onNodeWithText("Capabilities").assertIsDisplayed()
        composeRule.onNodeWithText("Playlists").assertIsDisplayed()

        headerNode.performClick()
        composeRule.waitForIdle()
        assertTrue(!expanded)
        headerNode.assert(hasStateDescription("Collapsed"))
        composeRule.onNodeWithText("Test connection").assertDoesNotExist()
    }

    @Test
    fun switchTap_togglesEnabledWithoutExpandingCard() {
        var expanded by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var expandClicks = 0
        var toggleCalls = 0

        composeRule.setContent {
            FeishinTheme(dark = true) {
                ServerProfileCard(
                    ui = sampleUi(enabled = enabled),
                    rank = 1,
                    expanded = expanded,
                    onToggleExpanded = {
                        expandClicks++
                        expanded = !expanded
                    },
                    isDragging = false,
                    dragHandleModifier = Modifier,
                    canMoveUp = false,
                    canMoveDown = false,
                    status = ProfileStatusUi(ProfileStatusTone.HEALTHY, "Healthy"),
                    isTesting = false,
                    scanUi = ProfileScanUi(isScanning = false, sublabel = "Ready to scan", canScan = true),
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleEnabled = {
                        toggleCalls++
                        enabled = it
                    },
                    onEdit = {},
                    onTestConnection = {},
                    onScan = {},
                    onCopyEndpoint = {},
                    onRequestRemove = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Enable Home Jellyfin").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCalls)
        assertEquals(0, expandClicks)
        assertEquals(false, enabled)
        assertEquals(false, expanded)
        composeRule.onNodeWithText("Home Jellyfin").assert(hasStateDescription("Collapsed"))
    }

    @Test
    fun reorderButtonsDisabledAtEnds_andRemoveInvokesCallback() {
        var removeCount = 0
        composeRule.setContent {
            FeishinTheme(dark = true) {
                ServerProfileCard(
                    ui = sampleUi(name = "First Server"),
                    rank = 1,
                    expanded = true,
                    onToggleExpanded = {},
                    isDragging = false,
                    dragHandleModifier = Modifier,
                    canMoveUp = false,
                    canMoveDown = true,
                    status = ProfileStatusUi(ProfileStatusTone.HEALTHY, "Healthy"),
                    isTesting = false,
                    scanUi = ProfileScanUi(isScanning = false, sublabel = "Ready to scan", canScan = true),
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleEnabled = {},
                    onEdit = {},
                    onTestConnection = {},
                    onScan = {},
                    onCopyEndpoint = {},
                    onRequestRemove = { removeCount++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Move First Server up").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Move First Server down").assertIsEnabled()

        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(1, removeCount)
    }

    @Test
    fun lastItem_moveDownDisabled_andMoveUpEnabled() {
        composeRule.setContent {
            FeishinTheme(dark = true) {
                ServerProfileCard(
                    ui = sampleUi(name = "Last Server"),
                    rank = 2,
                    expanded = true,
                    onToggleExpanded = {},
                    isDragging = false,
                    dragHandleModifier = Modifier,
                    canMoveUp = true,
                    canMoveDown = false,
                    status = ProfileStatusUi(ProfileStatusTone.HEALTHY, "Healthy"),
                    isTesting = false,
                    scanUi = ProfileScanUi(isScanning = false, sublabel = "Ready to scan", canScan = true),
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleEnabled = {},
                    onEdit = {},
                    onTestConnection = {},
                    onScan = {},
                    onCopyEndpoint = {},
                    onRequestRemove = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Move Last Server up").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Move Last Server down").assertIsNotEnabled()
    }

    @Test
    fun googleDriveCard_showsEmailSubline_detailsRows_chips_reIndexAndReconnect() {
        var reconnectClicks = 0
        val driveUi = sampleUi(
            id = "gd1",
            name = "Personal Drive",
            type = BackendType.GOOGLE_DRIVE,
            username = "alice@gmail.com",
            driveRootFolderName = "Music",
            indexedTrackCount = 2431,
        )
        val status = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.OK,
            backendType = BackendType.GOOGLE_DRIVE,
            indexedTrackCount = 2431,
        )
        assertEquals("Indexed · 2,431 tracks", status.label)

        var authStatus by mutableStateOf(ProfileAuthStatus.OK)

        composeRule.setContent {
            FeishinTheme(dark = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val currentStatus = resolveProfileStatus(
                        enabled = true,
                        online = true,
                        testState = TestState.Idle,
                        health = null,
                        authStatus = authStatus,
                        backendType = BackendType.GOOGLE_DRIVE,
                        indexedTrackCount = 2431,
                    )
                    ServerProfileCard(
                        ui = driveUi,
                        rank = 1,
                        expanded = true,
                        onToggleExpanded = {},
                        isDragging = false,
                        dragHandleModifier = Modifier,
                        canMoveUp = false,
                        canMoveDown = false,
                        status = currentStatus,
                        isTesting = false,
                        scanUi = resolveProfileScanUi(
                            capabilities = BackendCapabilities.forType(BackendType.GOOGLE_DRIVE),
                            enabled = true,
                            online = true,
                            scanState = ScanUiState.Finished(
                                count = 2431,
                                lastScan = "2026-11-28T19:30:00Z",
                            ),
                            backendType = BackendType.GOOGLE_DRIVE,
                        ),
                        onMoveUp = {},
                        onMoveDown = {},
                        onToggleEnabled = {},
                        onEdit = {},
                        onTestConnection = {},
                        onScan = {},
                        onCopyEndpoint = {},
                        onRequestRemove = {},
                        onReconnectDrive = { reconnectClicks++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Google Drive").assertIsDisplayed()
        composeRule.onNodeWithText("alice@gmail.com · Indexed · 2,431 tracks").assertIsDisplayed()
        composeRule.onNodeWithText("Music folder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Music").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Google Drive v3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Last index").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Local playlists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Local favorites").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No scrobbling").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Raw stream").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Re-index").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reconnect Google Drive").assertDoesNotExist()

        authStatus = ProfileAuthStatus.SESSION_EXPIRED
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alice@gmail.com · Session expired").assertIsDisplayed()
        composeRule.onNodeWithText("Reconnect Google Drive").performScrollTo().performClick()
        assertEquals(1, reconnectClicks)
    }

    @Test
    fun googleDriveStatus_formatsDiscoveringIndexingQuotaAndOffline() {
        val discovering = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.OK,
            backendType = BackendType.GOOGLE_DRIVE,
            scanState = ScanUiState.Scanning(count = 420, twoStage = true),
        )
        assertEquals("Discovering… 420 files", discovering.label)

        val indexing = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.OK,
            backendType = BackendType.GOOGLE_DRIVE,
            scanState = ScanUiState.Scanning(count = 160, totalCount = 420, twoStage = true),
        )
        assertEquals("Indexing… 38% (160/420)", indexing.label)

        val quota = resolveProfileStatus(
            enabled = true,
            online = true,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.OK,
            backendType = BackendType.GOOGLE_DRIVE,
            scanState = ScanUiState.Finished(count = 100, quotaLimited = true),
            quotaLimited = true,
        )
        assertEquals("Quota limited", quota.label)

        val offline = resolveProfileStatus(
            enabled = true,
            online = false,
            testState = TestState.Idle,
            health = null,
            authStatus = ProfileAuthStatus.OK,
            backendType = BackendType.GOOGLE_DRIVE,
        )
        assertEquals("Offline", offline.label)
    }
}
