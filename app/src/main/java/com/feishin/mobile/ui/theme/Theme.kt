package com.feishin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Vusic design system: near-black background, elevated gray surfaces, white
 * primary text, gray secondary text, white as the main accent (selected chips
 * are white pills with black text, the play button is a white circle).
 */
object VusicColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceHigh = Color(0xFF2A2A2A)
    val SecondaryText = Color(0xFF9AA0A6)
    val Accent = Color.White
    val OnAccent = Color.Black
}

private val Dark = darkColorScheme(
    primary = VusicColors.Accent,
    onPrimary = VusicColors.OnAccent,
    secondary = VusicColors.SecondaryText,
    onSecondary = Color.White,
    background = VusicColors.Background,
    onBackground = Color.White,
    surface = VusicColors.Surface,
    onSurface = Color.White,
    surfaceVariant = VusicColors.SurfaceHigh,
    onSurfaceVariant = VusicColors.SecondaryText,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = VusicColors.Background,
    surfaceContainer = VusicColors.Surface,
    surfaceContainerHigh = VusicColors.SurfaceHigh,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
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
