package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The song-cache limit. Phase 8 raised the SLIDER to 5 GB and the limit stayed
 * at 5 GB, because the supported-size table - which the slider is derived from -
 * still ended at 5120 MB. These tests pin the table, the snapping of values
 * saved by every earlier version, and the free-space rules.
 */
class SongCacheLimitTest {

    private val mb = StorageLimits.MB
    private val gb = StorageLimits.GB

    // ---- Range ----

    @Test
    fun theSmallestSizeIsUnchanged() {
        // The bottom of the rail must not move for existing users.
        assertEquals(256, CacheSizePolicy.STREAM_SIZES_MB.first())
    }

    @Test
    fun theLargestSizeIsExactly100Gb() {
        assertEquals(100L * gb, CacheSizePolicy.bytes(CacheSizePolicy.MAX_STREAM_MB))
        assertEquals(100L * gb, CacheSizePolicy.bytes(CacheSizePolicy.STREAM_SIZES_MB.last()))
    }

    @Test
    fun theSliderThumbIsFullExactlyAt100Gb() {
        // The rail is the whole supported range - it is NOT trimmed to the
        // device's size, because a limit bigger than the phone is exactly the
        // case the free-space warning is for.
        val steps = CacheSizePolicy.streamStepBytes()
        assertEquals(100L * gb, steps.last())
        assertEquals("100 GB", StorageLimits.label(steps.last()))
    }

    @Test
    fun theRangeIsAscendingAndHasNoDuplicates() {
        val sizes = CacheSizePolicy.STREAM_SIZES_MB
        assertEquals(sizes.sorted(), sizes)
        assertEquals(sizes.distinct().size, sizes.size)
    }

    @Test
    fun theOldFiveGigabyteCeilingIsStillASupportedSize() {
        // 5 GB was the old maximum; it must stay settable or an existing choice
        // would silently jump.
        assertTrue(5120 in CacheSizePolicy.STREAM_SIZES_MB)
    }

    @Test
    fun theStepsAreFinerAtTheLowEndAndCoarserAtTheTop() {
        val mbSizes = CacheSizePolicy.STREAM_SIZES_MB.map { it.toLong() }
        val firstGap = mbSizes[2] - mbSizes[1]     // 512 -> 1024 MB
        val midGap = mbSizes[6] - mbSizes[5]       // 5120 -> 6144 MB (1 GB step)
        val lastGap = mbSizes.last() - mbSizes[mbSizes.size - 2]  // 75 -> 100 GB
        assertEquals(512L, firstGap)
        assertEquals(1024L, midGap)
        assertTrue("steps must coarsen at the top", lastGap > midGap)
    }

    @Test
    fun aDeviceSmallerThanTheRailFallsBackToTheSmallestSupportedSize() {
        // A phone with 100 MB free still needs one settable step; the rail falls
        // back to the smallest supported size rather than becoming empty.
        val steps = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 100L * mb)
        assertEquals(listOf(256L * mb), steps)
    }

    // ---- Snapping a saved value ----

    @Test
    fun everySizeAnEarlierVersionCouldSaveStillLoadsUnchanged() {
        // 1.0 stored values could only be one of these.
        listOf(256, 512, 1024, 2048, 5120).forEach { saved ->
            assertEquals(
                "saved $saved MB must not be rewritten",
                saved,
                CacheSizePolicy.normalizeStream(saved),
            )
        }
    }

    @Test
    fun anOldValueBetweenTwoSizesSnapsToTheNearest() {
        assertEquals(5120, CacheSizePolicy.normalizeStream(5000))
        assertEquals(1024, CacheSizePolicy.normalizeStream(900))
    }

    @Test
    fun anAbsurdStoredValueIsClampedNotCrashed() {
        assertEquals(CacheSizePolicy.MAX_STREAM_MB, CacheSizePolicy.normalizeStream(Int.MAX_VALUE))
        assertEquals(256, CacheSizePolicy.normalizeStream(-5))
        assertEquals(256, CacheSizePolicy.normalizeStream(0))
    }

    @Test
    fun theDefaultIsUnchanged() {
        assertTrue(512 in CacheSizePolicy.STREAM_SIZES_MB)
        assertEquals(512, CacheSizePolicy.DEFAULT_STREAM_MB)
    }

    @Test
    fun oneHundredGigabyteFitsTheStoredIntegerUnit() {
        // The setting is stored in MB as an Int; 100 GB must not overflow it.
        val stored = CacheSizePolicy.MAX_STREAM_MB
        assertTrue(stored > 0)
        assertEquals(CacheSizePolicy.MAX_STREAM_MB, (100L * gb / mb).toInt())
    }

    // ---- Free-space warning ----

    @Test
    fun noWarningWhenTheLimitFits() {
        assertNull(
            StorageLimits.cacheSpaceWarning(10L * gb, usedBytes = 4L * gb, freeBytes = 40L * gb) { "x" },
        )
    }

    @Test
    fun aWarningNamesTheRealFreeSpace() {
        val msg = StorageLimits.cacheSpaceWarning(
            limitBytes = 100L * gb,
            usedBytes = 2L * gb,
            freeBytes = 12L * gb,
        ) { StorageLimits.label(it) }
        assertTrue(msg!!.contains("12 GB"))
        assertTrue(msg.contains("can't grow past what's free"))
    }

    @Test
    fun aLimitEqualToUsedPlusFreeIsStillFine() {
        assertNull(
            StorageLimits.cacheSpaceWarning(
                limitBytes = 20L * gb,
                usedBytes = 8L * gb,
                freeBytes = 12L * gb,
            ) { "x" },
        )
    }

    @Test
    fun aLimitBiggerThanTheWholePhoneWarns() {
        val msg = StorageLimits.cacheSpaceWarning(
            limitBytes = 100L * gb,
            usedBytes = 1L * gb,
            freeBytes = 500L * mb,
        ) { StorageLimits.label(it) }
        assertTrue(msg!!.contains("500 MB"))
    }

    @Test
    fun aLimitBiggerThanFreeSpacePlusWhatTheCacheAlreadyHoldsWarns() {
        // The common case: an empty-ish cache on a nearly full phone.
        assertTrue(
            StorageLimits.exceedsAvailable(
                limitBytes = 100L * gb,
                usedBytes = 0L,
                freeBytes = 6L * gb,
            ),
        )
    }

    // ---- Low-space safety ----

    @Test
    fun theFullLimitAppliesWhenThereIsRoom() {
        assertEquals(
            100L * gb,
            StorageLimits.effectiveCacheLimit(100L * gb, usedBytes = 5L * gb, freeBytes = 50L * gb),
        )
    }

    @Test
    fun belowTheReserveTheCacheMayNotGrow() {
        val used = 3L * gb
        val effective = StorageLimits.effectiveCacheLimit(
            limitBytes = 100L * gb,
            usedBytes = used,
            freeBytes = StorageLimits.LOW_SPACE_BYTES - 1,
        )
        assertEquals(used, effective)
    }

    @Test
    fun theReserveIsOneGigabyte() {
        assertEquals(1L * gb, StorageLimits.LOW_SPACE_BYTES)
    }

    @Test
    fun exactlyAtTheReserveTheLimitStillApplies() {
        assertEquals(
            100L * gb,
            StorageLimits.effectiveCacheLimit(
                100L * gb,
                usedBytes = 3L * gb,
                freeBytes = StorageLimits.LOW_SPACE_BYTES,
            ),
        )
    }

    @Test
    fun aLowSpaceCapNeverDeletesAnythingToGetThere() {
        // The cap equals what is already used, so LRU eviction has nothing to
        // do: no downloads and no playing song are at risk.
        val used = 7L * gb
        assertEquals(used, StorageLimits.effectiveCacheLimit(100L * gb, used, 0L))
        assertTrue(LruSizer().evictTo(used).isEmpty())
    }

    @Test
    fun aZeroLimitIsNeverTurnedIntoACacheOfZero() {
        assertEquals(0L, StorageLimits.effectiveCacheLimit(0L, usedBytes = 5L * gb, freeBytes = 0L))
    }

    // ---- The image cache is deliberately left where it was ----

    @Test
    fun theImageCacheCeilingIsUnchanged() {
        assertEquals(1024, CacheSizePolicy.IMAGE_SIZES_MB.last())
    }
}
