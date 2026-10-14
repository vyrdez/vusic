package com.feishin.mobile.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.feishin.mobile.data.favorites.FavoritesStore
import com.feishin.mobile.playback.PlayerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Widget transport controls. Talks straight to the MediaSession, so everything
 * keeps working with the app UI closed (the service owns playback): the
 * controller bind wakes the service even from a dead process, and the
 * player-driven update paths repaint the widget afterwards.
 */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var favorites: FavoritesStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(
                WidgetUpdater.ACTION_PREV,
                WidgetUpdater.ACTION_NEXT,
                WidgetUpdater.ACTION_TOGGLE,
                WidgetUpdater.ACTION_FAVORITE,
            )
        ) {
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                handle(context.applicationContext, action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String) {
        if (action == WidgetUpdater.ACTION_FAVORITE) {
            toggleFavorite(context)
            return
        }
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val controller = withContext(Dispatchers.Main) {
            MediaController.Builder(context, token).buildAsync()
        }.get(10, TimeUnit.SECONDS)
        try {
            withContext(Dispatchers.Main) {
                when (action) {
                    WidgetUpdater.ACTION_PREV -> controller.seekToPreviousMediaItem()
                    WidgetUpdater.ACTION_NEXT -> controller.seekToNextMediaItem()
                    WidgetUpdater.ACTION_TOGGLE ->
                        if (controller.isPlaying) controller.pause() else controller.play()
                }
            }
        } finally {
            withContext(Dispatchers.Main) { runCatching { controller.release() } }
        }
    }

    /**
     * Favorite toggle from the persisted widget state (the receiver has no
     * player mirror to consult). The star change re-syncs the collection and
     * emits, so the app's reactive push corrects the heart immediately.
     */
    private suspend fun toggleFavorite(context: Context) {
        val store = WidgetStateStore(context)
        val state = store.load()
        val trackId = state.trackId ?: return
        val next = !state.starred
        val result = favorites.setTrackStarred(trackId, next)
        if (result is com.feishin.mobile.core.common.Result.Ok) {
            store.save(state.copy(starred = next))
        }
    }
}
