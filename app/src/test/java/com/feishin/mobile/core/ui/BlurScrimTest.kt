package com.feishin.mobile.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.feishin.mobile.data.settings.BackgroundStyle
import com.feishin.mobile.ui.components.resolveMiniPlayerStyleColors
import com.feishin.mobile.ui.theme.toMaterialColorScheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class BlurScrimTest {

    private fun Color.toPackedArgb(): Long {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        return Contrast.argb(a, r, g, b)
    }

    private fun Color.compositeOver(bgArgb: Long): Long {
        val fgArgb = toPackedArgb()
        return if (alpha >= 0.999f) fgArgb else Contrast.blend(fgArgb, bgArgb, alpha)
    }

    private fun assertContrast(label: String, fgArgb: Long, bgArgb: Long, minRatio: Double) {
        val ratio = Contrast.ratio(fgArgb, bgArgb)
        assertTrue("$label contrast $ratio < $minRatio", ratio >= minRatio)
    }

    private fun averageRgb(pixels: IntArray): Long {
        if (pixels.isEmpty()) return 0xFF808080L
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a > 0) {
                rSum += (px ushr 16) and 0xFF
                gSum += (px ushr 8) and 0xFF
                bSum += px and 0xFF
                count++
            }
        }
        if (count == 0L) return 0xFF808080L
        return Contrast.argb(255, (rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    private fun staticNowPlayingPalette(cs: ColorScheme, dark: Boolean): NowPlayingPalette {
        val bg = cs.background.toPackedArgb()
        val surface = cs.surface.toPackedArgb()
        val surfaceContainer = cs.surfaceContainer.toPackedArgb()
        val primary = cs.primary.toPackedArgb()
        return NowPlayingColors.static(
            background = bg,
            backdropEnd = if (dark) cs.surfaceContainerLowest.toPackedArgb() else surface,
            onBackground = cs.onBackground.toPackedArgb(),
            onSurface = cs.onSurface.toPackedArgb(),
            onSurfaceVariant = cs.onSurfaceVariant.toPackedArgb(),
            surface = surface,
            surfaceContainer = surfaceContainer,
            primary = primary,
            onPrimary = cs.onPrimary.toPackedArgb(),
            primaryContainer = cs.primaryContainer.toPackedArgb(),
            onPrimaryContainer = cs.onPrimaryContainer.toPackedArgb(),
            track = PlayingColors.ringTrack(surface, primary),
        )
    }

    @Test
    fun averageLuminance_emptyArrayReturnsHalf() {
        assertEquals(0.5f, BlurScrim.averageLuminance(intArrayOf()), 0.001f)
    }

    @Test
    fun averageLuminance_blackWhiteAndGray() {
        val black = IntArray(16) { 0xFF000000.toInt() }
        val white = IntArray(16) { 0xFFFFFFFF.toInt() }
        val midGray = IntArray(16) { 0xFF777777.toInt() }

        assertEquals(0.0f, BlurScrim.averageLuminance(black), 0.001f)
        assertEquals(1.0f, BlurScrim.averageLuminance(white), 0.001f)

        val grayLum = BlurScrim.averageLuminance(midGray)
        assertTrue("midGray luminance in (0.15, 0.25): $grayLum", grayLum in 0.15f..0.25f)
    }

    @Test
    fun scrimAlpha_respectsClampedBounds() {
        for (step in -5..25) {
            val l = step / 20f
            val darkAlpha = BlurScrim.scrimAlpha(l, isDark = true)
            val lightAlpha = BlurScrim.scrimAlpha(l, isDark = false)
            val topDark = scrimAlpha(l, isDark = true)
            val topLight = scrimAlpha(l, isDark = false)
            assertEquals(darkAlpha, topDark, 1e-6f)
            assertEquals(lightAlpha, topLight, 1e-6f)
            assertTrue(
                "dark alpha out of bounds for L=$l: $darkAlpha",
                darkAlpha in BlurScrim.DARK_MIN_ALPHA..BlurScrim.DARK_MAX_ALPHA,
            )
            assertTrue(
                "light alpha out of bounds for L=$l: $lightAlpha",
                lightAlpha in BlurScrim.LIGHT_MIN_ALPHA..BlurScrim.LIGHT_MAX_ALPHA,
            )
        }
    }

    @Test
    fun scrimAlpha_isMonotonicAcrossLuminance() {
        // Dark theme: brighter artwork => stronger scrim (non-decreasing with L).
        var prevDark = BlurScrim.scrimAlpha(0f, isDark = true)
        for (i in 1..100) {
            val l = i / 100f
            val cur = BlurScrim.scrimAlpha(l, isDark = true)
            assertTrue("dark scrim must be non-decreasing at L=$l ($cur < $prevDark)", cur >= prevDark - 1e-6f)
            prevDark = cur
        }
        assertTrue(
            "dark scrim strictly stronger on white than black",
            BlurScrim.scrimAlpha(1f, isDark = true) > BlurScrim.scrimAlpha(0f, isDark = true),
        )

        // Light theme: darker artwork => stronger light scrim (non-increasing with L).
        var prevLight = BlurScrim.scrimAlpha(0f, isDark = false)
        for (i in 1..100) {
            val l = i / 100f
            val cur = BlurScrim.scrimAlpha(l, isDark = false)
            assertTrue("light scrim must be non-increasing at L=$l ($cur > $prevLight)", cur <= prevLight + 1e-6f)
            prevLight = cur
        }
        assertTrue(
            "light scrim strictly stronger on black than white",
            BlurScrim.scrimAlpha(0f, isDark = false) > BlurScrim.scrimAlpha(1f, isDark = false),
        )
    }

    @Test
    fun scrimAlpha_guaranteesWcagTextContrastAcrossAllLuminances() {
        val darkText = 0xFFFFFFFFL // Theme.kt Dark onBackground / onSurface
        val darkScrim = 0xFF0F0F0FL // Theme.kt DarkBackground
        val lightText = 0xFF0B0F14L // Theme.kt Light onBackground / onSurface
        val lightScrim = 0xFFF7F9FBL // Theme.kt Light background

        for (i in 0..100) {
            val l = i / 100f
            // Grayscale color whose WCAG relative luminance is ~l (via CIELAB L* or sRGB gamma).
            val srgbChannel = if (l <= 0.0031308f) {
                (12.92f * l * 255f).toInt()
            } else {
                ((1.055 * Math.pow(l.toDouble(), 1.0 / 2.4) - 0.055) * 255.0).toInt()
            }.coerceIn(0, 255)
            val artArgb = Contrast.argb(255, srgbChannel, srgbChannel, srgbChannel)
            val measuredL = Contrast.luminance(artArgb).toFloat()

            // Dark theme: blend darkScrim at scrimAlpha(measuredL, true) over artArgb
            val aDark = BlurScrim.scrimAlpha(measuredL, isDark = true)
            val darkSurface = Contrast.blend(darkScrim, artArgb, aDark)
            val darkRatio = Contrast.ratio(darkText, darkSurface)
            assertTrue(
                "dark contrast < 4.5 at L=$measuredL (alpha=$aDark): ratio=$darkRatio",
                darkRatio >= 4.5,
            )

            // Light theme: blend lightScrim at scrimAlpha(measuredL, false) over artArgb
            val aLight = BlurScrim.scrimAlpha(measuredL, isDark = false)
            val lightSurface = Contrast.blend(lightScrim, artArgb, aLight)
            val lightRatio = Contrast.ratio(lightText, lightSurface)
            assertTrue(
                "light contrast < 4.5 at L=$measuredL (alpha=$aLight): ratio=$lightRatio",
                lightRatio >= 4.5,
            )
        }
    }

    @Test
    fun boxBlur_smoothsSharpContrastWithoutChangingDimensions() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            if (idx % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val blurred = ArtworkBlurCache.boxBlur(pixels, w, h, radius = 3, passes = 3)
        assertEquals(w * h, blurred.size)
        // Far left stays dark, far right stays light, center boundary is blended mid-gray.
        val leftLum = Contrast.luminance(blurred[8 * w + 0].toLong() and 0xFFFFFFFFL)
        val midLum = Contrast.luminance(blurred[8 * w + (w / 2)].toLong() and 0xFFFFFFFFL)
        val rightLum = Contrast.luminance(blurred[8 * w + (w - 1)].toLong() and 0xFFFFFFFFL)
        assertTrue("left < mid < right ($leftLum, $midLum, $rightLum)", leftLum < midLum && midLum < rightLum)
    }

    @Test
    fun boxBlur_legacyFallback_preservesAlphaBoundsSmoothsEdgesAndIsDeterministic() {
        val w = 32
        val h = 32
        // High-frequency 1px checkerboard: max adjacent luminance delta = 1.0.
        val checkerboard = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        val blurredA = ArtworkBlurCache.boxBlur(
            checkerboard,
            w,
            h,
            radius = ArtworkBlurCache.LEGACY_BLUR_RADIUS_PX,
            passes = 3,
        )
        val blurredB = ArtworkBlurCache.boxBlur(
            checkerboard,
            w,
            h,
            radius = ArtworkBlurCache.LEGACY_BLUR_RADIUS_PX,
            passes = 3,
        )

        assertTrue("blurred output must be non-empty", blurredA.isNotEmpty())
        assertEquals("output bounds must match input grid", w * h, blurredA.size)
        assertArrayEquals("boxBlur must be deterministic", blurredA, blurredB)

        var maxAdjacentDelta = 0.0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = blurredA[y * w + x]
                val alpha = (px ushr 24) and 0xFF
                assertEquals("alpha must remain opaque 0xFF at ($x,$y)", 0xFF, alpha)
                val lum = Contrast.luminance(px.toLong() and 0xFFFFFFFFL)
                if (x + 1 < w) {
                    val rightLum = Contrast.luminance(blurredA[y * w + (x + 1)].toLong() and 0xFFFFFFFFL)
                    maxAdjacentDelta = max(maxAdjacentDelta, abs(lum - rightLum))
                }
                if (y + 1 < h) {
                    val downLum = Contrast.luminance(blurredA[(y + 1) * w + x].toLong() and 0xFFFFFFFFL)
                    maxAdjacentDelta = max(maxAdjacentDelta, abs(lum - downLum))
                }
            }
        }
        assertTrue(
            "boxBlur must smooth high-frequency edges (maxAdjacentDelta=$maxAdjacentDelta)",
            maxAdjacentDelta < 0.15,
        )

        // Transparent-edge fixture also keeps full opaque alpha and exact bounds.
        val transparentFixture = ArtworkFixtures.transparentEdge()
        val blurredTransparent = ArtworkBlurCache.boxBlur(
            transparentFixture.argb,
            transparentFixture.width,
            transparentFixture.height,
            radius = ArtworkBlurCache.LEGACY_BLUR_RADIUS_PX,
            passes = 3,
        )
        assertEquals(transparentFixture.width * transparentFixture.height, blurredTransparent.size)
        assertTrue(blurredTransparent.all { ((it ushr 24) and 0xFF) == 0xFF })
    }

    @Test
    fun allBackgroundStyles_miniPlayerNowPlayingQueue_meetContrastAcrossFixturesAndStatic() {
        for (dark in listOf(true, false)) {
            val staticDyn = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
            val staticCs = staticDyn.toMaterialColorScheme()
            val staticNpPalette = staticNowPlayingPalette(staticCs, dark)

            for ((name, artwork) in ArtworkFixtures.ALL) {
                val dyn = PlayingColors.schemeFromPixels(
                    artwork.argb,
                    artwork.width,
                    artwork.height,
                    dark = dark,
                )!!
                val dynCs = dyn.toMaterialColorScheme()
                val dynPlayingScheme = dyn.toPlayingScheme()
                val dynNpPalette = NowPlayingColors.fromScheme(dynPlayingScheme)

                val fixtureAvgLum = BlurScrim.averageLuminance(artwork.argb)
                val fixtureAvgColor = averageRgb(artwork.argb)
                val fixtureScrimAlpha = BlurScrim.scrimAlpha(fixtureAvgLum, isDark = dark)
                val worstArt = if (dark) 0xFFFFFFFFL else 0xFF000000L
                val worstScrimAlpha = BlurScrim.scrimAlpha(if (dark) 1f else 0f, isDark = dark)

                for (dynamicOn in listOf(true, false)) {
                    val tag = "$name(dark=$dark,dynamic=$dynamicOn)"
                    val scheme = if (dynamicOn) dynPlayingScheme else null
                    val cs = if (dynamicOn) dynCs else staticCs
                    val npPalette = if (dynamicOn) dynNpPalette else staticNpPalette

                    // 1) MiniPlayer across DEFAULT, GRADIENT (top & bottom), BLUR (scrim, worst, fixture avg)
                    for (style in BackgroundStyle.entries) {
                        val mini = resolveMiniPlayerStyleColors(
                            style = style,
                            scheme = scheme,
                            colorScheme = cs,
                            isDark = dark,
                        )
                        val miniInk = mini.inkColor.toPackedArgb()
                        val miniSubInk = mini.subtleInkColor.toPackedArgb()
                        val miniBar = mini.ringBarColor.toPackedArgb()

                        when (style) {
                            BackgroundStyle.DEFAULT -> {
                                val bg = mini.bottomColor.toPackedArgb()
                                assertContrast("$tag mini DEFAULT ink", miniInk, bg, 4.5)
                                assertContrast("$tag mini DEFAULT subInk", miniSubInk, bg, 4.5)
                                assertContrast("$tag mini DEFAULT ringBar", miniBar, bg, 3.0)
                                assertContrast("$tag mini DEFAULT ringTrack", miniBar, mini.ringTrackColor.compositeOver(bg), 3.0)
                            }
                            BackgroundStyle.GRADIENT -> {
                                val top = mini.topColor.toPackedArgb()
                                val bottom = mini.bottomColor.toPackedArgb()
                                assertContrast("$tag mini GRADIENT top ink", miniInk, top, 4.5)
                                assertContrast("$tag mini GRADIENT bottom ink", miniInk, bottom, 4.5)
                                assertContrast("$tag mini GRADIENT top subInk", miniSubInk, top, 4.5)
                                assertContrast("$tag mini GRADIENT bottom subInk", miniSubInk, bottom, 4.5)
                                assertContrast("$tag mini GRADIENT top ringBar", miniBar, top, 3.0)
                                assertContrast("$tag mini GRADIENT bottom ringBar", miniBar, bottom, 3.0)
                                assertContrast("$tag mini GRADIENT ringTrack", miniBar, mini.ringTrackColor.compositeOver(bottom), 3.0)
                            }
                            BackgroundStyle.BLUR -> {
                                val scrim = mini.scrimColor.toPackedArgb()
                                val worstBg = Contrast.blend(scrim, worstArt, worstScrimAlpha)
                                val fixtureBg = Contrast.blend(scrim, fixtureAvgColor, fixtureScrimAlpha)
                                for ((bgLabel, bg) in listOf("scrim" to scrim, "worstBlur" to worstBg, "fixtureBlur" to fixtureBg)) {
                                    assertContrast("$tag mini BLUR $bgLabel ink", miniInk, bg, 4.5)
                                    assertContrast("$tag mini BLUR $bgLabel subInk", miniSubInk, bg, 4.5)
                                    assertContrast("$tag mini BLUR $bgLabel ringBar", miniBar, bg, 3.0)
                                }
                                assertContrast("$tag mini BLUR ringTrack", miniBar, mini.ringTrackColor.compositeOver(scrim), 3.0)
                            }
                        }
                    }

                    // 2) NowPlaying across DEFAULT, GRADIENT (top & bottom), BLUR
                    val npSurface = if (dynamicOn) npPalette.backdropBottom else cs.surface.toPackedArgb()
                    val npText = if (dynamicOn) npPalette.content else cs.onSurface.toPackedArgb()
                    val npIcon = if (dynamicOn) npPalette.accent else cs.primary.toPackedArgb()
                    assertContrast("$tag np DEFAULT onSurface", npText, npSurface, 4.5)
                    assertContrast("$tag np DEFAULT primary", npIcon, npSurface, 3.0)
                    assertContrast("$tag np GRADIENT top text", npPalette.content, npPalette.backdropTop, 4.5)
                    assertContrast("$tag np GRADIENT bottom text", npPalette.content, npPalette.backdropBottom, 4.5)
                    assertContrast("$tag np GRADIENT top primary", npPalette.accent, npPalette.backdropTop, 3.0)
                    assertContrast("$tag np GRADIENT bottom primary", npPalette.accent, npPalette.backdropBottom, 3.0)
                    val npWorstBlur = Contrast.blend(npSurface, worstArt, worstScrimAlpha)
                    val npFixtureBlur = Contrast.blend(npSurface, fixtureAvgColor, fixtureScrimAlpha)
                    assertContrast("$tag np BLUR worst onSurface", npText, npWorstBlur, 4.5)
                    assertContrast("$tag np BLUR fixture onSurface", npText, npFixtureBlur, 4.5)
                    assertContrast("$tag np BLUR worst primary", npIcon, npWorstBlur, 3.0)
                    assertContrast("$tag np BLUR fixture primary", npIcon, npFixtureBlur, 3.0)

                    // 3) QueueSheet across DEFAULT, GRADIENT (top & bottom), BLUR
                    val qSurface = if (dynamicOn) npPalette.sheetBottom else cs.surface.toPackedArgb()
                    val qText = if (dynamicOn) npPalette.sheetContent else cs.onSurface.toPackedArgb()
                    val qIcon = if (dynamicOn) npPalette.accent else cs.primary.toPackedArgb()
                    assertContrast("$tag queue DEFAULT onSurface", qText, qSurface, 4.5)
                    assertContrast("$tag queue DEFAULT primary", qIcon, qSurface, 3.0)
                    assertContrast("$tag queue GRADIENT top text", npPalette.sheetContent, npPalette.sheetTop, 4.5)
                    assertContrast("$tag queue GRADIENT bottom text", npPalette.sheetContent, npPalette.sheetBottom, 4.5)
                    assertContrast("$tag queue GRADIENT top primary", npPalette.accent, npPalette.sheetTop, 3.0)
                    assertContrast("$tag queue GRADIENT bottom primary", npPalette.accent, npPalette.sheetBottom, 3.0)
                    val qWorstBlur = Contrast.blend(qSurface, worstArt, worstScrimAlpha)
                    val qFixtureBlur = Contrast.blend(qSurface, fixtureAvgColor, fixtureScrimAlpha)
                    assertContrast("$tag queue BLUR worst onSurface", qText, qWorstBlur, 4.5)
                    assertContrast("$tag queue BLUR fixture onSurface", qText, qFixtureBlur, 4.5)
                    assertContrast("$tag queue BLUR worst primary", qIcon, qWorstBlur, 3.0)
                    assertContrast("$tag queue BLUR fixture primary", qIcon, qFixtureBlur, 3.0)
                }
            }
        }
    }

    @Test
    fun artworkAlpha_staysWithinBoundsAcrossFullLuminanceRange() {
        for (dark in listOf(true, false)) {
            for (step in -5..25) {
                val l = step / 20f
                val alpha = BlurScrim.artworkAlpha(l, isDark = dark)
                assertTrue(
                    "artworkAlpha out of [${BlurScrim.ARTWORK_MIN_ALPHA}, ${BlurScrim.ARTWORK_MAX_ALPHA}] for dark=$dark, L=$l: $alpha",
                    alpha in BlurScrim.ARTWORK_MIN_ALPHA..BlurScrim.ARTWORK_MAX_ALPHA,
                )
            }
        }
    }

    @Test
    fun resolveStack_baseAlphaAlwaysOpaqueAcrossRolesThemesAndFixtures() {
        for (dark in listOf(true, false)) {
            val staticCs = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark).toMaterialColorScheme()
            for ((_, artwork) in ArtworkFixtures.ALL) {
                val dyn = PlayingColors.schemeFromPixels(artwork.argb, artwork.width, artwork.height, dark = dark)!!
                val scheme = dyn.toPlayingScheme()
                val lum = BlurScrim.averageLuminance(artwork.argb)
                for (role in BlurSurfaceRole.entries) {
                    val dynSpec = BlurScrim.resolveStack(
                        role = role,
                        avgLuminance = lum,
                        isDark = dark,
                        baseColorArgb = if (role == BlurSurfaceRole.MINI_PLAYER) scheme.miniContainer else scheme.surfaceBottom,
                        accentTintArgb = scheme.container,
                    )
                    val staticSpec = BlurScrim.resolveStack(
                        role = role,
                        avgLuminance = lum,
                        isDark = dark,
                        baseColorArgb = staticCs.surface.toPackedArgb(),
                        accentTintArgb = staticCs.primaryContainer.toPackedArgb(),
                    )
                    assertEquals(1.0f, dynSpec.baseAlpha, 0f)
                    assertEquals(1.0f, staticSpec.baseAlpha, 0f)
                    assertEquals(255, Contrast.alpha(dynSpec.baseColorArgb))
                    assertEquals(255, Contrast.alpha(staticSpec.baseColorArgb))
                    assertTrue(dynSpec.artworkAlpha in BlurScrim.ARTWORK_MIN_ALPHA..BlurScrim.ARTWORK_MAX_ALPHA)
                    assertTrue(staticSpec.artworkAlpha in BlurScrim.ARTWORK_MIN_ALPHA..BlurScrim.ARTWORK_MAX_ALPHA)
                }
            }
        }
    }

    @Test
    fun resolveStack_usesUniformScrimPreservingSpatialArtworkAcrossAllRoles() {
        for (dark in listOf(true, false)) {
            val base = if (dark) 0xFF121212L else 0xFFF4F6F9L
            val accent = if (dark) 0xFF28323EL else 0xFFD4E5F7L
            for (lum in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val expectedScrimAlpha = BlurScrim.scrimAlpha(lum, dark)
                for (role in BlurSurfaceRole.entries) {
                    val spec = BlurScrim.resolveStack(role, lum, dark, base, accent)
                    assertTrue(
                        "$role dark=$dark lum=$lum artworkAlpha (${spec.artworkAlpha}) must be in [0.75, 1.0]",
                        spec.artworkAlpha in 0.75f..1.0f,
                    )
                    assertTrue(
                        "$role dark=$dark lum=$lum scrim delta must be <= 0.05f (${spec.scrimStartAlpha} vs ${spec.scrimEndAlpha})",
                        abs(spec.scrimStartAlpha - spec.scrimEndAlpha) <= 0.05f,
                    )
                    assertEquals(expectedScrimAlpha, spec.scrimStartAlpha, 1e-6f)
                    assertEquals(expectedScrimAlpha, spec.scrimEndAlpha, 1e-6f)
                    assertEquals(base, spec.scrimStartArgb)
                    assertEquals(base, spec.scrimEndArgb)
                }
            }
        }
    }

    @Test
    fun resolveStack_splitWhiteTopPurpleBottomArtwork_preservesSpatialColorsAndContrast() {
        val topWhite = 0xFFFFFFFFL
        val bottomPurple = 0xFF9C4A88L
        val splitPixels = IntArray(64 * 64) { idx ->
            if (idx / 64 < 32) topWhite.toInt() else bottomPurple.toInt()
        }
        val avgLum = BlurScrim.averageLuminance(splitPixels)

        for (dark in listOf(true, false)) {
            val base = if (dark) 0xFF121212L else 0xFFF7F9FBL
            val text = if (dark) 0xFFFFFFFFL else 0xFF0B0F14L
            val icon = if (dark) 0xFFD0C4D8L else 0xFF3E3548L
            for (role in BlurSurfaceRole.entries) {
                val stack = BlurScrim.resolveStack(role, avgLum, dark, base)
                val topBg = BlurScrim.compositeLayerColor(
                    stack.baseColorArgb, topWhite, stack.artworkAlpha, stack.scrimStartArgb, stack.scrimStartAlpha,
                )
                val bottomBg = BlurScrim.compositeLayerColor(
                    stack.baseColorArgb, bottomPurple, stack.artworkAlpha, stack.scrimEndArgb, stack.scrimEndAlpha,
                )
                assertTrue(
                    "$role dark=$dark top and bottom must preserve spatial luminance difference",
                    Contrast.luminance(topBg) > Contrast.luminance(bottomBg) + 0.02,
                )
                assertTrue(
                    "$role dark=$dark bottom must preserve purple chroma (red > green)",
                    Contrast.red(bottomBg) > Contrast.green(bottomBg),
                )
                assertContrast("$role dark=$dark split top text", text, topBg, 4.5)
                assertContrast("$role dark=$dark split bottom text", text, bottomBg, 4.5)
                assertContrast("$role dark=$dark split top icon", icon, topBg, 3.0)
                assertContrast("$role dark=$dark split bottom icon", icon, bottomBg, 3.0)
            }
        }
    }

    @Test
    fun resolveStack_fullOpaqueStackWorstCaseContrastAcrossFixturesAndStatic() {
        for (dark in listOf(true, false)) {
            val staticDyn = PlayingColors.buildMaterialScheme(seedArgb = null, dark = dark)
            val staticCs = staticDyn.toMaterialColorScheme()
            val staticNpPalette = staticNowPlayingPalette(staticCs, dark)

            for ((name, artwork) in ArtworkFixtures.ALL) {
                val dyn = PlayingColors.schemeFromPixels(artwork.argb, artwork.width, artwork.height, dark = dark)!!
                val dynCs = dyn.toMaterialColorScheme()
                val dynPlayingScheme = dyn.toPlayingScheme()
                val dynNpPalette = NowPlayingColors.fromScheme(dynPlayingScheme)

                val fixtureAvgLum = BlurScrim.averageLuminance(artwork.argb)
                val fixtureAvgColor = averageRgb(artwork.argb)
                val worstLum = if (dark) 1f else 0f
                val worstArt = if (dark) 0xFFFFFFFFL else 0xFF000000L

                for (dynamicOn in listOf(true, false)) {
                    val tag = "$name(dark=$dark,dynamic=$dynamicOn)"
                    val scheme = if (dynamicOn) dynPlayingScheme else null
                    val cs = if (dynamicOn) dynCs else staticCs
                    val npPalette = if (dynamicOn) dynNpPalette else staticNpPalette

                    val scenarios = listOf(
                        Triple("worst", worstLum, worstArt),
                        Triple("fixture", fixtureAvgLum, fixtureAvgColor),
                    )

                    // 1) PLAYER stack
                    val npBase = if (dynamicOn) npPalette.backdropBottom else cs.surface.toPackedArgb()
                    val npText = if (dynamicOn) npPalette.content else cs.onSurface.toPackedArgb()
                    val npIcon = if (dynamicOn) npPalette.accent else cs.primary.toPackedArgb()
                    for ((label, lum, art) in scenarios) {
                        val stack = BlurScrim.resolveStack(BlurSurfaceRole.PLAYER, lum, dark, npBase)
                        val topBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimStartArgb, stack.scrimStartAlpha,
                        )
                        val bottomBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimEndArgb, stack.scrimEndAlpha,
                        )
                        assertContrast("$tag stack PLAYER $label top text", npText, topBg, 4.5)
                        assertContrast("$tag stack PLAYER $label bottom text", npText, bottomBg, 4.5)
                        assertContrast("$tag stack PLAYER $label top icon", npIcon, topBg, 3.0)
                        assertContrast("$tag stack PLAYER $label bottom icon", npIcon, bottomBg, 3.0)
                    }

                    // 2) QUEUE stack
                    val qBase = if (dynamicOn) npPalette.sheetBottom else cs.surface.toPackedArgb()
                    val qText = if (dynamicOn) npPalette.sheetContent else cs.onSurface.toPackedArgb()
                    val qIcon = if (dynamicOn) npPalette.accent else cs.primary.toPackedArgb()
                    for ((label, lum, art) in scenarios) {
                        val stack = BlurScrim.resolveStack(BlurSurfaceRole.QUEUE, lum, dark, qBase)
                        val topBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimStartArgb, stack.scrimStartAlpha,
                        )
                        val bottomBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimEndArgb, stack.scrimEndAlpha,
                        )
                        assertContrast("$tag stack QUEUE $label top text", qText, topBg, 4.5)
                        assertContrast("$tag stack QUEUE $label bottom text", qText, bottomBg, 4.5)
                        assertContrast("$tag stack QUEUE $label top icon", qIcon, topBg, 3.0)
                        assertContrast("$tag stack QUEUE $label bottom icon", qIcon, bottomBg, 3.0)
                    }

                    // 3) MINI_PLAYER stack
                    val mini = resolveMiniPlayerStyleColors(BackgroundStyle.BLUR, scheme, cs, isDark = dark)
                    val miniBase = mini.scrimColor.toPackedArgb()
                    val miniAccent = mini.topColor.toPackedArgb()
                    val miniInk = mini.inkColor.toPackedArgb()
                    val miniSubInk = mini.subtleInkColor.toPackedArgb()
                    val miniBar = mini.ringBarColor.toPackedArgb()
                    for ((label, lum, art) in scenarios) {
                        val stack = BlurScrim.resolveStack(BlurSurfaceRole.MINI_PLAYER, lum, dark, miniBase, miniAccent)
                        val leftBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimStartArgb, stack.scrimStartAlpha,
                        )
                        val rightBg = BlurScrim.compositeLayerColor(
                            stack.baseColorArgb, art, stack.artworkAlpha, stack.scrimEndArgb, stack.scrimEndAlpha,
                        )
                        assertContrast("$tag stack MINI $label left ink", miniInk, leftBg, 4.5)
                        assertContrast("$tag stack MINI $label right ink", miniInk, rightBg, 4.5)
                        assertContrast("$tag stack MINI $label left subInk", miniSubInk, leftBg, 4.5)
                        assertContrast("$tag stack MINI $label right subInk", miniSubInk, rightBg, 4.5)
                        assertContrast("$tag stack MINI $label left ringBar", miniBar, leftBg, 3.0)
                        assertContrast("$tag stack MINI $label right ringBar", miniBar, rightBg, 3.0)
                    }
                    assertContrast(
                        "$tag stack MINI ringTrack",
                        miniBar,
                        mini.ringTrackColor.compositeOver(miniBase),
                        3.0,
                    )
                }
            }
        }
    }
}
