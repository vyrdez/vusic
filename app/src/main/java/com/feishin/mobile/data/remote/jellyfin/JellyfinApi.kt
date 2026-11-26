package com.feishin.mobile.data.remote.jellyfin

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body request: JellyfinAuthRequest,
    ): JellyfinAuthResponse

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): JellyfinSystemInfoDto

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean? = null,
        @Query("Fields") fields: String? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("Filters") filters: String? = null,
        @Query("ParentId") parentId: String? = null,
        @Query("AlbumIds") albumIds: String? = null,
        @Query("ArtistIds") artistIds: String? = null,
        @Query("AlbumArtistIds") albumArtistIds: String? = null,
        @Query("Genres") genres: String? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
    ): JellyfinItemsResponse

    @GET("Artists/AlbumArtists")
    suspend fun getAlbumArtists(
        @Query("UserId") userId: String,
        @Query("Fields") fields: String? = null,
        @Query("Recursive") recursive: Boolean? = true,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Fields") fields: String? = null,
        @Query("Limit") limit: Int? = null,
    ): List<JellyfinBaseItemDto>

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    ): JellyfinBaseItemDto

    @GET("Items/{id}/InstantMix")
    suspend fun getInstantMix(
        @Path("id") id: String,
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int? = null,
        @Query("Fields") fields: String? = null,
    ): JellyfinItemsResponse

    @GET("MusicGenres")
    suspend fun getMusicGenres(
        @Query("UserId") userId: String,
        @Query("Recursive") recursive: Boolean? = true,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
    ): JellyfinItemsResponse

    @POST("Playlists")
    suspend fun createPlaylist(
        @Body request: CreateJellyfinPlaylistRequest,
    ): CreateJellyfinPlaylistResponse

    @GET("Playlists/{playlistId}/Items")
    suspend fun getPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("UserId") userId: String,
        @Query("Fields") fields: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
    ): JellyfinItemsResponse

    @POST("Playlists/{playlistId}/Items")
    suspend fun addPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("Ids") ids: String,
        @Query("UserId") userId: String,
    )

    @DELETE("Playlists/{playlistId}/Items")
    suspend fun removePlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("EntryIds") entryIds: String,
    )

    @DELETE("Items/{itemId}")
    suspend fun deleteItem(
        @Path("itemId") itemId: String,
    )

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun markFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    )

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun unmarkFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    )

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body info: JellyfinPlaybackStartInfo,
    )

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body info: JellyfinPlaybackProgressInfo,
    )

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body info: JellyfinPlaybackStopInfo,
    )

    @GET("Audio/{itemId}/Lyrics")
    suspend fun getLyrics(
        @Path("itemId") itemId: String,
    ): JellyfinLyricsDto

    @POST("Library/Refresh")
    suspend fun refreshLibrary()
}
