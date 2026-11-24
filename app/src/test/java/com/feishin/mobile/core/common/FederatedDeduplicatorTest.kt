package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FederatedDeduplicatorTest {

    private fun track(
        id: String,
        title: String = "Paranoid Android",
        album: String = "OK Computer",
        artist: String = "Radiohead",
        durationSec: Int = 383,
        suffix: String? = "mp3",
        bitRate: Int? = 320,
        sourceProfileId: String? = GlobalId.profileIdOrNull(id),
    ) = Track(
        id = id,
        title = title,
        album = album,
        albumId = null,
        artist = artist,
        artistId = null,
        coverArt = null,
        durationSec = durationSec,
        trackNo = 2,
        year = 1997,
        genre = "Alternative",
        suffix = suffix,
        starred = false,
        bitRate = bitRate,
        sourceProfileId = sourceProfileId,
    )

    private fun album(
        id: String,
        name: String = "OK Computer",
        artist: String = "Radiohead",
        year: Int? = 1997,
        songCount: Int = 12,
        sourceProfileId: String? = GlobalId.profileIdOrNull(id),
    ) = Album(
        id = id,
        name = name,
        artist = artist,
        artistId = null,
        coverArt = null,
        songCount = songCount,
        durationSec = 3200,
        playCount = 0,
        year = year,
        genre = "Alternative",
        starred = false,
        sourceProfileId = sourceProfileId,
    )

    @Test
    fun losslessBeatsLossyRegardlessOfPriorityOrMissingBitrate() {
        val lossyHighPriority = track(id = "p1:t1", suffix = "mp3", bitRate = 320)
        val losslessLowPriority = track(id = "p2:t9", suffix = "flac", bitRate = null)
        val priorities = mapOf("p1" to 0, "p2" to 5)

        val result = FederatedDeduplicator.deduplicateTracks(
            listOf(lossyHighPriority, losslessLowPriority),
            priorities,
        )

        assertEquals(1, result.size)
        assertEquals("p2:t9", result.single().id)
        assertFalse(FederatedDeduplicator.isSimilarQuality(lossyHighPriority, losslessLowPriority))
        assertTrue(FederatedDeduplicator.qualityScore(losslessLowPriority) > FederatedDeduplicator.qualityScore(lossyHighPriority))
    }

    @Test
    fun losslessVsLosslessUsesPriorityThenProfileIdThenItemId() {
        val flacP2 = track(id = "p2:t1", suffix = "flac", bitRate = 1100)
        val alacP1 = track(id = "p1:t9", suffix = "alac", bitRate = 900)
        val priorities = mapOf("p1" to 0, "p2" to 1)

        assertTrue(FederatedDeduplicator.isSimilarQuality(flacP2, alacP1))
        val winner = FederatedDeduplicator.deduplicateTracks(listOf(flacP2, alacP1), priorities).single()
        assertEquals("p1:t9", winner.id)

        // Same priority -> lexicographically smaller profileId wins
        val samePrio = mapOf("p1" to 0, "p2" to 0)
        assertEquals("p1:t9", FederatedDeduplicator.deduplicateTracks(listOf(flacP2, alacP1), samePrio).single().id)

        // Same profileId -> lexicographically smaller itemId wins
        val a = track(id = "p1:item-b", suffix = "wav", bitRate = 1411)
        val b = track(id = "p1:item-a", suffix = "flac", bitRate = 950)
        assertEquals("p1:item-a", FederatedDeduplicator.deduplicateTracks(listOf(a, b), samePrio).single().id)
    }

    @Test
    fun lossyWithin16KbpsTiesToPriorityWhileGreaterThan16KbpsPicksHigherBitrate() {
        val priorities = mapOf("p1" to 0, "p2" to 1)

        // 320 vs 304 -> diff == 16 <= 16 -> similar -> lower priority (p1) wins
        val p1Near = track(id = "p1:s1", suffix = "mp3", bitRate = 304)
        val p2Top = track(id = "p2:s2", suffix = "mp3", bitRate = 320)
        assertTrue(FederatedDeduplicator.isSimilarQuality(p1Near, p2Top))
        assertEquals("p1:s1", FederatedDeduplicator.deduplicateTracks(listOf(p2Top, p1Near), priorities).single().id)

        // 320 vs 303 -> diff == 17 > 16 -> strictly higher bitrate (p2) wins despite lower priority
        val p1Lower = track(id = "p1:s1", suffix = "mp3", bitRate = 303)
        assertFalse(FederatedDeduplicator.isSimilarQuality(p1Lower, p2Top))
        assertEquals("p2:s2", FederatedDeduplicator.deduplicateTracks(listOf(p1Lower, p2Top), priorities).single().id)

        // Known bitrate beats unknown bitrate
        val p1Unknown = track(id = "p1:s1", suffix = "mp3", bitRate = null)
        val p2Known = track(id = "p2:s2", suffix = "mp3", bitRate = 128)
        assertEquals("p2:s2", FederatedDeduplicator.deduplicateTracks(listOf(p1Unknown, p2Known), priorities).single().id)
    }

    @Test
    fun normalizesCjkNfkcUnicodeEditionNoiseAndDurationBuckets() {
        val fullWidthAndEdition = track(
            id = "p1:t1",
            artist = "Ｕｔａｄａ　Ｈｉｋａｒｕ",
            album = "Ｆｉｒｓｔ　Ｌｏｖｅ（Remastered 2014）",
            title = "東京　Ｎｉｇｈｔ [Deluxe Edition] (feat. Guest)",
            durationSec = 241,
            bitRate = 320,
        )
        val normalizedPlain = track(
            id = "p2:t2",
            artist = "Utada Hikaru",
            album = "First Love",
            title = "東京 Night",
            durationSec = 242, // (241 + 1)/2 == 121 == (242 + 1)/2
            bitRate = 192,
        )

        assertEquals(
            FederatedDeduplicator.trackKey(fullWidthAndEdition),
            FederatedDeduplicator.trackKey(normalizedPlain),
        )
        assertEquals("東京 night", FederatedDeduplicator.normalizeText(fullWidthAndEdition.title))

        val deduped = FederatedDeduplicator.deduplicateTracks(
            listOf(normalizedPlain, fullWidthAndEdition),
            mapOf("p1" to 1, "p2" to 0),
        )
        assertEquals(1, deduped.size)
        assertEquals("p1:t1", deduped.single().id)
    }

    @Test
    fun deduplicateAlbumsPrefersCompletenessThenPriorityDeterministically() {
        val a1 = album(id = "p2:al-2", name = "OK Computer (Remastered)", songCount = 12)
        val a2 = album(id = "p1:al-1", name = "OK Computer", songCount = 12)
        val aIncomplete = album(id = "p0:al-0", name = "OK Computer", songCount = 8)
        val priorities = mapOf("p0" to 0, "p1" to 1, "p2" to 2)

        assertEquals(FederatedDeduplicator.albumKey(a1), FederatedDeduplicator.albumKey(a2))

        val result = FederatedDeduplicator.deduplicateAlbums(
            listOf(aIncomplete, a1, a2),
            priorities,
        )
        assertEquals(1, result.size)
        // Complete 12-track albums beat incomplete 8-track album; p1 beats p2 by priority
        assertEquals("p1:al-1", result.single().id)
    }
}
