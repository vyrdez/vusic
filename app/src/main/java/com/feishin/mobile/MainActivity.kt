package com.feishin.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.store.ThemeMode
import com.feishin.mobile.ui.FeishinNav
import com.feishin.mobile.ui.MainViewModel
import com.feishin.mobile.ui.theme.FeishinTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val main: MainViewModel = hiltViewModel()
            val settings by main.settings.flow.collectAsState(initial = null)
            val dark = when (settings?.theme ?: ThemeMode.DARK) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            FeishinTheme(dark = dark) {
                FeishinNav(main = main)
            }
        }
    }
}
