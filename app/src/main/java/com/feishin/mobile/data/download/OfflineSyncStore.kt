package com.feishin.mobile.data.download

import com.feishin.mobile.core.common.GlobalId
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.db.PendingActionEntity
import com.feishin.mobile.domain.repo.BackendRegistry
import com.feishin.mobile.domain.repo.MusicDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kind of write that can be deferred while offline. */
enum class PendingActionType { SCROBBLE, STAR, UNSTAR, STAR_ALBUM, UNSTAR_ALBUM, STAR_ARTIST, UNSTAR_ARTIST }

/**
 * Queues server writes (scrobbles, stars) that happened while offline and replays
 * them per profile in order once the network returns, keeping the original
 * timestamp so scrobble history lands on the right day.
 */
@Singleton
class OfflineSyncStore private constructor(
    private val dao: PendingActionDao,
    private val repo: MusicDataRepository,
    private val registryProvider: () -> BackendRegistry?,
) {
    @Inject
    constructor(
        dao: PendingActionDao,
        repo: MusicDataRepository,
        backendRegistry: dagger.Lazy<BackendRegistry>,
    ) : this(
        dao = dao,
        repo = repo,
        registryProvider = { backendRegistry.get() },
    )

    internal constructor(
        dao: PendingActionDao,
        repo: MusicDataRepository,
        backendRegistry: BackendRegistry? = null,
    ) : this(
        dao = dao,
        repo = repo,
        registryProvider = { backendRegistry },
    )
    val pendingCount: Flow<Int> = dao.observeCount()

    val hasPending: Flow<Boolean> = dao.observeCount().map { it > 0 }

    suspend fun queueScrobble(songId: String, submission: Boolean, timeMs: Long = System.currentTimeMillis()) {
        if (GlobalId.isDeviceLocal(songId)) return
        dao.insert(
            PendingActionEntity(
                type = PendingActionType.SCROBBLE.name,
                targetId = songId,
                submission = submission,
                timeMs = timeMs,
                profileId = profileIdFor(songId),
            ),
        )
    }

    suspend fun queueStar(songId: String, starred: Boolean) {
        if (GlobalId.isDeviceLocal(songId)) return
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR.name else PendingActionType.UNSTAR.name,
                targetId = songId,
                timeMs = System.currentTimeMillis(),
                profileId = profileIdFor(songId),
            ),
        )
    }

    suspend fun queueStarAlbum(albumId: String, starred: Boolean) {
        if (GlobalId.isDeviceLocal(albumId)) return
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR_ALBUM.name else PendingActionType.UNSTAR_ALBUM.name,
                targetId = albumId,
                profileId = profileIdFor(albumId),
            ),
        )
    }

    suspend fun queueStarArtist(artistId: String, starred: Boolean) {
        if (GlobalId.isDeviceLocal(artistId)) return
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR_ARTIST.name else PendingActionType.UNSTAR_ARTIST.name,
                targetId = artistId,
                profileId = profileIdFor(artistId),
            ),
        )
    }

    /**
     * Replays pending actions independently per profile in timestamp/id order.
     * A failure on one profile pauses only that profile's queue so remaining
     * online profiles continue draining cleanly.
     * @return the number of actions successfully replayed across all profiles.
     */
    suspend fun flush(): Int {
        var done = 0
        for (profileId in dao.distinctProfileIds()) {
            for (action in dao.pendingForProfile(profileId)) {
                val ok = replay(action)
                if (!ok) break
                dao.delete(action.id)
                done++
            }
        }
        return done
    }

    private suspend fun replay(action: PendingActionEntity): Boolean {
        val type = runCatching { PendingActionType.valueOf(action.type) }.getOrNull() ?: return true
        val backend = registryProvider()?.get(action.profileId)
        val result: Result<Unit> = if (backend != null) {
            when (type) {
                PendingActionType.SCROBBLE -> backend.scrobbleAt(action.targetId, action.timeMs, action.submission)
                PendingActionType.STAR -> backend.starTrack(action.targetId, true)
                PendingActionType.UNSTAR -> backend.starTrack(action.targetId, false)
                PendingActionType.STAR_ALBUM -> backend.starAlbum(action.targetId, true)
                PendingActionType.UNSTAR_ALBUM -> backend.starAlbum(action.targetId, false)
                PendingActionType.STAR_ARTIST -> backend.starArtist(action.targetId, true)
                PendingActionType.UNSTAR_ARTIST -> backend.starArtist(action.targetId, false)
            }
        } else {
            when (type) {
                PendingActionType.SCROBBLE -> repo.scrobbleAt(action.targetId, action.timeMs, action.submission)
                PendingActionType.STAR -> repo.starTrack(action.targetId, true)
                PendingActionType.UNSTAR -> repo.starTrack(action.targetId, false)
                PendingActionType.STAR_ALBUM -> repo.starAlbum(action.targetId, true)
                PendingActionType.UNSTAR_ALBUM -> repo.starAlbum(action.targetId, false)
                PendingActionType.STAR_ARTIST -> repo.starArtist(action.targetId, true)
                PendingActionType.UNSTAR_ARTIST -> repo.starArtist(action.targetId, false)
            }
        }
        return result is Result.Ok
    }

    suspend fun clearForProfile(profileId: String) = dao.deleteByProfile(profileId)

    suspend fun clear() = dao.clear()

    private fun profileIdFor(id: String): String = GlobalId.profileIdOrNull(id) ?: DEFAULT_PROFILE_ID

    companion object {
        const val DEFAULT_PROFILE_ID = "p1"
    }
}
