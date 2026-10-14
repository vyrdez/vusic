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
- [x] M4.7 radio auto-DJ, Now Playing layout, queue, favorites collection
  (radio replaces the queue with the seed as item 1 and fills 20 more in the
  background with similar->genre->random fallthrough, session-scoped no-repeat
  memory, trigger-guarded appends within 2 of the end with quiet backoff, and
  persisted radio flag+seed; Now Playing matches the reference with large art,
  centered titles, Favorite chip, transport-only controls, bottom queue entry
  with marquee; queue sheet with source marquee, button row, Radio pill and
  auto-dismiss on Clear; server-backed Favorite songs collection with gradient
  or collage cover, reactive star sync, and duplicate detection with
  Skip/Add-anyway/Cancel on every add-to-playlist flow; 157 unit tests)
- [x] M4.8 shuffle restore + queue long-press actions
  (per-entry queue keys round-tripped through MediaItem extras; shuffle ON
  snapshots the order and permutes only the upcoming tail, shuffle OFF restores
  the snapshot by key with minimum `moveMediaItem` calls so the current item
  keeps playing and positions never jump; removals/play-next/append edits and
  manual drags while shuffled are folded into the snapshot; the flag, snapshot,
  keys and key counter persist with the queue across a restart; queue rows take
  long-press with haptics plus an explicit ⋮ button left of the drag handle;
  178 unit tests)
- [x] M5 offline downloads, caching, settings, crossfade, replaygain
  (Room download registry with status/progress/resume; WorkManager unique work
  with a dataSync foreground notification + Cancel and Wi-Fi-only constraints;
  per-server app-private storage; cover art downloaded too; keep-downloaded
  groups for album/playlist/favorites that re-sync on refresh; local-file
  playback chosen automatically for downloads (works for restored queues,
  shuffle restore and radio appends); offline mode + banner with cached library
  reads (stale-while-revalidate via Room JSON cache) and offline radio from
  downloads; queued offline scrobbles/stars replayed with original timestamps;
  library Downloads tab with sizes and delete; SimpleCache stream cache with
  configurable size, configurable Coil disk cache, clear-cache actions; full
  Settings screen (account/playback/downloads/network/appearance/about);
  ReplayGain with peak limiting and crossfade as a safe volume ramp; 217 unit
  tests)
- M4.8 root cause (shuffle off did not restore): `setShuffle(true)` permuted the
  tail in place with `moveMediaItem` and stored nothing, and `setShuffle(false)`
  had no restore branch at all — it only flipped the flag. The pre-shuffle order
  was therefore unrecoverable the moment the moves were applied.
- M4.8 restore rules implemented (all in `ShufflePlan`, pure and unit-tested):
  1. Every queue entry has a unique key (`queueId`, also written into the
     MediaItem extras), so two copies of the same song stay distinguishable and
     the current entry is found by key, never by song id or position.
  2. Shuffle ON snapshots the current key order, then permutes only the
     not-yet-played tail; played items and the current item keep their slots.
  3. Shuffle OFF restores the snapshot order: entries removed while shuffled are
     dropped, entries added while shuffled are appended in the order added
     ("play next" is inserted right after the current entry in the snapshot too,
     "add to queue"/radio appends go to the end).
  4. A manual drag while shuffled is treated as the NEW base order (the drag
     wins; restoring to the pre-shuffle order would silently discard the user's
     arrangement).
  5. New playback (playAll / Start radio) resets the snapshot; for a shuffled
     page the snapshot is the page's own track-list order, so toggling OFF
     restores the visible list order. Clear wipes the snapshot and the flag.
  6. Restore uses `moveMediaItem` only (minimum-move sequence), so nothing is
     removed, re-inserted or re-prepared: the current item keeps playing with no
     restart or position change. Repeat modes are untouched.
  7. The flag + snapshot + queue keys + key counter persist with the queue, so
     shuffle restores correctly across an app restart.
- M4.8 queue row actions: queue rows now take long-press (with a haptic tick)
  and also show an explicit ⋮ button to the left of the drag handle, because
  long-press alone was unreliable for the user. Tap still jumps, the handle
  still drags, swipe still removes; opening the sheet does not close the queue.
- [ ] M5 offline+settings (downloads, stream cache, bitrate wifi/mobile, theme, cert toggle, logout)
- [x] M5.5 library fixes, Search and Settings redesign, smart cache
  (Library: tab selection persisted through SavedStateHandle so back from
  Downloads & storage returns to the Downloads tab, new tab order
  Playlists/Downloads/Albums/Artists/Songs/Favorites with Playlists as the
  fresh-launch default, inline per-tab search that filters locally, circular
  artist photos from getArtistInfo2 with cover-art and initial fallbacks, and a
  "Sort by" bottom sheet with readable per-tab options, direction toggle,
  checkmark and DataStore persistence; Search: pill field with back/clear and
  autofocus, filter chips, recent searches with per-item removal, All view with
  colored section titles, top result, 5-song preview + View more, single-type
  paging with infinite scroll, 300ms debounce with stale-request cancellation,
  skeletons, empty/error states and local offline search; Settings: card home
  with live state summaries and an Offline chip, sub-screens (Server & Account
  with server type/API version/scan/test/logout+keep-downloads, Appearance &
  Layout per-tab layout+sort+mix length, Sound & Playback with ReplayGain
  sliders, transitions, the playback-cache section, battery exemption and reset
  to defaults, Connectivity with auto-offline and warnings, Storage with a usage
  bar, download cap, song/image cache controls and a Dangerous section, About);
  smart cache: CacheWriter-based prefetch of the next N queue items with
  stable Media3 cache keys shared with playback, cancellation on queue changes,
  Wi-Fi-only gate, auto-cache of played songs, and a built-in Cached collection
  tracked in Room that plays offline and self-prunes on eviction; 250 unit tests)
- [ ] M6 extras (Auto polish, home screen widget, README, final APK verify)
- [x] M6.0 UI fixes + queue freeze/root-cause + queue redesign + mini-player clear
  (1. Library grid/list toggle: root cause was that only the Albums and Artists
  branches read the `grid` flag — Songs/Playlists/Favorites/Downloads rendered
  list-only, so toggling on the default Playlists tab visibly did nothing. Every
  tab now has a grid variant (song cells, playlist cells incl. the pinned
  Favorite/Cached collections, favorites sections, downloads covers) and the
  screen reads the mode from the collected layouts map instead of sampling
  StateFlow.value without subscribing; per-tab DataStore persistence covered by
  `library_layoutToggle_isPerTab_andPersists`.
  2. Queue freeze: root cause was the reorderable library's onMove firing on
  EVERY drag frame straight into `player.move()`, which per frame (a) emitted a
  new queue (full-list recompose), (b) IPC'd `moveMediaItem` to the service,
  (c) wrote DataStore via persistAsync, and (d) re-triggered the
  `LaunchedEffect(currentIndex, queue.size)` auto-scroll `animateScrollToItem`,
  fighting the drag gesture — measured 18.5% janky frames, p99 101ms, 209 slow
  UI-thread frames over 16 gestures (kill -3 thread dump not permitted on this
  build: shell gets Operation not permitted). Fixed with commit-on-drop: onMove
  mutates only a local displayOrder copy, one `commitOrder()` on drop goes
  through the minimal `moveMediaItem` sequence + a single persist, stale drops
  (radio append / swipe-remove mid-drag) are ignored via `QueueCommit`, and the
  auto-scroll is gated on `isAnyItemDragging`. 48 consecutive drags with shuffle
  on and off: no freeze, no ANR, zero FATALs; order commits land visibly.
  3. Queue redesign: top row is source title (marquee) + Radio badge + text Save
  only (Clear and X removed); rows keep 56dp cover/title/Artist-duration/⋮ plus
  an immediate-drag handle (no long-press delay), tinted rounded current row
  with equalizer (playing) or play (paused) indicator, tap jumps, long-press
  actions, swipe-to-remove; pinned bottom bar with live shuffle, close chevron
  and repeat (off/all/one) in sync with Now Playing; neighbors animate with
  `animateItem`, dragged row lifts with elevation; Playing-from entry is now a
  72dp+ target so tap-to-open lands reliably.
  4. Mini-player drag-clear: downward drag follows the finger (offset + fade in
  graphicsLayer, no recomposition), past 160px commits `clearWithUndo()` (stops
  playback, clears queue/persisted queue/source/radio/shuffle, hides the
  player); short releases spring back; horizontal swipe-to-skip and tap-to-open
  untouched. Undo snackbar (~5s, Indefinite + auto-dismiss) restores queue,
  current item and position, paused. Verified on device incl. rotation.)
  277 unit tests)
- [x] M6.1 Android Auto (MediaLibraryService + browse tree + voice + car actions)
  (PlayerService converted from MediaSessionService to MediaLibraryService;
  existing phone behavior unchanged (same ExoPlayer/cache/session-activity/
  scrobble ticker; phone-initiated items already carry URIs and pass through
  onAddMediaItems untouched). Browse tree with exactly 4 root tabs (Home with
  recently-played/newest/random-mix/radio, Library with Playlists incl. Favorite
  songs + Cached plus Albums/Artists, Favorites with songs/albums/artists,
  Downloads), all paged; voice search via onSearch/onGetSearchResult on the
  search3 API ("play <artist/album/song>", artist→top songs, album→tracks,
  shuffle prefix honoured). Car playback (play/pause/next/previous/seek/shuffle/
  repeat) drives the same session player so the Auto queue IS the phone queue;
  browse placeholders resolve to phone-identical stream/download URIs on set and
  the queue mirrors into PlayerManager via adoptExternalQueue without touching
  the controller (start index self-heals through the queue-key transition
  listener); Favorite (toggle star) and Start radio custom actions with car-only
  custom layout; session artwork via a Coil-backed BitmapLoader (placeholder
  fallback, never fails). Root cause found while testing: a custom onConnect
  that replaced the default session commands with session-only commands strips
  the library command set, so every browse call fails PERMISSION_DENIED (-4)
  before any callback runs — fixed with DEFAULT_SESSION_AND_LIBRARY_COMMANDS.
  Verified on-device with a MediaBrowser test client (DHU needs a display and
  was not used): root = 4 tabs, all tabs + second level + paging fetch, pinned
  Favorite songs/Cached present, search round-trip, and play-from-browse starts
  real streaming playback with a resolved URI (4/4 androidTests green, plus 8
  AutoBrowse unit tests). Limitations: onGetItem not implemented (browsers use
  the complete session items instead); self-signed servers may not show browse
  art in the head unit; the radio browse action converges via the phone radio
  path.)

Verified: `./gradlew assembleDebug` green, `./gradlew lint` green (0 errors),
`testDebugUnitTest` 277/277 pass. APK at app/build/outputs/apk/debug/app-debug.apk.
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
- M5 assumptions/limitations:
  1. Crossfade is a **volume ramp** on the single ExoPlayer (fade the tail of the
     outgoing track and the head of the incoming one), not a true two-player
     overlap. A real crossfade needs a second ExoPlayer and would jeopardise
     gapless playback, scrobble timing and queue edits, so the safe version ships
     and the limitation is documented here.
  2. Gapless is an ExoPlayer build-time property; the toggle is persisted and
     applied when the player is built, not switched live.
  3. Stream-cache size is applied to Media3's LRU evictor when the cache is
     constructed, so a changed size takes effect on the next launch.
  4. "Allow cleartext HTTP" is informational: Android's cleartext policy is
     static per app (the manifest's network-security-config already permits it so
     LAN servers work), so the switch records intent without a runtime re-apply.
  5. Downloads use the `stream` endpoint when a transcoded quality is chosen and
     the original `download` endpoint otherwise. Resume uses HTTP Range when the
     server returns 206.
  6. Offline star/scrobble queueing keys off connectivity + the offline-mode
     toggle; the sync worker is scheduled with a connected constraint and retries
     with backoff, so a long offline period simply accumulates then drains.
- M5 device verification (emulator Pixel 7, live server): Settings renders all six
  sections; downloaded a song end-to-end (7.4 MB m4a + 143 KB cover in
  app-private storage, progress + completion notification on the vusic_downloads
  channel); the Downloads tab/manager shows the total and per-row actions; with
  airplane mode ON the offline banner appears, Home still renders from the cached
  library, the downloaded track plays from the local file (verified via
  media_session PLAYING with a monotonically advancing position), radio starts
  from downloaded songs with no error, favorites update optimistically, and the
  queued scrobbles/stars were observed in Room (8 rows) then replayed to 0 pending
  on reconnect with the starred song confirmed on the server via getStarred2;
  switching settings mid-song did not restart playback; zero FATALs.
- M5.5 root cause (Library back navigation): the Library route carries a default
  tab argument, and the screen re-applied it with `selectTab(initialTab)` on
  every entry. Coming back from Downloads & storage re-delivered that stale
  default (Playlists) over the user's Downloads selection. Fixed by persisting
  the tab in the SavedStateHandle and applying each nav-arg value only once.
- M5.5 cache keys: Media3 keys the cache by the full URI, and stream URLs carry a
  fresh salt per request, so nothing could ever hit. `StableCacheKey` keeps only
  the endpoint path plus id/maxBitRate/format and is shared by the player, the
  prefetch writer and the Cached collection. Verified on device: a prefetched
  song plays with a cache hit and zero upstream opens, including offline.
- M5.5 cache note: because the requested quality follows the network (Wi-Fi vs
  mobile), offline playback requests the quality the cached copy was stored at,
  otherwise airplane mode would build a mobile-quality key and miss the cache.
- M5.5 limitations: stream/image cache sizes apply on next launch (Media3/Coil
  fix capacity at construction); "auto offline" and the offline chip share one
  effective-offline rule; the Cached collection prunes itself when the LRU
  evictor removes bytes; Library inline search filters the loaded page only.
- M5.5 device verification (emulator Pixel 7, live server): Library opens on
  Playlists with the specified tab order; Downloads > Manage > back returns to
  the Downloads tab; the Sort sheet shows readable options + direction; Search
  renders the pill, chips, recent searches and the All view (Top result, colored
  Song/Album/Artist titles); Settings renders every card and sub-screen; a song
  downloaded (7.8 MB on disk) and appears in the Downloads tab; prefetch logged
  two completed songs with stable keys and a skipped track produced a cache hit
  with no upstream open; with airplane mode on, the Cached collection listed the
  cached songs and played one from cache with zero network requests and zero
  FATALs.
- M4.7 root cause (the "Nothing playing" text): after Clear the queue is empty
  so `player.current` becomes null, but `showNowPlaying` stayed true and the
  Now Playing overlay's null branch rendered a transparent box with
  "Nothing playing" stretched over every screen. Fixed at three levels: the
  scaffold only shows the overlay while a track exists
  (`NavRules.shouldShowNowPlaying`) and retracts `showNowPlaying` on null, the
  NP null branch now just closes with no placeholder, and the queue sheet
  auto-dismisses on empty. The mini player already returned early on empty
  (zero height, padding reclaimed). Verified: no stray text on Home/Library/
  Search/Settings, restart clean, queue cleared from the persisted store too.
- M4.7 radio: `PlayerManager.startRadio` plays the seed instantly as item 1
  (source "Radio • <title>") and fills 20 in the background via `RadioFetcher`
  (similar count=50, same-genre randoms, plain randoms, silent fallthrough,
  error-vs-empty distinguished so transient failures retry with backoff
  instead of claiming an empty library). Appends fire within 2 songs of the
  end (index>=size-3), tail-only so gapless/current item are untouched, with a
  generation trigger preventing double-appends; a `RadioMemory` session set
  plus the 50-song rolling history keeps repeats out, and artist spread caps 2
  in a row. Radio stays on until playAll/clear; flag+seed persist for restart;
  the queue sheet shows a Radio pill. Device: radio started from a track
  sheet, played instantly, filled to 21 in the background, appended without a
  gap at the end, and kept playing across rotation; logcat zero app FATALs.
- M4.7 Now Playing per the YouTube Music reference: 24dp top gap, near-full
  width 16dp-corner art capped by height (compact budget under 600dp so small
  screens and landscape keep the controls), 18dp artist-to-chip gap, centered
  short title/artist with long-text marquee (clip outside the marquee so the
  scroll stays in its viewport), multi-artist line centered, Favorite chip
  (guarded optimistic toggle converging to the server collection, rollback on
  error), heart/queue row removed, queue entry at the bottom with marquee and
  tap+swipe-up, pill centered and three-dot far right kept. Queue sheet: source
  on its own marquee line, buttons in an aligned row below with 14dp gap,
  single handle, smooth dismiss.
- M4.7 favorites: a virtual "Favorite songs" page backed by `getStarred2`,
  pinned in Library>Playlists and the Favorites chip with a heart-gradient or
  latest-4 collage cover, Play/Shuffle/queue/Play-next/Add-all menu, no
  delete/rename. Every star goes through `FavoritesStore` (reactive refresh via
  shared events); album/artist stars emit too so the Favorites tab stays fresh.
  Every add-to-playlist flow partitions by song id and asks on duplicates
  (Skip/Add anyway/Cancel), reports "Added X, skipped Y", refreshes covers.
  Device: favorited on device and confirmed on the server API, dup dialog
  shown, skip kept 4 songs, add-anyway made 5, duplicate removed again and the
  cover/header refreshed.
- M4.7 notes/limitations: drag-to-reorder and the share sheet still verified
  by inspection only (no adb gesture primitive; avoiding junk shares); the
  details dialogs likewise; the no-similar-songs fallback path is unit-tested
  plus the genre/random legs were exercised live; three independent free-model
  reviews caught 5 real defects (NP sheet z-order, double-fetch trigger hole,
  transient-vs-empty conflation, marquee clip order, refresh-failure wipe),
  all fixed; one uiautomator FATAL in logcat is the test harness's own dump
  tool, not the app.
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
