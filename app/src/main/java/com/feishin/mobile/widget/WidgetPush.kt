package com.feishin.mobile.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.ui.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hilt bridge for composables that cannot take the deps as parameters. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDepsEntry {
    fun imageLoader(): ImageLoader
    fun favorites(): FavoritesStore
}

/**
 * Pushes widget updates while the UI is alive: track, play state, queue
 * emptiness and the favorites collection (reactive heart) are all inputs, so
 * every change repaints without polling. The service covers the app-dead path.
 */
@Composable
fun WidgetPushEffect(main: MainViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext
    val entry = remember(app) {
        EntryPointAccessors.fromApplication(app, WidgetDepsEntry::class.java)
    }
    val current by main.player.current.collectAsState()
    val isPlaying by main.player.isPlaying.collectAsState()
    val queue by main.player.queue.collectAsState()
    val favTracks by entry.favorites().tracks.collectAsState()
    val favLoaded by entry.favorites().loaded.collectAsState()
    LaunchedEffect(current?.id, isPlaying, queue.isEmpty(), favTracks, favLoaded) {
        val saved = WidgetStateStore(app).load()
        val state = WidgetStateMapper.map(
            queueEmpty = queue.isEmpty(),
            current = current,
            isPlaying = isPlaying,
            favLoaded = favLoaded,
            favTrackIds = favTracks.map { it.id }.toSet(),
            persistedTrackId = saved.trackId,
            persistedStarred = saved.starred,
        )
        withContext(Dispatchers.IO) {
            runCatching {
                WidgetUpdater.update(app, state, entry.imageLoader()) { id ->
                    main.repo.coverArtUrl(id, 256)
                }
            }
        }
    }
}
