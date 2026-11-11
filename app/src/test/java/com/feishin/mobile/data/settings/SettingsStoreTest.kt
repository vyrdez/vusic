package com.feishin.mobile.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newDataStore(scope: kotlinx.coroutines.CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
        )

    @Test
    fun legacyRemovedPreferencesDoNotCrashAndKeepDefaults() = runTest {
        val dataStore = newDataStore(backgroundScope)
        dataStore.edit { it[stringPreferencesKey("legacy_display_mode")] = "MAX" }

        val settings = SettingsStore(dataStore).flow.first()

        assertEquals(20, settings.radioBatchSize)
        assertEquals(ThemeMode.DARK, settings.theme)
    }

    @Test
    fun radioBatchSizeDefaultsAndClamps() = runTest {
        val store = SettingsStore(newDataStore(backgroundScope))
        assertEquals(listOf(10, 20, 30, 50), SettingsStore.RADIO_BATCH_OPTIONS)
        assertEquals(20, store.flow.first().radioBatchSize)

        store.setRadioBatchSize(30)
        assertEquals(30, store.flow.first().radioBatchSize)

        store.setRadioBatchSize(5)
        assertEquals(10, store.flow.first().radioBatchSize)

        store.setRadioBatchSize(100)
        assertEquals(50, store.flow.first().radioBatchSize)
    }
}
