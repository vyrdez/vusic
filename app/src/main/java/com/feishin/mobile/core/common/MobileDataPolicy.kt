package com.feishin.mobile.core.common

/**
 * When the app may spend mobile data on downloads and on automatic caching.
 *
 * Pure, so the rule is unit-testable without a device and without a network.
 *
 * The two switches in Settings ("Download over mobile data" and "Auto-cache
 * over mobile data") both mean the same thing to this policy: mobile data is
 * permitted. What differs is the job, never the rule.
 *
 * Two details matter and are easy to get wrong:
 *
 *  * **Metered is not the same as cellular.** A phone can be on Wi-Fi through a
 *    hotspot that Android marks as metered, and an Ethernet cable can be
 *    metered too. The real question is `NET_CAPABILITY_NOT_METERED`, not the
 *    name of the transport, so a metered hotspot is treated as mobile data.
 *  * **Roaming is never used.** The user did not ask for their data to be
 *    spent abroad, and it is the one case where "use mobile data" almost
 *    always means an unpleasant bill. Android tells us cheaply
 *    (`NET_CAPABILITY_NOT_ROAMING`), so roaming is always refused.
 */
object MobileDataPolicy {

    /** Reason shown while a job waits for an unmetered network. */
    const val WAITING_FOR_WIFI = "Waiting for Wi-Fi"

    /** Reason shown while a job waits for any network at all. */
    const val WAITING_FOR_NETWORK = "Waiting for a connection"

    /**
     * True when a job that needs the network may run right now.
     *
     * @param online the device has a validated internet connection.
     * @param unmetered the connection is not metered (Wi-Fi, usually).
     * @param roaming the connection is roaming.
     * @param allowMobileData the user's switch for this job.
     */
    fun allows(
        online: Boolean,
        unmetered: Boolean,
        roaming: Boolean,
        allowMobileData: Boolean,
    ): Boolean {
        // Never while offline, whatever the switches say.
        if (!online) return false
        // An unmetered connection is always fine.
        if (unmetered) return true
        // Roaming is refused even when mobile data is allowed.
        if (roaming) return false
        return allowMobileData
    }

    /**
     * Why a job is waiting, or null when it may run.
     *
     * The order matters: an offline device is reported as offline even if the
     * switch is off, because "waiting for a connection" is the more useful
     * thing to say.
     */
    fun waitingReason(
        online: Boolean,
        unmetered: Boolean,
        roaming: Boolean,
        allowMobileData: Boolean,
    ): String? = when {
        !online -> WAITING_FOR_NETWORK
        unmetered -> null
        roaming -> WAITING_FOR_WIFI
        !allowMobileData -> WAITING_FOR_WIFI
        else -> null
    }

    /**
     * True when the connection is one the app must not use for bulk transfers
     * without being told: metered, and (optionally) roaming.
     */
    fun isMetered(unmetered: Boolean): Boolean = !unmetered
}
