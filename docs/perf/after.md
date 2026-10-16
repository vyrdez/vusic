# M7.0c after (§B + §C, release build)

Re-measured on the same `feishin_test` emulator setup as `baseline.md`.
Runtime numbers are emulator-relative; compare before/after, not absolutes.

## Size

| Metric | Baseline (M7.0a) | After §B | After §C | Δ vs baseline |
|---|---|---|---|---|
| arm64 APK file | 3,781,759 (universal) | 3,396,155 | 3,396,155 | **−385,604 (−10.2%)** |
| arm64 APK download | 3,328,525 | 3,228,994 | 3,228,994 | **−99,531 (−3.0%)** |
| resources.arsc (download) | 96,314 | 13,798 | 13,798 | −82,516 (−85.7%) |
| string locales | 86 | 1 | 1 | −85 |
| native ABIs in phone APK | 4 | 1 (arm64-v8a) | 1 | −3 ABIs (~43 KB raw) |
| dex defined / referenced | 34,814 / 39,047 | 34,814 / 39,047 | 34,845 / 39,078 | +31 (lifecycle-runtime-compose + profileinstaller; R8 re-shrunk) |
| baseline.prof embedded | no | no | yes (5,732 bytes raw + 862 bytes .profm) | startup classes precompiled at install |

Dex is byte-identical after §B: nothing removable — every dependency and every
Room entity/DAO is genuinely used (Guava via Media3, Tink+Gson via
security-crypto, Palette ×2 call sites, reorderable for queue drag,
material-icons-extended ~100 refs incl. extended-only icons).

## Startup (`am start -W` TotalTime, logged-in → Home)

Fair A/B on the same settled device (M7.0a build vs M7.0c build, back to
back, 5 runs each after dexopt settled):

| | Baseline (M7.0a build) | After §C | Δ |
|---|---|---|---|
| Cold ×5 | 1287, 1335, 1351, 1417, 1437 (median 1351) | 1087, 1100, 1214, 1288, 1361 (median 1214) | **−137 ms (−10%)** |
| Warm ×5 | 136–267 (median 237) | 155–456 (median 191) | within noise, no regression |

(An earlier non-back-to-back comparison looked worse for §C, but the device
states differed: baseline idle meminfo showed SWAP PSS 78 MB, the re-measure
0.5 KB. The A/B above was redone back-to-back to remove that confound.)

Startup-path changes: ImageLoader construction (blocking DataStore read) moved
out of `Application.onCreate` via lazy `Provider<ImageLoader>` (first image
load instead of every cold start); hand-written `baseline-prof.txt` (exact
verified methods only — Macrobenchmark generation would profile the login
screen on a clean device) embedded through a new `:baselineprofile` module;
debug-only StrictMode (disk/network on main thread) added to catch
regressions. FeishinApp.onCreate was already lean (Hilt + CrashGuard + 2
notification channels); WorkManager init was already on-demand.

## Memory (PSS KB, same states, same-day device)

| State | Baseline | After §C | Note |
|---|---|---|---|
| Idle Home | 113,500 | 126,938 | different swap states; content varies |
| +5 min playback (screen on) | 123,782 | 116,781 | −7.0 MB |
| +20 screens browsed | 152,033 | 160,743 | +8.7 MB, content-dependent (art) |

Soak/browse deltas sit in the native heap (ExoPlayer/codec buffers + Coil
image cache at 25% heap) and move ±10 MB run to run with different content;
no leak signature (soak grows only ~3 MB over 5 min and the process is flat
at idle: 0 frames/10 s static on both builds). Newly bounded in §C: lyrics
cache (was an unbounded LinkedHashMap → LRU 64) and the search artist-photo
map (was unbounded → FIFO cap 512). Nothing to do: Home cache is keyed by
genre (bounded set), the scheme cache was already LRU-24, similar-artist
data lives only in screen UI state (freed on exit), and Room needs no new
indexes (downloads already indexed; the rest are PK-keyed single-row tables).
Graphics stays 0/0 on both (SwiftShader, no GPU allocation).

## Jank (same SwiftShader battery, paused to match baseline conditions)

| Screen | Baseline frames / p50 | After §C frames / p50 | Note |
|---|---|---|---|
| Home scroll (8 drags) | 118 / 36 ms | 112–119 / 34 ms (3 back-to-back runs) | parity, stable |
| Library scroll (8 drags) | 610 / 93 ms | 662 / 97 ms | within SwiftShader noise |
| Settings nav (4 screens) | 28 / 113 ms | 29 / 89 ms | within noise |
| Queue drag (8 drags) | 288 / 117 ms | 563 / 150 ms | noisy — see below |
| Now Playing (scrub+scroll) | 35 / 85 ms | 259 / 150 ms | noisy — see below |

Static-screen check: 0 frames/10 s on BOTH builds (no continuous
recomposition — an earlier 133-frame reading was transient post-battery
state, not a regression; verified by reinstalling each build fresh).
SwiftShader software-renders every frame at 1080x2400 (~100% legacy-jank on
both), so the percentiles measure the emulator ceiling, not the app; frame
_counts_ vary with animation state (equalizer/marquee/current track), which
proves cross-run A/B on jank inconclusive here. The actionable perf evidence
is the fair back-to-back cold-start A/B (−10%).

## Lint / tests

- Lint warnings: 123 → 112 (−11: the 9 FrequentlyChangedStateReadInComposition
  and 5 ModifierParameter are gone; +3 GradleDependency notices for the two
  newly added artifacts).
- Unit tests: 358/358 pass.
- Compose compiler stability report: all 145 composables restartable+skippable
  (strong skipping effectively on); the only unstable app class is `FeishinApp`
  (never a composable parameter) — nothing to fix.

## Compose changes (§C)

- 94 `collectAsState()` → `collectAsStateWithLifecycle()` (17 files, both
  overloads; `initial` → `initialValue`): collections now pause below STARTED.
  The mini-player ring intentionally still samples `position.value` directly in
  a 4 Hz draw-phase loop (no recomposition, ~zero cost, playback-gated).
- 9 scroll/pagination reads → `remember { derivedStateOf { … } }`.
- `contentType = { "item" }` on all keyed `items()` (constant, zero-alloc).
- 5 Modifier parameters moved to first-optional position (all call sites use
  named args — verified).
- Verified, no change needed: single shared OkHttp for API (+1 justified
  dedicated Coil client, both @Singleton), transparent gzip (OkHttp default),
  debounced + stale-cancelled search, Media3-managed service lifecycle,
  Room indexes (downloads indexed; the rest are PK-keyed single-row tables —
  no migration needed), Palette on ≤128 px bitmaps.

## Background while playing, screen off (final build, 60 s window)

- Still playing after wake; PlayerService foreground the whole time.
- 2 `wakeupap` events attributed to the app in 60 s (baseline: 1 in 82 s —
  same audio-driven order, no polling introduced); 0 app alarms, 0 jobs.
- No change expected or observed: §C touched no background paths (service
  lifecycle, workers, caching and network code paths unchanged).

## StrictMode (debug-only, new in §C)

Caught and fixed 3 main-thread disk reads at startup, all verified gone
(zero app-code StrictMode sites after the fixes):
- `CredentialsStore.<init>` built EncryptedSharedPreferences (Tink keyset
  read) on the main thread → prefs are now `lazy`, warmed by an explicit
  `preload()` on IO behind the existing `ready` gate (no login flash —
  verified on device).
- `NetworkModule.provideOkHttp` ran TLS init on first composition → IO
  warmup of the Hilt singletons in `FeishinApp.onCreate` (Hilt caches, so
  later main-thread gets are memory-only; cache warmed before OkHttp since
  PlayerManager needs it at first composition).
- `PlayerModule.provideCache` opened the Media3 SQLite index on main →
  same warmup.
- `WidgetStateStore.load()` ran on main in the widget push → moved into
  the existing `withContext(IO)`.
