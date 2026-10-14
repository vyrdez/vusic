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
- Android Auto: the player is a Media3 `MediaLibraryService` with 4 root tabs
  (Home, Library, Favorites, Downloads), paged browsing, voice search
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
