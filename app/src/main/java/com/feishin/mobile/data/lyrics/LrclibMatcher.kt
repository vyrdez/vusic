package com.feishin.mobile.data.lyrics

import java.text.Normalizer
import kotlin.math.abs

object LrclibMatcher {

    private val SUFFIX_BRACKETS = Regex(
        """\s*[(\[【（](?:original\s+mix|feat\.?|ft\.?|remaster(?:ed)?|remix|live|bonus(?:\s+track)?|official|audio|deluxe|version|edit|mono|stereo|radio\s+edit|acoustic(?:[\s\w]+)?|sped\s+up(?:[\s\w]+)?)[^)\]】）]*[)\]】）]""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTIST_SPLIT = Regex("""\s*(?:/|,|&|\band\b|\bx\b|、)\s*""", RegexOption.IGNORE_CASE)

    /** Normalizes a string using NFKC, lowercase, stripping punctuation and redundant whitespace. */
    fun normalize(s: String): String {
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC)
        return nfkc.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /** Cleans bracketed and parenthesized tags (e.g. "(Original Mix)", "[Remastered]", "【Official】"). */
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

    /** Generates up to 5 deduplicated title variants. */
    fun generateTitleVariants(title: String): List<String> {
        val variants = mutableListOf<String>()
        val clean = cleanTitle(title)
        if (clean.isNotBlank()) variants += clean

        if (title.contains(" - ")) {
            val parts = title.split(" - ").map { it.trim() }.filter { it.isNotBlank() }
            for (part in parts) {
                val cleanPart = cleanTitle(part)
                if (cleanPart.isNotBlank()) variants += cleanPart
            }
        }

        if (title.trim().isNotBlank()) variants += title.trim()
        val nfkc = Normalizer.normalize(clean.ifBlank { title }, Normalizer.Form.NFKC).trim()
        if (nfkc.isNotBlank()) variants += nfkc

        return variants.distinct().take(5)
    }

    /** Generates up to 4 deduplicated artist variants. */
    fun generateArtistVariants(artist: String): List<String> {
        val variants = mutableListOf<String>()
        val trimmed = artist.trim()
        if (trimmed.isNotBlank() && !trimmed.equals("Unknown Artist", ignoreCase = true) && !trimmed.equals("<unknown>", ignoreCase = true)) {
            variants += trimmed
            val parts = trimmed.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                variants += parts.first()
                variants.addAll(parts)
            }
        }
        variants += "" // Empty artist for title-only query
        return variants.distinct().take(4)
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
     * Duration-first candidate scoring according to section_c_spec.md.
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

        val diffSec = if (targetDurationSec > 0 && candidateDuration > 0) {
            abs(candidateDuration - targetDurationSec)
        } else {
            -1L
        }

        // Section C: If delta d > 5: reject (0.0)
        if (diffSec > 5) return 0.0

        // Title similarity across variants
        val titleVariants = generateTitleVariants(targetTitle)
        val titleScore = titleVariants.maxOfOrNull { diceSimilarity(it, candidateTitle) } ?: 0.0

        val artistVariants = generateArtistVariants(targetArtist).filter { it.isNotBlank() }
        val artistScore = if (artistVariants.isEmpty() || isUnknownArtist) {
            0.0
        } else {
            artistVariants.maxOfOrNull { diceSimilarity(it, candidateArtist) } ?: 0.0
        }

        val cleanT = cleanTitle(targetTitle)
        val isShortTitle = cleanT.length < 4

        val matchAccepted: Boolean = if (diffSec in 0..3) {
            if (isShortTitle) {
                titleScore >= 0.65 && (artistScore >= 0.40 || diffSec <= 1)
            } else {
                titleScore >= 0.65
            }
        } else if (diffSec in 4..5) {
            // Tight tolerance for 4..5s
            titleScore >= 0.85 && (isUnknownArtist || artistScore >= 0.60)
        } else {
            // Duration unknown
            if (isUnknownArtist) titleScore >= 0.90 else (titleScore >= 0.75 && artistScore >= 0.50)
        }

        if (!matchAccepted) return 0.0

        val baseScore = if (isUnknownArtist || artistVariants.isEmpty()) {
            titleScore * 0.85
        } else {
            (titleScore * 0.6) + (artistScore * 0.4)
        }

        // Synced bonus: +0.05 for line-synced, +0.10 for word-synced
        val syncedBonus = if (!candidate.syncedLyrics.isNullOrBlank()) {
            if (candidate.syncedLyrics.contains("<") || candidate.syncedLyrics.contains("(")) 0.10 else 0.05
        } else {
            0.0
        }

        return (baseScore + syncedBonus).coerceIn(0.0, 1.0)
    }

    const val CONFIDENCE_THRESHOLD = 0.65
    const val UNKNOWN_ARTIST_THRESHOLD = 0.80
}