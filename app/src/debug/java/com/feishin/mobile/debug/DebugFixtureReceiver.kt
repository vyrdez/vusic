package com.feishin.mobile.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.feishin.mobile.core.common.VLog
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.DebugCoverOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only receiver triggered via:
 * `adb shell am broadcast -a com.feishin.mobile.DEBUG_FIXTURE --es name white_blue_logo`
 *
 * Publishes the named fixture to [DebugCoverOverride] immediately and writes all
 * fixture PNGs to `filesDir/fixtures/` on an IO thread.
 */
class DebugFixtureReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_FIXTURE) return

        val name = intent.getStringExtra(EXTRA_NAME)
        when {
            name == null -> Unit
            name.isEmpty() || name == NAME_CLEAR -> DebugCoverOverride.set(null)
            else -> {
                val pixels = ArtworkFixtures.ALL[name]
                if (pixels != null) {
                    DebugCoverOverride.set(pixels)
                } else {
                    VLog.w(TAG, "Unknown fixture name: $name")
                }
            }
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                DebugFixtureWriter.writeAll(appContext)
            } catch (t: Throwable) {
                VLog.w(TAG, "Failed writing fixture PNGs", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DEBUG_FIXTURE = "com.feishin.mobile.DEBUG_FIXTURE"
        const val EXTRA_NAME = "name"
        private const val NAME_CLEAR = "clear"
        private const val TAG = "DebugFixtureReceiver"
    }
}
