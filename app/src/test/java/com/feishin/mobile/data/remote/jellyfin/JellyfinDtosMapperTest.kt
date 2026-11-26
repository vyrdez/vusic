package com.feishin.mobile.data.remote.jellyfin

import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinDtosMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val profile = ServerProfile(
        id = "jf1",
        name = "Home Jellyfin",
        type = BackendType.JELLYFIN,
        url = "https://jellyfin.local",
        userId = "u123",
        credentials = ProfileCredentials.Jellyfin(
            username = "alice",
            accessToken = "tok-abc",
            serverId = "srv-1",
            deviceId = "dev-1",
        ),
    )

    @Test
    fun `authenticateByName response parses user policy EnableContentDownloading`() {
        val raw = """
            {
              "AccessToken": "token-xyz",
              "ServerId": "server-99",
              "User": {
                "Id": "user-42",
                "Name": "alice",
                "Policy": {
                  "IsAdministrator": false,
                  "EnableContentDownloading": true
                }
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<JellyfinAuthResponse>(raw)
        assertEquals("token-xyz", dto.accessToken)
        assertEquals("server-99", dto.serverId)
        assertEquals("user-42", dto.user.id)
        assertEquals("alice", dto.user.name)
        assertEquals(true, dto.user.policy?.enableContentDownloading)
    }

    @Test
    fun `items response maps albums and tracks with ticks, kbps bitrate, GlobalId, and cover fallback`() {
        val raw = """
            {
              "Items": [
                {
                  "Id": "alb-1",
                  "Name": "Discovery",
                  "Type": "MusicAlbum",
                  "RunTimeTicks": 36500000000,
                  "ProductionYear": 2001,
                  "Genres": ["Electronic", "House"],
                  "AlbumArtist": "Daft Punk",
                  "AlbumArtists": [{"Name": "Daft Punk", "Id": "art-1"}],
                  "ChildCount": 14,
                  "UserData": {"IsFavorite": true, "PlayCount": 12},
                  "ImageTags": {"Primary": "tag-alb"}
                },
                {
                  "Id": "trk-1",
                  "Name": "One More Time",
                  "Type": "Audio",
                  "MediaType": "Audio",
                  "RunTimeTicks": 3200000000,
                  "IndexNumber": 1,
                  "ParentIndexNumber": 1,
                  "ProductionYear": 2001,
                  "Genres": ["Electronic"],
                  "Album": "Discovery",
                  "AlbumId": "alb-1",
                  "AlbumArtist": "Daft Punk",
                  "ArtistItems": [{"Name": "Daft Punk", "Id": "art-1"}],
                  "UserData": {"IsFavorite": false, "PlayCount": 3},
                  "MediaSources": [
                    {
                      "Id": "trk-1",
                      "Container": "flac",
                      "Bitrate": 1024000,
                      "Size": 41943040,
                      "Path": "/music/Daft Punk/Discovery/01 One More Time.flac"
                    }
                  ],
                  "PlaylistItemId": "pl-item-77",
                  "ImageTags": {}
                },
                {
                  "Id": "trk-2",
                  "Name": "Aerodynamic",
                  "Type": "Audio",
                  "RunTimeTicks": 2120000000,
                  "IndexNumber": 2,
                  "ParentIndexNumber": 1,
                  "Album": "Discovery",
                  "AlbumId": "alb-1",
                  "ImageTags": {"Primary": "tag-trk-2"}
                }
              ],
              "TotalRecordCount": 3,
              "StartIndex": 0
            }
        """.trimIndent()

        val response = json.decodeFromString<JellyfinItemsResponse>(raw)
        assertEquals(3, response.totalRecordCount)

        val album = response.items[0].toAlbum(profile)
        assertEquals("jf1:alb-1", album.id)
        assertEquals("Discovery", album.name)
        assertEquals("Daft Punk", album.artist)
        assertEquals("jf1:art-1", album.artistId)
        assertEquals("jf1:alb-1", album.coverArt)
        assertEquals(14, album.songCount)
        assertEquals(3650, album.durationSec)
        assertEquals(12L, album.playCount)
        assertEquals(2001, album.year)
        assertEquals("Electronic", album.genre)
        assertTrue(album.starred)
        assertEquals("jf1", album.sourceProfileId)
        assertEquals("Home Jellyfin", album.sourceLabel)

        val trackNoPrimary = response.items[1].toTrack(profile)
        assertEquals("jf1:trk-1", trackNoPrimary.id)
        assertEquals("One More Time", trackNoPrimary.title)
        assertEquals("Discovery", trackNoPrimary.album)
        assertEquals("jf1:alb-1", trackNoPrimary.albumId)
        assertEquals("Daft Punk", trackNoPrimary.artist)
        assertEquals("jf1:art-1", trackNoPrimary.artistId)
        assertEquals("jf1:alb-1", trackNoPrimary.coverArt) // AlbumId fallback
        assertEquals(320, trackNoPrimary.durationSec)
        assertEquals(1, trackNoPrimary.trackNo)
        assertEquals("flac", trackNoPrimary.suffix)
        assertEquals(1024, trackNoPrimary.bitRate) // 1024000 bps -> 1024 kbps
        assertEquals(41943040L, trackNoPrimary.sizeBytes)
        assertEquals("/music/Daft Punk/Discovery/01 One More Time.flac", trackNoPrimary.path)
        assertFalse(trackNoPrimary.starred)
        assertEquals("jf1", trackNoPrimary.sourceProfileId)
        assertEquals("Home Jellyfin", trackNoPrimary.sourceLabel)
        assertEquals("pl-item-77", response.items[1].playlistItemId)

        val trackWithPrimary = response.items[2].toTrack(profile)
        assertEquals("jf1:trk-2", trackWithPrimary.coverArt)
    }

    @Test
    fun `latest items array and instant mix decode and map correctly`() {
        val latestRaw = """
            [
              {
                "Id": "alb-latest",
                "Name": "Random Access Memories",
                "Type": "MusicAlbum",
                "RunTimeTicks": 44800000000,
                "AlbumArtist": "Daft Punk",
                "ImageTags": {}
              }
            ]
        """.trimIndent()

        val latest = json.decodeFromString<List<JellyfinBaseItemDto>>(latestRaw)
        assertEquals(1, latest.size)
        val latestAlbum = latest.first().toAlbum(profile)
        assertEquals("jf1:alb-latest", latestAlbum.id)
        assertEquals(4480, latestAlbum.durationSec)
        assertNull(latestAlbum.coverArt)

        val mixRaw = """
            {
              "Items": [
                {
                  "Id": "mix-1",
                  "Name": "Instant Crush",
                  "Type": "Audio",
                  "RunTimeTicks": 3370000000,
                  "MediaSources": [{"Bitrate": 320000, "Container": "mp3"}]
                }
              ],
              "TotalRecordCount": 1
            }
        """.trimIndent()

        val mix = json.decodeFromString<JellyfinItemsResponse>(mixRaw)
        val mixTrack = mix.items.single().toTrack(profile)
        assertEquals("jf1:mix-1", mixTrack.id)
        assertEquals(337, mixTrack.durationSec)
        assertEquals(320, mixTrack.bitRate)
        assertNull(mixTrack.coverArt)
    }

    @Test
    fun `audio lyrics decodes synced and unsynced lines`() {
        val raw = """
            {
              "Metadata": {
                "Artist": "Daft Punk",
                "Title": "Touch",
                "IsSynced": true
              },
              "Lyrics": [
                {"Text": "Hold on", "Start": 150000000},
                {"Text": "If love is the answer", "Start": null}
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<JellyfinLyricsDto>(raw)
        assertEquals("Daft Punk", dto.metadata?.artist)
        assertEquals(true, dto.metadata?.isSynced)
        assertEquals(2, dto.lyrics.size)
        assertEquals("Hold on", dto.lyrics[0].text)
        assertEquals(150_000_000L, dto.lyrics[0].start)
        assertNull(dto.lyrics[1].start)
    }

    @Test
    fun `multi-disc tracks sort by ParentIndexNumber then IndexNumber`() {
        val d2t1 = JellyfinBaseItemDto(id = "4", name = "D2T1", parentIndexNumber = 2, indexNumber = 1)
        val d1t2 = JellyfinBaseItemDto(id = "2", name = "D1T2", parentIndexNumber = 1, indexNumber = 2)
        val d1t1 = JellyfinBaseItemDto(id = "1", name = "D1T1", parentIndexNumber = 1, indexNumber = 1)

        val sorted = listOf(d2t1, d1t2, d1t1).sortedByDiscAndTrack()
        assertEquals(listOf("1", "2", "4"), sorted.map { it.id })
    }
}
