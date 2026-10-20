# Library open times — 8.3 (cache-first tabs)

Measured on the API 34 emulator (Pixel 7 AVD), **debug** build, with the network
throttled to a realistic bad connection:

```
adb emu network speed gsm
adb emu network delay 800
```

The server is the live Navidrome instance (`navidrome.vyrde.site`), and its
metadata cache had already been populated by normal use, which is the case the
bug was about: *there is data to show, and the app still shows a spinner*.

## Method

The probe is `adb shell am force-stop` + `am start`, then polling
`uiautomator dump` until the Library's playlist rows are in the hierarchy
("Cached" and "test" both present). `uiautomator dump` costs ~1.5 s per call on
this screen, so the numbers include a constant probe overhead; the comparison is
like-for-like because the same script ran on both builds.

## Cold start → Library content visible (Playlists tab)

| Build | Trial 1 | Trial 2 | Trial 3 | Notes |
|---|---|---|---|---|
| 1.1.2 (network-first) | 4.7 s | 4.3 s | 4.3 s | every tab waited for its request |
| 1.1.3 (cache-first) | 3.4 s | 3.3 s | 3.3 s | content is a local read |

The remaining ~3 s is app start (process, auth gate, first composition), not the
library. The difference that matters is what the number is made of:

* **before**, the Library is ready when the last of six requests
  (`getAlbumList`, `getArtists`, `getGenres`, `getPlaylists`, `getStarred2`,
  `getRandomSongs(500)`) answers, and the tab shows `UiState.Loading` until then;
* **after**, the six tabs render their stored payload immediately and the
  requests run in the background, throttled to one pass per 3 minutes per entry.

## Warm tab switch with the cache present (before)

`Home → Library`, same throttle, 1.1.2: **11.9 s** until the first playlist row
appeared, with the cache fully populated — the reference screenshot
`docs/reference/phase8.3/…18-34-18…` shows the two spinners of that state.

On 1.1.3 the same switch is bounded by the probe (the rows are already in the
first dump after the tap); no request is on the way to the screen.

## What is not in these numbers

* Real cellular latency and packet loss (emulator throttling only).
* First launch with an empty cache: unchanged by design — spinner, then the
  request, and on failure a clear error with Retry within the 5 s connect /
  10 s read timeouts.
* Cover art (Coil keeps its own disk cache; the grid may still fill covers in).
