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
- [x] M4.5 Vusic branding, YouTube Music-style redesign, cover fixes, artist page
  (Vusic wordmark/avatar/login/manifest/widget/notification channel; #0F0F0F theme
  with white accents; shared SectionHeader/AlbumCard/TrackRow/FilterChipRow/
  Empty/Error/skeleton/EQ/marquee components; Home with genre chips, Speed dial
  (local pins), Listen again, Quick picks pager, New releases, Forgotten
  favorites, More-from-artist, mixes, Discover, Most played, Explore pills;
  Library chips + Songs tab + genres list screen; Search with recent searches;
  full TrackActionsSheet + album/artist/playlist sheets, playlist picker, share,
  details dialogs; NP per reference (Song|Lyrics pill, chips, white play circle,
  Playing-from row); queue screen (source header, Save-as-playlist, 56dp art);
  artist hero page (info/top-songs/similar/bio); cover IDs round-tripped through
  MediaItem extras; 113 unit tests)
- [x] M4.6 navigation, titles, home performance, now playing and playlist fixes
  (one scaffold keeps the nav bar + mini player on every screen with a shared
  back-arrow top app bar for details; avatar and bottom nav share one tab-route
  path; Go-to-album/artist collapses Now Playing first; Library/Search titles +
  detached chip row; Home loads every section in parallel and independently with
  stale-while-revalidate memory+disk cache and per-genre prefetch; standardized
  spacing tokens; 2-page Speed dial pager with animated dots; rebuilt thin seek
  bar with correct remaining time and TIME_UNSET handling; luminance-clamped
  opaque Now Playing gradient; single queue drag handle; playlist covers from
  server art or a count-aware collage; 124 unit tests)
- [ ] M5 offline+settings (downloads, stream cache, bitrate wifi/mobile, theme, cert toggle, logout)
- [ ] M6 extras (Auto manifest support, home screen widget, README, final APK verify)

Verified: `./gradlew assembleDebug` green, `./gradlew lint` green (0 errors),
`testDebugUnitTest` 124/124 pass. APK at app/build/outputs/apk/debug/app-debug.apk.
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
- M4.6 root causes (all fixed):
  1. Bottom nav vanished on detail screens because `MiniPlayer` and
     `NavigationBar` were rendered conditionally on `route in topLevel`, and each
     detail screen supplied its own layout. Fixed by one Scaffold whose bottomBar
     always renders both, plus a shared `AppTopBar` for detail routes.
  2. Tapping Home from an album did nothing: `goToTab` used
     `popUpTo(startDestination){saveState=true}` + `restoreState=true`, which
     folded the popped detail into the target tab's saved state so `restoreState`
     re-created it. Tapping Library worked only because it had no such state.
     Fixed by popping the detail as its own step first.
  3. Home's first load waited on the *mixes*: `loadAll` awaited 3 parallel
     `getSimilarSongs2` calls before its single `_ui.value = HomeUi(...)`. That
     endpoint measured 7.9-10.1s on this server while Home's real endpoints take
     ~300ms, so skeletons stayed up for the whole window. Fixed by publishing each
     section independently and treating mixes as non-blocking extra content.
  4. The seek bar rendered as a full block and read "-0:00" because the player
     exposes an unknown duration as 0, collapsing the slider range to `0f..1f`
     where the coerced position saturated to 1f. Fixed with pure
     `PlaybackProgress` math (unknown => 0 progress + "--:--", clamped remaining).
  5. Light/white covers produced a washed-out grey backdrop because the raw
     dominant colour was used and text flipped to dark. Fixed by clamping the
     backdrop's relative luminance (hue preserved) so white text always clears
     ~AA contrast; gradient stops are opaque.
- M4.6 Home load timings (emulator, live server, measured via `HomePerf` logs):
  BEFORE - first content only after the mixes resolved, so ~8-10s+ on skeletons
  (matches the 7.9-10.1s similar-songs probe). AFTER - first content paint at
  1.7-2.1s, all sections by ~2.05-2.6s; a Home revisit paints full content in
  under 700ms from the memory cache, and a cold start paints the disk snapshot
  before the network answers.
- M4.6 verification: build + lint (0 errors) + 124/124 unit tests green. On device:
  nav bar and back arrow on album/artist/playlist/genre, Home reachable from every
  screen, avatar -> Settings -> Home works, playlist collage + album-style header,
  Now Playing per spec (centred title, pill, three-dot far right, thin seek bar
  with correct remaining), queue sheet with one drag handle and 56dp art, Lyrics
  chip, rotation, zero FATALs.
- M4.6 notes/limitations: drag-to-reorder still cannot be driven over adb (no
  hold-then-move primitive); an independent review flagged the speed-dial collage
  and a premature skeleton-drop, both then fixed; the share sheet and details
  dialogs remain verified by inspection only (avoiding junk shares).
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
- M4.5 device verification (emulator Pixel 7, live server): Home renders all
  sections with live data; genre chips filter; speed-dial pin appears after
  refresh (fixed reactive rebuild + a launch NPE from field-init order); mix
  collages fixed (were passing raw cover IDs); track sheet matches reference;
  NP matches reference (pill toggle, chips, white play circle, Playing-from);
  queue art present incl. after kill+restore; save-as-playlist verified via API
  (24 tracks) and cleaned up; addToPlaylist contract verified via API; star both
  ways via API; rotation keeps state; NP inset fixed (pill was under status bar).
  Zero FATALs after fixes.
- M4.5 notes/limitations: package/applicationId unchanged (Vusic branding is
  label-only); Subsonic client ID stays FeishinMobile (preserves scrobble history
  attribution); playlist picker/add-to-playlist verified via API probe, share and
  details dialogs verified by code inspection only; artist pages for empty artist
  records render a clean empty hero (server data quirk, names can mismatch);
  similar-artist circles use placeholder art (last.fm provides none); Library SONGS
  tab is getRandomSongs(500) — Subsonic has no all-songs endpoint.
