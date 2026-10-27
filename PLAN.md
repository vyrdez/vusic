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

## Phase 7.2 status: published
Published at **https://github.com/vyrdez/vusic** (public, GPL-3.0-only),
default branch `main`, first release
<https://github.com/vyrdez/vusic/releases/tag/v1.0.0>.

- Pre-publish audit: no credential ever existed in the tree or history
  (keystore passwords: 0 matches in every commit). Personal data that DID exist
  in history — the server hostname, the Windows username (also reused as a test
  fixture), a `local.properties` carrying `sdk.dir`, and 2,835 committed build
  artifacts — was removed by rewriting all commits with `git filter-branch`
  (index-filter for images/build/`local.properties`, tree-filter for the text
  scrub), then expiring the reflog and garbage-collecting: 218 MiB -> 585 KiB.
  The M1–M7 history and the v1.0.0 tag were preserved (5 image-only commits
  pruned). Production code was verified byte-identical to the audited release
  commit, so the published APKs still match the published source.
- No screenshots, screen recordings or third-party app captures are published:
  every image file was stripped from every commit and is now gitignored
  (except application resources under `src/**/res`, which stay tracked).
- Release APKs are no longer committed; they exist only as GitHub Release
  assets (attached, then re-downloaded and re-verified for SHA-256 and
  signature).
- Identity: pre-existing commits are `feishin <feishin@local>` (a placeholder
  with no personal email, so no rewrite was needed); new commits use
  `289428354+vyrdez@users.noreply.github.com`.
- Repo settings: Issues on, Wiki/Projects/Discussions off, topics set, secret
  scanning + push protection enabled.
- CI: `.github/workflows/build.yml` (JDK 17, preinstalled Android SDK,
  `assembleDebug testDebugUnitTest lint`, no secrets, no release signing) is
  green on `main`. Reaching that fixed three workflow/environment bugs
  (setup-android's cmdline-tools 16.0 license failure, `sdkmanager` missing from
  PATH, and a lost executable bit on `gradlew`) plus two genuinely flaky tests in
  `ViewModelStateTest` that only surfaced under CI load. No app code, behaviour,
  colour or layout was changed in this phase.
- A fresh clone builds and tests clean with only `ANDROID_HOME` set — no
  `local.properties`, no keystore.

## Phase 8 status: gesture correctness, offline banner, playlist sort & search

Version 1.1.0 (versionCode 3) — `applicationId com.feishin.mobile` unchanged,
same release keystore as 1.0.0/7.1. Nine commits, one per section; reference
material (videos + Metrolist screenshots) is in `docs/reference/phase8/` and the
"before"/"after" captures in `docs/reference/before-8/` and `after-8/` (all
gitignored). Two things were NOT undone: the floating mini player and the queue
`SheetState` machine were extended, not replaced, and there is no new Room
migration (all new preferences go to DataStore).

- **P8-A queue-sheet gestures.** Five root causes in one interaction:
  (1) `SheetState.settle()` read `anim.value`, but `snapTo` had only *queued* its
  coroutine, so a release was judged on a stale pre-drag position;
  (2) `open` flipped only *after* `animateTo`, so tapping "Playing from" animated
  an uncomposed sheet and it popped in;
  (3) `PlayingFromBottom` used `detectVerticalDragGestures` and hardcoded
  `onDragEnd(0f)`, throwing the fling velocity away;
  (4) the sheet's `NestedScrollConnection` handled `onPostScroll` but never
  `onPostFling`, so a fast fling in the list at the top moved the sheet with no
  settle ever firing; (5) `SheetPhysics.POSITION_THRESHOLD` was 0.35 — far too
  high. Fix: a synchronous `dragValue` mirror used by `settle()` (re-anchored
  from `anim.value` when a settle is interrupted), compose-before-animate on
  open, a real `draggable` with true `onDragStopped` velocity for the peek strip,
  `onPostFling` on the nested-scroll connection (settles with the fling velocity
  and consumes at the top while open), and `POSITION_THRESHOLD = 0.18`.
- **P8-B mini player gestures.** The pill used `detectDragGestures` (no velocity),
  a downward-only axis test, `translationY` only, and an alpha fade as the
  "clear". Rewritten around the pure `MiniPlayerGesture.classify`: a real axis
  lock (vertical wins ties), the pill tracks the finger on both axes, fling-aware
  thresholds, direction conventions LEFT = previous / RIGHT = next / DOWN =
  clear / UP = open (this inverts the pre-1.1 left/right mapping, as specified),
  and the first/last track with repeat off springs back instead of restarting.
  Progress ring, floating shape, dynamic colour and transport are untouched.
- **P8-C offline banner.** Root cause: `OfflineBanner` was an overlay
  `align(TopCenter)` inside the *same* `Box` as the `NavHost`, so it painted over
  every screen's title and filter row instead of pushing them down (see
  `before-8/C-03`). It is now an `AnimatedVisibility(expandVertically)` row in
  the shared `Column` above the `NavHost`: one change fixes every screen, and the
  height is animated so content glides instead of jumping.
- **P8-D library search.** The field appeared instantly; it is now
  `AnimatedVisibility` + `expandVertically` at `Motion.Normal` (~220 ms), focus
  is taken after the animation starts (so the field is never focused while
  invisible) and the filter chips shift with the same motion.
- **P8-E playlist sort & search** (the one new feature). Pure `PlaylistOrdering`
  (index-based sort/filter, 27 tests) behind a shared `PlaylistSortRow`:
  Playlist order / Title / Artist / Date added / Duration, tap-to-toggle
  ascending/descending with an arrow, plus a search toggle filtering that
  playlist's tracks by title, artist or album. Applies to normal playlists,
  Favorite songs, Cached and Downloaded. A sorted or filtered list is read-only:
  removal resolves the track by id against the stored order (never the visible
  row) and is disabled while sorted/filtered, and Play/Shuffle use the visible
  list. Sort is remembered per playlist in DataStore; nothing is ever written
  back to the server. `Track` carries no date field, so Date added falls back to
  the server's order.
- **P8-F cache limits.** Root cause: the song- and image-cache sliders were built
  from `StorageLimits.steps(deviceMax)` (up to the whole device) while the stored
  value is clamped by `CacheSizePolicy.normalizeStream/normalizeImage` (top
  5 GB / 1 GB), so any larger pick bounced straight back — the "5 GB plateau".
  Both sliders now use `StorageLimits.cacheSteps(supportedBytes, deviceMax)`, the
  intersection of the cache's real sizes with the device, which is never empty;
  the clamp and the live-shrink behaviour are unchanged. The download cap keeps
  the device-bounded `steps()`. 10 new tests.
- **P8-G high refresh rate.** Two bugs. "Running at X Hz" read
  `display.refreshRate` during composition — not snapshot state — so nothing
  recomposed the row when the system changed rate and the line froze on its first
  value; it is now fed by a `DisplayManager.DisplayListener`. And STANDARD
  derived its `preferredRefreshRate` from the nearest mode's rate, so on a panel
  with no 60 Hz mode (90-only / 120-only) "Standard 60 Hz" asked for the higher
  rate and did nothing; `RefreshRate.requestedRate` now returns exactly 60 for
  STANDARD, the peak for MAX and 0 for AUTO. 3 new tests.
- **P8-H app logo.** The launcher drew a bespoke two-note beam; the app's own mark
  is the eighth note (`Icons.Default.MusicNote`, the glyph beside "Vusic").
  `ic_launcher_foreground` is now that white note centred in the 66 dp safe zone
  of the 108 dp canvas, the background is black, `ic_launcher_round` +
  `android:roundIcon` were added, the splash mark is the same note, the Media3
  playback notification is branded with a new `ic_stat_vusic` silhouette, and the
  login screen shows the note beside the wordmark. No old asset was orphaned, so
  none were removed. Masks were rendered from the icon's own path data
  (`after-8/H-01..H-04`) and the launcher's real circular mask was confirmed on
  the emulator (`H-05`), as was the playback-notification icon (`H-06`).

Verification (2026-10-17): `assembleDebug`, `assembleRelease`, `lint` (0 errors)
and `test` all green; 437 unit tests pass, including the new `SheetPhysicsTest`,
`MiniPlayerGestureTest`, `PlaylistOrderingTest`, `StorageLimitsTest` and
`RefreshRateTest` cases. Emulator walkthrough (Pixel 7, API 34, Dark and Light,
font scale 1.3, offline toggled on and off): queue open by tap / short drag /
long drag / fling and close by drag (each ≥ 10 times, including fast repeats),
dead-zone drags, the mini player in all four directions plus the first/last edge
cases, the offline banner on Home/Library/Search/Settings/detail/Now Playing,
library search, playlist sort/search on a normal playlist, Favorites, Cached and
Downloaded, and the storage sliders; playback never glitched, restarted or
skipped. Not verifiable on the emulator (reported, not claimed): the 60 Hz pin
itself, because the AVD exposes a single 60.000004 Hz mode so the row is hidden —
and even on a real phone vendor smart-refresh (e.g. HyperOS) can override the
request — and real launcher masks other than the emulator's.

Limitations: `Surface`/`Window.setFrameRate` is still unreachable for the
activity window in compileSdk 34, so the public window hints remain the only
lever for the refresh rate; the cache ceilings stay at the app's supported sizes
(5 GB stream / 1 GB image) rather than the whole device; and date-added sort
falls back to server order because `Track` carries no date field.

### Phase 8 release review fixes

An independent review of the phase-8 diff found no blockers but five
should-fix issues, all fixed before the 1.1.0 tag. Each was verified in the
source, not taken on the reviewer's word.

- **Mini player: a same-direction double swipe was swallowed and could strand
  the card.** `pendingSkip` was both the `LaunchedEffect` key and the
  direction, so two fast swipes in the same direction wrote the same value,
  the effect never restarted, and that swipe did nothing - while `dragX` was
  left wherever the finger released it (up to `cardW * 1.2f`), leaving the
  mini player permanently translated off to one side. Split into a
  monotonically increasing `skipSeq` (the trigger) and `skipDir` (the
  direction). Separately, the reset block in `finally` called `snapTo`, which
  *suspends* and therefore throws immediately on the cancelled coroutine it was
  written for - so the "never strand the card" guarantee the comment promised
  did not hold. It now runs inside `withContext(NonCancellable)`.
- **Playlist removal could delete the wrong copy.** `removeTrack` resolved the
  server entry index with `indexOfFirst { it.id == track.id }` and threw the
  source index away, so on a playlist holding the same song twice, removing the
  second copy deleted the first - exactly what the row key
  (`"${id}#$src"`) exists to prevent. `canMutateOrder` already guarantees the
  visible order *is* the stored order, so the source index is provably the
  server index: replaced with `removeAt(src)`.
- **A queue clear could commit from contradictory input.** `classify` gated its
  downward *distance* term on direction but not its downward *velocity* term, so
  dragging up and flicking downward on release (dy < 0, velocityY > 0) cleared
  the queue. The velocity arm is now gated on `dy > 0f`. A short genuine
  downward flick still clears.
- **The stale-sort fallback only covered the chips, not the data.** On the
  Downloads screen the fallback lived inside `PlaylistSortRow`, while the list
  was ordered by the *raw* sort - so a persisted `DURATION` (not offered there)
  left the chips showing nothing selected while the list was silently a no-op
  sort (`durationAt = { 0 }` for every row). Lifted to the screen; the chip row
  and the list now receive the same resolved sort.
- **`PlaylistOrdering.matches` compiled a `Regex` per row per keystroke.** The
  whitespace split pattern is hoisted to a private val; `visibleIndices` re-runs
  for every track on every character typed, so a large playlist was compiling
  thousands of `Regex` objects per keystroke.

Also cleaned up while in these files: two unused imports in `MiniPlayer`,
a duplicated dead `when` branch in `PlaylistOrdering.order`/`visibleIndices`
(PLAYLIST_ORDER and DATE_ADDED are handled by the early return above it), an
unreachable `size <= 1 && index < 0` clause in `hasPrevious` (`index` is
always >= 0), and three files missing a trailing newline. Two tests that
could not fail were replaced with real assertions: `classifyAlwaysReturnsAKnown
Action` asserted a non-null `Action` was in `Action.entries`, and
`zeroThresholdStillNeedsSomeTravel` duplicated an existing case - both now pin
exact expected values, with new regression tests for the contradictory-fling
and short-flick cases.

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
## Phase 8.2 status: queue close at top, song cache limit, mobile-data option (1.1.2)

Three sections, one commit each, version bumped to 1.1.2 / versionCode 5 in
`0ea217b`.

- **P8.2-A queue close at top** (`0ea217b`). A LazyColumn installs an
  overscroll effect by default, and Compose hands the WHOLE drag to it the
  moment the list is at its first item - the delta never enters the
  nested-scroll chain, so the sheet's handoff callback received
  `available.y == 0` on every frame (106 consecutive zero frames measured on
  the emulator). The fix is a pass-through `OverscrollEffect` on the queue's
  track list plus `QueueHandoff`, a pure function that decides whether the
  list or the sheet owns a delta, taken in `onPreScroll` before the list
  sees it. 16 unit tests.
- **P8.2-B song cache limit** (`b395f73`). The 5 GB survived Phase 8 because
  `CacheSizePolicy.STREAM_SIZES_MB` - the table the slider is derived from -
  still ended at 5120 MB; the slider was never the thing holding the limit
  down. The table now runs to 100 GB, the song-cache rail is the whole
  range (not trimmed to the device), the slider warns when the choice is
  bigger than the phone has, and the cache stops growing below 1 GB of free
  space instead of deleting anything. 24 unit tests.
- **P8.2-C mobile-data option** (`c7773e0`). Two switches in Settings >
  Connectivity > MOBILE DATA, both off by default. The data layer already
  had the gates; the download switch had no UI at all, the auto-cache gate
  compared the transport NAME (a metered hotspot counted as free), and
  nothing re-evaluated the queued work when a switch changed. The rule is
  now `MobileDataPolicy`: the real `NET_CAPABILITY_NOT_METERED` capability,
  never while offline, never while roaming. 17 unit tests.

Also fixed on the way: the settings sliders had one discrete position too
few, so one size per rail could not be picked at all (the old song-cache
rail skipped 2 GB), and the last step of a rail was rounded down.

Reference: `docs/reference/before-8.2/` and `docs/reference/after-8.2/`.

## Phase 8.3 status: offline hint placement, queue swipe-remove, queue restore, Library open time (1.1.3)

Four sections, one commit each, version bumped to 1.1.3 / versionCode 6
in a single prep commit for consistency.

- **P8.3-A banner remnant behind the search bar** (OfflineBanner).
  The Search screen emitted its offline hint `Text(...)` as a sibling *before*
  `Column(Modifier.fillMaxSize())`, so it painted underneath the search pill at
  the top of the NavHost content box — a half-clipped line behind the field.
  Fix: shared `OfflineBanner(text)` component (`ui/components/OfflineBanner.kt`,
  layout row, `clipToBounds`, real height) used by both the Nav banner and the
  Search hint. The Search hint is placed *inside* the Column after
  `FilterChipRow`, suppressed when `offlineBannerVisible` (passed from Nav.kt).
  Banner states: (1) `offlineMode` on → shared banner (respects `showOfflineChip`);
  (2) `autoOffline` on + network down → same shared banner; (3) both off +
  network down → no shared banner, Search shows its own hint row; (4)
  `showOfflineChip` off → no banner, no Search hint; (5) online → nothing.
  Settings' `OfflineChip` is untouched. Root cause reproduced on device.
- **P8.3-B queue swipe-remove** (QueueOps / PlayerManager / NowPlayingScreen).
  Root cause reproduced on device: one swipe removed 6 songs (50 → 44) — the
  dismiss `confirmValueChange` is remembered from the first composition of each
  row (M3 `rememberSwipeToDismissBoxState`) plus per-row `remember(item.queueId)`
  guard, so a swipe event replayed across settling recompositions removed more
  than one row. Fix: positional removal replaced by removal by stable queue item
  id (`QueueOps.removeById(queue, id, idOf, currentIndex)`, `PlayerManager.removeById`)
  with `AtomicBoolean` per-row guard + `rememberUpdatedState`; idempotent under
  repeated/rapid/during-settle dismiss; removing current/last/only works (single-item
  removal has no Undo today — reported, not added). Unit tests appended to
  `QueueOpsTest.kt`: remove by id with duplicates, double callback, remove current,
  remove last, remove while another item moved. Reorder, queue styling and sheet
  gestures untouched.
- **P8.3-C paused queue/mini player lost after app kill** (PlayerManager,
  QueueStore, PlayerService, DataStore).
  Root cause: nothing persisted across process death; restore bailed permanently
  when credentials were missing and stopped playback entirely on restart.
  Fix: queue items (stable ids + song id, title, artist, album, duration, cover id,
  download/cache flags), current item, position, repeat, shuffle state + original
  order, "Playing from" label persisted to DataStore (`feishin_queue`, a single
  small JSON blob — no queries, no Room migration needed; DataStore chosen over
  Room because there are zero relational queries and QueueStore already existed).
  Saved continuously and off-main: queue edits/pause/item change via `persist()`
  (`Dispatchers.IO`, skips clearing when empty and not yet restored),
  position every ~6 s (`savePosition`, side key `queue_position`), `onTaskRemoved`/
  `onDestroy` via `saveBlocking(1_500)`. Restores PAUSED at the saved position
  without foreground service, autoplay, audio focus or network (`setMediaItems` +
  `seekTo`, no `prepare()`); first play press starts normally; drag-down dismiss and
  "clear queue" clear persisted state; missing songs stay and are skipped by normal
  error handling (never crash); 100+ songs restore with the current item first;
  killed-while-playing also restores paused; 10 s credential await instead of
  permanent bail. Unit tests in `QueueStoreTest.kt` (round trip with repeat/flags,
  position-only write, position without snapshot, clear drops side key, save replaces
  side position). Mini player look/gestures, scrobbling rules and queue sheet untouched.
- **P8.3-D Library opens slowly on slow connections** (cache-first tabs).
  Root cause: every Library tab waited for its network request before rendering
  (`UiState.Loading` until the last of six requests answered; 4.3–4.7 s cold start,
  11.9 s warm tab switch measured on GSM + 800 ms). Fix: existing `MetadataCache`
  (Room, in the schema — no migration needed) now serves cache-first and decodes on
  `Dispatchers.IO`; new `LibraryRefresh` singleton (policy, `refreshed`/`stale`
  SharedFlows, `active` counter, `invalidateAll()`/`invalidate(key)`) and pure
  `CachePolicy` (per-key throttle ~3 min, one-shot force per pull-to-refresh, a
  failed attempt still consumes its window); `MusicRepository.cached()` is cache-first
  with background revalidation; `NetworkModule` API client gets 5 s connect / 10 s
  read / `retryOnConnectionFailure(false)`. LibraryViewModel + detail ViewModels
  render cache, reload silently on `refreshed`, keep old data on error, force-refresh
  after mutations; pull-to-refresh stays manual; optimistic local update after user
  changes; offline/no-network uses the same instant local path; playlist/album/artist
  pages cache-first; failed refresh keeps old data + small disappearing
  "Couldn't refresh". Unit tests in `CachePolicyTest.kt` (7 tests).
  Timings recorded in `docs/perf/library-8.3.md`; downloads logic, search, playback,
  offline banner and Library sort/layout settings untouched.

Reference: `docs/reference/before-8.3/` and `docs/reference/after-8.3/`.

## Phase 8.4 status: messages above mini player, download performance, save artist to playlist, login errors and restyle (1.1.4)

Four sections, one commit each, version bumped to 1.1.4 / versionCode 7.

- **P8.4-A in-app messages above the mini player** (Nav.kt, MessageHost, MiniPlayer).
  Root cause reproduced on device: the `SnackbarHost` lives inside the Scaffold's
  content slot while the floating mini player is hoisted above the scaffold in a
  separate full-window `Box`, so snackbars paint underneath the card and their
  text is cut off. Fix: the snackbar host is removed from the scaffold and a new
  shared `MessageHost` composable is placed after the mini player in the NavHost
  `Box` (correct z-order). It reads the mini player's measured bottom edge via
  `onVisibleChanged` / `onDragOffsetChanged` callbacks from `MiniPlayer` and the
  nav bar / IME insets measured in `Nav.kt`, then positions itself above the
  card (or above the nav bar when the card is hidden, above the keyboard when
  open) with an animated gap. Messages move smoothly as the card appears,
  disappears, or is dragged. Mini player look/gestures untouched.
- **P8.4-B download performance** (DownloadWorker).
  Root cause measured on emulator: `publishProgress()` wrote to Room on every
  64 KB chunk (~80 Room writes per 5 MB song), invalidating `observeAll()` and
  recomposing the whole UI for every chunk of every concurrent song — 89-song
  "keep downloaded" produced thousands of Room invalidations and visible jank.
  Fix: Room progress writes are throttled to ~2/sec and only when the percentage
  changes by a visible step (in-memory between writes, persisted on completion /
  pause); notification updates are throttled to ~1/sec. Enqueue remains a single
  `upsertAll` transaction. Already-downloaded songs are skipped.
  Tests added for batch enqueue (one transaction, only missing songs), progress
  throttling (a flood of updates yields few emissions), and resume-at-start.
  Measurements saved in `docs/perf/downloads-8.4.md`.
- **P8.4-C save all artist songs to a playlist** (TrackActionsSheet, MusicRepository).
  The artist page's three-dot "Save to playlist" previously showed
  "Open a song to save it to a playlist". Fix: it now gathers every song by the
  artist across all albums (off-main, deduped by song id, ordered by album year
  then track number), shows "Loading songs..." while gathering, and opens the
  existing `PlaylistPickerSheet` with all tracks (default new playlist name =
  artist name). `MusicRepository.addToPlaylist` and `createPlaylist` batch Subsonic
  `updatePlaylist` / `createPlaylist` calls at 100 song IDs per request so large
  artists (500+ songs) work. Existing playlist entries are skipped; the result
  message says "Added N songs to <playlist>. M were already there." Offline
  explains a connection is required. Artist page's other actions unchanged.
  Unit tests for song gathering (duplicates, ordering, empty artist, one album),
  batching, and already-in-playlist skip.
- **P8.4-D login errors and restyle** (LoginScreen, LoginViewModel, AppNav).
  Root cause: on failure the `LoginUi` state was replaced with a fresh
  `LoginUi()` dropping `busy`/`message`, and the error text was just an inline
  `Text` below the fields — no specific message, no snackbar, fields could lose
  focus. Fix: a failed "Test & Connect" / "Test connection only" never resets
  the form (URL, username, password all kept via `rememberSaveable`/`remember`),
  shows an inline themed error card above the buttons (icon, 1–2 lines,
  dismissible, clears on field edit) AND a snackbar per section A. Specific
  friendly messages per failure class (wrong credentials, unreachable server,
  invalid URL, untrusted certificate, non-Navidrome server, generic with details).
  While testing: button shows progress indicator, fields disabled, 10 s timeout.
  Restyle: filled rounded fields matching the app's search field, pill-shaped
  primary/secondary buttons, app logo mark + "Vusic" title, self-signed switch as
  a Settings-style row with the switch on the right. Works in Dark/Light/dynamic
  color. IME Next/Done, password show/hide, TalkBack labels.
  Authentication/token logic and credential storage unchanged.
  Unit tests for error mapping (each case). Form-state preservation is structural:
  typed values live in the composable's own state, so a failed attempt never
  resets them; verified manually on a throwaway Navidrome login flow.

Reference: `docs/reference/before-8.4/` and `docs/reference/after-8.4/`.

## Phase 9 (v1.2.0, versionCode 8)
- **P9-A synced lyrics**:
  - Full LRCLIB client with candidate scoring, multi-variant search, duration comparison, and fair-use caching.
  - OpenSubsonic structured lyrics fallback (`getLyricsBySongId` with line start times) and embedded file tags (ID3v2 USLT/SYLT, Vorbis/FLAC, MP4).
  - Room `lyrics_cache` table (migration 5 -> 6, verified by instrumented test `LyricsCacheMigrationTest`).
  - Synced lyrics renderer in Now Playing: binary-search active line, auto-scroll to 32-35% viewport anchor, drag-to-browse with 3.5s resume timer and floating "Sync" pill, "♪" instrumental markers, 3-dot intro countdown, source footer, and "Search again" menu action.
  - 11 lyrics settings in DataStore (`SettingsStore.kt`), `SettingsViewModel.kt`, and `SettingsScreen.kt`.
  - Unit tests: `LrcParserTest`, `LyricsLookupTest`, `LyricsSelectionTest`, `LrclibMatcherTest`, `EmbeddedLyricsExtractorTest`, `LyricsRepositoryTest`.
- **P9-B device files**:
  - Manifest audio permissions (`READ_MEDIA_AUDIO` on API 33+, `READ_EXTERNAL_STORAGE` on API <=32).
  - Pure filter and sort module `DeviceFilesFilter` with duration filter (0, 15s, 30s, 60s) and ringtone/alarm/notification exclusion.
  - Custom Coil `LocalAudioCoverFetcher` decoding local album art and embedded picture bytes without blocking the main thread.
  - ContentObserver live updates in `DeviceFilesViewModel` for automatic MediaStore sync.
  - `DeviceFilesScreen` with Songs, Albums, Artists tabs, inline search, local album/artist detail views, and permission explanation card.
  - "View my" bottom sheet dialog on the Library title with animated chevron and smooth crossfade between server and device music.
  - Mixed queue support in `QueueStore`, `PlayerManager`, `PlayerService`, and `TrackActionsSheet` (server-only actions like scrobble, star, download, and remote playlists hidden for local tracks).
  - Unit tests: `DeviceFilesFilterTest`, `QueueStoreTest` (mixed queue persistence).
- **P9-C dynamic theme label**:
  - Renamed "Dynamic Now Playing color" to "Dynamic Theme" in Settings > Appearance & Layout.
  - Preserved DataStore preference key `dynamic_np_color`.
  - Raised `versionCode` to 8 and `versionName` to `"1.2.0"`.

## Phase 9.1 (v1.2.1, versionCode 9)
- **P9.1-A provider chain**:
  - Lookup chain over LRCLIB, BetterLyrics (TTML word timing), KuGou (KRC word timing) and
    local metadata. Paxsenix is wired in but disabled: its public endpoint is gone.
  - Sources run in the user's order with a per-call timeout (9s) and a 15s budget per song.
    A provider that throws is skipped instead of aborting the chain.
  - Settings > Lyrics > "Lyrics sources": per-source switches, up/down buttons for priority,
    "Reset order", "Prefer word-by-word lyrics".
  - No migration from the old priority switch; the old key stays in DataStore and is ignored.
- **P9.1-B lyrics menu**:
  - 3-dot sheet on the Lyrics tab: Edit (LRC/plain override, cached for the song), Refetch,
    Search (LRCLIB by title/artist) and Copy.
  - Switches for agent positioning, interval indicator, offset and romanize-current-track.
- **P9.1-C lyrics text size**: +/- buttons, 80-220%; SMALL/LARGE still parse as 85/125.
- **P9.1-D romanize lyrics**: dual-line readings (Hepburn / Revised Romanization / Pinyin).
- **P9.1-E remember lyrics tab**: the Song/Lyrics selection is persisted in DataStore.
- **P9.1-F big playlist shuffle**:
  - Queue item building and the download-path lookup run off the main thread with one
    batched query.
  - `BoundedQueuePlayer` publishes a 100-item window around the current track to the
    session, so external clients never receive a 3,000-item queue.
  - Queue restore prefers the persisted snapshot over the published window (the window
    used to be adopted as the whole queue) and seeds the session in 100-item chunks.
  - "Play next"/"play next all" use the real queue index, not the window edge.
  - Artwork decoding for the session is capped at 256x256.
  - Reorders still go through `moveMediaItem` for queues over 100 items; the
    `setMediaItems` shortcut only runs when the whole queue fits the controller.
  - Benchmark report in `docs/perf/shuffle-9.1.md`.
- **P9.1-G add to playlist speed**:
  - No pre-flight network calls; duplicates are checked in the local cache.
  - The optimistic cache write commits before the sheet closes (closing used to cancel it).
  - The refresh event fires after the server call, not before, and carries the playlist id.
  - Rollback restores the pre-add cache and shows an error message; there is no retry action.
  - Benchmark report in `docs/perf/playlist-add-9.1.md`.

## Phase 9.2 status: queue shuffle fix, queue tap toggle, lyrics coverage & display, romanization (1.2.2)

Version 1.2.2 (versionCode 10) — `applicationId com.feishin.mobile` unchanged.

- **P9.2-A queue shuffle** (`PlayerManager.kt`, `QueueShufflePipelineTest.kt`):
  - Root cause: `BoundedQueuePlayer` exposes at most 100 items to `MediaController`. When `applyOrder` checked `c.mediaItemCount == before.size` (100 == 533 -> false), it fell back to ~500 sequential `c.moveMediaItem` IPC calls in a tight loop on `Dispatchers.Main`. Each call triggered `onTimelineChanged` across Binder to Bluetooth AVRCP daemon, overflowing its buffer and crashing with SIGABRT (Bluetooth reset + ANR).
  - Fix: In-memory `ConcurrentHashMap<Long, MediaItem>` cache maintained in `PlayerManager`. Atomic `c.setMediaItems(reorderedMediaItems, targetIndex, currentPos)` reorders queues of any size in a single call without timeline inspection or audio restart. Rapid toggles coalesced with job cancellation. Tested up to 5,000 items with at most 1 timeline-changing call.
- **P9.2-B queue tap toggle** (`PlayerManager.kt`, `NowPlayingScreen.kt`, `QueueTapToggleTest.kt`):
  - Tapping the currently playing song (identified by stable `queueId`, distinct even for duplicate tracks) toggles pause/play via `player.playOrToggle(queueId)` with zero seek and no restart. Works while buffering and under repeat-one.
  - Tapping another row seeks to 0 and starts playback as before.
- **P9.2-C provider chain and coverage** (`BetterLyricsProvider.kt`, `LrclibLyricsProvider.kt`, `KuGouLyricsProvider.kt`, `LrclibMatcher.kt`, `LyricsRepository.kt`, `SettingsStore.kt`, `LyricsProbeTest.kt`, `LyricsMatchingTest.kt`):
  - New default priority: BetterLyrics, LRCLIB, Paxsenix, KuGou, and Local data with automatic DataStore migration.
  - Multi-variant title/artist search generator handles bilingual " - " cuts, parenthetical tags, and credit splitting.
  - Duration-first candidate scoring (±3 s) accepting matches across romanized and kanji artist spellings.
  - Multi-query sequential lookups capped at 6 requests per provider with 20 s total budget.
  - Automated JVM `LyricsProbeTest` against real providers achieves 12/12 (100%) synced lyrics rate on probe seed tracks.
- **P9.2-D lyrics display** (`NowPlayingScreen.kt`, `LyricsScrollEngine.kt`, `SettingsStore.kt`, `SettingsScreen.kt`, `LyricsScrollEngineTest.kt`):
  - Root cause: `animateContentSize` on line items combined with font size differences (16sp vs 21sp) changed item heights mid-scroll, causing LazyColumn to lag back and jitter.
  - Fix: Constant item layout metrics; bold typography for all lines; active emphasis and 40% dimming driven purely in draw phase via `graphicsLayer` alpha/scale.
  - Removed word-by-word highlight rendering and both word settings to eliminate character spacing artifacts. 22 dp inter-line gaps.
  - Smooth continuous scroll engine with 35% viewport anchor and dynamic duration acceleration on far seeks.
- **P9.2-E romanization** (`Romanizer.kt`, `SettingsStore.kt`, `LyricsMenuSheet.kt`, `LyricsViewModel.kt`, `SettingsScreen.kt`, `NowPlayingScreen.kt`, `RomanizerTest.kt`):
  - Japanese morphological analyzer with reading dictionary: Kuromoji IPADIC (`com.atilika.kuromoji:kuromoji-ipadic:0.9.0`, Apache 2.0 / BSD license).
  - Complete Katakana reading to Hepburn transliteration without macrons (phonetic ou/uu, particles wa/e/o, word-separated, no CJK output).
  - Provider romanization sanity check rejects empty or CJK-polluted strings.
  - Single global "Romanize lyrics" switch in DataStore, merging old settings and removing per-track toggles.
- **P9.2-F lyrics menu style** (`LyricsMenuSheet.kt`, `TrackActionsSheet.kt`, `LyricsMenuContrastTest.kt`):
  - Reused standard `surfaceContainerLow` container matching Song menu sheet across Dark/Light and Dynamic Theme modes.
  - 4 big rounded-square `ActionTile` controls (Edit, Refetch, Search, Copy).
  - High-contrast controls and `VusicControlDefaults.switchColors()` meeting WCAG AA standards.
- **P9.2-G queue strip on lyrics** (`NowPlayingScreen.kt`, `LyricsQueueStripTest.kt`):
  - Added bottom `PlayingFromBottom` peek strip to Lyrics tab using hoisted sheet state with identical gestures.
  - Bottom padding set to 96 dp and floating Sync pill elevated to 88 dp to sit cleanly above the strip.
- **P9.2-H lyrics tab corrections** (`NowPlayingScreen.kt`, `LyricsTabCorrections.kt`, `LyricsTabCorrectionsTest.kt`):
  - Solid queue strip: Opaque `bottomBgColor` extending under bottom insets prevents lyrics from bleeding behind strip in all theme combinations.
  - Static queue strip: Hoisted top bar and single `PlayingFromBottom` strip outside `AnimatedContent` so strip remains completely static during tab transitions.
  - Lyrics movement on exit / collapse fixed: Auto-scroll cancelled and frozen during tab exit transitions, drag gestures, or sheet collapse. Snaps directly to active line upon return once viewport is measured (`viewportHeight > 0`) without animated scrolling from top.
  - Edge fade: 56 dp vertical gradient alpha mask (`DstIn`) applied to the lyrics list container. All pure functions covered in `LyricsTabCorrectionsTest.kt`.

## Phase 9.3 status: downloads fixes & lyrics polish (1.2.3)

Version 1.2.3 (versionCode 11) — `applicationId com.feishin.mobile` unchanged.

- **P9.3-D resume downloads** (`DownloadWorker.kt`, `DownloadScheduler.kt`, `DownloadRepository.kt`, `MusicDatabase.kt`, `DownloadResumeTest.kt`):
  - Root cause: Worker returned `Result.success()` on stop/disconnect, marking work finished so WorkManager never rescheduled; network errors set status to `FAILED`, which `nextQueued()` skipped; HTTP 206 opened file with `FileOutputStream(partial, false)`, truncating data.
  - Fix: Returning `Result.retry()` when stopped or pending; auto-resume observer on `MobileDataPolicy.allows()`; append-mode `FileOutputStream(partial, true)` for HTTP 206 Range requests; socket drop preserves byte progress in `QUEUED`.
- **P9.3-G romaji look** (`Romanizer.kt`, `NowPlayingScreen.kt`, `RomanizerJoiningTest.kt`):
  - 6-rule Kuromoji morphological token joining joins verb/adj endings, auxiliaries (`shiranai`, `aishite`), and non-independent verbs, while keeping particles separate. Kana readings accumulated prior to Hepburn transliteration.
  - Typography updated to 60% base font size, regular weight, 70% active opacity, and 3 dp gap.
- **P9.3-C simultaneous downloads** (`SettingsStore.kt`, `SettingsViewModel.kt`, `DownloadWorker.kt`, `SettingsScreen.kt`, `ConcurrentDownloadsTest.kt`):
  - Added `maxConcurrentDownloads` (1–5, default 2) to DataStore.
  - Dynamic slot limiter in `DownloadWorker` starts new downloads when increased without canceling running ones when decreased.
  - Settings > Storage displays minus/plus stepper row on the right with live active/queued helper text.
- **P9.3-B clear download queue** (`DownloadsScreen.kt`, `DownloadsViewModel.kt`, `DownloadRepository.kt`, `MusicDatabase.kt`, `ClearDownloadQueueTest.kt`):
  - Added "Clear all" button with confirmation dialog in the download queue header.
  - Batched cancellation of active/queued work, partial file cleanup, and state reset in a single transaction.
- **P9.3-E lyrics line gap** (`SettingsStore.kt`, `LyricsMenuSheet.kt`, `SettingsScreen.kt`, `NowPlayingScreen.kt`, `LyricsScrollEngine.kt`, `LyricsLineGapTest.kt`):
  - Added continuous `lyricsLineGapPercent` setting (50%–200%, default 100% = 22 dp) with steppers in Lyrics menu and Settings > Lyrics.
  - Dynamically scales line-to-line spacing while preserving romaji subline clearance and viewport anchor math.
- **P9.3-A download covers** (`DownloadWorker.kt`, `DownloadRepository.kt`, `NetworkModule.kt`, `MusicDatabase.kt`, `OfflineCoverStoreTest.kt`):
  - Persistent offline cover store in `download_covers`, keyed by `coverArtId` and de-duplicated across tracks.
  - Offline-first Coil image loader interceptor serves local cover files without network access.
  - Reference-counted deletion prunes cover files only when no remaining downloaded song uses them.
  - Resumable low-priority background backfill for previously downloaded tracks.
- **P9.3-F lyrics animation** (`LyricsScrollEngine.kt`, `NowPlayingScreen.kt`, `LyricsAnimationTest.kt`):
  - Target metrics from video analysis: distance-based opacity falloff curve (dist 0 = 1.0, dist 1 = 0.42, dist 2 = 0.22, dist 3+ = 0.10) with 1.06x active scale.
  - 420 ms FastOutSlowInEasing transition for fluid line gliding.
- **P9.3-H lyrics tab sync and anchor** (`NowPlayingScreen.kt`, `PlayerManager.kt`, `LyricsTabSyncTest.kt`):
  - Root cause: `isTabActive` and `isLeaving` were computed but not passed to `LyricsPanel`, causing re-entry to run false `SNAP` with mismatched negative offset while `AnimatedContent` had disposed child state.
  - Fix: Wires `isTabActive` / `isLeaving`; preserves exact scroll position on return when active line is unchanged; unifies 35% viewport anchor targeting; tightens player position polling to 100 ms.
