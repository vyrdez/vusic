# Feishin Mobile — Architecture & Milestone Plan

## Architecture
- Package `com.feishin.mobile`, single `:app` module, Min SDK 26, target/compile 34.
- Layers: `data` (Retrofit Subsonic API + AuthInterceptor + stores + Room + repositories),
  `playback` (PlayerManager singleton + PlaybackService MediaSessionService + scrobble + cache),
  `ui` (Compose NavHost + screens + viewmodels + components), `di` (Hilt modules), `util`.
- Auth: Subsonic token auth `u/t/s/v/c/f=json`, fresh UUID salt per request in
  `AuthInterceptor`, MD5(password+salt). Base URL normalized to `<server>/rest/`
  so path prefixes work. Retrofit `@Query` handles URL-encoding. Password never logged/sent plain.
- Credentials: EncryptedSharedPreferences (AES256_GCM) + StateFlow; NavHost start
  destination reacts to login state, no restart needed.
- Settings: DataStore Preferences. Self-signed toggle read live by a dynamic
  X509TrustManager inside the shared OkHttpClient -> applies without restart.
- Playback: `PlayerManager` owns one ExoPlayer (app context). `PlaybackService`
  exposes it via MediaSession (notification, lock-screen, BT, audio focus, gapless).
  Stream URLs carry per-request auth params. Cache via ExoPlayer SimpleCache.
- Offline: Room tracks downloads; files fetched with authed URLs via OkHttp.

## Versions (verified compatible set)
AGP 8.5.2, Kotlin 2.0.21, KSP 2.0.21-1.0.25, Hilt 2.52, Compose BOM 2024.10.00,
Media3 1.4.1, Retrofit 2.11.0, OkHttp 4.12.0, kotlinx-serialization 1.7.3,
Coil 2.6.0, Room 2.6.1, DataStore 1.1.1, Coroutines 1.9.0, Lifecycle 2.8.6,
Navigation-Compose 2.7.0, hilt-navigation-compose 1.2.0, security-crypto 1.1.0-alpha06.

## Milestones
- [x] M0 environment (JDK17, SDK34, build-tools 34.0.0, platform-tools)
- [x] M1 project foundation + auth (packages core/common, core/network, data/auth,
  data/settings, ui/login, ui/navigation, ui/theme; SubsonicAuth token/URL/parse
  helpers with 14 unit tests; AuthRepository connect/probe/logout; single NavHost
  with splash-until-ready + popUpTo; self-signed toggle on login (no restart);
  cleartext allowed via network security config; .gitignore + untracked
  local.properties/build dirs; 0 lint errors)
- [x] M2 library browsing, search, cover art (DTOs with single-object quirk
  tolerance; domain models + mappers, UI never sees DTOs; MusicDataRepository
  interface + Hilt binding; CoverArtUrlProvider with stable per-cover salt; Coil
  via Hilt on a self-signed-aware OkHttp client with crossfade/placeholder/error
  art; album infinite scroll; artist grid/list; genre albums screen; optimistic
  star with rollback; pull-to-refresh + rotation-safe scroll state everywhere;
  37 unit tests; 0 lint errors; device-verified end to end)
- [x] M3 playback service and scrobbling (PlayerService : MediaSessionService owns
  ExoPlayer via OkHttpDataSource on the self-signed-aware streaming client,
  WAKE_MODE_NETWORK, handleAudioBecomingNoisy, foreground mediaPlayback service,
  runtime POST_NOTIFICATIONS prompt; app-side PlayerManager drives a MediaController
  and exposes StateFlows; StreamUrlProvider with fresh salt + maxBitRate/format;
  ScrobblePolicy submits at 50%-or-4min once per track and ignores seek jumps;
  mini player with progress bar; 61 unit tests, 0 lint errors)
- [x] M4 queue + Now Playing (QueueOps pure index bookkeeping; PlayerManager owns
  app-side shuffle with tail-only Fisher-Yates via moveMediaItem so visible order
  always matches playback; QueueStore DataStore persistence with paused restore;
  full-screen Now Playing with Palette dynamic bg + contrast text, jitter-free
  scrub, shuffle/repeat/star/queue, artist/album navigation, back + swipe-down
  close; queue sheet with highlight/autoscroll/swipe-remove/tap-jump/clear;
  OpenSubsonic synced lyrics with fallback chain + in-memory cache; upgraded mini
  player (tap/swipe-up open, swipe L/R skip, hidden when empty); track overflow
  menus + detail header menus with snackbar confirmations; 89 unit tests)
- [ ] M5 offline+settings (downloads, stream cache, bitrate wifi/mobile, theme, cert toggle, logout)
- [ ] M6 extras (Auto manifest support, home screen widget, README, final APK verify)

Verified: `./gradlew assembleDebug` green, `./gradlew lint` green (0 errors),
`testDebugUnitTest` 3/3 pass. APK 23MB at app/build/outputs/apk/debug/app-debug.apk.
LIVE on-device test (emulator Pixel 7, API 34) against server.example.com: login OK,
Home with real albums/art, album detail (17 tracks), streaming playback confirmed
(mini player + Now Playing 0:33/3:42 + audio focus), zero FATAL crashes. Probe script
([local user]\AppData\Local\Temp\opencode\nd_probe.ps1, creds via env only, NOT in repo):
ping/albumLists/artists(673)/search/album/stream-HEAD-200-flac/coverArt-JPEG/lyrics/
playlists/genres/starred2 all OK. No adb physical device; emulator AVD `feishin_test`.

## Assumptions
- Server speaks Subsonic 1.16.1 / OpenSubsonic; `f=json` always.
- Phase-1 restructuring kept the already-built M2–M6 screens/playback in place
  (deleting verified working code to pretend it doesn't exist would be worse
  engineering); the required M1 package structure was applied to foundation/auth
  and all imports migrated. No M2+ behavior was added in this phase.
- Login fields use rememberSaveable for URL/username (rotation + process death);
  the password is intentionally NOT saved across process death (never persisted
  to disk outside EncryptedSharedPreferences).
- AuthRepository.ready flips true after the (synchronous) encrypted-prefs read,
  so the splash-until-ready gate never flickers in practice.
- Device verified 2026-10-12 on emulator `feishin_test` (Pixel 7, API 34, headless):
  install OK, launch OK, zero FATAL crashes; logout→login→Test & Connect→Home
  against the live server, all with real data. Live creds used via adb input only,
  never written to the repo.
- Crossfade: ExoPlayer has no native crossfade DSP; setting stored and applied as
  best-effort volume fade guard (documented in README).
- ReplayGain: no native Media3 support; stored as volume-normalization preference
  documented as best-effort (server `getAlbum` may carry replayGain fields; applied as gain).
- Credentials never committed; test config via local.properties/env only.
- M3 device verification (emulator Pixel 7, live server): album playback confirmed
  via `dumpsys media_session` (state=PLAYING, metadata title/artist/album correct);
  media notification present (category=transport, vis=PUBLIC, isForeground=true,
  types=0x2 mediaPlayback); AUDIO_BECOMING_NOISY paused playback and abandoned
  audio focus; playback continued for 5+ min with the screen Asleep and advanced
  to the next track (mid=0→1) => gapless/auto-advance + WAKE_MODE_NETWORK work;
  scrobbles verified through the API (getNowPlaying shows playerName=FeishinMobile
  state=playing; Frozen Flower top of recently played; playCount incremented).
- M3 notes: ExoPlayer owns audio focus and noisy handling; the service (not the UI)
  drives scrobbling so it survives the app process. Emulator has no real audio
  output, so "audio is audible" was verified via player state/position, not by ear.
- Lint requires the androidx `@file:OptIn` marker form for Media3 `UnstableApi`
  (plain `@file:OptIn` / class-level annotations leak to every caller).
- M2 device findings (fixed): Navidrome rejects getAlbumList type `alphabetical`
  (now `alphabeticalByName`); genre names arrive in `value` not `name` (mapper
  prefers `name`, falls back to `value`); empty `playlists:{}` correctly shows the
  empty state; Navidrome's generic artist art is a real 546-byte WebP (not a bug).
- M2 crash fixes: ViewModel `refreshing` state is now declared before
  `init { refresh() }` (Main.immediate executes the first launch synchronously —
  later field init caused an NPE that killed the app on detail/genre screens).
- M4 device verification (emulator Pixel 7, live server): NP opens opaque with
  Palette bg (dark navy for Frozen Flower, light lavender for Dasvidaniya, text
  switches white/dark for contrast); scrub 0:49→1:59 glitch-free; app-side shuffle
  reorders tail only, current never restarts; queue sheet shows highlight +
  autoscroll + handles; swipe-remove 17→16 with highlight intact; tap-jump changes
  track; kill→relaunch restores queue paused at saved index+position; rotation both
  ways keeps state; star toggle verified both directions via server API (fixed a
  double-negation bug where the tap was a no-op); Play-next/Add-to-queue menus +
  snackbar confirmed; repeat cycles with active tint; mini-player swipe skips;
  back gesture closes sheet then NP. Zero FATALs across the whole pass.
- M4 notes/limitations: server has no lyrics at all (verified via API), so the
  synced-lyrics UI is covered by unit tests + DTO-parse tests, while None/plain
  paths were verified live; drag-reorder gesture could not be driven over adb
  (no hold-then-move primitive) — move() semantics are unit-tested and the
  reorderable wiring keeps header outside the list so lazy indices == queue
  indices; queue edits never touched the current item's playback (positions
  advanced monotonically throughout).
