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
- Stream cache uses Media3 `SimpleCache` (LRU); a changed cache size takes effect
  on the next launch. The image cache size is configurable the same way.
- "Allow cleartext HTTP" records intent; Android's cleartext policy is static per
  app, and the manifest already permits it so LAN servers work.
- Cover art loads over plain HTTPS; the self-signed toggle applies to API/stream/
  download traffic via the shared OkHttp client (no restart needed).
- Login state drives navigation directly (no restart needed). Credentials live in
  EncryptedSharedPreferences; test/server config is never committed.
- Android Auto works through the MediaSession (voice-search + browse filters
  declared). Home widget shows a launcher entry that opens the app.
