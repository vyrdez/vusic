# Changelog

## 1.1.3 — 2026-10-20

### Fixed
- Search: the "offline — downloaded music only" hint no longer paints behind the
  search bar. It is a real layout row inside the content column, clipped to its
  bounds, and hidden when the app-wide offline banner already says it.
- Queue: swiping a song away removes exactly that song, once — a fast swipe no
  longer takes neighboring rows with it or leaves a blank gap.
- Queue restore: a paused queue survives recents-swipe and force-stop — same
  songs, same order, same position, same "Playing from", paused, shuffle/repeat
  restored, nothing plays by itself.
- Library: tabs open instantly from what is already stored and refresh quietly in
  the background (at most once every few minutes); pull-to-refresh still forces a
  full refresh. Failed refreshes keep the old list.

## 1.1.0 - gesture fixes, playlist sort & search, new icon

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

## 1.1.2 - queue close at top, 100 GB song cache, mobile-data option

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
- Real mobile data, a real metered hotspot and real roaming need real
  hardware; this pass was verified on an emulator with an emulated radio.
