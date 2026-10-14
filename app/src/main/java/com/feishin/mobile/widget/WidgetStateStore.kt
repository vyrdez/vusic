package com.feishin.mobile.widget

import android.content.Context
import androidx.core.content.edit

/**
 * Last-known widget state in SharedPreferences (synchronous, safe from
 * broadcasts and the provider): renders the widget when the app is dead,
 * backs the favorite toggle, and seeds the empty state.
 */
class WidgetStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("vusic_widget", Context.MODE_PRIVATE)

    fun load(): WidgetState = WidgetState(
        hasQueue = prefs.getBoolean("hasQueue", false),
        title = prefs.getString("title", null) ?: "Nothing playing",
        artist = prefs.getString("artist", null) ?: "Tap to open Vusic",
        coverArtId = prefs.getString("coverArtId", null),
        trackId = prefs.getString("trackId", null),
        isPlaying = prefs.getBoolean("isPlaying", false),
        starred = prefs.getBoolean("starred", false),
    ).let { s ->
        if (!s.hasQueue) WidgetState.empty() else s
    }

    fun save(state: WidgetState) {
        prefs.edit {
            putBoolean("hasQueue", state.hasQueue)
            putString("title", state.title)
            putString("artist", state.artist)
            putString("coverArtId", state.coverArtId)
            putString("trackId", state.trackId)
            putBoolean("isPlaying", state.isPlaying)
            putBoolean("starred", state.starred)
        }
    }

    /** Flips the persisted favorite flag for [trackId]; returns the new value. */
    fun toggleStarred(trackId: String): Boolean {
        val current = load()
        val next = if (current.trackId == trackId) !current.starred else true
        save(current.copy(trackId = trackId, starred = next, hasQueue = current.hasQueue))
        return next
    }
}
