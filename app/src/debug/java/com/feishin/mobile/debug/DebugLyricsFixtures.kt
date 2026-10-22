package com.feishin.mobile.debug

import com.feishin.mobile.core.network.LyricLine
import com.feishin.mobile.core.network.LyricWord
import com.feishin.mobile.core.network.Lyrics
import com.feishin.mobile.core.network.LyricsKind
import com.feishin.mobile.core.network.LyricsSource

object DebugLyricsFixtures {

    val synced = Lyrics(
        lines = listOf(
            LyricLine(0L, "Look at the stars"),
            LyricLine(3500L, "Look how they shine for you"),
            LyricLine(7000L, "And everything you do"),
            LyricLine(11000L, "Yeah, they were all yellow"),
            LyricLine(16000L, ""), // Gap
            LyricLine(19000L, "I came along"),
            LyricLine(22500L, "I wrote a song for you"),
            LyricLine(26000L, "And all the things you do"),
            LyricLine(30000L, "And it was called Yellow"),
        ),
        kind = LyricsKind.SYNCED,
        source = LyricsSource.LRCLIB,
    )

    val unsynced = Lyrics(
        lines = emptyList(),
        plainText = "Look at the stars\nLook how they shine for you\nAnd everything you do\nYeah, they were all yellow\n\nI came along\nI wrote a song for you",
        kind = LyricsKind.PLAIN,
        source = LyricsSource.SERVER_METADATA,
    )

    val none = Lyrics(
        lines = emptyList(),
        kind = LyricsKind.NONE,
        source = LyricsSource.CACHE,
    )

    val instrumental = Lyrics(
        lines = emptyList(),
        kind = LyricsKind.INSTRUMENTAL,
        source = LyricsSource.LRCLIB,
    )

    val word = Lyrics(
        lines = listOf(
            LyricLine(
                1000L,
                "Look at the stars",
                words = listOf(
                    LyricWord(1000L, "Look"),
                    LyricWord(1400L, "at"),
                    LyricWord(1800L, "the"),
                    LyricWord(2200L, "stars"),
                ),
            ),
            LyricLine(
                4000L,
                "Look how they shine for you",
                words = listOf(
                    LyricWord(4000L, "Look"),
                    LyricWord(4400L, "how"),
                    LyricWord(4800L, "they"),
                    LyricWord(5200L, "shine"),
                    LyricWord(5800L, "for"),
                    LyricWord(6200L, "you"),
                ),
            ),
        ),
        kind = LyricsKind.SYNCED,
        source = LyricsSource.LRCLIB,
    )

    val long = Lyrics(
        lines = (0..60).map { i ->
            LyricLine(i * 3000L, "Verse line $i of a very long epic song that goes on and on")
        },
        kind = LyricsKind.SYNCED,
        source = LyricsSource.LRCLIB,
    )

    val cjk = Lyrics(
        lines = listOf(
            LyricLine(1000L, "ねえ、笑ってくれよ - Please smile for me"),
            LyricLine(5000L, "晩餐歌を歌おう - Let's sing the dinner song"),
            LyricLine(9500L, "さよならは言わないでおくれよ - Don't say goodbye"),
            LyricLine(14000L, "愛していたいんだよ - I just want to keep loving you"),
        ),
        lang = "ja",
        kind = LyricsKind.SYNCED,
        source = LyricsSource.LRCLIB,
    )
}
