package com.feishin.mobile.core.common

/** Network class a stream request is going over. */
enum class NetworkClass { WIFI, MOBILE, OTHER }

/** A chosen stream/download encoding. */
data class StreamQuality(val bitrateKbps: Int, val format: String) {
    val isOriginal: Boolean get() = bitrateKbps <= 0 || format == "raw"
}

/**
 * Pure policy that turns the stored quality preferences plus the current network
 * class into a concrete request. Unit-testable; the player and downloader both
 * consult it so Wi-Fi and mobile settings stay consistent.
 */
object QualityPolicy {

    val BITRATES = listOf(0, 64, 128, 192, 256, 320)
    val FORMATS = listOf("raw", "mp3", "opus", "aac")

    fun forNetwork(
        network: NetworkClass,
        wifiBitrate: Int,
        wifiFormat: String,
        mobileBitrate: Int,
        mobileFormat: String,
    ): StreamQuality = when (network) {
        NetworkClass.WIFI -> StreamQuality(wifiBitrate, normalizeFormat(wifiFormat))
        NetworkClass.MOBILE -> StreamQuality(mobileBitrate, normalizeFormat(mobileFormat))
        // Ethernet/tethered behaves like Wi-Fi: unmetered.
        NetworkClass.OTHER -> StreamQuality(wifiBitrate, normalizeFormat(wifiFormat))
    }

    /** Downloads use their own quality, independent of the playback network. */
    fun forDownload(bitrate: Int, format: String, original: Boolean): StreamQuality =
        if (original) StreamQuality(0, "raw") else StreamQuality(bitrate, normalizeFormat(format))

    fun normalizeFormat(format: String): String =
        if (format.lowercase() in FORMATS) format.lowercase() else "raw"

    /** "Original" is only meaningful with the raw container. */
    fun label(bitrate: Int, format: String): String =
        if (bitrate <= 0 || format == "raw") "Original" else "$bitrate kbps $format"
}
