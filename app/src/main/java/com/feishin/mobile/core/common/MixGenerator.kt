package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import kotlin.random.Random

/**
 * Deterministic builder for the Home "Mixed for you" shelf.
 *
 * Always returns exactly 5 distinctly-named mixes ("My Mix 1" .. "My Mix 5")
 * whenever the combined pool holds at least 5 songs, so the shelf never
 * collapses to 1-3 cards on small libraries. Each mix holds up to [batchSize]
 * tracks with no repeats inside a mix (tracks may recur across mixes when the
 * pool is smaller than 5 * [batchSize]); cover collages derive from the
 * tracks via `Mix.covers`, so no extra work is needed for art.
 *
 * Pure and deterministic for a given input + [seed]: same seed replays the
 * same shelf, a new seed (refresh, genre change) re-seeds it. No network, so
 * mixes never gate the rest of Home.
 *
 * - Mix 1: top affinity (most-played / recent-favorite artist + same genre).
 * - Mix 2: secondary artist cluster, excluding Mix 1's primary artists.
 * - Mix 3: playlist + starred favorites blend.
 * - Mix 4: mood/style/era cluster (dominant genre, else decade, else fill).
 * - Mix 5: discovery & deep cuts (unplayed/unstarred first, familiar anchor
 *   interleaved every third track).
 */
object MixGenerator {

    data class GeneratedMix(val name: String, val tracks: List<Track>)

    fun generate(
        randomSongs: List<Track>,
        starredTracks: List<Track> = emptyList(),
        playlistTracks: List<Track> = emptyList(),
        frequentAlbums: List<Album> = emptyList(),
        recentAlbums: List<Album> = emptyList(),
        batchSize: Int = 25,
        seed: Long = 0L,
        genreFilter: String? = null,
    ): List<GeneratedMix> {
        val pool = (randomSongs + starredTracks + playlistTracks).distinctBy { it.id }
        if (pool.size < 5) return emptyList()
        val want = batchSize.coerceIn(1, 50)

        val starredIds = starredTracks.map { it.id }.toSet()
        val playlistIds = playlistTracks.map { it.id }.toSet()
        val recentAlbumIds = recentAlbums.map { it.id }.toSet()
        val recentAlbumNames = recentAlbums.map { it.name.trim().lowercase() }
            .filter { it.isNotBlank() }.toSet()

        val playsByArtist = HashMap<String, Long>()
        for (a in frequentAlbums) {
            val k = artistKey(a.artistId, a.artist)
            playsByArtist[k] = (playsByArtist[k] ?: 0L) + a.playCount
        }
        val countInPool = HashMap<String, Int>()
        for (t in pool) {
            val k = artistKey(t.artistId, t.artist)
            countInPool[k] = (countInPool[k] ?: 0) + 1
        }
        val rankedArtists = countInPool.keys.sortedWith(
            compareByDescending<String> { playsByArtist[it] ?: 0L }
                .thenByDescending { countInPool[it] ?: 0 }
                .thenBy { it },
        )
        val topKey = rankedArtists.getOrNull(0)
        val secondaryKeys = rankedArtists.drop(1).take(3).toSet()

        fun score(t: Track): Long {
            var s = (playsByArtist[artistKey(t.artistId, t.artist)] ?: 0L) * 1_000L
            if (t.id in starredIds) s += 500L
            if (t.id in playlistIds) s += 250L
            if (t.albumId in recentAlbumIds || t.album.trim().lowercase() in recentAlbumNames) s += 100L
            return s
        }
        val byScore = compareByDescending<Track> { score(it) }.thenBy { it.id }

        val genre = genreFilter?.trim()?.takeIf { it.isNotEmpty() && !it.equals("All", ignoreCase = true) }
        fun preferGenre(list: List<Track>): List<Track> {
            if (genre == null) return list
            val (match, rest) = list.partition { it.genre.equals(genre, ignoreCase = true) }
            return match + rest
        }

        // Mix 1: top-affinity cluster (own tracks, then same-genre relatives).
        val mix1Base = if (topKey != null) {
            val own = pool.filter { artistKey(it.artistId, it.artist) == topKey }.sortedWith(byScore)
            val topGenre = dominantGenre(own)
            val related = if (topGenre != null) {
                pool.filter { artistKey(it.artistId, it.artist) != topKey && it.genre.equals(topGenre, ignoreCase = true) }
                    .sortedWith(byScore)
            } else emptyList()
            own + related
        } else {
            pool.sortedWith(byScore)
        }

        // Mix 2: secondary artists only, so it never replays Mix 1's cluster.
        val mix2Base = pool.filter {
            val k = artistKey(it.artistId, it.artist)
            k != topKey && (secondaryKeys.isEmpty() || k in secondaryKeys)
        }.sortedWith(byScore)

        // Mix 3: favorites blend, seeded shuffle so refreshes vary the order.
        val mix3Base = (starredTracks + playlistTracks).distinctBy { it.id }
            .shuffled(Random(seed + 3_003L))

        // Mix 4: era/style cluster — dominant genre other than Mix 1's, else
        // the biggest decade group, else the seeded fill covers it.
        val mix1Genre = dominantGenre(mix1Base)
        val genreGroups = pool.mapNotNull { t ->
            t.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { it.lowercase() to t }
        }.groupBy({ it.first }, { it.second })
        val decadeGroups = pool.filter { (it.year ?: 0) > 0 }.groupBy { (it.year!! / 10) * 10 }
        val orderedGenres = genreGroups.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<Track>>> { it.value.size }.thenBy { it.key },
        )
        val mix4Genre = orderedGenres.firstOrNull { it.key != mix1Genre }?.key
            ?: orderedGenres.firstOrNull()?.key
        val genreCandidate = mix4Genre?.let { genreGroups[it].orEmpty() } ?: emptyList()
        val decadeCandidate = decadeGroups.entries.sortedWith(
            compareByDescending<Map.Entry<Int, List<Track>>> { it.value.size }.thenBy { it.key },
        ).firstOrNull()?.value.orEmpty()
        val mix4Base = if (genreCandidate.size >= decadeCandidate.size) {
            genreCandidate.sortedWith(byScore)
        } else {
            decadeCandidate.sortedWith(byScore)
        }

        // Mix 5: deep cuts first (unstarred, unlisted, not recent), with a
        // familiar anchor interleaved every third track.
        val deepCuts = pool.filter { t ->
            t.id !in starredIds && t.id !in playlistIds &&
                t.albumId !in recentAlbumIds && t.album.trim().lowercase() !in recentAlbumNames
        }.sortedWith(
            compareBy<Track> { playsByArtist[artistKey(it.artistId, it.artist)] ?: 0L }.thenBy { it.id },
        )
        val familiar = if (topKey != null) {
            pool.filter { artistKey(it.artistId, it.artist) == topKey }.sortedWith(byScore)
        } else emptyList()
        val seen5 = HashSet<String>()
        val mix5Base = ArrayList<Track>()
        for ((i, t) in deepCuts.withIndex()) {
            if (seen5.add(t.id)) mix5Base += t
            if (i % 2 == 1 && familiar.isNotEmpty()) {
                val f = familiar[(i / 2) % familiar.size]
                if (seen5.add(f.id)) mix5Base += f
            }
        }
        if (mix5Base.isEmpty()) mix5Base += familiar.ifEmpty { pool.sortedWith(byScore) }

        fun fill(base: List<Track>, mixIndex: Int): List<Track> {
            val out = ArrayList<Track>(minOf(want, pool.size))
            val seen = HashSet<String>()
            for (t in preferGenre(base)) {
                if (out.size >= want) break
                if (seen.add(t.id)) out += t
            }
            if (out.size < want) {
                for (t in preferGenre(pool.shuffled(Random(seed + mixIndex * 977L + 13L)))) {
                    if (out.size >= want) break
                    if (seen.add(t.id)) out += t
                }
            }
            return out
        }

        val bases = listOf(mix1Base, mix2Base, mix3Base, mix4Base, mix5Base)
        return List(5) { i -> GeneratedMix("My Mix ${i + 1}", fill(bases[i], i + 1)) }
    }

    private fun artistKey(artistId: String?, artist: String): String =
        (artistId?.trim() ?: artist.trim().lowercase()).ifBlank { artist.trim().lowercase() }

    private fun dominantGenre(tracks: List<Track>): String? =
        tracks.mapNotNull { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }
            .groupingBy { it.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
}
