# Changelog

## 1.2.5

### Fixed
- **Seamless queue shuffle and drag-reorder without audio cutout**:
  - Replaces `setMediaItems` in `PlayerManager.applyOrder` with slice mutations (`removeMediaItems` and `addMediaItems` around the active item), keeping the currently playing `MediaItem` untouched in the ExoPlayer timeline with 0 ms of pause or re-buffering.
  - Routes drag-reorder drops (`commitOrder`) through `QueueCommit.singleMove` and `moveMediaItem(fromIndex, toIndex)` without re-preparing the player.
- **Offline playback crash & self-healing protection for downloaded songs**:
  - Routes `file://`, `content://`, and `android.resource://` URIs directly through local `DefaultDataSource` (`FileDataSource`), completely bypassing `CacheDataSource` and `SimpleCache` so seeking across downloaded tracks is a pure local disk operation.
  - Prevents `onPlayerError` from deleting downloaded files or evicting `SimpleCache` entries when the device is offline (`shouldDeleteDownloadOnError`, `shouldEvictStreamCacheOnError`).
  - Guards `skipToNext`, `skipToPrevious`, `playAt`, and `playOrToggle` while offline to skip to the next playable downloaded/cached track or pause cleanly with an `"Offline: <title> is not downloaded"` message instead of crashing or cascading through dead stream URLs.
- **Downloads tab search filter, sort options, row cleanup, and full-list queueing**:
  - Wires live case-insensitive filtering by title, artist, and album on the Library > `Downloads` tab.
  - Replaces the single `Name` sort option on `Downloads` with `Title` (default, migrating saved `Name` values), `Artist`, `Date added`, and `Duration` (with Ascending / Descending support).
  - Removes the redundant per-row `X` delete button on `Downloads` rows while keeping the downloaded checkmark and `⋮` menu aligned.
  - Queues the entire currently visible (filtered and sorted) downloaded songs list starting at the tapped index (`Playing from Downloads`) instead of queueing only 1 song.
- **Block touch bleed from Now Playing to underlying search bars**:
  - Consumes all pointer events across 100% of the `NowPlayingScreen` and `QueueSheet` root containers so taps on empty regions never pass through to underlying screens.
  - Automatically clears focus and hides the soft keyboard (`focusManager.clearFocus()`, `keyboardController?.hide()`) when expanding Now Playing.

## 1.2.4

### Fixed
- **Playback recovery for downloaded tracks and corrupted cache**:
  - Validates minimum audio payload size (>=8 KB) and Content-Type during downloads, rejecting Subsonic JSON/XML error bodies and truncated files.
  - Automatically evicts corrupt/partial cache entries on player errors and transparently falls back to network streaming if a local download fails to play.
  - Strict local file existence and size validation before resolving downloaded tracks.
  - Resolves correct container file extensions and sets explicit audio MIME hints on local `MediaItem`s.
  - Fixes player permanently freezing at `0:00/--:--` in `STATE_IDLE` by automatically preparing ExoPlayer before playback commands.
  - Hardens `PrefetchManager` with error-ignoring cache flags and discards tiny error payloads before persisting.
- **Lyrics tab zero-pop-in entry and immediate snap**:
  - Removes tab-transition delay blocking auto-scroll, enabling immediate scroll positioning during the 300 ms enter animation.
  - Eliminates first-entry layout shifting by gating lyrics rendering until the initial scroll snap completes.
  - Seeds active-line scale and opacity targets with instant snap animation on initial composition, avoiding unwanted 420 ms pop-in tweens.
  - Hoists lyrics settings into `LyricsPanel` to prevent single-frame uninitialized metric re-evaluations.

## 1.2.3

### Added
- **Offline cover art download**:
  - Automatically downloads and stores cover art in a persistent offline art directory when songs, albums, or playlists are downloaded.
  - Offline-first Coil image loader reads local cached covers before network requests.
  - Reference-counted cover deletion ensures art is kept while any downloaded song uses it.
  - Low-priority background backfill fetches missing covers for previously downloaded tracks.
- **Configurable simultaneous downloads**:
  - New "Simultaneous downloads" stepper (1–5, default 2) in Settings > Storage > Downloaded music.
  - Dynamic slot limiter starts extra queued downloads when increased and avoids canceling active downloads when decreased.
- **Clear all download queue**:
  - "Clear all" button in the download queue header with a confirmation dialog.
  - Cancels queued and active jobs in a single batch, deletes partial files, and resets track states.
- **Adjustable lyrics line gap**:
  - Stepper row (50% to 200% in 10% steps, default 100% = 22 dp) in both Lyrics menu and Settings > Lyrics.
  - Scales line-to-line spacing smoothly without affecting the gap to romaji sub-lines.
- **Cleaner, smaller romaji with morphological word joining**:
  - 6-rule Kuromoji morphological joining joins verb/adjective endings, auxiliaries (`shira nai` -> `shiranai`, `aishi te` -> `aishite`), and non-independent verbs while keeping particles separate.
  - Smaller 60% text size, regular weight, and 70% active opacity.
- **Modern fluid lyrics animation**:
  - Distance-based opacity falloff curve (dist 0 = 1.0, dist 1 = 0.42, dist 2 = 0.22, dist 3+ = 0.10) with 1.06x active scale.
  - 420 ms FastOutSlowInEasing transition for fluid line gliding.

### Fixed
- **Download auto-resume after Wi-Fi disconnect and reconnect**:
  - Interrupted downloads remain in queued/paused state without being marked failed.
  - WorkManager jobs re-enqueued automatically when network reconnects via `MobileDataPolicy` observer.
  - HTTP Range requests (`bytes=N-`) open files in append mode on HTTP 206 responses, avoiding file truncation.
- **Lyrics tab sync and anchor drift**:
  - Retains exact scroll position when returning to the Lyrics tab while paused or when the active line has not changed.
  - Fixes double-offset downward shift on tab re-entry by hoisting scroll tracking state to `NowPlayingScreen` and accounting for top content padding in the anchor offset calculation (0 px drift verified).
  - Properly wires `isTabActive` and `isLeaving` to freeze auto-scroll animations during tab exits or sheet dismiss.
  - Tightened player position polling to 100 ms and added live ExoPlayer position resolution for instantaneous sync upon tab entry.

## 1.2.2

### Added
- **Provider chain and synced lyrics coverage**:
  - New default lookup order: BetterLyrics, LRCLIB, Paxsenix, KuGou, and Local data with automatic DataStore migration.
  - Multi-query search with intelligent title and artist variant generator (handles bilingual " - " titles, bracket tags, and multi-artist credits).
  - Duration-first scoring (±3 s) accepting matches across romanized and kanji artist spellings.
  - Automated JVM `lyricsProbe` test reading `tools/lyrics_probe.txt` verifying real synced lyrics coverage.
- **Full Japanese morphological romanization**:
  - Integrated Kuromoji IPADIC morphological analyzer for complete kanji-to-Hepburn transliteration.
  - Plain Hepburn format without macrons (phonetic ou/uu, particles wa/e/o, word spacing, and CJK removal).
  - Single global "Romanize lyrics" setting across Lyrics menu and Settings > Lyrics, removing per-track toggles.
- **Metrolist-inspired lyrics display & smooth flow**:
  - Full-line highlight only; removed word-by-word highlight and settings to eliminate character spacing artifacts.
  - Bold typography, 22 dp inter-line gaps, and draw-phase opacity scaling in `graphicsLayer`.
  - Zero-reflow continuous scroll engine with dynamic easing and seek jump acceleration.
- **Lyrics menu restyle**:
  - Matched Song menu sheet container across all Dark/Light and Dynamic Theme combinations.
  - Replaced circle buttons with big rounded-square `ActionTile` controls (Edit, Refetch, Search, Copy).
  - High-contrast controls and switches meeting WCAG AA standards.
- **Queue bottom strip on Lyrics tab**:
  - Mirrored bottom "Playing from" queue strip with identical 72 dp peek height and shared sheet gestures on the Lyrics tab.
  - Solid opaque background on the queue strip extending under navigation bar insets, preventing lyrics from bleeding behind it.
  - Hoisted top bar and single queue strip outside tab transitions, keeping the strip completely static without sliding, fading, or flickering.
- **Lyrics edge fade & exit freeze**:
  - Smooth 56 dp vertical gradient alpha mask (`DstIn`) at top and bottom of the lyrics container.
  - Lyrics list freezes in place during tab transitions or sheet dismiss, snapping directly to the active line upon return without jumping from the top.

### Fixed
- **Queue sheet shuffle freeze & Bluetooth daemon crash**:
  - Pre-cached `MediaItem`s in `PlayerManager` and replaced sequential IPC loops with atomic single-call reordering.
  - Coalesced rapid shuffle toggles to prevent Binder transaction buffer overflow and Bluetooth daemon restarts.
- **Queue item tap toggle**:
  - Tapping the currently playing song in the queue toggles pause/play without seeking back to 0. Tapping any other song jumps and plays from 0.

## 1.2.1

### Added
- **Configurable lyrics sources**:
  - Lookup chain over LRCLIB, BetterLyrics (TTML word timing), KuGou (KRC word timing, unofficial source) and local metadata (server lyrics plus device file tags). Paxsenix is still listed, but disabled: its public endpoint is gone.
  - Sources are tried in the order you set, with per-request timeouts and a 15 s budget per song.
  - Settings > Appearance & Layout > Lyrics > "Lyrics sources" has a switch per source, up/down buttons for priority, "Reset order" and a "Prefer word-by-word lyrics" switch.
  - Online lookups send the title, artist, album and duration only. Nothing else leaves the device.
- **Lyrics menu**:
  - 3-dot menu on the Lyrics tab with Edit (paste LRC or plain text, cached for that song), Refetch (skips the cache), Search (LRCLIB lookup by title/artist) and Copy.
  - Switches for agent positioning, interval indicator, lyrics offset (+/- ms) and romanizing the current track.
- **Continuous lyrics text size**:
  - + / - buttons step the lyrics size from 80% to 220%, replacing the Small/Normal/Large choices. Existing values still map to 85/100/125.
- **Dual-line romanization**:
  - Romanized readings under the original line for Japanese (Hepburn), Korean (Revised Romanization) and Chinese (Pinyin with tone marks).
  - Per-track toggle in the lyrics menu, plus a global auto-romanize switch for CJK tracks in Lyrics settings.
- **Now Playing tab memory**:
  - The Song/Lyrics selection is persisted, so collapsing and reopening the sheet keeps the tab you were on.

### Fixed
- **Large queues and MediaSession**:
  - Item building and download-path lookups for big queues moved off the main thread and into one batched query, instead of per-item work on the UI thread.
  - Artwork decoded for the session is capped at 256×256, so a full-size bitmap cannot fill the Binder buffer on its own.
  - The session publishes a 100-item window around the current track to external clients (Bluetooth AVRCP, Android Auto, lockscreen) instead of the whole playlist.
  - Restoring a queue after the app process is killed keeps the full queue; it used to adopt the published 100-item window as the queue and drop the rest. The session is now seeded in 100-item chunks instead of one oversized transaction.
  - "Play next" and "Play next all" insert at the real queue position instead of the edge of the published window.
  - Queue state is saved on a 500 ms debounce rather than on every position tick.
- **Add to playlist**:
  - Removed the pre-flight network calls; the picker reads playlists from the local cache and checks duplicates locally.
  - Duplicates ask "<song> is already in <playlist>. Add anyway?" with Add / Cancel.
  - The picker closes once the optimistic cache write lands, and the snackbar reports "Added to <playlist>" (or "Added N to <playlist>").
  - The server update appends just the new ids via `songIdToAdd`, so existing playlist contents are not re-uploaded.
  - On failure the cache change is rolled back and an error message is shown.
  - A newly created playlist now refreshes its detail screen; the refresh event used to carry the name instead of the server id.
- **Lyrics reliability**:
  - One failing provider no longer aborts the rest of the chain.
  - Timeouts and HTTP errors are no longer cached as "no lyrics" for 7 days; only a real "not found" answer is.
  - TTML lines are rebuilt from the line body, so word spans no longer run together ("Lookatthestars").
  - KuGou lyric lookup and download switched to HTTPS. Its search host has no usable certificate and stays on HTTP.
  - KRC payloads are capped at 2 MB compressed and 4 MB inflated.
  - Saving the lyrics editor without changes no longer rewrites a word-synced lyric as line-only LRC.

## 1.2.0

### Added
- **Synced Lyrics Engine (LRCLIB first, metadata fallback)**:
  - Real-time line-by-line synced lyrics with active line enlargement, smooth 32-35% auto-scroll, drag-pause (3.5s auto-resume with floating "Sync" pill), instrumental gap notes ("♪"), and 3-dot intro countdowns.
  - LRCLIB integration with smart candidate matching, fuzzy title cleaning, artist splitting, duration scoring, and rate-limited non-storm lookups.
  - OpenSubsonic structured lyrics fallback (`getLyricsBySongId` with fallback to legacy `getLyrics`) and embedded local audio tags (ID3v2 USLT/SYLT, Vorbis/FLAC, MP4).
  - Room `lyrics_cache` persistence with 7-day negative caching and manual "Search again" in the Lyrics menu.
  - Comprehensive Lyrics settings in Appearance & Layout: online fetch toggle, priority selection (Online first / Song file first), text alignment (Left/Center), text size (Small/Normal/Large), auto-scroll, tap line to seek, word-by-word highlight, keep screen on, sync offset adjustment (-2000ms to +2000ms), show unsynced lyrics fallback, and "Clear lyrics cache" action.
- **Device Files in Library**:
  - Optional local device music playback integrated into the Library tab via a "View my" bottom sheet switcher with persistent state (`library_view_mode`).
  - Native MediaStore integration with live `ContentObserver` updates, Songs, Albums, Artists tabs, and local Album / Artist detail views.
  - Local album cover art resolver via MediaStore album art and `MediaMetadataRetriever`.
  - Storage settings: "Show device files" switch with 3-state permission handling (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`) and minimum track duration filter (Off, 15s, 30s, 60s) to exclude short notification/ringtone audio clips.
  - Seamless mixed queues supporting both Navidrome server and local device tracks without scrobbling or starring local files.

### Changed
- Renamed "Dynamic Now Playing color" setting to "Dynamic Theme" in Settings > Appearance & Layout (preserving existing user preference).

### Fixed
- Device files: favourite, save-to-playlist, download and "go to album / artist" actions are no longer offered for local tracks, where they used to send a local id to the server.
- Lyrics: embedded file-tag lyrics now parse for real device files, whose `content://` streams are buffered before the format sniff.
- Lyrics: server lyric offsets are applied in milliseconds (including negative offsets) instead of being scaled by 1000.
- Lyrics: lookups are paced between requests, so skipping through tracks quickly does not hammer LRCLIB.
- Device files: revoking audio permission now flips the screen back to the permission card instead of failing a query.
- Device files: a permanently denied permission offers "Open app settings", and returning to the app picks up a newly granted permission.
- Queue restore keeps each track's local file URI, so a queue of device tracks still plays after the service is recreated.

## 1.1.3

### Fixed
- Search: the "offline - downloaded music only" hint no longer paints behind the
  search bar. It's a real layout row inside the content column, clipped to its
  bounds, and hidden when the app-wide offline banner already says it.
- Queue: swiping a song away removes exactly that song, once - a fast swipe no
  longer takes neighboring rows with it or leaves a blank gap.
- Queue restore: a paused queue survives recents-swipe and force-stop - same
  songs, same order, same position, same "Playing from", paused, shuffle/repeat
  restored, nothing plays by itself.
- Library: tabs open instantly from what is already stored and refresh quietly
  in the background (at most once every few minutes); pull-to-refresh still
  forces a full refresh. Failed refreshes keep the old list.

## 1.1.2

### Queue
- Dragging down the song list now closes the queue once the list reaches its
  first song: the sheet follows your finger from that point and closes on
  release, or springs back to fully open if you let go early. A drag that
  starts with the list already at the top closes it the same way. The list
  still scrolls first while it has somewhere to scroll, an upward drag still
  only ever scrolls the list, and a fling the list consumed never closes the
  sheet on its own.
- The list no longer stretches at its top edge: that animation was swallowing
  the drag before the sheet could see it.

### Storage
- The song cache limit now goes up to 100 GB, in sensible steps (256 MB and
  512 MB, then 1 GB steps, coarser above 10 GB). The slider's full travel is
  exactly 100 GB.
- The slider says so when the size you picked is bigger than this phone can
  hold ("Only 12 GB is free on this phone. The cache can't grow past what's
  free."). The choice is still allowed - you may be about to free space.
- Below 1 GB of free space the cache stops taking in new songs instead of
  filling the phone up. Nothing is deleted, so a song that is playing right
  now is never interrupted, and downloads are left alone.
- Sizes saved by earlier versions still load, and every size on the rail can
  now actually be picked.

### Connectivity
- Two new switches in Settings > Connectivity > MOBILE DATA, both off by
  default: "Download over mobile data" and "Auto-cache over mobile data".
  Each says that mobile data may be used and that extra data charges may
  apply.
- Turning one on applies straight away: a download that was waiting for
  Wi-Fi starts on mobile data, and one that is running on mobile data is
  interrupted and waits for Wi-Fi again. Progress is kept - it is resumed,
  never restarted and never failed.
- The download queue now says "Waiting for Wi-Fi" instead of the bare word
  "Queued" when that is why nothing is happening.
- A Wi-Fi hotspot that Android marks as metered is treated as metered, and
  mobile data is never used while roaming.

### Deliberate limitations
- Roaming is refused for downloads and caching even when mobile data is
  allowed; the app never spends data abroad.
- The free-space warning does not stop the choice, and the cache limit
  itself is not lowered automatically - only the growth is stopped.

## 1.1.1 - playlist page rework, Now Playing colours, lyrics transition, queue fixes

### Playlist page
- Header row is now shuffle / play / more, centred; the heart is gone (it
  was wired to nothing on the playlist screen, so nothing is lost). Delete
  moved into the menu with a confirmation dialog, offered only for
  user-created playlists.
- Search moved to the top bar, next to the back arrow; it stays put while
  scrolling and scrolls the cover and buttons away before the field
  appears. Results filter live; Back or clear closes it without moving the
  list.
- Sort chips replaced by one sort button (the Library's sheet, reused) with
  the same five keys and asc/desc, directly above the song list, on the
  right. An Edit pencil next to it reveals the per-row remove buttons with
  a short animation; removal still addresses the stored entry, so it is
  correct under any sort or filter.
- Downloads & storage got the same sort button + sheet + bar search.

### Now Playing
- With dynamic colour off, Now Playing uses only the static theme (no
  cover-derived tint anywhere: background, buttons, progress, queue peek,
  lyrics, mini-player expand transition) with one consistent accent. With
  it on, behaviour is unchanged except every text and icon meets contrast
  in all four combinations (Light/Dark x dynamic on/off).
- Song / Lyrics now switches with a 250-350 ms transition: the selection
  pill slides, the panels cross-fade with a short travel, the background
  colour animates instead of snapping. Playback, lyrics scroll and the
  progress bar are unaffected; 10 fast taps do not jank; the system
  animation scale is honoured (off = instant).

### Queue
- Scrolling up from the bottom of the queue scrolls the list to the top
  instead of closing the sheet; the sheet only closes once nothing is left
  to scroll, and a fling the list consumed no longer closes it. Dragging on
  the handle or header still opens and closes it.
- The highlighted row now follows the current track by its stable queue id
  (so duplicate songs and reorders are tracked correctly), including
  during a drag, and lands on the right row after a drop with no
  restart or skip.

### Deliberate limitations
- The search scroll is 350 ms and needs a header tall enough to scroll; on
  very short playlists the field still appears but the list may not move.
- A genuine downward flick shorter than the sheet's settle threshold no
  longer closes a sheet that is being dragged (the price of not closing on
  a fling the list consumed).

## 1.1.0 - gesture fixes, playlist sort & search, new icon

### Playback & queue
- Fixed the queue sheet: tapping or dragging the "Playing from" peel now opens
  and closes reliably, respects the fling velocity, and settles from the position
  you actually released at - it used to pop in, flicker on a short drag, or
  ignore a fast fling.
- Fixed the mini player: it follows your finger on both axes with a real axis
  lock and fling-aware thresholds. Drag left/right skips (left = previous,
  right = next), drag down clears the queue, drag up opens Now Playing, and the
  first/last track with repeat off springs back instead of restarting.

### Library & discovery
- New: sort and search inside a playlist - Playlist order / Title / Artist /
  Date added / Duration with an ascending/descending toggle, and a search box
  that filters that playlist's tracks by title, artist or album. Works for
  normal playlists, Favorite songs, Cached and Downloaded; the choice is
  remembered per playlist and never changes anything on the server.

### Everywhere else
- The offline banner is part of the layout now, so it no longer covers the
  screen title, and content glides down instead of being overlapped.
- The library search field animates open and closed.
- The storage sliders only offer cache sizes that actually apply, so the value
  you pick finally sticks (the song cache used to bounce back to 5 GB).
- "Standard 60 Hz" now asks for exactly 60 Hz, and "Running at X Hz" updates
  live instead of freezing at whatever it said when the screen opened.
- New app icon: the Vusic eighth note (matching the in-app mark) on black, with
  a matching splash, round icon, monochrome layer and playback-notification icon.

### Deliberate limitations
- Cache ceilings stay at the app's supported sizes (5 GB stream, 1 GB image).
- Date-added sort falls back to the server's order (tracks carry no date).
- The 60 Hz request is a public-API hint; vendor smart-refresh may override it.

## 1.0.0 - first release

Vusic is an Android music player streaming from a Navidrome server via the
Subsonic/OpenSubsonic API.

### Playback & queue
- Gapless-capable ExoPlayer with a volume-ramp crossfade, ReplayGain
  (peak-limited, never clips), per-network quality, and a smart
  prefetch/LRU stream cache so transitions have no loading gap.
- Queue with drag reorder, swipe-to-remove, shuffle with order restore,
  Save as playlist, radio from any song, undo after clear, and Now Playing
  with synced lyrics.
- Mini player with progress ring, artwork-driven dynamic colors, and
  drag-down-to-clear with Undo.

### Library & discovery
- Home (Listen again, New releases, Forgotten favorites, mixes, Explore),
  full Library (songs/albums/artists/playlists, grid/list, per-tab sort),
  Search with recent searches, artist pages, Favorites and Cached collections.
- Offline: per-collection downloads, offline browsing/playback/scrobbles
  (queued in Room and replayed with original timestamps), airplane-mode
  support with an offline banner.

### Everywhere else
- Android Auto: Home/Library/Favorites/Downloads tabs, paged browsing, voice
  search, transport + shuffle/repeat/seek from the car, favorite/star actions.
- Home screen widget (compact and large) with transport controls that work
  even with the app process dead.
- System / Dark / Light themes with live switching, artwork-tinted Now
  Playing and queue, and a 90/120 Hz refresh-rate preference (Auto/Max/60 Hz).
- Accessibility: content descriptions, 48dp targets, 1.3x font scaling
  without clipping, full TalkBack navigation of the main screens.

### Deliberate limitations
- Crossfade is a volume ramp, not a true two-player overlap.
- No sleep timer.
- Gray album covers fall back to the neutral theme (unit-tested only).