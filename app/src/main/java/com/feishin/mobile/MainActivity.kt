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
import androidx.lifecycle.lifecycleScope
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.ui.components.NowPlayingBarTint
import com.feishin.mobile.ui.navigation.AppNav
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.theme.FeishinTheme
import com.feishin.mobile.ui.theme.resolveDark
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* media notification still works */ }

    @Inject lateinit var settingsStore: SettingsStore

    /** Set in [onStop]; used to expire the Now Playing tab after a long pause. */
    private var lastBackgroundedAtMs = 0L

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

    override fun onStart() {
        super.onStart()
        val since = lastBackgroundedAtMs
        lastBackgroundedAtMs = 0L
        // P10.1 D2: after five minutes away, Now Playing reopens on Song. Cold
        // launch needs no reset: the tab value is session-only in SettingsStore,
        // so a new process already starts on Song. Shorter pauses keep the tab.
        if (since > 0L && System.currentTimeMillis() - since >= TAB_EXPIRY_MS) {
            lifecycleScope.launch { settingsStore.setNowPlayingLyricsMode(false) }
        }
    }

    override fun onStop() {
        lastBackgroundedAtMs = System.currentTimeMillis()
        super.onStop()
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

    private companion object {
        /** Background time after which the Now Playing tab returns to Song. */
        const val TAB_EXPIRY_MS = 5 * 60 * 1000L
    }
}
