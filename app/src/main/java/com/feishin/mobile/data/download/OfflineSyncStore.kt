package com.feishin.mobile.data.download

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.data.db.PendingActionDao
import com.feishin.mobile.data.db.PendingActionEntity
import com.feishin.mobile.domain.repo.MusicDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kind of write that can be deferred while offline. */
enum class PendingActionType { SCROBBLE, STAR, UNSTAR, STAR_ALBUM, UNSTAR_ALBUM, STAR_ARTIST, UNSTAR_ARTIST }

/**
 * Queues server writes (scrobbles, stars) that happened while offline and replays
 * them in order once the network returns, keeping the original timestamp so
 * scrobble history lands on the right day.
 */
@Singleton
class OfflineSyncStore @Inject constructor(
    private val dao: PendingActionDao,
    private val repo: MusicDataRepository,
) {
    val pendingCount: Flow<Int> = dao.observeCount()

    val hasPending: Flow<Boolean> = dao.observeCount().map { it > 0 }

    suspend fun queueScrobble(songId: String, submission: Boolean, timeMs: Long = System.currentTimeMillis()) {
        dao.insert(
            PendingActionEntity(
                type = PendingActionType.SCROBBLE.name,
                targetId = songId,
                submission = submission,
                timeMs = timeMs,
            ),
        )
    }

    suspend fun queueStar(songId: String, starred: Boolean) {
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR.name else PendingActionType.UNSTAR.name,
                targetId = songId,
                timeMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun queueStarAlbum(albumId: String, starred: Boolean) {
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR_ALBUM.name else PendingActionType.UNSTAR_ALBUM.name,
                targetId = albumId,
            ),
        )
    }

    suspend fun queueStarArtist(artistId: String, starred: Boolean) {
        dao.insert(
            PendingActionEntity(
                type = if (starred) PendingActionType.STAR_ARTIST.name else PendingActionType.UNSTAR_ARTIST.name,
                targetId = artistId,
            ),
        )
    }

    /**
     * Replays everything in timestamp order. Stops at the first failure so the
     * queue stays ordered and nothing is lost; the caller retries later.
     * @return the number of actions successfully replayed.
     */
    suspend fun flush(): Int {
        var done = 0
        for (action in dao.all()) {
            val ok = replay(action)
            if (!ok) break
            dao.delete(action.id)
            done++
        }
        return done
    }

    private suspend fun replay(action: PendingActionEntity): Boolean {
        val type = runCatching { PendingActionType.valueOf(action.type) }.getOrNull() ?: return true
        val result: Result<Unit> = when (type) {
            PendingActionType.SCROBBLE -> repo.scrobbleAt(action.targetId, action.timeMs, action.submission)
            PendingActionType.STAR -> repo.starTrack(action.targetId, true)
            PendingActionType.UNSTAR -> repo.starTrack(action.targetId, false)
            PendingActionType.STAR_ALBUM -> repo.starAlbum(action.targetId, true)
            PendingActionType.UNSTAR_ALBUM -> repo.starAlbum(action.targetId, false)
            PendingActionType.STAR_ARTIST -> repo.starArtist(action.targetId, true)
            PendingActionType.UNSTAR_ARTIST -> repo.starArtist(action.targetId, false)
        }
        return result is Result.Ok
    }

    suspend fun clear() = dao.clear()
}
