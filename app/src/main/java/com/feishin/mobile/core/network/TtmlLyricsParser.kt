package com.feishin.mobile.core.network

import java.util.regex.Pattern

/**
 * Parses Timed Text Markup Language (TTML) lyrics into [Lyrics].
 * Supports paragraph line timing (`<p begin="..." end="...">`), agent roles
 * (`ttm:agent="v1"`), and word/syllable-level spans (`<span begin="..." end="...">word</span>`).
 */
object TtmlLyricsParser {

    private val pPattern = Pattern.compile(
        """<p\b([^>]*)>(.*?)</p>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL,
    )
    private val spanPattern = Pattern.compile(
        """<span\b([^>]*)>(.*?)</span>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL,
    )
    private val attrPattern = Pattern.compile("""([a-zA-Z0-9:_]+)="([^"]*)"""")

    fun parse(
        ttmlXml: String,
        source: LyricsSource = LyricsSource.BETTER_LYRICS,
    ): Lyrics {
        if (ttmlXml.isBlank()) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }

        val lines = mutableListOf<LyricLine>()
        val pMatcher = pPattern.matcher(ttmlXml)

        while (pMatcher.find()) {
            val pAttrsRaw = pMatcher.group(1).orEmpty()
            val pBody = pMatcher.group(2).orEmpty()

            val pAttrs = parseAttributes(pAttrsRaw)
            val beginStr = pAttrs["begin"] ?: pAttrs["beginTime"]
            if (beginStr == null) continue

            val lineStartMs = parseTimestampMs(beginStr)
            val agent = pAttrs["ttm:agent"] ?: pAttrs["agent"]

            // Parse syllable/word spans if present
            val words = mutableListOf<LyricWord>()
            val spanMatcher = spanPattern.matcher(pBody)
            val spanTexts = StringBuilder()

            while (spanMatcher.find()) {
                val spanAttrsRaw = spanMatcher.group(1).orEmpty()
                val spanTextRaw = spanMatcher.group(2).orEmpty()
                val spanAttrs = parseAttributes(spanAttrsRaw)
                val spanBeginStr = spanAttrs["begin"] ?: spanAttrs["beginTime"]

                val wordText = cleanXmlText(spanTextRaw)
                val spanStartMs = if (spanBeginStr != null) {
                    parseTimestampMs(spanBeginStr)
                } else {
                    lineStartMs
                }

                if (wordText.isNotEmpty()) {
                    words.add(LyricWord(startMs = spanStartMs, text = wordText))
                    spanTexts.append(wordText)
                }
            }

            val lineText = if (words.isNotEmpty()) {
                spanTexts.toString().trim()
            } else {
                cleanXmlText(pBody).trim()
            }

            if (lineText.isNotEmpty()) {
                lines.add(
                    LyricLine(
                        startMs = lineStartMs,
                        text = lineText,
                        words = words,
                        agent = agent,
                    ),
                )
            }
        }

        if (lines.isEmpty()) {
            return Lyrics(emptyList(), 0, LyricsKind.NONE, source = source)
        }

        // Sort defensively by start time
        val sortedLines = lines.sortedBy { it.startMs }
        return Lyrics(
            lines = sortedLines,
            offsetMs = 0L,
            kind = LyricsKind.SYNCED,
            source = source,
        )
    }

    /**
     * Parses standard TTML timestamp formats:
     * - `33.642` or `33.642s` (seconds)
     * - `1:04.304` or `01:04.304` (minutes:seconds)
     * - `00:01:04.304` (hours:minutes:seconds)
     * - `33642ms` (milliseconds)
     */
    fun parseTimestampMs(timeStr: String): Long {
        val trimmed = timeStr.trim()
        if (trimmed.endsWith("ms", ignoreCase = true)) {
            return trimmed.dropLast(2).trim().toLongOrNull() ?: 0L
        }
        val withoutS = trimmed.removeSuffix("s").removeSuffix("S")
        val parts = withoutS.split(":")
        return when (parts.size) {
            1 -> {
                val sec = parts[0].toDoubleOrNull() ?: 0.0
                (sec * 1000.0).toLong().coerceAtLeast(0L)
            }
            2 -> {
                val min = parts[0].toLongOrNull() ?: 0L
                val sec = parts[1].toDoubleOrNull() ?: 0.0
                (min * 60_000L + (sec * 1000.0).toLong()).coerceAtLeast(0L)
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: 0L
                val min = parts[1].toLongOrNull() ?: 0L
                val sec = parts[2].toDoubleOrNull() ?: 0.0
                (hr * 3600_000L + min * 60_000L + (sec * 1000.0).toLong()).coerceAtLeast(0L)
            }
            else -> 0L
        }
    }

    private fun parseAttributes(attrsRaw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val matcher = attrPattern.matcher(attrsRaw)
        while (matcher.find()) {
            val key = matcher.group(1).orEmpty()
            val value = matcher.group(2).orEmpty()
            map[key] = value
        }
        return map
    }

    private fun cleanXmlText(raw: String): String {
        return raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<[^>]+>"""), "") // Strip remaining tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&nbsp;", " ")
    }
}
