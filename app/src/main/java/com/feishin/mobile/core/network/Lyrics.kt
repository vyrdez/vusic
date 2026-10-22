package com.feishin.mobile.core.network

import kotlinx.serialization.Serializable

/** One timed word inside a synced line. Empty when only line-level timing exists. */
@Serializable
data class LyricWord(val startMs: Long, val text: String)

/** One line of lyrics. [words] is non-empty only when word-level timing exists. */
@Serializable
data class LyricLine(
    val startMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val agent: String? = null,
)

/** How the lyrics should be presented and synced. */
@Serializable
enum class LyricsKind {
    /** Structured synced lines (or LRC with stamps) - highlight follows playback. */
    SYNCED,
    /** Plain text block - no timing, shown as read-only text. */
    PLAIN,
    /** No lyrics at all for this song. */
    NONE,
    /** The track is instrumental. */
    INSTRUMENTAL,
}

/** The provenance of these lyrics. */
@Serializable
enum class LyricsSource {
    /** Retrieved from local Room cache. */
    CACHE,
    /** Fetched online from lrclib.net. */
    LRCLIB,
    /** Fetched from BetterLyrics TTML API. */
    BETTER_LYRICS,
    /** Paxsenix API. */
    PAXSENIX,
    /** Fetched from KuGou. */
    KUGOU,
    /** Fetched from OpenSubsonic server metadata (structuredLyrics or getLyrics). */
    SERVER_METADATA,
    /** Extracted directly from device audio file tags (ID3v2, Vorbis, MP4). */
    FILE_TAGS,
}

/**
 * Fully resolved lyrics for one song. Produced by [LyricsProvider] implementations;
 * pure data so the UI, ViewModel, and Room cache never touch the network directly.
 *
 * Line start times are stored unshifted; [offsetMs] is subtracted when
 * comparing against the player position (see [lineIndexAt]).
 */
@Serializable
data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val offsetMs: Long = 0,
    val kind: LyricsKind = LyricsKind.SYNCED,
    val plainText: String = "",
    val lang: String = "",
    val source: LyricsSource = LyricsSource.LRCLIB,
) {
    val isSynced: Boolean get() = kind == LyricsKind.SYNCED && lines.isNotEmpty()
    val isPlain: Boolean get() = kind == LyricsKind.PLAIN && (lines.isNotEmpty() || plainText.isNotBlank())
    val isNone: Boolean get() = kind == LyricsKind.NONE || (lines.isEmpty() && plainText.isBlank())
    val isInstrumental: Boolean get() = kind == LyricsKind.INSTRUMENTAL

    /** Index of the line active at [positionMs], or -1 before the first line. */
    fun lineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val adjusted = positionMs - offsetMs
        if (adjusted < lines.first().startMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= adjusted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** Index of the word active within [lineIndex] at [positionMs], or -1 before the first word. */
    fun wordIndexAt(lineIndex: Int, positionMs: Long): Int {
        val line = lines.getOrNull(lineIndex) ?: return -1
        val words = line.words
        if (words.isEmpty()) return -1
        val adjusted = positionMs - offsetMs
        if (adjusted < words.first().startMs) return -1
        var low = 0
        var high = words.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (words[mid].startMs <= adjusted) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** The line active at [positionMs], or null when playback hasn't reached the first line. */
    fun lineAt(positionMs: Long): LyricLine? {
        val i = lineIndexAt(positionMs)
        return if (i < 0) null else lines[i]
    }
}

/** Builds a [Lyrics] from the legacy parsed [SyncedLyrics] (line-level only, no words). */
fun fromSynced(
    synced: SyncedLyrics,
    kind: LyricsKind = LyricsKind.SYNCED,
    source: LyricsSource = LyricsSource.SERVER_METADATA,
): Lyrics =
    Lyrics(
        lines = synced.lines.map { LyricLine(it.startMs, it.text) },
        offsetMs = synced.offsetMs,
        kind = kind,
        source = source,
    )
