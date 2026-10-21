package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistBatchTest {

    @Test
    fun `empty list yields no chunks`() {
        assertEquals(emptyList<List<String>>(), PlaylistBatch.chunks(emptyList()))
    }

    @Test
    fun `one request for up to 100 ids`() {
        val ids = (1..100).map { "s$it" }
        val chunks = PlaylistBatch.chunks(ids)
        assertEquals(1, chunks.size)
        assertEquals(100, chunks[0].size)
    }

    @Test
    fun `101 ids split into 100 and 1`() {
        val ids = (1..101).map { "s$it" }
        val chunks = PlaylistBatch.chunks(ids)
        assertEquals(2, chunks.size)
        assertEquals(100, chunks[0].size)
        assertEquals(1, chunks[1].size)
    }

    @Test
    fun `500 ids split into five batches of 100`() {
        val ids = (1..500).map { "s$it" }
        val chunks = PlaylistBatch.chunks(ids)
        assertEquals(5, chunks.size)
        assertTrue(chunks.all { it.size == 100 })
    }

    @Test
    fun `create first chunk takes 100 or null when empty`() {
        assertEquals(null, PlaylistBatch.firstCreateChunk(emptyList()))
        val ids = (1..150).map { "s$it" }
        assertEquals(100, PlaylistBatch.firstCreateChunk(ids)!!.size)
    }

    @Test
    fun `remaining chunks drop the first 100`() {
        val ids = (1..250).map { "s$it" }
        val rem = PlaylistBatch.remainingChunks(ids)
        assertEquals(2, rem.size)
        assertEquals("s101", rem[0][0])
    }
}
