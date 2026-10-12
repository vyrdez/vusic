package com.feishin.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.feishin.mobile.MainActivity
import com.feishin.mobile.R

class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setOnClickPendingIntent(R.id.widget_title, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
