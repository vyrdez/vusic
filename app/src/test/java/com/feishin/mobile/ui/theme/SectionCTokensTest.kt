package com.feishin.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.Contrast
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.core.ui.VusicDynamicScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 Section C unit tests:
 * 1. Central [VusicTypography] weights (Medium for headers/titles/labels, Normal for body/list items).
 * 2. Contrast guards (>= 4.5:1 text, >= 3.0:1 icons/controls) in Dark & Light, static & dynamic
 *    across all 9 [ArtworkFixtures.ALL] for headers, nav bar pill/label, and settings elements.
 * 3. Source-code check ensuring zero `Color(0x` hardcoded hexes in `TopLevelTitleBar.kt`,
 *    `SettingsComponents.kt`, and `Nav.kt` (`AppNavigationBar`).
 */
class SectionCTokensTest {

    private fun Color.toPackedArgb(): Long {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        return Contrast.argb(a, r, g, b)
    }

    private fun assertRatio(label: String, fg: Long, bg: Long, minRatio: Double) {
        val actual = Contrast.ratio(fg, bg)
        assertTrue(
            "$label contrast $actual < $minRatio (fg=0x${fg.toString(16)} bg=0x${bg.toString(16)})",
            actual >= minRatio,
        )
    }

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate source file for $relativePath from ${File(".").absolutePath}")
    }

    // ---- 1. Central Typography Weights -------------------------------------

    @Test
    fun vusicTypography_headersAndSectionTitlesUseMedium_bodyUsesNormal() {
        // Screen titles, section headers, and labels use FontWeight.Medium (500)
        assertEquals(FontWeight.Medium, VusicTypography.headlineSmall.fontWeight)
        assertEquals(FontWeight.Medium, VusicTypography.titleLarge.fontWeight)
        assertEquals(FontWeight.Medium, VusicTypography.titleMedium.fontWeight)
        assertEquals(FontWeight.Medium, VusicTypography.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, VusicTypography.labelMedium.fontWeight)

        // List item titles and body copy stay at FontWeight.Normal (400)
        assertEquals(FontWeight.Normal, VusicTypography.bodyLarge.fontWeight)
        assertEquals(FontWeight.Normal, VusicTypography.bodyMedium.fontWeight)
        assertEquals(FontWeight.Normal, VusicTypography.bodySmall.fontWeight)
    }

    // ---- 2. Token-level Contrast Across Static & All 9 Dynamic Fixtures ----

    private fun assertSectionCContrastForScheme(tag: String, s: VusicDynamicScheme) {
        val cs = s.toMaterialColorScheme()
        val tokens = s.toVusicColorTokens()

        // Header / section label color (colorScheme.primary / VusicColors.Accent / Header)
        // on background and surface must clear 4.5:1 text contrast.
        val headerColor = cs.primary.toPackedArgb()
        assertEquals(s.primary, headerColor)
        assertEquals(s.primary, tokens.accent.toPackedArgb())
        assertRatio("$tag header(primary)/background", headerColor, s.background, 4.5)
        assertRatio("$tag header(primary)/surface", headerColor, s.surface, 4.5)
        assertRatio("$tag header(primary)/surfaceContainerHigh", headerColor, s.surfaceContainerHigh, 4.5)

        // Nav bar: selected pill is secondaryContainer; icon & label use onSecondaryContainer.
        // Label sits below the pill on the nav bar container (background / surface), so
        // onSecondaryContainer must clear 4.5:1 on BOTH secondaryContainer AND surface/background!
        val navPill = cs.secondaryContainer.toPackedArgb()
        val navSelectedContent = cs.onSecondaryContainer.toPackedArgb()
        assertRatio("$tag nav onSecondaryContainer/secondaryContainer (pill)", navSelectedContent, navPill, 4.5)
        assertRatio("$tag nav onSecondaryContainer/surface (bar)", navSelectedContent, s.surface, 4.5)
        assertRatio("$tag nav onSecondaryContainer/background (bar)", navSelectedContent, s.background, 4.5)

        // Settings icon tile: onSecondaryContainer icon on secondaryContainer tile container
        assertRatio("$tag settings tile icon/container", s.onSecondaryContainer, s.secondaryContainer, 4.5)

        // Switch thumb on track:
        // Checked: SwitchOnThumb (onPrimary) on SwitchOnTrack (primary) >= 4.5:1 (clears 3.0:1 control bar)
        assertRatio("$tag switch checked thumb/track", s.onPrimary, s.primary, 3.0)
        // Unchecked: switchOffThumb on switchOffTrack >= 3.0:1, switchOffOutline on surface/card >= 3.0:1
        assertRatio("$tag switch unchecked thumb/track", s.switchOffThumb, s.switchOffTrack, 3.0)
        assertRatio("$tag switch unchecked outline/surface", s.switchOffOutline, s.surface, 3.0)
        assertRatio("$tag switch unchecked outline/card", s.switchOffOutline, s.surfaceContainerHigh, 3.0)

        // Slider active track & thumb (primary) on surface & card (surfaceContainerHigh) >= 3.0:1
        assertRatio("$tag slider activeTrack/surface", s.primary, s.surface, 3.0)
        assertRatio("$tag slider activeTrack/card", s.primary, s.surfaceContainerHigh, 3.0)

        // Dropdown trigger: surfaceContainerHigh fill, onSurface text & chevron, primary expanded border/text
        assertRatio("$tag dropdown trigger text/fill", s.onSurface, s.surfaceContainerHigh, 4.5)
        assertRatio("$tag dropdown trigger text/surface", s.onSurface, s.surface, 4.5)
        assertRatio("$tag dropdown expanded primary/fill", s.primary, s.surfaceContainerHigh, 3.0)
        assertRatio("$tag dropdown popup selected primary/popupBg", s.primary, s.surfaceContainerHigh, 4.5)
        assertRatio("$tag dropdown popup selected primary/surfaceContainer", s.primary, s.surfaceContainer, 4.5)
    }

    @Test
    fun staticSchemes_darkAndLight_meetAllSectionCContrastTargets() {
        for (dark in listOf(true, false)) {
            val scheme = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
            assertSectionCContrastForScheme("static(dark=$dark)", scheme)
        }
    }

    @Test
    fun dynamicSchemes_allNineFixtures_darkAndLight_meetAllSectionCContrastTargets() {
        assertEquals(9, ArtworkFixtures.ALL.size)
        for (dark in listOf(true, false)) {
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val scheme = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!
                assertSectionCContrastForScheme("$name(dark=$dark)", scheme)
            }
        }
    }

    // ---- 3. Source Check: Zero Hardcoded Color(0x...) in Touched Components -

    @Test
    fun touchedComponents_haveZeroHardcodedHexColors() {
        val topLevelTitleBar = resolveSourceFile("src/main/java/com/feishin/mobile/ui/components/TopLevelTitleBar.kt")
            .readText()
        val settingsComponents = resolveSourceFile("src/main/java/com/feishin/mobile/ui/settings/SettingsComponents.kt")
            .readText()
        val navFile = resolveSourceFile("src/main/java/com/feishin/mobile/ui/Nav.kt")
            .readText()

        assertTrue(
            "TopLevelTitleBar.kt must not contain hardcoded Color(0x...)",
            !topLevelTitleBar.contains("Color(0x"),
        )
        assertTrue(
            "SettingsComponents.kt must not contain hardcoded Color(0x...)",
            !settingsComponents.contains("Color(0x"),
        )

        val appNavBarSlice = navFile.substringAfter("private fun AppNavigationBar(")
            .substringBefore("private fun NavHostController.goToTab(")
        assertTrue("AppNavigationBar slice must be non-empty", appNavBarSlice.isNotBlank())
        assertTrue(
            "AppNavigationBar in Nav.kt must not contain hardcoded Color(0x...)",
            !appNavBarSlice.contains("Color(0x"),
        )
    }
}
