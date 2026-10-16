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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.settings.ThemeMode
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
            // Status/navigation bar icons must flip with the theme (dark icons
            // on light backgrounds), including while Now Playing and the queue
            // paint a tinted background.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
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
