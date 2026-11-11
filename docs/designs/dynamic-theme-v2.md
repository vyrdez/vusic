# Dynamic Theme v2 — Design Document (Phase 11 Section B)

## Overview & Root Cause
Currently, `rememberPlayingScheme` (`PlayingTheme.kt:84-88`) extracts artwork colors via `androidx.palette.graphics.Palette`, preferring `getVibrantColor` before `getDominantColor`, and `PlayingColors.schemeFor` (`PlayingColors.kt:96-101`) rejects any seed with HSL saturation `< 0.12f` (`GRAY_SATURATION`), falling back to `fallbackSeedArgb` (`MaterialTheme.colorScheme.primary`, `#0B6BA8` in Light) or `primaryContainer` (`#3A3F46` in Dark). Consequently, a mostly white or neutral cover with a tiny blue badge has its dominant white/gray area ignored in favor of the vibrant blue swatch—or if completely neutral, falls back to the blue/slate theme accent—painting the MiniPlayer and Now Playing accent blue instead of matching the artwork. Additionally, dynamic colors are currently scoped only to `LocalPlayingColors` rather than driving the root `MaterialTheme.colorScheme`.

---

## 1. Source Color Extraction (Most-Used Artwork Color)
We replace `Palette` vibrant preference with a deterministic, pure-JVM dominant-cluster extractor in `com.feishin.mobile.core.ui.ArtworkColorExtractor` (zero `android.*` imports):

```kotlin
fun extractSourceColor(pixels: IntArray, width: Int, height: Int): Long?
```

### Algorithm
1. **Downscale / Grid Subsampling**: Callers pass a bitmap decoded at `64×64` to `128×128`. If `width > 128` or `height > 128`, the function subsamples on a deterministic integer stride (`stepX = max(1, width / 96)`, `stepY = max(1, height / 96)`) so work is bounded to $\le 9,216$ pixels.
2. **Alpha Filtering**: Ignore any pixel with `alpha == 0` (`(argb ushr 24) == 0`). For semi-transparent pixels (`1..254`), use their unpremultiplied RGB channels directly. If all sampled pixels are fully transparent (or `pixels.isEmpty()`), return `null`.
3. **5-bit RGB Quantization**: Quantize each opaque pixel to a 15-bit bucket (`r5 = r ushr 3`, `g5 = g ushr 3`, `b5 = b ushr 3`, key `(r5 shl 10) or (g5 shl 5) or b5`) using a fixed `IntArray(32768)` histogram and channel-sum accumulators (`sumR`, `sumG`, `sumB`). Compute each non-empty bucket's mean RGB and convert to CIELAB $(L^*, a^*, b^*)$ via `Contrast.toLab`.
4. **Perceptual Clustering (Lab Distance Merging)**: Sort non-empty buckets by pixel count descending (tie-break: lower bucket index). Greedily merge buckets into clusters using CIELAB Euclidean distance against the cluster's *initial peak leader* bucket (preventing centroid drift/chaining across gradients):
   $$\Delta E^*_{ab}(\text{bucket}, \text{clusterLeader}) = \sqrt{(\Delta L^*)^2 + (\Delta a^*)^2 + (\Delta b^*)^2} \le 12.0$$
   - **Leader-Based Distance ($\le 12.0$)**: In CIELAB, JND is $\sim 2.3$, JPEG compression artifacts and subtle cover gradients span $\Delta E \approx 4\text{--}10$, while distinct hues sit at $\Delta E > 18$. Evaluating distance against the initial peak bucket rather than a moving centroid prevents gradient chaining (e.g. subtle gradient walking across stops into distinct accents). When a bucket joins, update the cluster's population count and weighted RGB sum; the cluster's final mean RGB determines the winning seed.
5. **Winner Selection & Neutral Eligibility**: Neutrals (white, gray, black) are never filtered out or penalized by chroma. The cluster with the largest total pixel count wins.
6. **Deterministic Tie-Break Rule**: If two clusters have identical pixel counts, prefer: (1) higher Lab lightness $L^*$ (integer-scaled $\lfloor L^* \times 100 \rfloor$), then (2) lower packed `0xFFRRGGBB` value of the cluster's mean RGB. The winning cluster's population-weighted mean RGB (packed as `0xFFRRGGBBL`) is returned.

### Threading, Caching & Pre-Seeding
- **Off-Main Execution & Thread Safety**: Pixel reading (`Bitmap.getPixels`) and `extractSourceColor` run on `Dispatchers.Default`. `DynamicSchemeCache` (capacity 64, keyed by `coverId: String`) is synchronized across main/default dispatchers, storing `seedArgb: Long` and precomputed schemes (~32 KB total heap).
- **Hardware Bitmap Guard & Flash Prevention**: Before launching an async decode, `rememberDynamicSeed` synchronously checks `DynamicSchemeCache.get(key)`. If absent, it inspects Coil's `memoryCache[MemoryCache.Key(thumbUrl)]`; if present and `bitmap.config != Bitmap.Config.HARDWARE`, it extracts on `Dispatchers.Default` (if hardware, dispatches `ImageRequest` with `.allowHardware(false)` to prevent `IllegalArgumentException`).
- **Retain Previous Scheme on Track Change**: While an async extraction is in flight, the UI retains the previous track's scheme instead of resetting to `null` or static default, eliminating intermediate theme flashes.

---

## 2. Tone Mapping & Contrast Guard Engine

### Color Space Choice: In-Repo CIELAB / CIE LCh (`Contrast.kt`)
- **Decision**: Extend the existing pure-JVM CIELAB/LCh engine in `Contrast.kt` (`toLab`, `toneOf`, `chromaOf`, `labHue`, `fromToneChroma`) rather than adding `material-color-utilities`.
- **Justification**: `gradle/libs.versions.toml` does not include `material-color-utilities` or `material-kolor`. `Contrast.kt` already implements D65 sRGB $\leftrightarrow$ CIELAB conversion and binary-searched gamut mapping (`fromToneChroma`) in ~120 lines of dependency-free Kotlin. CIELAB $L^*$ matches HCT Tone ($T \in [0, 100]$) directly because HCT's tone axis *is* CIE $L^*$, and $C^*_{ab}$ scales monotonically with HCT chroma.

### Chroma Scaling for Neutral vs. Chromatic Sources
Let seed Lab be $(L^*_s, C^*_s, h_s)$. Define a smooth neutral factor $\alpha = \text{clamp}(C^*_s / 18.0, 0.0, 1.0)$:
- When $C^*_s < 4.0$ (pure white, gray, black), target chroma is $0.0$ (true neutral gray surfaces and neutral white/near-black accents—no invented hue).
- For $C^*_s \ge 4.0$, role chroma is $\min(\text{roleChromaCap} \times \alpha, C^*_s)$, preserving soft saturation for pastels and capping vivid seeds.

### Dark Theme Tonal Mapping (`dark = true`)
- **Surfaces (Tone 6–14)**:
  - `surfaceContainerLowest`: $T = 4$, $C = \min(4\alpha, C^*_s)$
  - `background` / `surfaceContainerLow`: $T = 6$, $C = \min(6\alpha, C^*_s)$ (neutral source $\rightarrow$ `#0F0F0F`)
  - `surface` / `surfaceContainer`: $T = 11$, $C = \min(10\alpha, C^*_s)$ (MiniPlayer base $T = 12$)
  - `surfaceVariant` / `surfaceContainerHigh`: $T = 16$, $C = \min(14\alpha, C^*_s)$ (containers 5 tones above `surface`)
- **Primary & Containers**:
  - `primary` (accent): $T = 80$ (range $75\text{--}85$), $C = \min(36\alpha, C^*_s)$. For a white/neutral cover ($\alpha = 0$), `primary` is neutral light ($T = 85, C = 0 \rightarrow \text{\#D4D4D4}$), never blue.
  - `onPrimary`: $T = 12$, $C = \min(10\alpha, C^*_s)$
  - `primaryContainer`: $T = 22$, $C = \min(22\alpha, C^*_s)$; `onPrimaryContainer`: $T = 92$, $C = \min(10\alpha, C^*_s)$
  - `secondary`: $T = 78$, $C = \min(14\alpha, C^*_s)$; `onSurface`: $T = 96$, $C = \min(4\alpha, C^*_s)$; `onSurfaceVariant`: $T = 76$, $C = \min(8\alpha, C^*_s)$
  - `outline`: $T = 38$, $C = \min(10\alpha, C^*_s)$; `outlineVariant`: $T = 24$, $C = \min(8\alpha, C^*_s)$

### Light Theme Tonal Mapping (`dark = false`) & Section D Hook
- **Tint-Strength Hook**: `PlayingColors.buildMaterialScheme(seedArgb: Long, dark: Boolean, lightTintStrength: Float = DefaultLightTintStrength)` where `lightTintStrength` $\in [0f, 1f]$ scales surface chroma caps in Light mode (`effectiveCap = baseCap * lightTintStrength`). Section D can tune or expose this single parameter without altering the tone solver.
- **Clamped Lightness Invariant**: The seed's own lightness $L^*_s$ is **never** used to darken light surfaces; even a pure black cover ($L^*_s = 0$) maps only its hue $h_s$ and chroma $C^*_s$ onto fixed high tones:
  - `surface` / `surfaceContainerLowest`: $T = 98$, $C = \min(6\alpha \cdot \text{tint}, C^*_s)$
  - `background`: $T = 96$, $C = \min(8\alpha \cdot \text{tint}, C^*_s)$
  - `surfaceContainerLow`: $T = 95$, `surfaceContainer`: $T = 94$, `surfaceContainerHigh` / `surfaceVariant`: $T = 91$, $C = \min(14\alpha \cdot \text{tint}, C^*_s)$
- **Primary & Containers**:
  - `primary` (accent): $T = 40$ (range $35\text{--}45$), $C = \min(42\alpha, \max(C^*_s, 28\alpha))$. Neutral source ($\alpha = 0$) yields a dark neutral charcoal accent ($T = 35, C = 0$), not blue.
  - `onPrimary`: $T = 99$, $C = 0$; `primaryContainer`: $T = 88$, $C = \min(24\alpha, C^*_s)$; `onPrimaryContainer`: $T = 14$, $C = \min(12\alpha, C^*_s)$
  - `onSurface` / `onBackground`: $T = 10$, $C = \min(4\alpha, C^*_s)$; `onSurfaceVariant`: $T = 34$, $C = \min(10\alpha, C^*_s)$
  - `outline`: $T = 50$, $C = \min(10\alpha, C^*_s)$; `outlineVariant`: $T = 80$, $C = \min(8\alpha, C^*_s)$
  - `miniContainer`: $T = 94$ (`surfaceContainer`), $C = \min(10\alpha \cdot \text{tint}, C^*_s)$ (neutral pale card for MiniPlayer base)
  - `miniOutline`: $T = 80$ (`outlineVariant`), $C = \min(8\alpha \cdot \text{tint}, C^*_s)$ (ensures $\ge 3.0:1$ border against light background $T = 96$)

### Auto Tone Adjustment Algorithm (Contrast Guards)
Every generated pair is verified and auto-adjusted before construction:
- **Text pairs ($\ge 4.5:1$)**: `onBackground`/`background`, `onSurface`/`surface`, `onSurface`/`surfaceContainerHigh`, `onSurfaceVariant`/`background`, `onSurfaceVariant`/`surface`, `onSurfaceVariant`/`surfaceContainerHigh`, `onPrimary`/`primary`, `onPrimaryContainer`/`primaryContainer`, `chipOffText`/`chipOffFill`.
- **Icon / Control pairs ($\ge 3.0:1$)**: `primary`/`background`, `primary`/`surface`, `primary`/`surfaceContainer`, `primary`/`primaryContainer`, `switchOffOutline`/`surface`, `switchOffThumb`/`switchOffTrack`. (`ringTrack` is excluded from `primary` inputs: it is derived downstream from `primary` and `container` via `ringTrack(container, primary)` ensuring $\ge 3.05:1$).
- **Adjustment Step**: For a foreground role at $(h, T_{\text{fg}}, C_{\text{fg}})$ against background(s) $\{B_i\}$ with target ratio $R_{\min}$:
  1. Determine polarity from background luminance (move $T_{\text{fg}}$ toward $100$ if $\max \text{lum}(B_i) \le 0.18$, else toward $0$).
  2. Step $T_{\text{fg}}$ by $\pm 1.5$ tones (up to 64 iterations, clamping $0..100$); `fromToneChroma` automatically maps to gamut boundary.
  3. If extreme tone still fails across $\{B_i\}$, select between `0xFF000000L` and `0xFFFFFFFFL` by maximizing the minimum ratio across all backgrounds: $\max_{c \in \{0, 1\}} \min_{i} \text{ratio}(c, B_i)$.

---

## 3. Unified Output & Migration Plan

### Single Material ColorScheme at `FeishinTheme` Root
`PlayingColors` outputs a pure-JVM `VusicDynamicScheme` holding all 20 `MaterialTheme.colorScheme` roles, control tokens, and surface gradient stops as packed `0xFFRRGGBBL` longs.
- **`MainActivity.kt` / `FeishinTheme` (`Theme.kt:193`)**: `FeishinTheme(dark: Boolean, dynamicScheme: VusicDynamicScheme? = null, content: @Composable () -> Unit)` converts `dynamicScheme` to `MaterialTheme.colorScheme` when non-null, else uses static `Dark` or `Light`.
- Because `VusicColors` (`Background`, `Surface`, `SurfaceHigh`, `SecondaryText`, `Accent`, `OnAccent`) and `VusicControlColors` (`SwitchOnTrack`, `SliderThumb`, etc.) are already `@Composable` getters reading `MaterialTheme.colorScheme`, providing the dynamic `ColorScheme` at the root automatically updates top bars, section headers, bottom nav, chips, switches, and sliders. `VusicControlColors` off-state tokens (`ChipOffFill`, `ChipOffText`, `SwitchOffTrack`, `SwitchOffOutline`, `SwitchOffThumb`, `SliderInactiveTrack`) read from `dynamicScheme` when active, falling back to their static Dark/Light literals when `dynamicScheme == null`.
- **Settings Key**: `SettingsStore.dynamicNpColor` (`"dynamic_np_color"`) is **unchanged**.

### Migration & Adapter Plan (Preserving Lyrics 8.1, MiniPlayer & Now Playing)
| Component / File | Action | Details |
|---|---|---|
| `Theme.kt` (`FeishinTheme`) | **Update** | Accepts `dynamicScheme: VusicDynamicScheme?` (resolved at root from `main.player.current`, `settings.dynamicNpColor`, `dark`). Maps to `ColorScheme` and `LocalPlayingColors` adapter. |
| `Nav.kt:232-253` | **Remove** | Lift `rememberPlayingScheme` and `LocalPlayingColors` provider out of `AppNav` into `MainActivity` / `FeishinTheme` so the entire tree beneath `FeishinTheme` shares one scheme. |
| `PlayingScheme` & `LocalPlayingColors` (`PlayingColors.kt`, `PlayingTheme.kt`) | **Keep as Adapter** | Derived deterministically from `VusicDynamicScheme` (`PlayingScheme.fromDynamic(dynamicScheme)`). Keeps `Components.kt` (`TrackRow`), `MiniPlayer.kt`, and `NowPlayingScreen.kt` working without breaking signatures. |
| `NowPlayingColors.kt` | **Keep Unchanged** | `NowPlayingColors.fromScheme(scheme)` and `NowPlayingColors.static(...)` continue to drive Now Playing and Lyrics 8.1 (`deEmphasise`, `ensureReadable`, `lightBackdrop`). Remove `legacyCoverTint` (`NowPlayingScreen.kt:325-360`) because neutral covers now produce a valid neutral `PlayingScheme` ($C^* = 0$) instead of `null`. |
| `MiniPlayer.kt:173-208` | **Adapter / Theme** | Reads `scheme.miniContainer`, `scheme.miniOutline`, `scheme.primary`, `scheme.track` (which now match the new tones—neutral gray/white for white covers, never blue). |

---

## 4. Playback Lifecycle & Zero-Allocation Animation

### Playback State Rules
- **Active Item (Playing, Paused, or Restored Queue on Cold Start)**: `player.current` is non-null $\rightarrow$ when `dynamic_np_color` is enabled, compute and apply the track's `VusicDynamicScheme`. Pausing playback retains the current track's scheme.
- **Nothing Playing (`player.current == null`) or Setting Disabled**: Target scheme is the static `Dark` or `Light` `VusicDynamicScheme` wrapper.

### Animation Strategy: Hybrid Architecture & Zero-Allocation Interpolation
- **Root Recomposition Invariant**: In Compose Material3, `LocalColorScheme` is an internal `staticCompositionLocalOf`. Mutating root `MaterialTheme.colorScheme` on every animation frame forces full-subtree recomposition across the entire app hierarchy.
- **Hybrid Animation Solution**:
  1. **Root `MaterialTheme.colorScheme`**: Snaps discretely upon track scheme settlement (or snaps on track change). This updates top bars, nav rails, and global tokens in exactly **one** recomposition frame per track change.
  2. **Continuous Animation Scoped to `LocalPlayingColors`**: Fluid 400 ms color interpolation (`Contrast.blend` over `FastOutSlowInEasing`) runs exclusively inside `animatedPlayingScheme`, consumed only by `MiniPlayer` and `NowPlayingScreen` without invalidating the global UI hierarchy.
  3. **Zero-Allocation**: Single `Animatable(1f)` blend factor $t \in [0f, 1f]$ interpolates packed ARGB longs in $O(1)$ primitive math with zero per-frame object allocations. Snaps immediately (`duration = 0`) on cold start / restored queue state.

---

## 5. Pure-JVM Test Plan (`ArtworkDynamicThemeV2Test.kt`)
All tests run on standard JVM JUnit 4 (`./gradlew testDebugUnitTest`) using synthetic `IntArray` pixel fixtures (`64×64`):

1. **Pure White (`#FFFFFF`)**: Seed is `#FFFFFF` ($C^* < 2$); Dark `surface` $T \in [6, 14], C \le 2$; Dark `primary` $T \in [75, 85], C \le 2$ (not blue); Light `surface` $T \in [94, 98]$.
2. **Near-White Pastel (`#FFF8F2` warm cream)**: Seed is `#FFF8F2`; slight warm tint ($C^* \le 12$), no jump to high-chroma accent.
3. **Mostly White (90% `#FAFAFA`) + Small Blue Logo (10% `#0B6BA8`)**: Winning cluster is `#FAFAFA` (90% > 10%); generated `primary` and `miniContainer` have chroma $C^* \le 4$ (regression test for the reported bug).
4. **Mid Gray (`#808080`)**: Neutral seed wins; Dark & Light schemes have $C^* \le 2$ across all roles.
5. **Pure Black (`#000000`)**: Seed is `#000000`; **Light theme invariant**: Light `background` and `surface` stay at $T \in [94, 98]$ (never darkened by the black seed).
6. **Saturated Red (`#E8112D`)**: Hue preserved ($\Delta h < 15^\circ$); Dark `surface` $T \in [6, 14]$, `primary` $T \in [75, 85]$, chroma capped $\le 36$.
7. **Saturated Blue (`#1445DC`)**: Hue preserved; Light `primary` $T \in [35, 45]$ clears $\ge 3:1$ on `surface` and $\ge 4.5:1$ with `onPrimary`.
8. **Multicolor Checkerboard (Equal 50%/50% split `#FF0000` / `#0000FF`)**: Deterministic tie-break rule returns identical seed regardless of pixel array order.
9. **Transparent Edge (80% `0x00000000` transparent + 20% `#22AA55` green)**: Transparent pixels ignored; `#22AA55` wins; 100% transparent returns `null` and falls back to static theme.
10. **Universal Contrast Assertions (All 9 Fixtures $\times$ Dark & Light)**: Assert WCAG ratios: text pairs $\ge 4.5:1$, icon/control pairs (`primary`/`surface`, `primary`/`ringTrack`, switch/chip boundaries) $\ge 3.0:1$.

---

## 6. Risks & Open Questions
1. **Root `ColorScheme` Recomposition Cost**: Resolved by hybrid architecture: root `MaterialTheme.colorScheme` settles discretely on track change (1 recomposition frame), while 400 ms continuous tween is isolated to `LocalPlayingColors` consumers (MiniPlayer/Now Playing).
2. **Off-White Compression Noise**: Heavily compressed JPEG covers with vignette shadows could split a white background if $\Delta E > 12$. *Mitigation*: 5-bit pre-quantization + $\Delta E^*_{ab} = 12.0$ covers standard shadow falloff; tunable in one constant.
3. **Light Theme Neutral Accent Contrast**: For a pure white cover in Light mode, a neutral gray `primary` ($T \approx 35$) is subtle compared to static blue `#0B6BA8`. *Question*: Should completely achromatic covers in Light mode use dark charcoal ($T = 30$) for `primary` so active pills/toggles remain bold?
4. **Section D Light Surface Tint Coordination**: `lightTintStrength` defaults to `0.35f` until Section D finalizes light-surface tint rules.
5. **`androidx.palette` Removal**: Once `ArtworkColorExtractor` replaces `Palette` in `PlayingTheme.kt` and `NowPlayingScreen.kt`, the `androidx.palette:palette-ktx` dependency can be removed in cleanup.
