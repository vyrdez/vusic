package com.feishin.mobile.data.history

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.data.db.LocalAlbumPlayStats
import com.feishin.mobile.data.db.LocalPlayHistoryDao
import com.feishin.mobile.data.db.LocalPlayHistoryEntity
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Local play history store backing non-scrobbling backends (e.g. Google Drive)
 * for recent/frequent albums, Speed dial, and Home mixes.
 */
@Singleton
class LocalHistoryStore @Inject constructor(
    private val dao: LocalPlayHistoryDao,
    json: Json,
) {
    internal constructor(dao: LocalPlayHistoryDao) : this(dao = dao, json = DEFAULT_JSON)

    private val safeJson = Json(json) {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun recordPlay(
        track: Track,
        playedAtMs: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        val profileId = GlobalId.profileIdOrNull(track.id)
            ?: track.sourceProfileId?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PROFILE_ID
        val albumKey = track.albumId.orEmpty()
        val artistKey = track.artistId.orEmpty()
        val trackJson = safeJson.encodeToString(Track.serializer(), track)
        val albumJson = albumKey.takeIf { it.isNotBlank() }?.let { key ->
            safeJson.encodeToString(
                Album.serializer(),
                Album(
                    id = key,
                    name = track.album,
                    artist = track.artist,
                    artistId = track.artistId,
                    coverArt = track.coverArt,
                    songCount = 0,
                    durationSec = track.durationSec,
                    playCount = 1L,
                    year = track.year,
                    genre = track.genre,
                    starred = false,
                    sourceProfileId = profileId,
                    sourceLabel = track.sourceLabel,
                ),
            )
        }
        dao.insert(
            LocalPlayHistoryEntity(
                trackId = track.id,
                profileId = profileId,
                albumKey = albumKey,
                artistKey = artistKey,
                trackJson = trackJson,
                albumJson = albumJson,
                playedAtMs = playedAtMs,
            ),
        )
    }

    suspend fun recentEntries(
        profileId: String? = null,
        limit: Int = 50,
    ): List<LocalPlayHistoryEntity> = withContext(Dispatchers.IO) {
        if (profileId.isNullOrBlank()) {
            dao.recent(limit)
        } else {
            dao.recentForProfile(profileId, limit)
        }
    }

    suspend fun recentTracks(
        profileId: String? = null,
        limit: Int = 50,
    ): List<Track> = recentEntries(profileId = profileId, limit = limit).mapNotNull { entry ->
        runCatching { safeJson.decodeFromString(Track.serializer(), entry.trackJson) }.getOrNull()
    }

    suspend fun recentAlbums(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<LocalAlbumPlayStats> = withContext(Dispatchers.IO) {
        dao.recentAlbumsForProfile(profileId = profileId, limit = limit, offset = offset)
    }

    suspend fun frequentAlbums(
        profileId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<LocalAlbumPlayStats> = withContext(Dispatchers.IO) {
        dao.frequentAlbumsForProfile(profileId = profileId, limit = limit, offset = offset)
    }

    suspend fun clearProfile(profileId: String) = withContext(Dispatchers.IO) {
        dao.clearProfile(profileId)
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = "p1"
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
