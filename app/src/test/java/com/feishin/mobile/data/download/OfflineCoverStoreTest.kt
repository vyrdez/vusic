package com.feishin.mobile.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class OfflineCoverStoreTest {
    data class FakeDownload(val songId: String, val coverId: String?, val status: String)
    class FakeCoverStore {
        val completed = mutableListOf<FakeDownload>()
        val diskCovers = mutableMapOf<String, String>()
        var networkFetches = 0

        fun downloadSong(song: FakeDownload, coverOk: Boolean = true): Boolean {
            completed.add(song.copy(status = "COMPLETED"))
            song.coverId?.let { cid ->
                if (!diskCovers.containsKey(cid) && coverOk) {
                    networkFetches++
                    diskCovers[cid] = "/data/covers/$cid.jpg"
                }
            }
            return true
        }

        fun removeDownload(songId: String) {
            val removed = completed.find { it.songId == songId } ?: return
            completed.remove(removed)
            val remaining = completed.count { it.coverId == removed.coverId && it.status == "COMPLETED" }
            if (remaining == 0 && removed.coverId != null) diskCovers.remove(removed.coverId)
        }

        fun resolveImage(coverId: String, memCache: Map<String, String>, diskCache: Map<String, String>): String {
            diskCovers[coverId]?.let { return "OFFLINE_STORE:$it" }
            memCache[coverId]?.let { return "MEMORY_CACHE:$it" }
            diskCache[coverId]?.let { return "DISK_CACHE:$it" }
            networkFetches++
            return "NETWORK:$coverId"
        }

        fun missingCoversForBackfill(): List<String> =
            completed.filter { it.status == "COMPLETED" && it.coverId != null }
                .mapNotNull { it.coverId }.filter { it !in diskCovers }.distinct()
    }

    @Test
    fun deDuplication_storesCoverOnlyOnceForMultipleSongs() {
        val store = FakeCoverStore()
        store.downloadSong(FakeDownload("s1", "c1", "QUEUED"))
        store.downloadSong(FakeDownload("s2", "c1", "QUEUED"))
        assertEquals(1, store.networkFetches)
        assertEquals(1, store.diskCovers.size)
    }

    @Test
    fun referenceCounting_retainsUntilLastSongDeleted() {
        val store = FakeCoverStore()
        store.downloadSong(FakeDownload("s1", "c1", "QUEUED"))
        store.downloadSong(FakeDownload("s2", "c1", "QUEUED"))
        store.removeDownload("s1")
        assertTrue(store.diskCovers.containsKey("c1"))
        store.removeDownload("s2")
        assertFalse(store.diskCovers.containsKey("c1"))
    }

    @Test
    fun coilOfflineFallbackOrder_prefersOfflineStoreWithoutNetwork() {
        val store = FakeCoverStore().apply { diskCovers["c1"] = "/covers/c1.jpg" }
        val netBefore = store.networkFetches
        val res = store.resolveImage("c1", mapOf("c1" to "mem"), mapOf("c1" to "disk"))
        assertEquals("OFFLINE_STORE:/covers/c1.jpg", res)
        assertEquals(netBefore, store.networkFetches)
    }

    @Test
    fun backfill_picksOnlyCompletedSongsWithMissingCovers() {
        val store = FakeCoverStore().apply {
            completed.addAll(listOf(FakeDownload("s1", "c1", "COMPLETED"), FakeDownload("s2", "c2", "COMPLETED"), FakeDownload("s3", "c1", "COMPLETED"), FakeDownload("s4", null, "COMPLETED"), FakeDownload("s5", "c3", "QUEUED")))
            diskCovers["c1"] = "/covers/c1.jpg"
        }
        assertEquals(listOf("c2"), store.missingCoversForBackfill())
    }

    @Test
    fun songDownloadSuccess_whenCoverDownloadFails() {
        val store = FakeCoverStore()
        assertTrue(store.downloadSong(FakeDownload("s1", "c1", "QUEUED"), coverOk = false))
        assertEquals("COMPLETED", store.completed.first().status)
        assertFalse(store.diskCovers.containsKey("c1"))
    }
}