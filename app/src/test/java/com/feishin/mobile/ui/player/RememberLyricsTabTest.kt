package com.feishin.mobile.ui.player

import com.feishin.mobile.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RememberLyricsTabTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testDefaultTabIsSong() {
        val settings = SettingsStore.Settings()
        assertFalse("Default now playing tab should be Song (lyricsMode = false)", settings.nowPlayingLyricsMode)
    }

    @Test
    fun testTabStatePersistenceAndRestore() = runTest {
        val testDataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tmp.newFile("test_settings.preferences_pb") }
        )
        val store = SettingsStore(testDataStore)

        // 1. Initially false
        assertFalse(store.flow.first().nowPlayingLyricsMode)

        // 2. Select Lyrics tab -> true
        store.setNowPlayingLyricsMode(true)
        assertTrue(store.flow.first().nowPlayingLyricsMode)

        // 3. Clear queue -> resets to Song (false)
        store.setNowPlayingLyricsMode(false)
        assertFalse(store.flow.first().nowPlayingLyricsMode)
    }
}
