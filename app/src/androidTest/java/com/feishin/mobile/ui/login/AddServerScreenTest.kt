package com.feishin.mobile.ui.login

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.feishin.mobile.data.auth.AuthRepository
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileAuthStateStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.auth.TransientProbeRegistry
import com.feishin.mobile.data.backend.BackendClientFactory
import com.feishin.mobile.data.remote.CleartextGuardInterceptor
import com.feishin.mobile.data.remote.ProfileAuthInterceptor
import com.feishin.mobile.data.remote.ProfileTrustManager
import com.feishin.mobile.data.remote.drive.DriveAuthResult
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.theme.FeishinTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddServerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val createdVms = mutableListOf<LoginViewModel>()

    @After
    fun tearDown() {
        createdVms.forEach { it.viewModelScope.cancel() }
        createdVms.clear()
    }

    private fun createTestViewModel(initialProfiles: List<ServerProfile> = emptyList()): LoginViewModel {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "add_server_test_${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsStore(PreferenceDataStoreFactory.create { file })
        val profilePrefs = InMemorySharedPreferences()
        val legacyPrefs = InMemorySharedPreferences()
        val legacyCreds = CredentialsStore(ctx)
        val profileStore = ProfileStore(profilePrefs, legacyCreds, settings, legacyPrefs)
        if (initialProfiles.isNotEmpty()) {
            profileStore.saveAll(initialProfiles)
        }
        val probeRegistry = TransientProbeRegistry()
        val cleartextGuard = CleartextGuardInterceptor(profileStore, probeRegistry, settings)
        val profileAuth = ProfileAuthInterceptor(profileStore, probeRegistry)
        val trustManager = ProfileTrustManager(profileStore, probeRegistry, settings)
        val baseClient = OkHttpClient.Builder()
            .sslSocketFactory(trustManager.sslContext().socketFactory, trustManager)
            .hostnameVerifier(trustManager.hostnameVerifier())
            .addInterceptor(cleartextGuard)
            .addInterceptor(profileAuth)
            .addNetworkInterceptor(profileAuth.redirectGuard())
            .build()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val backendFactory = BackendClientFactory(json, baseClient)
        val auth = AuthRepository(
            profileStore = profileStore,
            legacyCredentials = legacyCreds,
            backendFactory = backendFactory,
            probeRegistry = probeRegistry,
            settings = settings,
            profileAuthStateStore = ProfileAuthStateStore(),
        )
        runBlocking { auth.preload() }
        return LoginViewModel(auth, settings).also { createdVms += it }
    }

    @Test
    fun driveTileTap_selectsGoogleDrive_hidesSecurityAndServerFields_andGatesConnect() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()
        val shown = mutableStateListOf<String>()

        composeRule.setContent {
            LaunchedEffect(messenger) {
                messenger.messages.collect { shown += it }
            }
            FeishinTheme(dark = true) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ServerFormContent(
                        vm = vm,
                        messenger = messenger,
                        addMode = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Navidrome").assertIsSelected()
        composeRule.onNodeWithText("Google Drive").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Google Drive").assertIsSelected()
        assertTrue(shown.none { it.contains("coming soon", ignoreCase = true) })
        composeRule.onNodeWithText("Server name").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in with Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Allow self-signed certificate").assertDoesNotExist()
        composeRule.onNodeWithText("Server URL").assertDoesNotExist()

        // Muted Test & Connect tap before sign-in prompts sign-in
        composeRule.onNodeWithText("Test & Connect").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(shown.contains("Sign in with Google first"))

        // After Google sign-in without a folder or scan-all toggle, muted Connect tap prompts folder choice
        vm.handleDriveAuthResult(
            DriveAuthResult.Authorized(
                email = "alice@gmail.com",
                accessToken = "tok-1",
                expiresAtEpochMs = 1_900_000_000_000L,
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alice@gmail.com").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Test & Connect").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(shown.contains("Choose a Music folder or turn on \"Scan all audio files\""))
    }

    @Test
    fun incompleteFormTap_showsValidationSnackbar_insteadOfSwallowingClicks() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()
        val shown = mutableStateListOf<String>()

        composeRule.setContent {
            LaunchedEffect(messenger) {
                messenger.messages.collect { shown += it }
            }
            FeishinTheme(dark = true) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ServerFormContent(
                        vm = vm,
                        messenger = messenger,
                        addMode = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Test & Connect").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(shown.contains("Fill in Server URL, Username, and Password first"))

        composeRule.onNodeWithText("Jellyfin").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Test connection only").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(shown.contains("Fill in Server URL and Username first"))
    }

    @Test
    fun jellyfinTile_selectsJellyfin_marksPasswordOptional_andClearsPassword() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()

        composeRule.setContent {
            FeishinTheme(dark = true) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ServerFormContent(
                        vm = vm,
                        messenger = messenger,
                        addMode = true,
                    )
                }
            }
        }

        // Initially only Server name has "Optional"
        assertEquals(1, composeRule.onAllNodesWithText("Optional").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Enter password").performScrollTo().performTextInput("hunter2")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter password").assertDoesNotExist()

        composeRule.onNodeWithText("Jellyfin").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Jellyfin").assertIsSelected()
        // Now both Server name and Password have "Optional"
        assertEquals(2, composeRule.onAllNodesWithText("Optional").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("https://jellyfin.example.com:8096").assertIsDisplayed()
        // Password cleared on backend tile switch
        composeRule.onNodeWithText("Enter password").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editMode_locksBackendGrid_andNeverPrefillsStoredSecret() {
        val existing = ServerProfile(
            id = "p-jf",
            name = "Home Jellyfin",
            type = BackendType.JELLYFIN,
            url = "https://jf.example.com:8096",
            userId = "u-1",
            credentials = ProfileCredentials.Jellyfin(
                username = "alice",
                accessToken = "super-secret-token",
                serverId = "srv-1",
                deviceId = "dev-1",
            ),
            allowSelfSigned = true,
            allowCleartext = false,
        )
        val vm = createTestViewModel(listOf(existing))
        val messenger = UiMessenger()

        composeRule.setContent {
            FeishinTheme(dark = true) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ServerFormContent(
                        vm = vm,
                        messenger = messenger,
                        existingProfile = existing,
                        addMode = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Backend type can't be changed. Remove and re-add to switch.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Navidrome").assertIsNotEnabled()
        composeRule.onNodeWithText("OpenSubsonic").assertIsNotEnabled()
        composeRule.onNodeWithText("Jellyfin").assertIsNotEnabled()

        composeRule.onNodeWithText("Home Jellyfin").assertIsDisplayed()
        composeRule.onNodeWithText("https://jf.example.com:8096").assertIsDisplayed()
        composeRule.onNodeWithText("alice").assertIsDisplayed()
        composeRule.onNodeWithText("Leave blank to keep current").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("super-secret-token").assertDoesNotExist()
        composeRule.onNodeWithText("TLS certificate chain won't be strictly verified")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Save & Connect").performScrollTo().assertIsDisplayed()
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?) = synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = synchronized(data) { (data[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int) = synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long) = synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float) = synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean) = synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?) = synchronized(data) { data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) updates[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) updates[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) updates[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) updates[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) updates[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                synchronized(data) {
                    if (clearAll) data.clear()
                    removals.forEach { data.remove(it) }
                    updates.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
