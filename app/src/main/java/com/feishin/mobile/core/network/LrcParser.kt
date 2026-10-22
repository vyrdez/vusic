package com.feishin.mobile.core.network

/**
 * Parses LRC text ([mm:ss.xx] time stamps) into [Lyrics].
 *
 * Supported:
 *   - `[mm:ss.xx]` / `[mm:ss,xx]` line stamps, centisecond (`.5`) and
 *     millisecond (`.500`) fractions.
 *   - Multiple stamps on one line (a line repeated at multiple times, e.g.
 *     `[00:10.00][00:45.00]Chorus`): produces a [LyricLine] for each stamp.
 *   - `[offset:+500]` and `[offset:-200]` in milliseconds shifting every line.
 *   - Blank timed lines become instrumental gaps (empty text at that time).
 *   - Enhanced `<mm:ss.xx>` karaoke stamps split a line into timed [LyricWord]s.
 *   - Metadata headers (`[ti:]`, `[ar:]`, `[al:]`, `[by:]`, etc.) are ignored.
 *   - Malformed lines skipped safely without crashing.
 *
 * A text with no timed lines yields [LyricsKind.NONE].
 */
object LrcParser {
    private val TIME_TAG = Regex("""([<\[])\s*(\d{1,3}):(\d{2})(?:[.,](\d{1,3}))?\s*([>\]])""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    private data class Tag(val timeMs: Long, val startIdx: Int, val endIdx: Int, val isAngle: Boolean)

    /** Milliseconds for mm:ss[.frac]. */
    private fun ms(minutes: Int, seconds: Int, frac: String?): Long {
        var f = 0L
        if (frac != null) {
            f = when {
                frac.length == 1 -> frac.toInt().toLong() * 100     // .5 -> 500 ms
                frac.length == 2 -> frac.toInt().toLong() * 10      // .50 -> 500 ms
                else -> frac.take(3).toInt().toLong()               // .500 -> 500 ms
            }
        }
        return minutes.toLong() * 60000 + seconds.toLong() * 1000 + f
    }

    fun parse(text: String, source: LyricsSource = LyricsSource.LRCLIB): Lyrics {
        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // Parse offset tag
            val offsetMatch = OFFSET_TAG.find(line)
            if (offsetMatch != null && TIME_TAG.find(line) == null) {
                runCatching {
                    offsetMs = offsetMatch.groupValues[1].toLong()
                }
                continue
            }

            // Find all time tags on the line
            val tags = mutableListOf<Tag>()
            try {
                for (m in TIME_TAG.findAll(line)) {
                    val openChar = m.groupValues[1]
                    val closeChar = m.groupValues[5]
                    // Ensure brackets match: [ ] or < >
                    val isAngle = openChar == "<" && closeChar == ">"
                    val isSquare = openChar == "[" && closeChar == "]"
                    if (!isAngle && !isSquare) continue

                    val minutes = m.groupValues[2].toInt()
                    val seconds = m.groupValues[3].toInt()
                    if (seconds >= 60) continue // Skip invalid seconds
                    val frac = m.groupValues[4].ifEmpty { null }

                    tags += Tag(
                        timeMs = ms(minutes, seconds, frac),
                        startIdx = m.range.first,
                        endIdx = m.range.last + 1,
                        isAngle = isAngle,
                    )
                }
            } catch (_: Exception) {
                continue
            }

            if (tags.isEmpty()) continue

            val squareTags = tags.filter { !it.isAngle }
            val angleTags = tags.filter { it.isAngle }.sortedBy { it.timeMs }

            // If there are no square bracket tags, but there are angle tags, use angle tags as stamps
            val lineStamps = if (squareTags.isNotEmpty()) squareTags else angleTags

            // Line text is everything after the last square bracket stamp (or the remaining text)
            val lastLineTag = lineStamps.maxByOrNull { it.endIdx } ?: continue
            var lineText = line.substring(lastLineTag.endIdx).trim()

            // Word-level timing from angle-bracket karaoke stamps if present
            val words = mutableListOf<LyricWord>()
            if (angleTags.size >= 2) {
                for (i in 0 until angleTags.lastIndex) {
                    val from = angleTags[i].endIdx
                    val to = angleTags[i + 1].startIdx
                    if (from <= to && from <= line.length && to <= line.length) {
                        val word = line.substring(from, to).trim()
                        if (word.isNotEmpty()) words += LyricWord(angleTags[i].timeMs, word)
                    }
                }
                val last = angleTags.last()
                if (last.endIdx <= line.length) {
                    val trail = line.substring(last.endIdx).trim()
                    if (trail.isNotEmpty()) words += LyricWord(last.timeMs, trail)
                }
                if (words.isNotEmpty()) {
                    lineText = words.joinToString(" ") { it.text }
                }
            }

            // Each square-bracket line stamp creates a line at that timestamp
            for (stamp in lineStamps) {
                lines += LyricLine(stamp.timeMs, lineText, words)
            }
        }

        // Deduplicate lines with exact same startMs and sort by startMs
        val deduped = lines.distinctBy { it.startMs to it.text }
            .sortedBy { it.startMs }

        return if (deduped.isEmpty()) {
            Lyrics(emptyList(), offsetMs, LyricsKind.NONE, source = source)
        } else {
            Lyrics(deduped, offsetMs, LyricsKind.SYNCED, source = source)
        }
    }
}
