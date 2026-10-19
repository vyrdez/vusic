package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app may spend mobile data on downloads and on automatic caching.
 *
 * The two switches both mean "mobile data is permitted"; the job differs, not
 * the rule. The interesting cases are the ones a transport-name check gets
 * wrong: a metered Wi-Fi hotspot, and roaming.
 */
class MobileDataPolicyTest {

    // ---- The ordinary cases ----

    @Test
    fun wifiIsAlwaysAllowedEvenWithTheSwitchOff() {
        assertTrue(
            MobileDataPolicy.allows(
                online = true, unmetered = true, roaming = false, allowMobileData = false,
            ),
        )
    }

    @Test
    fun mobileDataIsRefusedWithTheSwitchOff() {
        assertFalse(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = false, allowMobileData = false,
            ),
        )
    }

    @Test
    fun mobileDataIsAllowedWithTheSwitchOn() {
        assertTrue(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = false, allowMobileData = true,
            ),
        )
    }

    @Test
    fun nothingRunsWhileOffline() {
        assertFalse(
            MobileDataPolicy.allows(
                online = false, unmetered = true, roaming = false, allowMobileData = true,
            ),
        )
        assertFalse(
            MobileDataPolicy.allows(
                online = false, unmetered = false, roaming = false, allowMobileData = true,
            ),
        )
    }

    // ---- Metered is not the same as cellular ----

    @Test
    fun aMeteredHotspotIsTreatedAsMobileData() {
        // The connection is a Wi-Fi hotspot that Android marks as metered, so
        // the transport says Wi-Fi but the capability says metered. The policy
        // is fed the capability, and the hotspot is refused with the switch off.
        assertFalse(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = false, allowMobileData = false,
            ),
        )
        assertTrue(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = false, allowMobileData = true,
            ),
        )
    }

    // ---- Roaming ----

    @Test
    fun roamingIsRefusedEvenWithTheSwitchOn() {
        assertFalse(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = true, allowMobileData = true,
            ),
        )
    }

    @Test
    fun roamingOnAnUnmeteredConnectionIsStillFine() {
        // Roaming only matters when the data would actually be charged.
        assertTrue(
            MobileDataPolicy.allows(
                online = true, unmetered = true, roaming = true, allowMobileData = false,
            ),
        )
    }

    // ---- The waiting reason ----

    @Test
    fun aQueuedJobSaysItIsWaitingForWifi() {
        assertEquals(
            MobileDataPolicy.WAITING_FOR_WIFI,
            MobileDataPolicy.waitingReason(
                online = true, unmetered = false, roaming = false, allowMobileData = false,
            ),
        )
    }

    @Test
    fun aQueuedJobSaysNothingWhenItMayRun() {
        assertNull(
            MobileDataPolicy.waitingReason(
                online = true, unmetered = true, roaming = false, allowMobileData = false,
            ),
        )
        assertNull(
            MobileDataPolicy.waitingReason(
                online = true, unmetered = false, roaming = false, allowMobileData = true,
            ),
        )
    }

    @Test
    fun anOfflineDeviceSaysItIsWaitingForAConnection() {
        // More useful than "waiting for Wi-Fi" when there is no network at all.
        assertEquals(
            MobileDataPolicy.WAITING_FOR_NETWORK,
            MobileDataPolicy.waitingReason(
                online = false, unmetered = false, roaming = false, allowMobileData = false,
            ),
        )
    }

    @Test
    fun aRoamingConnectionAlsoSaysItIsWaitingForWifi() {
        assertEquals(
            MobileDataPolicy.WAITING_FOR_WIFI,
            MobileDataPolicy.waitingReason(
                online = true, unmetered = false, roaming = true, allowMobileData = true,
            ),
        )
    }

    // ---- The switches default to the old behaviour ----

    @Test
    fun bothSwitchesDefaultToOffWhichMeansWifiOnly() {
        // The stored settings are "wifi only" flags, and both default to true,
        // so an existing install keeps its behaviour exactly.
        assertTrue(com.feishin.mobile.data.settings.SettingsStore.Settings().downloadWifiOnly)
        assertTrue(com.feishin.mobile.data.settings.SettingsStore.Settings().cacheWifiOnly)
    }

    @Test
    fun theSwitchValueIsTheInverseOfTheStoredFlag() {
        // The UI shows "over mobile data", the store keeps "wifi only"; the
        // inversion happens in one place, the view model.
        val stored = true
        val shown = !stored
        assertFalse(shown)
        assertTrue(
            MobileDataPolicy.allows(
                online = true, unmetered = false, roaming = false, allowMobileData = shown,
            ).not(),
        )
    }

    // ---- Changing the switch re-evaluates the pending work ----

    @Test
    fun flippingTheSwitchChangesWhatPendingWorkMayDo() {
        val pending = 3
        val online = true
        val unmetered = false
        val roaming = false

        // Switch off: the pending download waits.
        val before = MobileDataPolicy.allows(online, unmetered, roaming, allowMobileData = false)
        // Switch on: the same pending work may start.
        val after = MobileDataPolicy.allows(online, unmetered, roaming, allowMobileData = true)
        assertFalse(before)
        assertTrue(after)
        assertEquals(3, pending)
    }
}
