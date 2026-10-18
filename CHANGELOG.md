# Changelog

## 1.1.0 — gesture fixes, playlist sort & search, new icon

### Playback & queue
- Fixed the queue sheet: tapping or dragging the "Playing from" peel now opens
  and closes reliably, respects the fling velocity, and settles from the position
  you actually released at — it used to pop in, flicker on a short drag, or
  ignore a fast fling.
- Fixed the mini player: it follows your finger on both axes with a real axis
  lock and fling-aware thresholds. Drag left/right skips (left = previous,
  right = next), drag down clears the queue, drag up opens Now Playing, and the
  first/last track with repeat off springs back instead of restarting.

### Library & discovery
- New: sort and search inside a playlist — Playlist order / Title / Artist /
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

## 1.0.0 — first release

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

## 1.1.1 - playlist page rework, Now Playing colours, lyrics transition, queue fixes

### Playlist page
- Header row is now shuffle / play / more, centred; the heart is gone (it
  was wired to nothing on the playlist screen, so nothing is lost). Delete
  moved into the ⋮ menu with a confirmation dialog, offered only for
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
  in all four combinations (Light/Dark × dynamic on/off).
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
- Touch feel, 120 Hz fling behaviour and real-device verification were not
  performed in this pass (only the emulator).
