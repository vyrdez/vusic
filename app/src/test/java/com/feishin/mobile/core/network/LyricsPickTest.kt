package com.feishin.mobile.core.network

import org.junit.Assert.assertSame
import org.junit.Test

class LyricsPickTest {

    private fun plain(text: String, source: LyricsSource) = Lyrics(
        plainText = text,
        kind = LyricsKind.PLAIN,
        source = source,
    )

    private fun synced(text: String, source: LyricsSource) = Lyrics(
        lines = listOf(LyricLine(1000L, text)),
        kind = LyricsKind.SYNCED,
        source = source,
    )

    @Test
    fun `plain online lyrics are replaced by synced metadata lyrics`() {
        val online = plain("Plain text from lrclib", LyricsSource.LRCLIB)
        val metadata = synced("Synced line from metadata", LyricsSource.SERVER_METADATA)

        assertSame(metadata, LyricsPick.upgrade(online = online, metadata = metadata))
    }

    @Test
    fun `synced online lyrics are never replaced`() {
        val online = synced("Online line", LyricsSource.LRCLIB)
        val metadata = synced("Metadata line", LyricsSource.SERVER_METADATA)

        assertSame(online, LyricsPick.upgrade(online = online, metadata = metadata))
    }

    @Test
    fun `plain metadata does not beat plain online text`() {
        val online = plain("online", LyricsSource.LRCLIB)
        val metadata = plain("metadata", LyricsSource.SERVER_METADATA)

        assertSame(online, LyricsPick.upgrade(online = online, metadata = metadata))
    }

    @Test
    fun `missing metadata keeps the online result`() {
        val online = plain("online", LyricsSource.LRCLIB)

        assertSame(online, LyricsPick.upgrade(online = online, metadata = null))
    }
}
