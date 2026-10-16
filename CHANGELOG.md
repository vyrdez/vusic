# Changelog

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
