package com.feishin.mobile.ui.search

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.PlayingColors
import com.feishin.mobile.ui.components.resolveSectionHeaderSpec
import com.feishin.mobile.ui.components.resolveSubSectionHeaderSpec
import com.feishin.mobile.ui.theme.VusicSpacing
import com.feishin.mobile.ui.theme.VusicTypography
import com.feishin.mobile.ui.theme.toMaterialColorScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11.1 Section A unit tests:
 * 1. Verifies Home (`SectionHeader`) and Search (`SubSectionHeader` / `SectionHeader`)
 *    resolve identical typography, color, and spacing tokens across Dark/Light static
 *    and all 9 dynamic artwork fixtures.
 * 2. Source-check assertions confirming all Search section headers route through
 *    the shared header component with zero `.sp` font-size literals and no fixed row height.
 */
class SearchHeaderStyleTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate source file for $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun homeAndSearchHeaderTokens_areIdentical_inStaticDarkAndLight() {
        for (dark in listOf(true, false)) {
            val cs = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark).toMaterialColorScheme()
            val homeSpec = resolveSectionHeaderSpec(typography = VusicTypography, colorScheme = cs)
            val searchSpec = resolveSubSectionHeaderSpec(typography = VusicTypography, colorScheme = cs)

            assertEquals("Static dark=$dark spec equality", homeSpec, searchSpec)
            assertEquals(VusicTypography.titleLarge.fontSize, searchSpec.textStyle.fontSize)
            assertEquals(FontWeight.Medium, searchSpec.textStyle.fontWeight)
            assertEquals(VusicTypography.titleLarge.lineHeight, searchSpec.textStyle.lineHeight)
            assertEquals(cs.primary, searchSpec.color)
            assertEquals(VusicSpacing.ScreenHorizontal, searchSpec.paddingStart)
            assertEquals(VusicSpacing.SectionGap, searchSpec.paddingTop)
            assertEquals(8.dp, searchSpec.paddingEnd)
            assertEquals(VusicSpacing.HeaderToContent, searchSpec.paddingBottom)
        }
    }

    @Test
    fun homeAndSearchHeaderTokens_areIdentical_acrossDynamicFixturesDarkAndLight() {
        assertTrue(ArtworkFixtures.ALL.size >= 9)
        for (dark in listOf(true, false)) {
            for ((name, artwork) in ArtworkFixtures.ALL) {
                val cs = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!.toMaterialColorScheme()
                val homeSpec = resolveSectionHeaderSpec(typography = VusicTypography, colorScheme = cs)
                val searchSpec = resolveSubSectionHeaderSpec(typography = VusicTypography, colorScheme = cs)

                assertEquals("$name dark=$dark spec equality", homeSpec, searchSpec)
                assertEquals("$name dark=$dark fontSize", VusicTypography.titleLarge.fontSize, searchSpec.textStyle.fontSize)
                assertEquals("$name dark=$dark fontWeight", FontWeight.Medium, searchSpec.textStyle.fontWeight)
                assertEquals("$name dark=$dark lineHeight", VusicTypography.titleLarge.lineHeight, searchSpec.textStyle.lineHeight)
                assertEquals("$name dark=$dark primary color", cs.primary, searchSpec.color)
                assertEquals(VusicSpacing.ScreenHorizontal, searchSpec.paddingStart)
                assertEquals(VusicSpacing.SectionGap, searchSpec.paddingTop)
                assertEquals(8.dp, searchSpec.paddingEnd)
                assertEquals(VusicSpacing.HeaderToContent, searchSpec.paddingBottom)
            }
        }
    }

    @Test
    fun searchScreenAndComponents_routeAllSearchHeadersThroughSharedHeader_withNoSpLiterals() {
        val searchSource = resolveSourceFile("src/main/java/com/feishin/mobile/ui/search/SearchScreen.kt")
            .readText()
            .replace("\r\n", "\n")
        val componentsSource = resolveSourceFile("src/main/java/com/feishin/mobile/ui/components/Components.kt")
            .readText()
            .replace("\r\n", "\n")

        val spLiteralRegex = Regex("\\.sp\\b")
        assertFalse(
            "SearchScreen.kt must not contain hardcoded .sp font sizes",
            spLiteralRegex.containsMatchIn(searchSource),
        )

        val headerSlice = componentsSource
            .substringAfter("data class SectionHeaderSpec(")
            .substringBefore("@Composable\nfun AlbumCard(")
        assertTrue("Components.kt header slice must be non-empty", headerSlice.isNotBlank())
        assertFalse(
            "Components.kt headers must not contain hardcoded .sp font sizes",
            spLiteralRegex.containsMatchIn(headerSlice),
        )
        assertFalse(
            "Components.kt headers must not fix row height (fontScale=1.3 clipping guard)",
            Regex("\\.height\\(").containsMatchIn(headerSlice),
        )
        assertTrue(
            "SectionHeader must resolve spec via resolveSectionHeaderSpec",
            headerSlice.contains("resolveSectionHeaderSpec("),
        )
        val subSectionSlice = headerSlice
            .substringAfter("fun SubSectionHeader(")
            .substringBefore("fun ScreenTitleRow(")
        assertTrue(
            "SubSectionHeader must delegate to SectionHeader",
            subSectionSlice.contains("SectionHeader("),
        )

        assertTrue(
            "Recent searches must use shared SectionHeader with Clear all action",
            Regex("""SectionHeader\(\s*title\s*=\s*"Recent searches",\s*actionText\s*=\s*"Clear all",\s*onAction\s*=\s*onClearAll""")
                .containsMatchIn(searchSource),
        )
        for (header in listOf("Top result", "Songs", "Albums", "Artists", "Playlists")) {
            assertTrue(
                "SearchScreen.kt must route \"$header\" through shared SectionHeader",
                searchSource.contains("SectionHeader(\"$header\")"),
            )
        }
        assertFalse(
            "SearchScreen.kt must not call legacy SubSectionHeader",
            searchSource.contains("SubSectionHeader("),
        )
    }
}
