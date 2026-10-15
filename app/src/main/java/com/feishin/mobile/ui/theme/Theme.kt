package com.feishin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** True while the dark scheme is active. Lets control colors pick a variant. */
val LocalVusicDark = staticCompositionLocalOf { true }

/**
 * Vusic design tokens.
 *
 * These used to be hardcoded dark hexes, which is why the Light theme rendered
 * dark: the scheme switched, but every screen painted literal dark colors. They
 * are now @Composable getters onto the active Material scheme, so all 135 call
 * sites became theme-aware without changing a single call site. The dark values
 * are unchanged — Dark looks exactly as it did.
 */
object VusicColors {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val SecondaryText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary

    /** Dark-only literal, kept for the dark scheme declaration below. */
    internal val DarkBackground = Color(0xFF0F0F0F)
    internal val DarkSurface = Color(0xFF1E1E1E)
    internal val DarkSurfaceHigh = Color(0xFF2A2A2A)
    internal val DarkSecondaryText = Color(0xFF9AA0A6)
}

/**
 * Control colors in one place, so every switch / checkbox / radio / slider /
 * chip draws its boundaries identically and visibly (>= 3:1 against its card)
 * in BOTH themes.
 */
object VusicControlColors {
    // ---- dark (unchanged) ----
    private val DarkSwitchOffTrack = Color(0xFF3A3A3A)
    private val DarkSwitchOffOutline = Color(0xFF8A8A8A)
    private val DarkSwitchOffThumb = Color(0xFFB8B8B8)
    private val DarkDisabledTrack = Color(0xFF232323)
    private val DarkDisabledOutline = Color(0xFF4A4A4A)
    private val DarkDisabledThumb = Color(0xFF5A5A5A)
    private val DarkSliderInactive = Color(0xFF6E6E6E)
    private val DarkChipOffFill = Color(0xFF2A2A2A)
    private val DarkChipOffText = Color(0xFFEDEDED)

    // ---- light: same contrast rules on a pale card ----
    private val LightSwitchOffTrack = Color(0xFFD3D6DA)
    private val LightSwitchOffOutline = Color(0xFF5F6368)
    private val LightSwitchOffThumb = Color(0xFF5F6368)
    private val LightDisabledTrack = Color(0xFFE8EAED)
    private val LightDisabledOutline = Color(0xFFBDC1C6)
    private val LightDisabledThumb = Color(0xFFBDC1C6)
    private val LightSliderInactive = Color(0xFF8A9096)
    private val LightChipOffFill = Color(0xFFE7EDF3)
    private val LightChipOffText = Color(0xFF10141A)

    private val dark: Boolean @Composable get() = LocalVusicDark.current

    val SwitchOffTrack: Color @Composable get() = if (dark) DarkSwitchOffTrack else LightSwitchOffTrack
    val SwitchOffOutline: Color @Composable get() = if (dark) DarkSwitchOffOutline else LightSwitchOffOutline
    val SwitchOffThumb: Color @Composable get() = if (dark) DarkSwitchOffThumb else LightSwitchOffThumb
    val DisabledTrack: Color @Composable get() = if (dark) DarkDisabledTrack else LightDisabledTrack
    val DisabledOutline: Color @Composable get() = if (dark) DarkDisabledOutline else LightDisabledOutline
    val DisabledThumb: Color @Composable get() = if (dark) DarkDisabledThumb else LightDisabledThumb
    val SliderInactiveTrack: Color @Composable get() = if (dark) DarkSliderInactive else LightSliderInactive
    val ChipOffFill: Color @Composable get() = if (dark) DarkChipOffFill else LightChipOffFill
    val ChipOffText: Color @Composable get() = if (dark) DarkChipOffText else LightChipOffText
    val CheckboxOffOutline: Color @Composable get() = if (dark) DarkSwitchOffOutline else LightSwitchOffOutline
    val RadioOffOutline: Color @Composable get() = if (dark) DarkSwitchOffOutline else LightSwitchOffOutline

    /** On: the accent (white in dark, deep blue in light) with a contrasting thumb. */
    val SwitchOnTrack: Color @Composable get() = MaterialTheme.colorScheme.primary
    val SwitchOnThumb: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val SliderThumb: Color @Composable get() = MaterialTheme.colorScheme.primary
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

    /**
     * Extra bottom padding inside a scrollable screen.
     *
     * The scaffold already reserves the nav bar, plus the floating mini player
     * and its gap while it is visible, so this is only breathing room — the last
     * item clears the card without floating a whole bar above it.
     */
    val ContentBottom = 24.dp

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

/** Dark: near-black background, elevated gray surfaces, white accent. Unchanged. */
private val Dark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A3F46),
    onPrimaryContainer = Color(0xFFEAF0F7),
    secondary = VusicColors.DarkSecondaryText,
    onSecondary = Color.White,
    background = VusicColors.DarkBackground,
    onBackground = Color.White,
    surface = VusicColors.DarkSurface,
    onSurface = Color.White,
    surfaceVariant = VusicColors.DarkSurfaceHigh,
    onSurfaceVariant = VusicColors.DarkSecondaryText,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = VusicColors.DarkBackground,
    surfaceContainer = VusicColors.DarkSurface,
    surfaceContainerHigh = VusicColors.DarkSurfaceHigh,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
)

/**
 * Light: pale surfaces, the same blue accent used for emphasis, and every token
 * the app reads (the old light scheme was missing most of them, so screens fell
 * back to defaults and looked washed out).
 */
private val Light = lightColorScheme(
    primary = Color(0xFF0B6BA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF48626F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE8FF),
    onSecondaryContainer = Color(0xFF001D33),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF0B0F14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0F14),
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = Color(0xFF3F4A52),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F9),
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE4E8EE),
    outline = Color(0xFF6E7A85),
    outlineVariant = Color(0xFFC3CCD5),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun FeishinTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalVusicDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Maps a stored theme to dark/light. Pure so the System mapping and the AMOLED
 * migration are unit-testable (see ThemeModeTest).
 *
 * @param stored the raw stored value; unknown strings and the removed AMOLED
 *        both resolve to [com.feishin.mobile.data.settings.ThemeMode.DARK].
 */
fun resolveDark(
    stored: com.feishin.mobile.data.settings.ThemeMode?,
    systemDark: Boolean,
): Boolean = when (stored ?: com.feishin.mobile.data.settings.ThemeMode.DARK) {
    com.feishin.mobile.data.settings.ThemeMode.DARK -> true
    com.feishin.mobile.data.settings.ThemeMode.LIGHT -> false
    com.feishin.mobile.data.settings.ThemeMode.SYSTEM -> systemDark
}

/**
 * Normalizes a raw stored theme name: unknown strings fall back to DARK and
 * the removed AMOLED migrates to DARK.
 */
fun migrateStoredTheme(raw: String?): com.feishin.mobile.data.settings.ThemeMode {
    // Compared as a string, not an enum value: AMOLED no longer exists in the
    // enum, so reading it must land on DARK rather than throwing.
    if (raw?.uppercase() == "AMOLED") return com.feishin.mobile.data.settings.ThemeMode.DARK
    return runCatching { com.feishin.mobile.data.settings.ThemeMode.valueOf(raw ?: "DARK") }
        .getOrDefault(com.feishin.mobile.data.settings.ThemeMode.DARK)
}

/**
 * Shared Material3 control colors so every switch, checkbox, radio and slider in
 * the app has the same visible boundaries in both themes.
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
