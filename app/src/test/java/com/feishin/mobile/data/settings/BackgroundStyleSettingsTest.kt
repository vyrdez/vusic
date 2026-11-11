package com.feishin.mobile.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackgroundStyleSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "bg_style_${UUID.randomUUID()}.preferences_pb") },
        )

    @Test
    fun defaultsApplyWhenKeysAbsentForNewAndExistingInstalls() = runTest {
        val freshStore = SettingsStore(newDataStore(backgroundScope))
        val fresh = freshStore.flow.first()
        assertEquals(BackgroundStyle.DEFAULT, fresh.miniPlayerBgStyle)
        assertEquals(BackgroundStyle.GRADIENT, fresh.playerBgStyle)
        assertEquals(BackgroundStyle.DEFAULT, fresh.queueBgStyle)

        // Existing install simulation: older keys exist, bg style keys do not.
        val existingDataStore = newDataStore(backgroundScope)
        existingDataStore.edit {
            it[stringPreferencesKey("theme")] = "DARK"
            it[booleanPreferencesKey("dynamic_np_color")] = true
        }
        val existing = SettingsStore(existingDataStore).flow.first()
        assertEquals(BackgroundStyle.DEFAULT, existing.miniPlayerBgStyle)
        assertEquals(BackgroundStyle.GRADIENT, existing.playerBgStyle)
        assertEquals(BackgroundStyle.DEFAULT, existing.queueBgStyle)
    }

    @Test
    fun roundTripPersistenceForAllThreeBackgroundStyles() = runTest {
        val store = SettingsStore(newDataStore(backgroundScope))

        for (style in BackgroundStyle.entries) {
            store.setMiniPlayerBgStyle(style)
            assertEquals(style, store.flow.first().miniPlayerBgStyle)

            store.setPlayerBgStyle(style)
            assertEquals(style, store.flow.first().playerBgStyle)

            store.setQueueBgStyle(style)
            assertEquals(style, store.flow.first().queueBgStyle)
        }
    }

    @Test
    fun fromStorageParsesValidKeysAndFallsBackOnUnknownOrNull() {
        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage("default", BackgroundStyle.BLUR))
        assertEquals(BackgroundStyle.GRADIENT, BackgroundStyle.fromStorage("gradient", BackgroundStyle.DEFAULT))
        assertEquals(BackgroundStyle.BLUR, BackgroundStyle.fromStorage("blur", BackgroundStyle.DEFAULT))

        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage(null, BackgroundStyle.DEFAULT))
        assertEquals(BackgroundStyle.GRADIENT, BackgroundStyle.fromStorage(null, BackgroundStyle.GRADIENT))
        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage("", BackgroundStyle.DEFAULT))
        assertEquals(BackgroundStyle.GRADIENT, BackgroundStyle.fromStorage("invalid_style", BackgroundStyle.GRADIENT))
        assertEquals(BackgroundStyle.DEFAULT, BackgroundStyle.fromStorage("corrupt", BackgroundStyle.DEFAULT))
    }

    @Test
    fun corruptStoredPreferencesFallBackToPerSettingDefaults() = runTest {
        val dataStore = newDataStore(backgroundScope)
        dataStore.edit {
            it[stringPreferencesKey("mini_player_bg_style")] = "unknown_mini"
            it[stringPreferencesKey("player_bg_style")] = "unknown_player"
            it[stringPreferencesKey("queue_bg_style")] = ""
        }

        val settings = SettingsStore(dataStore).flow.first()

        assertEquals(BackgroundStyle.DEFAULT, settings.miniPlayerBgStyle)
        assertEquals(BackgroundStyle.GRADIENT, settings.playerBgStyle)
        assertEquals(BackgroundStyle.DEFAULT, settings.queueBgStyle)
    }
}
