package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic cross-backend deduplication for federated Track and Album lists.
 *
 * Keeps the highest-quality copy (lossless tier > lossy bitrate > unknown bitrate),
 * treating copies within [BITRATE_SIMILARITY_KBPS] kbps (or both lossless / both unknown)
 * as equivalent and breaking ties deterministically by:
 * 1. Lower `ServerProfile.priority`
 * 2. Lexicographically smaller `profileId`
 * 3. Lexicographically smaller `itemId`
 */
object FederatedDeduplicator {
    const val BITRATE_SIMILARITY_KBPS = 16

    val LOSSLESS_SUFFIXES: Set<String> = setOf(
        "flac",
        "alac",
        "wav",
        "aiff",
        "dsd",
        "dsf",
        "wv",
        "ape",
    )

    private val BRACKETED_NOISE = Regex(
        """\s*[\(\[](?:[^\)\]]*\b)?(?:remaster(?:ed)?|deluxe|explicit|clean|bonus\s+track|feat\.?\b)[^\)\]]*[\)\]]""",
    )
    private val TRAILING_FEAT = Regex("""\s+\bfeat\.?\s+.*$""")
    private val BARE_EDITION_NOISE = Regex(
        """(?:\s*[-–—]\s*|\s+)\b(?:remaster(?:ed)?|deluxe(?:\s+edition)?|bonus\s+track)\b(?:\s+\d{4})?""",
    )
    private val TRAILING_ADVISORY = Regex("""\s*[-–—]\s*\b(?:explicit|clean)\b$""")
    private val NON_ALNUM_UNICODE = Regex("""[^\p{L}\p{N} ]""")
    private val MULTI_SPACE = Regex("""\s+""")

    fun normalizeText(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(BRACKETED_NOISE, "")
            .replace(TRAILING_FEAT, "")
            .replace(BARE_EDITION_NOISE, "")
            .replace(TRAILING_ADVISORY, "")
            .replace(NON_ALNUM_UNICODE, " ")
            .replace(MULTI_SPACE, " ")
            .trim()

    fun trackKey(t: Track): String {
        val durBucket = (t.durationSec + 1) / 2
        return "${normalizeText(t.artist)}|${normalizeText(t.album)}|${normalizeText(t.title)}|$durBucket"
    }

    fun albumKey(a: Album): String =
        "${normalizeText(a.artist)}|${normalizeText(a.name)}|${a.year ?: 0}"

    fun isLossless(suffix: String?): Boolean =
        suffix?.trim()?.lowercase(Locale.ROOT) in LOSSLESS_SUFFIXES

    fun qualityScore(track: Track): Int {
        val br = track.bitRate?.takeIf { it > 0 }
        return when {
            isLossless(track.suffix) -> 10_000 + (br ?: 1_000)
            br != null -> br
            else -> 0
        }
    }

    fun isSimilarQuality(a: Track, b: Track): Boolean {
        val losslessA = isLossless(a.suffix)
        val losslessB = isLossless(b.suffix)
        if (losslessA && losslessB) return true
        if (losslessA != losslessB) return false
        val brA = a.bitRate?.takeIf { it > 0 } ?: 0
        val brB = b.bitRate?.takeIf { it > 0 } ?: 0
        if (brA == 0 && brB == 0) return true
        if (brA == 0 || brB == 0) return false
        return abs(brA - brB) <= BITRATE_SIMILARITY_KBPS
    }

    fun deduplicateTracks(
        tracks: List<Track>,
        profilePriorities: Map<String, Int> = emptyMap(),
    ): List<Track> {
        if (tracks.size <= 1) return tracks
        val groups = LinkedHashMap<String, MutableList<Track>>(tracks.size)
        for (track in tracks) {
            groups.getOrPut(trackKey(track)) { mutableListOf() }.add(track)
        }
        return groups.values.map { candidates ->
            if (candidates.size == 1) candidates[0]
            else pickBestTrack(candidates, profilePriorities)
        }
    }

    fun deduplicateAlbums(
        albums: List<Album>,
        profilePriorities: Map<String, Int> = emptyMap(),
        albumQualityScores: Map<String, Int> = emptyMap(),
    ): List<Album> {
        if (albums.size <= 1) return albums
        val groups = LinkedHashMap<String, MutableList<Album>>(albums.size)
        for (album in albums) {
            groups.getOrPut(albumKey(album)) { mutableListOf() }.add(album)
        }
        return groups.values.map { candidates ->
            if (candidates.size == 1) candidates[0]
            else pickBestAlbum(candidates, profilePriorities, albumQualityScores)
        }
    }

    private fun pickBestTrack(
        candidates: List<Track>,
        profilePriorities: Map<String, Int>,
    ): Track {
        val lossless = candidates.filter { isLossless(it.suffix) }
        val topTier: List<Track> = when {
            lossless.isNotEmpty() -> lossless
            else -> {
                val knownLossy = candidates.filter { (it.bitRate ?: 0) > 0 }
                if (knownLossy.isNotEmpty()) {
                    val maxBr = knownLossy.maxOf { it.bitRate ?: 0 }
                    knownLossy.filter { maxBr - (it.bitRate ?: 0) <= BITRATE_SIMILARITY_KBPS }
                } else {
                    candidates
                }
            }
        }
        return topTier.minWith(trackTieBreakComparator(profilePriorities))
    }

    private fun pickBestAlbum(
        candidates: List<Album>,
        profilePriorities: Map<String, Int>,
        albumQualityScores: Map<String, Int>,
    ): Album {
        val scored = candidates.filter { (albumQualityScores[it.id] ?: 0) > 0 }
        val pool: List<Album> = if (scored.isNotEmpty()) {
            val maxScore = scored.maxOf { albumQualityScores[it.id] ?: 0 }
            val threshold = if (maxScore >= 10_000) Int.MAX_VALUE else BITRATE_SIMILARITY_KBPS
            scored.filter { candidate ->
                val s = albumQualityScores[candidate.id] ?: 0
                if (maxScore >= 10_000) s >= 10_000 else maxScore - s <= threshold
            }
        } else {
            val maxSongs = candidates.maxOf { it.songCount }
            candidates.filter { it.songCount == maxSongs }
        }
        return pool.minWith(albumTieBreakComparator(profilePriorities))
    }

    private fun trackTieBreakComparator(profilePriorities: Map<String, Int>): Comparator<Track> =
        compareBy<Track>(
            { priorityOf(it.sourceProfileId ?: GlobalId.profileIdOrNull(it.id), profilePriorities) },
            { it.sourceProfileId ?: GlobalId.profileIdOrNull(it.id) ?: "" },
            { GlobalId.rawId(it.id) },
        )

    private fun albumTieBreakComparator(profilePriorities: Map<String, Int>): Comparator<Album> =
        compareBy<Album>(
            { priorityOf(it.sourceProfileId ?: GlobalId.profileIdOrNull(it.id), profilePriorities) },
            { it.sourceProfileId ?: GlobalId.profileIdOrNull(it.id) ?: "" },
            { GlobalId.rawId(it.id) },
        )

    private fun priorityOf(profileId: String?, profilePriorities: Map<String, Int>): Int =
        if (profileId != null) profilePriorities[profileId] ?: Int.MAX_VALUE else Int.MAX_VALUE
}
