package com.feishin.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import coil.ImageLoader
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.core.network.CoverArtUrlProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget (replaces the old static placeholder). Rendering always
 * goes through [WidgetUpdater] from the persisted state, so the widget is
 * meaningful even when the app process is dead; live updates arrive
 * event-driven (player changes in the app, ExoPlayer listener in the service),
 * never by polling.
 */
@AndroidEntryPoint
class NowPlayingWidget : AppWidgetProvider() {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var creds: CredentialsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        scope.launch {
            WidgetUpdater.refreshFromStore(
                context.applicationContext,
                imageLoader,
                coverUrlFor(context.applicationContext),
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        // Resize: re-pick the compact/large layout for the new width.
        onUpdate(context, mgr, intArrayOf(widgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Favorite toggles land here too (broadcast to the receiver first, but
        // the provider refresh keeps the heart in sync if the app is alive).
        if (intent.action == WidgetUpdater.ACTION_FAVORITE) {
            val mgr = AppWidgetManager.getInstance(context)
            onUpdate(context, mgr, WidgetUpdater.widgetIds(context.applicationContext))
        }
    }

    private fun coverUrlFor(context: Context): (String?) -> String? {
        // Artwork URLs are rebuilt the same way the phone builds them (stable
        // salt => Coil disk-cache hits).
        return { coverArtId ->
            if (coverArtId.isNullOrBlank()) null
            else runCatching {
                val c = creds.state.value ?: return@runCatching null
                CoverArtUrlProvider.build(c.serverUrl, c.username, c.password, coverArtId, 256)
            }.getOrNull()
        }
    }
}
