package com.feishin.mobile.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Subsonic wire DTOs. Never exposed to the UI (see domain models + mappers).
 *
 * Quirks handled:
 * - Subsonic servers sometimes return a single object instead of a one-element
 *   array; every list field tolerates both shapes via [SingleOrListSerializer].
 * - Navidrome sends extra fields: unknown keys are ignored (converter config).
 * - Missing optional fields fall back to defaults.
 */
open class SingleOrListSerializer<T>(elementSerializer: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) element else JsonArray(listOf(element))
}

object AlbumDtoListSerializer : SingleOrListSerializer<AlbumDto>(AlbumDto.serializer())
object SongDtoListSerializer : SingleOrListSerializer<SongDto>(SongDto.serializer())
object ArtistDtoListSerializer : SingleOrListSerializer<ArtistDto>(ArtistDto.serializer())
object GenreDtoListSerializer : SingleOrListSerializer<GenreDto>(GenreDto.serializer())
object PlaylistDtoListSerializer : SingleOrListSerializer<PlaylistDto>(PlaylistDto.serializer())
object ArtistIndexDtoListSerializer : SingleOrListSerializer<ArtistIndexDto>(ArtistIndexDto.serializer())

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse(),
)

@Serializable
data class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val error: SubsonicError? = null,
    val albumList: AlbumListDto? = null,
    val albumList2: AlbumList2Dto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val album: AlbumDetailDto? = null,
    val artist: ArtistDetailDto? = null,
    val artists: ArtistsDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDetailDto? = null,
    val starred: StarredDto? = null,
    val starred2: Starred2Dto? = null,
    val genres: GenresDto? = null,
    val lyrics: LyricsDto? = null,
    val lyricsList: LyricsListDto? = null,
    val randomSongs: RandomSongsDto? = null,
    val topSongs: TopSongsDto? = null,
    val similarSongs2: SimilarSongs2Dto? = null,
    val artistInfo2: ArtistInfo2Dto? = null,
    val scanStatus: ScanStatusDto? = null,
    @SerialName("openSubsonicExtensions")
    val openSubsonicExtensions: List<OpenSubsonicExtensionDto>? = null,
)

@Serializable
data class SubsonicError(val code: Int = 0, val message: String = "")

@Serializable
data class AlbumListDto(
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumList2Dto(
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumDto(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Long = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
)

@Serializable
data class AlbumDetailDto(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class SongDto(
    val id: String = "",
    val title: String = "",
    val album: String = "",
    val albumId: String? = null,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long = 0,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val starred: String? = null,
    val path: String = "",
    /** OpenSubsonic ReplayGain block, when the server provides one. */
    val replayGain: ReplayGainDto? = null,
)

/** OpenSubsonic ReplayGain values (dB gain, linear peak). */
@Serializable
data class ReplayGainDto(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
)

@Serializable
data class ArtistsDto(
    @Serializable(with = ArtistIndexDtoListSerializer::class)
    val index: List<ArtistIndexDto> = emptyList(),
)

@Serializable
data class ArtistIndexDto(
    val name: String = "",
    @Serializable(with = ArtistDtoListSerializer::class)
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class ArtistDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
)

@Serializable
data class ArtistDetailDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class SearchResult3Dto(
    @Serializable(with = ArtistDtoListSerializer::class)
    val artist: List<ArtistDto> = emptyList(),
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
    @Serializable(with = PlaylistDtoListSerializer::class)
    val playlist: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistsDto(
    @Serializable(with = PlaylistDtoListSerializer::class)
    val playlist: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
)

@Serializable
data class PlaylistDetailDto(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    @Serializable(with = SongDtoListSerializer::class)
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class StarredDto(
    @Serializable(with = ArtistDtoListSerializer::class)
    val artist: List<ArtistDto> = emptyList(),
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class Starred2Dto(
    @Serializable(with = ArtistDtoListSerializer::class)
    val artist: List<ArtistDto> = emptyList(),
    @Serializable(with = AlbumDtoListSerializer::class)
    val album: List<AlbumDto> = emptyList(),
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class GenresDto(
    @Serializable(with = GenreDtoListSerializer::class)
    val genre: List<GenreDto> = emptyList(),
)

@Serializable
data class GenreDto(
    val name: String = "",
    /** Navidrome/OpenSubsonic puts the genre name in `value` instead of `name`. */
    val value: String = "",
    val songCount: Int = 0,
    val albumCount: Int = 0,
)

@Serializable
data class LyricsDto(val artist: String = "", val title: String = "", val value: String = "")

/** OpenSubsonic structured lyrics (`getLyricsBySongId`). */
@Serializable
data class LyricsListDto(
    val syncedLyrics: SyncedLyricsDto? = null,
    val plainLyrics: PlainLyricsDto? = null,
)

@Serializable
data class SyncedLyricsDto(
    val offset: Long = 0,
    @Serializable(with = SyncedLineDtoListSerializer::class)
    val line: List<SyncedLineDto> = emptyList(),
)

@Serializable
data class SyncedLineDto(val start: Long = 0, val value: String = "")

object SyncedLineDtoListSerializer : SingleOrListSerializer<SyncedLineDto>(SyncedLineDto.serializer())

@Serializable
data class PlainLyricsDto(val value: String = "")

@Serializable
data class RandomSongsDto(
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

/**
 * getTopSongs: Subsonic returns `track` elements; some servers return `song`.
 * Both are accepted and merged.
 */
@Serializable
data class TopSongsDto(
    val artist: String = "",
    @Serializable(with = SongDtoListSerializer::class)
    val track: List<SongDto> = emptyList(),
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class SimilarSongs2Dto(
    @Serializable(with = SongDtoListSerializer::class)
    val song: List<SongDto> = emptyList(),
)

/** startScan / getScanStatus. `scanning` false with a count means idle. */
@Serializable
data class ScanStatusDto(
    val scanning: Boolean = false,
    val count: Int = 0,
    val folderCount: Int? = null,
    val lastScan: String? = null,
)

/** getOpenSubsonicExtensions: one entry per supported extension. */
@Serializable
data class OpenSubsonicExtensionDto(
    val name: String = "",
    val versions: List<Int> = emptyList(),
)

/** getArtistInfo2. Every field is optional; servers differ wildly. */
@Serializable
data class ArtistInfo2Dto(
    val biography: String = "",
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImage: String? = null,
    val mediumImage: String? = null,
    val largeImage: String? = null,
    @Serializable(with = ArtistDtoListSerializer::class)
    val similarArtist: List<ArtistDto> = emptyList(),
)
