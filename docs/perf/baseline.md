# M7.0a baseline (release build, commit `ef69329`)

All numbers taken on the `feishin_test` emulator (1080x2400, SwiftShader
software GL, Intel i5-10500H host) with the R8-minified release APK.
Emulator numbers are meaningful only relatively — same setup is used for the
`after.md` re-measure.

## APK size (signed release APK, `app-release.apk`)

- File size: 3,781,759 bytes (3.61 MB)
- Download size (`apkanalyzer apk download-size`): 3,328,525 bytes (3.17 MB)
- `resources.arsc`: 96,314 bytes (download)
- `/res/` total: 256,326 bytes (download) — largest single entries are all
  <1 KB (`zz.png`, `zU.png`, `z9.png` …; R8 resource shrinking + obfuscated
  names, no large assets)
- Single `classes.dex`
- Native libs, per ABI (×4 ABIs: arm64-v8a, armeabi-v7a, x86, x86_64):
  - `libdatastore_shared_counter.so` (DataStore)
  - `libandroidx.graphics.path.so` (Compose graphics)
  - No other native code. A phone APK needs only the arm64-v8a pair.

## Dex / method count (release, R8 + obfuscated)

- Defined methods: 34,814 · referenced: 39,047 · dex raw: 5,253,582 bytes
- `androidx`: 12,270 defined (of which `media3`: 6,891 — ~20% of the whole dex)
- Debug (unobfuscated) attribution for the record:
  - `androidx.compose.*`: 79,513 · `com.google.*`: 33,904 (mostly Guava via
    Media3 + protobuf via DataStore; see below) · app `com.feishin.*`: 12,847
  - `kotlin`: 10,121 · `kotlinx`: 7,694 · `okhttp3`: 2,820 · `coil`: 2,077
  - `okio`: 1,495 · `dagger`: 440 · `retrofit2`: 411

## Dependency inventory (releaseRuntimeClasspath, all used)

| Dependency | Used for | Verdict |
|---|---|---|
| compose BOM 2024.10.00 (ui, foundation, material3, runtime, animation) | whole UI | keep |
| material-icons-extended | ~100 icon refs incl. extended-only (Radio, QueueMusic, DragHandle, RepeatOne, PlaylistPlay…) | keep (R8 strips unused icons) |
| activity-compose, core-ktx, splashscreen, lifecycle, navigation-compose, hilt-navigation-compose | nav/activity/lifecycle | keep |
| hilt-android 2.52 | DI | keep |
| media3 1.4.1 (exoplayer, session, common, datasource, database, datasource-okhttp) | playback, Auto, radio | keep; pulls Guava 33.0.0-android (media3-common needs it) |
| retrofit 2.11.0 + kotlinx-serialization converter + okhttp (+logging) | Subsonic API | keep (single shared OkHttp client) |
| kotlinx-serialization-json 1.7.3, coroutines 1.9.0 | DTOs, async | keep |
| coil-compose 2.6.0 | art loading (25% heap cache, display-sized requests) | keep |
| datastore-preferences 1.1.1 | settings/queue/home cache | keep (pulls protobuf-lite + 2 .so per ABI) |
| palette-ktx 1.0.0 | artwork seed (2 call sites) | keep |
| reorderable 2.5.1 | queue drag | keep |
| room-runtime/ktx 2.6.1 (+compiler via ksp) | downloads/offline cache | keep; all entities/DAOs used |
| work-runtime-ktx 2.9.1 + hilt-work | downloads/scrobble sync | keep (on-demand init) |
| security-crypto 1.1.0-alpha06 | EncryptedSharedPreferences credentials | keep; pulls Tink 1.8.0 + Gson 2.8.9 (only heavy transitive weight) |
| compose ui-tooling (`debugImplementation`) + preview (`implementation`, annotations only) | previews | keep |
| junit, coroutines-test (`testImplementation`) | unit tests | keep |

No Glance, no LeakCanary, no duplicate image/network libs.

## Code / resource counts

- Kotlin files: 125 main + 36 test
- Resources: 14 drawables (all XML vectors), 2 widget layouts, 1 strings.xml,
  3 xml configs, launcher icon — no PNGs, no extra locales/densities yet
- Lint (debug): 123 warnings — 72 GradleDependency (newer versions available,
  not chased), 14 HardcodedText, 9 FrequentlyChangedStateReadInComposition,
  5 ModifierParameter, 5 UnusedAttribute, 3 AGP-version, 3 ObsoleteSdkInt,
  3 UseTomlInstead, 2 VectorRaster, 1 each BatteryLife, CustomX509TrustManager,
  DataExtractionRules, ExportedService, InsecureBaseConfiguration,
  NestedWeights, RtlSymmetry

## Cold / warm start (`am start -W`, TotalTime, 10 runs, logged-in → Home)

- Cold: 1153, 1183, 1293, 1331, 1338, 1382, 1409, 1447, 1480, 1559 ms —
  median 1360 ms, worst 1559 ms
- Warm: 136, 150, 173, 217, 235, 239, 242, 264, 267, 267 ms —
  median 237 ms, worst 267 ms
- (No `reportFullyDrawn`; TotalTime = time to first frame. Runtime measured on
  a debug-signed release build — byte-identical R8 output, only the signature
  block differs (~12 KB) — so the logged-in session survived. Size numbers
  above are from the true release-signed APK.)

## Memory (`dumpsys meminfo com.feishin.mobile`, PSS KB)

- Idle on Home: TOTAL PSS 113,500 · Java heap (PSS/RSS) 384/18,716 ·
  native heap 3,544/5,176 · graphics 0/0 · RSS 145,512 · SWAP PSS 78,042
  (the emulator is heavily swapped; all absolute numbers carry that caveat)
- After 5 min playback, screen on: TOTAL PSS 123,782 (+10.3 MB vs idle) ·
  Java heap 15,904/36,768 · native heap 55,440/57,472 · graphics 0/0 ·
  RSS 222,048 · SWAP PSS 14,773
- After browsing 20 screens (still playing): TOTAL PSS 152,033 (+38.5 MB vs
  idle) · Java heap 21,828/45,948 · native heap 75,144/77,140 · graphics 0/0
  (SwiftShader: nothing allocated on the GPU) · RSS 254,724 · SWAP 13,664
- Growth sits almost entirely in the native heap (ExoPlayer/codec buffers +
  image cache); §C investigates whether the 5-min-playback native growth is
  cache or leak.

## Jank (`dumpsys gfxinfo`, SwiftShader software GL)

| Screen | Frames | Janky (legacy) | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| Home scroll (8 drags) | 118 | 99.2% | 36 ms | 65 ms | 77 ms | 129 ms |
| Library scroll (8 drags) | 610 | 100% | 93 ms | 121 ms | 150 ms | 150 ms |
| Settings nav (4 screens) | 28 | 100% | 113 ms | 150 ms | 150 ms | 200 ms |
| Queue drag (8 drags) | 288 | 99.7% | 117 ms | 200 ms | 200 ms | 350 ms |
| Now Playing (scrub+scroll) | 35 | 100% | 85 ms | 150 ms | 150 ms | 150 ms |

Renderer: `ro.hardware.egl=emulation`, hwui `skiagl` — every frame is
software-rasterized at 1080x2400, so ~100% legacy-jank is the emulator
ceiling, not necessarily the app. Relative comparison only.

## Background while playing, screen off (82 s window, batterystats reset first)

- Audio active the full window (expected — music playing); app partial
  wakelock 56 s of 82 s; **1** `wakeupap` event attributed to the app;
  **0** app alarms and **0** WorkManager jobs fired in the window.
- `dumpsys cpuinfo` (cumulative, emulator-skewed): 71% pkg
  (5.1% user + 66% kernel — kernel share is the emulator's audio/display
  virtualization, not app work; relative only).
- Running service: `PlayerService` foreground (`mediaPlayback`, id 1001,
  media3 notification) — the only app service; nothing else runs in
  background.

## Display modes (for §D)

- Exactly one mode: `id=1, 1080x2400, fps=60.000004`,
  `alternativeRefreshRates=[]`. `mDefaultPeakRefreshRate=0`,
  `mDefaultRefreshRate=60`.
- Conclusion: **90/120 Hz is not verifiable on this emulator** — there is no
  higher mode to request and no adaptive range. §D relies on the
  mode-selection unit tests plus the frame-time budget on 60 Hz
  (D.6-verified: `RefreshRateTest`, 8 cases). The `min/peak_refresh_rate`
  settings override was attempted and is NOT honored (still a single
  60.000004 Hz mode), so it was reset.
