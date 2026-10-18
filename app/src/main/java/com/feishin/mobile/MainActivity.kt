package com.feishin.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.ui.components.NowPlayingBarTint
import com.feishin.mobile.ui.navigation.AppNav
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.theme.FeishinTheme
import com.feishin.mobile.ui.theme.resolveDark
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* media notification still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        consumeNowPlayingIntent(intent)
        setContent {
            val main: MainViewModel = hiltViewModel()
            val settings by main.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            // System follows the OS live: isSystemInDarkTheme() is a
            // configuration read, so the activity recomposes (and re-themes)
            // the moment the OS switches, with no restart.
            val dark = resolveDark(settings?.theme, isSystemInDarkTheme())
            // Apply the refresh-rate request live (Auto/Max/Standard), without
            // a restart. Display switches while paused are covered by onResume.
            val rateMode = settings?.refreshRateMode
                ?: com.feishin.mobile.core.ui.RefreshRateMode.AUTO
            LaunchedEffect(rateMode) { applyRefreshRate(rateMode) }
            // Status/navigation bar icons must flip with the theme (dark icons
            // on light backgrounds), including while Now Playing and the queue
            // paint a tinted background. Now Playing overrides the theme when it
            // is on screen, because a tinted surface can be light in the Dark
            // theme (and dark in the Light one) and white icons there would be
            // invisible.
            val npLightBackdrop by NowPlayingBarTint.lightBackdrop
                .collectAsStateWithLifecycle(initialValue = null)
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                val lightBars = npLightBackdrop ?: !dark
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }
            FeishinTheme(dark = dark) {
                AppNav(main = main)
            }
        }
    }

    /** Widget cover tap opens Now Playing (cold start and warm). */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNowPlayingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-assert after display switches (rotation, multi-display, refresh
        // changes by the system) — cheap and idempotent.
        applyRefreshRate(lastRateMode)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRefreshRate(lastRateMode)
    }

    private var lastRateMode = com.feishin.mobile.core.ui.RefreshRateMode.AUTO

    /**
     * Requests the display mode for [mode] without ever changing resolution.
     *
     * All three modes go through the public window hints only:
     * - AUTO asks for the highest same-resolution rate via
     *   `preferredDisplayModeId` and leaves `preferredRefreshRate` unspecified,
     *   so the system keeps full control of the idle rate (touch boost on
     *   interaction, LTPO drop when static).
     * - MAX adds an explicit `preferredRefreshRate` at the peak rate — the
     *   strongest hold request the public API offers.
     * - STANDARD pins both hints at 60 Hz (battery saver).
     * The OS always has the final say (thermal/power overrides apply).
     *
     * Note: `Surface.setFrameRate` exists in the compile SDK (verified via
     * javap) but there is no public accessor for the *activity window's*
     * Surface — it only applies to app-owned SurfaceViews — so it cannot be
     * used here. Re-applied on resume/config-change (rotation, multi-display,
     * system-initiated rate switches) and live whenever the setting changes.
     */
    private fun applyRefreshRate(mode: com.feishin.mobile.core.ui.RefreshRateMode) {
        lastRateMode = mode
        val dm = getSystemService(android.hardware.display.DisplayManager::class.java) ?: return
        val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return
        val modes = display.supportedModes.map {
            com.feishin.mobile.core.ui.DisplayModeInfo(
                it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate,
            )
        }
        val cur = display.mode
        val targetId = com.feishin.mobile.core.ui.RefreshRate.selectModeId(
            modes, cur.physicalWidth, cur.physicalHeight, cur.modeId, mode,
        )
        val targetRate = modes.firstOrNull { it.id == targetId }?.refreshRate ?: 0f
        val attrs = window.attributes
        var changed = false
        if (attrs.preferredDisplayModeId != targetId) {
            attrs.preferredDisplayModeId = targetId
            changed = true
        }
        // 0 = no preference (Auto): the system owns the idle rate. Max and
        // Standard state their rate explicitly; Standard always asks for exactly
        // 60, even when the nearest mode is a higher rate (90/120-only panel).
        val wantRate = com.feishin.mobile.core.ui.RefreshRate.requestedRate(mode, targetRate)
        if (attrs.preferredRefreshRate != wantRate) {
            attrs.preferredRefreshRate = wantRate
            changed = true
        }
        if (changed) window.attributes = attrs
    }

    private fun consumeNowPlayingIntent(intent: android.content.Intent?) {
        if (intent?.action == "com.feishin.mobile.OPEN_NOW_PLAYING") {
            com.feishin.mobile.ui.components.NowPlayingNudge.nudge()
            intent.action = null
        }
    }

    /** Android 13+ requires POST_NOTIFICATIONS for the media notification. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
