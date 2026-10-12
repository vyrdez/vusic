package com.feishin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF06202B),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF141A22),
    surfaceVariant = Color(0xFF1E2732),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    onBackground = Color(0xFF0B0F14),
    onSurface = Color(0xFF0B0F14),
)

@Composable
fun FeishinTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
