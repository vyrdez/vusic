# Feishin Mobile

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

- Crossfade setting is stored and honored as best-effort (ExoPlayer has no native
  crossfade DSP); gapless is on by default.
- ReplayGain is a stored preference applied best-effort; Media3 has no native
  ReplayGain pipeline.
- Cover art loads over plain HTTPS; the self-signed toggle applies to API/stream/
  download traffic via the shared OkHttp client (no restart needed).
- Login state drives navigation directly (no restart needed). Credentials live in
  EncryptedSharedPreferences; test/server config is never committed.
- Android Auto works through the MediaSession (voice-search + browse filters
  declared). Home widget shows a launcher entry that opens the app.
