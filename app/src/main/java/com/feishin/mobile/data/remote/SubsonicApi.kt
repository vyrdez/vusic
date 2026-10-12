package com.feishin.mobile.data.remote

import com.feishin.mobile.data.model.SubsonicEnvelope
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {
    @GET("ping.view")
    suspend fun ping(): SubsonicEnvelope

    @GET("getAlbumList.view")
    suspend fun getAlbumList(
        @Query("type") type: String,
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope

    @GET("getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope

    @GET("search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 50,
    ): SubsonicEnvelope

    @GET("getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @GET("getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    @GET("getArtists.view")
    suspend fun getArtists(): SubsonicEnvelope

    @GET("getGenres.view")
    suspend fun getGenres(): SubsonicEnvelope

    @GET("getPlaylists.view")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("createPlaylist.view")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null,
    ): SubsonicEnvelope

    @GET("updatePlaylist.view")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("songIdToAdd") songIdToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexToRemove: List<Int>? = null,
    ): SubsonicEnvelope

    @GET("deletePlaylist.view")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("star.view")
    suspend fun star(
        @Query("id") ids: List<String>? = null,
        @Query("albumId") albumIds: List<String>? = null,
        @Query("artistId") artistIds: List<String>? = null,
    ): SubsonicEnvelope

    @GET("unstar.view")
    suspend fun unstar(
        @Query("id") ids: List<String>? = null,
        @Query("albumId") albumIds: List<String>? = null,
        @Query("artistId") artistIds: List<String>? = null,
    ): SubsonicEnvelope

    @GET("getStarred.view")
    suspend fun getStarred(): SubsonicEnvelope

    @GET("getStarred2.view")
    suspend fun getStarred2(): SubsonicEnvelope

    @GET("scrobble.view")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("time") time: Long? = null,
        @Query("submission") submission: Boolean = true,
    ): SubsonicEnvelope

    @GET("getLyrics.view")
    suspend fun getLyrics(
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null,
    ): SubsonicEnvelope
}
