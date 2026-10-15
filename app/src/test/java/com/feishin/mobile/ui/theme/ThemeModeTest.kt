package com.feishin.mobile.ui.theme

import com.feishin.mobile.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part E: AMOLED migration and the System mapping. */
class ThemeModeTest {

    @Test
    fun amoledMigratesToDark() {
        assertEquals(ThemeMode.DARK, migrateStoredTheme("AMOLED"))
        assertEquals(ThemeMode.DARK, migrateStoredTheme("amoled"))
    }

    @Test
    fun enumNoLongerContainsAmoled() {
        assertFalse(ThemeMode.entries.any { it.name == "AMOLED" })
        assertEquals(3, ThemeMode.entries.size)
        assertEquals(
            listOf("SYSTEM", "DARK", "LIGHT"),
            ThemeMode.entries.map { it.name },
        )
    }

    @Test
    fun knownValuesRoundTrip() {
        assertEquals(ThemeMode.SYSTEM, migrateStoredTheme("SYSTEM"))
        assertEquals(ThemeMode.DARK, migrateStoredTheme("DARK"))
        assertEquals(ThemeMode.LIGHT, migrateStoredTheme("LIGHT"))
    }

    @Test
    fun unknownAndNullFallBackToDark() {
        assertEquals(ThemeMode.DARK, migrateStoredTheme(null))
        assertEquals(ThemeMode.DARK, migrateStoredTheme(""))
        assertEquals(ThemeMode.DARK, migrateStoredTheme("SEPIA"))
        assertEquals(ThemeMode.DARK, migrateStoredTheme("{\"junk\":1}"))
    }

    @Test
    fun systemFollowsTheOsInBothDirections() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun darkAndLightIgnoreTheOs() {
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = false))
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = true))
        assertFalse(resolveDark(ThemeMode.LIGHT, systemDark = true))
        assertFalse(resolveDark(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun nullStoredBehavesAsDark() {
        // A fresh install (nothing stored) is Dark regardless of the OS, which
        // is the documented default and keeps the dark look as the baseline.
        assertTrue(resolveDark(null, systemDark = false))
        assertTrue(resolveDark(null, systemDark = true))
    }
}
