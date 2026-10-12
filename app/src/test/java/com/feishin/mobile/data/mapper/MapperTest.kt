package com.feishin.mobile.data.mapper

import com.feishin.mobile.data.model.AlbumDto
import com.feishin.mobile.data.model.ArtistDto
import com.feishin.mobile.data.model.GenreDto
import com.feishin.mobile.data.model.PlaylistDto
import com.feishin.mobile.data.model.SongDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {
    @Test
    fun album_mapsAllFields() {
        val d = AlbumDto(id = "a", name = "N", artist = "Ar", artistId = "ar", coverArt = "c",
            songCount = 5, duration = 300, playCount = 42, year = 2021, genre = "Rock", starred = "2024-01-01")
        val m = d.toDomain()
        assertEquals("a", m.id)
        assertEquals("N", m.name)
        assertEquals("Ar", m.artist)
        assertEquals("ar", m.artistId)
        assertEquals("c", m.coverArt)
        assertEquals(5, m.songCount)
        assertEquals(300, m.durationSec)
        assertEquals(42L, m.playCount)
        assertEquals(2021, m.year)
        assertEquals("Rock", m.genre)
        assertTrue(m.starred)
    }

    @Test
    fun track_blankNamesAndNullStar_getDefaults() {
        val m = SongDto(id = "s").toDomain()
        assertEquals("Unknown track", m.title)
        assertEquals("Unknown artist", m.artist)
        assertFalse(m.starred)
    }

    @Test
    fun genre_prefersNameFallsBackToValue() {
        assertEquals("Jazz", GenreDto(name = "Jazz", value = "Other").toDomain().name)
        assertEquals("Music", GenreDto(value = "Music").toDomain().name)
        assertEquals("Unknown", GenreDto().toDomain().name)
    }

    @Test
    fun artist_genre_playlist_map() {
        assertEquals("X", ArtistDto(id = "1", name = "X", albumCount = 3).toDomain().name)
        assertEquals(3, ArtistDto(id = "1", name = "X", albumCount = 3).toDomain().albumCount)
        val g = GenreDto(name = "Jazz", songCount = 10, albumCount = 2).toDomain()
        assertEquals("Jazz", g.name)
        assertEquals(10, g.songCount)
        val p = PlaylistDto(id = "p", name = "Mix", songCount = 4, duration = 900).toDomain()
        assertEquals(4, p.songCount)
        assertEquals(900, p.durationSec)
    }
}
