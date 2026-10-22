# Changelog

## 1.2.1

### Added
- **Configurable Lyrics Provider Chain**:
  - Provider pipeline supporting LRCLIB, BetterLyrics (TTML word-level timing, background vocals, transliteration), Paxsenix, KuGou (KRC word-level timing decoding with unofficial source disclaimer), and Local song metadata.
  - Sequential rate-limited lookup with global mutex, 5s connect / 8s read timeouts, and 15s overall budget per song.
  - Settings > Appearance & Layout > Lyrics > "Lyrics sources" reorderable drag-and-drop screen with per-source toggles, "Prefer word-by-word lyrics" toggle, "Reset order" button, and privacy policy disclosure.
  - Automatic migration from legacy 9.0 priority switches.
- **Lyrics Menu Bottom Sheet**:
  - Dedicated bottom sheet on Lyrics tab (3-dot menu button) preserving existing Song tab options.
  - Top action buttons: Edit (custom LRC / plain text override with timestamp validation, "Edited by you" source badge, and "Reset to original"), Refetch (bypasses cache and re-runs chain), Search (manual lookup by title/artist across enabled providers), and Copy.
  - Settings switches: Respect agent positioning, Show interval indicator, Lyrics offset adjuster (+/- ms), and Romanize current track toggle.
- **Continuous Lyrics Text Size**:
  - Granular text size slider from 80% to 220% in 10% steps in Lyrics settings, replacing discrete Small/Normal/Large options while preserving backward compatibility.
- **Dual-Line Romanization / Transliteration**:
  - Dual-line pronunciation rendering showing romanized reading beneath Japanese (Hepburn romaji), Korean (Revised Romanization), and Chinese (compact Pinyin with tone marks) characters.
  - Per-track toggle in lyrics menu and global auto-romanize setting in Appearance & Layout > Lyrics.
- **Remember Now Playing Lyrics Tab**:
  - Now Playing tab selection (Song vs Lyrics) hoisted to parent and persisted across sheet collapse/reopen.
  - Automatically resets to Song tab on queue clear or mini player dismiss.

### Fixed
- **Large Playlist Shuffle & MediaSession Stability (500+ Songs)**:
  - Fixed ANR freezes and Bluetooth/AVRCP daemon crashes (`TransactionTooLargeException`) when shuffling or loading large playlists (500 to 3,000+ songs).
  - Offloaded item conversion and download path lookups to `Dispatchers.IO` and `Dispatchers.Default` using batch query `completedPathMap()`.
  - Replaced loop of `c.moveMediaItem` with single atomic `c.setMediaItems(reordered, targetIndex, pos)` for queues > 30 items.
  - Introduced `BoundedQueuePlayer` forwarding player, capping the published timeline to 100 items around the active track for external clients (Bluetooth AVRCP, Android Auto, lockscreen).
  - Constrained `CoilBitmapLoader` artwork decodes to `256x256` to prevent Binder buffer overflow.
  - Debounced queue state persistence with 500ms coarse debounce.
- **Add-to-Playlist Speed & Optimistic UI**:
  - Fixed slow add-to-playlist by eliminating pre-flight network calls.
  - Duplicate check queries local Room cache in 0ms; missing cache proceeds without network block.
  - If song is already in playlist, displays confirmation dialog: `"<song> is already in <playlist>. Add anyway?"` with "Add" and "Cancel".
  - Instant dismissal of picker sheet on selection with immediate snackbar ("Added to <playlist>") and optimistic Room cache updates (`songCount`, `durationSec`, tracks).
  - Background atomic append via Subsonic `updatePlaylist` using `songIdToAdd=<id>` without re-uploading existing playlist contents.
  - Automatic Room cache rollback and retry snackbar ("Couldn't add to <playlist> • Retry") on network sync failure.

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