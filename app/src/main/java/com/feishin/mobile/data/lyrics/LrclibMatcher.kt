package com.feishin.mobile.data.lyrics

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

object LrclibMatcher {

    private val SUFFIX_BRACKETS = Regex(
        """\s*[(\[](?:original\s+mix|feat\.?|ft\.?|remaster(?:ed)?|remix|live|bonus(?:\s+track)?|official|audio|deluxe|version|edit|mono|stereo|radio\s+edit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTIST_SPLIT = Regex("""\s*(?:/|,|&|\band\b|\bx\b)\s*""", RegexOption.IGNORE_CASE)

    /** Normalizes a string using NFKC, lowercase, stripping punctuation and redundant whitespace. */
    fun normalize(s: String): String {
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC)
        return nfkc.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /** Cleans bracketed and parenthesized tags (e.g. "(Original Mix)", "[Remastered]"). */
    fun cleanTitle(title: String): String {
        val cleaned = SUFFIX_BRACKETS.replace(title, "").trim()
        return cleaned.ifBlank { title.trim() }
    }

    /** Splits bilingual or multi-part titles separated by " - ". */
    fun splitTitleParts(title: String): List<String> {
        val cleaned = cleanTitle(title)
        val parts = cleaned.split(" - ").map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        result += cleaned
        if (parts.size >= 2) {
            result.addAll(parts)
        }
        return result.distinct()
    }

    /** Splits multi-artist strings into individual artist names, plus primary artist. */
    fun splitArtists(artist: String): List<String> {
        val trimmed = artist.trim()
        val parts = trimmed.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        if (parts.isNotEmpty()) {
            result += parts.first() // Primary artist
            result.addAll(parts)
        }
        result += trimmed
        return result.distinct()
    }

    /** Calculates Sørensen-Dice coefficient similarity between two strings (0.0 to 1.0). */
    fun diceSimilarity(s1: String, s2: String): Double {
        val n1 = normalize(s1)
        val n2 = normalize(s2)
        if (n1 == n2) return 1.0
        if (n1.isEmpty() || n2.isEmpty()) return 0.0

        if (n1.length < 2 || n2.length < 2) {
            return if (n1 == n2 || n1.contains(n2) || n2.contains(n1)) 0.8 else 0.0
        }

        val bigrams1 = (0 until n1.length - 1).map { n1.substring(it, it + 2) }
        val bigrams2 = (0 until n2.length - 1).map { n2.substring(it, it + 2) }.toMutableList()

        var matches = 0
        for (b in bigrams1) {
            val idx = bigrams2.indexOf(b)
            if (idx >= 0) {
                matches++
                bigrams2.removeAt(idx)
            }
        }

        return (2.0 * matches) / (bigrams1.size + n2.length - 1)
    }

    /**
     * Scores a candidate from LRCLIB against target track metadata.
     * Returns a confidence score from 0.0 to 1.0.
     */
    fun scoreCandidate(
        candidate: LrclibResponseDto,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Long,
        isUnknownArtist: Boolean = false,
    ): Double {
        val candidateTitle = candidate.trackName ?: candidate.name ?: ""
        val candidateArtist = candidate.artistName ?: ""
        val candidateDuration = candidate.duration?.toLong() ?: 0L

        // 1. Duration check
        if (targetDurationSec > 0 && candidateDuration > 0) {
            val diffSec = abs(candidateDuration - targetDurationSec)
            if (diffSec > 5) return 0.0 // Reject outright if duration differs > 5 seconds
            if (diffSec > 2) {
                // Between 2 and 5 seconds: only allow if title and artist match almost exactly
                val tNorm = normalize(targetTitle)
                val cNorm = normalize(candidateTitle)
                val aNorm = normalize(targetArtist)
                val caNorm = normalize(candidateArtist)
                if (tNorm != cNorm || (!isUnknownArtist && aNorm != caNorm)) {
                    return 0.0
                }
            }
        }

        // 2. Title similarity
        val titleParts = splitTitleParts(targetTitle)
        val titleScore = titleParts.maxOfOrNull { part ->
            diceSimilarity(part, candidateTitle)
        } ?: 0.0

        if (titleScore < 0.6) return 0.0

        // 3. Artist similarity
        val artistScore = if (isUnknownArtist || targetArtist.isBlank() || targetArtist.equals("Unknown Artist", ignoreCase = true)) {
            // When artist is unknown, candidate must have very high title match
            if (titleScore >= 0.95) 0.8 else 0.0
        } else {
            val targetArtists = splitArtists(targetArtist)
            targetArtists.maxOfOrNull { a ->
                diceSimilarity(a, candidateArtist)
            } ?: 0.0
        }

        if (!isUnknownArtist && artistScore < 0.4) return 0.0

        // 4. Combined score
        val baseScore = if (isUnknownArtist) {
            titleScore
        } else {
            (titleScore * 0.6) + (artistScore * 0.4)
        }

        // Small bonus if syncedLyrics present
        val syncedBonus = if (!candidate.syncedLyrics.isNullOrBlank()) 0.05 else 0.0

        return (baseScore + syncedBonus).coerceIn(0.0, 1.0)
    }

    /** Minimum confidence threshold for accepting a match. */
    const val CONFIDENCE_THRESHOLD = 0.75
    const val UNKNOWN_ARTIST_THRESHOLD = 0.90
}
