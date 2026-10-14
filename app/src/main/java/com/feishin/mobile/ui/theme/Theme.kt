package com.feishin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

    /** AMOLED theme: true black backgrounds for OLED panels. */
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF101010)
    val AmoledSurfaceHigh = Color(0xFF1C1C1C)
}

/**
 * Control colors in one place, so every switch / checkbox / radio / slider /
 * chip in the app draws its boundaries identically. Off states are visible
 * (>= 3:1 against the card) instead of nearly invisible.
 */
object VusicControlColors {
    /** Track behind an off switch: #3A3A3A with a lighter outline. */
    val SwitchOffTrack = Color(0xFF3A3A3A)
    val SwitchOffOutline = Color(0xFF8A8A8A)
    val SwitchOffThumb = Color(0xFFB8B8B8)
    /** On: white track with a dark thumb (matches the accent play button). */
    val SwitchOnTrack = Color.White
    val SwitchOnThumb = Color(0xFF141414)
    /** Disabled states must read clearly dimmer than off. */
    val DisabledTrack = Color(0xFF232323)
    val DisabledOutline = Color(0xFF4A4A4A)
    val DisabledThumb = Color(0xFF5A5A5A)

    val CheckboxOffOutline = Color(0xFF8A8A8A)
    val RadioOffOutline = Color(0xFF8A8A8A)
    /** Inactive slider track: visible against a #2A2A2A card. */
    val SliderInactiveTrack = Color(0xFF6E6E6E)
    val SliderThumb = Color.White
    /** Unselected chip fill / text. */
    val ChipOffFill = Color(0xFF2A2A2A)
    val ChipOffText = Color(0xFFEDEDED)
}

/**
 * One place for vertical rhythm. Sections own their own spacing so no screen has
 * to hand-tune gaps.
 */
object VusicSpacing {
    /** Gap between two Home sections. */
    val SectionGap = 32.dp

    /** Gap between a section header and the content under it. */
    val HeaderToContent = 12.dp

    /** Standard screen side padding. */
    val ScreenHorizontal = 16.dp

    /** Bottom padding so content clears the mini player + nav bar. */
    val ContentBottom = 160.dp

    // ---- Settings/card rhythm (used by every settings surface) ----

    /** Inner padding of a settings card. */
    val CardPadding = 16.dp

    /** Vertical padding of a settings row (row min height 56dp with content). */
    val RowVertical = 12.dp

    /** Minimum height of a settings row. */
    val RowMinHeight = 56.dp

    /** Gap between a row title and its description. */
    val TitleToDescription = 4.dp

    /** Gap between two cards. */
    val CardGap = 16.dp

    /** Space above a section label. */
    val SectionTop = 24.dp

    /** Space below a section label. */
    val SectionBottom = 8.dp

    /** Minimum gap between a row's text and its trailing control. */
    val ControlGap = 16.dp

    /** Section header spacing shared by Home/Search/Settings. */
    val SubHeaderTop = 24.dp
    val SubHeaderBottom = 10.dp
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

private val Amoled = darkColorScheme(
    primary = VusicColors.Accent,
    onPrimary = VusicColors.OnAccent,
    secondary = VusicColors.SecondaryText,
    onSecondary = Color.White,
    background = VusicColors.AmoledBackground,
    onBackground = Color.White,
    surface = VusicColors.AmoledSurface,
    onSurface = Color.White,
    surfaceVariant = VusicColors.AmoledSurfaceHigh,
    onSurfaceVariant = VusicColors.SecondaryText,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = VusicColors.AmoledBackground,
    surfaceContainer = VusicColors.AmoledSurface,
    surfaceContainerHigh = VusicColors.AmoledSurfaceHigh,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1C1C1C),
)

@Composable
fun FeishinTheme(dark: Boolean, amoled: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when {
        amoled -> Amoled
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Shared Material3 control colors so every switch, checkbox, radio and slider in
 * the app has the same visible boundaries (WCAG non-text contrast on dark).
 */
object VusicControlDefaults {
    @Composable
    fun switchColors() = androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = VusicControlColors.SwitchOnThumb,
        checkedTrackColor = VusicControlColors.SwitchOnTrack,
        checkedBorderColor = VusicControlColors.SwitchOnTrack,
        uncheckedThumbColor = VusicControlColors.SwitchOffThumb,
        uncheckedTrackColor = VusicControlColors.SwitchOffTrack,
        uncheckedBorderColor = VusicControlColors.SwitchOffOutline,
        disabledCheckedThumbColor = VusicControlColors.DisabledThumb,
        disabledCheckedTrackColor = VusicControlColors.DisabledTrack,
        disabledUncheckedThumbColor = VusicControlColors.DisabledThumb,
        disabledUncheckedTrackColor = VusicControlColors.DisabledTrack,
        disabledUncheckedBorderColor = VusicControlColors.DisabledOutline,
    )

    @Composable
    fun sliderColors() = androidx.compose.material3.SliderDefaults.colors(
        thumbColor = VusicControlColors.SliderThumb,
        activeTrackColor = VusicControlColors.SliderThumb,
        inactiveTrackColor = VusicControlColors.SliderInactiveTrack,
        activeTickColor = Color.Transparent,
        inactiveTickColor = VusicControlColors.SliderInactiveTrack,
        disabledThumbColor = VusicControlColors.DisabledThumb,
        disabledActiveTrackColor = VusicControlColors.DisabledTrack,
        disabledInactiveTrackColor = VusicControlColors.DisabledTrack,
    )

    @Composable
    fun checkboxColors() = androidx.compose.material3.CheckboxDefaults.colors(
        checkedColor = VusicControlColors.SwitchOnTrack,
        checkmarkColor = VusicControlColors.SwitchOnThumb,
        uncheckedColor = VusicControlColors.CheckboxOffOutline,
    )

    @Composable
    fun radioColors() = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = VusicControlColors.SwitchOnTrack,
        unselectedColor = VusicControlColors.RadioOffOutline,
    )
}
