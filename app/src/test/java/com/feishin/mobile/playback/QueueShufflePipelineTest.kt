package com.feishin.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.feishin.mobile.core.common.ShufflePlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.random.Random

/** One timeline-changing call per settled toggle, same items/entry/position, latest toggle wins. */
class QueueShufflePipelineTest {

    private class FakePlayer(private val pos: Long, var playing: Boolean) {
        val calls = mutableListOf<List<MediaItem>>(); var index = -1; var position = -1L
        val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, m, a ->
            when (m.name) {
                "setMediaItems" -> { @Suppress("UNCHECKED_CAST") calls += a[0] as List<MediaItem>; index = a[1] as Int; position = a[2] as Long; null }
                "getCurrentPosition" -> pos
                "isPlaying" -> playing
                "play" -> { playing = true; null }
                "toString" -> "FakePlayer"
                else -> null
            }
        } as Player
    }

    private val ids = (0L until 533L).toList()
    private val items = ids.associateWith { MediaItem.Builder().setMediaId("song_$it").build() }
    private val shuffled = ShufflePlan.shuffledTail(ids, fromIndex = 1, random = Random(7))
    private val mediaIds = ids.map { "song_$it" }

    /** Mirrors applyOrder: plan, resolve, ONE setMediaItems, current entry kept by queue id. */
    private fun apply(p: Player, target: List<Long>): List<Long> {
        val order = ShufflePlan.applyOrder(target, ids) { it }
        val wasPlaying = p.isPlaying
        p.setMediaItems(order.map { items.getValue(it) }, order.indexOf(0L), p.currentPosition)
        if (wasPlaying && !p.isPlaying) p.play()
        return order
    }

    @Test
    fun shuffleOn_533Items_oneTimelineCall_identityAndPositionPreserved() {
        val fake = FakePlayer(pos = 42_000L, playing = true)
        val reordered = apply(fake.player, shuffled)
        assertEquals(1, fake.calls.size)
        assertEquals(mediaIds.toSet(), fake.calls[0].map { it.mediaId }.toSet())
        assertNotEquals(ids, reordered); assertEquals(0L, reordered.first())
        assertEquals(0, fake.index); assertEquals(42_000L, fake.position); assertTrue(fake.playing)
    }

    @Test
    fun shuffleOff_533Items_oneTimelineCall_originalOrderRestored() {
        val fake = FakePlayer(pos = 12_345L, playing = false)
        val restored = apply(fake.player, ShufflePlan.restore(shuffled, ids))
        assertEquals(1, fake.calls.size); assertEquals(ids, restored)
        assertEquals(mediaIds, fake.calls[0].map { it.mediaId })
        assertEquals(0, fake.index); assertEquals(12_345L, fake.position)
    }

    @Test
    fun toggleSpam_coalescesToLatestWin() = runTest {
        val fake = FakePlayer(pos = 0L, playing = true)
        var job: kotlinx.coroutines.Job? = null
        for (on in listOf(true, false, true, false, true)) {
            job?.cancel()
            job = launch { delay(5); apply(fake.player, if (on) shuffled else ids) }
        }
        advanceUntilIdle()
        assertEquals(1, fake.calls.size)
        assertNotEquals(mediaIds, fake.calls[0].map { it.mediaId })
        assertEquals("song_0", fake.calls[0].first().mediaId)
    }
}
