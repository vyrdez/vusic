package com.feishin.mobile.core.common

import com.feishin.mobile.data.db.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {

    @Test
    fun linearFromDb_knownValues() {
        assertEquals(1.0, ReplayGain.linearFromDb(0.0), 1e-6)
        assertEquals(0.5, ReplayGain.linearFromDb(-6.02), 1e-3)
        assertEquals(2.0, ReplayGain.linearFromDb(6.02), 1e-3)
    }

    @Test
    fun scale_neverBoostsAboveUnity() {
        // A positive gain would clip on most devices, so it is clamped to 1.
        assertEquals(1f, ReplayGain.scale(6.0), 1e-6f)
        assertEquals(1f, ReplayGain.scale(20.0, preampDb = 10.0), 1e-6f)
    }

    @Test
    fun scale_attenuates() {
        val s = ReplayGain.scale(-6.0)
        assertTrue(s < 1f)
        assertTrue(s > 0f)
    }

    @Test
    fun scale_nullGainIsUnity() {
        assertEquals(1f, ReplayGain.scale(null), 1e-6f)
    }

    @Test
    fun scale_peakLimiting() {
        // gain says 1.0 (0 dB) but the peak is 2.0 -> must attenuate to 0.5.
        val s = ReplayGain.scale(0.0, preampDb = 0.0, peak = 2.0)
        assertEquals(0.5f, s, 1e-3f)
    }

    @Test
    fun scale_preampApplies() {
        val withPre = ReplayGain.scale(-6.0, preampDb = 6.0)
        assertEquals(1f, withPre, 1e-2f)
    }

    @Test
    fun scale_ignoresBogusPeak() {
        assertEquals(0.5f, ReplayGain.scale(-6.02, peak = 0.0), 1e-3f)
        assertEquals(0.5f, ReplayGain.scale(-6.02, peak = -1.0), 1e-3f)
    }

    @Test
    fun pickGain_modes() {
        assertEquals(null, ReplayGain.pickGain(ReplayGainMode.OFF, -5.0, -3.0, -7.0))
        assertEquals(-5.0, ReplayGain.pickGain(ReplayGainMode.TRACK, -5.0, -3.0, -7.0))
        assertEquals(-3.0, ReplayGain.pickGain(ReplayGainMode.ALBUM, -5.0, -3.0, -7.0))
    }

    @Test
    fun pickGain_fallsBackWhenMissing() {
        assertEquals(-3.0, ReplayGain.pickGain(ReplayGainMode.TRACK, null, -3.0, -7.0))
        assertEquals(-7.0, ReplayGain.pickGain(ReplayGainMode.TRACK, null, null, -7.0))
        assertEquals(-5.0, ReplayGain.pickGain(ReplayGainMode.ALBUM, -5.0, null, -7.0))
    }

    @Test
    fun pickPeak_pairsWithTheMode() {
        assertEquals(0.9, ReplayGain.pickPeak(ReplayGainMode.TRACK, 0.9, 1.2))
        assertEquals(1.2, ReplayGain.pickPeak(ReplayGainMode.ALBUM, 0.9, 1.2))
        assertEquals(0.9, ReplayGain.pickPeak(ReplayGainMode.ALBUM, 0.9, null))
        assertEquals(null, ReplayGain.pickPeak(ReplayGainMode.OFF, 0.9, 1.2))
    }
}

class QualityPolicyTest {

    @Test
    fun wifiUsesWifiSettings() {
        val q = QualityPolicy.forNetwork(NetworkClass.WIFI, 0, "raw", 128, "mp3")
        assertEquals(0, q.bitrateKbps)
        assertEquals("raw", q.format)
        assertTrue(q.isOriginal)
    }

    @Test
    fun mobileUsesMobileSettings() {
        val q = QualityPolicy.forNetwork(NetworkClass.MOBILE, 0, "raw", 128, "mp3")
        assertEquals(128, q.bitrateKbps)
        assertEquals("mp3", q.format)
        assertFalse(q.isOriginal)
    }

    @Test
    fun otherTreatsAsUnmetered() {
        val q = QualityPolicy.forNetwork(NetworkClass.OTHER, 320, "opus", 64, "mp3")
        assertEquals(320, q.bitrateKbps)
        assertEquals("opus", q.format)
    }

    @Test
    fun download_originalIgnoresBitrate() {
        val q = QualityPolicy.forDownload(320, "mp3", original = true)
        assertTrue(q.isOriginal)
        assertEquals(0, q.bitrateKbps)
    }

    @Test
    fun download_transcoded() {
        val q = QualityPolicy.forDownload(192, "OPUS", original = false)
        assertEquals(192, q.bitrateKbps)
        assertEquals("opus", q.format)
    }

    @Test
    fun normalizeFormat_rejectsUnknown() {
        assertEquals("raw", QualityPolicy.normalizeFormat("flac"))
        assertEquals("mp3", QualityPolicy.normalizeFormat("MP3"))
    }

    @Test
    fun label_readsNaturally() {
        assertEquals("Original", QualityPolicy.label(0, "raw"))
        assertEquals("320 kbps mp3", QualityPolicy.label(320, "mp3"))
    }
}

class CacheSizePolicyTest {

    @Test
    fun streamSizes_snapToSupported() {
        assertEquals(256, CacheSizePolicy.normalizeStream(100))
        assertEquals(512, CacheSizePolicy.normalizeStream(512))
        assertEquals(1024, CacheSizePolicy.normalizeStream(900))
        // The ceiling is 100 GB now, so a value past it snaps to the ceiling
        // rather than to the old 5 GB.
        assertEquals(CacheSizePolicy.MAX_STREAM_MB, CacheSizePolicy.normalizeStream(99999))
    }

    @Test
    fun imageSizes_snapToSupported() {
        assertEquals(100, CacheSizePolicy.normalizeImage(1))
        assertEquals(250, CacheSizePolicy.normalizeImage(250))
        assertEquals(1024, CacheSizePolicy.normalizeImage(5000))
    }

    @Test
    fun bytes_convertsMb() {
        assertEquals(1024L * 1024, CacheSizePolicy.bytes(1))
        assertEquals(512L * 1024 * 1024, CacheSizePolicy.bytes(512))
    }

    @Test
    fun fits_respectsReserve() {
        val reserve = 64L * 1024 * 1024
        assertTrue(CacheSizePolicy.fits(sizeBytes = 10, freeBytes = reserve + 11))
        assertFalse(CacheSizePolicy.fits(sizeBytes = 10, freeBytes = reserve + 9))
        assertTrue(CacheSizePolicy.fits(sizeBytes = 5, freeBytes = Long.MAX_VALUE))
    }

    @Test
    fun human_formatsUnits() {
        assertEquals("512 B", CacheSizePolicy.human(512))
        assertEquals("1 KB".replace("1 KB", "1 KB"), CacheSizePolicy.human(1024).replace("1.0 KB", "1 KB"))
        assertTrue(CacheSizePolicy.human(1024L * 1024 * 1024).contains("GB"))
        assertTrue(CacheSizePolicy.human(5L * 1024 * 1024).contains("MB"))
    }
}

class DownloadStateMachineTest {

    @Test
    fun legalTransitions() {
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.DOWNLOADING, DownloadStatus.COMPLETED))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.PAUSED, DownloadStatus.QUEUED))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.FAILED, DownloadStatus.QUEUED))
    }

    @Test
    fun illegalTransitions() {
        assertFalse(DownloadStateMachine.canTransition(DownloadStatus.QUEUED, DownloadStatus.COMPLETED))
        assertFalse(DownloadStateMachine.canTransition(DownloadStatus.COMPLETED, DownloadStatus.DOWNLOADING))
    }

    @Test
    fun sameStateIsAlwaysAllowed() {
        DownloadStatus.entries.forEach {
            assertTrue(DownloadStateMachine.canTransition(it, it))
        }
    }

    @Test
    fun onlyQueuedAndPausedAreRunnable() {
        assertTrue(DownloadStateMachine.isRunnable(DownloadStatus.QUEUED))
        assertTrue(DownloadStateMachine.isRunnable(DownloadStatus.PAUSED))
        assertFalse(DownloadStateMachine.isRunnable(DownloadStatus.DOWNLOADING))
        assertFalse(DownloadStateMachine.isRunnable(DownloadStatus.COMPLETED))
    }

    @Test
    fun resumeDecision_needsBytesRangeAndRoomLeft() {
        assertTrue(DownloadStateMachine.resumeDecision(100, 1000, true))
        assertFalse(DownloadStateMachine.resumeDecision(0, 1000, true))
        assertFalse(DownloadStateMachine.resumeDecision(100, 1000, false))
        assertFalse(DownloadStateMachine.resumeDecision(1000, 1000, true))
    }

    @Test
    fun resumeDecision_unknownTotalStillResumes() {
        assertTrue(DownloadStateMachine.resumeDecision(100, 0, true))
    }

    @Test
    fun progress_clampsAndHandlesUnknown() {
        assertEquals(0f, DownloadStateMachine.progress(5, 0), 1e-6f)
        assertEquals(0.5f, DownloadStateMachine.progress(50, 100), 1e-6f)
        assertEquals(1f, DownloadStateMachine.progress(200, 100), 1e-6f)
    }
}

class PlaybackSourceTest {

    private val exists: (String) -> Boolean = { it == "/data/song.flac" }

    @Test
    fun localWhenFilePresent() {
        assertEquals(PlaybackSource.Kind.LOCAL, PlaybackSource.kind("/data/song.flac", exists))
    }

    @Test
    fun streamWhenMissing() {
        assertEquals(PlaybackSource.Kind.STREAM, PlaybackSource.kind("/data/gone.flac", exists))
        assertEquals(PlaybackSource.Kind.STREAM, PlaybackSource.kind(null, exists))
    }

    @Test
    fun localUri_addsSchemeOnce() {
        assertEquals("file:///data/song.flac", PlaybackSource.localUri("/data/song.flac"))
        assertEquals("file:///data/song.flac", PlaybackSource.localUri("file:///data/song.flac"))
    }

    @Test
    fun isPlayableLocalFile_validation() {
        val fileCheck: (String) -> Boolean = { it == "/valid/song.flac" || it == "/valid/song.part" || it == "/valid/song.tmp" || it == "/tiny/song.flac" }
        val lenCheck: (String) -> Long = {
            when (it) {
                "/valid/song.flac" -> 50_000L
                "/valid/song.part" -> 50_000L
                "/valid/song.tmp" -> 50_000L
                "/tiny/song.flac" -> 500L
                else -> 0L
            }
        }

        // Rejects null and missing
        assertFalse(PlaybackSource.isPlayableLocalFile(null, fileCheck, lenCheck))
        assertFalse(PlaybackSource.isPlayableLocalFile("/missing/song.flac", fileCheck, lenCheck))

        // Rejects .part and .tmp
        assertFalse(PlaybackSource.isPlayableLocalFile("/valid/song.part", fileCheck, lenCheck))
        assertFalse(PlaybackSource.isPlayableLocalFile("/valid/song.tmp", fileCheck, lenCheck))

        // Rejects < 8192 bytes
        assertFalse(PlaybackSource.isPlayableLocalFile("/tiny/song.flac", fileCheck, lenCheck))

        // Accepts valid file
        assertTrue(PlaybackSource.isPlayableLocalFile("/valid/song.flac", fileCheck, lenCheck))
    }

    @Test
    fun mimeTypeForPath_resolvesAudioMimeTypes() {
        assertEquals("audio/flac", PlaybackSource.mimeTypeForPath("/music/song.flac"))
        assertEquals("audio/flac", PlaybackSource.mimeTypeForPath("file:///storage/song.FLAC"))
        assertEquals("audio/mpeg", PlaybackSource.mimeTypeForPath("/music/song.mp3"))
        assertEquals("audio/ogg", PlaybackSource.mimeTypeForPath("/music/song.opus"))
        assertEquals("audio/ogg", PlaybackSource.mimeTypeForPath("file:///song.ogg"))
        assertEquals("audio/mp4", PlaybackSource.mimeTypeForPath("/music/song.m4a"))
        assertEquals("audio/mp4", PlaybackSource.mimeTypeForPath("/music/song.aac"))
        assertEquals("audio/mp4", PlaybackSource.mimeTypeForPath("/music/song.mp4"))
        assertEquals("audio/wav", PlaybackSource.mimeTypeForPath("/music/song.wav"))

        assertNull(PlaybackSource.mimeTypeForPath("/music/song.bin"))
        assertNull(PlaybackSource.mimeTypeForPath("/music/song"))
        assertNull(PlaybackSource.mimeTypeForPath(null))
    }

    @Test
    fun shouldBypassStreamCache_validation() {
        assertTrue(PlaybackSource.shouldBypassStreamCache("file"))
        assertTrue(PlaybackSource.shouldBypassStreamCache("content"))
        assertTrue(PlaybackSource.shouldBypassStreamCache("android.resource"))
        assertFalse(PlaybackSource.shouldBypassStreamCache("http"))
        assertFalse(PlaybackSource.shouldBypassStreamCache("https"))
        assertFalse(PlaybackSource.shouldBypassStreamCache(null))
    }
}

class OfflineRadioTest {

    private fun track(id: String, genre: String?, artist: String = "a") =
        com.feishin.mobile.domain.model.Track(
            id = id, title = id, album = "al", albumId = "al", artist = artist, artistId = artist,
            coverArt = null, durationSec = 100, trackNo = null, year = null, genre = genre,
            suffix = "flac", starred = false,
        )

    private val downloaded = listOf(
        track("r1", "Rock"), track("r2", "Rock"), track("r3", "Rock"),
        track("p1", "Pop"), track("p2", "Pop"),
        track("x1", null),
    )

    @Test
    fun sameGenreFirstThenOthers() {
        val out = OfflineRadio.pick(downloaded, "Rock", emptySet(), want = 6)
        assertEquals(listOf("r1", "r2", "r3", "p1", "p2", "x1"), out.map { it.id })
    }

    @Test
    fun respectsWant() {
        val out = OfflineRadio.pick(downloaded, "Rock", emptySet(), want = 2)
        assertEquals(listOf("r1", "r2"), out.map { it.id })
    }

    @Test
    fun excludesQueueAndHistory() {
        val out = OfflineRadio.pick(downloaded, "Rock", setOf("r1", "p1"), want = 10)
        assertEquals(listOf("r2", "r3", "p2", "x1"), out.map { it.id })
    }

    @Test
    fun emptyWhenNothingDownloaded() {
        assertTrue(OfflineRadio.pick(emptyList(), "Rock", emptySet()).isEmpty())
    }

    @Test
    fun emptyWhenEverythingExcluded() {
        val out = OfflineRadio.pick(downloaded, "Rock", downloaded.map { it.id }.toSet())
        assertTrue(out.isEmpty())
    }

    @Test
    fun noGenreFallsBackToAnyOrder() {
        val out = OfflineRadio.pick(downloaded, null, emptySet(), want = 3)
        assertEquals(3, out.size)
    }

    @Test
    fun neverDuplicates() {
        val dupes = downloaded + downloaded
        val out = OfflineRadio.pick(dupes, "Rock", emptySet(), want = 20)
        assertEquals(out.size, out.map { it.id }.toSet().size)
    }
}
