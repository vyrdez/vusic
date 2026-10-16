# Vusic

A personal Android music player for a self-hosted [Navidrome](https://www.navidrome.org/)
server, spoken to over the Subsonic/OpenSubsonic API.

Written in Kotlin with Jetpack Compose and Material 3. Dark by default, with
System and Light themes.

---

## Features

**Playback**

- Streaming and local playback through Media3/ExoPlayer, with gapless support.
- Crossfade as a volume ramp between tracks (see *Limitations*).
- ReplayGain using the server's OpenSubsonic gain/peak values, applied as a
  peak-limited scale that cannot clip.
- Per-network quality (bitrate and format) for mobile and Wi-Fi.
- Smart prefetch cache: the next queue items are fetched ahead of time, so
  transitions have no loading gap and a repeat play makes no network request.
- Synced lyrics when the server provides them.
- Sleep-timer-free by design (see *Limitations*).

**Queue**

- Drag to reorder, swipe to remove, shuffle with order restore.
- Save the queue as a playlist, add or remove individual tracks.
- Start a radio station from any song.
- Undo after clearing the queue, from the mini player or the queue screen.

**Library and discovery**

- Home: Listen again, New releases, Forgotten favorites, genre chips, mixes.
- Library tabs for playlists, downloads, albums, artists and songs, each with
  its own grid/list layout and sort order.
- Search with result chips, "view more" and recent searches.
- Artist and album pages, playlist pages.
- Favorites and a Cached collection of fully downloaded-for-offline songs.

**Offline**

- Download songs or whole albums/collections.
- Offline browsing, playback and radio; offline scrobbles and stars are queued
  locally and replayed with their original timestamps once you reconnect.
- Offline banner, Wi-Fi-only downloads, storage cap with live disk-usage display.

**Everywhere else**

- **Android Auto**: a Media3 `MediaLibraryService` with Home, Library, Favorites
  and Downloads tabs, paged browsing, voice search, transport controls plus
  shuffle/repeat/seek, and Favorite / Start-radio actions. The car and the phone
  share one queue.
- **Home screen widget**: compact and large layouts with cover art, title/artist
  and previous/play-pause/next/favorite. Updates on track changes, and the
  controls keep working even after the app process has been killed.
- **Notification and lock screen**: artwork, transport, and a seek bar.
- **Themes**: System, Dark and Light, switching live. Optional Dynamic Now
  Playing colour derived from the artwork.
- **Refresh rate**: Auto (peak rate while interacting, system lowers it when
  idle), Max (hold the peak rate) or Standard 60 Hz. The setting hides itself on
  devices that expose only one display mode.
- **Accessibility**: content descriptions on icon buttons, 48dp minimum touch
  targets, text that scales to 1.3x without clipping, TalkBack-navigable on the
  main screens.

---

## Requirements

- **Android 8.0 (API 26) or newer.**
- **A Navidrome server** reachable from the device. Any Subsonic-compatible
  server that exposes the OpenSubsonic endpoints Vusic uses should also work.
- A Navidrome user account. Sign in from the login screen; credentials are
  stored in Android's `EncryptedSharedPreferences`.

---

## Installing a release

1. Download `Vusic-<version>-arm64-v8a.apk` from the
   [Releases page](https://github.com/vyrdez/vusic/releases) (the phone build;
   `Vusic-<version>-x86_64.apk` is for emulators only).
2. Allow installs from your browser or file manager: **Settings → Apps →
   Special app access → Install unknown apps**.
3. Install the APK and open Vusic, then enter your server URL, username and
   password. Turn on *Allow self-signed certificates* if your server uses one.

Every release ships a `.sha256` file next to the APK. To verify a download:

```powershell
Get-FileHash .\Vusic-<version>-arm64-v8a.apk -Algorithm SHA256
```

Official releases are signed with a certificate whose SHA-256 fingerprint is:

```
33:99:ED:FC:E8:D6:82:E4:B1:99:D8:0A:A2:6D:6B:09:2B:78:EF:BE:C9:29:77:F9:49:1F:27:97:BD:CC:66:2C
```

Check it with `apksigner verify --print-certs` if you want to be sure the APK
came from this project.

---

## Building from source

Prerequisites:

- **JDK 17**
- Android SDK with **platform 34**, **build-tools 34.0.0** and **platform-tools**
- Point `sdk.dir` at your SDK in `local.properties` (Android Studio writes this
  for you), or set the `ANDROID_HOME` environment variable.

```bash
git clone https://github.com/vyrdez/vusic.git
cd vusic
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

The debug APK is written per ABI to
`app/build/outputs/apk/debug/app-<abi>-debug.apk` — install
`app-x86_64-debug.apk` on an emulator or `app-arm64-v8a-debug.apk` on a phone.

Other useful tasks:

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew lint                   # Android Lint
./gradlew assembleRelease        # R8-minified release build (see signing)
./gradlew clean
```

CI runs `assembleDebug`, `testDebugUnitTest` and `lint` on every push and pull
request to `main`.

### Release signing is local only

The release keystore is **not** in this repository and never will be. Android
ties app updates to the signing certificate, so the project key is kept outside
version control with a private backup.

`./gradlew assembleRelease` works without any signing material and produces an
*unsigned* APK. To sign your own build, create your own key — do not reuse
anyone else's:

```bash
keytool -genkeypair -v \
  -keystore keystore/vusic-release.jks \
  -alias vusic -keyalg RSA -keysize 4096 -validity 10950
```

Then copy `keystore.properties.example` to `keystore/keystore.properties` and
fill in your own passwords:

```properties
storePassword=your-store-password
keyAlias=vusic
keyPassword=your-key-password
```

Both `keystore/` and `*.jks` are gitignored. Back your key up somewhere safe.

---

## Architecture

Two Gradle modules:

| Module            | Purpose                                                          |
| ----------------- | ---------------------------------------------------------------- |
| `app`             | The whole application.                                            |
| `baselineprofile` | A hand-written Baseline Profile packaged into the release APK.     |

Inside `app/src/main/java/com/feishin/mobile`:

| Package            | Responsibility                                                     |
| ------------------ | ------------------------------------------------------------------ |
| `core/common`      | Pure helpers: URL building, auth tokens, replay-gain math, row layout, lyrics cache. |
| `core/network`     | Stream/cover-art URL providers and request signing.                 |
| `core/ui`          | Framework-free UI logic, e.g. the refresh-rate mode selector.       |
| `data/remote`      | Retrofit `SubsonicApi` and DTOs, the redacting HTTP logger.          |
| `data/network`     | OkHttp setup, TLS trust for self-signed servers, host rewriting.     |
| `data/db`          | Room database for offline scrobbles, stars and download bookkeeping.  |
| `data/auth`        | Credential store (`EncryptedSharedPreferences`) and session state.   |
| `data/cache`, `data/download` | Media3 caches and the WorkManager download pipeline.      |
| `data/settings`, `data/favorites`, `data/queue` | DataStore-backed settings, favourites, queue persistence. |
| `data/repo`, `data/mapper` | Repositories and DTO → domain mapping.                  |
| `domain/model`, `domain/repo` | Platform-independent models and repository interfaces. |
| `di`               | Hilt modules wiring the graph.                                      |
| `playback`         | `PlayerService` (Media3 `MediaLibraryService`), `PlayerManager`, prefetch, audio focus. |
| `ui/`              | Compose screens: `home`, `library`, `player`, `search`, `detail`, `settings`, `login`, `offline`, plus `components`, `theme`, `navigation`. |
| `widget`           | Glance home-screen widget and its action receiver.                  |

Key choices:

- **One player.** A single Media3 `PlayerService` is the only playback engine.
  The queue, the mini player, the widget, Android Auto and the notification all
  talk to the same `MediaSession`, so they can never disagree.
- **Plain coroutines + StateFlow**, no third-party DI/architecture framework
  beyond Hilt.
- **DataStore** for settings, **Room** for the offline scrobble/star queue and
  download bookkeeping, **EncryptedSharedPreferences** for credentials.
- **Build-time release hardening**: R8 with explicit keep rules for Retrofit,
  kotlinx.serialization, Hilt, Room, Media3, WorkManager, Coil and Glance; a
  packaged Baseline Profile; ABI splits (`arm64-v8a` for phones, `x86_64` for
  emulators); English-only resources; all HTTP logging compiled out of release
  builds.

---

## Settings overview

Settings is a single list of six sub-screens:

- **Server & Account** — server URL, username, password, change-server and
  logout (with a choice of whether downloads are kept).
- **Appearance & Layout** — theme (System/Dark/Light, live), Dynamic Now Playing
  colour, per-tab grid/list layout, per-tab sort order, mix length, refresh rate.
- **Sound & Playback** — bitrate and format per network, crossfade, ReplayGain,
  gapless, headphone/volume/focus behaviour, playback cache size.
- **Connectivity** — allow self-signed certificates, test connection.
- **Storage** — disk usage, download queue and concurrency, song-cache size,
  download limits, clear-cache actions.
- **About** — version and build number.

---

## Limitations

These are deliberate, not oversights:

- **Crossfade is a volume ramp**, not a true two-player overlap. A second
  ExoPlayer would put gapless playback, scrobble timing and queue edits at risk,
  so the ramp is the safe version that ships.
- **ReplayGain is peak-limited** — gain is applied as a volume scale that never
  boosts above unity, so it cannot clip.
- **Gapless is a build-time ExoPlayer property.** The toggle is persisted and
  applied when the player is constructed, not switched live.
- **No sleep timer.** Crossfade, buffer settings and cache tuning already change
  the sound; a timer that pauses at a fixed offset tends to land badly with
  variable bitrate, so it was left out.
- **Gray album covers** fall back to the neutral theme colour. The extraction
  path is unit-tested, not device-tested.
- **High refresh rate** is applied through the public window hints
  (`preferredDisplayModeId` / `preferredRefreshRate`). Behaviour above 60 Hz was
  verified by unit test and code inspection only — the emulator used during
  development exposes a single 60 Hz mode.
- **Bluetooth** audio-device switching was exercised on an emulator only.
- **Android Auto** browse art may not render in the head unit when your server
  uses a self-signed certificate, because the car's own image loader cannot use
  the app's trust settings.
- **Cleartext HTTP** is permitted by the manifest so LAN servers work; there is
  no per-server enforcement at runtime.
- **English only.** Resources are packaged for `en` alone.

---

## Acknowledgements

- Inspired by the open-source [Feishin](https://github.com/Feishin) project. No
  source code was copied from it — Vusic is an independent implementation, and
  the repository contains no third-party copyright headers or license notices.
- Built on the [Subsonic](https://subsonic.org/) / OpenSubsonic API
  specification, a public HTTP API implemented by Navidrome and many other
  servers.
- The application id `com.feishin.mobile` was chosen early in development and is
  retained so existing installs keep receiving updates.

---

## Disclaimer

Vusic is a personal, unofficial project. It is not affiliated with, endorsed by
or supported by the Navidrome or Subsonic projects, or by the Feishin project.
Use it at your own risk; there is no warranty of any kind. Your music, your
server, your data.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
