package com.feishin.mobile.ui.settings

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
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        enabled: Boolean = true,
        extensions: List<String> = emptyList(),
    ) = ServerProfileUi(
        id = id,
        name = name,
        type = type,
        url = "https://music.example.com:8096",
        host = "music.example.com:8096",
        username = "alice",
        enabled = enabled,
        extensions = ImmutableStrings(extensions),
        capabilities = BackendCapabilities.forType(type),
        productLabel = "Jellyfin",
        apiVersion = "10.9.11",
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
}
