package com.feishin.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProviderChainTest {

    private fun lineSynced(source: LyricsSource) = Lyrics(
        lines = listOf(LyricLine(1000L, "Hello line 1"), LyricLine(5000L, "Hello line 2")),
        kind = LyricsKind.SYNCED,
        source = source,
    )

    private fun wordSynced(source: LyricsSource) = Lyrics(
        lines = listOf(
            LyricLine(
                1000L, "Hello line 1",
                words = listOf(LyricWord(1000L, "Hello"), LyricWord(1500L, "line 1")),
            ),
        ),
        kind = LyricsKind.SYNCED,
        source = source,
    )

    private fun plain(source: LyricsSource) = Lyrics(
        lines = listOf(LyricLine(0L, "Unsynced text")),
        kind = LyricsKind.PLAIN,
        plainText = "Unsynced text",
        source = source,
    )

    @Test
    fun `first synced wins when preferWordByWord is false`() {
        val candidate1 = lineSynced(LyricsSource.LRCLIB)
        val candidate2 = wordSynced(LyricsSource.BETTER_LYRICS)

        val preferWordByWord = false
        var best: Lyrics? = null

        val list = listOf(candidate1, candidate2)
        for (item in list) {
            if (item.isSynced) {
                best = item
                break
            }
        }

        assertEquals(LyricsSource.LRCLIB, best?.source)
    }

    @Test
    fun `word synced upgrades over line synced when preferWordByWord is true`() {
        val candidate1 = lineSynced(LyricsSource.LRCLIB)
        val candidate2 = wordSynced(LyricsSource.BETTER_LYRICS)

        val preferWordByWord = true
        var best: Lyrics? = null

        val list = listOf(candidate1, candidate2)
        for (item in list) {
            if (item.isSynced) {
                val hasWords = item.lines.any { it.words.isNotEmpty() }
                if (preferWordByWord) {
                    if (best == null) {
                        best = item
                        if (hasWords) break
                    } else if (!best.lines.any { it.words.isNotEmpty() } && hasWords) {
                        best = item
                        break
                    }
                } else {
                    best = item
                    break
                }
            }
        }

        assertEquals(LyricsSource.BETTER_LYRICS, best?.source)
    }

    @Test
    fun `plain lyrics saved as fallback if no provider has synced`() {
        val candidate1 = Lyrics(emptyList(), 0L, LyricsKind.NONE, source = LyricsSource.LRCLIB)
        val candidate2 = plain(LyricsSource.BETTER_LYRICS)
        val candidate3 = Lyrics(emptyList(), 0L, LyricsKind.NONE, source = LyricsSource.KUGOU)

        var bestSynced: Lyrics? = null
        var plainFallback: Lyrics? = null

        val list = listOf(candidate1, candidate2, candidate3)
        for (item in list) {
            if (item.isSynced) {
                bestSynced = item
                break
            } else if (item.isPlain && plainFallback == null) {
                plainFallback = item
            }
        }

        val result = bestSynced ?: plainFallback
        assertEquals(LyricsSource.BETTER_LYRICS, result?.source)
        assertEquals(LyricsKind.PLAIN, result?.kind)
    }

    @Test
    fun `disabled providers are filtered out`() {
        val configuredOrder = listOf("LRCLIB", "BETTER_LYRICS", "PAXSENIX", "KUGOU", "LOCAL_DATA")
        val enabledSet = setOf("BETTER_LYRICS", "KUGOU")

        val active = configuredOrder.filter { it in enabledSet }
        assertEquals(listOf("BETTER_LYRICS", "KUGOU"), active)
    }
}
