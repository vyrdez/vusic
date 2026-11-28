package com.feishin.mobile.ui.login

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.feishin.mobile.data.settings.SettingsStore
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

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
        val file = File(ctx.cacheDir, "login_screen_test_${UUID.randomUUID()}.preferences_pb")
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
    fun subtitleFollowsSelectedBackendTile_includingGoogleDrive_andHidesSecurityCard() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()
        val shown = mutableStateListOf<String>()

        composeRule.setContent {
            LaunchedEffect(messenger) {
                messenger.messages.collect { shown += it }
            }
            FeishinTheme(dark = true) {
                LoginScreen(
                    vm = vm,
                    messenger = messenger,
                    addMode = false,
                )
            }
        }

        composeRule.onNodeWithText("Connect to your Navidrome server").assertIsDisplayed()

        composeRule.onNodeWithText("OpenSubsonic").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect to your OpenSubsonic server").assertIsDisplayed()

        composeRule.onNodeWithText("Jellyfin").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect to your Jellyfin server").assertIsDisplayed()

        composeRule.onNodeWithText("Google Drive").performClick()
        composeRule.waitForIdle()
        assertTrue(shown.none { it.contains("coming soon", ignoreCase = true) })
        composeRule.onNodeWithText("Google Drive").assertIsSelected()
        composeRule.onNodeWithText("Connect to your Google Drive").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in with Google").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Allow self-signed certificate").assertDoesNotExist()
    }

    @Test
    fun connectionBanner_infoState_rendersAndDismisses() {
        var state by mutableStateOf<ConnectionBannerState>(
            ConnectionBannerState.Info("Google sign-in cancelled"),
        )

        composeRule.setContent {
            FeishinTheme(dark = true) {
                ConnectionBanner(
                    state = state,
                    onDismiss = { state = ConnectionBannerState.Idle },
                )
            }
        }

        composeRule.onNodeWithText("Google sign-in cancelled").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Google sign-in cancelled").assertDoesNotExist()
    }

    @Test
    fun connectionBanner_hasPoliteLiveRegion_andDismissHidesIt() {
        var state by mutableStateOf<ConnectionBannerState>(
            ConnectionBannerState.Error("Invalid server URL format."),
        )

        composeRule.setContent {
            FeishinTheme(dark = true) {
                ConnectionBanner(
                    state = state,
                    onDismiss = { state = ConnectionBannerState.Idle },
                )
            }
        }

        composeRule.onNodeWithText("Invalid server URL format.").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Invalid server URL format.").assertDoesNotExist()
    }

    @Test
    fun deviceFilesButton_invokesCallbackAndEmitsOfflineEvent() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()
        val shown = mutableStateListOf<String>()
        var deviceCallbackCount = 0

        composeRule.setContent {
            LaunchedEffect(messenger) {
                messenger.messages.collect { shown += it }
            }
            FeishinTheme(dark = true) {
                LoginScreen(
                    vm = vm,
                    messenger = messenger,
                    addMode = false,
                    onUseDeviceFilesOnly = { deviceCallbackCount++ },
                )
            }
        }

        composeRule.onNodeWithText("Use Device files only").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            shown.contains("Offline mode · Using local device files only")
        }
        assertTrue(deviceCallbackCount == 1)
    }

    @Test
    fun incompleteFormTap_emitsValidationSnackbar() {
        val vm = createTestViewModel()
        val messenger = UiMessenger()
        val shown = mutableStateListOf<String>()

        composeRule.setContent {
            LaunchedEffect(messenger) {
                messenger.messages.collect { shown += it }
            }
            FeishinTheme(dark = true) {
                LoginScreen(
                    vm = vm,
                    messenger = messenger,
                    addMode = false,
                )
            }
        }

        composeRule.onNodeWithText("Test & Connect").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(shown.contains("Fill in Server URL, Username, and Password first"))
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
