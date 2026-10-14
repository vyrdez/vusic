package com.feishin.mobile.ui.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.mapper.toDomain
import com.feishin.mobile.data.model.ArtistInfo2Dto
import com.feishin.mobile.data.model.SubsonicEnvelope
import com.feishin.mobile.data.queue.QueueSnapshot
import com.feishin.mobile.data.queue.QueueStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.speedDialKey
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.ui.library.LibraryTab
import com.feishin.mobile.ui.library.LibraryTab.ALBUMS
import com.feishin.mobile.ui.library.LibraryTab.PLAYLISTS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun track(id: String) = Track(
    id, "T$id", "Al", "al", "Ar", "ar", null, 180, 1, 2024, "Music", "mp3", false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class M45Test {

    @get:Rule
    val temp = TemporaryFolder()

    // ---------- artist info parsing ----------

    @Test
    fun artistInfo_fullPayload_parses() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1",
            "artistInfo2":{"biography":"<p>Born in <b>Osaka</b>.</p>",
            "musicBrainzId":"mb-1","lastFmUrl":"https://fm/x",
            "smallImage":"s","mediumImage":"m","largeImage":"L",
            "similarArtist":[{"id":"a1","name":"One"},{"id":"a2","name":"Two"}]}}}"""
        val info = SubsonicAuth.json.decodeFromString(SubsonicEnvelope.serializer(), body)
            .response.artistInfo2
        assertEquals("L", info?.largeImage)
        assertEquals(2, info?.similarArtist?.size)
        val domain = info!!.toDomain()
        assertEquals("L", domain.imageUrl)
        assertEquals("One", domain.similarArtists[0].name)
    }

    @Test
    fun artistInfo_missingFields_defaultCleanly() {
        val minimal = SubsonicAuth.json.decodeFromString(
            SubsonicEnvelope.serializer(),
            """{"subsonic-response":{"status":"ok"}}""",
        ).response.artistInfo2
        assertEquals(null, minimal)
        val empty = ArtistInfo2Dto().toDomain()
        assertEquals("", empty.biography)
        assertEquals(null, empty.imageUrl)
        assertTrue(empty.similarArtists.isEmpty())
    }

    @Test
    fun artistInfo_imagePrefersLargest() {
        assertEquals("m", ArtistInfo2Dto(mediumImage = "m", smallImage = "s").toDomain().imageUrl)
        assertEquals("s", ArtistInfo2Dto(smallImage = "s").toDomain().imageUrl)
    }

    @Test
    fun artistInfo_singleSimilarArtistObject_isWrapped() {
        val body = """{"subsonic-response":{"status":"ok",
            "artistInfo2":{"similarArtist":{"id":"a9","name":"Solo"}}}}"""
        val info = SubsonicAuth.json.decodeFromString(SubsonicEnvelope.serializer(), body)
            .response.artistInfo2
        assertEquals(1, info?.similarArtist?.size)
        assertEquals("Solo", info?.similarArtist?.get(0)?.name)
    }

    // ---------- HTML stripping ----------

    @Test
    fun stripHtml_removesTagsAndEntities() {
        assertEquals(
            "Born in Osaka & Kyoto.",
            stripHtmlBio("<p>Born in <b>Osaka</b> &amp; Kyoto.</p>"),
        )
        assertEquals("", stripHtmlBio("   "))
        assertEquals("plain", stripHtmlBio("plain"))
        assertEquals("a < b > c", stripHtmlBio("a &lt; b &gt; c"))
    }

    // ---------- pins + recent searches ----------

    private fun settings() = SettingsStore(
        PreferenceDataStoreFactory.create { temp.newFile("m45_settings.preferences_pb") },
    )

    @Test
    fun pins_addRemoveRoundTrip() = runTest {
        val s = settings()
        assertTrue(s.pinsNow().isEmpty())
        s.setPinned(speedDialKey("album", "a1"), true)
        s.setPinned(speedDialKey("playlist", "p1"), true)
        assertEquals(setOf("album:a1", "playlist:p1"), s.pinsNow())
        s.setPinned(speedDialKey("album", "a1"), false)
        assertEquals(setOf("playlist:p1"), s.pinsNow())
    }

    @Test
    fun recentSearches_dedupeOrderAndLimit() = runTest {
        val s = settings()
        s.addRecentSearch("  ")
        s.addRecentSearch("frozen flower")
        s.addRecentSearch("rokudenashi")
        s.addRecentSearch("frozen flower")
        assertEquals(listOf("frozen flower", "rokudenashi"), s.flowNow().recentSearches)
        repeat(25) { s.addRecentSearch("q$it") }
        val recent = s.flowNow().recentSearches
        assertEquals(20, recent.size)
        assertEquals("q24", recent.first())
        s.clearRecentSearches()
        assertTrue(s.flowNow().recentSearches.isEmpty())
    }

    // ---------- queue source persistence ----------

    @Test
    fun queueSnapshot_sourceRoundTrips() = runTest {
        val store = QueueStore(
            PreferenceDataStoreFactory.create { temp.newFile("m45_queue.preferences_pb") },
        )
        store.save(listOf(track("1"), track("2")), 1, 12345L, "My Mix 1")
        val loaded: QueueSnapshot? = store.load()
        assertEquals("My Mix 1", loaded?.sourceName)
        assertEquals(1, loaded?.currentIndex)
        assertEquals(12345L, loaded?.positionMs)
        assertEquals(listOf("1", "2"), loaded?.tracks?.map { it.id })
    }

    // ---------- navigation routes ----------

    @Test
    fun routes_buildCorrectly() {
        assertEquals("library?tab=ALBUMS", com.feishin.mobile.ui.Routes.library(ALBUMS))
        assertEquals("library?tab=PLAYLISTS", com.feishin.mobile.ui.Routes.library(PLAYLISTS))
        assertEquals("library?tab=PLAYLISTS", com.feishin.mobile.ui.Routes.library())
        assertEquals("album/abc", com.feishin.mobile.ui.Routes.album("abc"))
        assertEquals("artist/xyz", com.feishin.mobile.ui.Routes.artist("xyz"))
        assertEquals("playlist/p1", com.feishin.mobile.ui.Routes.playlist("p1"))
        // Genre names with spaces/& stay in one path segment (percent-encoded).
        val g = com.feishin.mobile.ui.Routes.genre("Film & Animation")
        assertTrue(g.startsWith("genre/"))
        assertTrue(!g.contains(" ") && !g.contains("&"))
        assertEquals("genres", com.feishin.mobile.ui.Routes.GENRES)
    }

    @Test
    fun libraryTab_parsesBack() {
        assertEquals(LibraryTab.SONGS, LibraryTab.valueOf("SONGS"))
        assertEquals(LibraryTab.FAVORITES, LibraryTab.valueOf("FAVORITES"))
    }
}

private suspend fun SettingsStore.flowNow() = flow.first()
