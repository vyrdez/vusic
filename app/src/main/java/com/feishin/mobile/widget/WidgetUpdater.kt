package com.feishin.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.feishin.mobile.MainActivity
import com.feishin.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [WidgetState] into every pinned widget. No polling: the app calls
 * [update] on player-state changes while alive, and [PlayerService] calls it
 * from its ExoPlayer listener while only the service is alive.
 *
 * Suspend: cover art is fetched through the shared Coil loader (memory/disk
 * cached, self-signed-aware) on the caller's thread (IO).
 */
object WidgetUpdater {
    const val ACTION_PREV = "com.feishin.mobile.widget.PREV"
    const val ACTION_NEXT = "com.feishin.mobile.widget.NEXT"
    const val ACTION_TOGGLE = "com.feishin.mobile.widget.TOGGLE"
    const val ACTION_FAVORITE = "com.feishin.mobile.widget.FAVORITE"
    const val OPEN_NOW_PLAYING = "com.feishin.mobile.OPEN_NOW_PLAYING"

    fun widgetIds(context: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
    }

    suspend fun update(
        context: Context,
        state: WidgetState,
        imageLoader: ImageLoader,
        coverUrlFor: (String?) -> String?,
    ) {
        val app = context.applicationContext
        WidgetStateStore(app).save(state)
        val ids = widgetIds(app)
        if (ids.isEmpty()) return
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val url = coverUrlFor(state.coverArtId) ?: return@runCatching null
                val req = ImageRequest.Builder(app).data(url).size(256).build()
                imageLoader.execute(req).drawable?.toBitmap()?.let { rounded(it) }
            }.getOrNull()
        }
        val mgr = AppWidgetManager.getInstance(app)
        for (id in ids) {
            val width = runCatching {
                mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            }.getOrDefault(0)
            val layout = WidgetLayoutPicker.forMinWidthDp(width)
            val views = RemoteViews(
                app.packageName,
                if (layout == WidgetLayout.LARGE) R.layout.widget_now_playing_large
                else R.layout.widget_now_playing,
            )
            bind(app, views, state, bitmap, id)
            mgr.updateAppWidget(id, views)
        }
    }

    /** Re-renders all widgets from the persisted state (provider entry point). */
    suspend fun refreshFromStore(
        context: Context,
        imageLoader: ImageLoader,
        coverUrlFor: (String?) -> String?,
    ) = update(context, WidgetStateStore(context.applicationContext).load(), imageLoader, coverUrlFor)

    private fun bind(
        context: Context,
        views: RemoteViews,
        state: WidgetState,
        bitmap: Bitmap?,
        widgetId: Int,
    ) {
        views.setTextViewText(R.id.w_title, state.title)
        views.setTextViewText(R.id.w_artist, state.artist)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.w_cover, bitmap)
        } else {
            views.setImageViewResource(R.id.w_cover, R.drawable.art_placeholder)
        }
        views.setContentDescription(R.id.w_cover, "Album art for ${state.title}")
        views.setContentDescription(R.id.w_title, state.title)
        views.setContentDescription(R.id.w_artist, state.artist)

        val playing = state.hasQueue && state.isPlaying
        views.setImageViewResource(
            R.id.w_play,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setContentDescription(R.id.w_play, if (playing) "Pause" else "Play")
        views.setImageViewResource(
            R.id.w_fav,
            if (state.starred) R.drawable.ic_widget_fav else R.drawable.ic_widget_fav_off,
        )
        views.setContentDescription(
            R.id.w_fav,
            if (state.starred) "Remove from favorites" else "Add to favorites",
        )
        views.setContentDescription(R.id.w_prev, "Previous track")
        views.setContentDescription(R.id.w_next, "Next track")

        // Empty state: hide the transport, whole card opens the app.
        val transportVisible = if (state.hasQueue) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.w_prev, transportVisible)
        views.setViewVisibility(R.id.w_play, transportVisible)
        views.setViewVisibility(R.id.w_next, transportVisible)
        views.setViewVisibility(R.id.w_fav, transportVisible)

        views.setOnClickPendingIntent(R.id.w_cover, openApp(context, widgetId))
        views.setOnClickPendingIntent(R.id.w_title, openApp(context, widgetId))
        views.setOnClickPendingIntent(R.id.w_prev, action(context, ACTION_PREV, 11, widgetId))
        views.setOnClickPendingIntent(R.id.w_play, openApp(context, widgetId).takeIf { !state.hasQueue }
            ?: action(context, ACTION_TOGGLE, 12, widgetId))
        views.setOnClickPendingIntent(R.id.w_next, action(context, ACTION_NEXT, 13, widgetId))
        views.setOnClickPendingIntent(R.id.w_fav, action(context, ACTION_FAVORITE, 14, widgetId))
    }

    private fun openApp(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(OPEN_NOW_PLAYING)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(android.net.Uri.parse("vusic://nowplaying/$widgetId"))
        return PendingIntent.getActivity(
            context, 100 + widgetId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, action: String, code: Int, widgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(android.net.Uri.parse("vusic://widget/$action/$widgetId"))
        return PendingIntent.getBroadcast(
            context, code * 1000 + widgetId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun placeholderBitmap(context: Context): Bitmap {
        val d = context.getDrawable(R.drawable.art_placeholder)
        val bmp = d?.toBitmap(256, 256)
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.art_placeholder)
            ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        return rounded(bmp)
    }

    /** Rounded corners for cover art (RemoteViews ImageViews cannot clip). */
    fun rounded(bitmap: Bitmap): Bitmap {
        val radius = bitmap.width * 0.12f
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(
            android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            radius, radius, paint,
        )
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }
}

/** Broadcast extras shared by the widget provider and the action receiver. */
object WidgetExtras {
    fun widgetId(intent: Intent): Int =
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    fun optionsWidthDp(mgr: AppWidgetManager, widgetId: Int): Int =
        runCatching {
            val options: Bundle = mgr.getAppWidgetOptions(widgetId)
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        }.getOrDefault(0)
}
