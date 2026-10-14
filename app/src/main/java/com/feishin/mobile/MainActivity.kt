package com.feishin.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.settings.ThemeMode
import com.feishin.mobile.ui.navigation.AppNav
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.theme.FeishinTheme
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
            val settings by main.settings.flow.collectAsState(initial = null)
            val dark = when (settings?.theme ?: ThemeMode.DARK) {
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val amoled = (settings?.theme ?: ThemeMode.DARK) == ThemeMode.AMOLED
            FeishinTheme(dark = dark, amoled = amoled) {
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
