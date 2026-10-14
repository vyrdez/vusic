# Vusic

Android music player streaming from a Navidrome server via the Subsonic/OpenSubsonic API.
Kotlin, Jetpack Compose + Material 3 (dark default), Hilt, Retrofit, Media3, Coil, DataStore, Room.

## Rebuild & install

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

(Use `./gradlew` instead of `.\gradlew.bat` on macOS/Linux.)
Prerequisites: JDK 17 and Android SDK (platform 34, build-tools 34.0.0, platform-tools).
Set `sdk.dir` in `local.properties` if the SDK lives elsewhere.

### Release build (R8 minified, signed)

R8 minification + resource shrinking are on for release, with rules in
`app/proguard-rules.pro` covering Retrofit, kotlinx.serialization, Hilt, Room,
Media3, WorkManager, Coil and the Android Auto/service entry points.

```powershell
# One-time: create a local keystore (kept out of git via .gitignore).
#   keystore/vusic-release.jks        the keystore
#   keystore/keystore.properties      storePassword / keyAlias / keyPassword
.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

Without `keystore/` the release task still succeeds but produces an *unsigned*
APK (`app-release-unsigned.apk`), so CI needs no secrets.
Release builds log nothing: the HTTP logger and all diagnostics are
compile-time-disabled, and API credentials (`u`/`t`/`s`) are redacted in debug
as well.

## Notes / limitations

- Crossfade is a **best-effort volume ramp** on the single player (fades the tail
  of the outgoing track and the head of the incoming one). A true two-player
  overlap would put gapless playback, scrobble timing and queue edits at risk, so
  it is deliberately not used.
- ReplayGain uses the server's OpenSubsonic gain/peak values, applied as a
  peak-limited volume scale (never boosting above unity, so it cannot clip).
- Gapless is an ExoPlayer build-time property; the toggle is persisted and applied
  when the player is built.
- Offline: downloads live in app-private storage per server; playback, radio,
  favorites and library browsing all work with no connection, and offline
  scrobbles/stars are queued in Room and replayed with their original timestamps.
- Smart cache: the next few queue items are fetched into a shared Media3 cache
  using stable keys (song + quality, no per-request salt), so transitions have no
  loading gap and a repeat play makes no network request. A built-in "Cached"
  collection lists fully cached songs and plays them offline; it prunes itself as
  the LRU evicts bytes. Enable under Settings > Sound & Playback > Playback cache.
- Stream cache uses Media3 `SimpleCache` with a live LRU evictor: shrinking the
  limit trims the cache immediately, and the Cached collection + usage numbers
  refresh at once. Shrinking the image cache clears it immediately; growing it
  needs a restart (Settings offers a "Restart now" button).
- "Allow cleartext HTTP" records intent; Android's cleartext policy is static per
  app, and the manifest already permits it so LAN servers work.
- Cover art loads over plain HTTPS; the self-signed toggle applies to API/stream/
  download traffic via the shared OkHttp client (no restart needed).
- Login state drives navigation directly (no restart needed). Credentials live in
  EncryptedSharedPreferences; test/server config is never committed.
- Android Auto: the player is a Media3 `MediaLibraryService` with 4 root tabs  (Home, Library, Favorites, Downloads), paged browsing, voice search
  ("play <artist/album/song>", shuffle requests honoured), transport + shuffle +
  repeat + seek from the car, and Favorite / Start-radio custom actions. Browse
  playback resolves to the same stream/download URIs as the phone and mirrors
  the queue back into the app, so the car and phone never diverge; artwork is
  served through a Coil-backed `BitmapLoader`. Setup: install the APK, then in
  the Android Auto app enable developer settings (tap the version row 10x) and
  turn on "Unknown sources" — sideloaded apps are hidden otherwise. Offline and
  downloaded content work in the car; servers with self-signed certificates may
  not render browse art in the head unit (the car's loader can't use the app's
  trust settings).
- Home screen widget: resizable Now Playing widget (compact 4x1 and large 4x2)
  with cover art, title/artist, previous/play-pause/next and a favorite button;
  tapping the cover opens Now Playing. It updates on track/play/favorite changes
  without polling, shows "Nothing playing" when the queue is empty, and its
  controls keep working after the app process is killed (they talk to the media
  session; Play rebuilds the persisted queue paused).
- Accessibility: every icon button carries a content description, controls meet
  the 48dp minimum touch target, and text scales to 1.3x without clipping (the
  app is fully TalkBack-navigable on Home, Now Playing and Settings).
- Error handling: a crash-safe layer absorbs non-fatal background failures
  (a single failed network call can no longer take the app down) while real
  main-thread bugs still surface; common failures show friendly messages
  (wrong server, expired session, no network) instead of raw errors.
- Release cold start measured at 505–888 ms total on the Pixel 7 emulator with
  the R8-minified release APK.
