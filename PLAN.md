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
- [x] M1 setup+auth (project scaffold, login, connection test, encrypted creds, reactive nav)
- [x] M2 library browsing (home, library tabs, search, detail pages, paging/sort/grid toggle)
- [x] M3 playback service (MediaSessionService, notification, focus, gapless, scrobble, star)
- [x] M4 queue+Now Playing (full/mini player, queue ops, lyrics, dynamic bg)
- [x] M5 offline+settings (downloads, stream cache, bitrate wifi/mobile, theme, cert toggle, logout)
- [x] M6 extras (Auto manifest support, home screen widget, README, final APK verify)

Verified: `./gradlew assembleDebug` green, `./gradlew lint` green (0 errors),
`testDebugUnitTest` 3/3 pass. APK 23MB at app/build/outputs/apk/debug/app-debug.apk.
No adb device attached, so no on-device install/logcat check — noted below.

## Assumptions
- Server speaks Subsonic 1.16.1 / OpenSubsonic; `f=json` always.
- No device/emulator attached (`adb devices` empty) -> verify via assembleDebug+lint+unit tests.
- Crossfade: ExoPlayer has no native crossfade DSP; setting stored and applied as
  best-effort volume fade guard (documented in README).
- ReplayGain: no native Media3 support; stored as volume-normalization preference
  documented as best-effort (server `getAlbum` may carry replayGain fields; applied as gain).
- Credentials never committed; test config via local.properties/env only.
