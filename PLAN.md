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
- [ ] M2 library browsing (home, library tabs, search, detail pages, paging/sort/grid toggle)
- [ ] M3 playback service (MediaSessionService, notification, focus, gapless, scrobble, star)
- [ ] M4 queue+Now Playing (full/mini player, queue ops, lyrics, dynamic bg)
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
