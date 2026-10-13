package com.feishin.mobile.data.favorites

import com.feishin.mobile.core.common.Result
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Virtual playlist id for the built-in Favorite songs collection. */
const val FAVORITE_SONGS_PLAYLIST_ID = "__favorites__"

/** Cross-screen playlist events: added tracks refresh covers/lists, star changes sync favorites. */
sealed interface PlaylistEvent {
    data class TracksAdded(val playlistId: String) : PlaylistEvent
    data object FavoritesChanged : PlaylistEvent
}

@Singleton
class PlaylistEvents @Inject constructor() {
    private val _events = MutableSharedFlow<PlaylistEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlaylistEvent> = _events.asSharedFlow()

    fun emit(e: PlaylistEvent) {
        _events.tryEmit(e)
    }
}

/**
 * The built-in Favorite songs collection, backed by the server's starred songs
 * (`getStarred2`) so it stays in sync with Navidrome and across devices.
 * Every track star/unstar goes through here so all screens update reactively.
 */
@Singleton
class FavoritesStore @Inject constructor(
    private val repo: MusicDataRepository,
    private val events: PlaylistEvents,
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    /** True once the collection has been fetched at least once. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun refresh() {
        // A failed fetch must not wipe the known collection: the NP/sheet
        // chips converge to this store, and converging to "nothing" on a
        // transient error would flip favorited songs to unstarred.
        val tracks = (repo.starred() as? Result.Ok)?.value?.tracks
        if (tracks != null) _tracks.value = tracks
        _loaded.value = true
    }

    /**
     * Stars/unstars a track on the server and re-syncs the collection.
     * Callers keep their own optimistic UI with rollback on [Result.Err].
     */
    suspend fun setTrackStarred(id: String, starred: Boolean): Result<Unit> {
        val r = repo.starTrack(id, starred)
        if (r is Result.Ok) {
            refresh()
            events.emit(PlaylistEvent.FavoritesChanged)
        }
        return r
    }
}
