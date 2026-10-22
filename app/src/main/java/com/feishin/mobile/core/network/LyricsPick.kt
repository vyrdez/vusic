package com.feishin.mobile.core.network

/**
 * Decides which candidate to show when more than one source has lyrics.
 *
 * The only upgrade worth making today is plain online text versus synced
 * lyrics embedded in the song's own metadata: timings beat no timings.
 */
object LyricsPick {

    /**
     * Returns [metadata] only when [online] is plain text and [metadata] is
     * synced; every other combination keeps [online].
     */
    fun upgrade(online: Lyrics, metadata: Lyrics?): Lyrics =
        if (online.isPlain && metadata?.isSynced == true) metadata else online
}
