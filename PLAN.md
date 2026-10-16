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
- [x] M6.3 final polish and release readiness
  (Adaptive app icon already carried a monochrome layer; added an Android 12+
  splash using core-splashscreen with the Vusic note mark on the app background
  and no icon-in-icon chrome. Release build now runs R8 minify + resource shrink
  with a hand-written ruleset (proguard-rules.pro) for Retrofit,
  kotlinx.serialization, Hilt/Dagger, Room, Media3, WorkManager, Coil and the
  service/widget entry points; a local keystore (keystore/, gitignored) signs
  releases, and the task degrades to an unsigned APK when the keystore is absent
  so CI needs no secrets. Security: HTTP logging is compile-time OFF in release
  and credential-redacting (u/t/s/p -> ***) in debug — the old
  HttpLoggingInterceptor and the download-failure log that printed signed URLs
  are gone; verified on the signed release APK that no credentials appear in
  logcat. Error handling: CrashGuard absorbs non-fatal background failures on
  every app-level scope plus non-main threads, while main-thread crashes keep
  the platform default so real UI bugs stay visible. Accessibility: content
  descriptions on all icon buttons (incl. widget and Auto actions) and the 48dp
  target rule, TalkBack-verified on Home/Now Playing/Settings. Performance:
  release cold start 505–888 ms total (am start -W), no ANRs during the full
  flow. Root cause fixed in this pass: the Library grid "Cached" cell built an
  empty PlaylistCover.Collage, and PlaylistArt indexed urls[0] on an empty list
  — a release-only FATAL on opening Library; the collage branch now falls back
  to the styled placeholder for 0 covers and callers use PlaylistCover.select.
  295 unit tests.)
- Verified (M6.3): assembleDebug + assembleRelease + lint green; 295/295 unit
  tests; signed release APK installed on the emulator, full flow exercised
  (login, Home with real art, Library tabs incl. grid/list, playback, queue,
  widget, settings) with zero FATALs and no credential strings in logcat.
- [x] M6.5a gesture fixes
  (Root causes, all found by reading the code and reproduced on the emulator:
  A1 the freeze was an invisible touch-eater. "Is the queue sheet up?" was
  derived from a float (`progress > 0.001f || settleJob?.isActive`) that drags
  wrote directly, while the scrim above Now Playing was `fillMaxSize()` +
  `clickable` with `alpha = progress * 0.62f`. Any gesture that ended without
  reaching the settle callback (cancel, interruption, the queue mutating under
  the sheet, the `isActive` read not being snapshot state so it never re-fired)
  left progress at a tiny non-zero value: the scrim was composed, so it ate
  every tap, while its alpha was ~0 so nothing looked wrong. System back "fixed"
  it because BackHandler drove the one thing that could move progress.
  Fix: a SheetState owns the Animatable AND a boolean `open`; `open` only ever
  clears after the animation has landed exactly on 0, so a closed sheet has
  nothing composed over the screen at all. Every exit (tap-outside, chevron,
  back press, cancelled drag, queue emptied, configuration change) goes through
  settle()/close(). Release and cancel are separate callbacks that both settle,
  so an interrupted drag cannot strand anything.
  A2 half-open sheets: the settle decision now lives in the pure SheetPhysics
  (exactly two anchors, fling velocity + 0.35 position threshold, velocity
  deadzone), and a new drag cancels the running settle instead of racing it.
  A3 the artist page fought the user's own scroll. The hero item's height was
  computed from the scroll offset (`320 * (1 - scrollPx/520) + 96`), so every
  1px of upward scroll simultaneously grew the content above it by 0.615px:
  scrolling up lost ~62% of the gesture, and below 520px of scroll the sheet had
  to "un-collapse" the hero before the hero itself could move. Measured on the
  device: a 1100px up-drag moved the content ~135px. Compounding it,
  `topBar.hideBar = true` hid the shared bar for the whole screen, so once the
  hero was gone there was no back button and no title at all — exactly the
  screenshot. Fix: one scroll container; the hero keeps a fixed height and
  collapses by parallax (translation + fade) on the same scroll offset, so the
  offset maps 1:1 to movement in both directions, and a real collapsed top bar
  (back arrow + artist name, same status-bar inset handling) fades in over the
  list, so nothing is ever clipped and the way back always exists.
  Also fixed here: PlayerManager's scope had been moved to Dispatchers.IO in
  M6.3, so every MediaController call threw "called from a wrong thread" and
  CrashGuard swallowed it — playback silently never started. Back to
  Dispatchers.Main with a comment explaining why it must stay there.
  16 SheetPhysics unit tests. Verified on the emulator: 34 consecutive
  open/close cycles alternating tap-outside, chevron, partial drag release and
  cancelled drag, probing Now Playing's responsiveness after each — 0 freezes,
  no ANR, no app FATAL; a3 up/down scrolling re-tested from the hero, the
  button row and the album grid.)
- [x] M6.5b floating mini player
  (The card was a full-width slab sharing one black block with the nav bar,
  because both lived in Scaffold's bottomBar slot: the bottomBar is an opaque
  bar that reserves the full width, so no content could ever appear beside or
  behind the player. Now only AppNavigationBar stays in that slot (so the nav
  bar keeps its own surface) and MiniPlayer is an overlay inside the content
  area, aligned bottom-centre with a 12dp side margin, 28dp corners, an 8dp gap
  to the nav bar and a 12dp soft shadow. Content scrolls visibly behind and
  around it. Kept: cover, title, artist, previous, play/pause, next and a thin
  progress line, plus the swipe-up-to-open, left/right-to-skip and
  drag-down-to-clear-with-Undo gestures.
  Two inset subtleties, both found on device: (a) Scaffold's content slot fills
  the whole window and hands out innerPadding instead of pre-shrinking for the
  bottom bar, so the nav bar height must stay in the max(ime, bar) bottom
  padding — without it the card aligned to the screen bottom and hid behind the
  nav bar; the max(ime, bar) keyboard rule is unchanged. (b) The clearance for
  the card belongs on the CONTENT (the NavHost), not on the Box, or the Box
  shrinks too and the card floats 90dp above the nav bar. VusicSpacing
  .ContentBottom dropped 160dp -> 24dp now that the scaffold owns the insets.
  Enter/exit and the matching bottom padding are animated together, so clearing
  the queue shrinks both with no jump.
  Also fixed two real playback bugs found while verifying: PlayerManager
  adopted a queue restored by MediaSession.Callback.onPlaybackResumption (a
  widget or media-button press on an empty player filled the service but left
  the UI queue empty, so the mini player and queue sheet stayed blank while
  audio played); and connect() now retries with backoff instead of binding the
  MediaController exactly once — a failed first bind right after a force-stop or
  reinstall left the controller null for the whole process and every UI playback
  command silently no-oped. playAll/skip/seek now await the controller for up to
  3s instead of dropping a cold-start tap. Verified on the emulator: the card
  floats over Home/Library/Search/Settings and detail screens, content scrolls
  behind it, the keyboard still does not leave a black bar, and the clear/Undo
  path is unchanged.)
- [x] M6.5c settings and login polish
  (C1 the login switch had no *visible* label, and the cause was neither a
  missing Text nor clipping: LoginScreen was a bare Column, never a Surface, so
  LocalContentColor kept Compose's default Color.Black and every Text without an
  explicit colour — the "Vusic" title, the "Connect to your Navidrome server"
  subtitle and the self-signed switch's label — was drawn black on the black
  background. The field labels showed because OutlinedTextField takes its colours
  from the scheme, not from the content colour. Fixed by wrapping the screen in
  a Surface with an explicit contentColor. The switch is now a full-width
  labelled row (title + one-line explanation, 56dp min, whole row tappable and
  aligned with the fields), with an animated inline warning when it is on, and
  both buttons share one shape/height/padding. The screen scrolls so the fields
  and buttons stay reachable with the keyboard open at 1.3 font scale.
  C2 the "Settings" and "Library" titles sat 21px apart because ScreenTitleRow
  had no fixed height: the title was only centred inside whatever the tallest
  child happened to be, and Library carries three 48dp icon buttons while
  Settings has nothing but text. One TopLevelTitleBar (fixed 64dp row, one
  inset handler, one padding, one typography) now backs ScreenTitleRow, so
  every top-level screen matches by construction.
  C3 one SettingsActionButton + ChoicePills + UsageLegendRow for every Settings
  screen: always outlined, 1dp, rounded, 40dp min height, 16dp padding, content
  width, left-aligned under its row text (the "Original"/"raw" bitrate pills and
  every Browse/Open/Exempt/Clear/Delete/Reset/Log out button were either stranded
  at the far right or borderless), destructive actions red text + red border at
  78%, and pills flow left-to-right with 8dp gaps. Switches and sliders
  untouched; descriptions cap at 3 lines.
  C4 the usage bar divided each category by Vusic's own usage only and left free
  space out of the bar entirely, so the largest category was always ~100% of the
  bar (347 MB of song cache filled it end to end next to 3.94 GB free). New pure
  UsageBarMath makes every segment — free space included — a share of one
  total, gives non-zero segments a 2% minimum width decided by bytes rather than
  by fraction (so a 1-byte entry next to a terabyte is still visible), and keeps
  the bar summing to exactly 1 by settling the difference on the largest segment.
  The island is rebuilt with 16dp padding, a 12dp rounded bar, a "X used by
  Vusic, Y free" total label, one legend row per item with an 8dp dot, and
  animated segment widths. 11 UsageBarMath unit tests.)
- [x] M6.6a floating mini player fixes
  (A1 the black band: the card had been hoisted out of the bottomBar, but every
  list still carried a 160dp hardcoded bottom contentPadding — a leftover from
  the docked-player era — AND the NavHost added ~80dp of padding below that, so
  content stopped ~240dp above the nav bar while the card only covers 80dp. The
  ~160dp dead zone showed the scaffold background and read as a solid black
  block swallowing rows (e.g. Search's Albums). It was never a painted surface:
  no container between the card and the content has a background. Fix: the
  160dp literals are gone; every list takes its bottom contentPadding from a new
  LocalMiniClearance (24dp breathing room + the animated card claim, 0 when the
  queue is empty), provided by the scaffold — so items scroll behind the card
  and the last item stays reachable. Verified: content visibly behind/around
  the card on Home, Library, Search, Settings + Storage, playlist/album detail.
  A2 the card rode above the keyboard for two stacked reasons: it was aligned
  to the bottom of the IME-padded content box (whose bottom padding is
  max(nav, IME)), and SearchScreen added its own navigationBarsPadding() plus
  imePadding() on every list on top of that. Fix: the card is hoisted out of
  the scaffold's content slot into the full-window box, anchored above the
  measured nav bar height with no IME inset anywhere on it (same as the nav
  bar), so the keyboard draws over both; only the content box keeps
  max(ime, bar). SearchScreen's double insets are removed; Login (outside the
  scaffold, nothing consumes IME there) gets imePadding() so its buttons stay
  above the keyboard. Verified: with the keyboard open the card and nav bar are
  covered, results scroll above the keyboard, no black bar. Note: only the
  emulator was attached (no physical phone), so gesture/3-button-nav mode could
  not be compared; the mechanism (ignoring IME insets) is mode-independent.
  A3 the cover is now a true circle: the card has a fixed 72dp height and the
  56dp cover (card minus 2x8dp padding, Crop, 28dp corner) sits with equal 8dp
  padding left/top/bottom, concentric with the rounded end — no edge can clip.
  The thin progress line moved to the card's bottom edge below the circle, so
  it never cuts it. Gestures unchanged and re-verified: swipe-up opens Now
  Playing, drag-down clears with the Undo snackbar (Undo restores the queue and
  the card), swipe path untested-by-hand but code-identical to M6.5.
  Playback never restarted during any of this; logcat clean, no FATAL.)
- [x] M6.6b spacing and sub-menu animation
  (B1 the title gap was the status inset counted twice, exactly as suspected:
  the scaffold's content padding already starts below the status bar (every
  inset-less screen relies on that), and TopLevelTitleBar added its own status
  inset on top — worse, as dead space BELOW the 64dp row, because the row sat
  at the top of a 64dp+inset box. Measured on-device: title bottom to chip-row
  top was ~200px (~76dp). Fix: the row takes no inset of its own (status comes
  from the scaffold once), the row is a fixed 48dp (Library's 48dp icon buttons
  still fit exactly; both titles stay centred identically), the chip row's
  vertical padding is 8->4dp and the post-title spacer on Settings home is
  gone. Measured after: ~33px (~12.6dp) on Library, and Library/Settings title
  tops are both at y=157 — pixel-identical baselines. Other top-level screens
  (Home header, Search, sub-screen headers) were already tight and are
  untouched.
  B2 the sub-screens were never NavHost destinations: SettingsScreen swaps them
  with a bare `when` on a state variable, so the shared NavHost transitions
  could never apply and screens popped with zero animation. Fix: the swap is
  wrapped in AnimatedContent with slide+fade both ways at the new shared
  Motion.SubScreen (250ms), direction-aware — forward pushes Start, the back
  arrow and the system back gesture both go through go(HOME) which reverses to
  End. Device animation scales verified first: window 1.0, transition 1.0,
  animator unset (=1.0), so animations are fully on. Proven by transition log:
  HOME->SERVER dir=Start, SERVER->HOME dir=End, plus settled screenshots.)
- [x] M6.6c settings rows: controls on the right
  (One shared SettingControlRow for every row with a trailing control: text on
  the left in weight(1f), control on the right vertically centred, 16dp minimum
  gap, text capped at 3 lines and never under the control. The beside/next-line
  choice is the pure RowLayout.decide (12 unit tests): beside only when the
  text column keeps ~35% of the row and the text wraps into at most 3 lines;
  otherwise the control goes on the next line END-aligned. Single buttons and
  dropdowns are capped at 45% (they ellipsize gracefully); segmented controls
  and the bitrate pair are measured whole and stack as one piece — capping the
  pair had crushed the format button's text to zero width, found on-device.
  Segmented single-row selectors (Grid/List, Off/Track/Album, Theme) with equal
  cells and the selected cell filled white; sorts, timeouts and mix length stay
  dropdowns. A 4-cell Theme segmented stacks to the next line END with full
  labels rather than squeezing to initials. Server Save&test/Cancel are now
  outlined (no borderless buttons); Log out is a wrap-content red outlined
  button with its confirm; Downloads management buttons are red with confirms
  (they had none); Library Downloads-tab Manage buttons use the shared style.
  The usage bar's free segment now reuses the secondary-text token at 35%: it
  was SurfaceHigh on a SurfaceHigh card, i.e. invisible, so the bar read as 13%
  long (revisited in the color pass). Verified on the emulator at font scales
  1.0 and 1.3: every sub-screen, no overlaps, no under-runs; narrow widths are
  covered by unit tests (down to 70dp rows), not on-device.)
- [x] M6.7a home pager peek
  (Shared SongPagerSection now reserves endPadding = peek(32) + spacing(12):
  page width = viewport - 4 - 44, so the next page's first column peeks in by
  exactly 32dp (measured on-device: 84px on a 1080px screen). Left margin, ⋮
  inside the page, snapping, dots and Play-all unchanged; the math lives in
  pure PagerPeek with 5 unit tests over 320/360/411/600dp, including a
  100dp+ text-column floor so titles truncate instead of reaching the peek at
  1.3 scale. Follow-up per review: a lone page (no next column) now fills the
  full width instead of stranding the 44dp peek reservation. The last page of
  a multi-page section keeps its trailing gap: HorizontalPager sizes every page
  uniformly, so only the last page could be widened with a custom pager — out
  of scope; the reservation is structural, not a bug.)
- [x] M6.7b mini player ring
  (Linear progress bar removed. Progress is a 3dp ring around the round cover,
  starting at 12 o'clock: track faint (onSurface 22%), progress accent-colored.
  Geometry is exact — 56dp ring box, stroke inset by half its width so nothing
  clips, 46dp cover, even 2dp gap. Card is a full pill (36dp radius); ring and
  cover concentric with equal 8dp padding top/bottom/left. Per review the
  play/pause button stays where it was (prev | play | next on the right) and
  the cover is clean. The ring samples position/duration at ~4Hz in a coroutine
  (250ms linear tweens between samples) and is read in the draw phase, so
  position ticks redraw the Canvas without recomposing the card (position and
  duration are no longer collected as state). Snapped on track change and first
  composition, so a restored queue shows the right progress immediately.
  Verified: ring renders and advances, gestures intact (tap toggle, swipe-up
  open, drag-clear + Undo restore), ring correct after process-kill restore.
  One load-induced cold-start ANR seen on the emulator (4.7s cold start +
  back-to-back commands; main thread in platform Media3 session init, unrelated
  to the ring) — noted, not a regression.)
- [x] M6.7c artwork color scheme
  (One PlayingScheme per track from the artwork: seed = Palette vibrant, else
  dominant, else the theme primary, else the theme container pair. No
  material-kolor / material-color-utilities dependency is used on purpose —
  neither is in the catalog and the engine is ~120 lines of HSL + WCAG
  enforcement in core/ui (Contrast), small enough to own, test and tune with no
  version-compatibility risk. Every pair is ENFORCED at construction, so the
  readability requirement cannot silently regress: onContainer/secondary vs
  container >= 4.5, primary vs container >= 3, onPrimary vs primary >= 4.5,
  surface text/secondary vs both gradient stops >= 4.5. The loop darkens (or
  lightens) the container and falls back to pure black/white, which is
  guaranteed to pass; the ring track is the container tinted 18% toward primary
  and halved toward the container until primary-vs-track clears 3.05.
  Measured over 8 seeds x 2 themes (unit-test log): worst title 5.06:1, worst
  secondary 4.59:1, worst ring 3.62:1, worst ring track 3.14:1, worst
  onPrimary 4.85:1, worst surface text 9.76:1. Exposed via LocalPlayingColors;
  consumed by TrackRow (current-row highlight everywhere), the mini player
  (container, ring, icons, text), Now Playing (gradient backdrop + content) and
  the queue (sheet gradient, current row, played rows). Palette extraction runs
  off the main thread, cached per cover+theme (LRU 24), and every channel
  crossfades over Motion.SchemeChange (450ms). The "Dynamic Now Playing color"
  setting turns it off (composable is passed null), falling back to the theme's
  primaryContainer/onPrimaryContainer. Equalizer overlay gained a dark scrim so
  it stays readable on any art. Two bugs found and fixed on-device: Color(ULong)
  reads the top bits as a color-space id and crashed A hard (ArrayIndexOutOfBounds
  in getColorSpace) — all scheme Longs go through Color(Int) now; and rows with
  Color.Unspecified fell back to the dark LocalContentColor, so upcoming queue
  rows rendered dim — every row now sets its colour explicitly.)
- [x] M6.7d queue restyle
  (Behaviour untouched: same full-screen sheet over Now Playing, same SheetState
  machine with exactly two anchors, stable per-entry keys, drag only from the
  handle, swipe-to-remove, Undo, long-press actions sheet, Radio chip, Save,
  auto-scroll to current, persisted order; no Clear button. Restyled only.
  Background is a vertical gradient from the artwork scheme's surface tones
  (animated on track change), never flat gray/black. Header: large bold source
  title (marquee only when it overflows), Radio chip + Save pill right-aligned on
  the same row, a new "N songs · total duration" secondary line, and ONE
  standard drag handle. Save now uses the scheme's primary/onPrimary instead of
  hardcoded white — in Light theme the sheet is pale, so a white pill vanished.
  The 56dp covers moved from 4dp to 8dp corners and both text lines are one
  column at 16sp/14sp; the drag handle sits in a 48dp touch target. The current
  row uses a 16dp rounded container from the scheme with the equalizer over a
  dark scrim; rows before it are dimmed via the scheme's secondary (>= 4.5:1)
  while upcoming rows are full strength; the ⋮/handle glyphs follow the row
  colour so they stay visible on the tinted container. Bottom bar: off = dim
  glyph, on = scheme primary on a tonal pill, over an OPAQUE gradient scrim with
  56dp list bottom padding so rows never ghost through. Verified in Dark on the
  emulator; Light verification folded into M6.7e (the Light theme itself was
  broken until then).)
- [x] M6.7b dark mode color toning (narrow fix)
  (Dark only; Light is unchanged and was checked for the same oversaturation —
  it is not oversaturated, see the report.) The dark tint was vivid because it
  reused the seed's own saturation: a purple cover produced a bright saturated
  purple row and mini player (measured: row and mini identical, tone 26-42 with
  chroma 5-49).
  Root cause and fix: the scheme worked in HSL lightness with the seed's
  saturation, which has no perceptual meaning across hues and no chroma cap. It
  now works in CIELAB — tone = L*, chroma = C*ab — added to core/ui/Contrast
  (srgb<->Lab, toneOf/chromaOf/labHue, and fromToneChroma which binary-searches
  chroma down to the highest value inside the sRGB gamut, so the tone is exact
  and the chroma never exceeds its cap). The seed now supplies ONLY the hue
  (its Lab hue angle), and every surface sets its own tone and chroma.
  All limits live in DarkToneSpec: highlight container tone 22 / chroma 22;
  mini player tone 12 / chroma 13 plus a tone-26 hairline; accent (ring, mini
  icons/title, queue toggles, Save pill) tone 75 / chroma 34; ring track tone
  30 / chroma 16; queue + Now Playing gradient tone 11 -> 7 / chroma 14; text
  tone 92 / chroma 10 and secondary tone 78 / chroma 12. The scheme gained
  miniContainer and miniOutline, and the mini player now draws a 1dp hairline so
  the near-black pill still reads as a shape against the page background.
  Readability is still enforced by search, not assumed: enforce() walks the tone
  until the ratio passes (4.5 for text, 3.0 for the ring) and falls back to
  black/white, which always passes. Verified over the 8 seeds (white, black,
  gray, yellow, red, blue, pastel pink, null) — measured tones/chromas:
  chromatic seeds highlight 21.7 / 21.7-22.3, mini 11.6-11.8 / 12.7-13.4,
  accent 74.7-74.9 / 33.7-34.2, gradient 10.5-10.9 -> 6.7-6.9 / 14.0-14.4;
  ratios title 10.08-10.12, secondary 6.84-6.89, ring-on-track 4.75-4.77,
  onPrimary 10.56-10.62, surfaces 15.93-16.05 and 17.26-17.31. Gray and missing
  art take the neutral app theme (chroma <= 6) exactly as the setting-off path
  does — one neutral, not two.
  On-device (Dark, cover hues purple/orange/green/red/blue): current-row fill
  #512922 tone 21.8 chroma 21.9; mini fill #301915 tone 11.8 chroma 13.4 vs a
  tone-4.3 page background; ring/accent #F4A496 tone 74.8 chroma 34.0 with the
  title at 8.31:1 and the ring 8.31:1 on the fill; queue gradient tone 10.8 ->
  6.7 chroma ~13.8; Now Playing gradient tone 10.4 -> 6.7 chroma ~13.8 with
  17.7:1 title contrast. Colour animation on track change still works and
  playback never glitched.)
- [x] M6.7e themes: remove amoled, fix light
  (AMOLED is gone: enum, Settings selector (now System / Dark / Light), the
  Theme.kt scheme, and docs. A stored AMOLED — or any unknown string — migrates
  to DARK through the pure migrateStoredTheme(), matched by STRING because the
  enum value no longer exists; resolveDark() maps SYSTEM to the OS live
  (isSystemInDarkTheme is a configuration read, so the activity re-themes with
  no restart). Dark is byte-identical to what it was.
  The Light theme RENDERED DARK, and the cause was not the scheme: all 135
  colour references were hardcoded dark literals in VusicColors, so switching
  the scheme changed nothing on screen. VusicColors' members are now
  @Composable getters onto the active Material scheme and VusicControlColors
  gained a full Light variant, so every call site became theme-aware without
  being edited. The Light scheme itself was also missing most roles (no
  primaryContainer, no outlineVariant, ...) and is now complete.
  Also swept the ~45 remaining hardcoded Color.White/Black/hex literals across
  Nav (top bar, bottom nav container and selected pill), Components (selected
  chip, Play-all pill, Retry pill, pager dots), the Settings buttons and
  segmented control, Library/Artist/Home/Search play buttons and avatars,
  the search fields and cursors, and the actions-sheet icons — all now theme
  tokens. Allowed exceptions, commented in place: scrims over artwork
  (TrackRow and queue equalizer circles, hero gradients, the queue's black
  modal scrim, the Now Playing play circle).
  Status and navigation bar icons now flip with the theme via
  WindowCompat.setDecorFitsSystemWindows-style insets controllers, re-applied
  whenever the theme changes.
  Verified on the emulator in both themes: Home, Library (all tabs), Search
  (keyboard up), Settings + Appearance/Sound/Connectivity/Storage, Now
  Playing, queue, mini player, dialogs — all legible with visible controls;
  switches/segmented/outlined buttons/dividers/nav pill all visible in Light.
  Now Playing and the queue correctly use the LIGHT artwork scheme (pale tinted
  gradient, dark text) rather than a dark gradient with white text. System mode
  verified live in both directions by toggling `adb shell cmd uimode night`.
  7 ThemeModeTest cases cover the migration and the System mapping.)
- [x] M6.6d M6.5 leftover re-verification (fixes were needed)
  (D1 usage island verified: full-width bar with proportional segments (556 MB
  used renders ~13% against 3.63 GB free), "X used by Vusic, Y free" label,
  legend rows, 16dp padding.
  D2 login verified end to end (logged out and back in on-device): white
  title/subtitle on black, labeled 56dp switch row with description and
  animated warning, consistent filled+outlined buttons, keyboard-open and 1.3
  scale both keep every field and button reachable. Self-signed survived logout
  (same store as Settings > Connectivity, as required); a stray tap had
  flipped it off mid-test and it was restored ON, as was an accidentally
  changed mobile bitrate (320 back to 128).
  D3 artist page: (a) the 1100px up-drag now moves content 1079px (was ~135px
  before M6.5a); (b) the collapsed bar (back + name) had a follow-up bug — it
  faded on scroll offset alone, but once the hero scrolls off,
  firstVisibleItemScrollOffset resets and the bar vanished mid-page. Now stays
  at alpha 1 while firstVisibleItemIndex > 0; verified in screenshot.
  (c) scrolling the artist page CRASHED the app: duplicate lazy keys
  (`IllegalArgumentException: Key ... was already used`) — Navidrome repeats
  ids in similar artists (and can in tracks/albums). Fixed by distinctBy id on
  the artist screen's top songs (render and play from the same deduped list so
  indices match), albums and similar artists, plus the same guard on Search
  (all four sections, both layouts) and Home carousels. Library repo-backed
  lists were left alone (no evidence of dupes there).
  D4 gfxinfo, emulator only (SwiftShader; GPU percentiles peg at 4950ms, an
  emulator artifact): Home fling 88.6% janky, Library 69.6%, Settings 83% —
  dominated by image decode during flings (60/413 slow draw commands) on
  software rendering. No M6.5 baseline was ever measured and no physical phone
  was attached, so before/after cannot be compared; nothing in 6.6 runs
  per-frame during scroll (animations are enter/exit and value changes), so no
  animation-caused regression is indicated. Playback never glitched throughout.)
- [x] M6.5d forgotten favorites pager and animations
  (D1 Forgotten favorites was a flat five-row list while Quick picks had a pager,
  dots and a "Play all" pill. Both now render through one shared
  SongPagerSection (4 rows per page, dot row that appears only with more than
  one page, source name passed through for playback history), and the section
  header gained the same "Play all" pill. The dots animate their size and alpha
  rather than popping. Empty state still renders nothing, so the section
  disappears when there is no data. Verified on the device: Forgotten favorites
  shows the pager, the "Play all" pill and a hidden dot row (single page).
  D2 all motion now comes from one Motion object (Fast 150 / Normal 220 /
  Slow 300, a 28ms stagger step, spring variants for anything the finger is
  holding and tween variants for everything else, and a durationScale hook so
  the system animation setting can zero every duration). Added: shared NavHost
  slide+fade transitions in both directions instead of per-screen blocks; the
  Settings home cards fade and slide in with a one-time stagger plus press
  feedback; animated usage-bar segment widths and animated colour on every
  button border, pill fill and switch state; the login warning and helper text
  animate in and out; animateItem() on the Library songs grid so entries fade
  rather than pop. Nothing animates on first frame and nothing gates input.)
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
- [x] M6.2 home screen widget
  (Replaced the static placeholder: 56dp cover, title/artist, previous/play-
  pause/next and a favorite heart; whole card opens the app. Two layouts
  (compact 4x1, large 4x2 with a 104dp cover) chosen by measured width
  (>= 260dp) with resize support; dark Vusic style, rounded corners, cover
  rounded in code (RemoteViews cannot clip). Rendering always goes through a
  persisted SharedPreferences state so the widget is meaningful with the app
  dead; updates are event-driven — the app pushes on track/play/queue/favorites
  changes while alive, PlayerService pushes from its ExoPlayer listener when
  only the service is alive — with a key guard so background transitions never
  spam RemoteViews. Controls talk straight to the MediaSession (controller bind
  wakes the service from a dead process); favorite toggles the star through
  FavoritesStore and the app push corrects the heart. Playing with an empty
  player implements MediaSession.Callback.onPlaybackResumption: the persisted
  queue is rebuilt (same stream/download URIs) and restored paused, so widget
  Play after a process kill resumes instead of failing. Root causes found on
  device: (a) PlayerService.pushWidget read ExoPlayer off the app thread
  ("Player is accessed on the wrong thread" FATAL) — the player is snapshotted
  on Main only, with captured values for the IO fan-out; (b) Play with an empty
  player logged the Media3 onPlaybackResumption note and did nothing — now
  implemented. Verified on the emulator: widget picked/pinned (preview renders),
  Next/Play/Favorite drive the session, heart flips, title advances, controls
  work with the app force-stopped, and Play-with-dead-app resumed the queue from
  cache (cache hit, position preserved, no FATAL); both layouts render. 5 widget
  unit tests. Limitation: with the app process dead and the player empty,
  Next/Prev are no-ops until Play restores the queue.)

- [x] M7.0a baseline numbers
  (Release APK 3,781,759 bytes file / 3,328,525 download; dex 34,814 defined /
  39,047 referenced methods (media3 ~20%); full dependency inventory — every
  direct dep is used (Guava via Media3, Tink+Gson via security-crypto); 125+36
  Kotlin files; lint 123 warnings (72 version-notices); cold start median 1360
  ms / worst 1559 ms, warm median 237 ms / worst 267 ms (`am start -W` x10,
  emulator); PSS idle 113.5 MB → 123.8 MB after 5 min playback → 152.0 MB after
  20 screens; jank percentiles per screen (SwiftShader software GL, ~100%
  legacy-jank — relative only); screen-off 82 s: audio full window, 1 app
  wakeup, 0 alarms/jobs, PlayerService foreground only. Emulator exposes a
  single 60 Hz mode, so 90/120 Hz is not verifiable on-device. Full numbers in
  docs/perf/baseline.md. Runtime measured on a debug-signed release build —
  byte-identical R8 output, only the signature block differs — so the login
  survived; size numbers are from the true release-signed APK.)

- [x] M7.0b dead weight
  (Nothing removable in code or deps: no TODOs/Coming-soon/previews in main,
  all Room entities+DAOs wired, Palette/reorderable/Glance-free widget all
  genuinely used, material-icons-extended has ~100 refs incl. extended-only
  icons (R8 strips the rest), logging already centralized in DEBUG-gated VLog
  (the one raw Log.w is also DEBUG-gated). Wins are packaging-only: ABI splits
  (arm64-v8a phone + x86_64 emulator, no universal) and resConfigs("en")
  (86 → 1 string locale). arm64 APK 3,396,155 bytes file (-385 KB, -10.2%) /
  3,228,994 download (-100 KB, -3.0%); arsc download 96,314 → 13,798 bytes;
  dex method counts byte-identical (34,814/39,047). Smoke-tested on the x86_64
  split: login kept, play, queue, search, settings, no FATAL/ANR.)

- [x] M7.0c runtime tuning
  (94 collectAsState() → collectAsStateWithLifecycle() across 17 files, both
  overloads — collections now pause below STARTED; the mini-player ring keeps
  its direct position.value sampling in a 4 Hz draw-phase loop by design: no
  recomposition, ~zero cost, playback-gated. 9 scroll/pagination reads →
  derivedStateOf (kills 9 lint warnings); contentType on all keyed lists
  (constant, zero-alloc); 5 Modifier params moved first-optional (all call
  sites named — verified). Compose stability report: 145/145 composables
  restartable+skippable, only FeishinApp unstable (never a param) — nothing to
  fix. Startup: lazy Provider<ImageLoader> moves the blocking DataStore read
  out of Application.onCreate; hand-written baseline-prof.txt (exact verified
  methods only; Macrobenchmark would profile the login screen) embedded via a
  new :baselineprofile module (Groovy DSL — KTS accessors don't resolve for
  that plugin here; profile compiler rejects method wildcards, hence exact
  signatures). Debug-only StrictMode added. Verified no-ops: OkHttp (one
  shared API client + one justified Coil client, both @Singleton; gzip is
  OkHttp-default), debounced+cancelled search, Media3-managed service
  lifecycle, Room indexes sufficient (no migration), Coil already sized (25% +
  display-size + hardware bitmaps), Palette on ≤128 px art. Fair back-to-back
  A/B cold start: 1351 ms → 1214 ms (−10%); warm 237 → 191 ms (noise). Bound
  the two unbounded in-memory caches (lyrics LRU-64, search artist-photos
  FIFO-512). Debug-only StrictMode caught 3 main-thread disk reads, all
  fixed and verified zero: EncryptedSharedPreferences creation →
  lazy prefs + IO preload() behind the ready gate (no login flash);
  OkHttp TLS init + Media3 SimpleCache SQLite open → IO warmup of the Hilt
  singletons in FeishinApp.onCreate (cache first); widget DataStore load →
  withContext(IO). Lint 123 → 112, tests 358/358 (366 with §D). Full numbers
  in docs/perf/after.md.)

## Phase 7.1 status (final)
- Release 1.0.0 (versionCode 2) signed with the long-lived key
  (`vusic`, to 2054-03-02). Baseline profile packaged. Lint 112 warnings /
  0 errors, tests 366/366 (debug+release variants counted once: 358 unit +
  8 refresh-rate).
- E2E on the signed release (x86_64, emulator): cold start, login screen
  (readable, labeled self-signed switch, friendly wrong-host error, masked
  password, no credential/log leaks in logcat), cached Home on relaunch,
  play/mini-player/ring/Now Playing, queue open-close x10, drag reorder
  (order persists), swipe remove, all 9 actions-sheet rows, save sheet,
  shuffle on/off + restore, mini drag-down clear + Undo restore, radio
  (16-track queue), search + chips, Library tabs/toggle/sort, artist page +
  scroll, Favorites + Cached collections, song download + offline playback
  with position advancing under airplane mode + reconnect, settings
  sub-screens, theme live-switch, refresh-rate row hidden by design,
  font 1.3 Home/Settings without clipping, media notification with
  artwork/controls/seek, update-over-install, zero FATAL/ANR.
- NOT verified (no real hardware / lost session — see report): correct-password
  login (session was lost when the Auto test run uninstalled the app; no
  credentials available), save-as-playlist completion, logout keep/remove
  choice, scrobble-sync observation, widget tap controls on-device (verified
  in M6.2; adb broadcasts to the exported=false receiver are inconclusive),
  headphone-unplug pause (broadcast did not pause; ExoPlayer built-in noisy
  handling is on, but the pause_on_headphone/resume_on_focus_gain toggles are
  not wired to anything — pre-existing, non-blocking), lock-screen controls
  beyond the notification, 120 Hz/Auto idle-drop/8.3 ms budget (single-mode
  emulator), Bluetooth, battery over long sessions, login screen at 1.3x.
- Auto browse device test: 3/4 pass; auto_playFromBrowse fails on an empty
  first album — browse code untouched since M6.1/M6.3, data-dependent.

- [x] M7.1b release audit
  (versionName 1.0.0, versionCode 2 — history and the installed build were all
  versionCode 1, so 2 updates over everything; applicationId unchanged.
  `clean assembleRelease lint test` green: tests 732/732 (both variants),
  lint 112 warnings / 0 errors, lintVitalRelease clean. Badging: package
  com.feishin.mobile 2/1.0.0, minSdk 26 targetSdk 34, not debuggable, no
  allowBackup deviation. Permissions, each needed: INTERNET (streaming/API),
  ACCESS_NETWORK_STATE (offline detection), FOREGROUND_SERVICE +
  _MEDIA_PLAYBACK + _DATA_SYNC (player service, media + WorkManager download
  FGS types), WAKE_LOCK (playback/downloads), BLUETOOTH maxSdk 30 (headphone
  unplug on old APIs), MODIFY_AUDIO_SETTINGS (audio focus), POST_NOTIFICATIONS
  (media/download notifications, runtime-granted); plus library-added
  RECEIVE_BOOT_COMPLETED (WorkManager) and the system dynamic-receiver
  permission. Exported: only MainActivity (launcher) and PlayerService
  (Android Auto + media buttons); widget receivers and WorkManager internals
  are not exported. apksigner: v2-signed, cert DN CN=Vusic + SHA-256 matching
  the keystore (NOT the debug cert), both splits. arm64 APK 3,396,155 bytes
  (arm64 .so only), x86_64 3,395,257 bytes; baseline.prof packaged in both.
  Security: strings.xml has no URLs/secrets, release logging is compile-time
  off (VLog + RedactingHttpLogger are BuildConfig.DEBUG-gated), credentials
  live only in EncryptedSharedPreferences.)

- [x] M7.1c end-to-end — no fixes commit (nothing release-blocking found).
  Full checklist run on the signed release; pass/fail per item and the
  explicitly-not-verified list are in the final report (commit message for
  M7.1c omitted per the "only if fixes are needed" rule).
- [x] M7.1d docs and release notes (README settings overview +
  keystore/fingerprint/versioning, CHANGELOG 1.0.0, docs/RELEASING.md,
  release/ APKs + SHA-256, v1.0.0 tag, root cleanup of two stray 0-byte
  files).

- [x] M7.0d high refresh rate (nothing in the app caps frame pacing: the
  60 Hz audit found no frame-counting — ring/marquee/equalizer/scroll
  physics are all time-based)
  (New pure selector core/ui/RefreshRate: highest same-resolution rate for
  AUTO/MAX, nearest-to-60 for STANDARD, ties prefer current,
  higher-rate-at-lower-resolution never wins; RefreshRateTest 8 cases green.
  Applied in MainActivity through public window hints only:
  preferredDisplayModeId always, preferredRefreshRate 0 for Auto / peak for
  Max / 60 for Standard; Surface.setFrameRate verified present in
  compileSdk 34 but has no public accessor for the activity window's
  Surface, so deliberately unused. Re-applied on create/resume/config-change
  and live on setting change. The one allowed UI addition: Settings >
  Appearance & Layout > "Refresh rate" (Auto default / Max / Standard
  60 Hz), DataStore-backed, live-applied, hidden on single-rate devices,
  with a "Running at X Hz" secondary line. The emulator exposes a single
  60.000004 Hz mode and ignores the min/peak_refresh_rate override (tried,
  reset), so 90/120 Hz rendering, Auto idle-drop and the 8.3 ms budget are
  NOT verifiable here — stated in after.md. Verified instead: row hides
  correctly, preferredDisplayMode=1 confirmed live in the window attrs, no
  FATAL/ANR across all apply paths, StrictMode clean, no 60 Hz regressions.)

- [x] M7.1a keystore hygiene
  (Release keystore `keystore/vusic-release.jks` + `keystore/keystore.properties`
  (keys: storePassword, keyAlias, keyPassword) are untracked and .gitignore-covered
  (`keystore/`, `*.jks`, `*.keystore`); `git log --all` shows neither was ever
  committed and the password values appear nowhere in history or tracked files.
  Signing config reads only that untracked file (falls back to unsigned when
  absent). Alias `vusic`, validity 2026-10-15 → 2054-03-02 (~27.4 years, over
  the 25-year minimum — no replacement, which would break updates). Reference
  copy + SIGNING-INFO.txt (no passwords) at D:\Feishin-release-signing\ —
  outside git by design, back it up. Certificate SHA-256:
  33:99:ED:FC:E8:D6:82:E4:B1:99:D8:0A:A2:6D:6B:09:2B:78:EF:BE:C9:29:77:F9:49:1F:27:97:BD:CC:66:2C.)

Verified: `./gradlew assembleDebug` green, `./gradlew lint` green (0 errors),
`testDebugUnitTest` 295/295 pass.
LIVE on-device test (emulator Pixel 7, API 34) against a live Navidrome
server: login OK,
Home with real albums/art, album detail (17 tracks), streaming playback confirmed
(mini player + Now Playing 0:33/3:42 + audio focus), zero FATAL crashes.
Server API probe (kept outside the repository, credentials via environment
variables only):
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
- AuthRepository.ready flips true after the credentials preload finishes on IO
  (lazy EncryptedSharedPreferences creation + read); AppNav gates the nav
  graph on it, so there is no login flash. No disk reads happen on the main
  thread at startup (verified with debug-only StrictMode: zero app sites).
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
