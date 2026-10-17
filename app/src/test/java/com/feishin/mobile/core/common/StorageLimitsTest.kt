package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P8-F: storage-limit slider policy. Pure, so no Android needed.
 *
 * The point of these tests is the invariant that used to be broken: every step a
 * cache slider offers must be a size the cache will actually accept, or the
 * thumb bounces back under the user's finger.
 */
class StorageLimitsTest {

    private val gb = StorageLimits.GB
    private val mb = StorageLimits.MB

    // ---- device max ---------------------------------------------------------

    @Test
    fun deviceMax_readsAndBounds() {
        assertEquals(64L * gb, StorageLimits.deviceMaxBytes { 64L * gb })
        // Below 1 GB or absurd values fall back rather than offering nonsense.
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { 100L * mb })
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { 0L })
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { 90_000L * gb })
    }

    @Test
    fun deviceMax_readFailureFallsBack() {
        assertEquals(StorageLimits.DEFAULT_MAX_BYTES, StorageLimits.deviceMaxBytes { error("no statfs") })
    }

    // ---- download cap steps -------------------------------------------------

    @Test
    fun downloadSteps_neverExceedTheDevice() {
        val steps = StorageLimits.steps(128L * gb)
        assertTrue(steps.isNotEmpty())
        assertEquals(128L * gb, steps.last())
        assertTrue(steps.all { it <= 128L * gb })
        // No absurd 1/2 TB step on a 128 GB device.
        assertFalse(steps.contains(1000L * gb))
        assertFalse(steps.contains(2000L * gb))
    }

    @Test
    fun downloadSteps_areAscendingAndDistinct() {
        val steps = StorageLimits.steps(512L * gb)
        assertEquals(steps.sorted(), steps)
        assertEquals(steps.distinct(), steps)
        assertEquals(512L * gb, steps.last())
    }

    // ---- cache steps --------------------------------------------------------

    @Test
    fun cacheSteps_offerOnlySizesTheCacheAccepts() {
        // 512 GB device: the rail must not reach past the cache's real ceiling.
        val steps = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 512L * gb)
        assertEquals(CacheSizePolicy.streamStepBytes(), steps)
        assertEquals(5120L * mb, steps.last())
        // Every offered size survives the store's clamp untouched, which is the
        // whole reason the old plateau happened.
        for (step in steps) {
            val asMb = (step / mb).toInt()
            assertEquals(asMb, CacheSizePolicy.normalizeStream(asMb))
        }
    }

    @Test
    fun cacheSteps_areBoundedByASmallDevice() {
        // A 4 GB device cannot hold a 5 GB cache: the top step drops to 2 GB.
        val steps = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 4L * gb)
        assertEquals(listOf(256L * mb, 512L * mb, 1L * gb, 2L * gb), steps)
        assertTrue(steps.all { it <= 4L * gb })
    }

    @Test
    fun imageSteps_areBoundedByTheSameRule() {
        val big = StorageLimits.cacheSteps(CacheSizePolicy.imageStepBytes(), 256L * gb)
        assertEquals(CacheSizePolicy.imageStepBytes(), big)
        assertEquals(1024L * mb, big.last())
        val small = StorageLimits.cacheSteps(CacheSizePolicy.imageStepBytes(), 400L * mb)
        assertEquals(listOf(100L * mb, 250L * mb), small)
    }

    @Test
    fun cacheSteps_neverEmpty() {
        // Device smaller than every supported size: fall back to the smallest
        // supported size so the rail still has something to stand on.
        val steps = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 64L * mb)
        assertEquals(listOf(256L * mb), steps)
        assertEquals(listOf<Long>(), StorageLimits.cacheSteps(emptyList(), 64L * gb))
    }

    @Test
    fun cacheSteps_indexRoundTrips() {
        val steps = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 512L * gb)
        for ((i, step) in steps.withIndex()) {
            assertEquals(i, StorageLimits.indexOf(steps, step))
            assertEquals(step, StorageLimits.nearestStep(steps, step))
        }
        // A stored value between steps snaps to the nearest one, never past the end.
        assertEquals(CacheSizePolicy.bytes(1024), StorageLimits.nearestStep(steps, 900L * mb))
    }

    // ---- clamping stays honest ----------------------------------------------

    @Test
    fun clamping_stillSnapsOntoTheOfferedSteps() {
        val stream = StorageLimits.cacheSteps(CacheSizePolicy.streamStepBytes(), 512L * gb)
        // An oversized stored value is clamped to the rail's top, not left off it.
        val clamped = CacheSizePolicy.normalizeStream(99_999)
        assertTrue(stream.contains(CacheSizePolicy.bytes(clamped)))
        val images = StorageLimits.cacheSteps(CacheSizePolicy.imageStepBytes(), 512L * gb)
        assertTrue(images.contains(CacheSizePolicy.bytes(CacheSizePolicy.normalizeImage(99_999))))
    }
}
