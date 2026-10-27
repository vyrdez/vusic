package com.feishin.mobile.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClearDownloadQueueTest {
    data class FakeDownload(val songId: String, val status: String, val filePath: String? = null, val tempPath: String? = null)

    class FakeDownloadQueueManager {
        val rows = mutableListOf<FakeDownload>()
        var cancelledSchedulerCount = 0

        fun partialFilePaths(): List<String> =
            rows.filter { it.status in listOf("DOWNLOADING", "PAUSED") }.mapNotNull { it.tempPath ?: it.filePath }

        fun clearPending(): Int {
            val countBefore = rows.size
            rows.removeAll { it.status in listOf("QUEUED", "DOWNLOADING", "PAUSED") }
            return countBefore - rows.size
        }

        fun clearQueue(deleteFile: (String) -> Boolean) {
            cancelledSchedulerCount++
            partialFilePaths().forEach { deleteFile(it) }
            clearPending()
        }
    }

    @Test
    fun clearsQueuedDownloadingPausedInSingleBatch() {
        val manager = FakeDownloadQueueManager().apply {
            rows.addAll(listOf(FakeDownload("1", "QUEUED"), FakeDownload("2", "DOWNLOADING", tempPath = "/tmp/2.tmp"), FakeDownload("3", "PAUSED", tempPath = "/tmp/3.tmp")))
        }
        assertEquals(3, manager.clearPending())
        assertTrue(manager.rows.isEmpty())
    }

    @Test
    fun finishedDownloadsRemainUntouched() {
        val manager = FakeDownloadQueueManager()
        val finished = FakeDownload("c1", "COMPLETED", filePath = "/music/c1.flac")
        manager.rows.addAll(listOf(finished, FakeDownload("q1", "QUEUED")))
        val deleted = mutableListOf<String>()
        manager.clearQueue { deleted.add(it); true }
        assertEquals(listOf(finished), manager.rows)
        assertFalse(deleted.contains("/music/c1.flac"))
    }

    @Test
    fun partialFilesAreDeleted() {
        val tempFile = File.createTempFile("test_dl_", ".tmp").apply { writeText("partial") }
        assertTrue(tempFile.exists())
        val manager = FakeDownloadQueueManager().apply {
            rows.add(FakeDownload("1", "DOWNLOADING", tempPath = tempFile.absolutePath))
        }
        manager.clearQueue { File(it).delete() }
        assertFalse(tempFile.exists())
        assertTrue(manager.rows.isEmpty())
        assertEquals(1, manager.cancelledSchedulerCount)
    }

    @Test
    fun idempotentOnEmptyQueue() {
        val manager = FakeDownloadQueueManager()
        var deletedCount = 0
        manager.clearQueue { deletedCount++; true }
        assertTrue(manager.rows.isEmpty())
        assertEquals(0, deletedCount)
        assertEquals(1, manager.cancelledSchedulerCount)
    }
}
