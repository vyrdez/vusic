package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part A logic: storage-limit steps, LRU eviction, avatar fallbacks. */
class M6PartATest {

    // ---- A4: storage limit steps -------------------------------------------

    @Test
    fun steps_startAt256MbAndAlwaysEndAtExactMax() {
        val max = 128L * StorageLimits.GB
        val steps = StorageLimits.steps(max)
        assertEquals(256L * StorageLimits.MB, steps.first())
        assertEquals(max, steps.last())
        // Ascending and unique.
        assertEquals(steps.sorted(), steps)
        assertEquals(steps.distinct().size, steps.size)
    }

    @Test
    fun steps_includeNominalSizesBelowMax() {
        val steps = StorageLimits.steps(100L * StorageLimits.GB)
        assertTrue(steps.contains(1L * StorageLimits.GB))
        assertTrue(steps.contains(10L * StorageLimits.GB))
        assertTrue(steps.contains(50L * StorageLimits.GB))
        assertEquals(100L * StorageLimits.GB, steps.last())
    }

    @Test
    fun steps_onTinyDeviceStillProduceUsableRail() {
        // A device reporting less than the first nominal step: the rail is still
        // non-empty and capped at the real maximum.
        val max = 200L * StorageLimits.MB
        val steps = StorageLimits.steps(max)
        assertTrue(steps.isNotEmpty())
        assertEquals(max, steps.last())
    }

    @Test
    fun deviceMax_fallsBackWhenReadFails() {
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { 0L })
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { throw IllegalStateException("no stat") })
    }

    @Test
    fun deviceMax_usesReportedTotalWhenPlausible() {
        val reported = 512L * StorageLimits.GB
        assertEquals(reported, StorageLimits.deviceMaxBytes { reported })
    }

    @Test
    fun indexOf_snapsStoredValueOntoRail() {
        val steps = StorageLimits.steps(128L * StorageLimits.GB)
        assertEquals(0, StorageLimits.indexOf(steps, 0L))
        val i10 = StorageLimits.indexOf(steps, 10L * StorageLimits.GB)
        assertEquals(10L * StorageLimits.GB, steps[i10])
        // A value between steps snaps to the closest one.
        val between = StorageLimits.indexOf(steps, 11L * StorageLimits.GB)
        assertTrue(steps[between] == 10L * StorageLimits.GB || steps[between] == 20L * StorageLimits.GB)
    }

    @Test
    fun label_isHumanReadable() {
        assertEquals("256 MB", StorageLimits.label(256L * StorageLimits.MB))
        assertEquals("1.0 GB", StorageLimits.label(1L * StorageLimits.GB))
        assertEquals("10 GB", StorageLimits.label(10L * StorageLimits.GB))
        assertEquals("512 MB", StorageLimits.label(512L * StorageLimits.MB))
    }

    @Test
    fun exceedsAvailable_onlyWhenBiggerThanDevice() {
        val used = 4L * StorageLimits.GB
        val free = 60L * StorageLimits.GB
        assertTrue(StorageLimits.exceedsAvailable(100L * StorageLimits.GB, used, free))
        assertFalse(StorageLimits.exceedsAvailable(50L * StorageLimits.GB, used, free))
        assertFalse(StorageLimits.exceedsAvailable(64L * StorageLimits.GB, used, free))
    }

    @Test
    fun cachingAllowed_pausesBelowOneGb() {
        assertFalse(StorageLimits.cachingAllowed(StorageLimits.LOW_SPACE_BYTES - 1))
        assertTrue(StorageLimits.cachingAllowed(StorageLimits.LOW_SPACE_BYTES))
    }

    // ---- A4: live LRU eviction ---------------------------------------------

    @Test
    fun lruSizer_evictsLeastRecentlyTouchedFirst() {
        val s = LruSizer()
        s.put("a", 100, touch = 1)
        s.put("b", 100, touch = 2)
        s.put("c", 100, touch = 3)
        val evicted = s.evictTo(250)
        assertEquals(listOf("a"), evicted)
        assertEquals(200, s.size)
    }

    @Test
    fun lruSizer_shrinkingToZeroBudgetEvictsEverything() {
        val s = LruSizer()
        s.put("a", 10, 1)
        s.put("b", 10, 2)
        // maxBytes <= 0 means "no limit configured yet" -> no eviction.
        assertTrue(s.evictTo(0).isEmpty())
        assertEquals(listOf("a", "b"), s.evictTo(1))
        assertEquals(0, s.size)
        assertEquals(0, s.count)
    }

    @Test
    fun lruSizer_touchRefreshesRecency() {
        val s = LruSizer()
        s.put("a", 100, 1)
        s.put("b", 100, 2)
        // a is touched again -> b becomes the least recently used.
        s.put("a", 100, 3)
        assertEquals(listOf("b"), s.evictTo(150))
    }

    @Test
    fun lruSizer_removeKeepsAccountingConsistent() {
        val s = LruSizer()
        s.put("a", 100, 1)
        s.put("b", 100, 2)
        s.remove("a")
        assertEquals(100, s.size)
        assertFalse(s.contains("a"))
        assertTrue(s.evictTo(100).isEmpty())
    }

    // ---- A7: avatar fallback chain -----------------------------------------

    @Test
    fun avatar_skipsProviderPlaceholder() {
        // Last.fm's default star counts as missing, so cover art wins.
        val star = "https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png"
        assertEquals("cover", AvatarFallback.pick(star, "cover", "album"))
        assertTrue(AvatarFallback.isPlaceholderUrl(star))
    }

    @Test
    fun avatar_prefersInfoThenCoverThenAlbum() {
        assertEquals("info", AvatarFallback.pick("info", "cover", "album"))
        assertEquals("cover", AvatarFallback.pick(null, "cover", "album"))
        assertEquals("album", AvatarFallback.pick("", null, "album"))
        assertEquals(null, AvatarFallback.pick(null, null, null))
        assertEquals(null, AvatarFallback.pick("", "  ", null))
    }

    @Test
    fun avatar_treatsCommonPlaceholderNamesAsMissing() {
        assertTrue(AvatarFallback.isPlaceholderUrl("https://x/default_avatar.png"))
        assertTrue(AvatarFallback.isPlaceholderUrl("https://x/noimage.jpg"))
        assertFalse(AvatarFallback.isPlaceholderUrl("https://x/real-artist.jpg"))
    }
}
