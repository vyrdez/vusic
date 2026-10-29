@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.playback

import androidx.media3.common.Player
import com.feishin.mobile.core.common.DownloadStateMachine

object PlaybackRecovery {

    /**
     * Determines whether playback should be retried from the network.
     * Guards against retry loops by allowing at most one retry per failed item.
     */
    fun shouldRetryFromNetwork(
        errorCode: Int,
        alreadyRetried: Boolean,
        isOffline: Boolean = false,
    ): Boolean {
        if (isOffline || alreadyRetried) return false
        return true
    }

    fun shouldDeleteDownloadOnError(isOffline: Boolean, fileExistsAndValid: Boolean): Boolean =
        !isOffline && !fileExistsAndValid

    fun shouldEvictStreamCacheOnError(isOffline: Boolean): Boolean = !isOffline

    /**
     * In Media3, calling play() while in STATE_IDLE only sets playWhenReady = true
     * without preparing the media source, leaving the player stuck.
     */
    fun shouldPrepareBeforePlay(playbackState: Int): Boolean =
        playbackState == Player.STATE_IDLE

    /**
     * Rejects/evicts cached bodies that are smaller than the audio minimum
     * or are Subsonic JSON/XML error payloads.
     */
    fun isInvalidCachedPayload(bytes: Long, headerBytes: ByteArray? = null): Boolean {
        if (bytes < DownloadStateMachine.MIN_VALID_AUDIO_BYTES) return true
        if (headerBytes != null && headerBytes.isNotEmpty() && isJsonOrXmlPayload(headerBytes)) return true
        return false
    }

    /**
     * Checks if byte content resembles JSON or XML Subsonic error responses.
     */
    fun isJsonOrXmlPayload(headerBytes: ByteArray): Boolean {
        var i = 0
        if (headerBytes.size >= 3 &&
            headerBytes[0] == 0xEF.toByte() &&
            headerBytes[1] == 0xBB.toByte() &&
            headerBytes[2] == 0xBF.toByte()
        ) {
            i = 3
        }
        while (i < headerBytes.size) {
            val b = headerBytes[i].toInt().toChar()
            if (b.isWhitespace()) {
                i++
            } else {
                if (b == '{' || b == '<' || b == '[') {
                    return true
                }
                break
            }
        }
        val text = String(headerBytes, 0, minOf(headerBytes.size, 512), Charsets.UTF_8).lowercase()
        return text.contains("subsonic-response") || text.contains("\"error\"") || text.contains("<error")
    }
}
