package com.feishin.mobile.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** Section B (section_b_spec.md 2/3): tapping the playing row toggles in place, no seek; any
 *  other row seeks from 0 and plays. Rows match by stable queueId, so duplicate songs stay
 *  distinct, and repeat-one changes nothing. Mirrors PlayerManager.playOrToggle, which cannot be
 *  built here (needs a MediaController and a Context). */
class QueueTapToggleTest {
    private class Fake(playing: Boolean, ready: Boolean = playing) {
        val calls = mutableListOf<String>()
        var index = -1; var ms = -1L; var repeat = Player.REPEAT_MODE_OFF
        private var isPlayingNow = playing; private var readyNow = ready
        val p: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java)
        ) { _, m, a ->
            when (m.name) {
                "isPlaying" -> isPlayingNow
                "getPlayWhenReady" -> readyNow
                "getRepeatMode" -> repeat
                "play" -> { calls += "play"; isPlayingNow = true; readyNow = true; null }
                "pause" -> { calls += "pause"; isPlayingNow = false; readyNow = false; null }
                "seekTo" -> { calls += "seekTo"; index = a[0] as Int; ms = a[1] as Long; null }
                "toString" -> "Fake"
                else -> null
            }
        } as Player
    }

    private fun tap(p: Player, q: List<Long>, at: Int, id: Long) {
        if (q.getOrNull(at) == id) { if (p.isPlaying || p.playWhenReady) p.pause() else p.play(); return }
        val i = q.indexOfFirst { it == id }
        if (i >= 0) { p.seekTo(i, 0L); p.play() }
    }

    private val q = listOf(10L, 20L, 30L)
    @Test fun currentWhilePlaying_pausesNeverSeeks() {
        val f = Fake(playing = true)
        tap(f.p, q, 1, 20L)
        assertEquals(listOf("pause"), f.calls); assertEquals(-1, f.index); assertFalse(f.p.isPlaying)
    }
    @Test fun currentWhilePaused_playsWithoutSeeking() {
        val f = Fake(playing = false)
        tap(f.p, q, 1, 20L)
        assertEquals(listOf("play"), f.calls); assertEquals(-1, f.index); assertTrue(f.p.isPlaying)
    }
    @Test fun currentWhileBuffering_pauses() { // ready but not playing yet
        val f = Fake(playing = false, ready = true)
        tap(f.p, q, 1, 20L)
        assertEquals(listOf("pause"), f.calls); assertEquals(-1, f.index)
    }
    @Test fun otherRow_seeksToItsIndexFromZeroThenPlays() {
        val f = Fake(playing = true)
        tap(f.p, q, 0, 30L)
        assertEquals(listOf("seekTo", "play"), f.calls); assertEquals(2, f.index); assertEquals(0L, f.ms)
    }
    @Test fun duplicateSong_copiesDistinguishedByQueueId() { // one song, two entries; 90 plays
        val dup = listOf(90L, 91L)
        val same = Fake(playing = true)
        tap(same.p, dup, 0, 90L)
        assertEquals(listOf("pause"), same.calls); assertEquals(-1, same.index)
        val other = Fake(playing = true)
        tap(other.p, dup, 0, 91L)
        assertEquals(listOf("seekTo", "play"), other.calls); assertEquals(1, other.index); assertEquals(0L, other.ms)
    }
    @Test fun repeatOne_currentTogglesInPlace_otherStartsFromZero() {
        val same = Fake(playing = true).apply { repeat = Player.REPEAT_MODE_ONE }
        tap(same.p, q, 2, 30L)
        assertEquals(listOf("pause"), same.calls); assertEquals(-1, same.index)
        assertEquals(Player.REPEAT_MODE_ONE, same.p.repeatMode)
        val other = Fake(playing = true).apply { repeat = Player.REPEAT_MODE_ONE }
        tap(other.p, q, 2, 10L)
        assertEquals(listOf("seekTo", "play"), other.calls); assertEquals(0, other.index); assertEquals(0L, other.ms)
    }
}
