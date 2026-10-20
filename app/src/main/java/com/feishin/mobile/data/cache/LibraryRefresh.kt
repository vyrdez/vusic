package com.feishin.mobile.data.cache

import com.feishin.mobile.core.common.CachePolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The library's refresh story, in one place: which reads may be served from
 * cache, when a background revalidation is allowed, and who hears about it.
 *
 * The repository writes through here; the Library and the detail screens
 * listen. A screen never polls: it renders whatever the cache holds, then the
 * one event this bus emits says "a fresher payload landed, read it again".
 *
 * Kept off the repository interface so the domain API stays about music, and
 * so test fakes do not have to grow a refresh story they never use.
 */
@Singleton
class LibraryRefresh @Inject constructor() {

    val policy = CachePolicy()

    private val _refreshed = MutableSharedFlow<String>(extraBufferCapacity = 128)
    /** Cache keys whose payload was just refreshed from the server. */
    val refreshed: SharedFlow<String> = _refreshed.asSharedFlow()

    private val _stale = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Cache keys whose background refresh failed; old data is still on screen. */
    val stale: SharedFlow<String> = _stale.asSharedFlow()

    private val _active = MutableStateFlow(0)
    /**
     * Network refreshes running over data that is already on screen. Drives the
     * small inline indicator: zero means nothing is loading.
     */
    val active: StateFlow<Int> = _active.asStateFlow()

    fun beginRefresh() {
        _active.update { it + 1 }
    }

    fun endRefresh() {
        _active.update { maxOf(0, it - 1) }
    }

    fun emitRefreshed(key: String) {
        _refreshed.tryEmit(key)
    }

    fun emitStale(key: String) {
        _stale.tryEmit(key)
    }

    /** Pull-to-refresh: every read in the next few seconds waits for the network. */
    fun invalidateAll() = policy.forceAll()

    /** One entry, e.g. after starring: its next read must not serve the cache. */
    fun invalidate(key: String) = policy.force(key)
}
