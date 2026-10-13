package com.feishin.mobile.ui.theme

import com.feishin.mobile.domain.model.PlaylistCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VusicDesignTest {

    // ---------- colour luminance clamping ----------

    @Test
    fun white_isDarkenedForReadableText() {
        val (r, g, b) = VusicColorMath.clampToDark(1f, 1f, 1f)
        val lum = VusicColorMath.relativeLuminance(r, g, b)
        assertTrue("white must be clamped down, got $lum", lum <= VusicColorMath.MAX_BACKDROP_LUMINANCE)
        // White text on the clamped backdrop must clear WCAG AA for large text.
        assertTrue(VusicColorMath.whiteContrastOn(lum) >= 4.5f)
    }

    @Test
    fun alreadyDarkColour_isUnchanged() {
        val dark = VusicColorMath.clampToDark(0.05f, 0.05f, 0.06f)
        assertEquals(0.05f, dark.first, 0.001f)
        assertEquals(0.05f, dark.second, 0.001f)
    }

    @Test
    fun clampingPreservesHueOrdering() {
        // A saturated pink should stay pink-dominant after clamping.
        val (r, g, b) = VusicColorMath.clampToDark(1f, 0.2f, 0.6f)
        assertTrue("red should stay the largest channel", r > g && r > b)
        assertTrue(VusicColorMath.relativeLuminance(r, g, b) <= VusicColorMath.MAX_BACKDROP_LUMINANCE)
    }

    @Test
    fun everySampledCoverColourEndsUpReadable() {
        val samples = listOf(
            Triple(1f, 1f, 1f),       // white cover
            Triple(0.95f, 0.95f, 0.9f), // near-white
            Triple(1f, 0.9f, 0.2f),   // yellow
            Triple(0.2f, 0.9f, 1f),   // cyan/blue art
            Triple(1f, 0.3f, 0.3f),   // red
            Triple(0f, 0f, 0f),       // black
            Triple(0.1f, 0.1f, 0.1f), // dark grey
            Triple(0.6f, 0.4f, 0.8f), // lavender
        )
        for ((r, g, b) in samples) {
            val (cr, cg, cb) = VusicColorMath.clampToDark(r, g, b)
            val lum = VusicColorMath.relativeLuminance(cr, cg, cb)
            assertTrue(
                "sample ($r,$g,$b) -> luminance $lum must be dark",
                lum <= VusicColorMath.MAX_BACKDROP_LUMINANCE + 0.001f,
            )
            assertTrue(
                "sample ($r,$g,$b) must give white text >= 4.5 contrast",
                VusicColorMath.whiteContrastOn(lum) >= 4.5f,
            )
        }
    }

    // ---------- playlist cover selection ----------

    @Test
    fun playlistCover_prefersServerArt() {
        val cover = PlaylistCover.select("srv-1", listOf("a", "b"))
        assertTrue(cover is PlaylistCover.Server)
        assertEquals("srv-1", (cover as PlaylistCover.Server).coverArtId)
    }

    @Test
    fun playlistCover_collageFromTracks_dedupedAndCappedAtFour() {
        val cover = PlaylistCover.select(null, listOf("a", "a", "b", null, "c", "d", "e", " "))
        assertTrue(cover is PlaylistCover.Collage)
        assertEquals(listOf("a", "b", "c", "d"), (cover as PlaylistCover.Collage).coverArtIds)
    }

    @Test
    fun playlistCover_singleTrackIsSingleCover() {
        val cover = PlaylistCover.select(null, listOf("only"))
        assertTrue(cover is PlaylistCover.Collage)
        assertEquals(1, (cover as PlaylistCover.Collage).coverArtIds.size)
    }

    @Test
    fun playlistCover_emptyIsPlaceholder() {
        assertTrue(PlaylistCover.select(null, emptyList()) is PlaylistCover.Placeholder)
        assertTrue(PlaylistCover.select(null, listOf(null, "", "  ")) is PlaylistCover.Placeholder)
        // Blank server art falls through to the collage/placeholder path.
        assertTrue(PlaylistCover.select("   ", emptyList()) is PlaylistCover.Placeholder)
    }
}
