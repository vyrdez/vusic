package com.feishin.mobile.data.model

import com.feishin.mobile.core.network.SubsonicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoParsingTest {
    private val json = SubsonicAuth.json

    private fun parse(body: String): SubsonicResponse =
        json.decodeFromString(SubsonicEnvelope.serializer(), body).response

    @Test
    fun albumList_arrayShape() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "albumList2":{"album":[{"id":"a1","name":"Frozen Flower","artist":"Rokudenashi","songCount":17,"duration":3544},{"id":"a2","name":"15"}]}}}""",
        )
        assertEquals("ok", r.status)
        assertEquals(2, r.albumList2?.album?.size)
        assertEquals("a1", r.albumList2?.album?.get(0)?.id)
    }

    @Test
    fun albumList_singleObjectShape_isWrapped() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "albumList2":{"album":{"id":"solo","name":"Solo","artist":"One"}}}}""",
        )
        assertEquals(1, r.albumList2?.album?.size)
        assertEquals("solo", r.albumList2?.album?.get(0)?.id)
    }

    @Test
    fun search_singleObjectsAndMissingFields() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "searchResult3":{"artist":{"id":"ar1","name":"A"},"album":[],"song":{"id":"s1","title":"T"}}}}""",
        )
        val s = r.searchResult3
        assertEquals(1, s?.artist?.size)
        assertEquals(0, s?.album?.size)
        assertEquals(1, s?.song?.size)
        // Missing optional fields fall back to defaults, not crash.
        assertEquals("", s?.song?.get(0)?.artist)
        assertEquals(0, s?.song?.get(0)?.duration)
    }

    @Test
    fun artistIndex_singleObjectShape() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "artists":{"index":{"name":"R","artist":{"id":"ar9","name":"Roku"}}}}}""",
        )
        assertEquals(1, r.artists?.index?.size)
        assertEquals(1, r.artists?.index?.get(0)?.artist?.size)
    }

    @Test
    fun playlistDetail_singleEntryObject() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "playlist":{"id":"p1","name":"Mix","entry":{"id":"s1","title":"T"}}}}""",
        )
        assertEquals(1, r.playlist?.entry?.size)
    }

    @Test
    fun albumDetail_singleSongObject_plusExtrasIgnored() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "album":{"id":"a1","name":"N","song":{"id":"s1","title":"T"},"someFutureField":{"x":1},"replayGain":{}}}}""",
        )
        assertEquals(1, r.album?.song?.size)
    }

    @Test
    fun emptyBody_defaultsDoNotCrash() {
        val r = parse("""{"subsonic-response":{}}""")
        assertEquals("", r.status)
        assertTrue(r.albumList2 == null)
    }

    @Test
    fun genres_navidromeValueShape() {
        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "genres":{"genre":[{"value":"Music","songCount":1841,"albumCount":1218},{"value":"Rock"}]}}}""",
        )
        assertEquals(2, r.genres?.genre?.size)
        assertEquals("Music", r.genres?.genre?.get(0)?.value)
        assertEquals("", r.genres?.genre?.get(0)?.name)
    }

    @Test
    fun starred_singleObjects() {        val r = parse(
            """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "starred2":{"artist":[],"album":{"id":"a1","name":"N"},"song":[]}}}""",
        )
        assertEquals(1, r.starred2?.album?.size)
        assertTrue(r.starred2?.song?.isEmpty() == true)
    }
}
