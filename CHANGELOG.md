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
