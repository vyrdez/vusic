package com.feishin.mobile.data.remote.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinBaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)

@Serializable
data class JellyfinBaseItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("AlbumArtists") val albumArtists: List<JellyfinNameIdDto> = emptyList(),
    @SerialName("Artists") val artists: List<String> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<JellyfinNameIdDto> = emptyList(),
    @SerialName("UserData") val userData: JellyfinUserDataDto? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
    @SerialName("ImageTags") val imageTags: JellyfinImageTagsDto? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("RecursiveItemCount") val recursiveItemCount: Int? = null,
    @SerialName("SongCount") val songCount: Int? = null,
    @SerialName("AlbumCount") val albumCount: Int? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Overview") val overview: String? = null,
)

@Serializable
data class JellyfinNameIdDto(
    @SerialName("Name") val name: String = "",
    @SerialName("Id") val id: String = "",
)

@Serializable
data class JellyfinUserDataDto(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("PlayCount") val playCount: Long = 0L,
)

@Serializable
data class JellyfinMediaSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Path") val path: String? = null,
)

@Serializable
data class JellyfinImageTagsDto(
    @SerialName("Primary") val primary: String? = null,
)

@Serializable
data class JellyfinAuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

@Serializable
data class JellyfinAuthResponse(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("ServerId") val serverId: String = "",
    @SerialName("User") val user: JellyfinUserDto = JellyfinUserDto(),
)

@Serializable
data class JellyfinUserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Policy") val policy: JellyfinUserPolicyDto? = null,
)

@Serializable
data class JellyfinUserPolicyDto(
    @SerialName("EnableContentDownloading") val enableContentDownloading: Boolean? = null,
)

@Serializable
data class JellyfinSystemInfoDto(
    @SerialName("Version") val version: String = "",
    @SerialName("ProductName") val productName: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("ServerName") val serverName: String = "",
)

@Serializable
data class JellyfinPlaybackStartInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PositionTicks") val positionTicks: Long = 0L,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
)

@Serializable
data class JellyfinPlaybackProgressInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
)

@Serializable
data class JellyfinPlaybackStopInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("PlaySessionId") val playSessionId: String,
)

@Serializable
data class JellyfinLyricsDto(
    @SerialName("Metadata") val metadata: JellyfinLyricsMetadataDto? = null,
    @SerialName("Lyrics") val lyrics: List<JellyfinLyricLineDto> = emptyList(),
)

@Serializable
data class JellyfinLyricsMetadataDto(
    @SerialName("Artist") val artist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("IsSynced") val isSynced: Boolean? = null,
)

@Serializable
data class JellyfinLyricLineDto(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null,
)

@Serializable
data class CreateJellyfinPlaylistRequest(
    @SerialName("Name") val name: String,
    @SerialName("Ids") val ids: List<String> = emptyList(),
    @SerialName("UserId") val userId: String,
    @SerialName("MediaType") val mediaType: String = "Audio",
)

@Serializable
data class CreateJellyfinPlaylistResponse(
    @SerialName("Id") val id: String = "",
)
